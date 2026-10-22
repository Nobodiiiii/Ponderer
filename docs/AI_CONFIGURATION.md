# AI 配置

自定义配置、Codex 配置、Claude Code 配置共用游戏内的输出预算、请求超时、
Agent 轮次与网络设置；选择外部配置来源后仍可在 AI 配置页调整这些选项。
思考强度留空时继承所选来源，来源未设置时交给 API 决定。

## 默认值与现有配置

| 配置 | 默认值 | 范围 / 行为 |
| --- | --- | --- |
| `ai.model` | OpenAI：`gpt-6-astra`；Anthropic：`claude-sonnet-5-5` | 仅在未指定模型时使用；兼容服务应填写自己的模型 ID |
| `ai.wireApi` | `auto` | 按官方地址识别；可明确选择 `responses`、`chat` 或 `anthropic`（Messages） |
| `ai.streaming` | `true` | 流式接收并显示进度；端点不支持 SSE 时可关闭，适用于所有配置来源 |
| `ai.maxTokens` | 65536 | 1024–262144；单次请求的思考与输出合计上限，应小于等于所用模型的输出上限 |
| `ai.requestTimeoutSeconds` | 300 | 30–1800 秒，每次请求独立计时 |
| `ai.maxAgentTurns` | 12 | 2–64；每轮最多 4 次工具调用，总调用次数由轮次约束 |

首次升级会一次性迁移已有配置，具体规则见下文。增加预算和轮次会提高可能的
费用与等待时间，输出预算不是上下文窗口大小。模型能否调用取决于所选服务和账户权限。

`ai.maxTokens` 每次请求重新计数，不是整次 Agent 生成任务的总额度。
token 是模型处理文本的单位，与字数不一一对应；推理模型的内部思考和最终场景
JSON 共用这份额度。例如上限为 32768，思考已用 20000，留给正文的额度就不足
13000。设置上限不要求模型用满，费用取决于实际用量。

65536 为高思考强度和复杂场景留出更多空间；普通查询通常无需用满。
当前默认模型支持约 128K 输出，但不同模型和兼容服务有各自的限制。
此前保存的 16384、32768 等低于新默认值的上限会一次性提升到 65536。

## 旧配置迁移

Fabric 与 NeoForge 在加载配置前自动迁移各自的 `ponderer-client.toml`，
并在同目录保留原文件备份 `ponderer-client.toml.pre-ai-v1.bak`。
迁移和 `ai.configVersion = 1` 一起保存；之后启动不会重复迁移。

- 移除 `ai.provider`：旧 Anthropic 选择转为 `ai.wireApi = "anthropic"`；
  已经明确选择的协议保留，其他旧配置使用 `auto`。
- 没有协议字段的旧配置会把原先由程序补上的 `/v1` 写入基础地址，
  保持原请求路径；已有协议字段的地址保留其完整前缀。
- `ai.maxTokens` 低于 65536 时一律提升到 65536，等于或高于该值时保留。
  迁移后用户主动调低上限，后续启动会保留该选择。
- 旧默认轮次 6 更新为 12，其他已配置轮次保留。
  缺少的请求超时、思考强度使用新默认值。
- 自定义模型、密钥、代理和配置来源等原值保留。
  模型名称留空时使用新版默认模型；不修改外部 Codex / Claude Code 配置文件。
  Claude Code 和 Codex 的旧 Chat / Messages 来源仍按原规则补齐 `/v1`；
  Codex Responses 来源继续使用配置中的完整前缀。

迁移使用原子替换保存。读取、备份或保存失败时停止配置加载并报告错误，
避免配置校验提前删除旧字段。

## 协议选择

- 配置页只保留“API 协议”，不再单独选择供应商。协议同时决定请求格式、鉴权方式、默认地址和默认模型。
- 自定义 OpenAI 官方配置留空基础地址即可使用 `https://api.openai.com/v1` 和 Responses。
- 选择 Anthropic Messages 后，基础地址和模型留空时使用 Anthropic 默认值。
  自动模式也能识别 `api.anthropic.com`。
- 中转服务、DeepSeek、Ollama、LM Studio 等默认保留 Chat Completions。
  中转服务提供 Responses 或 Anthropic Messages 时，应明确选择对应协议。
  基础地址需包含服务要求的 `/v1` 等前缀。
- 旧 `ai.provider` 会一次性转换成协议并移除，不再保留为运行时配置项。
- Chat Completions 对 OpenAI 官方接口和已识别的 OpenAI 推理模型使用
  `max_completion_tokens`，其他兼容模型沿用 `max_tokens`。
- Codex 来源遵循 `wire_api`，未填写时使用 Responses；Claude Code 使用 Messages。
  游戏内的协议选择只作用于自定义配置。
- Responses 请求使用 `store: false`，每轮由客户端携带已有工具记录。

## 思考输出与失败反馈

Claude 的响应可能先包含思考块，再包含正文；正文用于工具协议，原生多轮请求保留并回放不透明 thinking/signature。
三种协议都会识别输出预算耗尽，避免把被截断的场景当作完整 JSON 交给 Agent 修复。
遇到截断时，应在模型允许范围内增加输出预算，或降低支持该选项的模型的思考强度。
思考强度的可用值由模型决定，界面列出的选项并不代表每个模型都支持。

接口参数依据 [OpenAI Docs：推理模型](https://developers.openai.com/api/docs/guides/reasoning)、
[Chat Completions 参数](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、
[Claude effort](https://platform.claude.com/docs/en/build-with-claude/effort)；默认模型依据
[OpenAI 模型指南](https://developers.openai.com/api/docs/guides/latest-model)和
[Claude Sonnet 5.5](https://platform.claude.com/docs/en/models/sonnet-5-5/overview)。


## Agent 运行与工具调用

`ai.toolCalling` 支持 auto、native、json。auto 对官方 OpenAI Chat、Responses 和 Anthropic
使用原生工具，对其他 Chat 兼容地址使用 JSON；也可显式选择模式。
新增 `ai.maxRunSeconds=900`、`ai.maxRunTokens=524288`、`ai.contextTokens=131072`、
`ai.noProgressTurns=4`，均可在 AI 配置界面修改。contextTokens 须与所用模型匹配。

新版提示词与旧文件备份、宿主草稿、事务补丁、证据读取、持久运行记录、恢复和撤销的详细行为见
[AI 场景 Agent](AI_HARNESS.md)。
