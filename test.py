#!/usr/bin/env python3
"""
Build, copy, and quickplay local Ponderer client instances.
"""

from __future__ import annotations

import argparse
import glob
import io
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

PROJECT_ROOT = Path(__file__).resolve().parent
DOT_MINECRAFT = PROJECT_ROOT / ".minecraft"
VERSIONS_DIR = DOT_MINECRAFT / "versions"
LIBRARIES_DIR = DOT_MINECRAFT / "libraries"
ASSETS_DIR = DOT_MINECRAFT / "assets"

DEFAULT_WIDTH = 1600
DEFAULT_HEIGHT = 900
DEFAULT_SMOKE_TIMEOUT = 40
DEFAULT_SMOKE_LOG_LINES = 160
POST_ENTRY_SETTLE_SECONDS = 2.0

SUCCESS_PATTERNS = (
    re.compile(r"\[Server thread/INFO\]: .+ logged in with entity id "),
    re.compile(r"\[Server thread/INFO\]: .+ joined the game"),
    re.compile(r"\[Server thread/INFO\]: .+加入了游戏"),
)

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


def find_java() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        executable = "java.exe" if platform.system() == "Windows" else "java"
        candidate = Path(java_home) / "bin" / executable
        if candidate.exists():
            return str(candidate)
    return "java"


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

    args = ["-Xmx4G", "-Xms512M"]
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
        "${auth_uuid}": str(uuid.uuid4()).replace("-", ""),
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


def run_build(targets: list[PlatformSpec], offline: bool) -> None:
    gradlew = PROJECT_ROOT / ("gradlew.bat" if platform.system() == "Windows" else "gradlew")
    if not gradlew.exists():
        gradlew = PROJECT_ROOT / "gradlew"

    tasks = list(dict.fromkeys(f":{target.module}:build" for target in targets))
    command = [str(gradlew)]
    if offline:
        command.append("--offline")
    command.extend(tasks)

    print(f"[BUILD] {' '.join(command[1:])}")
    subprocess.run(command, cwd=PROJECT_ROOT, check=True)


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
    for existing in mods_dir.glob(f"{mod_id}-{mc_version}-{spec.loader}-*.jar"):
        if existing != destination:
            try:
                existing.unlink()
            except PermissionError:
                print(f"[WARN] Could not remove locked mod jar: {existing}")

    try:
        shutil.copy2(jar_path, destination)
    except PermissionError:
        if destination.exists():
            print(f"[WARN] Reusing locked mod jar: {destination}")
        else:
            raise
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


def stop_process_tree(process: subprocess.Popen) -> None:
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
        find_java(),
        *build_jvm_args(version_data, instance, classpath),
        version_data["mainClass"],
        *build_game_args(version_data, instance, world, width, height),
    ]
    return command, instance


def start_client(spec: PlatformSpec, mc_version: str, world: str, width: int, height: int) -> tuple[subprocess.Popen, Path]:
    command, instance = build_launch_command(spec, mc_version, world, width, height)
    process = subprocess.Popen(
        command,
        cwd=instance,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if os.name == "nt" else 0,
    )
    return process, instance


def launch(spec: PlatformSpec, mc_version: str, world: str, width: int, height: int, dry_run: bool) -> None:
    command, instance = build_launch_command(spec, mc_version, world, width, height)
    if dry_run:
        print(f"[DRY] {instance.name}")
        print(f"  world: {world}")
        print(f"  args: {len(command)}")
        return

    process, instance = start_client(spec, mc_version, world, width, height)
    print(f"[LAUNCH] {instance.name}")
    print(f"  world: {world}")
    print(f"  pid: {process.pid}")


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
        return SmokeResult(spec.target, True, latest_log_candidates(instance)[0])

    timer_done = threading.Event()
    stop_timer = threading.Event()
    launched = threading.Event()
    process_holder: dict[str, subprocess.Popen] = {}
    launch_error: dict[str, Exception] = {}

    def timer_thread() -> None:
        stop_timer.wait(timeout)
        timer_done.set()

    def quickplay_thread() -> None:
        try:
            process, _instance = start_client(spec, mc_version, world, width, height)
            process_holder["process"] = process
            print(f"[SMOKE][LAUNCH] {_instance.name}")
            print(f"  world: {world}")
            print(f"  pid: {process.pid}")
        except Exception as exc:
            launch_error["error"] = exc
            timer_done.set()
        finally:
            launched.set()

    print(f"[SMOKE] {instance.name}: waiting {timeout}s for quickplay world entry")
    timer = threading.Thread(target=timer_thread, name=f"smoke-timer-{spec.target}")
    quickplay = threading.Thread(target=quickplay_thread, name=f"smoke-quickplay-{spec.target}")
    timer.start()
    quickplay.start()

    try:
        log_path = latest_log_candidates(instance)[0]
        log_text = ""
        ok = False
        while not timer_done.wait(0.1):
            log_path, log_text = read_smoke_log(instance, cursors)
            if log_text and entered_world(log_text):
                ok = True
                break
            if "error" in launch_error:
                break

        final_log_path, final_log_text = read_smoke_log(instance, cursors)
        if final_log_text.strip():
            log_path = final_log_path
            log_text = final_log_text
        ok = ok or bool(log_text and entered_world(log_text))

        launched.wait(timeout=1)
        process = process_holder.get("process")
        exit_code = process.poll() if process else None

        if ok:
            print(
                f"[SMOKE][PASS] {instance.name}: entered world within {timeout}s; "
                f"waiting {POST_ENTRY_SETTLE_SECONDS:g}s before cleanup"
            )
            time.sleep(POST_ENTRY_SETTLE_SECONDS)
        else:
            print(f"[SMOKE][FAIL] {instance.name}: did not enter world within {timeout}s")
            if "error" in launch_error:
                print(f"[SMOKE][LAUNCH ERROR] {launch_error['error']}")
            if exit_code is not None:
                print(f"[SMOKE][PROCESS EXIT] code={exit_code}")
            print(f"[SMOKE][LOG] {log_path}")
            print(extract_error_excerpt(log_text, max_log_lines))

        return SmokeResult(spec.target, ok, log_path, exit_code)
    finally:
        process = process_holder.get("process")
        if process:
            stop_process_tree(process)

        stop_timer.set()
        timer.join(timeout=1)
        quickplay.join(timeout=1)


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

    if not args.no_copy:
        for target in targets:
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
        for target in targets
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
    parser.add_argument("--smoke", action="store_true", help="Build, quickplay, wait for a world-entry log marker, then clean up.")
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
