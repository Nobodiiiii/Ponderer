# Changelog

## 1.5.0

### 改进 / Improvements

- **场景包升级为资源包格式**：导入导出功能现使用标准资源包（Resource Pack）格式，支持语义版本控制与自动加载——游戏启动时自动检测 `resourcepacks/` 中的 Ponderer 包，仅在首次或版本升级时解包。脚本自动添加包名前缀以避免冲突。可直接上传至 Modrinth / CurseForge 分享。
  Scene pack export/import now uses the standard Resource Pack format with semantic versioning and auto-loading — packs in `resourcepacks/` are detected on game start and only unpacked on first load or version upgrade. Scripts are automatically prefixed with the pack name to avoid conflicts. Packs can be uploaded to Modrinth / CurseForge for sharing.

- **移除历史遗留 `steps` 字段**：场景 JSON 不再包含已弃用的 `steps` 字段。
  Removed deprecated legacy `steps` field from scene JSON.

---

## 1.4.5

### 改进 / Improvements

- 版本更新至 1.4.5。
  Version bump to 1.4.5.

---

## 1.4.4

### 改进 / Improvements

- **优化 AI 生成**：改进了 AI 场景生成的质量与稳定性。
  Improved AI scene generation quality and stability.

- **扩展显示结构**：`show_structure` 步骤新增高度（height）、缩放（scale）字段，支持通过文件浏览器选择结构文件。
  Extended `show_structure` step with height, scale fields and file browser for structure selection.

---

## 1.4.3

### 新增功能 / New Features

- **显示操作提示步骤**：新增 `show_controls` 步骤类型，可在场景中展示玩家操作提示，支持方向、鼠标动作、手持物品、潜行/Ctrl 状态等配置。
  New `show_controls` step type to display player control hints in the scene, with direction, mouse action, held item, sneaking/CTRL state options.

### 改进 / Improvements

- **更新 AI**：优化了 AI 场景生成流程。
  Updated AI scene generation pipeline.

---

## 1.4.2

### 新增功能 / New Features

- **清除实体/掉落物步骤**：新增 `clear_entities` 和 `clear_item_entities` 步骤类型，支持按实体/物品类型和坐标范围清除，也可清除整个场景。
  New `clear_entities` and `clear_item_entities` step types. Supports filtering by entity/item type and position range, or clearing the full scene.

---

## 1.4.1

### 改进 / Improvements

- **优化 AI 生成**：改进了 AI 场景生成的提示词与输出质量。
  Improved AI scene generation prompts and output quality.

---

## 1.4.0

### 新增功能 / New Features

- **AI 场景生成（Beta）**：通过 LLM（Claude / ChatGPT 等）根据结构和自然语言描述自动生成完整的思索场景 JSON。支持 Anthropic 和 OpenAI 兼容 API（OpenAI、DeepSeek、Groq、Ollama、LM Studio 等），可配置代理、模型参数和最大 token 数。
  AI scene generation (Beta): automatically generate complete Ponder scene JSON from structures and natural language descriptions using LLMs (Claude / ChatGPT, etc.). Supports Anthropic and OpenAI-compatible APIs (OpenAI, DeepSeek, Groq, Ollama, LM Studio), with configurable proxy, model parameters, and max tokens.

---

## 1.3.0.1

### 改进 / Improvements

- **界面优化**：改进了编辑器界面的布局与交互体验。
  UI improvements for better editor layout and interaction.

---

## 1.3.0

### 新增功能 / New Features

- **模组功能配置面板**：新增图形化配置界面，可在游戏内直接管理所有模组设置（LLM 提供商、API 密钥、模型、代理、蓝图载体物品等），无需手动输入指令。
  Mod config panel: a new GUI for managing all mod settings in-game (LLM provider, API key, model, proxy, blueprint carrier item, etc.) — no commands needed.

---

## 1.2.5.2

### 修复 / Fixes

- 修复了 NBT 不兼容中文字符的问题。
  Fixed NBT not supporting Chinese characters.

---

## 1.2.5.1

### 修复 / Fixes

- 修复了可能的不兼容机械动力（Create）的问题。
  Fixed possible incompatibility with the Create mod.

---

## 1.2.5

### 修复 / Fixes

- 修复了在"显示结构"前"隐藏区段"会导致崩溃的问题。
  Fixed crash when "hide section" is used before "show structure".

- 修复了和机械动力一起加载会导致原版思索丢失本地化文本的问题。
  Fixed Create mod causing vanilla Ponder scenes to lose localized text.

---

## 1.2.4

### 改进 / Improvements

- 版本更新与内部优化。
  Version bump and internal improvements.

---

## 1.2.3

### 改进 / Improvements

- 优化 jarJar 配置以支持 Ponder 和 Flywheel 的打包。
  Optimized jarJar configuration to support Ponder and Flywheel bundling.

---

## 1.2.2

### 改进 / Improvements

- 优化场景编辑器功能，调整示例，修复 mixin 问题。
  Improved scene editor functionality, adjusted examples, fixed mixin issues.

---

## 1.2.1

### 新增功能 / New Features

- **移植到 Forge**：将模组从 NeoForge 1.21.1 移植到 Forge 1.20.1，并进行了一些优化。
  Ported the mod from NeoForge 1.21.1 to Forge 1.20.1 with minor optimizations.

---

## 1.2.0

### 新增功能 / New Features

- **JEI 集成**：所有 ID 输入框旁新增 [J] 按钮（需安装 JEI），点击后可从 JEI 物品列表中点击或拖放物品自动填入 ID。支持方块、物品、实体三种模式，自动过滤不兼容类型。
  JEI integration for all ID input fields. Click [J] to browse JEI, then click or drag-drop items to fill in IDs. Supports block, item, and entity modes with automatic type filtering.

- **方块状态属性**：放置/替换方块时可指定 BlockState 属性（如 facing、half、powered 等）。编辑器中以动态列表形式输入，支持添加/删除属性行。
  Block state properties support when placing/replacing blocks (e.g. facing, half, powered). Editor uses a dynamic key=value list with add/remove buttons.

- **扩展实体解析**：JEI 中除刷怪蛋外，船、矿车、盔甲架等物品类实体也可直接拖入实体 ID 字段。
  Extended entity resolution: boats, minecarts, armor stands, and other item-based entities can be dragged into entity ID fields from JEI (not just spawn eggs).

---

## 1.1.0

### 新增功能 / New Features

- **步骤复制/粘贴**：在场景编辑器中复制步骤并粘贴到任意位置。
  Copy/paste steps in the scene editor and insert at any position.

- **撤销/重做**：Ctrl+Z 撤销、Ctrl+Y 重做步骤操作。
  Undo with Ctrl+Z, redo with Ctrl+Y.

- **场景包导入导出**：将所有脚本和结构导出为 ZIP 文件，方便在社区分享或备份。
  Export all scripts and structures as a ZIP file for easy sharing or backup.
  - `/ponderer export [filename]`
  - `/ponderer import <filename>`

- **场景选点**：所有坐标字段旁新增选点按钮（+），点击后跳转到 Ponder 场景中直接选取方块坐标。左键选取方块坐标，右键选取相邻方块坐标。实体/文本等非方块字段自动应用面感知偏移（+0.5）。
  All coordinate fields now have a pick button (+). Click it to jump into the Ponder scene and pick block coordinates directly. Left-click picks the block position, right-click picks the adjacent block position. Non-block fields (entity/text) automatically apply face-aware +0.5 offset.

- **编辑器表单 tooltip**：所有标签和选点按钮支持鼠标悬停显示 tooltip 说明。
  All labels and pick buttons now show tooltip descriptions on hover.

### 改进 / Improvements

- 步骤编辑器支持在指定位置插入新步骤（而非仅追加到末尾）。
  Step editor supports inserting new steps at a specific position (not just appending).

- 步骤类型选择器按类别分组排列，更易查找。
  Step type selector is grouped by category for easier navigation.

---

## 1.0.0

### 核心功能 / Core Features

- **JSON DSL 场景定义**：在 `config/ponderer/scripts/` 中使用 JSON DSL 定义 Ponder 场景。
  JSON DSL scene definition in `config/ponderer/scripts/`.

- **游戏内场景编辑器**：通过图形界面新增、编辑、删除、排序步骤。
  In-game scene editor with GUI for adding, editing, deleting, and reordering steps.

- **自定义结构加载**：从 `config/ponderer/structures/` 加载自定义结构文件。
  Custom structure loading from `config/ponderer/structures/`.

- **蓝图选区与结构保存**：使用蓝图工具（默认为纸）选择区域并保存结构。
  Blueprint selection and structure saving using the blueprint tool (default: paper).

- **开箱即用**：内置引导思索；手持书与笔可直接查看示例思索。
  Works out of the box with a built-in guide scene; hold a writable_book to view the demo scene.

- **NBT 场景过滤**：通过 `nbtFilter` 实现基于 NBT 的场景匹配。
  NBT-based scene filtering via `nbtFilter`.

- **本地热重载**：编辑后直接重载，快速查看效果。
  Local hot reload for fast iteration without restarts.

- **多人协作同步**：客户端与服务端拉取/推送场景内容（含冲突处理）。
  Client-server pull/push with conflict handling.

- **PonderJS 双向转换**：支持与 PonderJS 格式互相导入/导出。
  Bidirectional PonderJS conversion (import/export).

- **物品列表界面**：展示全部已注册思索物品。
  Item list UI for all registered ponder items.

### 命令 / Commands

- `/ponderer reload`、`pull`、`push`、`download`、`new`、`copy`、`delete`、`list`、`convert`
