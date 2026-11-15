#!/usr/bin/env python3
"""Build and sync Ponderer/Lib to Biotech's development dependencies and instance."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import zipfile

PROJECT_ROOT = Path(__file__).resolve().parent


def properties(path: Path) -> dict[str, str]:
    return dict(re.findall(r"(?m)^[ \t]*([\w.]+)[ \t]*=[ \t]*([^\r\n]*?)[ \t]*$", path.read_text(encoding="utf-8")))


def filename_value(props: dict[str, str], key: str) -> str:
    value = props.get(key, "")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+_-]*", value):
        raise ValueError(f"Missing or invalid Gradle property: {key}={value!r}")
    return value


def check_unlocked(paths: list[Path]) -> None:
    if os.name != "nt":
        return
    import ctypes
    from ctypes import wintypes

    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    create = kernel.CreateFileW
    create.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p,
                       wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    create.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    for path in paths:
        if not path.exists():
            continue
        handle = create(str(path), 0x80, 0, None, 3, 0x80, None)  # Read attributes; exclusive sharing.
        if handle == ctypes.c_void_p(-1).value:
            raise OSError(ctypes.get_last_error(), f"File unavailable; close the game or build using it, then retry: {path}")
        kernel.CloseHandle(handle)


def managed_jars(directory: Path, prefixes: tuple[str, ...]) -> list[Path]:
    return sorted(p for p in directory.glob("*.jar") if p.name.startswith(prefixes))


def copy_jars(sources: list[Path], directory: Path, prefixes: tuple[str, ...]) -> None:
    """Stage a complete set, roll back failed replacements, and disable obsolete versions."""
    directory.mkdir(parents=True, exist_ok=True)
    directory = directory.resolve()
    old = managed_jars(directory, prefixes)
    check_unlocked(old)
    names = {p.name for p in sources}
    moved: list[tuple[Path, Path]] = []
    installed: list[Path] = []
    staging = Path(tempfile.mkdtemp(prefix=".ponderer-sync-", dir=directory)).resolve()
    preserve_backups = False
    try:
        for source in sources:
            shutil.copy2(source, staging / source.name)
        try:
            for path in old:
                backup = staging / ("previous-" + path.name)
                os.replace(path, backup)
                moved.append((path, backup))
            for source in sources:
                destination = directory / source.name
                os.replace(staging / source.name, destination)
                installed.append(destination)
            for index, (original, backup) in enumerate(moved):
                if original.name not in names:
                    disabled = original.with_name(original.name + f".disabled-{staging.name}")
                    os.replace(backup, disabled)
                    moved[index] = (original, disabled)
                    print(f"[DISABLED] {disabled}", flush=True)
        except BaseException as error:
            failures = []
            for path in installed:
                try:
                    path.unlink()
                except OSError as restore_error:
                    failures.append(str(restore_error))
            for original, backup in reversed(moved):
                try:
                    os.replace(backup, original)
                except OSError as restore_error:
                    failures.append(str(restore_error))
            if failures:
                preserve_backups = True
                raise RuntimeError(f"Replacement and rollback failed; recovery files kept in {staging}: {'; '.join(failures)}") from error
            raise
    finally:
        if not preserve_backups and staging.parent == directory and staging.name.startswith(".ponderer-sync-"):
            shutil.rmtree(staging)
    for path in installed:
        print(f"[COPY] {path} ({path.stat().st_size} bytes)", flush=True)


def stop_build(process: subprocess.Popen) -> None:
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
    else:
        os.killpg(process.pid, signal.SIGKILL)
    process.wait(timeout=20)


def build(root: Path, tasks: list[str], online: bool, timeout: int, log: Path) -> None:
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(wrapper), *tasks, "--console=plain", "--no-daemon"]
    if not online:
        command.append("--offline")
    log.parent.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["GRADLE_OPTS"] = env.get("GRADLE_OPTS", "") + " -Dfile.encoding=UTF-8"
    print(f"[BUILD] {root}: {' '.join(tasks)}; log: {log}", flush=True)
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=root, env=env, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=os.name != "nt")
        deadline = time.monotonic() + timeout
        try:
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError(f"Build exceeded {timeout}s; log: {log}")
                try:
                    code = process.wait(timeout=min(30, remaining))
                    break
                except subprocess.TimeoutExpired:
                    print(f"[BUILD] Still running; log: {log}", flush=True)
        finally:
            if process.poll() is None:
                stop_build(process)
    if code:
        print(log.read_text(encoding="utf-8", errors="replace")[-6000:], file=sys.stderr)
        raise RuntimeError(f"Build failed ({code}); log: {log}")
    print(f"[OK] {' '.join(tasks)}", flush=True)


def require_jar(path: Path) -> None:
    with zipfile.ZipFile(path) as archive:
        if "META-INF/neoforge.mods.toml" not in archive.namelist():
            raise ValueError(f"Not a NeoForge mod JAR: {path}")


def sync_versions(path: Path, editor: str, library: str) -> None:
    original = path.read_bytes()
    text = original.decode("utf-8")
    for key, value in {"ponderer_version": editor, "ponderer_lib_version": library}.items():
        text, count = re.subn(rf"(?m)^([ \t]*{key}[ \t]*=[ \t]*)[^\r\n]*", rf"\g<1>{value}", text)
        if count != 1:
            raise ValueError(f"Expected one {key} entry in {path}")
    if text.encode("utf-8") != original:
        path.write_bytes(text.encode("utf-8"))
        print(f"[VERSIONS] {path}: Ponderer {editor}, Lib {library}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--biotech", type=Path, default=PROJECT_ROOT.parent / "create_biotech",
                        help="Biotech project root (default: adjacent create_biotech)")
    parser.add_argument("--instance", type=Path, help="Instance directory containing mods/ (default: Biotech .minecraft/versions/<MC>-NeoForge)")
    parser.add_argument("--skip-instance", action="store_true", help="Update development libs and staging only")
    parser.add_argument("--online", action="store_true", help="Allow Gradle dependency downloads (default: offline)")
    parser.add_argument("--timeout", type=int, default=900, help="Timeout per build in seconds (default: 900)")
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be greater than zero")
    if args.instance and args.skip_instance:
        parser.error("--instance and --skip-instance cannot be combined")

    biotech = args.biotech.resolve(strict=True)
    if biotech == PROJECT_ROOT:
        raise ValueError("Biotech must be a different project")
    source = properties(PROJECT_ROOT / "gradle.properties")
    target = properties(biotech / "gradle.properties")
    mc = filename_value(source, "minecraft_version")
    editor = filename_value(source, "mod_version")
    library = filename_value(source, "lib_version")
    mod = filename_value(target, "mod_id")
    includes = re.findall(r"(?m)^\s*include\s+([^\r\n]+)", (PROJECT_ROOT / "settings.gradle").read_text(encoding="utf-8"))
    modules = set(re.findall(r"['\"]([^'\"]+)['\"]", " ".join(includes)))
    if not {"NeoForge", "RuntimeNeoForge"}.issubset(modules):
        raise ValueError("This branch does not include NeoForge and RuntimeNeoForge")
    if target.get("minecraft_version") != mc or mod != "create_biotech":
        raise ValueError(f"Target must be a create_biotech project for Minecraft {mc}")
    # Validate both entries before starting work or changing development dependencies.
    for key in ("ponderer_version", "ponderer_lib_version"):
        filename_value(target, key)
    instance = None if args.skip_instance else (args.instance or biotech / ".minecraft" / "versions" / f"{mc}-NeoForge").resolve(strict=True)
    if instance is not None and not (instance / "mods").is_dir():
        raise ValueError(f"Instance has no mods directory: {instance}; use --skip-instance for development-only sync")
    prefixes = (f"ponderer-{mc}-neoforge-", f"pondererlib-{mc}-neoforge-")
    check_unlocked(managed_jars(biotech / "libs", prefixes))
    if instance is not None:
        check_unlocked(managed_jars(instance / "mods", prefixes))

    logs = PROJECT_ROOT / "build" / "biotech-sync"
    build(PROJECT_ROOT, [":NeoForge:build", ":RuntimeNeoForge:build"], args.online, args.timeout, logs / "ponderer.log")
    jars = [PROJECT_ROOT / "NeoForge" / "build" / "libs" / f"ponderer-{mc}-neoforge-{editor}.jar",
            PROJECT_ROOT / "NeoForge" / "runtime" / "build" / "libs" / f"pondererlib-{mc}-neoforge-{library}.jar"]
    for jar in jars:
        require_jar(jar)
    sync_versions(biotech / "gradle.properties", editor, library)
    copy_jars(jars, biotech / "libs", prefixes)
    copy_jars(jars, biotech / "build" / "quickplay" / "mods", prefixes)
    if instance is not None:
        copy_jars(jars, instance / "mods", prefixes)
    print("[DONE] Ponderer/Lib updated in Biotech development directories" + (" and the instance." if instance else ". Instance skipped."), flush=True)


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
    try:
        main()
    except KeyboardInterrupt:
        print("\n[STOP] Sync interrupted; the active build was stopped.", file=sys.stderr)
        sys.exit(130)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        sys.exit(1)
