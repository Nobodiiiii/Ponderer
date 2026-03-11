#!/usr/bin/env python3
"""
Ponderer 自动化 Build + 启动 MC 测试实例

用法:
    python test.py fabric           # Build + 启动 Fabric
    python test.py forge            # Build + 启动 Forge/NeoForge
    python test.py both             # Build + 同时启动两个平台
    python test.py fabric --skip-build   # 跳过 build，直接启动
    python test.py fabric --no-copy      # 跳过 build 和 jar 复制
"""

import sys
import io

# 修复 Windows GBK 编码问题
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

import argparse
import glob
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import uuid

# ─── 配置 ────────────────────────────────────────────────────────────────────

PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
DOT_MINECRAFT = os.path.join(PROJECT_ROOT, ".minecraft")
VERSIONS_DIR = os.path.join(DOT_MINECRAFT, "versions")
LIBRARIES_DIR = os.path.join(DOT_MINECRAFT, "libraries")
ASSETS_DIR = os.path.join(DOT_MINECRAFT, "assets")
DEFAULT_WIDTH = 1920
DEFAULT_HEIGHT = 1080

# 平台配置: branch -> [(platform_name, gradle_project, jar_pattern, instance_dir, version_json)]
PLATFORM_CONFIG = {
    "1.20.1": {
        "fabric": {
            "gradle_project": ":Fabric:build",
            "jar_glob": "ponderer-1.20.1-fabric-{ver}.jar",
            "instance": "1.20.1-Fabric",
            "version_json": "1.20.1-Fabric.json",
        },
        "forge": {
            "gradle_project": ":Forge:build",
            "jar_glob": "ponderer-1.20.1-forge-{ver}-all.jar",
            "build_dir": "Forge",
            "instance": "1.20.1-Forge",
            "version_json": "1.20.1-Forge.json",
        },
    },
    "1.21.1": {
        "fabric": {
            "gradle_project": ":Fabric:build",
            "jar_glob": "ponderer-1.21.1-fabric-{ver}.jar",
            "instance": "1.21.1-Fabric",
            "version_json": "1.21.1-Fabric.json",
        },
        "neoforge": {
            "gradle_project": ":NeoForge:build",
            "jar_glob": "ponderer-1.21.1-neoforge-{ver}.jar",
            "build_dir": "NeoForge",
            "instance": "1.21.1-NeoForge",
            "version_json": "1.21.1-NeoForge.json",
        },
    },
}

# forge 在 1.20.1, neoforge 在 1.21.1 — 统一用 "forge" 作为命令参数
FORGE_ALIAS = {"1.20.1": "forge", "1.21.1": "neoforge"}


# ─── 工具函数 ─────────────────────────────────────────────────────────────────


def read_gradle_properties():
    props = {}
    with open(os.path.join(PROJECT_ROOT, "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


def detect_branch():
    try:
        branch = (
            subprocess.check_output(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"],
                cwd=PROJECT_ROOT,
                stderr=subprocess.DEVNULL,
            )
            .decode()
            .strip()
        )
        # 从分支名提取 MC 版本 (如 "1.20.1", "1.21.1")
        for ver in PLATFORM_CONFIG:
            if ver in branch:
                return ver
        print(f"[WARN] 无法从分支 '{branch}' 识别 MC 版本，默认使用 1.20.1")
        return "1.20.1"
    except Exception:
        print("[WARN] 无法检测 git 分支，默认使用 1.20.1")
        return "1.20.1"


def resolve_instance_path(instance_name):
    """兼容新旧布局: 优先 .minecraft/versions/<instance>，其次 .minecraft/<instance>"""
    new_layout = os.path.join(VERSIONS_DIR, instance_name)
    if os.path.isdir(new_layout):
        return new_layout
    old_layout = os.path.join(DOT_MINECRAFT, instance_name)
    return old_layout


def resolve_version_json_path(config, instance_path):
    """优先使用配置文件名，找不到时回退到 <instance>.json 与历史 *2.json。"""
    candidates = [
        config.get("version_json", ""),
        f"{config['instance']}.json",
        f"{config['instance']}2.json",
    ]
    for name in candidates:
        if not name:
            continue
        path = os.path.join(instance_path, name)
        if os.path.exists(path):
            return path
    return os.path.join(instance_path, config.get("version_json", ""))


def find_java():
    """从 hmclversion.cfg 读取 java 路径，回退到 PATH"""
    search_roots = [DOT_MINECRAFT]
    if os.path.isdir(VERSIONS_DIR):
        search_roots.insert(0, VERSIONS_DIR)

    for root in search_roots:
        for instance_dir in os.listdir(root):
            cfg_path = os.path.join(root, instance_dir, "hmclversion.cfg")
            if os.path.exists(cfg_path):
                with open(cfg_path, encoding="utf-8") as f:
                    cfg = json.load(f)
                    java_path = cfg.get("defaultJavaPath", "")
                    if java_path and os.path.exists(java_path):
                        return java_path
    # Fallback
    return "java"


def maven_to_path(coords):
    """将 Maven 坐标 (group:artifact:version) 转为文件路径"""
    parts = coords.split(":")
    if len(parts) < 3:
        return None
    group, artifact, version = parts[0], parts[1], parts[2]
    group_path = group.replace(".", "/")
    jar_name = f"{artifact}-{version}.jar"
    return f"{group_path}/{artifact}/{version}/{jar_name}"


def os_rule_allows(rules):
    """检查 version JSON 中的 OS 规则是否允许当前平台 (Windows)"""
    if not rules:
        return True
    result = False
    for rule in rules:
        action = rule.get("action") == "allow"
        os_info = rule.get("os", {})
        features = rule.get("features", {})
        # 带 features 的规则（如 is_demo_user）默认跳过
        if features:
            continue
        if not os_info:
            result = action
        elif os_info.get("name") == "windows":
            result = action
        elif os_info.get("name") in ("osx", "linux"):
            result = not action
    return result


def feature_rule_allows(rules, features):
    """检查带 features 条件的规则是否允许。"""
    if not rules:
        return True
    result = False
    for rule in rules:
        action = rule.get("action") == "allow"
        req = rule.get("features", {})
        if req:
            matched = all(bool(features.get(k, False)) == bool(v) for k, v in req.items())
            if matched:
                result = action
        else:
            result = action
    return result


def build_classpath(version_data, instance_path):
    """从 version JSON 构建 classpath"""
    cp_entries = []
    for lib in version_data.get("libraries", []):
        rules = lib.get("rules", [])
        if not os_rule_allows(rules):
            continue

        dl = lib.get("downloads", {}).get("artifact", {})
        path = dl.get("path", "")
        if path:
            full_path = os.path.join(LIBRARIES_DIR, path.replace("/", os.sep))
        else:
            # Fabric/NeoForge loader libraries — maven coords
            name = lib.get("name", "")
            maven_path = maven_to_path(name)
            if not maven_path:
                continue
            full_path = os.path.join(LIBRARIES_DIR, maven_path.replace("/", os.sep))

        if os.path.exists(full_path):
            cp_entries.append(full_path)

    # 添加客户端 jar
    version_id = version_data.get("id", "")
    client_jar = os.path.join(instance_path, f"{version_id}.jar")
    if os.path.exists(client_jar):
        cp_entries.append(client_jar)

    return cp_entries


def build_jvm_args(version_data, instance_path, classpath_str):
    """从 version JSON 构建 JVM 参数"""
    natives_dir = os.path.join(instance_path, "natives-windows-x86_64")
    log4j_path = os.path.join(instance_path, "log4j2.xml")
    version_id = version_data.get("id", "")

    jvm_args = ["-Xmx8G", "-Xms512M"]

    raw_jvm = version_data.get("arguments", {}).get("jvm", [])
    for arg in raw_jvm:
        if isinstance(arg, str):
            # 模板替换
            val = arg
            val = val.replace("${natives_directory}", natives_dir)
            val = val.replace("${launcher_name}", "ponderer-test")
            val = val.replace("${launcher_version}", "1.0")
            val = val.replace("${classpath}", classpath_str)
            val = val.replace("${library_directory}", LIBRARIES_DIR)
            val = val.replace("${classpath_separator}", os.pathsep)
            val = val.replace("${version_name}", version_id)
            val = val.replace(
                "${primary_jar_name}", f"{version_id}.jar"
            )
            jvm_args.append(val)
        elif isinstance(arg, dict):
            rules = arg.get("rules", [])
            if os_rule_allows(rules):
                value = arg.get("value", [])
                if isinstance(value, list):
                    for v in value:
                        v = v.replace("${natives_directory}", natives_dir)
                        v = v.replace("${classpath}", classpath_str)
                        v = v.replace("${library_directory}", LIBRARIES_DIR)
                        v = v.replace("${classpath_separator}", os.pathsep)
                        v = v.replace("${version_name}", version_id)
                        jvm_args.append(v)
                elif isinstance(value, str):
                    jvm_args.append(value)

    # log4j 配置
    if os.path.exists(log4j_path):
        jvm_args.append(f"-Dlog4j.configurationFile={log4j_path}")

    return jvm_args


def build_game_args(version_data, instance_path, width, height):
    """从 version JSON 构建游戏参数"""
    version_id = version_data.get("id", "")
    asset_index = version_data.get("assetIndex", {}).get("id", version_data.get("assets", "5"))
    feature_flags = {
        "has_custom_resolution": True,
        "is_demo_user": False,
        "has_quick_plays_support": False,
        "is_quick_play_singleplayer": False,
        "is_quick_play_multiplayer": False,
        "is_quick_play_realms": False,
    }

    replacements = {
        "${auth_player_name}": "Dev",
        "${version_name}": version_id,
        "${game_directory}": instance_path,
        "${assets_root}": ASSETS_DIR,
        "${assets_index_name}": str(asset_index),
        "${auth_uuid}": str(uuid.uuid4()).replace("-", ""),
        "${auth_access_token}": "0",
        "${clientid}": "0",
        "${auth_xuid}": "0",
        "${user_type}": "legacy",
        "${version_type}": "release",
        "${resolution_width}": str(width),
        "${resolution_height}": str(height),
    }

    game_args = []
    raw_game = version_data.get("arguments", {}).get("game", [])
    for arg in raw_game:
        if isinstance(arg, str):
            val = arg
            for k, v in replacements.items():
                val = val.replace(k, v)
            game_args.append(val)
        elif isinstance(arg, dict):
            rules = arg.get("rules", [])
            os_rules = [r for r in rules if r.get("os")]
            os_ok = os_rule_allows(os_rules) if os_rules else True
            if os_ok and feature_rule_allows(rules, feature_flags):
                value = arg.get("value", [])
                if isinstance(value, list):
                    for v in value:
                        vv = v
                        for k, rv in replacements.items():
                            vv = vv.replace(k, rv)
                        game_args.append(vv)
                elif isinstance(value, str):
                    vv = value
                    for k, rv in replacements.items():
                        vv = vv.replace(k, rv)
                    game_args.append(vv)

    return game_args


def build_launch_command(config, mc_version, mod_version, width, height):
    """构建完整的 MC 启动命令"""
    instance_path = resolve_instance_path(config["instance"])
    version_json_path = resolve_version_json_path(config, instance_path)

    if not os.path.exists(version_json_path):
        print(f"[ERROR] Version JSON 不存在: {version_json_path}")
        return None

    with open(version_json_path, encoding="utf-8") as f:
        version_data = json.load(f)

    java = find_java()
    classpath = build_classpath(version_data, instance_path)
    classpath_str = os.pathsep.join(classpath)
    jvm_args = build_jvm_args(version_data, instance_path, classpath_str)
    game_args = build_game_args(version_data, instance_path, width, height)
    main_class = version_data.get("mainClass", "")

    cmd = [java] + jvm_args + [main_class] + game_args
    return cmd, instance_path


def copy_jar(config, mc_version, mod_version):
    """复制 build 产物到 mods 目录，替换旧版本"""
    build_dir_name = config.get("build_dir", "Fabric")
    # 从 gradle_project 推断 build 目录
    if ":Fabric:" in config["gradle_project"]:
        build_dir_name = "Fabric"
    elif ":Forge:" in config["gradle_project"]:
        build_dir_name = "Forge"
    elif ":NeoForge:" in config["gradle_project"]:
        build_dir_name = "NeoForge"

    build_libs = os.path.join(PROJECT_ROOT, build_dir_name, "build", "libs")
    jar_pattern = config["jar_glob"].replace("{ver}", mod_version)
    jar_path = os.path.join(build_libs, jar_pattern)

    if not os.path.exists(jar_path):
        print(f"[ERROR] Build 产物不存在: {jar_path}")
        return False

    instance_path = resolve_instance_path(config["instance"])
    mods_dir = os.path.join(instance_path, "mods")
    os.makedirs(mods_dir, exist_ok=True)

    # 删除旧的 ponderer jar
    jar_prefix = config["jar_glob"].split("{ver}")[0]  # e.g. "ponderer-1.20.1-fabric-"
    for old_jar in os.listdir(mods_dir):
        if old_jar.startswith(jar_prefix):
            old_path = os.path.join(mods_dir, old_jar)
            os.remove(old_path)
            print(f"  删除旧版本: {old_jar}")

    dest = os.path.join(mods_dir, os.path.basename(jar_path))
    shutil.copy2(jar_path, dest)
    print(f"  复制: {os.path.basename(jar_path)} -> mods/")
    return True


def run_build(targets):
    """运行 Gradle build"""
    gradle_cmd = os.path.join(PROJECT_ROOT, "gradlew.bat")
    if not os.path.exists(gradle_cmd):
        gradle_cmd = os.path.join(PROJECT_ROOT, "gradlew")

    tasks = list(set(t["gradle_project"] for t in targets))
    cmd = [gradle_cmd] + tasks
    print(f"[BUILD] {' '.join(tasks)}")
    result = subprocess.run(cmd, cwd=PROJECT_ROOT)
    if result.returncode != 0:
        print("[ERROR] Build 失败!")
        sys.exit(1)
    print("[BUILD] 完成\n")


def launch_instance(config, mc_version, mod_version, width, height):
    """启动一个 MC 实例"""
    platform_name = config["instance"]
    print(f"[LAUNCH] {platform_name}")

    result = build_launch_command(config, mc_version, mod_version, width, height)
    if not result:
        return

    cmd, instance_path = result
    # 找到主类（在 JVM 参数之后、游戏参数之前的那个）
    main_class = "?"
    for a in cmd[1:]:
        if not a.startswith("-") and "." in a and "=" not in a and os.sep not in a:
            main_class = a
            break
    print(f"  工作目录: {instance_path}")
    print(f"  主类: {main_class}")

    # 启动进程（不阻塞）
    # stdout/stderr 不重定向，MC 日志直接输出到终端方便调试
    process = subprocess.Popen(
        cmd,
        cwd=instance_path,
        creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
        if platform.system() == "Windows"
        else 0,
    )
    print(f"  PID: {process.pid}")
    print(f"  MC 窗口已启动 ✓\n")


# ─── 主流程 ───────────────────────────────────────────────────────────────────


def main():
    parser = argparse.ArgumentParser(description="Ponderer 自动化 Build + 启动 MC 测试")
    parser.add_argument(
        "target",
        nargs="?",
        default="both",
        choices=["fabric", "forge", "both"],
        help="启动目标: fabric, forge(或neoforge), both（默认: both）",
    )
    parser.add_argument("--skip-build", action="store_true", help="跳过 Gradle build")
    parser.add_argument("--no-copy", action="store_true", help="跳过 build 和 jar 复制")
    parser.add_argument("--width", type=int, default=DEFAULT_WIDTH, help=f"窗口宽度（默认: {DEFAULT_WIDTH}）")
    parser.add_argument("--height", type=int, default=DEFAULT_HEIGHT, help=f"窗口高度（默认: {DEFAULT_HEIGHT}）")
    args = parser.parse_args()

    props = read_gradle_properties()
    mod_version = props.get("mod_version", "0.0.0")
    mc_version = detect_branch()

    print(f"═══ Ponderer Test Launcher ═══")
    print(f"  MC 版本: {mc_version}")
    print(f"  Mod 版本: {mod_version}")
    print()

    if mc_version not in PLATFORM_CONFIG:
        print(f"[ERROR] 未配置 MC 版本: {mc_version}")
        sys.exit(1)

    platforms = PLATFORM_CONFIG[mc_version]
    forge_key = FORGE_ALIAS.get(mc_version, "forge")

    # 确定目标平台
    targets = []
    if args.target in ("fabric", "both"):
        if "fabric" in platforms:
            targets.append(platforms["fabric"])
        else:
            print("[ERROR] 当前分支无 Fabric 配置")
            sys.exit(1)
    if args.target in ("forge", "both"):
        if forge_key in platforms:
            targets.append(platforms[forge_key])
        else:
            print(f"[ERROR] 当前分支无 {forge_key} 配置")
            sys.exit(1)

    # Build
    if not args.skip_build and not args.no_copy:
        run_build(targets)

    # 复制 jar
    if not args.no_copy:
        print("[COPY] 复制 jar 到 mods 目录")
        for config in targets:
            if not copy_jar(config, mc_version, mod_version):
                sys.exit(1)
        print()

    # 启动
    for config in targets:
        launch_instance(config, mc_version, mod_version, args.width, args.height)

    print("═══ 全部启动完成 ═══")


if __name__ == "__main__":
    main()
