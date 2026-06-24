# Changelog

## 1.10.0

### 新增功能 / New Features

- **投影仪完整化**：新增微型投影仪与等身投影仪的物品、配方、模型、方块状态与配置界面，支持投影模式、触发模式、播放定位、文本缩放、蓝色光束与可视距离设置。
  Added complete miniature and life-size projector support with items, recipes, models, blockstates, config UI, projection modes, trigger modes, seek controls, text scaling, blue-tint beams, and render-distance controls.

- **远程工作区管理**：新增远程目录浏览、上传/拉取、删除、回滚、历史查看与命令侧交互，并补充对应网络载荷与服务端响应流程。
  Added remote workspace browsing, upload/pull, delete, rollback, history viewing, command-side actions, and the supporting network payload/response flow.

- **功能可用性同步**：新增服务端功能开关同步与功能配置页面，用于在多人环境中同步投影仪、蓝图等功能的可用状态。
  Added server-synchronized feature availability and feature configuration screens for multiplayer control over projector, blueprint, and related functionality.

### 改进 / Improvements

- **投影渲染与 overlay 优化**：重构投影仪渲染管线，改进 show_controls、文本窗口、GUI 图层、3D 物品图标、深度抗遮挡、场景旋转与缓存距离判断。
  Reworked projector rendering for show_controls, text windows, GUI layers, 3D item icons, depth anti-occlusion, scene rotation, and cached render-distance checks.

- **跨加载器网络与注册整理**：整理 Common / Forge / Fabric 的注册与网络辅助接口，补齐 Fabric Mod Menu 集成，并改善配置同步后的投影仪状态一致性。
  Refined Common / Forge / Fabric registration and networking helpers, added Fabric Mod Menu integration, and improved projector state consistency after config sync.

### 修复 / Fixes

- **投影仪同步与界面回归修复**：修复配置合并后的投影仪同步问题，并改进多个投影仪配置页、资源包选择页和动态列表控件的交互细节。
  Fixed projector config sync after merges and polished projector config screens, pack selection, and dynamic list controls.

---

## 1.9.0

### 新增功能 / New Features

- **结构列表、预览与子结构步骤**：新增结构选择器与实时预览组件，`show_structure` / `show_extra_structure` 可直接浏览本地结构文件，并将额外结构作为子结构插入场景。
  Added a structure picker with live previews, and expanded `show_structure` workflows with `show_extra_structure` so local structure files can be browsed and inserted as sub-structures in scenes.

- **NBT 扩展编辑器**：新增可展开的大型 NBT 文本编辑器，支持路径 / 坐标识别、多行输入增强，以及方块实体、实体、掉落物等步骤的 NBT 编辑与选取流程。
  Added an expanded NBT editor with path/coordinate detection, improved multi-line editing, and better NBT edit/pick flows for block entities, entities, and item entities.

- **文本轨道与进度条**：新增场景文本列表与文本标记指令，支持在 Ponder 播放过程中展示进度条、同步文本步骤，并改进共享文本的悬浮定位。
  Added a scene text list and marker instructions for progress bars and synchronized text playback, while also improving floating shared-text positioning.

- **高级动画与界面回放**：扩展 `show_interface` 的客户端截图 / 回放能力，并为区段、实体、掉落物等步骤补充外部动画与更细致的移动动画配置。
  Expanded `show_interface` with client-side screen capture/replay, and added richer movement/external animation controls for sections, entities, and item entities.

### 改进 / Improvements

- **导出与脚本生成优化**：重构 Java 导出模板与运行时支持代码，改进结构引用、导出结果反馈与清单生成，为复杂场景导出提供更稳定的产物。
  Reworked Java export templates and runtime support generation, improving structure references, export feedback, and manifest output for more reliable complex-scene exports.

- **编辑器交互与缩放适配**：新增 UI 缩放支持，优化长文本输入框、结构预览、旋转相机和多处步骤表单细节，提升大场景编辑体验。
  Added dedicated UI scaling support and refined long text inputs, structure previews, rotate-camera controls, and multiple step forms for better large-scene editing.

### 修复 / Fixes

- **场景运行兼容性修复**：修复合并区段后 `hide_section` 清理错误、Ponder 原生界面层级 / 文本索引问题，以及若干 NBT 选取与边界框相关回归。
  Fixed merged-section `hide_section` cleanup, vanilla Ponder UI layering/text-index issues, and several regressions around NBT picking and scene bounds.

---

## 1.8.3

### 新增功能 / New Features

- **Java 模组导出**：新增 Java 模组导出页面，可扫描目标工程并将选定场景导出为 `GeneratedPonderSupport`、注册代码与语言条目，方便将游戏内编辑的内容直接接入源码模组。
  Added a Java module export screen that scans a target project and exports selected scenes as `GeneratedPonderSupport`, registration code, and language entries so in-game authored content can move straight into source-controlled mods.

### 改进 / Improvements

- **导出清单与覆盖策略**：导出流程会维护 manifest 并同步受管场景资源，减少重复生成与手动整理成本。
  Export now maintains a manifest and updates managed scene resources incrementally, reducing duplicate generation and manual cleanup.

---

## 1.8.2

### 新增功能 / New Features

- **开发者模式与可编辑场景**：新增默认可编辑与开发者模式配置；场景可显式标记 `editable`，只读场景在尝试编辑时会提示并可直达模组配置。
  Added `default editable` and `developer mode` config options. Scenes can now declare `editable`, and readonly scenes show a guided prompt that links directly to mod config before editing.

- **权限管理界面**：新增服务端权限管理页面，支持按管理员 / 上传 / 拉取三类权限查看和调整白名单，并处理 OP 权限同步与只读视图。
  Added a server-side permission management screen for admin/upload/pull roles, with operator synchronization and readonly viewer states.

- **服务端蓝图物品配置**：新增蓝图物品配置页面，支持同步内置蓝图物品开关与载体物品设置。
  Added a blueprint item config screen with synchronized server-side builtin blueprint toggles and carrier item settings.

### 改进 / Improvements

- **通用对话框与导航重构**：引入统一的对话框、返回状态与折叠分组列表组件，改进功能页面与权限页面的交互体验。
  Introduced reusable dialog, navigation-state, and collapsible list components to improve Function Page and permission workflows.

---

## 1.8.1

### 改进 / Improvements

- **只读资源包导入提示**：编辑来自资源包的场景时，新增只读提示与一键导入为本地可编辑副本的流程，导入导出体验更清晰。
  Added a readonly import prompt when editing resource-pack scenes, allowing one-click import to local editable copies with a clearer import/export flow.

---

## 1.8.0

### 新增功能 / New Features

- **界面型思索片段**：新增 `show_interface`、`change_interface_slot`、`click_interface` 步骤，可在思索中镜像 GUI、修改槽位并模拟点击。
  Added `show_interface`, `change_interface_slot`, and `click_interface` steps for mirrored GUIs, slot changes, and simulated clicks inside Ponder scenes.

- **片段类型选择**：新建片段时可直接选择结构片段或界面片段，界面型思索工作流更完整。
  Added scene-segment type selection so new segments can start as either structure scenes or interface scenes.

### 改进 / Improvements

- **编辑器与预览重构**：重写大量 UI 基础设施，支持搜索、独立功能页面、按键设置，以及 Forge 侧的内嵌界面预览与 JEI 叠层处理。
  Reworked major UI foundations with search, a dedicated function page, keybinding settings, and Forge-side embedded interface preview with JEI overlay handling.

---

## 1.7.1

### 改进 / Improvements

- **区段与方块入场动画**：`show_section_and_merge` 与 `set_block` 支持分层 / 分方向入场动画，并可配置时长、间隔与智能显示。
  Added layered/directional entrance animations to `show_section_and_merge` and `set_block`, with configurable duration, interval, and smart visibility handling.

- **1.7.1 系列补充优化**：修复区域选点与蓝图选区问题，改进单端安装兼容性，并为 `show_structure` 新增旋转偏移。
  Follow-up 1.7.1 improvements fixed area picking and blueprint selection, improved single-sided install compatibility, and added rotation offset support to `show_structure`.

---

## 1.7.0

### 新增功能 / New Features

- **世界触发思索**：新增触发管理器与触发方式编辑器，支持按结构或坐标范围在世界中触发思索，并提供自动触发、大字提示、小字提示等模式。
  Added world-space ponder triggers with a dedicated trigger manager/editor, supporting structure-based or coordinate-based activation plus auto/title/subtitle hint modes.

- **触发选点与结构选择工具**：新增触发区域选点流程、结构列表界面与专用快捷键，完善“随地大小寻思”的工作流。
  Added coordinate picking, a structure list UI, and dedicated keybindings to support the new in-world trigger workflow.

---

## 1.6.5

### 新增功能 / New Features

- **高亮区域步骤**：新增 `highlight_section` 步骤，用于强调指定方块或区域。
  Added a `highlight_section` step for emphasizing specific blocks or regions.

### 改进 / Improvements

- **优化新建步骤界面**：改进步骤类型选择与新建流程，提升编辑效率。
  Improved the add-step selector and creation flow for faster editing.

---

## 1.6.4

### 新增功能 / New Features

- **调整视角步骤**：新增 `zoom_scene` 步骤，支持平滑移动视角中心并调整缩放倍率。
  Added a `zoom_scene` step for smoothly moving the camera center and adjusting zoom level.

### 改进 / Improvements

- **区段操作增强**：改进区段移动 / 旋转相关步骤，并扩展 `show_structure` 的显示参数。
  Enhanced section move/rotate workflows and expanded `show_structure` display controls.

---

## 1.6.3

### 改进 / Improvements

- **NBT 选取与编辑增强**：补齐更完整的 NBT 选取流程，改进方块与实体 NBT 编辑步骤，并新增实体 NBT 修改界面。
  Expanded the NBT picking workflow, improved block/entity NBT editing steps, and added a dedicated entity NBT modification screen.

---

## 1.6.0

### 新增 / Added

- **Forge + Fabric 双平台（MC 1.20.1）**：完成从单 Forge 架构到 `Common` / `Forge` / `Fabric` 三模块的迁移。
  Full dual-loader support on Minecraft 1.20.1 via `Common` / `Forge` / `Fabric` modules.

- **平台服务抽象（SPI）**：新增 `PlatformHelper`、`NetworkHelper`、`RegistrationHelper`，通过 `ServiceLoader` 按平台加载实现。
  Added SPI-based platform services (`PlatformHelper`, `NetworkHelper`, `RegistrationHelper`) loaded through `ServiceLoader`.

---

## 1.5.1

### 改进 / Improvements

- **资源包导入导出优化**：进一步完善资源包场景的导入导出、运行时注册与物品列表展示，提升资源包工作流可用性。
  Further improved resource-pack scene import/export, runtime registration, and item-list presentation for a smoother pack workflow.

---

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
