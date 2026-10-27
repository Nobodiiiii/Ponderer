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
import tempfile
from dataclasses import dataclass
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


@dataclass(frozen=True)
class PlatformSpec:
    target: str
    module: str
    loader: str
    instance_suffix: str
    prefer_all_jar: bool = False


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


def main() -> int:
    parser = argparse.ArgumentParser(description="Build, copy, and quickplay local Ponderer client instances.")
    parser.add_argument("target", nargs="?", default="both", choices=["fabric", "forge", "neoforge", "both"])
    parser.add_argument("--world", help="Save folder name under each selected instance's saves directory.")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--online-build", action="store_true")
    parser.add_argument("--no-copy", action="store_true")
    parser.add_argument("--dry-run", action="store_true", help="Build/copy as requested, but print launch targets instead of starting clients.")
    parser.add_argument("--width", type=int, default=DEFAULT_WIDTH)
    parser.add_argument("--height", type=int, default=DEFAULT_HEIGHT)
    args = parser.parse_args()

    props = read_gradle_properties()
    mc_version = props["minecraft_version"]
    targets = resolve_targets(args.target)
    worlds = {
        target: resolve_world(instance_dir(target, mc_version), args.world)
        for target in targets
    }

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
