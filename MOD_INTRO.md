# Ponderer 模组介绍

Create 制作组在 6.0 版本将 Ponder 拆分为独立模组，然而较高的使用门槛使这一优秀功能未能被更多玩家所体验。Ponderer 正是为此而生。

Ponderer 是一个面向玩家与整合包作者的「游戏内思索（Ponder）制作工具」。
你不需要离开游戏，也不需要先写脚本，就可以直接在世界里搭建、录制和调整思索教学流程。

支持 **Forge 1.20.1** 和 **NeoForge 1.21.1** 双版本。

## 你可以用它做什么

- **在游戏内新建思索**：从手持物品或指定物品快速生成新的思索条目（支持不同 NBT 分别思索，如不同署名的成书）。
- **可视化编辑步骤**：通过图形界面编辑文本、镜头、方块变化、实体生成、操作提示、音效等 Ponder 步骤，支持复制粘贴、撤销重做、坐标选点。
- **AI 场景生成（Beta）**：通过 LLM（Claude / ChatGPT 等）根据结构和自然语言描述自动生成完整场景，支持多种 API 提供商。
- **蓝图选区与结构保存**：使用蓝图工具选择区域并保存结构，便于复用和迭代演示内容。
- **多人协作同步**：从服务器拉取与向服务器推送思索内容，方便团队协作或快速获取其他玩家创建的思索。
- **格式互转与场景包**：支持与 PonderJS 格式互相转换；支持将场景和结构打包为 ZIP 文件分享。
- **模组配置面板**：通过图形界面管理所有设置，无需手动输入指令。
- **JEI 集成**：所有 ID 输入框支持从 JEI 点击或拖放自动填入（可选依赖）。
- **开箱即用**：内置引导思索；手持**书与笔**即可查看示例。

## 指令总览（用途 + 用法）

- `/ponderer reload`：重载本地场景脚本并刷新思索索引。
- `/ponderer pull [force|keep_local]`：从服务端拉取场景。
- `/ponderer push [force] [<id>]`：推送场景到服务端。
- `/ponderer download <id>`：导入指定结构。
- `/ponderer new hand [use_held_nbt|<nbt>]`：以主手物品创建新场景。
- `/ponderer new <item> [<nbt>]`：以指定物品创建新场景。
- `/ponderer copy <id> <target_item>`：复制场景并改绑到目标物品。
- `/ponderer delete <id>` / `delete item <item_id>`：删除场景。
- `/ponderer list`：打开思索物品列表界面。
- `/ponderer convert to_ponderjs|from_ponderjs all|<id>`：PonderJS 格式转换。
- `/ponderer export [filename]` / `import <filename>`：场景包导入导出。

## 适合哪些人

- 想给自己整合包做引导教程的作者
- 想给服务器玩家制作上手教学的管理员
- 想用更直观方式维护 Ponder 内容的普通玩家

## 核心体验

Ponderer 的目标是：
**把"写教程"变成"在游戏里直接搭教程"**。

从创建、编辑、预览到同步，整个流程尽量保持在 Minecraft 内完成，让思索内容的制作更快、更直观。

## Q&A：为什么不直接使用 PonderJS？

PonderJS 在当前流程下无法做到热重载，内容迭代的反馈链路更长；同时，直接传输 JS 脚本也会引入额外的安全隐患。

Ponderer 采用更安全的数据传输方式，并提供与 PonderJS 的双向转换能力。你可以在两种工作流之间按需切换（其中少量接口为 PonderJS 原生暂不支持）。

---

# Ponderer Mod Introduction

The Create team spun Ponder off into a standalone module in version 6.0. Unfortunately, its steep learning curve has kept this brilliant feature hidden from much of the community — and that's exactly the problem Ponderer aims to solve.

Ponderer is an in-game Ponder authoring tool for players and modpack creators.
You can build, edit, and iterate tutorial scenes directly in Minecraft without leaving the game or writing scripts first.

Supports both **Forge 1.20.1** and **NeoForge 1.21.1**.

## What you can do with it

- **Create scenes in-game**: Quickly create Ponder entries from your held item or a specified item (supports different NBT for separate scenes, e.g. signed books with different authors).
- **Edit steps visually**: GUI editor for text, camera, block changes, entity spawning, control hints, sounds, and more, with copy-paste, undo-redo, and coordinate picking.
- **AI scene generation (Beta)**: Automatically generate complete scenes from structures and natural language descriptions via LLMs (Claude / ChatGPT, etc.), with multi-provider support.
- **Blueprint selection and structure saving**: Use the blueprint tool to select areas and save structures for reuse and iterating demo content.
- **Multiplayer collaboration sync**: Pull from and push to server-side scene data, making it easy for teams to collaboratively maintain tutorials.
- **Format conversion & scene packs**: Convert to/from PonderJS; bundle scenes and structures as ZIP for sharing.
- **Mod config panel**: Manage all settings via a GUI — no commands needed.
- **JEI integration**: Click or drag-drop from JEI to fill in ID fields (optional dependency).
- **Works out of the box**: Built-in guide scene; hold a **writable_book** to view the demo.

## Command Reference (Purpose + Usage)

- `/ponderer reload`: Reload local scene files and refresh the ponder index.
- `/ponderer pull [force|keep_local]`: Pull scenes from server.
- `/ponderer push [force] [<id>]`: Push scenes to server.
- `/ponderer download <id>`: Import a specific structure.
- `/ponderer new hand [use_held_nbt|<nbt>]`: Create a new scene from main-hand item.
- `/ponderer new <item> [<nbt>]`: Create a new scene for the specified item.
- `/ponderer copy <id> <target_item>`: Copy a scene and retarget it.
- `/ponderer delete <id>` / `delete item <item_id>`: Delete scenes.
- `/ponderer list`: Open the ponder item list UI.
- `/ponderer convert to_ponderjs|from_ponderjs all|<id>`: PonderJS conversion.
- `/ponderer export [filename]` / `import <filename>`: Scene pack import/export.

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
