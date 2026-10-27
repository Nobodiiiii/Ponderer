# 模块化阶段 1–6 实施记录

范围：Minecraft 1.21.1；活动构建项目仍为 Common、NeoForge、Fabric。阶段 7 的独立 JAR 不在本次范围。

阶段 1–6 实施时，用户明确要求不运行测试与验收，因此当时只进行了源码检查，未执行 Gradle、JUnit、客户端 smoke 或人工运行验收。后续已按用户新请求补跑构建与 smoke，结果见文末。未新增测试。

## 阶段 1：场景契约和叶子能力

- 场景数据、文本、界面描述/兼容迁移及步骤词汇归 `scene`。
- JSON、标准化和结构引用收集归 `SceneCodec`、`SceneNormalizer`、`StructureReferences`，编辑默认值显式传入。
- 结构检查归 `support.structure`；Minecraft NBT 处理归 `support.nbt`，避免把游戏类型带入场景模型。
- AI 诊断留在 AI 消费端，语言环境由客户端装配提供；JSON 字段、旧别名与池索引算法保留。
- 暂留 SceneStore 委托，随工作区迁移消除产品级混合职责。

后续如需验证，首先执行 `./gradlew.bat :NeoForge:build :Fabric:build`；本次未执行。

## 阶段 2：工作区与应用用例

- 将原 SceneStore 算法分入 WorkspaceLayout、LocalSceneRepository、LocalStructureRepository、LocalPackService，保留路径、备份、导入顺序和结果字段。
- WorkspaceFiles 显式接收配置/游戏根目录和默认可编辑策略，独占包状态；工作区不发布运行状态，也不引用屏幕或投影仪。
- SceneRefreshCoordinator 统一发布与重载；投影缓存通过装配订阅失效。编辑器保存使用应用用例，SaveOutcome 分别报告写盘与发布结果。
- SceneStore 作为过渡静态委托保留至阶段 6。原有异步重载调用时机保留。
- 仅进行源码与差异检查；未运行构建、测试或运行验收。

## 阶段 3：独立运行服务

- DynamicPonderPlugin 只负责注册适配；SceneCompiler 负责结构/分段与编译；SceneStepExecutor 保留原步骤执行算法。
- RuntimeEngine 接收 SceneSource、StructureSource、IngredientResolver 和可选注册贡献；ProductRuntime 负责产品装配，内存宿主无需引用工作区。
- 场景目录改为实例 SceneCatalog，独立 PonderIdIndex 保留重复 ID 的注册顺序；SceneSnapshot 隔离输入和可变草稿。
- CompilationResult / PlaybackSession 提供独立时间线。投影专用上下文下沉为 PlaybackExecutionContext，保留帧时间与副作用抑制；界面提示改为通用 OverlayTimeline。
- 蓝图指南和默认分类注册移到装配贡献，JEI 通过运行 SPI 注入。结构资源通过流打开，路径选择只留在产品适配器。
- Ponder API 核对来源：ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/registration/PonderSceneRegistry.java。
- 仅进行源码引用与差异检查；未编译，也未运行 Mixin smoke 或功能验收。

## 阶段 4：编辑、播放呈现与界面采集

- EditorSession / EditCommand / EditorHistory 接管草稿步骤变更和历史；屏幕保留原有即时保存、分段切换和跨屏返回流程。
- 界面数据在 scene.interfaces，执行/沙盒在 runtime.interfaces，采集与槽位拖拽在 editor.capture。运行槽位指令只发布变化通知。
- InterfaceEditingExtension 和可关闭 EditorModule 隔离编辑预览/幽灵槽位、按键接管和监听；普通播放使用空扩展即可运行。
- 播放控件在 runtime.viewer，编辑预览在 editor.preview。PonderUIMixin 保留播放职责，PonderEditorMixin 持有按钮、点选、采集提示和编辑返回；两端配置明确列出归属。
- 界面平台由客户端装配显式安装，AE2 菜单构建与 JEI 渲染从执行实现中转为注入能力。
- 仅做源码引用、服务描述与差异检查；Mixin 配置/注入变更未做编译、映射或 smoke 验证。

## 阶段 5：功能协议、服务与平台能力

- 保留全部 payload ID、codec 字段/顺序及反馈 envelope allowlist 顺序；DTO 不再承载文件、配置、屏幕和服务调用。各功能的 protocol 与 handler 分开；服务器批量发送 SyncSender 与客户端 RemotePullClient 分离，冲突/权限/自动同步算法保留。
- 投影仪、蓝图、触发器归 features；远程目录/历史/权限归 sync。工作区删除通知改为注入回调，不再引用同步服务。
- PlatformHelper 仅保留环境能力，投影菜单使用独立 ProjectorPlatform；加载器负责安装对应实现。服务器可用性以不可变 Snapshot 保存，原捕获与回退时机保留。
- WorldFeedback 只处理提示调度和显示；编辑工具由产品生命周期独立调度。JEI 渲染和 AE2 菜单通过消费方 SPI 接入，编辑 JEI 操作仍在编辑工具一侧。
- AI 管线接收 GenerationResources 和策略，输出不可变候选 SceneSnapshot；产品接入器沿用原保存方式。Java 导出接收固定场景快照与只读结构来源，算法不再读取全局目录。
- 仅检查源码引用和 diff 格式；未运行构建、测试、联网 LLM 或功能验收。连接清理、遗留门面及最终依赖归属在阶段 6 收口。

## 阶段 6：依赖收口、状态清理和归属

- 删除 SceneStore 等迁移门面；Workspace API 完整覆盖布局、仓库、资源包、状态和注册表，产品实例只由 bootstrap 构造；RuntimeAccess / WorkspaceAccess 显式安装，无隐式产品回退。
- 产品屏幕、组合 Mixin、命令、AI 保存适配及 PonderJS 文件用例归 application；编辑核心以会话、预览和采集回调为边界。编辑 JEI 呈现通过消费方接口注入，兼容插件与屏幕拖放归应用装配。
- 自定义分类显式消费来源集合，Java 导出包含请求快照中的跨场景分类，PonderJS codec 不再依赖命令或全局运行目录。功能注册供应器归各自功能，产品保留原初始化顺序。
- 可关闭编辑、同步、AI、投影客户端安装；世界/连接变化统一清理临时状态、监听、预览/渲染资源和旧 AI 回调。运行实例跟踪并关闭其播放会话，清理采用逐项尝试和异常汇总。
- 修复拆仓库时两个重载方法被重复首个实现的问题，恢复基线的结构 ResourceLocation 路径与按 pack 查找场景实现。其余 JSON、协议 ID、codec、配置键和存储布局沿用原格式。
- 移动或拆分的 Mixin 同步两端配置；涉及映射类型的 UI 注入移除不合适的 remap=false。按用户要求未编译、查看生成 refmap 或运行 smoke，此部分运行注入仍未验证。
- [归属说明](MODULARIZATION_OWNERSHIP.md) 和 [逐项清单](MODULARIZATION_OWNERSHIP.csv) 记录主源码、资源、配置、注册、Mixin、SPI 与入口。原方案更新为实施状态，并保留阶段 7 的后续设计。
- 已做语法/重复签名/项目引用、Mixin/SPI 源码解析、协议 codec 对照及 diff 格式检查；没有运行构建、测试或验收，没有新增测试。阶段 7 的物理 Gradle/JAR 提取不在本次范围。

## 后续构建与 smoke（2026-10-27）

用户随后要求运行构建和 smoke。本轮修复了实际验证发现的迁移遗漏，并作为独立修复提交：

- `PonderPackRegistry` 实现 `PackRegistry`，恢复 `PackEntry` 类型继承和工作区接口返回类型。
- JEI 拖放调用正确的公开 `resolveIngredientId`；调色板解析方法移到实际使用它的步骤执行器。
- 原 Mixin 配置覆盖整个产品根包，导致两端模组入口均触发 `IllegalClassLoadError`。两端现在分别声明平台、runtime、editor、application、feedback 的专用 Mixin 包；保留原有 40/41 个注入，更新加载器元数据和归属表。

验证结果（Java 21，离线缓存）：

| 检查 | 命令或证据 | 结果 |
| --- | --- | --- |
| 两端构建 | `./gradlew.bat :NeoForge:build :Fabric:build --offline --no-daemon --console=plain --continue` | 通过；最终重跑 16 秒 |
| 两端界面 smoke | `./gradlew.bat quickPlaySmoke -PskipBuild -PsmokeTimeout=180 -PsmokeInterfaces --offline --no-daemon --console=plain` | 通过；约 1 分 21 秒，两个实例均实际启动，均为 `interfaces PASS (intercepted=7, taintDrops=0)` |
| Fabric refmap | `Fabric/build/classes/java/main/ponderer.refmap.json` | 迁移后的 UI、反馈注入包含 `class_* / method_* / field_*` 映射；JAR 中 refmap 存在 |
| NeoForge Mixin | 最终 JAR 的五份配置与实际启动/界面路径 | 当前 1.21.1 ModDev 构建未产生独立 refmap；未按旧版 Forge 的 SRG 名称推断 |
| 归属与打包 | CSV 覆盖 641 个主源码/资源输入；两端 JAR 的配置类均存在 | 通过；没有遗留旧 `ponder/` 类 |
| 清理 | smoke 自带每端 180 秒超时/进程树清理，外层 600 秒超时 | 通过；最终客户端 PID 11980、13072 均已退出 |

最终日志：`build/modularization-verification/build-3.log`、`build/modularization-verification/smoke-2.log`；运行日志位于项目本地 `.minecraft/versions/1.21.1-{Fabric,NeoForge}/logs/latest.log`。

检查边界：两端均提示 `world capture fixture unavailable; skipped`。自检的空 ID 场景在预览编译中记录空指针日志；已核对重构前相同的空场景夹具和 `ResourceLocation.tryParse(scene.id)` 路径，未在本轮扩展修改该逻辑。NeoForge 本地模组环境另有 Curios 的 scroll/spellbook 槽位未注册日志。smoke PASS 不代表这些日志消失，也不覆盖完整人工功能验收、独立运行宿主或专用服务器；本轮未运行 Common JUnit。

## 审计后修复（2026-10-27）

用户要求修复阶段 1–6 审计发现，禁止新增测试，功能开关允许下次启动生效。本轮已修复 F1–F6：恢复包迁移后的访问与既有夹具装配；保存结果覆盖真实索引刷新并提供恢复/重试；普通播放贯穿宿主结构来源；NBT 过滤由运行实例独占；AI 提交与取消/作用域关闭确定先后；反馈协议表和刷新依赖由产品装配连接。JSON、工作区路径、配置与游戏注册 ID、34 个 payload codec 和 24 个 envelope 序号保持不变。

验证：两端 build、既有 Common JUnit（61 项中 60 通过、1 个真实 LLM 用例按原条件跳过）、双端界面 smoke、NeoForge 现有场景普通 Ponder 播放 smoke 均通过。新增运行 Mixin 的 Fabric 映射与两端最终 JAR 配置已核对。没有新增测试文件、用例、断言、脚本或 smoke 夹具；只为原投影时间线测试调整显式运行宿主装配及测试运行依赖。

现有成功保存、编辑返回/定位、同步冲突策略、AI 保存/复查流程保留；刷新失败现在明确报告，已写盘结果不冒充磁盘保存失败，AI 已完成提交也不再改标取消。具体调用链、验证命令和未实测范围见 [审计修复记录](MODULARIZATION_AUDIT.md#修复状态2026-10-27)。本轮仍不实施阶段 7。

## 功能链路复审后的补充修复（2026-10-27）

复审发现的 R1/R2 已完成源码修复：世界切换/停用 AI 在关闭任务作用域后显式释放生成界面的任务状态；同步覆盖返回磁盘写入数量与发布异常，最终提示保留刷新失败和重试路径。生成请求被同步拒绝时也会恢复界面可操作状态。原有草稿、运行历史、协议、冲突和保存算法保留，没有新增测试。

两端 build 和 Common 既有测试通过（60 通过、1 跳过）。最新 smoke、打包检查和仍未实测的场景见 [复审修复记录](MODULARIZATION_REAUDIT.md#r1r2-修复状态2026-10-27)。此处源码修复不等于阶段 6 的独立宿主、多人或专用服务器已完成运行验收。
