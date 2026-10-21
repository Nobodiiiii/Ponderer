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

## 验证命令

使用现有 `:Common:test`、`:NeoForge:build`、`:Fabric:build`。
本轮不新增测试文件或用例。真实模型效果和游戏内视觉效果需要另行验证。
