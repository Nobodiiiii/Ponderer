<p align="center">
  <img src="Common/src/main/resources/logo.png" alt="Ponderer logo">
</p>

# Ponderer

[中文](#中文) | [English](#english)

## 中文

Ponderer 是一个 Minecraft 模组，提供数据驱动的 Ponder 场景编写、游戏内可视化编辑、AI 辅助生成、热重载以及客户端/服务端同步能力。

### 画廊

<div style="max-width: 100%; overflow-x: auto;">
  <div style="width: 100%; min-width: 960px; text-align: center;">
    <div style="margin-bottom: 12px;">
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/3-built-in-steps.gif" alt="丰富的内置步骤" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">丰富的内置步骤</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/5-blueprint-structures.gif" alt="使用蓝图保存结构并在思索中使用" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">使用蓝图保存结构并在思索中使用</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/6-ai-scene-generation.gif" alt="AI驱动的思索自动生成" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">AI驱动的思索自动生成</div>
      </div>
    </div>
    <div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/1-miniature-projector.gif" alt="微缩思索投影仪" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">微缩思索投影仪</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/2-life-size-projector.gif" alt="实景思索投影仪" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">实景思索投影仪</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/4-sync-and-remote-management.gif" alt="服务器多端同步与远程管理" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">服务器多端同步与远程管理</div>
      </div>
    </div>
  </div>
</div>

### 当前分支与支持平台

- 当前分支：`1.20.1`
- 当前主维护版本：**Minecraft 1.20.1**
- 加载器支持：**Forge 47.2.6+**、**Fabric Loader 0.16.9+**
- 旧分支：`1.20.1forge`（仅 Forge，已进入废弃流程）

### 运行要求

|            | Forge 1.20.1  | Fabric 1.20.1         |
| ---------- | ------------- | --------------------- |
| Minecraft  | 1.20.1        | 1.20.1                |
| 模组加载器 | Forge 47.2.6+ | Fabric Loader 0.16.9+ |
| Fabric API | -             | 0.92.3+1.20.1         |
| Java       | 17            | 17                    |

### 思索的游戏内编辑

- **可视化场景编辑**：在游戏内创建、编辑、删除和排序 Ponder 思索，支持 **热重载**。
- **丰富步骤类型**：覆盖 结构展示/叠加结构、文本、实体与掉落物、镜头旋转/缩放、高亮区域、控制提示、声音、方块修改、区段移动/旋转 等绝大部分常用教程动作。
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
├── Forge/                         # Forge 1.20.1 平台适配
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

# 仅构建 Fabric / Forge
./gradlew :Fabric:build
./gradlew :Forge:build

# 开发运行
./gradlew :Fabric:runClient
./gradlew :Forge:runClient
```

Windows 请使用 `gradlew.bat`。

### 许可证

MIT

---

## English

Ponderer is a Minecraft mod for data-driven Ponder scene authoring, in-game visual editing, AI-assisted generation, hot reload, and client/server synchronization.

### Gallery

<div style="max-width: 100%; overflow-x: auto;">
  <div style="width: 100%; min-width: 960px; text-align: center;">
    <div style="margin-bottom: 12px;">
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/3-built-in-steps.gif" alt="Rich built-in steps" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">Rich built-in steps</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/5-blueprint-structures.gif" alt="Save structures with blueprints and reuse them in Ponder" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">Save structures with blueprints and reuse them in Ponder</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/6-ai-scene-generation.gif" alt="AI-driven automatic Ponder generation" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">AI-driven automatic Ponder generation</div>
      </div>
    </div>
    <div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/1-miniature-projector.gif" alt="Miniature Ponder Projector" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">Miniature Ponder Projector</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/2-life-size-projector.gif" alt="Life-Size Ponder Projector" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">Life-Size Ponder Projector</div>
      </div>
      <div style="display: inline-block; width: 32%; vertical-align: top;">
        <img src="docs/gallery/4-sync-and-remote-management.gif" alt="Multi-client sync and remote management" style="display: block; width: 100%; height: auto;">
        <div style="margin-top: 8px; font-size: 12px; color: #808080;">Multi-client sync and remote management</div>
      </div>
    </div>
  </div>
</div>

### Branch and platform status

- Active branch: `1.20.1`
- Currently maintained target: **Minecraft 1.20.1**
- Loader support: **Forge 47.2.6+** and **Fabric Loader 0.16.9+**
- Legacy branch: `1.20.1forge` (Forge-only, has entered deprecation)

### Requirements

|            | Forge 1.20.1  | Fabric 1.20.1         |
| ---------- | ------------- | --------------------- |
| Minecraft  | 1.20.1        | 1.20.1                |
| Mod Loader | Forge 47.2.6+ | Fabric Loader 0.16.9+ |
| Fabric API | -             | 0.92.3+1.20.1         |
| Java       | 17            | 17                    |

### In-Game Ponder Editing

- **Visual scene editing**: Create, edit, delete, and reorder Ponder scenes in-game, with **hot reload** support.
- **Rich step types**: Covers most common tutorial actions, including structure display/overlay structures, text, entities and dropped items, camera rotation/zoom, highlighted areas, control hints, sounds, block edits, and section movement/rotation.
- **Form-based editing**: Dedicated configuration screens for different steps validate fields such as IDs, coordinates, NBT, durations, directions, and selections, reducing the error cost of hand-written JSON.
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
├── Forge/                         # Forge 1.20.1 platform adapter
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

# Build Fabric / Forge only
./gradlew :Fabric:build
./gradlew :Forge:build

# Development runs
./gradlew :Fabric:runClient
./gradlew :Forge:runClient
```

Use `gradlew.bat` on Windows.

### License

MIT
