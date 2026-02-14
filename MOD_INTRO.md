# Ponderer 模组介绍

Ponderer 是一个面向玩家与整合包作者的「游戏内思索（Ponder）制作工具」。
你不需要离开游戏，也不需要先写脚本，就可以直接在世界里搭建、录制和调整思索教学流程。

## 你可以用它做什么

- **在游戏内新建思索**：从手持物品或指定物品快速生成新的思索条目（支持不同nbt分别思索，如不同署名的成书）。
- **可视化编辑步骤**：点击本模组创建的思索左下角编辑图标，即可通过图形界面编辑文本、镜头、方块变化、实体生成、音效等 Ponder 步骤。
- **蓝图选区与结构保存**：使用蓝图工具选择区域并保存结构，便于复用和迭代演示内容。
- **纸作为默认蓝图载体**：开箱即用，且内置了对应引导思索；手持**书与笔**即可直接查看示例思索。
- **本地热重载**：编辑后可直接重载，快速查看效果，减少反复重启。
- **多人协作同步**：支持从服务器拉取与向服务器推送思索内容，方便团队协作维护教程或快速获取其他玩家创建的思索。
- **格式互转**：支持与 PonderJS 格式互相转换，兼容不同工作流。

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

## 适合哪些人

- 想给自己整合包做引导教程的作者
- 想给服务器玩家制作上手教学的管理员
- 想用更直观方式维护 Ponder 内容的普通玩家

## 核心体验

Ponderer 的目标是：
**把“写教程”变成“在游戏里直接搭教程”**。

从创建、编辑、预览到同步，整个流程尽量保持在 Minecraft 内完成，让思索内容的制作更快、更直观。

## 版本说明（1.0）

1.0 版本聚焦稳定可用的核心工作流：
- 场景新建与编辑
- 结构保存与引用
- 本地重载
- 客户端/服务器同步
- PonderJS 双向转换

如果你想快速上手，建议先在单人世界创建一个简单示例，体验一轮“新建 → 编辑 → 重载 → 同步”的完整流程。

## Q&A：为什么不直接使用 PonderJS？

PonderJS 在当前流程下无法做到热重载，内容迭代的反馈链路更长；同时，直接传输 JS 脚本也会引入额外的安全隐患。

Ponderer 采用更安全的数据传输方式，并提供与 PonderJS 的双向转换能力。你可以在两种工作流之间按需切换（其中少量接口为 PonderJS 原生暂不支持）。

---

# Ponderer Mod Introduction (English)

Ponderer is an in-game Ponder authoring tool for players and modpack creators.
You can build, edit, and iterate tutorial scenes directly in Minecraft without leaving the game or writing scripts first.

## What you can do with it

- **Create scenes in-game**: Quickly create Ponder entries from your held item or a specified item.
- **Edit steps visually**: Use GUI editors for text, camera, block changes, entity spawning, sounds, and more.
- **Blueprint selection and structure saving**: Select areas and save structures for fast reuse.
- **Default blueprint carrier is paper**: Works out of the box with a built-in matching guide scene; hold a **writable_book** to open the demo scene directly.
- **Hot reload locally**: Reload changes quickly for fast iteration without restart loops.
- **Multiplayer collaboration sync**: Pull from and push to server-side scene data.
- **Format conversion**: Convert to and from PonderJS for cross-workflow compatibility.

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

## Who this is for

- Modpack authors who want in-game onboarding tutorials
- Server admins who want player-friendly guidance content
- Players who prefer visual scene editing over script-first workflows

## Core experience

Ponderer is built around one goal:
**turn “writing tutorials” into “building tutorials directly in-game.”**

From creation and editing to preview and sync, the workflow stays inside Minecraft as much as possible.

## Version note (1.0)

Version 1.0 focuses on a stable core pipeline:
- Scene creation and editing
- Structure save and reference
- Local hot reload
- Client/server synchronization
- Bidirectional PonderJS conversion

If you are new, start with a small sample scene and run one full cycle: create → edit → reload → sync.

## Q&A: Why not use PonderJS directly?

In this workflow, PonderJS does not provide hot-reload, so iteration feedback is slower. Also, directly transmitting JS scripts introduces additional security risks.

Ponderer uses a safer data-transfer approach and still provides bidirectional conversion with PonderJS, so you can switch between workflows when needed (with a few APIs that are not natively supported by PonderJS).
