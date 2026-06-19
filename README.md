# Ponderer

[中文](#中文) | [English](#english)

## 中文

Ponderer 是一个 Minecraft 模组，提供数据驱动的 Ponder 场景编写、游戏内可视化编辑、AI 辅助生成、热重载以及客户端/服务端同步能力。

### 当前分支与支持平台

| 分支 | Minecraft | 加载器 | 状态 |
|---|---|---|---|
| `1.20.1` | 1.20.1 | Forge + Fabric | 维护中 |
| `1.21.1` | 1.21.1 | NeoForge + Fabric | 维护中 |
| `1.21.1neoforge-deprecated` | 1.21.1 | 仅 NeoForge | 已废弃 |
| `1.20.1forge-deprecated` | 1.20.1 | 仅 Forge | 已废弃 |

自 1.6.0 起，项目采用多平台架构（Lotus），单一代码库同时构建 Forge/NeoForge 和 Fabric 版本。

### 运行要求

| | Forge 1.20.1 | Fabric 1.20.1 | NeoForge 1.21.1 | Fabric 1.21.1 |
|---|---|---|---|---|
| Minecraft | 1.20.1 | 1.20.1 | 1.21.1 | 1.21.1 |
| 模组加载器 | Forge 47.2.6+ | Fabric Loader 0.16.9+ | NeoForge 21.1.219+ | Fabric Loader 0.16.14+ |
| Ponder | 1.0.91 | 1.0.91 | 1.0.69 | 1.0.69 |
| Flywheel | 1.0.0-215 | 1.0.0-215 | 1.0.4 | 1.0.4 |
| Java | 17 | 17 | 21 | 21 |

### 思索的游戏内编辑

- **可视化场景编辑**：在游戏内创建、编辑、删除和排序 Ponder 步骤，支持 **复制粘贴**、**撤销重做** 与 **坐标选点**（默认 `V`）。
- **丰富步骤类型**：覆盖 **结构展示/叠加结构**、**文本/共享文本**、**实体与掉落物**、**镜头旋转/缩放**、**高亮区域**、**控制提示**、**声音**、**方块修改**、**区段移动/旋转**、**红石提示** 等常用教程动作。
- **界面化表单编辑**：为不同步骤提供专用配置界面，支持 ID、坐标、NBT、时长、方向、选区等字段校验，减少手写 JSON 的出错成本。
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
- **热重载联动**：生成或保存后可刷新本地思索索引，快速在 Ponder 预览中检查结果。

### 思索投影仪

- **两种投影仪方块**：提供 **微缩投影仪** `ponderer:miniature_projector` 与 **实景投影仪** `ponderer:life_size_projector`，将源物品对应的 Ponder 场景投射到世界中。
- **仅渲染播放**：投影会解析物品绑定的场景并播放教程画面，不打开真实界面，也不改动玩家实际交互状态。
- **播放与触发控制**：支持 **手动循环**、**手动单次**、**红石脉冲单次**、**红石持续循环**，并提供播放定位、场景切换和远程思索拉取。
- **投影表现配置**：可调整微缩/实景展示、投影偏移、投影模式（默认/仅场景/仅文本）、文本缩放、幕间间隔、蓝色投影效果、文本防遮与兼容模式。
- **多人服务器管理**：服务端可同步并管控投影仪功能开关，关闭后投影仪不再可放置、配置或播放，已有投影仪按配置逻辑转换/失效。

### 思索内体验优化

- **文本进度条**：在思索播放过程中逐步推进说明文本，适合多阶段提示、节奏控制和长教程拆分。
- **界面思索**：支持展示界面、修改槽位和模拟点击流程，用于演示容器、菜单、物品栏或自定义 UI 的交互逻辑。
- **控制提示增强**：可展示左键、右键、滚轮、按键修饰符和物品/流体材料提示，让操作教学更直观。
- **JEI 辅助填充**：可选集成 **JEI**，通过点击或拖放物品填入 ID 字段，减少手动输入。
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
└── Common/src/main/resources/
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

### Branches & Supported Platforms

| Branch | Minecraft | Loaders | Status |
|---|---|---|---|
| `1.20.1` | 1.20.1 | Forge + Fabric | Maintained |
| `1.21.1` | 1.21.1 | NeoForge + Fabric | Maintained |
| `1.21.1neoforge-deprecated` | 1.21.1 | NeoForge only | Deprecated |
| `1.20.1forge-deprecated` | 1.20.1 | Forge only | Deprecated |

Since 1.6.0, the project uses a multi-platform architecture (Lotus) — a single codebase builds for both Forge/NeoForge and Fabric.

### Requirements

| | Forge 1.20.1 | Fabric 1.20.1 | NeoForge 1.21.1 | Fabric 1.21.1 |
|---|---|---|---|---|
| Minecraft | 1.20.1 | 1.20.1 | 1.21.1 | 1.21.1 |
| Mod Loader | Forge 47.2.6+ | Fabric Loader 0.16.9+ | NeoForge 21.1.219+ | Fabric Loader 0.16.14+ |
| Ponder | 1.0.91 | 1.0.91 | 1.0.69 | 1.0.69 |
| Flywheel | 1.0.0-215 | 1.0.0-215 | 1.0.4 | 1.0.4 |
| Java | 17 | 17 | 21 | 21 |

### In-Game Ponder Editing

- **Visual scene editing**: Create, edit, delete, and reorder Ponder steps in game, with **copy-paste**, **undo-redo**, and **coordinate picking** (`V` by default).
- **Rich step coverage**: Supports **structures/extra structures**, **text/shared text**, **entities and item entities**, **camera rotation/zoom**, **section highlights**, **control hints**, **sounds**, **block edits**, **section movement/rotation**, and **redstone indicators**.
- **Form-based configuration**: Dedicated screens validate IDs, coordinates, NBT, durations, directions, and selections so fewer scenes need hand-written JSON.
- **Structures and blueprints**: Save selected areas with the blueprint tool and load custom structure assets from `config/ponderer/structures/`.
- **JSON DSL storage**: Scenes are stored as **data-driven JSON** under `config/ponderer/scripts/`, making them friendly to version control and external tooling.

### Import/Export & Multiplayer Sync

- **Client/server sync**: Use `/ponderer pull` and `/ponderer push` to sync scenes between client and server, with **conflict handling** and force/keep-local strategies.
- **Remote workspace management**: Browse, pull, delete, inspect history, and roll back remote scenes or structures for server and modpack collaboration.
- **Scene pack import/export**: Bundle scenes and structures as **resource-pack-format ZIPs** with version metadata and auto-loading for easy distribution.
- **Bidirectional PonderJS conversion**: Convert between Ponderer JSON and **PonderJS** formats when moving between script-first and in-game workflows.
- **Permissions and feature sync**: Servers can sync feature availability and use permission management for uploads, edits, and selected feature toggles.

### AI Scene Generation

- **Multi-provider support**: Configure LLM providers such as **Claude / ChatGPT** and generate draft Ponder scenes from natural-language prompts.
- **Structure-aware generation**: Uses structure descriptions, registry mapping, and user instructions to help the model reference the right blocks, items, coordinates, and steps.
- **In-game workflow**: Describe what you want in game, generate a draft, then continue polishing it in the scene editor.
- **Hot-reload friendly**: Generated or saved scenes can refresh the local Ponder index for quick preview and iteration.

### Ponder Projectors

- **Two projector blocks**: **Miniature Projector** `ponderer:miniature_projector` and **Life-Size Projector** `ponderer:life_size_projector` project the Ponder scenes for a source item into the world.
- **Render-only playback**: Projectors resolve item-bound scenes and play tutorial visuals without opening real UI screens or mutating the player's actual interaction state.
- **Playback and triggers**: Supports **manual loop**, **manual once**, **redstone pulse**, and **redstone hold**, plus seek controls, scene switching, and remote Ponder pull.
- **Projection configuration**: Tune miniature/life-size display, projection offset, projection mode (default/scene only/text only), text scale, intervals, blue projection effects, text anti-occlusion, and compatibility mode.
- **Server management**: Servers can sync and control projector availability; when disabled, projector blocks can no longer be placed, configured, or played, and existing projectors follow the configured conversion/disable behavior.

### In-Ponder Experience Improvements

- **Text progress bars**: Advance explanations during Ponder playback for multi-stage hints, pacing, and long tutorial breakdowns.
- **Interface Pondering**: Show interfaces, modify slots, and simulate click flows for containers, menus, inventories, or custom UI demonstrations.
- **Enhanced control hints**: Display left click, right click, scroll, modifier keys, and item/fluid material hints for clearer operation tutorials.
- **JEI-assisted fields**: Optional **JEI** integration can fill ID fields by clicking or drag-dropping items from JEI.
- **Built-in guidance and config**: Includes a built-in guide scene, keybinding settings, UI scaling, and mod configuration screens for easier onboarding and tuning.

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
└── Common/src/main/resources/
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
