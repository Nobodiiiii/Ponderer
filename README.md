# Ponderer

[中文](#中文) | [English](#english)

## 中文

Ponderer 是一个 Minecraft 模组，提供数据驱动的 Ponder 场景编写、游戏内可视化编辑、AI 辅助生成、热重载以及客户端/服务端同步能力。

### 当前分支与支持平台

- 当前分支：`1.21.1`
- 当前主维护版本：**Minecraft 1.21.1**
- 加载器支持：**NeoForge 21.1.219+**、**Fabric Loader 0.16.14+**
- 旧分支：`1.21.1neoforge-deprecated`（仅 NeoForge，已进入废弃流程）

### 运行要求

|            | NeoForge 1.21.1     | Fabric 1.21.1          |
| ---------- | ------------------- | ---------------------- |
| Minecraft  | 1.21.1              | 1.21.1                 |
| 模组加载器 | NeoForge 21.1.219+  | Fabric Loader 0.16.14+ |
| Fabric API | -                   | 0.116.9+1.21.1         |
| Java       | 21                  | 21                     |

### 思索的游戏内编辑

- **可视化场景编辑**：在游戏内创建、编辑、删除和排序 Ponder 思索，支持 **热重载**。
- **思索标签**：在编辑器的“修改信息 → 思索标签”中，按图标和名称多选已安装模组的分类（如 Create 的动力源），支持搜索标签名称、ID 和说明。选择后保存返回，再保存描述即可热重载；标签对整个思索的所有分场景生效，并将关联物品加入对应标签目录。标签保存在 JSON 的 `tags` 数组中，随场景同步、打包及 Java 模块导出。
- **自定义标签**：标签页提供“新建自定义标签”，支持 ID、可翻译名称与说明、物品图标（JEI 拖放或点击 + 展开背包选择）。ID 只填一个名称，如 `machines`，自动保存为 `ponderer:machines`，不填写前缀或斜杠。新建时自动选中；逐级保存返回并保存描述后，自定义分类出现在 Ponder 标签目录，其他思索也可选用。在所属思索中可编辑、重命名和删除；有其他思索引用时需先移除引用，才能重命名或删除。定义保存在所属思索 JSON 的 `customTags` 对象中；分发复用标签的思索时，请一并包含定义所属的思索。Java 模块导出包含所引用的定义和翻译，并统一将自定义标签的命名空间改为目标模组（如 `examplemod:machines`）；资源包导出保留 `ponderer:`。旧版 `ponderer:custom/…` ID 仍可读取，未重命名时保留原有引用。
- **丰富步骤类型**：覆盖 结构展示/叠加结构、文本、实体与掉落物、镜头旋转/缩放、高亮区域、控制提示、声音、方块修改、区段移动/旋转 等绝大部分常用教程动作。
- **界面化表单编辑**：为不同步骤提供专用配置界面，支持 ID、坐标、NBT、时长、方向、选区等字段校验，减少手写 JSON 的出错成本。
- **按物品标签绑定**：新建思索时关闭“使用手持物品”，在“物品 ID / #标签”填写 `#minecraft:logs` 等物品标签，可同时填写 NBT 条件。JSON 的 `items` 可混用物品 ID 与 `#标签`；命令示例：`/ponderer new tag minecraft:logs {example:1b}`。标签随进服和数据包重载重新解析。
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

### 项目结构（多平台）

```
├── Common/src/main/java/com/nododiiiii/ponderer/   # 跨平台共享代码
│   ├── Ponderer.java              # 模组入口
│   ├── Config.java                # 模组配置
│   ├── ai/                        # AI 场景生成（LLM 调用、结构描述、注册表映射）
│   ├── blueprint/                 # 蓝图选区与结构保存
│   ├── compat/jei/                # JEI 集成（拖放填入、物品浏览）
│   ├── mixin/                     # Mixin（Ponder UI 扩展、NBT 过滤、本地化修复）
│   ├── network/                   # 客户端/服务端网络通信（同步、上传、下载）
│   ├── ponder/                    # 核心逻辑（场景解析、存储、PonderJS 转换、命令）
│   ├── projector/                 # 投影仪方块、播放状态、场景解析与客户端渲染
│   ├── registry/                  # 物品、方块、菜单与方块实体注册
│   └── ui/                        # 所有编辑器界面（场景编辑器、步骤编辑器、AI 配置等）
├── NeoForge/                      # NeoForge 1.21.1 平台适配
├── Fabric/                        # Fabric 平台适配
├── buildSrc/                      # Lotus + 注解转换构建插件
└── Common/src/main/resources/
    ├── assets/ponderer/lang/      # 语言文件（en_us、zh_cn）
    ├── data/ponderer/             # 默认脚本与结构
    └── ponderer.mixins.json       # Mixin 配置
```

### 构建与运行

```bash
# 构建全部模块
./gradlew build

# 仅构建 Fabric / NeoForge
./gradlew :Fabric:build
./gradlew :NeoForge:build

# 开发运行
./gradlew :Fabric:runClient
./gradlew :NeoForge:runClient
```

Windows 请使用 `gradlew.bat`。

### 许可证

MIT

---

## English

Ponderer is a Minecraft mod for data-driven Ponder scene authoring, in-game visual editing, AI-assisted generation, hot reload, and client/server synchronization.

### Branch and platform status

- Active branch: `1.21.1`
- Currently maintained target: **Minecraft 1.21.1**
- Loader support: **NeoForge 21.1.219+** and **Fabric Loader 0.16.14+**
- Legacy branch: `1.21.1neoforge-deprecated` (NeoForge-only, has entered deprecation)

### Requirements

|            | NeoForge 1.21.1     | Fabric 1.21.1          |
| ---------- | ------------------- | ---------------------- |
| Minecraft  | 1.21.1              | 1.21.1                 |
| Mod Loader | NeoForge 21.1.219+  | Fabric Loader 0.16.14+ |
| Fabric API | -                   | 0.116.9+1.21.1         |
| Java       | 21                  | 21                     |

### In-Game Ponder Editing

- **Visual scene editing**: Create, edit, delete, and reorder Ponder scenes in-game, with **hot reload** support.
- **Ponder tags**: Open **Info → Ponder Tags** to select categories from installed mods (such as Create's Kinetic Sources) by icon and name. Search names, IDs, and descriptions; save the selection, then save the description to reload. Tags apply to all scenes in the ponder and add its associated items to the category index. The JSON `tags` array travels with scene sync, resource packs, and Java module exports.
- **Custom tags**: Choose **Create Custom Tag** on the tag screen to set an ID, translated name and description, and item icon (including JEI drops and a + button that opens your inventory). Enter only a name such as `machines`; it is saved as `ponderer:machines`, with no prefix or slash to enter. New tags are selected automatically. Save back through the forms, then save the description to list the category in Ponder and make it available to other ponders. Edit, rename, or delete definitions in their owning ponder; remove references from other ponders before renaming or deleting. Definitions live in the owner's JSON `customTags` object. Include the owning ponder when distributing scenes that reuse its tags. Java module exports include referenced definitions and translations, moving custom tag IDs and references to the destination mod's namespace (e.g. `examplemod:machines`). Resource packs retain `ponderer:`. Legacy `ponderer:custom/…` IDs remain readable and keep their existing references unless renamed.
- **Rich step types**: Covers most common tutorial actions, including structure display/overlay structures, text, entities and dropped items, camera rotation/zoom, highlighted areas, control hints, sounds, block edits, and section movement/rotation.
- **Form-based editing**: Dedicated configuration screens for different steps validate fields such as IDs, coordinates, NBT, durations, directions, and selections, reducing the error cost of hand-written JSON.
- **Item tag bindings**: When creating a ponder, disable “Use held item” and enter an item tag such as `#minecraft:logs` in “Item ID / #Tag”, optionally with an NBT filter. JSON `items` can mix item IDs and `#tags`. Command example: `/ponderer new tag minecraft:logs {example:1b}`. Tags are resolved again on joining and datapack reloads.
- **Structures and blueprint assets**: Save selected areas with the blueprint tool and load custom structure assets from `config/ponderer/structures/`.
- **JSON DSL storage**: Scenes are stored as **data-driven JSON** under `config/ponderer/scripts/`, making them easy to version, adjust manually, and process with toolchains.

### Import/Export & Multiplayer Sync

- **Client/server sync**: Use `/ponderer pull` and `/ponderer push` to sync scenes between client and server, with **conflict handling** and force/keep-local strategies.
- **Remote workspace management**: Browse, pull, delete, inspect history, and roll back remote scenes or structures, making it suitable for server and modpack teams to maintain content together.
- **Scene pack import/export**: Bundle scenes and structures into **resource-pack-format ZIP files** with version information and auto-loading, making distribution to Modrinth, CurseForge, or modpacks easier.
- **Bidirectional PonderJS conversion**: Import and export between Ponderer JSON and **PonderJS** formats, so you can switch between script workflows and in-game editing.
- **Permissions and feature sync**: Servers can synchronize feature availability and use permission management to control uploads, editing, and selected feature toggles.

### AI Scene Generation

- **Multi-provider support**: Configure LLM providers such as **Claude / ChatGPT** and generate draft Ponder scenes from natural-language prompts.
- **Structure-aware generation**: Combines structure descriptions, registry mappings, and user prompts so AI can more easily reference the correct blocks, items, coordinates, and demonstration steps.
- **In-game generation flow**: Fill in your request in-game, generate a draft, and continue refining it in the editor without repeatedly switching between external files and Minecraft.

### Ponder Projectors

- **Two projector blocks**: Provides a **Miniature Projector** and a **Life-Size Projector**, projecting the Ponder scene associated with a source item into the world.
- **Playback and trigger controls**: Supports a rich set of configurable options for different scene needs.
- **Multiplayer server management**: Servers can synchronize and control projector feature toggles. When disabled, projectors can no longer be placed, configured, or played, making this suitable for servers that want a more vanilla-focused experience.

### In-Ponder Experience Improvements

- **Text progress bar**: Shows a text progress panel inside Ponders, easing the slow pacing caused by not being able to drag the progress bar manually.
- **UI Ponders**: Supports showing interfaces, modifying slots, and simulating click flows to demonstrate containers, menus, inventories, or custom UI interactions.
- **Built-in guidance and configuration**: Includes built-in example scenes, keybinding settings, UI scaling, and mod configuration pages for easier onboarding and tuning.

### Project Structure

```
├── Common/src/main/java/com/nododiiiii/ponderer/   # Cross-platform shared code
│   ├── Ponderer.java              # Mod entry point
│   ├── Config.java                # Mod configuration
│   ├── ai/                        # AI scene generation (LLM calls, structure description, registry mapping)
│   ├── blueprint/                 # Blueprint selection & structure saving
│   ├── compat/jei/                # JEI integration (drag-drop, item browsing)
│   ├── mixin/                     # Mixins (Ponder UI extensions, NBT filtering, localization fixes)
│   ├── network/                   # Client/server networking (sync, upload, download)
│   ├── ponder/                    # Core logic (scene parsing, storage, PonderJS conversion, commands)
│   ├── projector/                 # Projector blocks, playback state, scene resolution, and client rendering
│   ├── registry/                  # Item, block, menu, and block entity registration
│   └── ui/                        # All editor screens (scene editor, step editors, AI config, etc.)
├── NeoForge/                      # NeoForge 1.21.1 platform adapter
├── Fabric/                        # Fabric platform adapter
├── buildSrc/                      # Lotus + annotation transform Gradle plugin
└── Common/src/main/resources/
    ├── assets/ponderer/lang/      # Language files (en_us, zh_cn)
    ├── data/ponderer/             # Default scripts & structures
    └── ponderer.mixins.json       # Mixin config
```

### Build & run

```bash
# Build all modules
./gradlew build

# Build Fabric / NeoForge only
./gradlew :Fabric:build
./gradlew :NeoForge:build

# Development runs
./gradlew :Fabric:runClient
./gradlew :NeoForge:runClient
```

Use `gradlew.bat` on Windows.

### License

MIT
