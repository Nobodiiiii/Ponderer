# 同步到 Biotech 开发环境

需要 Python 3.10+、项目要求的 JDK，以及相邻的 `create_biotech` 工程。完整同步前关闭 Biotech 游戏实例。

在 Ponderer 根目录运行：

```powershell
python .\sync_biotech.py
```

脚本默认离线执行以下流程：

1. 构建 `:NeoForge:build :RuntimeNeoForge:build`。
2. 将 Ponderer 和 Lib 复制到 Biotech 的 `libs/`，同步 `gradle.properties` 中的 `ponderer_version` 和 `ponderer_lib_version`。
3. 将两份 JAR 直接复制到 Biotech 的 `build/quickplay/mods/` 暂存目录。
4. 将 Ponderer 和 Lib 更新到 `.minecraft/versions/<MC>-NeoForge/mods/`。

目标目录从脚本所在仓库定位，因此可以从其他工作目录执行脚本。同步直接使用 JAR，不需要发布到本地 Maven。脚本仅构建和同步 Ponderer 与 Lib，不执行 Biotech 的 Gradle 任务、不替换 Biotech 本体，也不启动游戏。

Gradle 构建默认最多运行 900 秒，超时或按 Ctrl+C 会停止当前构建进程树。构建日志在 Ponderer 的 `build/biotech-sync/ponderer.log`；复制前会检查 Windows 文件占用。每个目录的替换失败时恢复该目录原有 Ponderer 和 Lib JAR，已成功更新的其他目录保留，解决报错后重新执行即可。旧版本的 Ponderer 和 Lib JAR 改为 `.jar.disabled-*`，避免重复加载。

可选参数：

```powershell
# 允许下载缺失依赖
python .\sync_biotech.py --online

# 仅更新开发依赖和暂存目录，适用于游戏仍在运行或尚未创建实例
python .\sync_biotech.py --skip-instance

# 指定其他 Biotech 工程、游戏实例和单次构建超时
python .\sync_biotech.py --biotech D:\Dev\create_biotech --instance D:\MC\BiotechDev --timeout 1200
```

`--instance` 接收包含 `mods/` 的实例目录。脚本要求两边 Minecraft 版本一致，且当前 Ponderer 分支启用了 NeoForge 和 RuntimeNeoForge。Lib 的兼容版本范围仍由 Biotech 工程维护。
