# AI 场景 Agent

AI 使用单 Agent 工具循环。共享实现位于 `Common`，由 Fabric 和 NeoForge 共用。

`AiSceneAgentPipeline` 负责轮次与结束条件；`AiConversation` 负责模型上下文和压缩，
`AiToolCalls` 将原生调用与 JSON 协议归一到相同校验，`AiToolExecutor` 负责工具调度，
`AiRunEvents` 记录有序运行事件。原生调用直接读取类型化参数，仅在诊断输出时序列化，
不再先转换为 JSON 文本再解析。

## 编辑边界

调整模式须先读取原场景，然后在响应的顶层提供 `edit_scope.paths`，使用精确 JSON Pointer，
例如 `/scenes/0/steps/1/text`。宿主比较实际差异，拒绝范围外的变化；整个场景、片段或
steps 数组不是合法的局部授权。整体重写须由生成界面的“整体重写场景”开关明确选择，
通过 Request.editMode 和 manifest.edit_mode 传递。自然语言关键词不会切换权限，
“不要整体重写”也不会跳过局部边界。旧调用和旧恢复资料默认局部编辑。

仅添加/插入的任务还必须保留原有步骤和元数据。范围检查提供机械边界，不能从自然语言
证明每一个修改都符合用户意图；最终差异仍应可供用户检查。

质量警告始终不阻断，带有 `origin=pre_existing|new|changed` 和 `blocking=false`。
审阅结果同时返回 `priority=high|normal`、`suggestion` 和 `warning_summary`，汇总警告数量、
高优先级数量及新增/变化数量。提示词与每轮提醒要求优先处理高优先级、新增/变化以及
当前任务相关的原有警告；在范围和预算内修复，再检查结果。刻意保留的重叠、保守估算或
无法在本轮处理的重要警告应简要说明。无关的旧警告不扩大编辑权限，警告不会变成提交前置条件。
局部编辑不会对全场景补关键帧，避免宿主后处理改变未请求的步骤。

## 宿主草稿

`get_scene_draft` 返回 `draft_id`、`revision` 和所选 JSON Pointer 的值；不提供 paths 时读取全文。
`apply_scene_patch` 使用相同 ID 和 revision，支持 `add/replace/remove`。数组 add 是插入，
对象 add 仅允许新字段，replace/remove 必须带与旧值相等的 expected。整个补丁事务成功后
才更新 revision；冲突或越界不会部分应用。每轮只能执行一个草稿修改工具。

首次新建可通过 review_scene 提供完整 scene；后续 review_scene/submit_scene 可直接使用
`{ "draft_id": "...", "revision": N }`，不再重传场景。调整模式先读取原场景，优先使用补丁。
保存前比较原场景文件内容；生成期间发生外部修改时拒绝覆盖，保留结果供检查。

## 提示词与证据

内置 core、authoring/editing、building 片段位于 `assets/ponderer/ai/prompts/`。
工具契约由注册表生成，详细步骤字段通过 get_step_schema 按需提供，不注入大型题材示例。
manifest 包含实际 Minecraft 版本、加载器和已加载模组版本。

v9 提示词、步骤索引和 get_step_schema 不再向模型提供 `shared_text` 步骤，文字注释使用
`text`；已有场景中的 `shared_text` 仍可通过校验并正常播放。

提示词与步骤 schema 明确区分显示/动画时长和时间线等待：`text` 默认显示 60 tick，
期间继续调度后续步骤；`idle`、有效的镜头调整和界面动作才提供已知的
阻塞时间。文字重叠检查累计相邻注释之间所有显式等待与已知阻塞操作，覆盖缺少等待、
跨视觉动作的等待及共享文本，并给出等待缺口；不会把粒子或文字的 duration 加成等待。
依赖运行时可见性的分层入场间隔和可跳过的交互练习不作为保证的阅读时间，因此结果是
保守的静态警告，允许有意的重叠和运行时确认后的取舍。等待发生变化时，即使文字未改，
对应的时序警告也会标为 changed，而非仅按文字身份视为原有警告。

系统提示词仅使用当前模组内置的新版内容，不再读取或追加磁盘上的旧提示词、用户偏好。
每次生成开始时，若 `config/ponderer/prompts/` 中存在 `user_instructions.txt`、
`agent_system.txt`、`json_system.txt` 或 `outline_system.txt`，将其原样移入该目录下新建的
`backups/pre-v9-<唯一后缀>/`，保留原文件名且不覆盖已有备份。没有旧文件时不创建新备份，
也不再生成空白的 user_instructions.txt。备份失败时保留未归档的原文件并记录警告，
仍只使用内置提示词。prompt-version.txt 记录当前版本。

AI 新建场景的审阅与提交会忽略模型返回的 tags/customTags，分类由用户在编辑器中管理。

read_reference 可读取搜索命中附近的连续 chunk。无命中的搜索不算有效读取；编辑某片段
须先 include_steps=true 读取其内容。局部编辑无需为纯文字修改强制查询结构。
未知 step 类型被拒绝，目录包含当前运行时支持的接口指令。
步骤目录同时生成 inputSchema 和必填字段、类型、枚举、向量长度及非负时长校验；
原始 JSON 在类型转换后仍会再检查，避免 Gson 的类型转换掩盖错误。坐标、注册表和接口
动作等语义校验由宿主补充，接口指令复用 InterfaceStepSpecs 的解析规则。

## 自动保存与查看生成结果

模型成功提交且通过场景校验后，直接保存场景并重新加载本地思索，不再打开草稿审阅页。
宿主在接受 AI 提交后，将结果中所有 `text`/`shared_text` 的 `placeNearTarget` 统一设为
`true`，适用于新建和调整场景，即使模型省略该字段或返回 false 也生效。该后处理不依赖
提示词，模型的局部编辑草稿保持原样；最终 JSON、保存文件和撤销差异包含处理后的值。
保存仍检查原文件基线、新建同名冲突，并保留差异和撤销资料。失败、取消或预算耗尽时，
已有草稿检查点继续保留，可从运行记录恢复；旧版 awaiting_review 记录也走草稿恢复流程。

生成界面的“查看生成的思索”播放本次客户端会话中最近一次成功生成并保存的场景，
仅展示该场景的片段，从第一个片段开始。后续生成失败或取消不会替换上次成功的目标。
关闭或返回后回到原 AI 界面并恢复原导航状态；若原来是新建模式，自动切为编辑该场景，
已经处于编辑模式时保留其当前选择。正常播放不代表自动视觉验收通过。

## 工具传输

配置 `ai.toolCalling` 支持 auto、native、json。auto 对官方 OpenAI Responses、Chat 和
Anthropic 使用原生工具，对其他兼容端点保守使用 JSON；自建服务可明确选择 native。
三种协议保留 assistant/tool 角色、调用 ID 和结果关系；Responses 的 reasoning 输出、
Anthropic thinking/signature 以不透明内容回放，兼容模式只在适配器边界转换为文本。

所有工具参数先经过同一宿主 schema 校验。错误含 code/retryable，单个工具失败不会
中断其他独立读取。图像放在完整工具结果批次之后，避免打断原生调用结果配对。
不因任意 HTTP 错误自动切换协议；令牌耗尽与上下文容量分别处理。

`query_structure`、`get_step_schema`、`search_references`、`read_reference`、
`inspect_existing_scene`、`get_scene_draft` 的连续读取可最多三路并发；运行状态中的读取
回执同步更新，模型历史仍按原调用顺序加入结果。访问运行时注册表的工具保留串行，
未明确标记安全的工具也是顺序屏障。草稿修改、任务记忆修改和终止工具必须独占一轮。
读取线程由运行取消域管理，取消、到期或退出时关闭线程池；读取中仍通过协作检查退出。

ai.streaming 默认开启；三种协议均支持 SSE，按完整结束事件组装工具调用和不透明
reasoning/thinking/signature，再交给 Agent 执行。生成器与结构预览界面一样组合居中：
左侧 200 GUI 像素侧栏使表单右移 100 像素，窄窗口回退到居中表单并收紧侧栏。
侧栏使用 Catnip 的 BoxElement 原生背景、渐变边框、文字配色及 BoxWidget 按钮，
显示节流后的 AI 正文和简短工具状态。下方表单提示
不再显示生成进度或流式输出。原生工具模式显示 assistant 文本；JSON 兼容模式只显示可选的
顶层 message 字符串（建议放在 tool_calls 之前），不显示协议 JSON、工具参数和思考正文。
模型按需提供面向用户的简短更新。左栏默认跟随最新内容，上滚后暂停跟随，可点击底部回到最新。
输出在离开页面及窗口缩放时保留，新一轮生成清空。服务返回普通 JSON 时仍显示完整正文；
不支持流式请求的端点可在配置页关闭。
请求超时覆盖响应体读取，取消和整次到期会关闭在途响应流；中断流不会作为完整响应使用。

运行过程中可提交最多 8 条、每条最多 2048 字符的补充指令。指令在下一完整轮次送入模型，
保留到压缩摘要和恢复资料中，不改变宿主选定的编辑模式。提交成功与关闭指令队列原子决定，
已接收但尚未处理的补充指令会阻止本轮结束。每轮记录请求耗时和各工具耗时。

协议参考：[OpenAI function calling](https://developers.openai.com/api/docs/guides/function-calling)、
[Responses migration](https://developers.openai.com/api/docs/guides/migrate-to-responses)、
[Anthropic tool results](https://platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls)。

## 运行预算与恢复

| 配置 | 默认值 | 含义 |
| --- | ---: | --- |
| ai.maxRunSeconds | 900 | 包含准备资料、模型、工具和重试等待的整次时间预算 |
| ai.maxRunTokens | 524288 | 累计输入与输出预算；缓存输入仍计入上下文 |
| ai.contextTokens | 131072 | 模型的上下文容量，须按实际模型设置 |
| ai.noProgressTurns | 4 | 连续没有新证据或草稿变化时停机 |

已知用量采用服务返回值；未知用量明确标记为估算，不填零。文本预估区分 ASCII 与
Unicode，使用服务返回的输入用量向上校准安全余量；图像按每张 4096 token 预留。
这是启发式估算，不是模型的精确 tokenizer 或计费凭据，无法保证覆盖所有模型和图像尺寸。
每次请求的输出上限同时受单次配置、剩余总预算、上下文余额限制。单次输出截断、上下文
容量不足和整次预算耗尽分别记录，截断内容不会作为完整场景提交。

临时网络失败、HTTP 408/429/5xx 最多重试两次，尊重 Retry-After，等待也可取消并受时间
预算限制。无法确定是否计费用量的失败请求按输入估算和预留输出上限保守入账。
明确的上下文溢出错误每轮允许一次强制压缩重试；该次压缩移除旧轮次，保留宿主状态和任务
记忆。压缩不能缩小输入或重试仍溢出时，以 context_exhausted 停止并保留草稿。
HTTP 错误只读取有限长度的错误包用于分类，不记录或回显任意服务响应正文。
其他 HTTP 错误不会自动重试或切换协议。到期取消在途 HTTP future；本地结构解析等同步
工作在协作取消检查点退出，不会强行终止游戏线程。

上下文接近容量时优先保留最近三轮，仍超预算则从最早轮次开始移除，调用、结果和不透明
reasoning 始终一起保留或移除。宿主固定保留原始场景、当前草稿、编辑范围、验证错误、
用户补充指令、变更路径和最近读取位置索引；读取索引不代表证据正文，模型须按需重新读取被移除的
具体证据，不把模型生成的摘要当成证据。`finish_task` 支持 partial、needs_input、failed，
这些结果保留草稿但不会保存成成功场景。

复杂任务可以通过 `update_task_state` 替换小型结构化工作记忆：`facts` 中保存备注及其读取
工具和参数，`decisions` 保存方案选择，`next_steps` 保存未完成步骤。事实备注的来源必须
匹配本轮成功执行的读取；最多保留 12 条事实备注、8 条决策和 8 条待办，总长最多 16384
字符。该工具不修改场景、不会单独重置“无进展”计数，也不增加编辑权限。工作记忆在
压缩、checkpoint 和恢复间保留，`get_scene_draft` 也会返回；恢复后旧回执不恢复，必须
重新读取来源才能依赖或更新事实备注。宿主只验证读取来源存在，不证明备注语义为真。

每次运行独立保存在 `config/ponderer/logs/runs/<UUID>/`，包含有效提示词、manifest、
逐轮 trace/usage、metrics、checkpoint、diff、status；模型与协议记录不包含 API key。
checkpoint 在工具调用后和退出时原子更新。原有 last_agent_trace.log 仍保留最新运行记录。

`events.jsonl` 是新增运行的有序事件记录，事件包含 version、run_id、sequence、type、turn、
at 和 data。请求、工具执行、上下文压缩、轮次结束、草稿变化、进度和运行状态走同一
事件入口。先追加完整事件，再通知进度或更新状态、检查点与 metrics 的兼容 JSON 文件。
运行记录和恢复优先读取事件中最新的对应快照，旧记录继续读取原 JSON 文件；事件落盘后
进程中断、兼容文件尚未更新时仍能读取最新快照。末尾未完成的事件行不会参与恢复，下次
追加前移除该残行；已完成行的版本或顺序损坏则报错，不猜测恢复。该日志不提供跨进程
并发写入或远程会话重放，场景恢复仍以最近一次完整 checkpoint 为准。

生成界面的“AI 运行记录与差异”可查看最近 50 次运行、前后值、原有/新增/变化的警告，
复制完整差异、打开已保存场景或运行目录、为中断草稿补充说明并恢复。
恢复会创建新的运行和预算，重新获取证据、验证和检查文件基线，不恢复旧的远程会话。
原场景在运行期间或暂停后被修改时拒绝覆盖；新场景不会覆盖已有同名文件。

保存前先落盘撤销资料，再通过临时文件替换场景；文件系统不支持原子移动时回退普通替换。
保存后可撤销这一轮：调整恢复原文件，新建删除该文件。撤销前再次比较保存后的内容，
有后续编辑时禁用撤销以保护新内容。保存前后的文件比较是乐观并发控制，不提供跨进程锁。

## 验证命令

使用现有 `:Common:test`、`:NeoForge:build`、`:Fabric:build`。
本轮不新增测试文件或用例。现有自动测试覆盖基础修复循环、配置读取和场景校验，
不代表并发读取、上下文溢出重试、事件日志恢复、流式协议、生成结果跳转、补充指令和撤销
已完成真实服务或游戏内验收。

真实模型连通性使用现有命令：
`gradlew.bat --offline :Common:test --tests com.nododiiiii.ponderer.ai.RealLlmCodexConfigTest -PrealLlm=true`。
本机检查在发送请求前失败：Codex 配置没有可用 API key。配置可用凭据后应先重跑该命令，
再在游戏中分别验证三种原生协议、多轮工具调用、恢复、差异和撤销。
