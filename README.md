# Ponderer

[中文](#中文) | [English](#english)

## 中文

Ponderer 是一个 Minecraft 模组，提供数据驱动的 Ponder 场景编写、游戏内可视化编辑、AI 辅助生成、热重载以及客户端/服务端同步能力。

支持版本：
- **Forge 1.20.1**（当前分支）
- **NeoForge 1.21.1**

### 运行要求

| | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Minecraft | 1.20.1 | 1.21.1 |
| 模组加载器 | Forge 47.2.6+ | NeoForge 21.1.219+ |
| Ponder | 1.0.91 | 1.0.60 |
| Flywheel | 1.0.0-215 | 1.0.4 |
| Java | 17 | 21 |

### 核心功能
- **JSON DSL 场景定义**：在 `config/ponderer/scripts/` 中使用 JSON 编写 Ponder 场景
- **游戏内场景编辑器**：通过图形界面新增/编辑/删除/排序步骤，支持复制粘贴、撤销重做、坐标选点、丰富的步骤类型（默认按 V 键打开）
- **AI 场景生成（Beta）**：通过 LLM（Claude / ChatGPT 等）根据结构和自然语言描述自动生成场景，支持多种 API 提供商
- **蓝图与结构**：使用蓝图工具选区保存结构，从 `config/ponderer/structures/` 加载自定义结构
- **多人协作同步**：客户端与服务端拉取/推送场景（含冲突处理）
- **PonderJS 双向转换**：支持与 PonderJS 格式互相导入/导出
- **场景包导入导出**：将场景和结构打包为资源包格式的 ZIP 文件，支持版本控制与自动加载，方便在社区分享
- **JEI 集成**：所有 ID 输入框支持从 JEI 点击或拖放自动填入（可选依赖）
- **开箱即用**：内置引导思索，手持"书与笔"可直接查看示例

### 项目结构
```
src/main/java/com/nododiiiii/ponderer/
├── Ponderer.java              # 模组入口
├── Config.java                # 模组配置
├── ai/                        # AI 场景生成（LLM 调用、结构描述、注册表映射）
├── blueprint/                 # 蓝图选区与结构保存
├── compat/jei/                # JEI 集成（拖放填入、物品浏览）
├── mixin/                     # Mixin（Ponder UI 扩展、NBT 过滤、本地化修复）
├── network/                   # 客户端/服务端网络通信（同步、上传、下载）
├── ponder/                    # 核心逻辑（场景解析、存储、PonderJS 转换、命令）
├── registry/                  # 物品注册
└── ui/                        # 所有编辑器界面（场景编辑器、步骤编辑器、AI 配置等）

src/main/resources/
├── assets/ponderer/lang/      # 语言文件（en_us、zh_cn）
├── data/ponderer/             # 默认脚本与结构
└── ponderer.mixins.json       # Mixin 配置
```

### 命令
- `/ponderer reload`：重载本地场景脚本并刷新思索索引
- `/ponderer pull [force|keep_local]`：从服务端拉取场景
- `/ponderer push [force] [<id>]`：推送场景到服务端
- `/ponderer download <id>`：导入指定结构
- `/ponderer new hand [use_held_nbt|<nbt>]`：以主手物品创建新场景
- `/ponderer new <item> [<nbt>]`：以指定物品创建新场景
- `/ponderer copy <id> <target_item>`：复制场景并改绑到目标物品
- `/ponderer delete <id>` / `delete item <item_id>`：删除场景
- `/ponderer list`：打开思索物品列表界面
- `/ponderer convert to_ponderjs|from_ponderjs all|<id>`：PonderJS 格式转换
- `/ponderer export`：打开导出界面，将场景打包为资源包
- `/ponderer import`：打开导入界面，从资源包加载场景

### 构建
```bash
./gradlew build
./gradlew runClient
```

### Q&A
**Q：为什么不直接使用 PonderJS？**

**A：** PonderJS 本身不支持热重载，编辑反馈链路较长；另外，直接传输 JS 脚本也会带来额外安全风险。Ponderer 采用更安全的数据传输方式，同时提供了与 PonderJS 的双向转换能力，方便你在两种工作流之间切换（其中少量接口是 PonderJS 原生暂不支持的）。

### 许可证
MIT

---

## English

Ponderer is a Minecraft mod that provides data-driven Ponder scene authoring, in-game visual editing, AI-assisted generation, hot-reload, and client/server sync.

Supported versions:
- **Forge 1.20.1** (current branch)
- **NeoForge 1.21.1**

### Requirements

| | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Minecraft | 1.20.1 | 1.21.1 |
| Mod Loader | Forge 47.2.6+ | NeoForge 21.1.219+ |
| Ponder | 1.0.91 | 1.0.60 |
| Flywheel | 1.0.0-215 | 1.0.4 |
| Java | 17 | 21 |

### Key Features
- **JSON DSL scene definition**: Author Ponder scenes in JSON under `config/ponderer/scripts/`
- **In-game scene editor**: GUI for adding/editing/deleting/reordering steps, with copy-paste, undo-redo, coordinate picking, and rich step types (press V to open by default)
- **AI scene generation (Beta)**: Generate scenes from structures and natural language via LLMs (Claude / ChatGPT, etc.), with multi-provider support
- **Blueprints & structures**: Select areas with the blueprint tool, load custom structures from `config/ponderer/structures/`
- **Multiplayer sync**: Client-server pull/push with conflict handling
- **Bidirectional PonderJS conversion**: Import/export between Ponderer and PonderJS formats
- **Scene pack export/import**: Bundle scenes and structures as resource-pack-format ZIPs with versioning and auto-loading, for easy sharing
- **JEI integration**: Click or drag-drop from JEI to fill in ID fields (optional dependency)
- **Works out of the box**: Built-in guide scene; hold a `writable_book` to view the demo

### Project Structure
```
src/main/java/com/nododiiiii/ponderer/
├── Ponderer.java              # Mod entry point
├── Config.java                # Mod configuration
├── ai/                        # AI scene generation (LLM calls, structure description, registry mapping)
├── blueprint/                 # Blueprint selection & structure saving
├── compat/jei/                # JEI integration (drag-drop, item browsing)
├── mixin/                     # Mixins (Ponder UI extensions, NBT filtering, localization fixes)
├── network/                   # Client/server networking (sync, upload, download)
├── ponder/                    # Core logic (scene parsing, storage, PonderJS conversion, commands)
├── registry/                  # Item registration
└── ui/                        # All editor screens (scene editor, step editors, AI config, etc.)

src/main/resources/
├── assets/ponderer/lang/      # Language files (en_us, zh_cn)
├── data/ponderer/             # Default scripts & structures
└── ponderer.mixins.json       # Mixin config
```

### Commands
- `/ponderer reload`: Reload local scene files and refresh the ponder index
- `/ponderer pull [force|keep_local]`: Pull scenes from server
- `/ponderer push [force] [<id>]`: Push scenes to server
- `/ponderer download <id>`: Import a specific structure
- `/ponderer new hand [use_held_nbt|<nbt>]`: Create a new scene from main-hand item
- `/ponderer new <item> [<nbt>]`: Create a new scene for the specified item
- `/ponderer copy <id> <target_item>`: Copy a scene and retarget it
- `/ponderer delete <id>` / `delete item <item_id>`: Delete scenes
- `/ponderer list`: Open the ponder item list UI
- `/ponderer convert to_ponderjs|from_ponderjs all|<id>`: PonderJS conversion
- `/ponderer export`: Open the export screen to bundle scenes as a resource pack
- `/ponderer import`: Open the import screen to load scenes from a resource pack

### Build
```bash
./gradlew build
./gradlew runClient
```

### Q&A
**Q: Why not use PonderJS directly?**

**A:** PonderJS does not provide hot-reload in this workflow, which makes iteration slower. Directly transmitting JS scripts also introduces additional security risks. Ponderer uses a safer data transfer approach and still provides bidirectional conversion with PonderJS, so you can switch workflows when needed (with a few APIs that are not natively supported by PonderJS).

### License
MIT
