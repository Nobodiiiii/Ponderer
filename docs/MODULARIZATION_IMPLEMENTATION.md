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
