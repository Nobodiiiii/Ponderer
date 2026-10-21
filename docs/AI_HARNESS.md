# AI 场景 Agent

AI 使用单 Agent 工具循环。共享实现位于 `Common`，由 Fabric 和 NeoForge 共用。

## 编辑边界

调整模式须先读取原场景，然后在响应的顶层提供 `edit_scope.paths`，使用精确 JSON Pointer，
例如 `/scenes/0/steps/1/text`。宿主比较实际差异，拒绝范围外的变化；整个场景、片段或
steps 数组不是合法的局部授权。明确要求整体重写的任务可以走整体重写模式。

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

## 工具传输

配置 `ai.toolCalling` 支持 auto、native、json。auto 对官方 OpenAI Responses、Chat 和
Anthropic 使用原生工具，对其他兼容端点保守使用 JSON；自建服务可明确选择 native。
三种协议保留 assistant/tool 角色、调用 ID 和结果关系；Responses 的 reasoning 输出、
Anthropic thinking/signature 以不透明内容回放，兼容模式只在适配器边界转换为文本。

所有工具参数先经过同一宿主 schema 校验。错误含 code/retryable，单个工具失败不会
中断其他独立读取。图像放在完整工具结果批次之后，避免打断原生调用结果配对。
不因任意 HTTP 错误自动切换协议；令牌耗尽与上下文容量分别处理。

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

已知用量采用服务返回值；未知用量明确标记为估算，不填零。文本预估使用 UTF-8 字节数
作为保守额度，图像按每张 4096 token 预留；这不是模型的精确 tokenizer，也不是计费凭据。
每次请求的输出上限同时受单次配置、剩余总预算、上下文余额限制。单次输出截断、上下文
容量不足和整次预算耗尽分别记录，截断内容不会作为完整场景提交。

临时网络失败、HTTP 408/429/5xx 最多重试两次，尊重 Retry-After，等待也可取消并受时间
预算限制。无法确定是否计费用量的失败请求按输入估算和预留输出上限保守入账。
其他 HTTP 错误不会自动重试或切换协议。到期取消在途 HTTP future；本地结构解析等同步
工作在协作取消检查点退出，不会强行终止游戏线程。

上下文接近容量时按完整轮次压缩，调用、结果和不透明 reasoning 一起保留或移除。
宿主保留原始场景、当前草稿、编辑范围、验证错误和读取标记；模型须按需重新读取被移除的
具体证据，不依赖模型生成的摘要。`finish_task` 支持 partial、needs_input、failed，
这些结果保留草稿但不会保存成成功场景。

每次运行独立保存在 `config/ponderer/logs/runs/<UUID>/`，包含有效提示词、manifest、
逐轮 trace/usage、metrics、checkpoint、diff、status；模型与协议记录不包含 API key。
checkpoint 在工具调用后和退出时原子更新。原有 last_agent_trace.log 仍保留最新运行记录。

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
不代表新增原生协议、恢复和撤销已完成真实服务或游戏内验收。

真实模型连通性使用现有命令：
`gradlew.bat --offline :Common:test --tests com.nododiiiii.ponderer.ai.RealLlmCodexConfigTest -PrealLlm=true`。
本机检查在发送请求前失败：Codex 配置没有可用 API key。配置可用凭据后应先重跑该命令，
再在游戏中分别验证三种原生协议、多轮工具调用、恢复、差异和撤销。
