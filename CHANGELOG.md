# Changelog

## 1.11.0

### 新增功能 / New Features

- **物品标签挂载**：支持用 `#物品标签` 批量挂载思索，并可附加 NBT 匹配条件。
  Added item-tag bindings with optional NBT filters to attach a Ponder to every matching item.

- **多物品挂载**：可添加、移除多组物品 ID 或标签，每组独立设置 NBT 条件。
  Added multiple item ID or tag bindings, each with its own NBT filter and add/remove controls.

- **思索分类标签**：描述编辑页新增标签选择器，支持搜索、图标预览和多选，并关联全部分场景与挂载物品。
  Added a searchable category picker with icons and multiple selection, linking tags to all segments and bound items.

- **自定义思索标签**：可创建、编辑、重命名和删除标签，设置中英文名称、说明与物品图标，ID 自动添加 `ponderer:` 前缀。
  Added custom-category creation, editing, renaming, and deletion with bilingual text, item icons, and an automatic `ponderer:` namespace.

- **思索者来源标签**：所有思索自动附带不可移除的“思索者...”标签，Java 导出后也保留来源说明。
  Added a mandatory The Ponderer category to all scenes, retaining attribution in Java exports.

- **从世界捕获界面**：正常打开目标容器即可捕获来源、开屏数据、槽位和可见方块上下文，保存后可在思索中回放。
  Added world capture for container sources, opening data, slots, and visible block context for replay inside Ponder.

- **界面试用模式**：新增“试一试”、重置和退出操作，可暂停教程并在独立演示环境中操作容器。
  Added Try, Reset, and Exit controls to pause tutorials and interact with containers in a separate demo environment.

- **界面槽位脚本**：新增 `set_interface_slots`，可写入带数量和数据组件的物品，并叠加流体等非物品配料图标。
  Added `set_interface_slots` for item counts and components, plus display-only non-item ingredient overlays.

- **界面数值动画**：新增 `set_interface_data`，可按指定时长改变容器数据槽数值。
  Added `set_interface_data` to animate menu data-slot values over a specified duration.

- **界面玩家上下文**：新增 `set_interface_context`，可设置演示玩家的游戏模式、经验、背包和选中快捷栏。
  Added `set_interface_context` for demo-player game mode, experience, inventory, and selected hotbar slot.

- **界面操作脚本**：新增 `interface_action`，支持点击、拖动物品、快捷移动、滚动、输入文字与按键，并显示演示光标。
  Added `interface_action` for clicks, item drags, quick moves, scrolling, text, and keys, with an animated demo cursor.

- **界面隐藏脚本**：新增 `hide_interface`，可在时间线中关闭当前演示界面。
  Added `hide_interface` to close the current demo interface from the timeline.

- **界面练习脚本**：新增 `await_interface`，可等待玩家完成槽位目标，并设置提示、超时或跳过。
  Added `await_interface` for slot-based exercises with hints, timeouts, and a skip control.

- **粒子预设**：新增通用“粒子效果”步骤，提供成功、红石、烟雾、飞溅、物品碎屑和方块碎屑预设。
  Added a generic Particle Effect step with success, redstone, smoke, spit, item, and block presets.

- **自定义粒子**：支持填写原版或模组粒子 ID 及 SNBT 参数，保存时校验粒子与参数。
  Added vanilla and modded particle IDs with SNBT parameters and validation when saving.

- **粒子分布与发射**：可设置定点、方盒或球体分布，以及位置、范围、每刻数量、持续时间和初始速度。
  Added point, box, and sphere distributions with position, spread, count per tick, emission duration, and initial velocity.

- **进度条自由定位**：支持点击和长按拖动到任意时间，并吸附附近关键帧及首尾位置，无关键帧时也可使用。
  Added click-and-drag seeking at any time, with snapping to nearby keyframes and endpoints, including scenes without keyframes.

- **播放倍速**：新增 `0.5x`、`1x`、`2x` 独立按钮，可直接选择播放速度。
  Added dedicated `0.5x`, `1x`, and `2x` playback buttons.

- **场景缩放滑条**：新增 `0.2x–5x` 缩放滑条，中点为 `1x`，悬停显示当前倍率，右键可重置。
  Added a `0.2x–5x` scene zoom slider with `1x` at the midpoint, a hover multiplier, and right-click reset.

- **暂停自由预览**：可用按钮或空格暂停，悬停识别方块，并用左键旋转、右键平移和滚轮缩放，按 `Q` 可恢复播放与原视角。
  Added button/Space toggling for paused block inspection, left-drag rotation, right-drag panning, and wheel zoom; `Q` resumes playback and restores the view.

- **步骤列表预览**：新增场景预览，滚动列表可定位到顶部步骤，拖动时间轴可同步滚动列表。
  Added a scene preview that seeks to the top visible step as the list scrolls and scrolls the list when scrubbing the timeline.

- **步骤编辑预览**：步骤编辑页新增场景预览，进入时定位到当前步骤，往返列表页时保留视角与文本显隐状态。
  Added scene previews to step editors, seeking to the current step and retaining camera and text visibility when returning to the list.

- **编辑器预览视角**：支持左键旋转、右键平移、滚轮缩放及一键重置。
  Added left-drag rotation, right-drag panning, wheel zoom, and one-click camera reset to editor previews.

- **预览文本开关**：暂停自由预览和编辑器预览可按 `T` 隐藏或显示思索文本。
  Added `T` to hide or show Ponder text in paused free previews and editor previews.

- **步骤快速切换**：编辑页可用上下箭头切换步骤，存在未保存修改时可选择保存、放弃或取消切换。
  Added previous/next step arrows with Save, Discard, and Cancel choices for unsaved edits.

- **当前步骤试播**：可直接试播表单中的未保存步骤，播放时暂缓后续步骤，试播不会写入场景。
  Added playback of unsaved form values with later steps paused, without writing the preview to the scene.

- **预览内选点**：可在编辑预览中旋转视角并用中键取点，自动区分方块、相邻放置位置与面中心，按 `Esc` 取消。
  Added middle-click picking inside editor previews for blocks, adjacent placement positions, and face centers, with camera controls and Esc cancellation.

- **多步骤复制粘贴**：右键复制按钮可多选步骤，再按列表顺序一次粘贴到指定步骤后。
  Added multiple-step selection by right-clicking Copy, then pasting all selected steps in list order after a chosen step.

- **坐标拖动调节**：坐标与向量输入框支持长按后上下拖动，以 `0.5` 为步长调整。
  Added long-press vertical dragging to adjust coordinate and vector fields in `0.5` increments.

- **时长拖动调节**：时长输入框支持长按后上下拖动，以 `10 tick` 为步长调整且不低于零。
  Added long-press vertical dragging to adjust duration fields in `10-tick` increments, clamped at zero.

- **世界生成结构预览**：结构列表可读取服务器可用结构，预览可用的起始模板，再确认选择或清空。
  Added server-backed world-generation structure selection with available starting-template previews and confirm/clear controls.

- **AI 多轮生成**：改为多轮工具调用流程，可查询信息、检查草稿并修正后提交，默认 12 轮，上限可设为 `2–64`。
  Reworked AI generation into a tool-driven query, review, and revision loop, defaulting to 12 turns with a configurable limit of `2–64`.

- **AI 结构查询**：可按概览、层、坐标、区域或方块查询所选结构，并读取相关 NBT。
  Added AI queries for structure summaries, layers, positions, regions, blocks, and related NBT.

- **AI 注册表查询**：可按名称查找物品等注册项，并读取方块属性、可用值及配料信息。
  Added AI registry lookup by name and inspection of block properties, valid values, and ingredient information.

- **AI 步骤规则查询**：可按需读取步骤字段、约束、时序和示例，并补齐界面步骤规则。
  Added on-demand AI access to step fields, constraints, timing, and examples, including interface-step contracts.

- **AI 参考资料检索**：可检索参考网页内容、读取相关图片，并按需检查已有场景的分段与步骤。
  Added AI searches over reference pages and images, plus targeted inspection of existing scene segments and steps.

- **AI 参考分段读取**：可连续读取参考资料的指定片段，补齐搜索摘要之外的上下文。
  Added consecutive reference-chunk reading to retrieve context beyond search excerpts.

- **AI 场景质量检查**：新增长文本、缺少视觉引导、等待时长、掉落物清理和中英文本完整性等检查。
  Added checks for long text, missing visual guidance, wait durations, item cleanup, and bilingual text completeness.

- **终止 AI 生成**：新增终止按钮，可取消模型请求、网页和图片读取，停止后保留可恢复草稿。
  Added cancellation for model requests, page and image fetching, retaining a recoverable draft when stopped.

- **AI 编辑已有思索**：可从列表选择已有思索并读取其结构，默认仅修改本次要求涉及的内容。
  Added existing-scene selection with its structures, defaulting to changes within the requested scope.

- **AI 整体重写**：编辑已有思索时可显式开启整体重写，允许重新组织全部场景内容。
  Added an explicit whole-scene rewrite toggle when editing an existing Ponder.

- **AI 草稿局部修改**：支持按字段增删替换草稿，版本或原值冲突时整批拒绝修改。
  Added transactional field-level draft additions, replacements, and removals, rejecting the entire patch on revision or old-value conflicts.

- **AI 补充指令**：生成过程中可追加要求，指令排队到下一轮处理，并随草稿保留。
  Added instructions during generation, queued for the next turn and retained with the draft.

- **AI 输出侧栏**：新增可滚动的输出面板，自动跟随最新消息，上翻查看后可一键回到最新内容。
  Added a scrollable AI output panel with automatic following and a return-to-latest control after scrolling back.

- **AI 流式响应**：支持流式接收并显示响应进度，服务不支持时可在配置中关闭。
  Added streamed responses with reception progress and a configuration toggle for services without streaming support.

- **AI 原生工具调用**：支持 Responses、Chat Completions 与 Anthropic 原生函数调用，也可选择 JSON 兼容模式。
  Added native tools for Responses, Chat Completions, and Anthropic, with an optional JSON compatibility mode.

- **AI 协议选择**：可自动识别官方地址或手动选择 API 协议，自定义地址保留填写的路径前缀。
  Added automatic or explicit API protocol selection while preserving custom base-URL path prefixes.

- **AI 推理强度**：可继承 Codex、Claude Code 配置中的推理强度，或在游戏内统一覆盖。
  Added reasoning-effort inheritance from Codex and Claude Code configurations, with an in-game override.

- **AI 请求超时**：可将单次请求超时设为 `30–1800` 秒，默认 300 秒，对所有配置来源生效。
  Added a `30–1800` second request timeout, defaulting to 300 seconds across all configuration sources.

- **AI 运行预算**：可设置整次运行的时间、累计 token 预算与上下文容量，到达限制后保留草稿并停止。
  Added run-wide time, cumulative token, and context limits, retaining the draft when a limit stops generation.

- **AI 无进展停机**：连续多轮没有新资料或草稿变化时自动停止，可配置判定轮数。
  Added configurable automatic stopping after consecutive turns without new evidence or draft changes.

- **AI 未完成任务**：遇到信息不足或无法完成的任务时，可保留部分草稿并说明停止原因。
  Added partial-draft retention with a stop reason when information is missing or a task cannot be completed.

- **AI 运行记录**：新增运行记录页，可查看最近 50 次运行的状态、轮次、工具次数、用量与耗时，并打开运行文件。
  Added history for the latest 50 runs with status, turns, tool calls, usage, elapsed time, and access to run files.

- **AI 修改差异**：运行记录可逐项查看修改前后的字段，并复制完整差异。
  Added before/after field inspection and full-diff copying in AI run history.

- **AI 草稿恢复**：中断或未完成的运行可补充说明后恢复，并开启新的运行预算。
  Added recovery of interrupted or unfinished drafts with optional clarification and a fresh run budget.

- **AI 保存撤销**：可从运行记录撤销一次保存，恢复原场景或删除该次新建场景，并保护后续修改。
  Added undo for saved AI changes, restoring the original scene or removing a newly created one while protecting later edits.

- **查看生成的思索**：可直接播放最近一次成功生成的全部分场景，返回后继续编辑该思索。
  Added direct playback of all segments from the latest successful generation, returning to edit that scene afterward.


- **远程待处理更新**：新增冲突列表，可逐项或一键忽略、覆盖，忽略记录跨重连保留并在远端出现新版本时重新提示。
  Added pending remote updates with individual/bulk ignore or overwrite, persistent ignores, and renewed notices for newer versions.

- **自动同步客户端设置**：新增自动、始终开启和始终关闭三种模式，自动模式单人关闭、联机及局域网开启。
  Added Auto, Always On, and Always Off sync modes; Auto disables sync in singleplayer and enables it in multiplayer and LAN.

- **源资源包多选**：可在同一列表勾选多个源资源包，查看图标与内容数量，确认后统一应用。
  Added source-pack multi-selection with icons and content counts, applied together on confirmation.

- **AE2 界面适配**：支持从 AE2 方块或线缆部件创建对应的本地演示菜单。
  Added local demo-menu opening for AE2 blocks and cable-bus parts.

### 改进 / Improvements

- **标签选择列表**：改用带图标、名称与 ID 的整行选择，编辑和删除操作收拢为图标按钮。
  Updated category selection to full rows with icons, names, IDs, and compact edit/delete buttons.

- **来源标签图标**：“思索者...”标签改用持续旋转的专用侦测器模型，Java 导出同步附带模型与纹理。
  Added a rotating custom observer icon to the attribution category, including its model and texture in Java exports.

- **物品与标签候选**：悬停 ID 输入框可展开所属标签，支持点击或滚轮切换，标签图标会轮播成员物品。
  Added hover dropdowns and wheel selection for item IDs and their tags, with cycling tag-member icons.

- **挂载功能联动**：物品目录、快捷触发和投影仪同步识别多物品、标签及独立 NBT 条件，标签重载后刷新场景。
  Item browsing, triggers, and projectors now resolve multiple bindings, tags, and per-binding NBT, refreshing scenes after tag reloads.

- **自定义标签引用保护**：其他思索仍引用的标签不能重命名或删除，未注册的已有标签可保留或移除。
  Protected custom categories referenced by other scenes from renaming/deletion, while keeping unavailable saved tags editable.

- **背包物品选择**：多处物品字段新增背包选择按钮，可回填物品 ID 与 NBT，点击弹窗外即可关闭。
  Added inventory-picker buttons to item fields to fill item IDs and NBT, with outside-click dismissal.

- **新建思索物品捕获**：移除“使用手持物品”选项，改用背包选择，并以“同时捕获 NBT”控制是否填入 NBT。
  Simplified new-Ponder item selection to an inventory picker with an optional Capture NBT toggle.

- **新建思索流程**：先选择结构或界面场景，再编辑描述，取消类型选择时不写入本地文件。
  New Ponders now start with structure/interface scene selection followed by description editing; cancelling type selection leaves no local file.

- **新建思索 NBT 编辑**：NBT 字段接入展开编辑器，可在场景创建前编辑完整 SNBT。
  Added the expanded NBT editor to new-Ponder forms for editing full SNBT before a scene exists.

- **JEI 字段填入**：物品、方块和实体等字段改用可预览的配料槽，可直接拖入对应 JEI 配料。
  Replaced JEI field toggles with preview slots accepting matching item, block, entity, and other ingredient drops.

- **实体物品识别**：实体字段除刷怪蛋外，也可从船等实体物品解析对应实体 ID。
  Entity fields now resolve boats and other matching entity items in addition to spawn eggs.

- **JEI 显隐设置**：编辑器保留 JEI 原有显隐状态，列表未显示时提示对应快捷键或空间不足。
  Editors now preserve JEI visibility preferences and explain how to reveal a hidden or space-constrained ingredient list.

- **界面槽位编辑**：支持拖动已有物品、右键清空及保存数量和数据组件，并通过实际菜单逻辑预览结果。
  Slot editing now supports existing-item drags, right-click clearing, counts, and components through the actual menu logic.

- **界面回放共用实现**：NeoForge 与 Fabric 共用客户端演示环境，容器操作与网络发送从实际游戏状态中隔离。
  Unified NeoForge and Fabric interface replay in a client-side demo environment with isolated container operations and networking.

- **旧界面步骤兼容**：旧的显示界面、更改槽位和点击界面步骤可继续播放，并接入新的回放流程。
  Kept legacy show, slot-change, and click interface steps compatible with the new replay flow.

- **界面叠层定位**：文本、槽位图标与 JEI 使用嵌入容器的实际范围定位，并支持槽位物品识别。
  Text, slot overlays, and JEI now use the embedded container bounds, with ingredient lookup for its slots.

- **旧粒子步骤兼容**：成功与红石提示仍可直接播放，编辑保存后转为通用粒子步骤并保留位置和关键帧。
  Legacy success and redstone steps still play, and convert on save while preserving position and keyframes.

- **粒子 Java 导出**：Java 模组导出支持通用粒子及其分布、发射数量、速度和持续时间。
  Added Java export for generic particles, distributions, counts, velocity, and emission duration.

- **思索标签 Java 导出**：导出标签注册、物品关联和翻译，自定义标签自动使用目标模组前缀。
  Java export now includes category registration, item associations, and translations under the target mod's custom-category namespace.

- **物品标签 Java 导出**：标签挂载按当前解析出的物品列表导出，并提示标签展开与 NBT 条件未保留的限制。
  Java export expands item tags into their currently resolved item IDs and reports tag expansion and omitted NBT filters.

- **文本进度默认设置**：可配置进入思索时是否显示文本进度，右键“舒适阅读”按钮可切换并保存默认值。
  Added a default text-progress setting, also saved by right-clicking the Comfy Reading button.

- **文本进度面板样式**：沿用思索原生对话框配色和边框，并通过箭头关联“舒适阅读”按钮。
  Matched the text-progress panel to native Ponder speech-box colors and borders, with a pointer to the Comfy Reading button.

- **视角平移与缩放**：调整视角改为前半程缓动平移、后半程缓动缩放，并在设定时长内到达目标。
  View adjustments now ease through panning followed by zooming, reaching their targets within the configured duration.

- **相机旋转**：改用 Ponder 原生缓出旋转，时长控制开始旋转后的等待时间，零时长仍为瞬间切换。
  Camera rotation now uses Ponder easing, with duration controlling the wait and zero duration remaining instantaneous.

- **未保存修改提示**：统一返回与放弃修改对话框，明确提供保存返回、不保存返回和继续编辑选项。
  Unified unsaved-change dialogs with explicit save, discard, and continue-editing choices.

- **列表布局**：统一各类条目的左右边距、控件对齐和间距，并移除点击条目时的白色选中边框。
  Unified horizontal margins, control alignment, and spacing across list-entry types, and removed white selection outlines.

- **控件边界**：按钮外框与点击范围保持一致，输入框和物品展示格统一保留 `1px` 内侧留白。
  Aligned button borders with hit bounds and standardized `1px` inner padding for inputs and ingredient slots.

- **界面分组样式**：统一菜单、列表和物品目录的分组标题、分隔线及编辑页背景。
  Unified section headings and dividers across menus, lists, and item browsing, plus editor backgrounds.

- **字段工具图标**：背包选择、方块与 NBT 抓取、选点、展开编辑和结构选择改用独立图标。
  Added distinct icons for inventory picking, block/NBT capture, point picking, expanded editing, and structure selection.

- **步骤操作按钮**：插入、复制、粘贴和删除改用图标，复制多选状态会高亮。
  Replaced step insert, copy, paste, and delete labels with icons and highlighted selected Copy buttons.

- **远程管理按钮**：拉取、上传、历史、忽略和覆盖改用图标，覆盖与删除使用警示配色。
  Added icons for remote pull, upload, history, ignore, and overwrite actions, with warning colors for overwrite and delete.

- **确认与执行按钮**：选择器、导出和 AI 生成页改用确认与取消图标，区分执行操作与保存修改。
  Added confirm/cancel icons to selectors, exports, and AI generation, distinguishing action execution from saving edits.

- **字段格式提示**：为物品、NBT、角度、缩放、链接和结构字段统一填写示例及悬停说明。
  Standardized example values and hover descriptions for item, NBT, angle, scale, link, and structure fields.

- **坐标类型提示**：区分场景、世界、界面坐标与向量，明确整数、小数及取值范围。
  Distinguished scene, world, and interface coordinates from vectors, with integer/decimal formats and valid ranges.

- **步骤内容摘要**：步骤行并列显示类型和灰色参数摘要，悬停可查看完整文本、坐标、数量及动作。
  Step rows now show types alongside gray parameter summaries, with full text, coordinates, counts, and actions on hover.

- **步骤摘要本地化**：物品、方块、实体、可用音效字幕及共享文本优先显示当前语言内容。
  Step summaries now resolve localized item, block, and entity names, available sound subtitles, and shared text.

- **步骤内容搜索**：步骤列表搜索同时匹配步骤类型与参数摘要。
  Step-list searches now match both step types and parameter summaries.

- **小数方块坐标**：方块范围和坐标触发条件接受小数，保存时向下取整为所在方块坐标，包含负坐标。
  Block ranges and coordinate triggers now accept decimals and floor them to containing-block coordinates, including negative values.

- **设置入口**：V 菜单直接列出通用、AI、蓝图、投影仪及快捷键设置，权限管理移入“同步”分组。
  The V menu now directly lists general, AI, blueprint, projector, and keybinding settings, with permissions under Sync.

- **结构后台读取**：本地结构、服务器模板和 AI 结构信息改为后台读取解析，并显示加载状态。
  Moved local structure loading, server-template reading, and AI structure analysis to background workers with loading feedback.

- **结构预览分帧构建**：方块填充和模型构建分帧进行，减轻大结构加载时的界面卡顿。
  Split preview block population and model baking across frames to reduce stalls when loading large structures.

- **结构预览加载清理**：切换结构、关闭页面或更换世界时，取消过期加载并释放预览资源。
  Cancelled stale preview loads and released resources when changing structures, closing screens, or switching worlds.

- **场景渲染缓存**：定期回收闲置区段缓存，丢弃投影场景或切换世界时释放对应资源。
  Added idle scene-cache cleanup and resource release when projector scenes are discarded or worlds change.

- **手动远程拉取**：拉取指定场景或结构时直接覆盖本地，场景依赖的结构也会同步覆盖。
  Pulling a specific remote scene or structure now overwrites its local copy, including a scene's dependent structures.

- **编辑器初始视角**：预览默认稍微放大，重置视角时恢复相同倍率。
  Increased the default editor-preview zoom and used the same zoom when resetting the view.

- **操作结果提示**：界面操作结果显示在原页面，并用不同颜色区分普通信息、成功、警告和错误。
  Moved UI action results to their originating screens, distinguishing information, success, warnings, and errors by color.

- **世界内提示优先级**：统一协调工具状态、蓝图提示与思索触发提示，短时结果优先显示，退出工具后清理提示。
  Coordinated world tool, blueprint, and Ponder-trigger hints, prioritizing transient results and clearing hints when tools end.

- **蓝图操作提示**：按选角、调整和保存阶段显示操作说明，并同步显示选区尺寸。
  Added stage-specific blueprint hints for corner selection, resizing, and saving, alongside selection dimensions.

- **蓝图保存反馈**：保存成功后保留页面与结构 ID，并禁用重复保存。
  Kept the blueprint page and saved structure ID visible after success, disabling duplicate saves.

- **NBT 选取状态**：等待服务器返回方块实体数据时持续显示等待提示，切换世界后清理状态。
  Added a persistent waiting hint while fetching block-entity NBT, clearing the state when changing worlds.

- **世界选取提示**：坐标选取与界面捕获期间持续显示当前操作提示，完成后自动清除。
  Kept coordinate-picking and interface-capture hints visible throughout the operation, clearing them on completion.

- **蓝图教程说明**：内置教程先介绍蓝图用途，再演示选区和保存操作。
  Added an introduction to blueprint usage before the built-in selection and saving demonstrations.

- **功能入口说明**：移除 AI 与界面型思索的 Beta 标记，打开界面步骤时直接进入编辑。
  Removed Beta labels from AI and interface Ponders, opening interface-step editors directly.

- **AI 提交校验**：补充步骤类型、必填字段、枚举、坐标、注册项与界面数据校验，将错误反馈给模型修正。
  Expanded AI validation for step types, required fields, enums, coordinates, registry entries, and interface data, returning errors for revision.

- **AI 生成反馈**：显示当前轮次、请求与工具状态，记录各轮耗时，并明确标记估算用量。
  Added turn, request, and tool progress with timing records and explicitly labeled estimated usage.

- **AI 质量警告来源**：区分原有、新增和变化的警告，局部编辑时避免因历史问题扩大修改范围。
  Distinguished pre-existing, new, and changed quality warnings to keep local edits within scope.

- **AI 警告修正建议**：警告附带优先级、等待缺口与修正建议，指导模型处理相关问题，同时保持非阻断提交。
  Added warning priorities, timing shortfalls, and repair suggestions while keeping warnings non-blocking.

- **AI 内置提示词**：改用分场景组装的版本化内置提示词，旧提示词文件自动归档后停止加载。
  Switched to versioned built-in prompts assembled for each task, automatically archiving and retiring legacy prompt files.

- **AI 运行环境依据**：向模型提供当前 Minecraft、加载器与已加载模组信息，按实际环境生成内容。
  Supplied Minecraft, loader, and loaded-mod information to ground generation in the current runtime.

- **AI 资料读取校验**：编辑前要求读取目标分场景的实际步骤，参考搜索无结果时不再视为已阅读资料。
  Required actual target-segment steps before edits and stopped treating empty reference searches as completed reading.

- **AI 并行查询**：独立的结构、规则、资料与草稿查询可并行执行，最多同时进行 3 项。
  Added up to three concurrent independent structure, schema, reference, and draft queries.

- **AI 工具错误恢复**：参数或工具执行错误会返回错误代码供模型修正，独立查询可继续完成。
  Returned parameter and tool failures as error codes for revision while allowing independent queries to complete.

- **AI 上下文压缩**：按完整轮次压缩较早对话，保留当前草稿、修改范围、错误及补充要求。
  Compacted older conversation turns while retaining the current draft, edit scope, errors, and added instructions.

- **AI 任务记忆**：可保存带资料来源的事实笔记、决策和后续步骤，供压缩对话与恢复草稿时继续使用。
  Added source-linked working notes, decisions, and next steps that survive conversation compaction and draft recovery.

- **AI 临时失败重试**：网络错误、限流和服务端临时错误最多重试 2 次，并遵守服务要求的等待时间。
  Added up to two retries for network errors, rate limits, and temporary server failures, respecting Retry-After delays.

- **AI 配置迁移**：升级时备份旧配置并迁移协议、地址与默认限额，保留自定义模型和凭据。
  Added backed-up migration of legacy protocols, base URLs, and default limits while preserving custom models and credentials.

- **AI 单次输出上限**：默认提高到 65536 token，可设为 `1024–262144`，对导入配置同样生效。
  Raised the default output limit to 65536 tokens, configurable from `1024–262144` across imported and custom configurations.

- **AI 默认模型**：OpenAI 默认模型更新为 `gpt-6-astra`，Anthropic 更新为 `claude-sonnet-5-5`。
  Updated default models to `gpt-6-astra` for OpenAI and `claude-sonnet-5-5` for Anthropic.

- **AI 生成页面布局**：输出侧栏与表单整体居中，结构预览、翻页、增删和名称集中排列，补充指令紧邻提示词。
  Centered the output panel and form together, grouped structure previews and controls, and placed added instructions beside the prompt.


- **本地验证入口**：启动脚本与 Gradle 支持界面回放自检、指定物品打开思索及超时退出。
  Added interface-replay smoke checks and item-specific Ponder opening to the launcher and Gradle verification flow.

- **本地玩家身份**：开发启动脚本改用固定 UUID，重复启动可沿用同一玩家数据。
  Local development launches now use a fixed UUID to retain the same player data between runs.

- **本地存档占用检查**：冒烟启动前检测存档锁，已占用时跳过对应客户端并明确标记，避免重复启动。
  Local smoke launches now check save locks and explicitly skip occupied worlds before launching another client.

### 修复 / Fixes

- **Ponder 文本框兼容**：修复 Ponder 1.0.87 移除旧提示字段后，打开编辑界面触发 `NoSuchFieldError` 的问题。
  Fixed editor `NoSuchFieldError` failures after Ponder 1.0.87 removed the legacy hint field.

- **界面捕获来源识别**：修复旧手持物品设置误判方块界面的问题，补齐代理方块、无点击来源及扩展开屏数据的捕获。
  Fixed stale held-item settings misclassifying block captures and improved proxy-block, no-click, and extended opening-data capture.

- **自动同步冲突保护**：保留本地修改和手动删除的场景或结构，避免自动覆盖或恢复，并提示前往远程管理处理。
  Automatic sync now preserves locally edited or deleted scenes and structures, directing conflicts to remote management.

- **列表滚动条拖动**：修复步骤列表等页面点击滚动条后丢失焦点、无法持续拖动的问题。
  Fixed list scrollbars losing focus after clicks and failing to drag.

- **放置方块入场选项**：移除动画模式中实际按向下入场处理的“无动画”选项，默认明确选择向下入场。
  Removed the misleading None option from animated block entrances and explicitly defaulted to a downward entrance.

- **选取物品回填**：创建掉落物时同步填入数量，选择无 NBT 物品时清除旧 NBT，避免沿用上次数据。
  Item-entity picking now fills counts, and selecting items without NBT clears stale NBT values.

- **界面物品 NBT 回填**：修复显示界面步骤中，物品 NBT 展开编辑后未正确回填的问题。
  Fixed expanded item-NBT edits not returning correctly to Show Interface steps.

- **新分场景保存**：新建分场景的描述页未改字段时也可保存，避免保存按钮不可用。
  Enabled saving new segment descriptions even when their initial fields have not been changed.

- **坐标触发输入校验**：无效坐标会显示错误，不再被静默替换为零。
  Invalid trigger coordinates now report errors instead of silently becoming zero.

- **结构预览渲染**：修复预览污染后续界面光照、第三方方块实体渲染干扰矩阵及上下拖动反向的问题。
  Fixed preview lighting leaking into other UI, block-entity renderers disturbing transforms, and inverted vertical dragging.

- **世界生成模板显示**：按 `final_state` 替换拼图方块，过滤结构辅助方块，并为缺失或过大的模板提供提示。
  Applied jigsaw `final_state` replacements, hid structure helper blocks, and added missing/oversized-template feedback.

- **倍速与拖动动画**：修正倍速下区段和叠层插值、完成提示，以及拖动进度条时的动画漂移。
  Fixed section/overlay interpolation and completion hints at altered speeds, plus animation drift while scrubbing.

- **暂停预览时间定位**：修复回拖进度条时退出自由预览或丢失视角的问题，并刷新定位后的动画插值。
  Fixed backward seeking leaving free preview or losing its camera view, and refreshed interpolation after seeks.

- **预览方块提示**：修正底板方块和重叠区段的拾取，完整物品提示显示在场景文本上方。
  Fixed picking base-layer blocks and overlapping sections, and rendered full item tooltips above scene text.

- **文本与控件遮挡**：修复文本进度面板遮挡思索文本、倍速按钮盖住下一场景提示的问题。
  Fixed the text-progress panel covering scene text and speed buttons covering the Next Up hint.

- **NBT 物品预览与触发**：统一按 1.21.1 数据组件或旧 NBT 条件构造物品，供预览、触发和编辑返回使用。
  Fixed stack construction from 1.21.1 components or legacy NBT for previews, triggers, and editor returns.

- **JEI 拖入区域**：修正滚动后配料槽的有效区域，避免拖入屏幕外或被裁剪的字段。
  Fixed JEI drop bounds after scrolling so offscreen or clipped fields cannot receive drops.

- **JEI 界面避让**：为编辑器预览、控件、背包弹窗和标签候选预留空间，避免与 JEI 重叠。
  Added JEI exclusion areas for editor previews, controls, inventory popups, and tag dropdowns to prevent overlap.

- **返回列表界面缩放**：修复退出思索返回物品目录时，界面缩放被历史页面初始化覆盖的问题。
  Fixed GUI scaling being overwritten by navigation-history screens when returning from Ponder to the item browser.

- **投影仪模型朝向**：配置界面模型使用固定朝向，避免随世界中投影仪的放置方向变化。
  Fixed the projector config preview to use a consistent facing independent of its world placement.

- **参考网页图片**：修复相对图片路径解析，允许 AI 读取网页相对路径下的参考图片。
  Fixed relative image URL resolution when fetching AI reference pages.

- **文本编辑草稿隔离**：取消文本编辑或试播未保存步骤时，不再改动原步骤的多语言文本。
  Fixed cancelled text edits and unsaved previews mutating the original step's localized text.

- **预览动画时长**：时间轴覆盖非阻塞动画的完整持续时间，避免末尾效果被截断。
  Extended preview timelines through non-blocking effects so animations at the end can finish.

- **试播后时间定位**：单步试播后拖动时间轴会恢复正常回放，包括定位到当前刻或结尾。
  Fixed timeline seeking after single-step previews to restore normal playback, including the current tick and endpoint.

- **新增方块重影**：显示结构时只接管当时已有的方块，后续放置方块不再被旧区段重复绘制。
  Fixed structure reveals capturing future block placements and rendering them in multiple sections.

- **智能入场判断**：按播放时的实际可见区段判断已显示方块，避免重叠结构重复入场或已显示部分消失。
  Fixed smart entrances to use runtime section visibility, avoiding repeated reveals or disappearing overlaps.

- **隐藏与销毁残留**：隐藏、替换和销毁方块时清理各区段的旧归属，避免动画合并后重新出现。
  Fixed stale section ownership during hiding, replacement, and destruction, preventing blocks from reappearing after merges.

- **Java 导出区段显示**：导出代码同步采用新的区段归属逻辑，修复放置、显隐与合并动画中的重影。
  Updated exported Java section ownership to fix ghosting during placement, visibility changes, and merges.

- **分场景切换返回**：切换思索分场景时不再被提前送回物品列表，退出播放后再返回原页面。
  Fixed segment transitions returning to the item list prematurely, restoring the originating page only after leaving playback.

- **标签图标渲染**：绘制来源标签图标后恢复深度测试，避免后续进度条和列表渲染异常。
  Restored depth testing after attribution-icon rendering to prevent broken progress bars and lists.

- **异步操作反馈归属**：服务器回复只更新发起操作的页面，避免延迟结果串入其他页面或聊天栏。
  Routed server replies to the initiating screen so delayed results cannot leak into unrelated pages or chat.

- **远程操作状态保留**：刷新远程目录时保留上传、删除等操作结果，避免状态立即被覆盖。
  Preserved upload, deletion, and other action results when refreshing the remote catalog.

- **AI 文本等待检测**：累计相邻文本之间的实际阻塞时长，识别缺少等待，并区分效果时长与时间线等待。
  Fixed text pacing checks to total blocking time between annotations and distinguish effect lifetimes from timeline waits.

- **AI 新建标签处理**：忽略模型自行生成的分类标签与定义，避免新思索引用未注册标签。
  Ignored model-generated category tags and definitions in new scenes to prevent unregistered category references.

- **AI 保存冲突保护**：保存前复查原文件，发生外部修改或新建 ID 重名时保留草稿并拒绝覆盖。
  Rechecked original files before saving, retaining drafts and refusing to overwrite external edits or existing IDs.

- **AI 中断记录恢复**：从完整日志事件恢复运行状态，忽略中断写入的末行，并兼容旧版记录。
  Recovered run state from complete journal events, ignoring torn final writes and retaining legacy-record compatibility.

- **JEI 配料提示**：使用 JEI 原生配料提示与渲染，保留模组附加信息，并兼容旧版 JEI。
  Switched ingredient rendering and tooltips to JEI's native APIs, preserving mod-added details and older JEI compatibility.

- **本地模组包更新**：启动脚本改为暂存后替换 JAR，文件被占用时明确报错，避免覆盖运行中的模组包。
  Local launchers now stage JAR replacements and report locked files instead of overwriting a running client's archive.

- **Windows Java 启动**：为 Gradle 与测试客户端指定项目内 IPC 临时目录，规避 Java 21 本地回环连接失败。
  Set a project-local IPC directory for Gradle and test clients to avoid Java 21 loopback initialization failures on Windows.

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
