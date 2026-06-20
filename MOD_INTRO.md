# Ponderer 模组介绍

Create 制作组在 6.0 版本将 Ponder 拆分为独立模组，然而较高的使用门槛使这一优秀功能未能被更多玩家所体验。Ponderer 正是为此而生。

Ponderer 是一个面向玩家与整合包作者的「游戏内思索（Ponder）制作工具」。
你不需要离开游戏，也不需要先写脚本，就可以直接在世界里搭建、录制和调整思索教学流程。

## 你可以用它做什么

### 思索的游戏内编辑

- **可视化场景编辑**：在游戏内创建、编辑、删除和排序 Ponder 思索，支持 **热重载** ，**在游戏中按 V 打开模组菜单**。
- **丰富步骤类型**：覆盖 结构展示/叠加结构、文本、实体与掉落物、镜头旋转/缩放、高亮区域、控制提示、声音、方块修改、区段移动/旋转 等绝大部分常用教程动作。
- **结构与蓝图素材**：使用蓝图工具保存选区结构，并从 `config/ponderer/structures/` 加载自定义结构素材。
- **JSON DSL 存储**：场景以 **数据驱动 JSON** 存放在 `config/ponderer/scripts/`，便于版本管理、手动调整和工具链处理。

### 导入导出与多人游戏同步

- **客户端/服务端同步**：通过 `/ponderer pull` 与 `/ponderer push` 在客户端和服务端之间同步场景，支持 **冲突处理** 与强制/保留本地策略。
- **远程工作区管理**：提供远程场景/结构浏览、拉取、删除、历史记录与回滚能力，适合服务器或整合包团队协作维护。
- **场景包导入导出**：将场景与结构打包为 **资源包格式 ZIP**，支持版本信息与自动加载，方便分发到 Modrinth / CurseForge 或整合包。
- **PonderJS 双向转换**：支持 Ponderer JSON 与 **PonderJS** 格式互相导入/导出，方便在脚本工作流和游戏内编辑之间切换。
- **权限与功能同步**：服务端可同步功能可用性，并配合权限管理控制上传、编辑和部分功能开关。

### AI 场景生成

- **多提供商支持**：可配置 **Claude / ChatGPT** 等 LLM 提供商，用自然语言生成 Ponder 场景草稿。
- **结构感知生成**：结合结构描述、注册表映射和用户提示词，让 AI 更容易引用正确方块、物品、坐标与演示步骤。
- **游戏内生成流程**：在游戏内填写需求、生成草稿并继续进入编辑器微调，避免在外部文件和游戏之间反复切换。

### 思索投影仪

- **两种投影仪方块**：提供 **微缩投影仪** 与 **实景投影仪** ，将源物品对应的 Ponder 场景投射到世界中。
- **播放与触发控制**：支持非常丰富的可配置项，满足各场景需求。
- **多人服务器管理**：服务端可同步并管控投影仪功能开关，关闭后投影仪不再可放置、配置或播放，适合纯原版服务器。

### 思索内体验优化

- **文本进度条**：在思索显示文本进度板，解决思索不能手动拖动进度条的拖沓问题。
- **界面思索**：支持展示界面、修改槽位和模拟点击流程，用于演示容器、菜单、物品栏或自定义 UI 的交互逻辑。
- **内置引导与配置**：包含内置示例场景、快捷键设置、界面缩放与模组配置页，便于上手和按需调整体验。

## 适合哪些人

- 想给自己整合包做引导教程的作者
- 想给服务器玩家制作上手教学的管理员
- 想用更直观方式维护、体验 Ponder 内容的普通玩家

## 核心体验

Ponderer 的目标是：
**把"写教程"变成"在游戏里直接搭教程"**。

从创建、编辑、预览到同步，整个流程尽量保持在 Minecraft 内完成，让思索内容的制作更快、更直观。

## Q&A

### 1. 版本支持计划？

| 游戏版本         | Forge                     | NeoForge                  | Fabric              |
| ---------------- | ------------------------- | ------------------------- | --------------------------- |
| **26.1**   | **无支持计划**      | **即将支持**        | **即将支持**          |
| **1.21.1** | **无支持计划**      | **维护中**          | **维护中**        |
| **1.20.1** | **维护中**          | **无支持计划**      | **维护中** |
| **1.12.2** | **计划支持**          | **无计划支持**      | **无计划支持** |
| **1.7.10** | **计划支持**          | **无计划支持**      | **无计划支持** |

### 2. 为什么不直接使用 PonderJS？

本模组提供思索多端同步能力，直接传输 JS 脚本会引入额外的安全隐患。Ponderer 采用更安全的数据传输方式，并提供与 PonderJS 的双向转换能力。你可以在两种工作流之间按需切换。同时， Ponderer 提供了大量PonderJS 原生暂不支持的接口。

---

# Ponderer Mod Introduction

The Create team spun Ponder off into a standalone module in version 6.0. Unfortunately, its steep learning curve has kept this brilliant feature hidden from much of the community — and that's exactly the problem Ponderer aims to solve.

Ponderer is an in-game Ponder authoring tool for players and modpack creators.
You can build, edit, and iterate tutorial scenes directly in Minecraft without leaving the game or writing scripts first.

Supports both **Forge 1.20.1** and **NeoForge 1.21.1**.

## What you can do with it

- **Create scenes in-game**: Quickly create Ponder entries from your held item or a specified item (supports different NBT for separate scenes, e.g. signed books with different authors).
- **Edit steps visually**: GUI editor for text, camera, block changes, entity spawning, control hints, sounds, and more, with copy-paste, undo-redo, and coordinate picking.
- **In-game scene editor**: GUI for adding/editing/deleting/reordering steps, with copy-paste, undo-redo, coordinate picking, and rich step types (press V to open by default)
- **AI scene generation (Beta)**: Automatically generate complete scenes from structures and natural language descriptions via LLMs (Claude / ChatGPT, etc.), with multi-provider support.
- **Blueprint selection and structure saving**: Use the blueprint tool to select areas and save structures for reuse and iterating demo content.
- **Multiplayer collaboration sync**: Pull from and push to server-side scene data, making it easy for teams to collaboratively maintain tutorials.
- **Format conversion & scene packs**: Convert to/from PonderJS; bundle scenes and structures as resource-pack-format ZIPs with versioning and auto-loading for sharing.
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
- `/ponderer export`: Open the export screen to bundle scenes as a resource pack.
- `/ponderer import`: Open the import screen to load scenes from a resource pack.

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
