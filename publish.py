#!/usr/bin/env python3
"""
Ponderer 一键构建 + 发布脚本
用法: python publish.py [--dry-run] [--skip-build] [--version-type release|beta|alpha] [--only-modrinth] [--only-curseforge]
"""

import io
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# 修复 Windows 终端中文输出
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

try:
    import requests
except ImportError:
    print("缺少 requests 库，正在安装...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "requests"])
    import requests

# ── 常量 ──────────────────────────────────────────────────────────────────────

PROJECT_ROOT = Path(__file__).parent.resolve()
GRADLE_PROPS = PROJECT_ROOT / "gradle.properties"
CHANGELOG_FILE = PROJECT_ROOT / "CHANGELOG.md"
ENV_FILE = PROJECT_ROOT / ".env"

MODRINTH_API = "https://api.modrinth.com/v2"
MODRINTH_PROJECT_ID = "voXQB5Hr"  # the-ponderer

CURSEFORGE_UPLOAD_API = "https://minecraft.curseforge.com/api"
CURSEFORGE_PROJECT_ID=1461949

# CurseForge 固定版本 ID（1.20.1 / 1.21.1）
CF_MC_VERSION_IDS = {
    "1.20.1": 9990,
    "1.21.1": 11779,
}

CF_LOADER_VERSION_IDS = {
    "Forge": 7498,
    "Fabric": 7499,
    "NeoForge": 10150,
}

# 分支 → 构建配置映射
BRANCH_CONFIG = {
    "1.21.1": {
        "mc_version": "1.21.1",
        "loaders": [
            {
                "name": "neoforge",
                "display": "NeoForge",
                "task": ":NeoForge:build",
                "jar_dir": "NeoForge/build/libs",
                "modrinth_loader": "neoforge",
                "curseforge_loader": "NeoForge",
            },
            {
                "name": "fabric",
                "display": "Fabric",
                "task": ":Fabric:build",
                "jar_dir": "Fabric/build/libs",
                "modrinth_loader": "fabric",
                "curseforge_loader": "Fabric",
            },
        ],
    },
    "1.20.1": {
        "mc_version": "1.20.1",
        "loaders": [
            {
                "name": "forge",
                "display": "Forge",
                "task": ":Forge:build",
                "jar_dir": "Forge/build/libs",
                "modrinth_loader": "forge",
                "curseforge_loader": "Forge",
            },
            {
                "name": "fabric",
                "display": "Fabric",
                "task": ":Fabric:build",
                "jar_dir": "Fabric/build/libs",
                "modrinth_loader": "fabric",
                "curseforge_loader": "Fabric",
            },
        ],
    },
}


# ── 工具函数 ────────────────────────────────────────────────────────────────

def load_env():
    """从 .env 文件加载环境变量"""
    if not ENV_FILE.exists():
        print(f"错误: 找不到 .env 文件，请在项目根目录创建 .env 并填入:")
        print(f"  MODRINTH_TOKEN=你的modrinth_token")
        print(f"  CURSEFORGE_TOKEN=你的curseforge_token")
        print(f"\nModrinth token: https://modrinth.com/settings/pats")
        print(f"CurseForge token: https://authors-old.curseforge.com/account/api-tokens")
        print(f"CurseForge 项目 ID: 在 CurseForge 项目页面 URL 中可以找到")
        sys.exit(1)

    env = {}
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, _, value = line.partition("=")
            env[key.strip()] = value.strip()
    return env


def get_current_branch():
    """获取当前 git 分支名"""
    result = subprocess.run(
        ["git", "branch", "--show-current"],
        capture_output=True, text=True, cwd=PROJECT_ROOT
    )
    return result.stdout.strip()


def read_gradle_properties():
    """解析 gradle.properties"""
    props = {}
    for line in GRADLE_PROPS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    return props


def extract_changelog(version: str) -> str:
    """从 CHANGELOG.md 提取指定版本的更新日志"""
    if not CHANGELOG_FILE.exists():
        print(f"警告: 未找到 CHANGELOG.md，将使用空 changelog")
        return ""

    content = CHANGELOG_FILE.read_text(encoding="utf-8")
    # 匹配 ## X.Y.Z 格式的版本标题
    pattern = rf"## {re.escape(version)}\s*\n(.*?)(?=\n## |\Z)"
    match = re.search(pattern, content, re.DOTALL)
    if match:
        return match.group(1).strip()

    print(f"警告: CHANGELOG.md 中未找到版本 {version} 的内容")
    return ""


def _version_candidates(version: str):
    """生成可匹配的版本候选（例如 1.6.0.1 -> 1.6.0.1 / 1.6.0）"""
    candidates = []
    if version:
        candidates.append(version)

    parts = version.split(".") if version else []
    if len(parts) >= 4:
        candidates.append(".".join(parts[:3]))

    # 去重并保序
    seen = set()
    unique = []
    for v in candidates:
        if v not in seen:
            seen.add(v)
            unique.append(v)
    return unique


def extract_changelog_from_git(version: str, mc_version: str, max_log: int = 200) -> str:
    """当 CHANGELOG 缺失时，从 git 提交标题中提取版本说明

    期望格式示例：
    1.6.0 - 1.20.1 feat: 新增 xxx
    """
    candidates = _version_candidates(version)
    if not candidates:
        return ""

    result = subprocess.run(
        ["git", "log", f"-n{max_log}", "--pretty=format:%s"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        cwd=PROJECT_ROOT,
    )
    if result.returncode != 0:
        return ""

    subjects = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not subjects:
        return ""

    # 匹配：<version> - <mc_version> <message>
    for subject in subjects:
        for ver in candidates:
            pattern = rf"^\s*{re.escape(ver)}\s*-\s*{re.escape(mc_version)}\s*(.+)$"
            match = re.match(pattern, subject, re.IGNORECASE)
            if not match:
                continue

            trimmed = match.group(1).strip()
            # 去掉前置分隔符，保留提交本体（如 feat: xxx）
            trimmed = re.sub(r"^[\-:：\s]+", "", trimmed)
            return trimmed

    return ""


def find_jar(jar_dir: str, mc_version: str, loader: str, mod_version: str) -> Path:
    """查找构建产物 jar 文件"""
    jar_path = PROJECT_ROOT / jar_dir
    expected_name = f"ponderer-{mc_version}-{loader}-{mod_version}.jar"
    expected = jar_path / expected_name

    if expected.exists():
        return expected

    # 回退: 查找匹配模式的 jar
    if jar_path.exists():
        jars = list(jar_path.glob(f"ponderer-*-{loader}-{mod_version}*.jar"))
        # 排除 sources jar
        jars = [j for j in jars if "-sources" not in j.name]
        if jars:
            return jars[0]

    print(f"错误: 找不到构建产物 {expected}")
    return None


def run_build(task: str):
    """执行 gradle 构建"""
    gradlew = PROJECT_ROOT / "gradlew.bat" if os.name == "nt" else PROJECT_ROOT / "gradlew"
    cmd = [str(gradlew), task]
    print(f"  执行: {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=PROJECT_ROOT)
    return result.returncode == 0


# ── Modrinth 上传 ──────────────────────────────────────────────────────────

def upload_modrinth(
    token: str,
    jar_path: Path,
    mod_version: str,
    mc_version: str,
    loader: str,
    changelog: str,
    version_type: str,
    loader_display: str,
):
    """上传到 Modrinth"""
    version_name = f"Ponderer - 思索者 [{loader_display}] {mc_version} - {mod_version}"
    version_number = f"{mod_version}"

    data = {
        "name": version_name,
        "version_number": version_number,
        "changelog": changelog,
        "game_versions": [mc_version],
        "version_type": version_type,
        "loaders": [loader],
        "featured": True,
        "project_id": MODRINTH_PROJECT_ID,
        "file_parts": ["file"],
        "dependencies": [
            {"project_id": "u6dRKJwZ", "dependency_type": "optional"},  # JEI
        ],
    }

    resp = requests.post(
        f"{MODRINTH_API}/version",
        headers={"Authorization": token},
        data={"data": json.dumps(data)},
        files={"file": (jar_path.name, open(jar_path, "rb"), "application/java-archive")},
    )

    if resp.status_code in (200, 201):
        result = resp.json()
        version_id = result.get("id", "")
        print(f"  Modrinth 上传成功: https://modrinth.com/mod/the-ponderer/version/{version_id}")
        return True
    else:
        print(f"  Modrinth 上传失败 [{resp.status_code}]: {resp.text}")
        return False


# ── CurseForge 上传 ─────────────────────────────────────────────────────────

_cf_version_cache = None
_cf_version_types_cache = None

def get_curseforge_version_types(token: str):
    """获取 CurseForge 的 version type 列表（用于过滤无效版本）"""
    global _cf_version_types_cache
    if _cf_version_types_cache is not None:
        return _cf_version_types_cache

    resp = requests.get(
        f"{CURSEFORGE_UPLOAD_API}/game/version-types",
        headers={"X-Api-Token": token},
    )
    if resp.status_code != 200:
        print(f"  CurseForge 获取版本类型列表失败 [{resp.status_code}]: {resp.text}")
        return []

    _cf_version_types_cache = resp.json()
    return _cf_version_types_cache


def get_curseforge_game_versions(token: str):
    """获取 CurseForge 的 game version ID 列表（带缓存）"""
    global _cf_version_cache
    if _cf_version_cache is not None:
        return _cf_version_cache

    resp = requests.get(
        f"{CURSEFORGE_UPLOAD_API}/game/versions",
        headers={"X-Api-Token": token},
    )
    if resp.status_code != 200:
        print(f"  CurseForge 获取版本列表失败 [{resp.status_code}]: {resp.text}")
        return []

    _cf_version_cache = resp.json()
    return _cf_version_cache


def find_cf_version_ids(token: str, mc_version: str, loader_name: str):
    """查找 CurseForge 对应的 MC 版本和 loader 的 ID"""
    fixed_mc_id = CF_MC_VERSION_IDS.get(mc_version)
    fixed_loader_id = CF_LOADER_VERSION_IDS.get(loader_name)
    if fixed_mc_id and fixed_loader_id:
        return [fixed_mc_id, fixed_loader_id]

    versions = get_curseforge_game_versions(token)
    if not versions:
        return []

    # 获取有效的 version type ID 集合（排除 Bukkit 等无效类型）
    version_types = get_curseforge_version_types(token)
    valid_type_ids = {vt["id"] for vt in version_types} if version_types else None

    ids = []
    for v in versions:
        # 过滤掉无效 type（如 Bukkit type=1 不在 version-types 列表中）
        if valid_type_ids is not None and v.get("gameVersionTypeID") not in valid_type_ids:
            continue

        name = v.get("name", "")
        if name == mc_version or name == loader_name:
            ids.append(v["id"])

    return ids


def upload_curseforge(
    token: str,
    project_id: str,
    jar_path: Path,
    mc_version: str,
    mod_version: str,
    loader_name: str,
    changelog: str,
    version_type: str,
):
    """上传到 CurseForge"""
    game_version_ids = find_cf_version_ids(token, mc_version, loader_name)
    if not game_version_ids:
        print(f"  CurseForge: 无法找到对应的 game version ID (MC {mc_version}, {loader_name})")
        return False

    metadata = {
        "changelog": changelog,
        "changelogType": "markdown",
        "displayName": f"Ponderer - [{loader_name}] {mc_version} - {mod_version}",
        "gameVersions": game_version_ids,
        "releaseType": version_type,
    }

    resp = requests.post(
        f"{CURSEFORGE_UPLOAD_API}/projects/{project_id}/upload-file",
        headers={"X-Api-Token": token},
        data={"metadata": json.dumps(metadata)},
        files={"file": (jar_path.name, open(jar_path, "rb"), "application/java-archive")},
    )

    if resp.status_code == 200:
        result = resp.json()
        file_id = result.get("id", "")
        print(f"  CurseForge 上传成功 (file ID: {file_id})")
        return True
    else:
        print(f"  CurseForge 上传失败 [{resp.status_code}]: {resp.text}")
        return False


# ── 主流程 ──────────────────────────────────────────────────────────────────

def main():
    dry_run = "--dry-run" in sys.argv
    skip_build = "--skip-build" in sys.argv
    only_modrinth = "--only-modrinth" in sys.argv
    only_curseforge = "--only-curseforge" in sys.argv
    upload_modrinth_flag = not only_curseforge
    upload_curseforge_flag = not only_modrinth

    # 版本类型
    version_type = "release"
    for arg in sys.argv[1:]:
        if arg.startswith("--version-type"):
            if "=" in arg:
                version_type = arg.split("=", 1)[1]

    # ── 1. 检测分支 ──
    branch = get_current_branch()
    print(f"\n=== Ponderer 发布工具 ===\n")
    print(f"  当前分支: {branch}")

    if branch not in BRANCH_CONFIG:
        print(f"  错误: 未识别的分支 '{branch}'，支持的分支: {', '.join(BRANCH_CONFIG.keys())}")
        sys.exit(1)

    config = BRANCH_CONFIG[branch]
    mc_version = config["mc_version"]

    # ── 2. 读取版本信息 ──
    props = read_gradle_properties()
    mod_version = props.get("mod_version", "")
    if not mod_version:
        print("  错误: gradle.properties 中未找到 mod_version")
        sys.exit(1)

    print(f"  Mod 版本: {mod_version}")
    print(f"  MC 版本: {mc_version}")

    # ── 3. 提取 changelog ──
    changelog = extract_changelog(mod_version)
    if not changelog:
        git_changelog = extract_changelog_from_git(mod_version, mc_version)
        if git_changelog:
            changelog = git_changelog
            print(f"  从 Git 提取 changelog: {changelog}")

    if changelog:
        # 只显示前 3 行预览
        preview = "\n".join(changelog.splitlines()[:3])
        print(f"  Changelog 预览:\n    {preview}")
        if len(changelog.splitlines()) > 3:
            print(f"    ... (共 {len(changelog.splitlines())} 行)")
    else:
        print("  Changelog: (空)")

    # ── 4. 选择版本类型 ──
    if "--version-type" not in " ".join(sys.argv):
        print(f"\n  版本类型 [release/beta/alpha] (直接回车默认 release): ", end="")
        user_input = input().strip().lower()
        if user_input in ("release", "beta", "alpha"):
            version_type = user_input
        elif user_input:
            print(f"  无效的版本类型 '{user_input}'，使用默认 release")

    print(f"  版本类型: {version_type}")

    # ── 5. 加载密钥 ──
    if not dry_run:
        env = load_env()
        modrinth_token = env.get("MODRINTH_TOKEN", "")
        curseforge_token = env.get("CURSEFORGE_TOKEN", "")

        missing = []
        if not modrinth_token:
            missing.append("MODRINTH_TOKEN")
        if not curseforge_token:
            missing.append("CURSEFORGE_TOKEN")
        if missing:
            print(f"\n  错误: .env 中缺少: {', '.join(missing)}")
            sys.exit(1)

    if dry_run:
        print("\n  [DRY RUN 模式 - 仅构建不上传]")

    # ── 6. 构建 ──
    print(f"\n--- 构建 ---\n")
    jars = []

    for loader in config["loaders"]:
        loader_display = loader["display"]

        if skip_build:
            print(f"  跳过构建 {loader_display} (--skip-build)")
        else:
            print(f"  构建 {loader_display}...")
            if not run_build(loader["task"]):
                print(f"  构建 {loader_display} 失败!")
                sys.exit(1)
            print(f"  {loader_display} 构建成功")

        jar = find_jar(loader["jar_dir"], mc_version, loader["name"], mod_version)
        if jar is None:
            sys.exit(1)

        jars.append((loader, jar))
        print(f"  产物: {jar.name} ({jar.stat().st_size / 1024 / 1024:.1f} MB)")

    if dry_run:
        print(f"\n--- Dry Run 完成 ---")
        print(f"  以下文件将被上传:")
        for loader, jar in jars:
            print(f"    - {jar.name} → Modrinth ({loader['modrinth_loader']}) + CurseForge ({loader['curseforge_loader']})")
        print(f"\n  去掉 --dry-run 参数以执行实际上传")
        return

    # ── 7. 上传 ──
    if upload_modrinth_flag:
        print(f"\n--- 上传到 Modrinth ---\n")
        for loader, jar in jars:
            upload_modrinth(
                token=modrinth_token,
                jar_path=jar,
                mod_version=mod_version,
                mc_version=mc_version,
                loader=loader["modrinth_loader"],
                changelog=changelog,
                version_type=version_type,
                loader_display=loader["display"],
            )

    if upload_curseforge_flag:
        print(f"\n--- 上传到 CurseForge ---\n")
        for loader, jar in jars:
            upload_curseforge(
                token=curseforge_token,
                project_id=CURSEFORGE_PROJECT_ID,
                jar_path=jar,
                mc_version=mc_version,
                mod_version=mod_version,
                loader_name=loader["curseforge_loader"],
                changelog=changelog,
                version_type=version_type,
            )

    print(f"\n=== 发布完成! ===\n")


if __name__ == "__main__":
    main()
