# 1.11.0 → Minecraft 1.20.1 移植报告

## 范围与位置

- 来源：`1.21.1` 分支 `f36960dc`（1.11.0），以其 `CHANGELOG.md` 和完整生产源码为基准。
- 目标基线：`1.20.1` 分支 `1141b04d`（1.10.5）；当前版本改为 `1.11.0`。
- 合入分支：`1.20.1`；移植实现来自 `codex/port-1.11-to-1.20.1`。
- 当前工作目录：`D:/Files/ponderer`；移植完成后迁回主工作区，临时工作树的归档清理请求已提交，应用返回排队状态，尚未确认删除完成。
- 当前 `settings.gradle` 启用 `Common`、`Forge`、`Fabric`，运行与编译使用 Java 17。
- 主工作区使用 `1.20.1` 分支；1.21.1 分支保留，`publish.py` 发布脚本修改未纳入此次移植。

采用源版实现与资源，保留其控件布局、尺寸、图标、配色、交互流程、提示及新增页面。
差异集中在 Java / Minecraft / Catnip / JEI / 加载器 API。没有重新设计界面。

## 更新内容核对

下表是生产代码与源版逐文件对照后的覆盖范围；“已移植”表示源码实现已落入目标工程，
不表示每个入口都已人工操作或逐像素验收。

| 更新组 | 对应实现（`Common/src/main/java/com/nododiiiii/ponderer/` 下） | 状态 |
| --- | --- | --- |
| 物品标签、多物品挂载、NBT 条件、候选下拉 | `ponder/SceneItemBindings.java`、`ui/ItemTagDropdown.java`、`ui/SceneDescEditorScreen.java` | 已移植 |
| 分类标签、自定义标签、来源标签 | `ponder/CustomPonderTags.java`、`ponder/PondererAttribution.java`、`ui/SceneTagsScreen.java`、`ui/CustomTagEditorScreen.java` | 已移植 |
| 世界界面捕获与开屏数据 | `interfaces/capture/`、两端 `InterfaceExtendedCaptureMixin` | 已移植；缓冲区改用 1.20.1 API |
| 界面沙盒、试用、重置、界面脚本及练习 | `interfaces/sandbox/`、`interfaces/host/`、`interfaces/model/` | 已移植；物品保存原生 NBT |
| AE2 方块与部件菜单 | `compat/ae2/Ae2InterfaceCompat.java` | 已移植；对照 AE2 15.4.10 源码调整定位器和缓冲区类型 |
| 自由定位、倍速、缩放、暂停预览、文本开关 | `mixin/PonderPlaybackMixin.java`、`ui/PonderFreePreview.java`、`ui/PonderSpeedButton.java`、`ui/PonderZoomSlider.java` | 已移植 |
| 列表与表单场景预览、视角保持、取点、步骤导航及试播 | `ui/SceneEditorPreview.java`、`ui/AbstractStepEditorScreen.java`、`ui/SceneEditorScreen.java` | 已移植 |
| 多步骤复制粘贴、字段拖动、摘要本地化与搜索 | `ui/catnip/NumericFieldDrag.java`、`ui/StepPreviewText.java`、`ui/catnip/SceneStepListEntry.java` | 已移植 |
| 世界生成结构选择、预览及后台分帧加载 | `network/StructurePreview*`、`ui/StructureListScreen.java`、`ui/StructurePreviewWidget.java` | 已移植 |
| 远程冲突处理、忽略记录、自动同步偏好及手动覆盖拉取 | `ponder/PendingRemoteUpdates.java`、`ponder/AutoRemoteSync*`、`ponder/RemoteWorkspaceService.java`、`ui/RemoteBrowserScreen.java` | 已移植 |
| 源资源包多选 | `ui/PonderPackSelectScreen.java` | 已移植 |
| 粒子预设、参数校验、分布与发射 | `ponder/ParticleEffectSpec.java`、`ponder/ParticleEffects.java`、`ui/ParticleEffectScreen.java` | 已移植；使用 1.20.1 `/particle` 参数 |
| Java 粒子、物品标签、分类标签导出及动画修复 | `ponder/JavaModuleExportService.java`、`ponder/generated_ponder_support.java.template` 资源 | 已移植；生成代码使用 1.20.1 API |
| 槽位拖放、数量及物品数据、嵌入容器叠层 | `ui/InterfaceSlotEditState.java`、`ui/InterfaceSlotOverlayRenderer.java`、`interfaces/host/` | 已移植；组件改用原生 NBT |
| 文本进度默认设置与原生对话框样式 | `ui/PonderTextListWidget.java`、`mixin/PonderUIMixin.java` | 已移植 |
| 背包选择、新建流程、NBT 展开编辑、统一工具图标 | `ui/PlayerInventoryPicker.java`、`ui/SceneTypeSelectorScreen.java`、`ui/FieldDecorators.java`、`ui/catnip/PondererGuiIcons.java` | 已移植 |
| 相机平移、缩放及旋转动画 | `ponder/AdjustViewInstruction.java`、`ponder/SceneRuntime.java` | 已移植 |
| 蓝图分阶段提示、保存反馈及内置教程 | `blueprint/`、`ponder/DynamicPonderPlugin.java` | 已移植 |
| 选点、NBT 等待提示及统一反馈 | `feedback/`、`ui/CoordPickState.java`、`ui/NbtPickState.java` | 已移植 |
| JEI 配料槽、拖放、提示、可见性及滚动裁剪 | `compat/jei/`、`ui/catnip/IngredientSlotWidget.java` | 已移植；适配 JEI 15 API |
| 场景缓存清理、插值与方块区段修复、投影仪朝向 | `ponder/PonderRenderCache.java`、`ponder/SceneSectionOwnership.java`、`projector/client/`、对应 Mixin | 已移植 |
| 表单样式、边距、按钮边框、标题、未保存提示及 V 菜单 | `ui/catnip/`、`ui/UILayoutConstants.java`、`ui/PondererDialogScreen.java`、`ui/FunctionScreen.java` | 沿用源版 |
| AI 多轮工具、流式输出、预算、历史、恢复、撤销、配置迁移及保护 | `ai/`、`ui/AiGenerateScreen.java`、`ui/AiOutputPanel.java`、`ui/AiRunHistoryScreen.java` | 已移植 |
| 其余 changelog 修复：保存、NBT 返回、导航、区段动画及列表拖动 | 源版相关生产文件整体移入，随后逐项做 API 适配 | 已移植 |

## 代际适配与跳过项

1. **加载器与网络**：NeoForge 事件、菜单、模型数据入口改为 Forge；保留 Common 业务处理。
   1.20.1 不存在的 payload/codec API 由轻量兼容类型连接到 Forge SimpleChannel 和 Fabric 旧网络接口，
   保留源版消息字段、反馈请求/响应与处理顺序。
2. **Java 与基础 API**：Java 21 列表方法改用 Java 17 等价方法；调整 ResourceLocation、
   配置、NBT、方块实体、粒子、顶点缓冲、鼠标滚轮与菜单接口。
3. **UI**：`CompatConfigScreenList` 将新列表矩形 API 桥接到旧列表，保持源版视口坐标与尺寸；
   旧版控件高度用 accessor；原版 1.21 槽位与投影仪页面背景纹理作为独立资源随包提供，保持相同外观；
   未进世界的配置页保留 Catnip 全景背景。
   两条粒子 tooltip 仅将示例改为 1.20.1 可执行语法。
   场景编辑预览与结构选择预览使用 1.20.1 `PonderUI` 的光源方向：本代 GUI 翻转仅改位置矩阵，
   Catnip 对无方向阴影的面还固定使用正 Y 法线；直接沿用 1.21.1 的负 Y 光源会使顶面及这类面偏暗。
   撤掉此前仅翻转法线的尝试，保留本代法线约定、满亮光照贴图和绘制后恢复光源的处理。
   用户已确认现有光源适配解决了预览偏暗，临时诊断代码已移除。
4. **Mixin**：涉及映射成员或 Minecraft 描述符的目标由注解处理器生成 refmap。
   `Forge/src/main/mappings/ponderer.tsrg` 补充稳定第三方成员与映射描述符，不用关闭 remap 绕过。
5. **无法原样支持的数据**：1.21 的任意物品 data components 无法在 1.20.1 直接解码；
   此部分跳过自动跨代转换。1.20.1 的捕获、槽位、背包选择、保存和回放使用完整原生物品 NBT。
   界面沙盒遇到显式 `components` 会报告提示，使用 `nbt` 可保留本代物品数据。
6. **粒子语法**：1.21 粒子 SNBT 不直接兼容；预设已换成颜色、数量等一致的 1.20.1 参数。
   外部 1.21 场景若使用该语法，需要按 `docs/PARTICLE_EFFECTS.md` 调整参数。
7. **原生背景模糊**：投影仪配置页保留 1.21 背景叠层纹理与绘制方式；1.20.1 缺少
   `GameRenderer.processBlurEffect` 管线，本次跳过该原生动态模糊效果。
8. **已有源版限制**：PonderJS 通用粒子转换仍保留源版 `TODO_UNSUPPORTED_STEP`；
   Java 导出仍按源版展开物品标签并省略 NBT 条件。界面不模拟服务端机器、ME 网络或任意模组同步。

按仓库要求，没有新增测试文件、测试用例或测试专用脚本；源版新增 `InterfaceSmokeTest` 未引入。
现有三份 AI 测试只更新了旧断言及本地 mock 路径以符合移入后的行为。

## 验证记录

- `gradlew.bat :Common:build :Forge:build :Fabric:build --offline --console=plain`：通过。
- 现有 Common JUnit：54 个用例中 52 通过、2 个真实 LLM 选择性用例跳过，0 失败 / 0 错误。
- `gradlew.bat quickPlaySmoke -PskipBuild -PsmokeTimeout=90 -PpythonExecutable=C:/Python313/python.exe --offline --console=plain`：
  Forge、Fabric 均进入世界，既有脚本自动结束并清理进程；外层等待另有 300 秒退出保护。
- 检查最终 JAR 的 refmap：Forge 使用 `m_*` / `f_*`，Fabric 使用 `class_*` / `method_*`。
- Java 导出支持模板实例化后，用 Java 17 和 1.20.1 类路径单独编译通过。
- 资源 JSON 可解析；`git diff --check` 通过。
- 预览光源适配构建通过，日志为 `build/preview-lighting-native-build.log`；用户随后确认画面已修复。
- 删除临时诊断后，三模块 `build --offline --console=plain --no-daemon` 通过，现有 Common JUnit
  52 通过、2 跳过；日志为 `build/preview-lighting-cleanup-build.log`。最终 Forge / Fabric JAR
  已确认无诊断类或引用，并通过 `test.py both --skip-build --dry-run` 更新到本地实例。
- 对照来源提交的 Common / Fabric 生产文件：348 个内容相同（忽略行尾差异），136 个作本代适配，
  5 份 recipe 保留本代 `recipes` 路径及 JSON 结构；仅排除 1 个测试辅助文件，无缺失生产文件。
  UI 常量、图标及布局沿用源版。

验证日志位于忽略目录 `build/port-final-build.log`、`build/port-quickplay-smoke.log`；
现有测试报告为 `Common/build/reports/tests/test/index.html`。

首次 Forge 启动检查遇到客户端 `InjectionError`：容器叠层拦截目标残留 NeoForge 事件总线描述符。
已改为 Forge 的 `IEventBus.post(Event):boolean`，重新构建和双端进世界检查均通过。
项目根 `.minecraft/crash-reports/` 不存在；实际诊断依据版本日志及启动控制台捕获，未使用旧崩溃报告。

Forge 实例另有四份既有 KubeJS 导出脚本报告 `Ponder is not defined`；其文件早于本次移植，
当前 mods 中未找到 PonderJS JAR。未修改这些本地脚本，PonderJS 实际回放不在已验证范围。

**未实测范围**：未进行 GUI 自动化或逐像素截图对照；未逐项操作编辑器、捕获回放、AE2 菜单和 JEI；
未进行真实外部 LLM 调用、独立服务端 / LAN 多人同步。构建、源码对照和进世界通过不能替代这些交互验收。

## 投影仪渲染回归复核

微缩和实景投影随视角大幅偏转的直接原因是移植提交 `39c4af83` 将场景从方块实体渲染
移入 `ProjectorWorldOverlayQueue`，却沿用了 1.21.1 的矩阵处理。1.20.1 的方块实体
`PoseStack` 已含相机旋转，Forge `AFTER_WEATHER` 阶段的全局 ModelView 又含一次相机旋转，
导致保存的姿态在延迟绘制时重复旋转。1.21.1 则在全局 ModelView 中应用视图矩阵，
方块实体使用独立的局部 `PoseStack`；同一份延迟绘制代码不能直接照搬。

依据本机缓存的 Minecraft 源码核对：1.20.1 `GameRenderer.renderLevel` 将相机旋转乘入
传给 `LevelRenderer` 的姿态，`LevelRenderer.renderLevel` 在云与天气阶段将它再乘入全局
ModelView，并在派发 `AFTER_WEATHER` 后才弹栈；1.21.1 的 `LevelRenderer` 在渲染实体前
设置全局视图矩阵，同时新建局部姿态栈。

本地实例日志确认实际加载 `Ponder-Forge-1.20.1-1.0.92.jar`。对比本机缓存的 Ponder
1.0.91 / 1.0.92 源码，`PonderScene.renderScene` 和 `WorldSectionElementImpl.renderLayer`
完全相同；本地 `ref/1.20.1/Ponder/Common/src/main/java/net/createmod/ponder/` 下也保留了
相同的场景到区段绘制链。因此本次根因是遗漏 Minecraft 渲染约定适配，并非该次 Ponder 更新。

修复位于 Common 的 `ProjectorWorldOverlayQueue.render`：绘制延迟场景、光束和叠层时，
临时将全局 ModelView 设为单位矩阵，使用已保存的完整姿态，并通过 `finally` 恢复原状态。
`gradlew.bat :Common:build :Forge:build :Fabric:build --offline --console=plain --no-daemon`
通过；现有 Common JUnit 52 通过、2 跳过，日志为 `build/projector-matrix-build.log`。
未新增测试，未修改 Mixin；尚未进行游戏内转动视角的视觉复核。

## 产物

- Forge：`Forge/build/libs/ponderer-1.20.1-forge-1.11.0-all.jar`。
- Fabric：`Fabric/build/libs/ponderer-1.20.1-fabric-1.11.0.jar`。

移植改动已提交并合入本地 `1.20.1` 分支。构建产物和验证报告已随迁移保留；
临时迁移备份位于忽略目录 `build/port-worktree-transfer/`。
