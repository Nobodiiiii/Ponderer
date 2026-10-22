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

`config/ponderer/prompts/user_instructions.txt` 是每次生成重新读取的追加偏好。
首次迁移保留原 `agent_system.txt`，另存 `agent_system.v1.bak`；已知默认内容无需重复追加，
自定义内容保留为追加偏好并移除旧占位符。已有 user_instructions.txt 不会被覆盖。
内置工具协议和宿主编辑边界始终生效。prompt-version.txt 记录当前版本。

read_reference 可读取搜索命中附近的连续 chunk。无命中的搜索不算有效读取；编辑某片段
须先 include_steps=true 读取其内容。局部编辑无需为纯文字修改强制查询结构。
未知 step 类型被拒绝，目录包含当前运行时支持的接口指令。
步骤目录同时生成 inputSchema 和必填字段、类型、枚举、向量长度及非负时长校验；
原始 JSON 在类型转换后仍会再检查，避免 Gson 的类型转换掩盖错误。坐标、注册表和接口
动作等语义校验由宿主补充，接口指令复用 InterfaceStepSpecs 的解析规则。

## 预览与反馈

生成界面在模型提交成功后进入 awaiting_review，草稿已持久保存，场景文件尚未修改。
审阅页复用场景编辑器的真实播放组件，可切换片段、定位或播放步骤、查看完整差异，
填写反馈后继续生成，或者明确确认保存。关闭页面保留草稿，运行记录可重新打开审阅。
预览编译或播放抛出异常时禁用当前审阅页的保存操作。

反馈包含当前片段、步骤和用户说明，并保存为 review_feedback.json。继续修改默认以
当前审阅草稿为局部编辑基线，原文件的保存基线仍沿用首次运行的内容；用户确认保存时
再次检查，防止预览期间的外部修改被覆盖。这里由用户判断画面质量，静态校验成功和
预览组件成功加载不会被记录为自动视觉验收通过。

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
reasoning/thinking/signature，再交给 Agent 执行。界面显示节流后的接收字符数，
不展示思考正文。服务返回普通 JSON 时仍可读取；不支持流式请求的端点可在配置页关闭。
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
不代表并发读取、上下文溢出重试、事件日志恢复、流式协议、预览反馈、补充指令和撤销
已完成真实服务或游戏内验收。

真实模型连通性使用现有命令：
`gradlew.bat --offline :Common:test --tests com.nododiiiii.ponderer.ai.RealLlmCodexConfigTest -PrealLlm=true`。
本机检查在发送请求前失败：Codex 配置没有可用 API key。配置可用凭据后应先重跑该命令，
再在游戏中分别验证三种原生协议、多轮工具调用、恢复、差异和撤销。
