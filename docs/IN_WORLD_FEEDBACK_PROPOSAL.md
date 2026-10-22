# 世界内提示扫描与统一方案

扫描日期：2026-10-20；实现日期：2026-10-21。状态：已实现并完成构建、现有测试与双平台入世界 smoke。

按后续要求，**UI 发起的操作只回 UI 提示位，关闭后也不补发世界聊天**。第 2、3 节保留重构前扫描，行号仅供历史定位。

## 1. 范围与结论

以当前工作区为准：分支 `1.21.1`，`minecraft_version=1.21.1`；
`settings.gradle` 实际启用 `Common`、`NeoForge`、`Fabric`，不包含 `Forge`。

扫描覆盖三个活动模块中的聊天消息、ActionBar（快捷栏上方小字）、标题，
以及从世界操作进入/返回编辑器时的状态反馈、网络响应和平台登录通知。
普通按钮说明、物品 tooltip、思索教程正文、预览中的鼠标旁操作说明属于内容或局部帮助，
保留在其现有界面/渲染路径，不作为世界消息迁移。

实现按**用途**统一入口：持续操作引导走一个 ActionBar 状态入口；即时交互反馈走一个短时入口；
任务结果按发起上下文回到界面或聊天；后台通知走聊天摘要；场景触发提示保留作者配置。
成功/警告/错误是独立等级，不能靠等级决定渠道，也不能靠业务代码传 `true/false` 决定展示位置。

## 2. 重构前输出清单

直接调用统计：23 个 Java 文件，46 处消息输出调用。其中
`displayClientMessage` 28 处、`sendSystemMessage` 17 处、`setTitle` 1 处；
另有 `setTimes` 1 处用于标题时长，不计入消息数。
这些是调用位置数量，不是提示文案或实际出现次数；还需沿包装方法和响应处理检查实际用途。

下表行号为扫描时工作区行号。公共 Java 根目录为
`Common/src/main/java/com/nododiiiii/ponderer/`，表中未标模块的路径均相对此根目录。

| 入口及行号 | 触发内容 | 当前展示 | 建议类型 |
| --- | --- | --- | --- |
| `blueprint/BlueprintHandler.java:86,112,118,123,131,209,221,231,259` | 调整尺寸、无目标、选点、清除、保存成败；最终经 `sendStatus` | 全部 ActionBar | 尺寸/选点为操作状态；无目标/清除为即时反馈；保存为任务结果 |
| `ui/CoordPickState.java:93,105,213` | 三阶段选区引导、滚轮调整尺寸 | ActionBar，每 tick 重发引导，尺寸另发 | 操作状态，合并阶段与尺寸 |
| `mixin/MinecraftNbtPickMixin.java:23,30` | NBT 拾取引导 | ActionBar，随 `handleKeybinds` 刷新 | 操作状态 |
| `Fabric/.../fabric/PondererFabricClient.java:119,120` | 同一 NBT 拾取引导 | ActionBar，平台 tick 再发一次 | 删除重复发送，平台只驱动公共入口 |
| `NeoForge/.../neoforge/PondererNeoForgeClient.java:132,133` | 同一 NBT 拾取引导 | ActionBar，平台 tick 再发一次 | 同上 |
| `interfaces/capture/InterfaceCapture.java:64,69,138,166` | 打开目标界面的捕获引导；完成、取消、失败返回编辑器 | 开始发聊天；结束写 snapshot notice | 开始为操作状态；结束为表单反馈 |
| `DisabledFeatureUse.java:15,20` | 服务端禁用蓝图/投影仪后使用物品 | 红色 ActionBar | 即时交互反馈，保留服务端权威判断 |
| `ponder/TriggerManager.java:117,144,145,155` | 区域触发：自动打开、大字、持续绿色小字 | 自动开界面 / Title / ActionBar | 场景触发专用入口，保留配置 |
| `ponder/PondererClientCommands.java:847,857,892,920,935` | 命令成败、数量、路径、后续命令、资源包移除 | 通常聊天；部分命令通过临时 sink 回表单；移除直接绕过 sink | 命令反馈 / 有明确来源的任务结果 |
| `network/SyncRequestPayload.java:38` | 拉取权限不足 | 服务端聊天 | 拉取任务结果 |
| `network/SyncResponsePayload.java:254,301,319,355,360,376,418,424,438,444,469` | 手动/自动拉取摘要、冲突、写入失败、服务端跳过 | 多处服务端或客户端聊天 | 手动为任务/命令反馈，自动为后台通知 |
| `ponder/AutoRemoteSyncService.java:326,334` | 自动同步跳过过大文件、读取失败 | 服务端逐项聊天 | 后台通知，按同步批次汇总 |
| `network/UploadScenePayload.java:71,83,94,98` | 上传无权限、冲突、成功、失败 | 服务端聊天；后三者同时发结果包 | 上传任务结果，只保留一个展示出口 |
| `network/UploadResponsePayload.java:40,74` | 上传状态写回、冲突提示、远程浏览器状态 | 界面状态；无对应界面时冲突另发聊天 | 上传任务结果，先处理数据再统一通知 |
| `network/DownloadStructurePayload.java:46,54,63,76,88,90` | 下载/导入结构成败 | 服务端聊天，同时发下载结果包 | 下载任务结果，只保留一个展示出口 |
| `network/BlueprintConfigResponsePayload.java:53,61` | 蓝图配置结果 | 对应界面状态，否则聊天 | 配置任务结果 |
| `network/ProjectorFeatureConfigResponsePayload.java:54,62` | 投影仪开关配置结果 | 对应界面状态，否则聊天 | 配置任务结果 |
| `network/PermissionListResponsePayload.java:84,95` | 白名单管理结果 | 对应界面状态，否则聊天 | 权限任务结果 |
| `network/RemoteActionResponsePayload.java:34,41` | 远程拉取、删除、回滚等结果 | 按当前界面类型分支，否则聊天 | 有请求归属的任务结果 |
| `ui/ImportPackScreen.java:156,188,190` | 资源包状态解释及导入成败 | 聊天，即使导入界面仍打开 | 状态解释/失败留在界面；成功交给后继界面 |
| `ui/ReadonlyPackImportPromptScreen.java:43,68,88,90` | 从只读资源包导入后编辑 | 聊天，同时切换/返回界面 | 任务结果，明确指定后继界面接收者 |
| `ui/AiGenerateScreen.java:514,521,525,530` | AI 生成完成/失败 | 成功同时写界面状态和聊天；错误只写缓存/界面状态 | 异步任务结果，根据请求归属选择一个出口 |
| `compat/resourcify/ResourcifyCompat.java:42,46` | 未安装 Resourcify 时点击浏览 | 聊天；打开异常仅写日志 | 当前操作的失败反馈，界面优先 |
| `Fabric/.../fabric/PondererFabricClient.java:184,218,229,240` | 登录后的资源包载入/更新/冲突/孤立资源包提示、可点击移除命令 | 聊天 | 后台通知，公共实现 |
| `NeoForge/.../neoforge/PondererNeoForgeClient.java:179,212,223,234` | 同上 | 聊天，重复实现拼装及样式 | 同上 |

平台完整路径分别为
`Fabric/src/main/java/com/nododiiiii/ponderer/fabric/PondererFabricClient.java` 和
`NeoForge/src/main/java/com/nododiiiii/ponderer/neoforge/PondererNeoForgeClient.java`。

需要一并衔接的现有路径：

- `ui/catnip/AbstractDeclarativeScreen.java:68,73,80,101` 已有公共表单消息存储和渲染，继续作为界面适配器。
- `ui/ProjectorConfigScreen.java:1681,1690,1711,1719` 自己保存并裁剪底部状态，应适配同一反馈模型。
- `network/DownloadStructureResultPayload.java:40` 已把结果交给两个结构编辑器，不能在迁移时漏掉选中目标更新等行为。
- `ui/ShowInterfaceScreen.java:228` 将捕获结果一律送入 `setInfoMessage`；迁移时应显式携带等级，取消、失败、成功分别处理。
- `ponder/PonderJsConversionService.java:458` 已转发到命令 `notifyClient`，随命令入口统一即可。
- `interfaces/sandbox/SandboxPlayer.java:83,88` 把模拟玩家消息交给沙箱 `session.notice`；这是沙箱隔离路径，不能改成真实玩家全局消息。

## 3. 重构前的不一致与约束

1. **同类引导分散**：NBT、选区是 ActionBar；界面捕获开始是聊天。应统一成可更新、可结束的操作引导。
2. **相同位置有多个写入者**：NBT、选区引导、尺寸、蓝图反馈、区域绿色小字均写 ActionBar。
   选区引导每 tick 重发，区域小字每 20 tick 重发；代码没有统一优先级。实际覆盖先后尚未在游戏中观察。
3. **重复发送**：两个活动平台的 NBT tick 提示与其均注册的公共 `MinecraftNbtPickMixin` 重复。
   蓝图保存失败既在 `BlueprintHandler` 发 ActionBar，又由 `BlueprintPromptScreen.java:71,78` 写表单错误。
   AI 成功同时写表单和聊天。上传冲突在无远程界面时有服务端与客户端两条聊天路径。
4. **同步 sink 接不住异步结果**：`runWithMessageSink` 在 `finally` 恢复，只能接住同步执行期间的输出。
   网络结果另走包处理逻辑；`unregisterPack` 又直接绕过 sink。
5. **结果跟当前界面类型绑定**：部分响应通过 `instanceof` 找当前界面，没有请求身份。
   关闭后重开同种界面，或两个同类请求先后发出时，现有路由不足以证明归属。
6. **消息模型不一致**：有 `Component.translatable`，有 `UIText.of` 后包成 literal，远程操作还直接传英文字符串。
   `ProjectorConfigScreen` 根据英文前缀重新翻译，命令 `isErrorResult` 根据翻译键中的 `failed` 等片段猜错误等级。
7. **触发提示是内容配置**：`TriggerEditorScreen.java:192,210` 分别配置大字、小字及频率；
   `TriggerManager` 还兼容旧的 `hintStyle`。不能因为统一技术入口而把这些配置改成固定渠道。

## 4. 类型到展示路径的固定规则

| 类型 | 例子 | 公共入口 | 展示规则 |
| --- | --- | --- | --- |
| 持续操作引导 | NBT 拾取、坐标选区、界面捕获、蓝图尺寸 | `WorldFeedback.hint/clear` | ActionBar，按 owner 更新；结束后撤销 |
| 即时交互反馈 | 无目标、清除选区、功能禁用 | `WorldFeedback.interaction` | 世界中短时 ActionBar；在本模组界面内则送 UI 提示位 |
| 任务结果 | 保存、导入、生成、上传、下载、配置/权限修改 | `ClientFeedback.result` / `FeedbackTarget.acceptFeedback` | UI 来源保留原界面归属，允许明确转交后继界面；关闭后不转聊天 |
| 命令输出 | 手动命令的数量、路径、多行报告 | `PondererClientCommands.notifyClient/runReportingTo` | 聊天命令保留聊天结果；表单调用回表单，异步沿用原请求归属 |
| 后台通知 | 自动同步、资源包更新、孤立包 | `ClientFeedback.chat` / `PackFeedback.show` | 聊天摘要；自动同步无变化不提示，保留孤立包建议命令 |
| 表单校验 | 必填项、非法 NBT、覆盖确认 | 现有表单 `setErrorMessage/setInfoMessage` | 留在所属界面，不额外发世界消息 |
| 场景触发 | 区域大字、小字、自动打开 | `WorldFeedback.title/hint` | 保留作者配置和频率；小字进入公共仲裁 |

“任务结果回界面或聊天”是一条统一的上下文路由规则：由发起上下文决定，不由各业务文件临时判断当前界面。
同一个任务终态只选一个展示出口，不能既让服务端发聊天，又让客户端自行补发。
后台同步与玩家手动拉取是不同用途，不能仅因为都调用同步代码就视为同一提示类型。

具体迁移例子：

- NBT 拾取、坐标选区、界面捕获开始均注册 `INTERACTION_HINT`。NBT 等待服务器响应时更新为等待状态。
- 蓝图选点/尺寸成为同一份引导状态；不再用尺寸消息与操作说明竞争同一个 ActionBar。
- 蓝图保存成功保留完成界面，在原提示位显示蓝图 ID；保存按钮禁用，完成后可返回世界。
  保存失败则保留界面并显示表单错误，不再额外发 ActionBar。
- AI 完成/失败只更新任务界面与任务缓存，不再发送成功聊天；进度仍留在任务界面。
- V 菜单发起上传后，最终结果回原页面；从聊天执行同一动作，结果回聊天。
- 孤立包保留现有 `SUGGEST_COMMAND` 交互语义，不在重构时改成直接执行删除命令。

## 5. 公共实现边界

反馈层位于 `Common/.../feedback/`，平台模块仅挂接 tick 和注册网络。

```text
世界引导/触发 -> WorldFeedback -> ActionBar / Title
同步命令/本地任务 -> ClientFeedback -> 原 UI 提示位 / 命令聊天
UI 网络操作 -> FeedbackRequestPayload(UUID, 业务请求)
            -> ServerFeedback -> FeedbackResponsePayload(UUID, 业务响应/完成标记)
            -> ClientFeedback -> 原 UI / 明确指定的后继 UI
后台资源包/自动同步 -> PackFeedback / ClientFeedback.chat -> 聊天摘要
```

消息携带明确的 `INFO/SUCCESS/WARNING/ERROR` 等级和 `Component` 正文。
网络信封只允许列出的业务包，不允许递归嵌套或反向调用；服务端在原业务校验内执行，
仍保留权限与数据处理。已有数据包处理先执行，完成标记到达后集中显示结果。
请求保存发起时的 Screen 对象，防止关闭后重开同类界面收到旧任务；断线清空，
超时 120 秒回收。聊天来源不会因为响应时打开了 UI 而改变渠道。

Catnip 表单基类和投影仪配置页实现 `FeedbackTarget`，复用各自既有提示位置。
结构下载完成返回父编辑器时，通过 `continueOn` 明确移交提示；导入包也把成功结果交给后继页。
远程操作用可翻译 Component，移除了按英文前缀、翻译键片段猜测结果等级的逻辑。
NeoForge 网络协议由 4 升至 5；客户端和服务端需一起更新，Fabric 也需使用匹配版本。

规则集中在反馈层：

1. **输出 API 集中**：业务层不直接调用 `displayClientMessage`、`sendSystemMessage`、`setTitle`；
   只有对应适配器拥有这些调用。消息组装保持可翻译 `Component`，保留点击、悬停和格式信息。
2. **状态提示不排队**：每个 owner 只保留最新状态；阶段变化时更新，尺寸变化时更新同一状态。
   tick 只由一个公共入口刷新当前胜出的提示，避免多个生产者每 tick 直接写 HUD。
3. **ActionBar 仲裁**：短时交互反馈 > 主动拾取/捕获引导 > 被动区域小字。
   短时反馈建议约 2 秒，然后恢复仍有效的引导。模式结束、换世界、断线清理状态。
   只仲裁本模组生产的提示；空闲时停止写入，不无条件清空原版或其他模组正在使用的小字。
4. **结果按归属投递**：用请求 ID 与接收令牌匹配原界面/指定后继界面，不把结果投给任意同类界面。
   表单关闭时仍只更新原接收者，绝不回退聊天；旧连接的结果丢弃；进度不回聊天刷屏。
5. **仅展示一次**：结果随请求聚合，在完成标记到达后移除请求；已完成请求的重复回包不再展示。
   不同请求的相同文案分别处理；业务数据写回与状态提示分开，撤掉服务端并行聊天。
6. **后台批次汇总**：一次同步的成功数、失败数、冲突数生成一条摘要；详情在远程管理界面或日志。
   保留当前“自动同步无变化不提示”的行为；孤立包提示继续遵守 `PACK_ORPHAN_PROMPT`。
7. **等级统一**：信息使用常规文字，成功绿色、警告黄色、错误红色，并有相应语义文案。
   去掉解析英文和猜翻译键等级的逻辑；校验错误持续到修改/重试，不按 HUD 的短时规则消失。
8. **线程与服务端边界**：客户端适配器在客户端线程运行。服务端只产生结果并经网络桥发送，
   服务端公共模型不能依赖 Minecraft 客户端类；`DisabledFeatureUse` 等路径继续由服务端判定结果。
   沙箱模拟消息仍留在沙箱，不被全局反馈层转发给真实玩家。

网络桥封装原业务包；上传无权限已补齐响应，结构下载保留选中目标更新。
模式随拉取请求传递，移除了容易串请求的客户端全局 pending mode。

## 6. 落地顺序与验收

1. **先收口出口**：建立消息模型和适配器，迁移客户端/服务端消息出口与两个平台的资源包通知拼装。
   这一阶段先保留原渠道，确保翻译、点击事件和原有数据更新完整。
2. **统一操作引导**：把 NBT、坐标、蓝图与界面捕获接到状态入口，增加 ActionBar 仲裁；
   公共 mixin 只管输入拦截，去掉平台和 mixin 的重复提示发送。
3. **统一任务归属**：迁移异步结果，补齐请求身份、等级和翻译信息；处理界面切换、权限拒绝，撤掉双重发送。
   命令临时 sink 过渡到显式上下文，移除命令也走相同入口。
4. **统一后台摘要和样式**：汇总自动同步及资源包通知，接入现有表单状态栏、投影仪状态栏。
   保持场景触发配置、旧版配置兼容、沙箱隔离和删除命令交互语义。

实现后的验证应包含：

- 默认运行受影响模块的 scoped build；三模块均受影响时用
  `.\gradlew.bat :Common:build :NeoForge:build :Fabric:build`。
- 修改同步行为时运行已有 `SyncResponsePayloadTest`，不新增测试文件或用例。
- 修改公共 mixin 时，检查两个活动平台的编译输出/生成 refmap，继续遵守映射约定，并运行
  `.\gradlew.bat quickPlaySmoke -PsmokeTimeout=180`；现有 `test.py` 提供 smoke 超时及进程清理。
- 游戏内核对：引导不会被下一 tick 覆盖、尺寸与阶段一致、取消后提示撤销、进入触发区域时优先显示主动引导。
- 核对保存失败只出现一次；上传/下载所有终态只有一个展示出口；关闭再重开同类页面不会收到旧任务结果。
- 核对自动同步无变化保持安静、冲突可找到处理入口、孤立包点击语义保持不变，两个平台行为一致。

## 7. 本次验证与未验证项

- `:Common:build :NeoForge:build :Fabric:build --offline --console=plain` 通过；
  Common 已有 JUnit 共 61 项，0 失败、0 错误、1 项跳过，未新增测试文件或用例。
- 两份语言 JSON 均可解析，未新增重复键；diff 空白检查通过（按 Windows CRLF 检查）。
- Fabric 生成 refmap 已核对：`overlayMessageString -> field_2018`、
  `overlayMessageTime -> field_2041`，`handleKeybinds -> method_1508`。
  NeoForge 当前 ModDev 使用 Mojmap，无单独 refmap；编译及运行加载通过。
- `quickPlaySmoke -PskipBuild -PsmokeTimeout=180 --offline --console=plain` 通过：
  Fabric、NeoForge 均成功进入世界，并按脚本自动清理进程。
- 静态扫描确认真实玩家聊天输出只保留在 `ClientFeedback.chat`；ActionBar/Title
  只由 `WorldFeedback` 写入。`SandboxPlayer` 的模拟消息覆盖保留在沙箱内。

无实现阻塞。入世界 smoke 不等于完整交互验收；未使用 GUI 自动化，尚未逐项人工核对：
Catnip 提示位的多行文字布局、上传/下载权限拒绝与重试、关闭重开页面的迟到响应、
选点/滚轮/取消与区域触发并发时的视觉时长。下一步可按第 6 节游戏内验收项检查。
验证日志保存在 `build/feedback-build.log` 和 `build/feedback-smoke.log`。
