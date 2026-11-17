# 覆盖 Biotech 1.21.1 实例模组

需要 Python 3.10+、项目要求的 JDK，以及相邻的 `create_biotech` 工程。同步前关闭 Biotech 游戏实例。

在 Ponderer 根目录运行：

```powershell
python .\sync_biotech.py
```

脚本默认离线执行以下流程：

1. 构建 `:NeoForge:build :RuntimeNeoForge:build`。
2. 将生成的 Ponderer 和 Lib JAR 直接覆盖到 `../create_biotech/.minecraft/versions/1.21.1-NeoForge/mods/`。
3. 清理该目录中旧版本的 Ponderer 和 Lib JAR，避免重复加载。

目标目录从脚本所在仓库定位，因此可以从其他工作目录执行脚本。同步直接使用 JAR，不需要发布到本地 Maven。脚本仅构建 Ponderer 与 Lib 并覆盖游戏实例中的两份模组，不再更新 Biotech 的 `libs/`、`build/quickplay/mods/` 或 `gradle.properties`，不执行 Biotech 的 Gradle 任务、不替换 Biotech 本体，也不启动游戏。

Gradle 构建默认最多运行 900 秒，超时或按 Ctrl+C 会停止当前构建进程树。构建日志在 Ponderer 的 `build/biotech-sync/ponderer.log`；复制前会检查 Windows 文件占用。替换失败时恢复实例原有 Ponderer 和 Lib JAR。成功后移除临时备份，不再生成 `.jar.disabled-*` 文件；已有的禁用文件保持原样。

可选参数：

```powershell
# 允许下载缺失依赖
python .\sync_biotech.py --online

# 指定其他 Biotech 工程、游戏实例和单次构建超时
python .\sync_biotech.py --biotech D:\Dev\create_biotech --instance D:\MC\BiotechDev --timeout 1200
```

`--instance` 接收包含 `mods/` 的实例目录，默认使用 Biotech 工程中的 `.minecraft/versions/<MC>-NeoForge/`（当前为 `1.21.1-NeoForge`）。脚本要求两边 Minecraft 版本一致，且当前 Ponderer 分支启用了 NeoForge 和 RuntimeNeoForge。`--skip-instance` 已移除。
