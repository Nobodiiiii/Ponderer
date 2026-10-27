# 模块化阶段 1–6：功能链路与修复后复审

日期：2026-10-27。对象为 `1.21.1` 分支当前工作区（`ea725e07` 加任务开始时已有的 F1–F6 修复），同时对照模块化前 `457d0619` 和修复前 `ea725e07`。以当前 `settings.gradle` 为准，活动模块为 Common、NeoForge、Fabric，Minecraft 1.21.1。阶段 7 不在范围内。

最初复审只新增本报告；随后用户要求修复问题并提交，R1/R2 的源码修复与新一轮验证见下。先前报告中的“六项完成源码修复”不代表所有用户操作链路已完成运行验收。

## R1/R2 修复状态（2026-10-27）

- R1：世界/连接变化以及停用 AI 时，由产品装配在关闭生成作用域后显式调用 `AiGenerateScreen.resetGenerationScope()`，释放任务引用并清除“生成中/取消中”。保留用户输入、草稿、输出和运行历史；作用域结束提示引导查看历史，不把已经落盘的运行改标为取消。旧任务仍受代次和任务身份校验约束。同步拒绝启动时也会释放界面忙碌状态，允许后续重新尝试。
- R2：同步工作区发布回调逐项执行并返回汇总异常；装配传入会抛出失败的完整刷新操作。`RemotePullClient` 在最终摘要中保留刷新错误，并将 `OverwriteResult(applied, refreshFailure)` 返回界面。覆盖界面分别显示磁盘写入数量和刷新失败，错误与 `/ponderer reload` 提示不会被成功消息清掉。payload、冲突与写盘计数算法保持不变。
- 构建与既有 JUnit：`./gradlew.bat :NeoForge:build :Fabric:build :Common:test --offline --no-daemon --console=plain --continue` 通过，28 秒；61 项中 60 通过、1 个真实 LLM 用例按原条件跳过。见 [构建日志](D:/Files/ponderer/build/modularization-final-fixes/build-tests.log)。没有新增测试文件、用例、断言、夹具或测试脚本。
- 双端界面 smoke：`./gradlew.bat quickPlaySmoke -PskipBuild -PsmokeTimeout=180 -PsmokeInterfaces --offline --no-daemon --console=plain` 通过，约 79 秒；两端均实际启动且 `interfaces PASS (intercepted=7, taintDrops=0)`。见 [双端日志](D:/Files/ponderer/build/modularization-final-fixes/smoke.log)。NeoForge 再执行 `python test.py neoforge --skip-build --smoke --smoke-timeout 180 --smoke-interfaces --smoke-ponder-item minecraft:grass_block`，进入 `PackagePortScreen` 并通过观看/交互切换，见 [播放日志](D:/Files/ponderer/build/modularization-final-fixes/ponder-playback-smoke.log)。
- 最终 JAR：两端各 5 份配置，41/42 个 Mixin 类全部存在且无重复；Fabric 结构加载描述符的 `class_2960 / class_3499` 映射正确，644 个活动主输入均有归属。唯一有变动的既有投影测试仍为 8 个用例、22 处断言，仅保留前轮的显式宿主装配调整。
- 标准 smoke 仍有原空 ID 自检夹具触发的 `String.indexOf` NPE，世界采集夹具仍 unavailable；没有为通过验证修改它们。所有运行使用超时及进程树清理，两端临时失焦暂停设置均在 finally 中恢复。
- R1 的真实生成中世界切换、R2 的刷新异常注入仍未运行，不能把构建和既有 smoke 当作这两个场景的运行复现。独立宿主、多人和专用服务器的原验收边界也仍保留。

下文保留修复前发现和该次复审的原始验证证据；其中“未修复”“下一步”等判断描述的是修复前状态。阶段 6 的独立宿主、多实例、专用服务器与完整人工验收边界仍然存在。

## 修复前结论

主成功路径未发现新的算法级断链；原有 JSON、包布局、协议及主要步骤执行逻辑有兼容证据。F1–F6 的主要修复可在当前源码中确认，构建与既有测试通过。但仍有一项 P1 和一项 P2：AI 作用域结束后没有完成界面任务状态清理；远程冲突覆盖丢失刷新失败结果。因而不能判定阶段 1–6 已完全验收，也不能宣称用户可感知行为完全相同。

下述缺陷由完整源码调用链确认，未进行真实 LLM 或刷新异常注入复现。运行检查的通过和失败均单独记录，不将夹具不适用当作产品回归。

## R1 · P1 · AI 作用域结束后，生成界面永久保留忙碌状态

主要位置：[AiSceneGenerator.java:140](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ai/AiSceneGenerator.java:140)。相关位置：[ClientCatalogBindings.java:79](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/bootstrap/ClientCatalogBindings.java:79)、[AiGenerateScreen.java:830](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ui/AiGenerateScreen.java:830)、[AiGenerateScreen.java:945](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ui/AiGenerateScreen.java:945)。

触发条件：AI 尚未结束时离开世界、断开连接或切换维度，随后再次打开 AI 生成界面。

调用链：`ClientFeatureLifecycle.tick()` 检测世界/连接变化 → `resetWorld()` → `AiSceneGenerator.cancelAll()` 推进 `epoch` 并取消任务 → completion 发现代次不同，直接返回，不调用成功、失败或取消回调。

而 `AiGenerateScreen` 的 `cachedGenerating / cachedCancelling / cachedGenerationTask` 都是跨屏幕保留的静态状态；正常结束仅由这三个回调清理。世界清理列表未包含该界面的状态重置，打开界面时也未从 `task.completion().isDone()` 恢复状态。再次生成和恢复任务均被 `cachedGenerating` 拦截；点击取消只设置 `cachedCancelling=true`，已经结束的 completion 不会再次通知界面。因此这一入口会停留在“生成中/取消中”，通常需重启客户端恢复。

比较：模块化前没有这一代次短路，会执行终态回调；阶段 6 引入的旧作用域回调抑制有必要，但缺少与之配套的界面状态收尾。当前提交互斥修复解决了“取消与落盘”的先后问题，尚未闭合“取消 → UI 可再次生成”链路。

修复方向：为产品 AI 界面提供明确的作用域结束清理，或将终态任务状态与面向旧世界的成功/错误通知分开处理。保留草稿、历史和已完成提交的语义，避免恢复失效世界回调。

## R2 · P2 · 远程管理的本地冲突覆盖会清掉刷新错误

主要位置：[RemoteBrowserScreen.java:254](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ui/RemoteBrowserScreen.java:254)。相关位置：[RemotePullClient.java:149](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/sync/client/RemotePullClient.java:149)、[ClientCatalogBindings.java:20](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/bootstrap/ClientCatalogBindings.java:20)、[AbstractDeclarativeScreen.java:94](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ui/widgets/AbstractDeclarativeScreen.java:94)。

触发条件：在远程管理界面选择“覆盖”待处理冲突，本地写入成功，但随后的场景索引刷新抛出异常。

调用链：`overwriteUpdates()` → `RemotePullClient.overwritePending()` → `finishSession()` → `SyncClientEvents.workspaceChanged()` → 装配的 `refreshAndReport()`。协调器会报告 ERROR 并返回 `-1`，但事件仅传递 `Runnable`，返回值丢失；`overwritePending()` 仍按磁盘写入计数返回成功。`finishSession()` 随后发布成功摘要，外层 `overwriteUpdates()` 又执行 `setInfoMessage(overwritten)`；该方法明确将 `errorMessage` 清空。

此入口是本地按钮操作，没有 `ClientFeedback.receive()` 的请求上下文。在线请求路径对多条反馈汇总并保留最高错误级别的机制，不能保护这条链路。用户最终看见“已覆盖”，运行目录却可能仍是协调器恢复的旧目录，重试提示也丢失。

比较：旧问题属于刷新结果没有进入用例结果边界；当前保存/导入/AI 多数入口已补齐，但这里仍保留仅含写入数量的结果。不能据此认定原 F2 已覆盖所有同步入口。

修复方向：同步完成/本地覆盖结果应区分磁盘提交与发布状态；让界面消费最终结果后决定提示，保留刷新失败及 `/ponderer reload` 重试信息。无须改变 payload 布局或冲突策略。

## 链路与行为比较

| 阶段/入口 | 本次核对结果 | 验收边界 |
| --- | --- | --- |
| 1：场景契约、标准化、NBT 与界面模型 | 数据模型与叶子能力已分开；既有场景、界面、NBT 测试通过；`sourceFile` 在运行快照中显式保留 | 已验证相关现有行为，不能外推为所有历史包都已实测 |
| 2：编辑步骤保存、元信息、触发器 | 写盘 → 完整目录/索引刷新 → 保存结果 → 预览或屏幕对象更新；磁盘成功与刷新失败分开处理 | 正常持久化与编辑算法的源码链路保留；恢复旧索引及异常提示未做故障注入 |
| 2/5：导入、复制、删除、转换、AI 撤销 | 入口已使用统一刷新；导入/AI 主要入口不再在捕获刷新失败后直接追加成功提示 | 本地远程冲突覆盖例外，见 R2；未逐个进行人工操作 |
| 3：普通播放、编辑预览、投影 | 普通 Ponder 通过实例结构句柄进入同一 `StructureSource`；直接编译使用原始结构 ID；场景标题/ID 仍由原 storyboard 生成；步骤主体大多原样迁移 | NeoForge 现有嵌入界面场景实际播放通过；独立宿主和多实例未实测 |
| 3：NBT 过滤 | 插件仅清理自己的 Registration；汇总查询保留原匹配及全部过滤后的兜底规则 | 多实例隔离为源码确认；未做双宿主运行验收 |
| 4：编辑、撤销/重做、分段、采集与输入 | 会话封装保留原列表变更、历史及返回定位流程；编辑预览会关闭其 PlaybackSession；运行指令通过扩展通知编辑侧 | 既有模型测试及界面 smoke 覆盖有限；完整撤销/保存/返回组合未人工验收 |
| 5：同步、协议、蓝图、投影平台与兼容 | DTO/处理器及平台能力已分开；协议 ID、编解码正文和 envelope 顺序一致；服务器功能快照沿用捕获/回退逻辑 | 无多人或专用服务器互通实测；功能开关按既有修复记录允许下次启动生效 |
| 5/6：AI | 冲突检查、撤销记录、落盘与主线程刷新仍在；取消/代次校验与最终提交有同一顺序边界 | 回调与界面任务清理没有闭合，见 R1；无真实 LLM 或并发复现 |
| 6：依赖与生命周期 | 产品装配安装反馈协议表、同步刷新和投影失效订阅；运行/工作区未发现反向产品实现引用；输入、预览、沙盒等有关闭路径 | R1 阻断完整生命周期验收；内存/只读来源、分别不装配功能、专用服务器仍缺运行证据 |

“相同”应限定为已有兼容证据的正常路径。刷新失败现在明确提示、取消先发生则不写盘，是原修复的预期行为变化；不应要求这些错误路径继续复现旧缺陷。R1、R2 是完成这些变化时仍遗漏的收尾。

## 本轮验证

Java：`C:/Users/Nobodiiiii/.jdks/ms-21.0.9`，仅对本轮子进程设置。

| 检查 | 证据与结果 |
| --- | --- |
| 两端构建与既有 JUnit | `./gradlew.bat :NeoForge:build :Fabric:build :Common:test --offline --no-daemon --console=plain --continue --rerun-tasks`：Gradle 通过，38 秒，34 项任务全部执行；61 项测试中 60 通过、1 个真实 LLM 按原条件跳过。见 [build-tests.log](D:/Files/ponderer/build/modularization-reaudit/build-tests.log)。外层打印日志时遇到终端 GBK 编码错误，Gradle 子进程退出码为 0，另行核对原始日志与 XML；没有因此重跑测试 |
| 线格式静态对照 | 34 个 payload 的 TYPE 初始化、92 个 encode/decode/type 方法，去除空白、注释和内部包前缀后未见正文差异；产品注册表 24 个 envelope 项与修复前顺序一致。这是源码兼容检查，不是网络互通实测 |
| 归属和最终 JAR | 当前 644 个 main 输入均在 CSV 中，CSV 路径全部存在；两端各 5 份 Mixin 配置，Fabric 41 个、NeoForge 42 个，无重复且类全部打包 |
| Fabric 映射 | `PonderStructureSourceMixin.loadSchematic` 描述符生成 `class_2960 / class_3499` 映射；当前 NeoForge ModDev 产物没有独立 refmap，不套用旧 Forge 名称要求 |
| 两端扩展 smoke | `./gradlew.bat quickPlaySmoke -PskipBuild -PsmokeTimeout=180 -PsmokeInterfaces -PsmokePonderItem=minecraft:grass_block --offline --no-daemon --console=plain`：NeoForge 实际打开 `PackagePortScreen`，观看/交互切换通过，`interfaces PASS (intercepted=7, taintDrops=0)`；Fabric 无草方块场景，空列表触发 `PonderUI.<init>` 的 `IndexOutOfBoundsException`，整体命令失败。见 [smoke.log](D:/Files/ponderer/build/modularization-reaudit/smoke.log) |
| Fabric 现有场景检查 | 改用已有 `minecraft:oak_log` 场景。一次进入世界后未进入 smoke 用例，180 秒超时；临时关闭失焦暂停后进入了普通 Ponder，但首段没有界面，既有 smoke 的嵌入界面断言失败。源码/JSON 确认界面位于第二段，smoke 只检查当前首段，故这两次均不作为产品回归证据。见 [暂停运行日志](D:/Files/ponderer/build/modularization-reaudit/fabric-playback-smoke.log)、[现有场景日志](D:/Files/ponderer/build/modularization-reaudit/fabric-playback-smoke-unpaused.log) |
| Fabric 标准界面 smoke | `python test.py fabric --skip-build --smoke --smoke-timeout 180 --smoke-interfaces`：实际启动、执行沙盒与编辑界面阶段，`interfaces PASS (intercepted=7, taintDrops=0)`，退出码 0。见 [fabric-interface-smoke.log](D:/Files/ponderer/build/modularization-reaudit/fabric-interface-smoke.log)。保留原自检空 ID 预览产生的 NPE 日志，未为使检查通过而修改夹具 |

运行均使用现有 `test.py`：每端 180 秒限制、finally 清理进程树，外层 Gradle smoke 600 秒、单端 300 秒、构建 900 秒限制。临时修改的 Fabric `pauseOnLostFocus` 在每次运行的 finally 中恢复原始文件内容。未新增测试、断言、夹具或测试脚本。

日志检查遵循全局 `minecraft-crash-triage` 技能，先读本项目 `.minecraft/versions/1.21.1-{Fabric,NeoForge}/logs/latest.log`。异常类型均为客户端：Fabric 扩展检查的空列表错误发生在自检选择不存在场景时；标准 smoke 最早的相关 NPE 为 `String.indexOf`，调用链来自 `InterfaceSmokeTest` 构造空 ID 文档后的 `SceneCompiler.createStoryBoard`，与原记录的夹具限制一致，下一处应检查 EDITOR 阶段的空文档构造。NeoForge 保留已有 Curios 槽位错误。世界采集夹具仍显示 unavailable。项目 `.minecraft/crash-reports/` 不存在；版本实例中的旧崩溃报告属于此前启动失败，不当作本次新故障。运行日志快照保存在 `build/modularization-reaudit/`。

未验证且不能由 build/smoke 推导：R1 的真实生成中世界切换、R2 的索引异常注入；内存/只读宿主和多宿主并存；完整人工编辑与多人同步；功能分别停止/不装配；专用服务器；缺少可选兼容模组。下一步应先修复 R1/R2，再通过既有功能入口检查上述终态，不新增测试文件。完整功能等价性目前仍不成立。
