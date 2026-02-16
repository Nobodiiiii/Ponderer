# Ponderer 模组介绍

Create 制作组在 6.0 版本将 Ponder 拆分为独立模组，然而较高的使用门槛使这一优秀功能未能被更多玩家所体验。Ponderer 正是为此而生。

Ponderer 是一个面向玩家与整合包作者的「游戏内思索（Ponder）制作工具」。
你不需要离开游戏，也不需要先写脚本，就可以直接在世界里搭建、录制和调整思索教学流程。

## 你可以用它做什么

- **在游戏内新建思索**：从手持物品或指定物品快速生成新的思索条目（支持不同nbt分别思索，如不同署名的成书）。
- **可视化编辑步骤**：点击本模组创建的思索左下角编辑图标，即可通过图形界面编辑文本、镜头、方块变化、实体生成、音效等 Ponder 步骤。所有坐标字段均支持从场景中直接选点（左键选取方块坐标，右键选取相邻方块坐标）。
- **复制粘贴与撤销重做**：支持步骤复制/粘贴、在指定位置插入、Ctrl+Z 撤销 / Ctrl+Y 重做。
- **蓝图选区与结构保存**：使用蓝图工具（默认为纸）选择区域并保存结构，便于复用和迭代演示内容。
- **开箱即用**：内置了对应引导思索；手持**书与笔**即可直接查看示例思索。
- **本地热重载**：编辑后可直接重载，快速查看效果，减少反复重启。
- **多人协作同步**：支持从服务器拉取与向服务器推送思索内容，方便团队协作维护教程或快速获取其他玩家创建的思索。
- **格式互转**：支持与 PonderJS 格式互相转换，兼容不同工作流。
- **场景包导入导出**：支持将所有场景和结构打包为 ZIP 文件，方便在社区分享或备份。
- **JEI 集成**：所有 ID 输入框支持从 JEI 物品列表中点击或拖放自动填入（需安装 JEI），方块、物品、实体三种模式自动过滤不兼容类型。
- **方块状态属性**：放置或替换方块时可指定朝向、半边等 BlockState 属性，编辑器中以动态列表形式输入。
- **扩展实体解析**：船、矿车、盔甲架等物品类实体也可通过 JEI 直接拖入实体 ID 字段。

## 指令总览（用途 + 用法）

- `/ponderer reload`：重载本地场景脚本并刷新思索索引。
- `/ponderer pull`：从服务端拉取改动（冲突检查模式）。
- `/ponderer pull force`：强制以服务端版本覆盖本地。
- `/ponderer pull keep_local`：拉取时尽量保留本地版本。
- `/ponderer push`：推送本地场景到服务端（冲突检查模式）。
- `/ponderer push force`：强制覆盖服务端场景。
- `/ponderer push <id>`：仅推送指定场景 ID。
- `/ponderer push force <id>`：强制推送并覆盖指定场景 ID。
- `/ponderer download <id>`：将指定结构导入到 Ponderer 的结构目录。
- `/ponderer new hand`：以主手物品创建新场景。
- `/ponderer new hand use_held_nbt`：以主手物品 + 当前物品 NBT 创建场景。
- `/ponderer new hand <nbt>`：以主手物品 + 指定 NBT 创建场景。
- `/ponderer new <item>`：以指定物品创建新场景。
- `/ponderer new <item> <nbt>`：以指定物品 + 指定 NBT 创建新场景。
- `/ponderer copy <id> <target_item>`：复制指定场景并改绑到目标物品。
- `/ponderer delete <id>`：删除指定场景。
- `/ponderer delete item <item_id>`：删除某个物品下的所有场景。
- `/ponderer list`：打开思索物品列表界面。
- `/ponderer convert to_ponderjs all`：将全部场景转换为 PonderJS。
- `/ponderer convert to_ponderjs <id>`：将指定场景转换为 PonderJS。
- `/ponderer convert from_ponderjs all`：将全部 PonderJS 场景导回 Ponderer。
- `/ponderer convert from_ponderjs <id>`：将指定 PonderJS 场景导回 Ponderer。
- `/ponderer export [filename]`：将所有脚本和结构导出为 ZIP 文件到 `config/ponderer/`。
- `/ponderer import <filename>`：从 `config/ponderer/` 中的 ZIP 文件导入脚本和结构。

## 适合哪些人

- 想给自己整合包做引导教程的作者
- 想给服务器玩家制作上手教学的管理员
- 想用更直观方式维护 Ponder 内容的普通玩家

## 核心体验

Ponderer 的目标是：
**把“写教程”变成“在游戏里直接搭教程”**。

从创建、编辑、预览到同步，整个流程尽量保持在 Minecraft 内完成，让思索内容的制作更快、更直观。

## Q&A：为什么不直接使用 PonderJS？

PonderJS 在当前流程下无法做到热重载，内容迭代的反馈链路更长；同时，直接传输 JS 脚本也会引入额外的安全隐患。

Ponderer 采用更安全的数据传输方式，并提供与 PonderJS 的双向转换能力。你可以在两种工作流之间按需切换（其中少量接口为 PonderJS 原生暂不支持）。

---

# Ponderer Mod Introduction

The Create team spun Ponder off into a standalone module in version 6.0. Unfortunately, its steep learning curve has kept this brilliant feature hidden from much of the community — and that's exactly the problem Ponderer aims to solve.

Ponderer is an in-game Ponder authoring tool for players and modpack creators.
You can build, edit, and iterate tutorial scenes directly in Minecraft without leaving the game or writing scripts first.

## What you can do with it

- **Create scenes in-game**: Quickly create Ponder entries from your held item or a specified item (supports different NBT for separate scenes, e.g. signed books with different authors).
- **Edit steps visually**: Click the edit icon at the bottom-left of scenes created by this mod to open the GUI editor for text, camera, block changes, entity spawning, sounds, and more. All coordinate fields support picking directly from the scene (left-click to select block coordinates, right-click to select adjacent block coordinates).
- **Copy/paste and undo/redo**: Copy/paste steps, insert at any position, undo with Ctrl+Z and redo with Ctrl+Y.
- **Blueprint selection and structure saving**: Use the blueprint tool (default: paper) to select areas and save structures for reuse and iterating demo content.
- **Works out of the box**: Comes with a built-in guide scene; hold a **writable_book** to view the demo scene directly.
- **Local hot reload**: Reload changes instantly to see results quickly, without repeated restarts.
- **Multiplayer collaboration sync**: Pull from and push to server-side scene data, making it easy for teams to collaboratively maintain tutorials or for players to quickly access scenes created by others.
- **Format conversion**: Convert to and from PonderJS for cross-workflow compatibility.
- **Scene pack export/import**: Export all scenes and structures as a ZIP file for easy community sharing or backup.
- **JEI integration**: All ID input fields support click or drag-drop from JEI item list (requires JEI installed). Supports block, item, and entity modes with automatic type filtering.
- **Block state properties**: Specify BlockState properties (facing, half, etc.) when placing or replacing blocks, using a dynamic key=value list editor.
- **Extended entity resolution**: Boats, minecarts, armor stands, and other item-based entities can be dragged into entity ID fields via JEI.

## Command Reference (Purpose + Usage)

- `/ponderer reload`: Reload local scene files and refresh the ponder index.
- `/ponderer pull`: Pull server changes in conflict-check mode.
- `/ponderer pull force`: Force server version to overwrite local data.
- `/ponderer pull keep_local`: Pull while preferring to keep local changes.
- `/ponderer push`: Push local scenes to server in conflict-check mode.
- `/ponderer push force`: Force overwrite scenes on the server.
- `/ponderer push <id>`: Push only the specified scene ID.
- `/ponderer push force <id>`: Force-push and overwrite only the specified scene ID.
- `/ponderer download <id>`: Import the specified structure into Ponderer structures.
- `/ponderer new hand`: Create a new scene from the main-hand item.
- `/ponderer new hand use_held_nbt`: Create from main-hand item with current held NBT.
- `/ponderer new hand <nbt>`: Create from main-hand item with explicit NBT.
- `/ponderer new <item>`: Create a new scene for the specified item.
- `/ponderer new <item> <nbt>`: Create a new scene for item + explicit NBT.
- `/ponderer copy <id> <target_item>`: Copy a scene and retarget it to another item.
- `/ponderer delete <id>`: Delete the specified scene.
- `/ponderer delete item <item_id>`: Delete all scenes under one item.
- `/ponderer list`: Open the ponder item list UI.
- `/ponderer convert to_ponderjs all`: Convert all scenes to PonderJS.
- `/ponderer convert to_ponderjs <id>`: Convert one scene to PonderJS.
- `/ponderer convert from_ponderjs all`: Import all scenes back from PonderJS.
- `/ponderer convert from_ponderjs <id>`: Import one scene back from PonderJS.
- `/ponderer export [filename]`: Export all scripts and structures as a ZIP file to `config/ponderer/`.
- `/ponderer import <filename>`: Import scripts and structures from a ZIP file in `config/ponderer/`.

## Who this is for

- Modpack authors who want in-game onboarding tutorials
- Server admins who want player-friendly guidance content
- Players who prefer visual scene editing over script-first workflows

## Core experience

Ponderer is built around one goal:
**Turn "writing tutorials" into "building tutorials directly in-game."**

From creation and editing to preview and sync, the workflow stays inside Minecraft as much as possible, making Ponder content creation faster and more intuitive.

## Q&A: Why not use PonderJS directly?

PonderJS does not provide hot-reload in this workflow, which makes iteration feedback slower. Also, directly transmitting JS scripts introduces additional security risks.

Ponderer uses a safer data-transfer approach and still provides bidirectional conversion with PonderJS, so you can switch between workflows when needed (with a few APIs that are not natively supported by PonderJS).
