# 模块化阶段 1–6 审计

日期：2026-10-27。审计对象：`1.21.1` 分支、`ea725e07`，对照实施前基线 `457d0619`、模块化方案、实施记录和归属清单。实际活动模块为 `Common / NeoForge / Fabric`，Minecraft 1.21.1、Java 21。

## 修复状态（2026-10-27）

用户随后要求修复 F1–F6，禁止新增测试，并允许配置开关下次启动生效。六项均已完成源码修复；下文原始发现和分阶段判断保留为修复前证据。构建、既有 JUnit 和 smoke 的结果不等于全部功能已完成运行验收。

后续功能链路复审又确认 AI 界面任务状态清理与远程冲突覆盖提示仍有遗漏；R1/R2 已补充修复，最新源码行为、验证证据与未实测边界见 [复审修复记录](MODULARIZATION_REAUDIT.md#r1r2-修复状态2026-10-27)。

| 项目 | 本轮修复 | 证据与边界 |
| --- | --- | --- |
| F1 | `IdentityRemapper.splitPath` 恢复跨包访问；既有投影时间线夹具显式安装、关闭运行宿主，Common 为其补齐 Ponder 测试运行依赖 | 61 项既有 JUnit：60 通过、1 个真实 LLM 用例按原条件跳过；没有增加测试文件、用例、断言或测试脚本 |
| F2 | 保存、元信息、触发器、导入、删除、复制、转换、同步和 AI 的发布进入统一刷新边界；真实索引成功后才推进目录版本；逐个隔离监听异常 | `SaveOutcome` 捕获实际索引失败；恢复旧目录并尝试重建旧索引，保留恢复异常；提示 `/ponderer reload` 重试。AI 生成、撤销与导入不会用随后的成功消息掩盖刷新错误；异常注入恢复尚未运行 |
| F3 | 每个运行实例注册内部结构句柄，原生 Ponder 的结构加载 Mixin 按句柄调用该实例的 `StructureSource` | 产品来源仍加载原始 ID，保持本地文件/资源优先级；序列化结构 ID 和场景标题/ID 未变。NeoForge 普通 Ponder 播放 smoke 通过；独立内存宿主未实测 |
| F4 | NBT 绑定由每个插件独占的 `Registration` 保存；重注册与关闭只清理自身绑定，原生界面桥只汇总查询 | 匹配、无过滤放行及全部过滤后的兜底算法保留；运行实例关闭释放自身资源句柄和绑定。双实例并存目前为源码链路审阅 |
| F5 | 最终保存取得仓库锁后，在同一作用域锁内验证代次/安装状态，再通过取消提交锁落盘；取消和提交有确定先后顺序 | 取消先发生则不写场景；提交先完成则保留完成状态，作用域关闭不再将已落盘结果标成取消。保留基线冲突检查、撤销日志、正常保存/刷新/成功回调；没有调用真实 LLM 或运行竞态复现 |
| F6 | 产品装配安装固定顺序的反馈协议表；同步发布工作区事件，装配注册刷新回调；投影仪消费装配传入的订阅能力 | feedback 不再引用具体业务协议；sync/projector 不再引用 `SceneRefreshCoordinator` 实现。14 个请求、10 个响应的 envelope 序号逐项一致；34 个业务 payload 的 ID/codec 未改 |

### 功能链路与用户可感知行为

| 入口 | 修复后的完整链路 | 与修复前成功行为的比较 |
| --- | --- | --- |
| 编辑步骤、撤销/重做、删除/排序/分段 | 编辑会话 → 工作区保存 → 目录发布与 Ponder 索引重载 → 预览重新加载 → 原返回/定位流程 | 沿用原草稿/历史、磁盘格式、即时保存和返回定位；刷新失败时明确说明已写盘并给出重试路径 |
| 元信息、触发器 | 候选文档 → 同一保存用例 → 同步屏幕对象和基线 → 原返回路径 | 持久化字段与成功交互不变；刷新失败不误报为磁盘写入失败 |
| 导入、复制、删除、PonderJS 转换 | 原工作区操作 → 同一完整刷新 → 更新列表/提示 | 工作区的路径、备份、导入顺序和删除算法未改；刷新错误不会被下一条成功提示覆盖 |
| 手动/自动远程拉取 | 原解码/权限/冲突处理 → 工作区与同步元数据 → 工作区变更事件 → 装配刷新 → 待处理列表/反馈 | payload 与 envelope 布局、冲突策略、同步摘要保留；关闭同步时同时解除刷新订阅 |
| 普通 Ponder 与直接编译 | 同一运行实例资源来源 → 模板 → 原 storyboard → 原场景 ID/物品/NBT 过滤 → 播放 | 产品资源选择及场景 ID 保留；普通播放现在也能使用宿主注入来源，实例不再互清过滤状态 |
| AI 生成、取消、世界变化、撤销 | 原生成管线 → 基线/覆盖检查与撤销记录 → 有序提交 → 主线程完整刷新 → 原成功或明确刷新错误提示 | 成功生成/保存/复查流程保留；修正取消后仍写场景及已完成结果被改标取消的行为；草稿和日志保留 |
| 功能开关 | 保持现有配置与启动装配 | 按用户边界允许下次启动起效；本轮没有扩展为动态撤销服务器注册的机制 |

### 修复验证

- `./gradlew.bat :NeoForge:build :Fabric:build :Common:test --offline --no-daemon --console=plain --continue`：最终通过，约 26 秒；[日志](D:/Files/ponderer/build/modularization-fixes/verification-final.log)。最初恢复编译后暴露的旧投影夹具装配和运行依赖缺失已修正，断言与用例数量保持不变。
- `./gradlew.bat quickPlaySmoke -PskipBuild -PsmokeTimeout=180 -PsmokeInterfaces --offline --no-daemon --console=plain`：Fabric、NeoForge 均实际启动并 `interfaces PASS (intercepted=7, taintDrops=0)`；[日志](D:/Files/ponderer/build/modularization-fixes/smoke.log)。
- `python test.py neoforge --skip-build --smoke --smoke-timeout 180 --smoke-interfaces --smoke-ponder-item minecraft:grass_block`：普通 Ponder 播放进入 `PackagePortScreen`，嵌入界面、观看/交互切换检查通过；[日志](D:/Files/ponderer/build/modularization-fixes/ponder-playback-smoke.log)。使用现有场景，没有新增夹具或测试。
- Fabric 新 Mixin 的 `loadSchematic` 描述符已生成 `class_2960 / class_3499` 映射；最终两端 JAR 分别有 41/42 个 Mixin，配置类全部存在，无重复。NeoForge 当前 ModDev 产物无单独 refmap，已由实际启动/播放验证注入。
- 归属表覆盖 644 个活动主输入（579 个 Java、64 个资源、1 个元数据模板），83 个两端 Mixin 配置项均有归属；CSV 路径均存在。固定协议表 24 个项与修复前顺序一致。
- 所有启动均有每端 180 秒限制及清理，外层构建 900 秒、双端 smoke 600 秒、单端播放 300 秒超时；启动的客户端 PID 13968、12848、26192 均已确认退出。

客户端日志按 `minecraft-crash-triage` 技能核对：Fabric 最早相关异常仍为既有空 ID 夹具产生的 `NullPointerException`（`String.indexOf`，来自 `InterfaceSmokeTest` 创建的空文档预览），并非新增结构来源注入失败；下一处相关源码为 `InterfaceSmokeTest` 的 EDITOR 阶段空文档构造。NeoForge 日志保留原有 Curios `scroll/spellbook` 槽位错误，普通 Ponder 检查未见该空场景异常。两端仍跳过 `world capture fixture unavailable`；项目 `.minecraft/crash-reports/` 不存在。日志快照保存在 `build/modularization-fixes/{Fabric,NeoForge}-latest.log`。这些限制没有被 smoke PASS 掩盖，也未为了通过验证修改既有 smoke。

仍未运行：独立内存/只读宿主、两个宿主并存、专用服务器、完整人工编辑/多人同步、刷新异常注入与恢复、真实 AI 和并发竞争复现。这些是运行验收边界；源码修复与已运行检查均已完成。

## 修复前审计结论

包级迁移、双端编译打包和界面沙盒启动已成立，但不能将阶段 1–6 判为全部验收通过。发现 1 项 P1 和 5 项 P2：既有测试无法编译；保存结果没有覆盖真实索引刷新；宿主结构来源没有贯穿普通播放；运行实例共享过滤状态；AI 作用域关闭未约束最终落盘；业务依赖仍有闭环。

阶段 7 的物理 Gradle 模块和独立发行 JAR 本来就不在本次范围，其缺失不计为缺陷。最初审计仅新增本页文档，未修复业务源码，也未新增测试；后续修复见上方记录。

## 发现

### F1 · P1 · 包迁移破坏了既有测试的访问权限

位置：[IdentityRemapper.java:206](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/support/nbt/IdentityRemapper.java:206)、[InterfaceModelTest.java:271](D:/Files/ponderer/Common/src/test/java/com/nododiiiii/ponderer/interfaces/model/InterfaceModelTest.java:271)。

`IdentityRemapper` 已由 `interfaces.model` 移到 `support.nbt`，但 `splitPath(String)` 仍为包可见；原测试保留在 `interfaces.model`，三处直接调用均不可访问。本轮 `:Common:test` 实际停在 `:Common:compileTestJava`，报出三个访问权限错误，JUnit 尚未开始执行。这是包迁移引入的确定性回归，不能用两个加载器的 build 成功代替 Common 测试通过。

建议同步调整既有测试与实现的访问边界，保留原有断言；先恢复 `:Common:compileTestJava`，再运行现有相关测试，不增加测试文件或用例。

证据：[build-tests.log](D:/Files/ponderer/build/modularization-audit/build-tests.log)。

### F2 · P2 · SaveOutcome 没有包含真实的 Ponder 刷新结果

位置：[SceneApplicationService.java:15](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/SceneApplicationService.java:15)、[SceneEditorScreen.java:891](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ui/SceneEditorScreen.java:891)、[SceneRefreshCoordinator.java:13](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/SceneRefreshCoordinator.java:13)。

保存用例只执行 `loadCatalog()` 就返回 `refreshFailure = null`；真正的 `PonderIndex.reload()` 由屏幕在返回后另行提交给客户端执行器。因此索引注册抛错时，结果对象已经报告刷新无错误，屏幕的“已保存、刷新失败”分支捕获不到这次失败。目录版本也在索引刷新之前递增；索引失败后没有保留最近可用索引或显式重试的路径。修改信息、触发器、导入和同步仍各自拼接“写入 → loadCatalog → reloadPonderIndex”，未完整进入同一个用例结果边界。

同一协调器的失效监听器在 `finally` 中直接循环调用；任一监听器抛错会跳过后续监听器，并可能遮蔽索引本身的异常，不符合方案关于监听器失败隔离的约定。

建议由应用协调器完成或追踪整次发布与索引重载，明确目录版本和刷新完成状态，统一返回保存/刷新结果；逐个隔离监听器异常并提供刷新重试。代码路径已确认，尚未执行异常注入和恢复验收。

### F3 · P2 · 宿主 StructureSource 只接入了直接编译路径

位置：[DynamicPonderPlugin.java:56](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/runtime/core/DynamicPonderPlugin.java:56)、[SceneCompiler.java:223](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/runtime/core/SceneCompiler.java:223)、[Fabric 结构加载 Mixin:51](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryMixinFabric.java:51)；NeoForge 存在相同产品路径适配。

`engine.compile()` 确实调用注入的 `StructureSource.loadSchematic()`；但 `engine.plugin()` 注册普通 Ponder 场景时只把结构 ID 传给 `addStoryBoard()`。Ponder 随后通过自己的静态 `loadSchematic()` 加载基础结构，不会调用该运行实例的来源。现有加载器 Mixin 仅为 `ponderer` 命名空间访问产品 `WorkspaceAccess`，没有宿主来源路由。

具体受影响的接入：宿主使用自己的命名空间和内存结构来源，直接编译能取得结构；按归属文档示意注册 `engine.plugin()` 后，从普通 Ponder 入口播放相同场景，基础结构仍走资源管理器，缺失时成为空模板。为宿主重新写一套结构加载 Mixin 才能绕过这一缺口，不符合已声明的资源 SPI 边界。

已先检查本地 [PonderSceneRegistry.java:103](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/registration/PonderSceneRegistry.java:103)，并以 Gradle 缓存中的 `Ponder-Common-1.21.1-1.0.69-sources.jar` 核对了相同调用及缺失资源返回空模板的行为；没有反编译或联网查询。

建议在注册/加载适配边界按运行实例路由基础结构来源，使普通播放和直接编译消费同一来源。此项为源码调用链确认，尚未用独立宿主实际播放复现。

### F4 · P2 · 多个运行实例会互相清除 NBT 绑定

位置：[DynamicPonderPlugin.java:87](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/runtime/core/DynamicPonderPlugin.java:87)、[NbtSceneFilter.java:41](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/runtime/api/NbtSceneFilter.java:41)。

虽然 `SceneCatalog` 和 ID 索引已实例化，NBT 绑定仍保存在静态 `FILTERS / ALL_SCENES`。每个 `DynamicPonderPlugin.registerScenes()` 开头都执行全局 `NbtSceneFilter.clear()`。两个不同命名空间的 `RuntimeEngine` 同时注册时，Ponder 逐个调用插件注册，后一个实例会删除前一个实例刚注册的过滤信息；前者的悬停可见性和播放过滤随后退化为未注册过滤时的放行行为。

本地 [PonderIndex.java:74](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/PonderIndex.java:74) 以及准确版本的 sources JAR 均确认逐插件注册顺序。当前单产品 smoke 只有一个产品运行实例，不覆盖该情况。

建议将绑定归运行实例所有，或由统一注册适配器按所有者替换/移除绑定；单个插件不得清空所有实例的状态。尚未执行双宿主运行验收。

### F5 · P2 · AI 作用域失效检查发生在落盘之后

位置：[AiSceneGenerator.java:120](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ai/AiSceneGenerator.java:120)、[AiSceneGenerator.java:151](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/application/ai/AiSceneGenerator.java:151)、[ClientCatalogBindings.java:79](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/bootstrap/ClientCatalogBindings.java:79)。

世界变化和 `stopAi()` 会增加 `epoch` 并取消任务，但 `requestEpoch` 只在主线程 completion 回调中核对。工作线程在落盘前检查一次取消，随后进入 `saveResult()`；该方法不接收作用域或取消标识，而且可能等待仓库锁。若任务通过取消检查后等待锁，此时关闭 AI 或切换世界，任务取得锁后仍会调用 `saveSceneToLocalDetailed()` 覆盖目标文件；完成回调随后把这次运行标成 cancelled，文件却已经提交。

旧 AI 保存流程原本就有最终取消窗口；本项是阶段 6 新增作用域隔离仍未覆盖持久化边界，并非声称整个竞态首次由本次迁移产生。

建议让最终提交携带作用域代次和取消令牌，在取得仓库锁后验证，并使作用域关闭与提交有明确的原子先后关系。当前仅完成并发时序审阅，未调用真实 LLM，也未做该竞争条件的运行复现。

### F6 · P2 · 仍存在业务依赖闭环和对应用实现的反向引用

位置：[FeedbackPackets.java:3](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/feedback/protocol/FeedbackPackets.java:3)、[RemotePullClient.java:153](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/sync/client/RemotePullClient.java:153)、[ProjectorClientModule.java:3](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/features/projector/client/ProjectorClientModule.java:3)。

反馈协议的静态 allowlist 直接引用同步、蓝图和投影仪的具体 DTO/codec；这些功能的处理器或客户端又依赖反馈能力，形成逻辑上的 `feedback ↔ sync / blueprint / projector`。初始化反馈 envelope 需要连带解析这些功能的协议类。归属文档虽然将 envelope 分配给反馈，但没有消除方案阶段 6 要求移除的业务依赖环。

同步完成直接调用 `application.SceneRefreshCoordinator`，投影仪模块也直接订阅该应用实现，而非消费应用 API 或由 bootstrap 连接。应用层同时组合这些功能，因而仍存在跨层实现依赖。两个加载器把所有 Common 源码合并编译，所以目前 build 不会暴露未来物理拆分时的这些边界问题。

建议让产品装配传入保留现有顺序的协议注册表，并由应用用例/装配回调处理刷新与失效订阅。必须保持 envelope 索引、payload ID 和 codec 布局兼容。该项已通过 import、完整限定类名和调用搜索确认，不依赖运行猜测。

## 分阶段判定

| 阶段 | 已确认成果 | 验收判断 |
| --- | --- | --- |
| 1 场景与叶子能力 | `scene` 不导入游戏/业务实现；标准化默认值显式传入；结构/NBT 支持已迁移 | 结构完成；F1 阻断既有行为测试，不能判完整验收通过 |
| 2 工作区与应用用例 | 工作区不导入运行层、编辑 UI 或产品实现；保存不再借用投影缓存刷新 | 部分完成；F2 的保存/刷新结果和异常恢复尚未闭合 |
| 3 运行服务 | `runtime` 不引用产品/工作区/编辑/AI 等包；有资源 SPI、快照、实例目录和直接编译接口 | 部分完成；F3、F4 阻止普通播放来源一致性和实例隔离成立 |
| 4 编辑、播放与采集 | 会话、预览、采集职责已分开；运行/编辑 Mixin 分开；双端界面 smoke 通过 | 主路径通过有限验证；完整编辑保存/返回/撤销、停用后的行为尚未验收 |
| 5 同步与产品功能 | 34 个 payload 的 TYPE 初始化和 codec/type 方法保持兼容；协议与处理器分离；功能包与平台接口已迁移 | 部分完成；F5、F6 仍影响作用域和装配边界；相关 JUnit 被 F1 阻断 |
| 6 依赖收口与逻辑独立 | SceneStore 等旧入口已移除；641 个输入均有归属；存在客户端安装/清理句柄 | 未达到验收条件；业务闭环、共享状态和独立宿主缺口仍在 |

产品 UI 集中到 `application.ui`、编辑内核保留会话/预览/采集，是实施记录明确说明的范围调整，本审计没有仅因目录与初稿不同就计为缺陷。Config 集中保存、发布形式不变也符合当前记录的阶段范围。

## 本轮验证证据

执行 Java 使用 `C:/Users/Nobodiiiii/.jdks/ms-21.0.9`，仅为本轮进程设置，不修改系统 Java 配置。

| 验证 | 结果和限制 |
| --- | --- |
| `./gradlew.bat :NeoForge:build :Fabric:build :Common:test --offline --no-daemon --console=plain --continue` | 两端 build 完成；整体因 `:Common:compileTestJava` 三处访问权限错误失败，JUnit 未执行。外层 900 秒超时，实际约 13 秒。见 [日志](D:/Files/ponderer/build/modularization-audit/build-tests.log) |
| `./gradlew.bat quickPlaySmoke -PskipBuild -PsmokeTimeout=180 -PsmokeInterfaces --offline --no-daemon --console=plain` | 双端实际启动，各自 `interfaces PASS (intercepted=7, taintDrops=0)`，整体成功约 81 秒。每端 180 秒超时及 finally 清理，外层 600 秒超时。PID 4168、5228 已退出。见 [日志](D:/Files/ponderer/build/modularization-audit/smoke.log) |
| 归属 CSV 与 git 跟踪的活动模块 main 输入对照 | 641 个输入全部覆盖；CSV 引用路径均存在 |
| 当前 1.21.1 / 1.11.0 两端最终 JAR | 每端 5 份配置；Fabric 40 个、NeoForge 41 个 Mixin，目标类均存在，无重复条目，无旧 `ponder/` 类 |
| Fabric 生成 refmap | 应用 UI/坐标采集和反馈访问器具有 `class_* / method_* / field_*` 映射；最终 JAR 携带 refmap。NeoForge 当前 ModDev 产物无独立 refmap，不套用 1.20.1 Forge 的命名要求 |
| 34 个传输 DTO 与 `457d0619` 源码对照 | TYPE 初始化一致；提取出的 92 个 encode/decode/type 方法在归一化空白、注释和内部包名后未发现正文差异；不等于在线协议互通实测 |
| 依赖方向 | 搜索 import 与完整限定类名，runtime、workspace、support 未发现禁止的产品实现引用；其他业务闭环见 F6 |

两端 smoke 都跳过 `world capture fixture unavailable`。自检空 ID 预览日志问题已由前次实施记录说明为基线夹具限制，本轮没有将它计为新增回归；本轮 smoke 也不能证明其消失。

未覆盖：独立内存/只读宿主、两个运行实例并存、专用服务器、缺少可选兼容模组、刷新异常恢复、完整编辑/多人同步流程、功能逐项停用/重装以及 AI 取消与落盘竞争。没有启用真实 LLM。

修复前的建议是先处理 F1，下一条验证命令为 `./gradlew.bat :Common:test --offline --no-daemon --console=plain`；随后修复刷新和运行实例边界，再通过既有客户端/界面 smoke 与可用的人工验收路径补齐阶段 6。涉及 Mixin 修改时按仓库规则重新检查生成映射并执行 `quickPlaySmoke`。审计不要求新增测试或测试专用脚本。
