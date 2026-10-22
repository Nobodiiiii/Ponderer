# Changelog

## 1.11.0

### 中文

### 新增功能

##### 物品挂载与思索标签

- **物品标签挂载**：支持用 `#物品标签` 批量挂载思索，并可附加 NBT 匹配条件。
- **多物品挂载**：可添加、移除多组物品 ID 或标签，每组独立设置 NBT 条件。
- **物品与标签候选**：悬停 ID 输入框可展开所属标签，支持点击或滚轮切换，标签图标会轮播成员物品。
- **思索分类标签**：描述编辑页新增标签选择器，支持搜索、图标预览和多选，并关联全部分场景与挂载物品。
- **自定义思索标签**：可创建、编辑、重命名和删除标签，设置中英文名称、说明与物品图标，并保护仍被引用的标签。
- **思索者来源标签**：所有思索自动附带“思索者...”标签。

##### 界面捕获与交互

- **从世界捕获界面**：正常打开目标容器即可捕获来源、开屏数据、槽位和可见方块上下文，保存后可在思索中回放。
- **界面试用模式**：新增“试一试”、重置和退出操作，可暂停教程并在独立演示环境中操作容器。
- **界面脚本**：可编排槽位物品与配料、数值动画、玩家状态、点击拖动、文字按键输入及界面关闭，并显示演示光标。
- **界面练习脚本**：新增 `await_interface`，可等待玩家完成槽位目标，并设置提示、超时或跳过。
- **AE2 界面适配**：支持从 AE2 方块或线缆部件创建对应的本地演示菜单。

##### 播放与自由预览

- **进度条自由定位**：支持点击和长按拖动到任意时间，并吸附附近关键帧及首尾位置，无关键帧时也可使用。
- **播放倍速**：新增 `0.5x`、`1x`、`2x` 独立按钮，可直接选择播放速度。
- **场景缩放滑条**：新增 `0.2x–5x` 缩放滑条，中点为 `1x`，悬停显示当前倍率，右键可重置。
- **暂停自由预览**：可用按钮或空格暂停，悬停识别方块，并用左键旋转、右键平移和滚轮缩放，按 `Q` 可恢复播放与原视角。
- **预览文本开关**：暂停自由预览和编辑器预览可按 `T` 隐藏或显示思索文本。

##### 步骤编辑器

- **步骤列表预览**：新增场景预览，滚动列表可定位到顶部步骤，拖动时间轴可同步滚动列表。
- **步骤编辑预览**：步骤编辑页新增场景预览，进入时定位到当前步骤，往返列表页时保留视角与文本显隐状态。
- **编辑器预览视角**：支持左键旋转、右键平移、滚轮缩放及一键重置。
- **步骤快速切换**：编辑页可用上下箭头切换步骤，存在未保存修改时可选择保存、放弃或取消切换。
- **当前步骤试播**：可直接试播表单中的未保存步骤，播放时暂缓后续步骤，试播不会写入场景。
- **预览内选点**：可在编辑预览中旋转视角并用中键取点，自动区分方块、相邻放置位置与面中心，按 `Esc` 取消。
- **多步骤复制粘贴**：右键复制按钮可多选步骤，再按列表顺序一次粘贴到指定步骤后。
- **坐标拖动调节**：坐标与向量输入框支持长按后上下拖动，以 `0.5` 为步长调整。
- **时长拖动调节**：时长输入框支持长按后上下拖动，以 `10 tick` 为步长调整且不低于零。

##### 世界生成结构

- **世界生成结构预览**：结构列表可读取服务器可用结构，预览可用的起始模板，再确认选择或清空。

##### 远程同步与资源包

- **远程待处理更新**：新增冲突列表，可逐项或一键忽略、覆盖，忽略记录跨重连保留并在远端出现新版本时重新提示。
- **自动同步客户端设置**：新增自动、始终开启和始终关闭三种模式，自动模式单人关闭、联机及局域网开启。
- **源资源包多选**：可在同一列表勾选多个源资源包，查看图标与内容数量，确认后统一应用。

##### 粒子效果与 Java 导出

- **粒子预设**：新增通用“粒子效果”步骤，提供成功、红石、烟雾、飞溅、物品碎屑和方块碎屑预设。
- **自定义粒子**：支持填写原版或模组粒子 ID 及 SNBT 参数，保存时校验粒子与参数。
- **粒子分布与发射**：可设置定点、方盒或球体分布，以及位置、范围、每刻数量、持续时间和初始速度。
- **粒子与标签导出**：Java 导出支持通用粒子、思索标签与物品标签挂载，物品标签展开为当前物品列表，NBT 条件暂不保留。

### 改进

##### 界面场景

- **界面槽位编辑**：支持拖动已有物品、右键清空及保存数量和数据组件。
- **界面叠层定位**：文本、槽位图标与 JEI 使用嵌入容器的实际范围定位，并支持槽位物品识别。

##### 文本进度

- **文本进度默认设置**：可配置进入思索时是否显示文本进度，右键“舒适阅读”按钮可切换并保存默认值。
- **文本进度面板样式**：沿用思索原生对话框配色和边框，并通过箭头关联“舒适阅读”按钮。

##### 物品选择与新建思索

- **背包物品选择**：多处物品字段新增背包选择按钮，可回填物品 ID 与 NBT，点击弹窗外即可关闭。
- **新建思索物品捕获**：移除“使用手持物品”选项，改用背包选择，并以“同时捕获 NBT”控制是否填入 NBT。
- **新建思索流程**：先选择结构或界面场景，再编辑描述，取消类型选择时不写入本地文件。
- **新建思索 NBT 编辑**：NBT 字段接入展开编辑器，可在场景创建前编辑完整 SNBT。

##### 步骤列表与字段编辑

- **步骤操作按钮**：插入、复制、粘贴和删除改用图标，复制多选状态会高亮。
- **步骤内容摘要**：步骤行并列显示类型和灰色参数摘要，悬停可查看完整文本、坐标、数量及动作。
- **步骤摘要本地化**：物品、方块、实体、可用音效字幕及共享文本优先显示当前语言内容。
- **步骤内容搜索**：步骤列表搜索同时匹配步骤类型与参数摘要。
- **字段工具图标**：背包选择、方块与 NBT 抓取、选点、展开编辑和结构选择改用独立图标。
- **字段格式提示**：为物品、NBT、角度、缩放、链接和结构字段统一填写示例及悬停说明。
- **坐标类型提示**：区分场景、世界、界面坐标与向量，明确整数、小数及取值范围。
- **小数方块坐标**：方块范围和坐标触发条件接受小数，保存时向下取整为所在方块坐标，包含负坐标。

##### 相机动画

- **视角平移与缩放**：调整视角改为前半程缓动平移、后半程缓动缩放，并在设定时长内到达目标。
- **相机旋转**：改用 Ponder 原生缓出旋转，时长控制开始旋转后的等待时间，零时长仍为瞬间切换。

##### 结构、蓝图与选点提示

- **结构后台读取**：结构预览与信息解析改为后台处理，并显示加载状态。
- **结构预览分帧构建**：方块填充和模型构建分帧进行，减轻大结构加载时的界面卡顿。
- **蓝图操作提示**：按选角、调整和保存阶段显示操作说明，并同步显示选区尺寸。
- **蓝图保存反馈**：保存成功后保留页面与结构 ID，并禁用重复保存。
- **蓝图教程说明**：内置教程先介绍蓝图用途，再演示选区和保存操作。
- **NBT 选取状态**：等待服务器返回方块实体数据时持续显示等待提示，切换世界后清理状态。
- **坐标选取提示**：选取期间持续显示当前操作提示，完成后自动清除。
- **世界内提示优先级**：统一协调工具状态、蓝图提示与思索触发提示，短时结果优先显示，退出工具后清理提示。

##### 远程管理

- **手动远程拉取**：拉取指定场景或结构时直接覆盖本地，场景依赖的结构也会同步覆盖。
- **远程管理按钮**：拉取、上传、历史、忽略和覆盖改用图标，覆盖与删除使用警示配色。

##### JEI 集成

- **JEI 字段填入**：物品、方块和实体等字段改用可预览的配料槽，可直接拖入对应 JEI 配料。
- **实体物品识别**：实体字段除刷怪蛋外，也可从船等实体物品解析对应实体 ID。
- **JEI 显隐设置**：编辑器保留 JEI 原有显隐状态，列表未显示时提示对应快捷键或空间不足。

##### 场景渲染

- **场景渲染缓存**：定期回收闲置区段缓存，丢弃投影场景或切换世界时释放对应资源。

##### 界面与操作反馈

- **未保存修改提示**：统一返回与放弃修改对话框，明确提供保存返回、不保存返回和继续编辑选项。
- **列表布局**：统一各类条目的左右边距、控件对齐和间距，并移除点击条目时的白色选中边框。
- **控件边界**：按钮外框与点击范围保持一致，输入框和物品展示格统一保留 `1px` 内侧留白。
- **界面分组样式**：统一菜单、列表和物品目录的分组标题、分隔线及编辑页背景。
- **确认与执行按钮**：选择器与导出页改用确认和取消图标，区分执行操作与保存修改。
- **设置入口**：V 菜单直接列出通用、AI、蓝图、投影仪及快捷键设置，权限管理移入“同步”分组。
- **操作结果提示**：界面操作结果显示在原页面，并用不同颜色区分普通信息、成功、警告和错误。

##### AI 生成与编辑

- **AI 多轮生成与编辑**：可查询结构、注册表、步骤规则和参考资料后检查草稿，支持局部编辑或整体重写已有思索。
- **AI 运行交互**：新增流式输出侧栏、运行中补充指令与终止按钮，生成后可直接播放结果。
- **AI 记录与恢复**：可查看运行状态、用量和修改差异，恢复中断草稿，或撤销已保存的修改。
- **AI 服务与运行配置**：支持 Responses、Chat Completions、Anthropic 原生工具或 JSON 兼容，可设置推理强度、超时和运行预算。
- **AI 生成保障**：改用内置提示词并归档旧文件，结合实际模组和步骤规则校验草稿，支持上下文压缩、临时失败重试与保存冲突保护。

### 修复

##### JEI 集成

- **新版 JEI 兼容**：修复了与新版 JEI 不兼容的问题。
- **JEI 拖入区域**：修正滚动后字段的有效拖入区域，避免屏幕外或被裁剪的字段接收配料。
- **JEI 配料提示**：使用 JEI 原生配料提示与渲染，保留模组附加信息，并兼容旧版 JEI。

##### 物品 NBT

- **NBT 物品预览与触发**：统一按 1.21.1 数据组件或旧 NBT 条件构造物品，供预览、触发和编辑返回使用。

##### 界面场景

- **界面物品 NBT 回填**：修复显示界面步骤中，物品 NBT 展开编辑后未正确回填的问题。

##### 思索播放与导航

- **文本面板遮挡**：修复文本进度面板遮挡思索文本的问题。
- **返回列表界面缩放**：修复退出思索返回物品目录时，界面缩放被历史页面初始化覆盖的问题。
- **分场景切换返回**：切换思索分场景时不再被提前送回物品列表，退出播放后再返回原页面。

##### 物品选择与场景保存

- **选取物品回填**：创建掉落物时同步填入数量，选择无 NBT 物品时清除旧 NBT，避免沿用上次数据。
- **场景写入保护**：先完整写入临时文件再替换原文件，降低保存中断导致场景损坏的风险。

##### 文本与坐标编辑

- **文本编辑草稿隔离**：取消文本编辑时，不再改动原步骤的多语言文本。
- **坐标触发输入校验**：无效坐标会显示错误，不再被静默替换为零。

##### 方块与区段动画

- **放置方块入场选项**：移除动画模式中实际按向下入场处理的“无动画”选项，默认明确选择向下入场。
- **区段方块重影**：修复放置、隐藏、替换、销毁及合并动画中的重复绘制和残留，Java 导出同步修正。
- **智能入场判断**：按播放时的实际可见区段判断已显示方块，避免重叠结构重复入场或已显示部分消失。

##### 结构预览

- **结构预览渲染**：修复预览污染后续界面光照、第三方方块实体渲染干扰矩阵及上下拖动反向的问题。

##### 自动同步

- **自动同步冲突保护**：保留本地修改和手动删除的场景或结构，避免自动覆盖或恢复，并提示前往远程管理处理。

##### 投影仪

- **投影仪模型朝向**：配置界面模型使用固定朝向，避免随世界中投影仪的放置方向变化。

##### 列表与 Ponder 兼容

- **列表滚动条拖动**：修复步骤列表等页面点击滚动条后丢失焦点、无法持续拖动的问题。
- **Ponder 文本框兼容**：修复 Ponder 1.0.87 移除旧提示字段后，打开编辑界面触发 `NoSuchFieldError` 的问题。

### English

### New Features

##### Item Bindings and Ponder Categories

- Added item-tag bindings with optional NBT filters to attach a Ponder to every matching item.
- Added multiple item ID or tag bindings, each with its own NBT filter and add/remove controls.
- Added hover dropdowns and wheel selection for item IDs and their tags, with cycling tag-member icons.
- Added a searchable category picker with icons and multiple selection, linking tags to all segments and bound items.
- Added custom-category creation, editing, renaming, and deletion with bilingual text and item icons, protecting referenced categories from renaming or deletion.
- All scenes now automatically include The Ponderer category.

##### Interface Capture and Interaction

- Added world capture for container sources, opening data, slots, and visible block context for replay inside Ponder.
- Added Try, Reset, and Exit controls to pause tutorials and interact with containers in a separate demo environment.
- Added interface scripting for slot items and ingredients, data animations, player context, clicks and drags, text/key input, and closing interfaces, with a demo cursor.
- Added `await_interface` for slot-based exercises with hints, timeouts, and a skip control.
- Added local demo-menu opening for AE2 blocks and cable-bus parts.

##### Playback and Free Preview

- Added click-and-drag seeking at any time, with snapping to nearby keyframes and endpoints, including scenes without keyframes.
- Added dedicated `0.5x`, `1x`, and `2x` playback buttons.
- Added a `0.2x–5x` scene zoom slider with `1x` at the midpoint, a hover multiplier, and right-click reset.
- Added button/Space toggling for paused block inspection, left-drag rotation, right-drag panning, and wheel zoom; `Q` resumes playback and restores the view.
- Added `T` to hide or show Ponder text in paused free previews and editor previews.

##### Step Editor

- Added a scene preview that seeks to the top visible step as the list scrolls and scrolls the list when scrubbing the timeline.
- Added scene previews to step editors, seeking to the current step and retaining camera and text visibility when returning to the list.
- Added left-drag rotation, right-drag panning, wheel zoom, and one-click camera reset to editor previews.
- Added previous/next step arrows with Save, Discard, and Cancel choices for unsaved edits.
- Added playback of unsaved form values with later steps paused, without writing the preview to the scene.
- Added middle-click picking inside editor previews for blocks, adjacent placement positions, and face centers, with camera controls and Esc cancellation.
- Added multiple-step selection by right-clicking Copy, then pasting all selected steps in list order after a chosen step.
- Added long-press vertical dragging to adjust coordinate and vector fields in `0.5` increments.
- Added long-press vertical dragging to adjust duration fields in `10-tick` increments, clamped at zero.

##### World-Generation Structures

- Added server-backed world-generation structure selection with available starting-template previews and confirm/clear controls.

##### Remote Sync and Resource Packs

- Added pending remote updates with individual/bulk ignore or overwrite, persistent ignores, and renewed notices for newer versions.
- Added Auto, Always On, and Always Off sync modes; Auto disables sync in singleplayer and enables it in multiplayer and LAN.
- Added source-pack multi-selection with icons and content counts, applied together on confirmation.

##### Particle Effects and Java Export

- Added a generic Particle Effect step with success, redstone, smoke, spit, item, and block presets.
- Added vanilla and modded particle IDs with SNBT parameters and validation when saving.
- Added point, box, and sphere distributions with position, spread, count per tick, emission duration, and initial velocity.
- Added Java export for generic particles, Ponder categories, and item-tag bindings, expanding item tags to current item IDs and omitting NBT filters.

### Improvements

##### Interface Scenes

- Slot editing now supports dragging existing items, right-click clearing, and saving counts and components.
- Text, slot overlays, and JEI now use the embedded container bounds, with ingredient lookup for its slots.

##### Text Progress

- Added a default text-progress setting, also saved by right-clicking the Comfy Reading button.
- Matched the text-progress panel to native Ponder speech-box colors and borders, with a pointer to the Comfy Reading button.

##### Item Selection and Ponder Creation

- Added inventory-picker buttons to item fields to fill item IDs and NBT, with outside-click dismissal.
- Simplified new-Ponder item selection to an inventory picker with an optional Capture NBT toggle.
- New Ponders now start with structure/interface scene selection followed by description editing; cancelling type selection leaves no local file.
- Added the expanded NBT editor to new-Ponder forms for editing full SNBT before a scene exists.

##### Step Lists and Field Editing

- Replaced step insert, copy, paste, and delete labels with icons and highlighted selected Copy buttons.
- Step rows now show types alongside gray parameter summaries, with full text, coordinates, counts, and actions on hover.
- Step summaries now resolve localized item, block, and entity names, available sound subtitles, and shared text.
- Step-list searches now match both step types and parameter summaries.
- Added distinct icons for inventory picking, block/NBT capture, point picking, expanded editing, and structure selection.
- Standardized example values and hover descriptions for item, NBT, angle, scale, link, and structure fields.
- Distinguished scene, world, and interface coordinates from vectors, with integer/decimal formats and valid ranges.
- Block ranges and coordinate triggers now accept decimals and floor them to containing-block coordinates, including negative values.

##### Camera Animation

- View adjustments now ease through panning followed by zooming, reaching their targets within the configured duration.
- Camera rotation now uses Ponder easing, with duration controlling the wait and zero duration remaining instantaneous.

##### Structures, Blueprints, and Picking Hints

- Moved structure-preview loading and analysis to background workers with loading feedback.
- Split preview block population and model baking across frames to reduce stalls when loading large structures.
- Added stage-specific blueprint hints for corner selection, resizing, and saving, alongside selection dimensions.
- Kept the blueprint page and saved structure ID visible after success, disabling duplicate saves.
- Added an introduction to blueprint usage before the built-in selection and saving demonstrations.
- Added a persistent waiting hint while fetching block-entity NBT, clearing the state when changing worlds.
- Kept coordinate-picking hints visible throughout selection, clearing them on completion.
- Coordinated world tool, blueprint, and Ponder-trigger hints, prioritizing transient results and clearing hints when tools end.

##### Remote Management

- Pulling a specific remote scene or structure now overwrites its local copy, including a scene's dependent structures.
- Added icons for remote pull, upload, history, ignore, and overwrite actions, with warning colors for overwrite and delete.

##### JEI Integration

- Replaced JEI field toggles with preview slots accepting matching item, block, entity, and other ingredient drops.
- Entity fields now resolve boats and other matching entity items in addition to spawn eggs.
- Editors now preserve JEI visibility preferences and explain how to reveal a hidden or space-constrained ingredient list.

##### Scene Rendering

- Added idle scene-cache cleanup and resource release when projector scenes are discarded or worlds change.

##### UI and Action Feedback

- Unified unsaved-change dialogs with explicit save, discard, and continue-editing choices.
- Unified horizontal margins, control alignment, and spacing across list-entry types, and removed white selection outlines.
- Aligned button borders with hit bounds and standardized `1px` inner padding for inputs and ingredient slots.
- Unified section headings and dividers across menus, lists, and item browsing, plus editor backgrounds.
- Added confirm/cancel icons to selectors and exports, distinguishing action execution from saving edits.
- The V menu now directly lists general, AI, blueprint, projector, and keybinding settings, with permissions under Sync.
- Moved UI action results to their originating screens, distinguishing information, success, warnings, and errors by color.

##### AI Generation and Editing

- Added multi-turn generation with structure, registry, step-schema, and reference queries, draft review, and scoped editing or full rewrites of existing scenes.
- Added a streaming output panel, instructions during runs, cancellation, and direct playback of generated scenes.
- Added run status, usage, and change history, with interrupted-draft recovery and undo for saved changes.
- Added Responses, Chat Completions, and Anthropic native tools or JSON compatibility, with reasoning-effort, timeout, and run-budget settings.
- Switched to built-in prompts and archived legacy files, with runtime-grounded validation, context compaction, transient-error retries, and save-conflict protection.

### Fixes

##### JEI Integration

- Fixed incompatibility with newer JEI versions.
- Fixed ingredient-drop bounds after scrolling so offscreen or clipped fields cannot receive drops.
- Switched ingredient rendering and tooltips to JEI's native APIs, preserving mod-added details and older JEI compatibility.

##### Item NBT

- Fixed stack construction from 1.21.1 components or legacy NBT for previews, triggers, and editor returns.

##### Interface Scenes

- Fixed expanded item-NBT edits not returning correctly to Show Interface steps.

##### Ponder Playback and Navigation

- Fixed the text-progress panel covering scene text.
- Fixed GUI scaling being overwritten by navigation-history screens when returning from Ponder to the item browser.
- Fixed segment transitions returning to the item list prematurely, restoring the originating page only after leaving playback.

##### Item Selection and Scene Saving

- Item-entity picking now fills counts, and selecting items without NBT clears stale NBT values.
- Wrote scenes to temporary files before replacement to reduce corruption from interrupted saves.

##### Text and Coordinate Editing

- Fixed cancelled text edits mutating the original step's localized text.
- Invalid trigger coordinates now report errors instead of silently becoming zero.

##### Block and Section Animation

- Removed the misleading None option from animated block entrances and explicitly defaulted to a downward entrance.
- Fixed duplicate or stale blocks during placement, hiding, replacement, destruction, and section merges, including Java exports.
- Fixed smart entrances to use runtime section visibility, avoiding repeated reveals or disappearing overlaps.

##### Structure Preview

- Fixed preview lighting leaking into other UI, block-entity renderers disturbing transforms, and inverted vertical dragging.

##### Automatic Sync

- Automatic sync now preserves locally edited or deleted scenes and structures, directing conflicts to remote management.

##### Projectors

- Fixed the projector config preview to use a consistent facing independent of its world placement.

##### Lists and Ponder Compatibility

- Fixed list scrollbars losing focus after clicks and failing to drag.
- Fixed editor `NoSuchFieldError` failures after Ponder 1.0.87 removed the legacy hint field.

---

## 1.10.6.1

### 修复 / Fixes

- **JEI 依赖范围修正**：移除 NeoForge 侧 JEI 依赖的 19.43 版本上限，允许使用 19.21.0.243 及以上版本。
  Removed the JEI 19.43 upper version limit in NeoForge metadata, allowing JEI 19.21.0.243 and later.

---

## 1.10.6

### 修复 / Fixes

- **JEI 界面兼容性**：改进对不同 JEI 叠层渲染接口的适配，修复思索界面中物品列表、书签与 tooltip 的显示兼容问题，并补齐 GUI 区域信息同步。
  Improved compatibility with different JEI overlay rendering APIs, fixing ingredient-list, bookmark, and tooltip rendering in Ponder screens and synchronizing GUI bounds.

---

## 1.10.5

### 新增功能 / New Features

- **NeoForge Java 模组导出**：Java 导出新增 NeoForge 目标工程识别，支持读取 `neoforge.mods.toml` 并生成对应的客户端场景注册代码。
  Added NeoForge target-project detection to Java export, including `neoforge.mods.toml` parsing and generated client-side scene registration.

### 改进 / Improvements

- **1.21.1 导出代码适配**：更新生成代码中的资源路径、物品自定义数据与结构 NBT 读取方式，使导出模板适配 1.21.1 API。
  Updated generated resource-location, item custom-data, and structure NBT code to use the 1.21.1 APIs.

### 修复 / Fixes

- **AE2 线缆渲染**：补充 NeoForge 思索场景中的方块实体模型数据读取，修复 AE2 线缆不显示的问题。
  Added block-entity model-data access in NeoForge Ponder levels, fixing missing AE2 cable rendering.

---

## 1.10.4

### 改进 / Improvements

- **投影仪播放重构**：投影场景改由客户端根据源物品解析并维护播放状态，服务端发送红石触发信号；手动播放与进度定位使用客户端播放流程，并改进场景缓存与渲染边界计算。
  Reworked projectors to resolve scenes from the source item and manage playback on the client, with server-side redstone trigger signals. Manual playback and seeking now use the client playback flow, with refined scene caching and render bounds.

- **投影仪配置界面**：调整投影仪模型的显示位置、比例与朝向，补充 JEI 避让区域，并改进时间轴末尾关键帧的悬浮与定位。
  Refined projector model placement, scale, and orientation in the config screen, added JEI exclusion areas, and improved hovering and seeking at timeline endpoints.

- **远程同步去重**：拉取时跳过内容相同的本地文件，减少重复写入与无变化的更新提示。
  Remote pulls now skip local files with identical contents, reducing redundant writes and update notifications.

### 修复 / Fixes

- **投影播放状态修复**：修复仅红石状态同步导致手动播放重置的问题，完善手动循环、单次播放结束与末帧保留时的动画冻结逻辑。
  Fixed manual playback resetting on redstone-only state updates, and refined manual looping, single-run completion, and animation freezing when retaining the final frame.

- **跨加载器思索适配**：调整 Fabric / NeoForge 的结构加载、NBT 场景过滤与场景变换相关 Mixin 映射，并补齐投影场景的动画时钟适配。
  Adjusted Fabric / NeoForge mixin mappings for structure loading, NBT scene filtering, and scene transforms, and expanded animation-clock support for projected scenes.

---

## 1.10.2

### 新增功能 / New Features

- **远程工作区自动同步**：玩家加入服务器后自动同步可拉取的场景与结构；远端上传、删除、回滚及后台检测到的文件变化会增量同步给有拉取权限的在线玩家。
  Added automatic scene and structure sync on server join, plus incremental updates for players with pull access after remote uploads, deletions, rollbacks, or changes detected by background scans.

### 改进 / Improvements

- **删除同步与本地冲突保护**：同步支持远端文件删除，并保留存在本地修改的文件，补充删除冲突与失败提示。
  Sync now propagates remote file deletions while preserving locally modified files, with deletion-conflict and failure messages.

- **同步节流与反馈**：自动同步采用后台轮询、变更合并与分批发送，并仅在产生更新、跳过或冲突时显示同步结果。
  Automatic sync uses background polling, coalesced changes, and batched delivery, showing results only when files are updated, skipped, or conflicted.

---

## 1.10.1

### 新增功能 / New Features

- **蓝图合成配方**：新增内置蓝图的无序合成配方，可使用纸、淡蓝色染料与羽毛合成。
  Added a shapeless recipe for the built-in blueprint using paper, light blue dye, and a feather.

### 改进 / Improvements

- **投影仪配置界面**：新增投影仪模型展示，并在未放入源物品时为播放、远端拉取操作提供明确提示。
  Added a projector model display to the config screen and clear prompts when playback or remote pulls are attempted without a source item.

### 修复 / Fixes

- **1.21.1 配方加载与功能开关**：修正投影仪配方目录为 `recipe/`，为 NeoForge / Fabric 补充蓝图与投影仪的配置加载条件，禁用对应功能时不再加载其配方。
  Corrected projector recipes to use the 1.21.1 `recipe/` directory and added NeoForge / Fabric config conditions so blueprint and projector recipes are omitted when their features are disabled.

---

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

- **跨加载器网络与注册整理**：整理 Common / NeoForge / Fabric 的注册与网络辅助接口，补齐 Fabric Mod Menu 集成，并改善配置同步后的投影仪状态一致性。
  Refined Common / NeoForge / Fabric registration and networking helpers, added Fabric Mod Menu integration, and improved projector state consistency after config sync.

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

## 1.6.0

### 改进 / Improvements

- **多平台架构重构**：使用 Lotus 多平台架构重构代码，单一代码库同时支持 NeoForge 和 Fabric（1.21.1）/ Forge 和 Fabric（1.20.1）。
  Refactored to a multi-platform architecture (Lotus) — a single codebase now builds for both NeoForge + Fabric (1.21.1) and Forge + Fabric (1.20.1).

- **新增 Fabric 支持**：1.20.1 和 1.21.1 均新增 Fabric 加载器支持。
  Added Fabric loader support for both 1.20.1 and 1.21.1.

---

## 1.5.3.1

### 修复 / Fixes

- 修复了从旧分支移植到多平台架构时的兼容性问题。
  Fixed compatibility issues when porting from the legacy branch to the multi-platform architecture.

---

## 1.5.3

### 改进 / Improvements

- **重构 UI 代码**：大规模重构了编辑器界面代码，引入统一的布局常量系统（UILayoutConstants），提升各编辑器屏幕的一致性和可维护性。
  Major UI code refactor — introduced a unified layout constants system (UILayoutConstants) for improved consistency and maintainability across all editor screens.

---

## 1.5.2.4

### 新增功能 / New Features

- **MC百科自动链接**：在 AI 场景生成中新增 MC百科（mcmod.cn）自动链接功能，可自动获取相关模组/物品的百科页面作为参考。（感谢 GitHub @Sddft97 的 PR）
  Added automatic MC Wiki (mcmod.cn) linking in AI scene generation — automatically fetches relevant mod/item wiki pages as reference. (Thanks to GitHub @Sddft97 for the PR)

---

## 1.5.2.3

### 改进 / Improvements

- 从 Forge 版跟进了内容更新，包括 AI 场景生成、配置面板、JEI 集成优化、场景包功能等。
  Synced content updates from the Forge branch, including AI scene generation, config panel, JEI integration improvements, scene pack features, and more.

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
