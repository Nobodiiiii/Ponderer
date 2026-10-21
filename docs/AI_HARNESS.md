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

## 验证

使用现有 `:Common:test`、`:NeoForge:build`、`:Fabric:build`。
本轮不新增测试文件或用例。真实模型效果和游戏内视觉效果需要另行验证。
