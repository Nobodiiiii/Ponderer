#!/usr/bin/env python3
"""
Build, copy, and quickplay local Ponderer client instances.
"""

from __future__ import annotations

import argparse
import errno
import glob
import io
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from contextlib import contextmanager, nullcontext
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

UTF8_JVM_ARGS = (
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8",
)


def configure_output_encoding() -> None:
    if platform.system() == "Windows":
        try:
            import ctypes

            kernel32 = ctypes.windll.kernel32
            kernel32.SetConsoleCP(65001)
            kernel32.SetConsoleOutputCP(65001)
        except (AttributeError, OSError):
            pass

    sys.stdout = io.TextIOWrapper(
        sys.stdout.buffer, encoding="utf-8", errors="replace", line_buffering=True
    )
    sys.stderr = io.TextIOWrapper(
        sys.stderr.buffer, encoding="utf-8", errors="replace", line_buffering=True
    )


def append_missing_jvm_args(existing: str) -> str:
    combined = existing.strip()
    for arg in UTF8_JVM_ARGS:
        if arg not in combined:
            combined = f"{combined} {arg}".strip()
    return combined


def windows_java_ipc_arg() -> str:
    ipc_dir = PROJECT_ROOT / ".gradle" / "java-ipc"
    ipc_dir.mkdir(parents=True, exist_ok=True)
    return f"-Djdk.net.unixdomain.tmpdir={ipc_dir}"


def gradle_subprocess_env() -> dict[str, str] | None:
    if platform.system() != "Windows":
        return None

    env = os.environ.copy()
    env["GRADLE_OPTS"] = append_missing_jvm_args(env.get("GRADLE_OPTS", ""))
    # Apply before the wrapper/daemon starts: project build scripts run too late
    # to repair a Selector's "Unable to establish loopback connection" failure.
    java_options = env.get("JAVA_TOOL_OPTIONS", "")
    if "-Djdk.net.unixdomain.tmpdir=" not in java_options:
        env["JAVA_TOOL_OPTIONS"] = f'{java_options} "{windows_java_ipc_arg()}"'.strip()
    env.setdefault("PYTHONIOENCODING", "utf-8")
    return env


def gradle_encoding_args() -> list[str]:
    if platform.system() != "Windows":
        return []

    props = read_gradle_properties()
    jvmargs = append_missing_jvm_args(props.get("org.gradle.jvmargs", ""))
    return [f"-Dorg.gradle.jvmargs={jvmargs}"]


configure_output_encoding()

PROJECT_ROOT = Path(__file__).resolve().parent
DOT_MINECRAFT = PROJECT_ROOT / ".minecraft"
VERSIONS_DIR = DOT_MINECRAFT / "versions"
LIBRARIES_DIR = DOT_MINECRAFT / "libraries"
ASSETS_DIR = DOT_MINECRAFT / "assets"

DEFAULT_WIDTH = 1600
DEFAULT_HEIGHT = 900
# Fixed offline UUID for Dev, shared by all local client launches.
DEFAULT_PLAYER_UUID = "380df991f603344ca090369bad2a924a"
DEFAULT_SMOKE_TIMEOUT = 40
DEFAULT_SMOKE_LOG_LINES = 160
POST_ENTRY_SETTLE_SECONDS = 2.0
LAUNCH_STARTUP_CHECK_SECONDS = 3.0

SUCCESS_PATTERNS = (
    re.compile(r"\[Server thread/INFO\](?: \[[^\]]+\])?: .+ logged in with entity id "),
    re.compile(r"\[Server thread/INFO\](?: \[[^\]]+\])?: .+ joined the game"),
    re.compile(r"\[Server thread/INFO\](?: \[[^\]]+\])?: .+加入了游戏"),
)

WORLD_LOCKED_PATTERN = re.compile(r"session\.lock: already locked \(possibly by other Minecraft instance\?\)")

ERROR_PATTERN = re.compile(
    r"\b(ERROR|FATAL)\b|Exception|Caused by:|Mixin apply failed|Crash|Failed to|Unable to",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class PlatformSpec:
    target: str
    module: str
    loader: str
    instance_suffix: str
    prefer_all_jar: bool = False


@dataclass(frozen=True)
class LogCursor:
    exists: bool
    size: int = 0
    mtime_ns: int = 0


@dataclass(frozen=True)
class SmokeResult:
    target: str
    ok: bool
    log_path: Path
    process_exit_code: int | None = None


KNOWN_PLATFORMS = (
    PlatformSpec("fabric", "Fabric", "fabric", "Fabric"),
    PlatformSpec("forge", "Forge", "forge", "Forge", prefer_all_jar=True),
    PlatformSpec("neoforge", "NeoForge", "neoforge", "NeoForge"),
)


def read_gradle_properties() -> dict[str, str]:
    props: dict[str, str] = {}
    for line in (PROJECT_ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def read_settings_modules() -> set[str]:
    settings = (PROJECT_ROOT / "settings.gradle").read_text(encoding="utf-8")
    modules: set[str] = set()
    for match in re.finditer(r"^\s*include\s+(.+)$", settings, flags=re.MULTILINE):
        modules.update(name.lstrip(":") for name in re.findall(r"['\"]([^'\"]+)['\"]", match.group(1)))
    return modules


def available_platforms() -> dict[str, PlatformSpec]:
    modules = read_settings_modules()
    return {spec.target: spec for spec in KNOWN_PLATFORMS if spec.module in modules}


def resolve_targets(target: str) -> list[PlatformSpec]:
    platforms = available_platforms()
    if target == "both":
        selected = []
        if "fabric" in platforms:
            selected.append(platforms["fabric"])
        selected.extend([platforms[key] for key in ("forge", "neoforge") if key in platforms][:1])
    elif target == "forge":
        selected = [platforms[key] for key in ("forge", "neoforge") if key in platforms][:1]
    elif target in platforms:
        selected = [platforms[target]]
    else:
        selected = []

    if not selected:
        configured = ", ".join(sorted(platforms)) or "none"
        raise RuntimeError(f"Target '{target}' is unavailable. Active client platforms: {configured}")
    return selected


@lru_cache(maxsize=None)
def find_java(required_major: int) -> str:
    executable = "java.exe" if os.name == "nt" else "java"
    candidates: list[Path] = []
    for java_home in (os.environ.get("JAVA_HOME"), read_gradle_properties().get("org.gradle.java.home")):
        if java_home:
            candidates.append(Path(java_home) / "bin" / executable)
    path_java = shutil.which("java")
    if path_java:
        candidates.append(Path(path_java))

    # IntelliJ and Gradle toolchains can use a different JDK from JAVA_HOME.
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    jdk_roots = [Path.home() / ".jdks", gradle_home / "jdks"]
    if os.name == "nt":
        program_files = Path(os.environ.get("ProgramFiles", "C:/Program Files"))
        jdk_roots.extend(program_files / name for name in ("Java", "Microsoft", "Eclipse Adoptium"))
    elif sys.platform == "darwin":
        jdk_roots.extend((Path("/Library/Java/JavaVirtualMachines"), Path.home() / "Library/Java/JavaVirtualMachines"))
    else:
        jdk_roots.append(Path("/usr/lib/jvm"))
    for root in jdk_roots:
        if root.is_dir():
            candidates.extend(sorted(root.glob(f"*/bin/{executable}")))
            candidates.extend(sorted(root.glob(f"*/*/bin/{executable}")))
            candidates.extend(sorted(root.glob(f"*/Contents/Home/bin/{executable}")))

    checked: list[str] = []
    fallback: tuple[Path, int] | None = None
    for candidate in dict.fromkeys(path.resolve() for path in candidates):
        if not candidate.is_file():
            continue
        try:
            result = subprocess.run(
                [str(candidate), "-version"], capture_output=True,
                text=True, encoding="utf-8", errors="replace", timeout=5,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            checked.append(f"{candidate}: {exc}")
            continue
        match = re.search(r'\bversion\s+"(?:1\.)?(\d+)', result.stderr + result.stdout)
        if result.returncode != 0 or not match:
            checked.append(f"{candidate}: could not determine Java version")
            continue
        major = int(match.group(1))
        checked.append(f"{candidate}: Java {major}")
        if major == required_major:
            print(f"[JAVA] Java {major}: {candidate}")
            return str(candidate)
        if major > required_major and (fallback is None or major < fallback[1]):
            fallback = (candidate, major)

    if fallback:
        candidate, major = fallback
        print(f"[JAVA] Java {major}: {candidate}")
        return str(candidate)
    details = "\n  ".join(checked) or "No Java executables found."
    raise RuntimeError(
        f"This client requires Java {required_major} or newer. Set JAVA_HOME to a compatible JDK.\n  {details}"
    )


def maven_to_path(coords: str) -> Path | None:
    parts = coords.split(":")
    if len(parts) < 3:
        return None
    group, artifact, version = parts[:3]
    return Path(group.replace(".", "/")) / artifact / version / f"{artifact}-{version}.jar"


def current_os_name() -> str:
    system = platform.system()
    if system == "Windows":
        return "windows"
    if system == "Darwin":
        return "osx"
    return "linux"


def os_rule_allows(rules: list[dict]) -> bool:
    if not rules:
        return True

    current_os = current_os_name()
    result = False
    for rule in rules:
        if rule.get("features"):
            continue

        action = rule.get("action") == "allow"
        os_info = rule.get("os", {})
        os_name = os_info.get("name")
        if not os_info or os_name == current_os:
            result = action
        elif not action:
            result = True
    return result


def feature_rule_allows(rules: list[dict], features: dict[str, bool]) -> bool:
    if not rules:
        return True

    result = False
    for rule in rules:
        action = rule.get("action") == "allow"
        required = rule.get("features", {})
        if not required or all(bool(features.get(key, False)) == bool(value) for key, value in required.items()):
            result = action
    return result


def argument_rules_allow(rules: list[dict], features: dict[str, bool]) -> bool:
    os_rules = [rule for rule in rules if rule.get("os")]
    feature_rules = [rule for rule in rules if not rule.get("os")]
    return (not os_rules or os_rule_allows(os_rules)) and feature_rule_allows(feature_rules, features)


def replace_tokens(value: str, replacements: dict[str, str]) -> str:
    for token, replacement in replacements.items():
        value = value.replace(token, replacement)
    return value


def iter_argument_values(value: str | list[str]) -> list[str]:
    return value if isinstance(value, list) else [value]


def instance_name(spec: PlatformSpec, mc_version: str) -> str:
    return f"{mc_version}-{spec.instance_suffix}"


def instance_dir(spec: PlatformSpec, mc_version: str) -> Path:
    return VERSIONS_DIR / instance_name(spec, mc_version)


def resolve_version_json(spec: PlatformSpec, mc_version: str) -> Path:
    path = instance_dir(spec, mc_version) / f"{instance_name(spec, mc_version)}.json"
    if not path.exists():
        raise FileNotFoundError(f"Version json not found: {path}")
    return path


def build_classpath(version_data: dict, instance: Path) -> list[str]:
    entries: list[str] = []
    for library in version_data.get("libraries", []):
        if not os_rule_allows(library.get("rules", [])):
            continue

        artifact = library.get("downloads", {}).get("artifact", {})
        if artifact.get("path"):
            full_path = LIBRARIES_DIR / Path(artifact["path"])
        else:
            maven_path = maven_to_path(library.get("name", ""))
            if maven_path is None:
                continue
            full_path = LIBRARIES_DIR / maven_path

        if full_path.exists():
            entries.append(str(full_path))

    client_jar = instance / f"{version_data['id']}.jar"
    if client_jar.exists():
        entries.append(str(client_jar))
    return entries


def build_jvm_args(version_data: dict, instance: Path, classpath: str) -> list[str]:
    version_id = version_data["id"]
    replacements = {
        "${natives_directory}": str(instance / "natives-windows-x86_64"),
        "${launcher_name}": "ponderer-quickplay",
        "${launcher_version}": "1.0",
        "${classpath}": classpath,
        "${library_directory}": str(LIBRARIES_DIR),
        "${classpath_separator}": os.pathsep,
        "${version_name}": version_id,
        "${primary_jar_name}": f"{version_id}.jar",
    }

    args = ["-Xmx4G", "-Xms512M", *UTF8_JVM_ARGS]
    if platform.system() == "Windows":
        args.append(windows_java_ipc_arg())
    for entry in version_data.get("arguments", {}).get("jvm", []):
        if isinstance(entry, str):
            args.append(replace_tokens(entry, replacements))
        elif isinstance(entry, dict) and os_rule_allows(entry.get("rules", [])):
            for item in iter_argument_values(entry.get("value", [])):
                args.append(replace_tokens(item, replacements))

    log4j_config = instance / "log4j2.xml"
    if log4j_config.exists():
        args.append(f"-Dlog4j.configurationFile={log4j_config}")
    return args


def build_game_args(version_data: dict, instance: Path, world: str, width: int, height: int) -> list[str]:
    asset_index = version_data.get("assetIndex", {}).get("id", version_data.get("assets", "5"))
    replacements = {
        "${auth_player_name}": "Dev",
        "${version_name}": version_data["id"],
        "${game_directory}": str(instance),
        "${assets_root}": str(ASSETS_DIR),
        "${assets_index_name}": str(asset_index),
        "${auth_uuid}": DEFAULT_PLAYER_UUID,
        "${auth_access_token}": "0",
        "${clientid}": "0",
        "${auth_xuid}": "0",
        "${user_type}": "legacy",
        "${version_type}": "release",
        "${resolution_width}": str(width),
        "${resolution_height}": str(height),
        "${quickPlayPath}": "quickPlay/ponderer.json",
        "${quickPlaySingleplayer}": world,
        "${quickPlayMultiplayer}": "",
        "${quickPlayRealms}": "",
        "${auth_session}": "0",
        "${user_properties}": "{}",
        "${profile_properties}": "{}",
    }
    features = {
        "has_custom_resolution": True,
        "is_demo_user": False,
        "has_quick_plays_support": True,
        "is_quick_play_singleplayer": True,
        "is_quick_play_multiplayer": False,
        "is_quick_play_realms": False,
    }

    args: list[str] = []
    for entry in version_data.get("arguments", {}).get("game", []):
        if isinstance(entry, str):
            args.append(replace_tokens(entry, replacements))
        elif isinstance(entry, dict) and argument_rules_allow(entry.get("rules", []), features):
            for item in iter_argument_values(entry.get("value", [])):
                args.append(replace_tokens(item, replacements))
    return args


def newest_world(instance: Path) -> str:
    saves = instance / "saves"
    worlds = [path for path in saves.glob("*") if path.is_dir() and (path / "level.dat").exists()]
    if not worlds:
        raise FileNotFoundError(f"No worlds found under {saves}")
    return max(worlds, key=lambda path: (path / "level.dat").stat().st_mtime).name


def resolve_world(instance: Path, requested_world: str | None) -> str:
    world = requested_world or newest_world(instance)
    if not (instance / "saves" / world / "level.dat").exists():
        raise FileNotFoundError(f"World not found: {instance / 'saves' / world}")
    return world


def world_is_locked(instance: Path, world: str) -> bool:
    """Probe the existing session lock without changing the save or creating a lock file."""
    try:
        handle = (instance / "saves" / world / "session.lock").open("r+b")
    except FileNotFoundError:
        return False
    except OSError as exc:
        if getattr(exc, "winerror", None) in (32, 33):
            return True
        raise

    with handle:
        if os.name == "nt":
            import msvcrt

            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError as exc:
                if exc.errno in (errno.EACCES, errno.EAGAIN, errno.EDEADLK):
                    return True
                raise
            msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl

            try:
                fcntl.lockf(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                return True
            fcntl.lockf(handle, fcntl.LOCK_UN)
    return False


def run_build(targets: list[PlatformSpec], offline: bool) -> None:
    gradlew = PROJECT_ROOT / ("gradlew.bat" if platform.system() == "Windows" else "gradlew")
    if not gradlew.exists():
        gradlew = PROJECT_ROOT / "gradlew"

    tasks = list(dict.fromkeys(f":{target.module}:build" for target in targets))
    command = [str(gradlew)]
    if offline:
        command.append("--offline")
    command.extend(gradle_encoding_args())
    command.extend(tasks)

    print(f"[BUILD] {' '.join(command[1:])}")
    subprocess.run(command, cwd=PROJECT_ROOT, env=gradle_subprocess_env(), check=True)


def find_mod_jar(spec: PlatformSpec, props: dict[str, str]) -> Path:
    mod_id = props.get("mod_id", "ponderer")
    mc_version = props["minecraft_version"]
    mod_version = props["mod_version"]
    libs = PROJECT_ROOT / spec.module / "build" / "libs"
    base_name = f"{mod_id}-{mc_version}-{spec.loader}-{mod_version}"

    exact_name = f"{base_name}-all.jar" if spec.prefer_all_jar else f"{base_name}.jar"
    exact_path = libs / exact_name
    if exact_path.exists():
        return exact_path

    patterns = [str(libs / f"{base_name}*.jar"), str(libs / f"{mod_id}-{mc_version}-{spec.loader}-*.jar")]
    candidates = [Path(path) for pattern in patterns for path in glob.glob(pattern)]
    candidates = [path for path in candidates if not path.name.endswith("-sources.jar")]
    if spec.prefer_all_jar:
        all_jars = [path for path in candidates if path.name.endswith("-all.jar")]
        candidates = all_jars or candidates
    else:
        plain_jars = [
            path
            for path in candidates
            if not any(path.name.endswith(suffix) for suffix in ("-all.jar", "-shadow.jar", "-slim.jar"))
        ]
        candidates = plain_jars or candidates

    if not candidates:
        raise FileNotFoundError(f"Built mod jar not found in {libs}")
    return max(candidates, key=lambda path: path.stat().st_mtime)


def copy_mod_jar(spec: PlatformSpec, props: dict[str, str], instance: Path) -> Path:
    jar_path = find_mod_jar(spec, props)
    mods_dir = instance / "mods"
    mods_dir.mkdir(exist_ok=True)

    mod_id = props.get("mod_id", "ponderer")
    mc_version = props["minecraft_version"]
    destination = mods_dir / jar_path.name
    # Never overwrite a jar in place: a running client's ZipFile keeps entry
    # offsets from the original archive and cannot safely read the new bytes.
    staged_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(dir=mods_dir, suffix=".tmp", delete=False) as staged:
            staged_path = Path(staged.name)
        shutil.copy2(jar_path, staged_path)
        for existing in mods_dir.glob(f"{mod_id}-{mc_version}-{spec.loader}-*.jar"):
            if existing != destination:
                existing.unlink()
        os.replace(staged_path, destination)
    except PermissionError:
        raise RuntimeError(
            f"Could not update mod jar in {mods_dir}. Close Minecraft for this instance and retry."
        ) from None
    finally:
        if staged_path is not None:
            staged_path.unlink(missing_ok=True)
    return destination


def latest_log_candidates(instance: Path) -> list[Path]:
    return [instance / "logs" / "latest.log", DOT_MINECRAFT / "logs" / "latest.log"]


def capture_log_cursor(path: Path) -> LogCursor:
    if not path.exists():
        return LogCursor(False)
    stat = path.stat()
    return LogCursor(True, stat.st_size, stat.st_mtime_ns)


def read_log_since(path: Path, cursor: LogCursor) -> str:
    try:
        stat = path.stat()
        start = 0
        if cursor.exists and stat.st_mtime_ns == cursor.mtime_ns and stat.st_size >= cursor.size:
            start = cursor.size

        with path.open("rb") as handle:
            handle.seek(start)
            return handle.read().decode("utf-8", errors="replace")
    except OSError:
        return ""


def read_smoke_log(instance: Path, cursors: dict[Path, LogCursor]) -> tuple[Path, str]:
    candidates = latest_log_candidates(instance)
    for path in candidates:
        text = read_log_since(path, cursors[path])
        if text.strip():
            return path, text
    return candidates[0], ""


def entered_world(log_text: str) -> bool:
    return any(pattern.search(log_text) for pattern in SUCCESS_PATTERNS)


def smoke_outcome(log_text: str) -> bool | None:
    """True once world entry or an occupied world is logged, None while pending."""
    if WORLD_LOCKED_PATTERN.search(log_text) or entered_world(log_text):
        return True
    return None


def extract_error_excerpt(log_text: str, max_lines: int) -> str:
    lines = log_text.splitlines()
    if not lines:
        return "No new log output was written during the smoke window."

    excerpt: list[str] = []
    index = 0
    blocks = 0
    while index < len(lines) and len(excerpt) < max_lines and blocks < 3:
        if not ERROR_PATTERN.search(lines[index]):
            index += 1
            continue

        start = max(0, index - 2)
        end = index + 1
        while end < len(lines) and end - index < 80:
            line = lines[end]
            if (
                line.startswith((" ", "\t"))
                or line.startswith(("Caused by:", "Suppressed:", "..."))
                or ERROR_PATTERN.search(line)
            ):
                end += 1
                continue
            break

        excerpt.extend(lines[start:end])
        excerpt.append("")
        blocks += 1
        index = end

    if excerpt:
        return "\n".join(excerpt[:max_lines])
    return "\n".join(lines[-max_lines:])


class WindowsSmokeProcess:
    """A native child whose job also kills descendants if the launcher exits."""

    def __init__(self, api, handle, job, pid: int):
        self.api, self.handle, self.job, self.pid = api, handle, job, pid

    def poll(self) -> int | None:
        import ctypes
        from ctypes import wintypes as w

        status = self.api.WaitForSingleObject(self.handle, 0)
        if status == 0xFFFFFFFF:  # WAIT_FAILED
            raise ctypes.WinError(ctypes.get_last_error())
        if status == 258:  # WAIT_TIMEOUT
            return None
        code = w.DWORD()
        if not self.api.GetExitCodeProcess(self.handle, ctypes.byref(code)):
            raise ctypes.WinError(ctypes.get_last_error())
        return code.value

    def wait(self, timeout: float = 5) -> int:
        deadline = time.monotonic() + timeout
        while (code := self.poll()) is None:
            if time.monotonic() >= deadline:
                raise subprocess.TimeoutExpired(str(self.pid), timeout)
            time.sleep(0.05)
        return code

    def close(self) -> None:
        # KILL_ON_JOB_CLOSE covers children even when the root already exited.
        if self.job:
            self.api.CloseHandle(self.job)
            self.job = None
        if self.handle:
            try:
                self.wait()
            finally:
                self.api.CloseHandle(self.handle)
                self.handle = None


class WindowsSmokeDesktop:
    """Smoke-only Win32 desktop, with a tray entry and a safe return control.

    Native desktops: https://learn.microsoft.com/windows/win32/winstation/desktops
    No SwitchDesktop call is made until the user clicks or presses the hotkey.
    """

    def __init__(self, label: str):
        import ctypes as c
        from ctypes import wintypes as w

        self.c, self.w = c, w
        self.user = c.WinDLL("user32", use_last_error=True)
        self.kernel = c.WinDLL("kernel32", use_last_error=True)
        self.shell = c.WinDLL("shell32", use_last_error=True)
        self.label = label
        self.name = f"PondererSmoke-{os.getpid()}-{time.monotonic_ns()}"
        self.home = self.desktop = None
        self.stopping = threading.Event()
        self.switch_lock = threading.Lock()
        self.threads: list[threading.Thread] = []
        self.windows: dict[str, int] = {}
        self.callbacks = []
        self.process_pid: int | None = None
        self.input_error: Exception | None = None
        self.input_wake = threading.Event()
        self.disabled_windows: set[int] = set()
        self.game_rects: dict[int, tuple[int, int, int, int]] = {}

        self.WndProc = c.WINFUNCTYPE(c.c_ssize_t, w.HWND, w.UINT, w.WPARAM, w.LPARAM)
        self.EnumWindowProc = c.WINFUNCTYPE(w.BOOL, w.HWND, w.LPARAM)

        class WindowClass(c.Structure):
            _fields_ = [("style", w.UINT), ("lpfnWndProc", self.WndProc),
                        ("cbClsExtra", c.c_int), ("cbWndExtra", c.c_int),
                        ("hInstance", w.HINSTANCE), ("hIcon", w.HICON),
                        ("hCursor", w.HANDLE), ("hbrBackground", w.HANDLE),
                        ("lpszMenuName", w.LPCWSTR), ("lpszClassName", w.LPCWSTR)]

        class NotifyIcon(c.Structure):
            _fields_ = [("cbSize", w.DWORD), ("hWnd", w.HWND), ("uID", w.UINT),
                        ("uFlags", w.UINT), ("uCallbackMessage", w.UINT),
                        ("hIcon", w.HICON), ("szTip", w.WCHAR * 128),
                        ("dwState", w.DWORD), ("dwStateMask", w.DWORD),
                        ("szInfo", w.WCHAR * 256), ("uVersion", w.UINT),
                        ("szInfoTitle", w.WCHAR * 64), ("dwInfoFlags", w.DWORD),
                        ("guidItem", c.c_byte * 16), ("hBalloonIcon", w.HICON)]

        class StartupInfo(c.Structure):
            _fields_ = [("cb", w.DWORD), ("lpReserved", w.LPWSTR),
                        ("lpDesktop", w.LPWSTR), ("lpTitle", w.LPWSTR),
                        *[(name, w.DWORD) for name in ("dwX", "dwY", "dwXSize", "dwYSize",
                            "dwXCountChars", "dwYCountChars", "dwFillAttribute", "dwFlags")],
                        ("wShowWindow", w.WORD), ("cbReserved2", w.WORD),
                        ("lpReserved2", c.c_void_p), ("hStdInput", w.HANDLE),
                        ("hStdOutput", w.HANDLE), ("hStdError", w.HANDLE)]

        class ProcessInfo(c.Structure):
            _fields_ = [("hProcess", w.HANDLE), ("hThread", w.HANDLE),
                        ("dwProcessId", w.DWORD), ("dwThreadId", w.DWORD)]

        class JobBasicLimits(c.Structure):
            _fields_ = [("PerProcessUserTimeLimit", c.c_int64), ("PerJobUserTimeLimit", c.c_int64),
                        ("LimitFlags", w.DWORD), ("MinimumWorkingSetSize", c.c_size_t),
                        ("MaximumWorkingSetSize", c.c_size_t), ("ActiveProcessLimit", w.DWORD),
                        ("Affinity", c.c_size_t), ("PriorityClass", w.DWORD),
                        ("SchedulingClass", w.DWORD)]

        class JobLimits(c.Structure):
            _fields_ = [("BasicLimitInformation", JobBasicLimits), ("IoInfo", c.c_uint64 * 6),
                        ("ProcessMemoryLimit", c.c_size_t), ("JobMemoryLimit", c.c_size_t),
                        ("PeakProcessMemoryUsed", c.c_size_t), ("PeakJobMemoryUsed", c.c_size_t)]

        self.WindowClass, self.NotifyIcon = WindowClass, NotifyIcon
        self.StartupInfo, self.ProcessInfo, self.JobLimits = StartupInfo, ProcessInfo, JobLimits

        def bind(dll, name, result, *args):
            fn = getattr(dll, name)
            fn.restype, fn.argtypes = result, args
            return fn

        u, k, s = self.user, self.kernel, self.shell
        bind(u, "OpenInputDesktop", w.HANDLE, w.DWORD, w.BOOL, w.DWORD)
        bind(u, "CreateDesktopW", w.HANDLE, w.LPCWSTR, c.c_void_p, c.c_void_p,
             w.DWORD, w.DWORD, c.c_void_p)
        bind(u, "CloseDesktop", w.BOOL, w.HANDLE)
        bind(u, "SwitchDesktop", w.BOOL, w.HANDLE)
        bind(u, "SetThreadDesktop", w.BOOL, w.HANDLE)
        bind(u, "GetUserObjectInformationW", w.BOOL, w.HANDLE, c.c_int,
             c.c_void_p, w.DWORD, c.POINTER(w.DWORD))
        bind(u, "RegisterClassW", w.WORD, c.POINTER(WindowClass))
        bind(u, "UnregisterClassW", w.BOOL, w.LPCWSTR, w.HINSTANCE)
        bind(u, "CreateWindowExW", w.HWND, w.DWORD, w.LPCWSTR, w.LPCWSTR, w.DWORD,
             c.c_int, c.c_int, c.c_int, c.c_int, w.HWND, w.HMENU, w.HINSTANCE, c.c_void_p)
        bind(u, "DefWindowProcW", c.c_ssize_t, w.HWND, w.UINT, w.WPARAM, w.LPARAM)
        bind(u, "DestroyWindow", w.BOOL, w.HWND)
        bind(u, "PostMessageW", w.BOOL, w.HWND, w.UINT, w.WPARAM, w.LPARAM)
        bind(u, "PostQuitMessage", None, c.c_int)
        bind(u, "GetMessageW", c.c_int, c.POINTER(w.MSG), w.HWND, w.UINT, w.UINT)
        bind(u, "TranslateMessage", w.BOOL, c.POINTER(w.MSG))
        bind(u, "DispatchMessageW", c.c_ssize_t, c.POINTER(w.MSG))
        bind(u, "LoadIconW", w.HICON, w.HINSTANCE, c.c_void_p)
        bind(u, "LoadCursorW", w.HANDLE, w.HINSTANCE, c.c_void_p)
        bind(u, "RegisterHotKey", w.BOOL, w.HWND, c.c_int, w.UINT, w.UINT)
        bind(u, "UnregisterHotKey", w.BOOL, w.HWND, c.c_int)
        bind(u, "RegisterWindowMessageW", w.UINT, w.LPCWSTR)
        bind(u, "EnumDesktopWindows", w.BOOL, w.HANDLE, self.EnumWindowProc, w.LPARAM)
        bind(u, "GetWindowThreadProcessId", w.DWORD, w.HWND, c.POINTER(w.DWORD))
        bind(u, "GetForegroundWindow", w.HWND)
        bind(u, "GetClientRect", w.BOOL, w.HWND, c.POINTER(w.RECT))
        bind(u, "ClientToScreen", w.BOOL, w.HWND, c.POINTER(w.POINT))
        bind(u, "GetSystemMetrics", c.c_int, c.c_int)
        bind(u, "IsWindowEnabled", w.BOOL, w.HWND)
        bind(u, "EnableWindow", w.BOOL, w.HWND, w.BOOL)
        bind(u, "GetClipCursor", w.BOOL, c.POINTER(w.RECT))
        bind(u, "ClipCursor", w.BOOL, c.POINTER(w.RECT))
        bind(u, "SetThreadDpiAwarenessContext", w.HANDLE, w.HANDLE)
        bind(s, "Shell_NotifyIconW", w.BOOL, w.DWORD, c.POINTER(NotifyIcon))
        bind(k, "GetModuleHandleW", w.HINSTANCE, w.LPCWSTR)
        bind(k, "GetCurrentProcess", w.HANDLE)
        bind(k, "DuplicateHandle", w.BOOL, w.HANDLE, w.HANDLE, w.HANDLE,
             c.POINTER(w.HANDLE), w.DWORD, w.BOOL, w.DWORD)
        bind(k, "CloseHandle", w.BOOL, w.HANDLE)
        bind(k, "CreateProcessW", w.BOOL, w.LPCWSTR, w.LPWSTR, c.c_void_p,
             c.c_void_p, w.BOOL, w.DWORD, c.c_void_p, w.LPCWSTR,
             c.POINTER(StartupInfo), c.POINTER(ProcessInfo))
        bind(k, "CreateJobObjectW", w.HANDLE, c.c_void_p, w.LPCWSTR)
        bind(k, "SetInformationJobObject", w.BOOL, w.HANDLE, c.c_int, c.c_void_p, w.DWORD)
        bind(k, "AssignProcessToJobObject", w.BOOL, w.HANDLE, w.HANDLE)
        bind(k, "ResumeThread", w.DWORD, w.HANDLE)
        bind(k, "TerminateProcess", w.BOOL, w.HANDLE, w.UINT)
        bind(k, "WaitForSingleObject", w.DWORD, w.HANDLE, w.DWORD)
        bind(k, "GetExitCodeProcess", w.BOOL, w.HANDLE, c.POINTER(w.DWORD))

        try:
            self.home = self.checked(u.OpenInputDesktop(0, False, 0x0103))
            self.desktop = self.checked(u.CreateDesktopW(self.name, None, None, 0, 0x01FF, None))
        except BaseException:
            self.close()
            raise

    def checked(self, value):
        if not value:
            raise self.c.WinError(self.c.get_last_error())
        return value

    def desktop_name(self, handle) -> str:
        buffer = self.c.create_unicode_buffer(256)
        needed = self.w.DWORD()
        self.checked(self.user.GetUserObjectInformationW(
            handle, 2, buffer, self.c.sizeof(buffer), self.c.byref(needed)))  # UOI_NAME
        return buffer.value

    def viewing_smoke(self) -> bool:
        current = self.user.OpenInputDesktop(0, False, 0x0001)
        if not current:  # For example, the workstation is locked; do not switch it.
            return False
        try:
            return self.desktop_name(current) == self.name
        finally:
            self.user.CloseDesktop(current)

    def switch(self, show_smoke: bool) -> None:
        with self.switch_lock:
            if self.stopping.is_set():
                return
            if not self.user.SwitchDesktop(self.desktop if show_smoke else self.home):
                print(f"[SMOKE][WINDOW] Desktop switch failed: {self.c.WinError(self.c.get_last_error())}")
            elif not show_smoke:
                self.release_mouse_clip()
            self.input_wake.set()

    def release_mouse_clip(self) -> None:
        # ClipCursor is shared by the window station, not isolated by a private
        # desktop. Only release a rectangle belonging to this smoke's windows;
        # never repeatedly clear another foreground application's mouse grab.
        # https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-clipcursor
        u, c, w = self.user, self.c, self.w
        previous_dpi = u.SetThreadDpiAwarenessContext(w.HANDLE(-4))  # PER_MONITOR_AWARE_V2
        try:
            rect = w.RECT()
            if u.GetClipCursor(c.byref(rect)):
                clip = (rect.left, rect.top, rect.right, rect.bottom)
                if clip in tuple(self.game_rects.values()):
                    foreground = u.GetForegroundWindow()
                    pid = w.DWORD()
                    u.GetWindowThreadProcessId(foreground, c.byref(pid))
                    if pid.value != self.process_pid and self.client_clip_rect(foreground) == clip:
                        # Another visible game can legitimately clip to the
                        # same-sized client area; preserve its input ownership.
                        return
                    self.checked(u.ClipCursor(None))
                    print(f"[SMOKE][INPUT] Released smoke mouse clip: {clip}")
        finally:
            if previous_dpi:
                u.SetThreadDpiAwarenessContext(previous_dpi)

    def client_clip_rect(self, hwnd) -> tuple[int, int, int, int] | None:
        u, c, w = self.user, self.c, self.w
        rect = w.RECT()
        if not u.GetClientRect(hwnd, c.byref(rect)):
            return None
        start, end = w.POINT(rect.left, rect.top), w.POINT(rect.right, rect.bottom)
        if not u.ClientToScreen(hwnd, c.byref(start)) or not u.ClientToScreen(hwnd, c.byref(end)):
            return None
        x, y, width, height = (u.GetSystemMetrics(n) for n in (76, 77, 78, 79))
        return (max(start.x, x), max(start.y, y), min(end.x, x + width), min(end.y, y + height))

    def guard_input(self, ready: threading.Event) -> None:
        """Keep inactive smoke windows from owning focus/capture in their desktop."""
        u, c, w = self.user, self.c, self.w
        previous_dpi = None
        try:
            self.checked(u.SetThreadDesktop(self.desktop))
            previous_dpi = u.SetThreadDpiAwarenessContext(w.HANDLE(-4))
            ready.set()
            while not self.stopping.is_set():
                owned = []

                @self.EnumWindowProc
                def collect(hwnd, _):
                    pid = w.DWORD()
                    u.GetWindowThreadProcessId(hwnd, c.byref(pid))
                    if pid.value == self.process_pid:
                        owned.append(hwnd)
                    return True

                c.set_last_error(0)
                enumerated = u.EnumDesktopWindows(self.desktop, collect, 0)
                if not enumerated and c.get_last_error():
                    raise c.WinError(c.get_last_error())
                # A new desktop can return zero with no error before Java has
                # created its first window; that is an empty enumeration.
                interactive = self.viewing_smoke()
                for hwnd in owned:
                    bounds = self.client_clip_rect(hwnd)
                    x, y, width, height = (u.GetSystemMetrics(n) for n in (76, 77, 78, 79))
                    # A full-screen rectangle imposes no restriction.
                    if (bounds is not None and bounds[0] < bounds[2] and bounds[1] < bounds[3]
                            and bounds != (x, y, x + width, y + height)):
                        self.game_rects[hwnd] = bounds
                    if interactive:
                        if hwnd in self.disabled_windows:
                            u.EnableWindow(hwnd, True)
                            self.disabled_windows.discard(hwnd)
                    elif u.IsWindowEnabled(hwnd):
                        # Disabling removes native focus and prevents GLFW from
                        # reacquiring it while Minecraft enters the world. This
                        # leaves rendering and the unpaused world running.
                        u.EnableWindow(hwnd, False)
                        self.disabled_windows.add(hwnd)
                        # Also clear GLFW's remembered disabled-cursor state.
                        u.PostMessageW(hwnd, 0x0008, 0, 0)  # WM_KILLFOCUS
                        print(f"[SMOKE][INPUT] Background input disabled: PID {self.process_pid}, HWND {hwnd}")
                if not interactive:
                    self.release_mouse_clip()
                self.input_wake.wait(0.05)
                self.input_wake.clear()
        except Exception as exc:
            self.input_error = exc
        finally:
            if previous_dpi:
                u.SetThreadDpiAwarenessContext(previous_dpi)
            ready.set()

    def show_controls(self, pid: int) -> None:
        # A private desktop has no Explorer/tray. Always create a return button
        # there before enabling the tray entry on the user's original desktop.
        for role in ("return", "tray"):
            ready = threading.Event()
            errors = []
            thread = threading.Thread(target=self.control_loop, args=(role, pid, ready, errors),
                                      name=f"smoke-{role}", daemon=True)
            self.threads.append(thread)
            thread.start()
            if not ready.wait(5):
                raise RuntimeError(f"Timed out creating smoke {role} control")
            if errors:
                if role == "return":
                    raise RuntimeError(f"Cannot create smoke return control: {errors[0]}")
                print(f"[SMOKE][TRAY] Unavailable; continuing silently: {errors[0]}")

    def control_loop(self, role: str, pid: int, ready: threading.Event, errors: list) -> None:
        c, w, u = self.c, self.w, self.user
        hwnd = None
        registered = hotkey = icon_added = False
        instance = self.kernel.GetModuleHandleW(None)
        class_name = f"{self.name}-{role}"
        icon = self.NotifyIcon()
        taskbar_created = u.RegisterWindowMessageW("TaskbarCreated")

        def wndproc(window, message, wp, lp):
            try:
                if message == 0x8001 and (lp & 0xFFFF) in (0x0202, 0x0400, 0x0401):
                    self.switch(True)  # Tray click / keyboard activation.
                elif message == 0x0312:  # WM_HOTKEY: Ctrl+Alt+F10 returns to work.
                    self.switch(False)
                elif message == 0x0111 and (wp & 0xFFFF) == 1:  # Return button.
                    self.switch(False)
                elif message == 0x0010:  # Closing the return panel returns too.
                    if not self.stopping.is_set():
                        self.switch(False)
                    else:
                        u.DestroyWindow(window)
                elif message == 0x0002:
                    u.PostQuitMessage(0)
                elif message == taskbar_created and role == "tray":
                    self.shell.Shell_NotifyIconW(0, c.byref(icon))
                else:
                    return u.DefWindowProcW(window, message, wp, lp)
            except Exception as exc:
                print(f"[SMOKE][WINDOW] {exc}")
            return 0

        callback = self.WndProc(wndproc)
        self.callbacks.append(callback)  # Keep native callbacks alive through window destruction.
        try:
            self.checked(u.SetThreadDesktop(self.home if role == "tray" else self.desktop))
            window_class = self.WindowClass()
            window_class.lpfnWndProc = callback
            window_class.hInstance = instance
            window_class.hIcon = u.LoadIconW(None, 32512)  # Shared application icon.
            window_class.hCursor = u.LoadCursorW(None, 32512)
            window_class.hbrBackground = 16  # COLOR_BTNFACE + 1
            window_class.lpszClassName = class_name
            self.checked(u.RegisterClassW(c.byref(window_class)))
            registered = True
            # Return panel stays above the game without activating it. The tray
            # owner is invisible and never takes focus on the original desktop.
            hwnd = self.checked(u.CreateWindowExW(
                0x08000088 if role == "return" else 0x80, class_name, "Ponderer smoke",
                0x90800000 if role == "return" else 0, 16, 16, 440, 44,
                None, None, instance, None))
            self.windows[role] = hwnd
            if role == "return":
                self.checked(u.CreateWindowExW(0, "BUTTON",
                    "返回工作桌面 / Return (Ctrl+Alt+F10)", 0x50000000,
                    0, 0, 438, 42, hwnd, 1, instance, None))
            else:
                icon.cbSize, icon.hWnd, icon.uID = c.sizeof(icon), hwnd, 1
                icon.uFlags, icon.uCallbackMessage = 7, 0x8001  # MESSAGE | ICON | TIP
                icon.hIcon = window_class.hIcon
                icon.szTip = f"Ponderer smoke: {self.label} (PID {pid}) — 点击查看；Ctrl+Alt+F10 返回"[:127]
                self.checked(self.shell.Shell_NotifyIconW(0, c.byref(icon)))  # NIM_ADD
                icon_added = True
                print(f"[SMOKE][TRAY] {self.label}: click to view; return button / Ctrl+Alt+F10 to go back")
            if role == "return":
                hotkey = bool(u.RegisterHotKey(hwnd, 1, 0x4003, 0x79))  # NOREPEAT | CTRL | ALT, F10
                if not hotkey:
                    print("[SMOKE][WINDOW] Ctrl+Alt+F10 unavailable; return button remains available")
            ready.set()
            message = w.MSG()
            while not self.stopping.is_set():
                result = u.GetMessageW(c.byref(message), None, 0, 0)
                if result == -1:
                    raise c.WinError(c.get_last_error())
                if result == 0:
                    break
                u.TranslateMessage(c.byref(message))
                u.DispatchMessageW(c.byref(message))
        except Exception as exc:
            errors.append(exc)
        finally:
            if icon_added:
                self.shell.Shell_NotifyIconW(2, c.byref(icon))  # NIM_DELETE
            if hotkey:
                u.UnregisterHotKey(hwnd, 1)
            if hwnd:
                u.DestroyWindow(hwnd)
                self.windows.pop(role, None)
            if registered:
                u.UnregisterClassW(class_name, instance)
            ready.set()

    def launch(self, command: list[str], instance: Path, output) -> WindowsSmokeProcess:
        import msvcrt

        c, w, k = self.c, self.w, self.kernel
        handles = []
        info = self.ProcessInfo()
        job = self.checked(k.CreateJobObjectW(None, None))
        try:
            limits = self.JobLimits()
            limits.BasicLimitInformation.LimitFlags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            self.checked(k.SetInformationJobObject(job, 9, c.byref(limits), c.sizeof(limits)))

            def duplicate(file):
                handle = w.HANDLE()
                current = k.GetCurrentProcess()
                self.checked(k.DuplicateHandle(current, msvcrt.get_osfhandle(file.fileno()),
                                               current, c.byref(handle), 0, True, 2))
                handles.append(handle)
                return handle.value

            startup = self.StartupInfo()
            startup.cb, startup.lpDesktop, startup.dwFlags = c.sizeof(startup), self.name, 0x0100
            with open(os.devnull, "rb") as null_input:
                startup.hStdInput = duplicate(null_input)
            startup.hStdOutput = startup.hStdError = duplicate(output)
            # Suspended creation allows job assignment before any child can escape.
            flags = subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP | 0x0004
            self.checked(k.CreateProcessW(command[0], c.create_unicode_buffer(subprocess.list2cmdline(command)),
                None, None, True, flags, None, str(instance), c.byref(startup), c.byref(info)))
            self.checked(k.AssignProcessToJobObject(job, info.hProcess))
            # Start before Java can create or focus an early/game window.
            self.process_pid = info.dwProcessId
            ready = threading.Event()
            guard = threading.Thread(target=self.guard_input, args=(ready,),
                                     name="smoke-input", daemon=True)
            self.threads.append(guard)
            guard.start()
            if not ready.wait(5):
                raise RuntimeError("Timed out starting smoke input guard")
            if self.input_error is not None:
                raise RuntimeError(f"Cannot guard smoke input: {self.input_error}")
            if k.ResumeThread(info.hThread) == 0xFFFFFFFF:
                raise c.WinError(c.get_last_error())
            process = WindowsSmokeProcess(k, info.hProcess, job, info.dwProcessId)
            info.hProcess = job = None  # Ownership transferred to process.
            return process
        finally:
            if info.hProcess:
                k.TerminateProcess(info.hProcess, 1)
                k.WaitForSingleObject(info.hProcess, 5000)
                k.CloseHandle(info.hProcess)
            if info.hThread:
                k.CloseHandle(info.hThread)
            if job:
                k.CloseHandle(job)
            for handle in handles:
                k.CloseHandle(handle)

    def close(self) -> None:
        try:
            # Serialize shutdown with tray callbacks so a late click cannot
            # switch into a desktop after its return controls have been removed.
            with self.switch_lock:
                self.stopping.set()
                self.input_wake.set()
                if self.desktop and self.home and self.viewing_smoke():
                    self.checked(self.user.SwitchDesktop(self.home))
                self.release_mouse_clip()
        finally:
            for hwnd in list(self.windows.values()):
                self.user.PostMessageW(hwnd, 0x0010, 0, 0)
            for thread in self.threads:
                thread.join(timeout=5)
            for handle in (self.desktop, self.home):
                if handle:
                    self.user.CloseDesktop(handle)
            self.desktop = self.home = None


@contextmanager
def smoke_options(instance: Path):
    """Restore only our temporary keys; retain any other settings saved by the game."""
    path = instance / "options.txt"
    existed = path.exists()
    original = path.read_bytes() if existed else b""
    changed = original
    previous = {}
    for key in (b"pauseOnLostFocus", b"fullscreen"):
        pattern = rb"(?m)^" + key + rb":[^\r\n]*"
        match = re.search(pattern, original)
        previous[key] = match.group() if match else None
        if match:
            changed = re.sub(pattern, key + b":false", changed)
        else:
            changed += (b"" if not changed or changed.endswith(b"\n") else b"\n") + key + b":false\n"
    path.write_bytes(changed)
    try:
        yield
    finally:
        current = path.read_bytes() if path.exists() else b""
        if current == changed:
            if existed:
                path.write_bytes(original)
            else:
                path.unlink(missing_ok=True)
        else:
            for key, value in previous.items():
                pattern = rb"(?m)^" + key + rb":[^\r\n]*"
                if value is None:
                    current = re.sub(pattern + rb"\r?\n?", b"", current)
                elif re.search(pattern, current):
                    current = re.sub(pattern, lambda _match: value, current)
                else:
                    current += (b"" if not current or current.endswith(b"\n") else b"\n") + value + b"\n"
            path.write_bytes(current)


def stop_process_tree(process: subprocess.Popen | WindowsSmokeProcess) -> None:
    if isinstance(process, WindowsSmokeProcess):
        process.close()
        print(f"[SMOKE][CLEANUP] PID {process.pid}: process exited; job closed")
        return
    if process.poll() is not None:
        return

    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        return

    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def build_launch_command(spec: PlatformSpec, mc_version: str, world: str, width: int, height: int) -> tuple[list[str], Path]:
    version_json = resolve_version_json(spec, mc_version)
    instance = version_json.parent
    version_data = json.loads(version_json.read_text(encoding="utf-8"))
    classpath = os.pathsep.join(build_classpath(version_data, instance))
    command = [
        find_java(int(version_data.get("javaVersion", {}).get("majorVersion", 17))),
        *build_jvm_args(version_data, instance, classpath),
        version_data["mainClass"],
        *build_game_args(version_data, instance, world, width, height),
    ]
    return command, instance


def start_client(
    spec: PlatformSpec, mc_version: str, world: str, width: int, height: int,
    smoke_desktop: WindowsSmokeDesktop | None = None,
) -> tuple[subprocess.Popen | WindowsSmokeProcess, Path]:
    command, instance = build_launch_command(spec, mc_version, world, width, height)
    console_log = instance / "logs" / "quickplay-launcher.log"
    console_log.parent.mkdir(parents=True, exist_ok=True)
    with console_log.open("wb") as output:
        if smoke_desktop is not None:
            return smoke_desktop.launch(command, instance, output), instance
        process = subprocess.Popen(
            command,
            cwd=instance,
            stdin=subprocess.DEVNULL,
            stdout=output,
            stderr=subprocess.STDOUT,
            creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if os.name == "nt" else 0,
        )
    return process, instance


def launch(spec: PlatformSpec, mc_version: str, world: str, width: int, height: int, dry_run: bool) -> None:
    if dry_run:
        command, instance = build_launch_command(spec, mc_version, world, width, height)
        print(f"[DRY] {instance.name}")
        print(f"  world: {world}")
        print(f"  args: {len(command)}")
        return

    process, instance = start_client(spec, mc_version, world, width, height)
    print(f"[LAUNCH] {instance.name}")
    print(f"  world: {world}")
    print(f"  pid: {process.pid}")
    console_log = instance / "logs" / "quickplay-launcher.log"
    print(f"  console: {console_log}")
    try:
        exit_code = process.wait(timeout=LAUNCH_STARTUP_CHECK_SECONDS)
    except subprocess.TimeoutExpired:
        return
    excerpt = console_log.read_text(encoding="utf-8", errors="replace")
    raise RuntimeError(
        f"{instance.name} exited during startup (code={exit_code}). See {console_log}\n"
        f"{extract_error_excerpt(excerpt, DEFAULT_SMOKE_LOG_LINES)}"
    )


def smoke_target(
    spec: PlatformSpec,
    mc_version: str,
    world: str,
    width: int,
    height: int,
    timeout: int,
    max_log_lines: int,
    dry_run: bool,
) -> SmokeResult:
    instance = instance_dir(spec, mc_version)
    cursors = {path: capture_log_cursor(path) for path in latest_log_candidates(instance)}

    if dry_run:
        command, _ = build_launch_command(spec, mc_version, world, width, height)
        print(f"[SMOKE][DRY] {instance.name}")
        print(f"  world: {world}")
        print(f"  timeout: {timeout}s")
        print(f"  args: {len(command)}")
        if os.name == "nt":
            print("  smoke window: private Win32 desktop + optional tray; temporary unpaused/windowed settings")
        return SmokeResult(spec.target, True, latest_log_candidates(instance)[0])

    if world_is_locked(instance, world):
        print(f"[SMOKE][SKIP] {instance.name}: world '{world}' is in use; treating as success")
        return SmokeResult(spec.target, True, latest_log_candidates(instance)[0])

    goal = "quickplay world entry"
    print(f"[SMOKE] {instance.name}: waiting {timeout}s for {goal}")
    deadline = time.monotonic() + timeout
    desktop = None
    process = None
    log_path = latest_log_candidates(instance)[0]
    log_text = ""
    try:
        if os.name == "nt":
            desktop = WindowsSmokeDesktop(instance.name)
        with smoke_options(instance) if desktop is not None else nullcontext():
            try:
                # Launch synchronously: no worker can spawn a late, orphaned client
                # after the timeout/desktop/configuration cleanup has already run.
                process, _ = start_client(spec, mc_version, world, width, height, desktop)
                print(f"[SMOKE][LAUNCH] {instance.name}\n  world: {world}\n  pid: {process.pid}")
                if desktop is not None:
                    print(f"[SMOKE][DESKTOP] {desktop.name}: background; no automatic switch")
                    desktop.show_controls(process.pid)
                while time.monotonic() < deadline:
                    if desktop is not None and desktop.input_error is not None:
                        raise RuntimeError(f"Smoke input guard failed: {desktop.input_error}")
                    log_path, log_text = read_smoke_log(instance, cursors)
                    if smoke_outcome(log_text) is not None or process.poll() is not None:
                        break
                    time.sleep(0.1)

                log_path, log_text = read_smoke_log(instance, cursors)
                exit_code = process.poll()
                ok = smoke_outcome(log_text) is True
                if ok and WORLD_LOCKED_PATTERN.search(log_text):
                    print(f"[SMOKE][SKIP] {instance.name}: world '{world}' is in use; treating as success")
                elif ok:
                    print(f"[SMOKE][PASS] {instance.name}: {goal} reached within {timeout}s; "
                          f"waiting {POST_ENTRY_SETTLE_SECONDS:g}s before cleanup")
                    time.sleep(POST_ENTRY_SETTLE_SECONDS)
                    if desktop is not None and desktop.input_error is not None:
                        raise RuntimeError(f"Smoke input guard failed: {desktop.input_error}")
                else:
                    print(f"[SMOKE][FAIL] {instance.name}: {goal} not reached within {timeout}s")
                    if exit_code is not None:
                        print(f"[SMOKE][PROCESS EXIT] code={exit_code}")
                    print(f"[SMOKE][LOG] {log_path}\n{extract_error_excerpt(log_text, max_log_lines)}")
                    console_log = instance / "logs" / "quickplay-launcher.log"
                    if not log_text.strip() and console_log.exists():
                        print(extract_error_excerpt(console_log.read_text(encoding="utf-8", errors="replace"),
                                                    max_log_lines))
                return SmokeResult(spec.target, ok, log_path, exit_code)
            finally:
                if process is not None:
                    stop_process_tree(process)
    except (OSError, RuntimeError) as exc:
        print(f"[SMOKE][FAIL] {instance.name}: {exc}")
        return SmokeResult(spec.target, False, log_path)
    finally:
        if desktop is not None:
            desktop.close()


def run_smoke(
    targets: list[PlatformSpec],
    props: dict[str, str],
    mc_version: str,
    worlds: dict[PlatformSpec, str],
    args: argparse.Namespace,
) -> int:
    print("=== Ponderer Quickplay Smoke ===")
    print(f"  Minecraft: {mc_version}")
    print(f"  Targets: {', '.join(target.target for target in targets)}")
    print(f"  Timeout: {args.smoke_timeout}s")

    if not args.skip_build:
        run_build(targets, offline=not args.online_build)

    launch_targets = []
    for target in targets:
        instance = instance_dir(target, mc_version)
        if not args.dry_run and world_is_locked(instance, worlds[target]):
            print(f"[SMOKE][SKIP] {instance.name}: world '{worlds[target]}' is in use; treating as success")
        else:
            launch_targets.append(target)
    if not launch_targets:
        return 0

    if not args.no_copy:
        for target in launch_targets:
            destination = copy_mod_jar(target, props, instance_dir(target, mc_version))
            print(f"[COPY] {destination}")

    results = [
        smoke_target(
            target,
            mc_version,
            worlds[target],
            args.width,
            args.height,
            args.smoke_timeout,
            args.smoke_log_lines,
            args.dry_run,
        )
        for target in launch_targets
    ]
    return 0 if all(result.ok for result in results) else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Build, copy, and quickplay local Ponderer client instances.")
    parser.add_argument("target", nargs="?", default="both", choices=["fabric", "forge", "neoforge", "both"])
    parser.add_argument("--world", help="Save folder name under each selected instance's saves directory.")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--online-build", action="store_true")
    parser.add_argument("--no-copy", action="store_true")
    parser.add_argument("--dry-run", action="store_true", help="Build/copy as requested, but print launch targets instead of starting clients.")
    parser.add_argument("--smoke", action="store_true", help="Build, quickplay, wait for a world-entry log marker, then clean up; occupied worlds count as success.")
    parser.add_argument("--smoke-timeout", type=int, default=DEFAULT_SMOKE_TIMEOUT)
    parser.add_argument("--smoke-log-lines", type=int, default=DEFAULT_SMOKE_LOG_LINES)
    parser.add_argument("--width", type=int, default=DEFAULT_WIDTH)
    parser.add_argument("--height", type=int, default=DEFAULT_HEIGHT)
    args = parser.parse_args()
    if args.smoke_timeout <= 0:
        parser.error("--smoke-timeout must be greater than 0")
    if args.smoke_log_lines <= 0:
        parser.error("--smoke-log-lines must be greater than 0")

    props = read_gradle_properties()
    mc_version = props["minecraft_version"]
    targets = resolve_targets(args.target)
    worlds = {
        target: resolve_world(instance_dir(target, mc_version), args.world)
        for target in targets
    }

    if args.smoke:
        return run_smoke(targets, props, mc_version, worlds, args)

    print("=== Ponderer Quickplay ===")
    print(f"  Minecraft: {mc_version}")
    print(f"  Targets: {', '.join(target.target for target in targets)}")

    if not args.skip_build:
        run_build(targets, offline=not args.online_build)

    if not args.no_copy:
        for target in targets:
            destination = copy_mod_jar(target, props, instance_dir(target, mc_version))
            print(f"[COPY] {destination}")

    for target in targets:
        launch(target, mc_version, worlds[target], args.width, args.height, args.dry_run)

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        raise SystemExit(1)
