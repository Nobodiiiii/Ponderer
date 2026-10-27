# 模块化阶段 1–6 实施记录

范围：Minecraft 1.21.1；活动构建项目仍为 Common、NeoForge、Fabric。阶段 7 的独立 JAR 不在本次范围。

本次用户明确要求不运行测试与验收。因此未执行 Gradle、JUnit、客户端 smoke 或人工运行验收；源码检查只用于迁移与代码审阅，不构成功能等价证明。未新增测试。

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
