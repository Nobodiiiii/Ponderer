# Ponderer

[中文](#中文) | [English](#english)

## 中文

Ponderer 是一个 Minecraft 模组，提供数据驱动的 Ponder 场景编写、游戏内可视化编辑、AI 辅助生成、热重载以及客户端/服务端同步能力。

### 当前分支与支持平台

- 当前分支：`1.20.1`
- 当前主维护版本：**Minecraft 1.20.1**
- 加载器支持：**Forge 47.2.6+**、**Fabric Loader 0.16.9+**
- 旧分支：`1.20.1forge`（仅 Forge，已进入废弃流程）

### 运行要求

| | Forge 1.20.1 | Fabric 1.20.1 |
|---|---|---|
| Minecraft | 1.20.1 | 1.20.1 |
| 模组加载器 | Forge 47.2.6+ | Fabric Loader 0.16.9+ |
| Fabric API | - | 0.92.3+1.20.1 |
| Java | 17 | 17 |

### 核心功能

- JSON DSL 场景定义：在 `config/ponderer/scripts/` 中使用 JSON 编写 Ponder 场景
- 游戏内场景编辑器：新增/编辑/删除/排序步骤，支持复制粘贴、撤销重做、坐标选点（默认 `V`）
- AI 场景生成（Beta）：支持 Claude / ChatGPT 等 LLM 提供商
- 蓝图与结构：选区保存结构，从 `config/ponderer/structures/` 加载
- 多人协作同步：客户端与服务端拉取/推送场景（含冲突处理）
- PonderJS 双向转换：与 PonderJS 互相导入/导出
- 场景包导入导出：资源包格式 ZIP（带版本控制与自动加载）
- JEI 集成：ID 输入框支持 JEI 点击或拖放填充（可选依赖）

### 项目结构（多平台）

```
Common/   # 平台无关代码（核心逻辑、UI、网络抽象、mixin、资源）
Forge/    # Forge 入口与平台实现（SPI 服务实现）
Fabric/   # Fabric 入口与平台实现（SPI 服务实现）
buildSrc/ # Lotus + 注解转换构建插件
```

### 常用命令

- `/ponderer reload`：重载本地场景脚本
- `/ponderer pull [force|keep_local]`：从服务端拉取场景
- `/ponderer push [force] [<id>]`：推送场景到服务端
- `/ponderer convert to_ponderjs|from_ponderjs all|<id>`：PonderJS 转换
- `/ponderer export` / `/ponderer import`：场景包导入导出

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

Ponderer is a Minecraft mod for data-driven Ponder authoring, in-game visual editing, AI-assisted generation, hot reload, and client/server sync.

### Branch and platform status

- Active branch: `1.20.1`
- Active target: **Minecraft 1.20.1**
- Loaders: **Forge 47.2.6+** and **Fabric Loader 0.16.9+**
- Legacy branch: `1.20.1forge` (Forge-only, now being deprecated)

### Requirements

| | Forge 1.20.1 | Fabric 1.20.1 |
|---|---|---|
| Minecraft | 1.20.1 | 1.20.1 |
| Mod Loader | Forge 47.2.6+ | Fabric Loader 0.16.9+ |
| Fabric API | - | 0.92.3+1.20.1 |
| Java | 17 | 17 |

### Highlights

- JSON DSL scene definition under `config/ponderer/scripts/`
- In-game scene editor (add/edit/delete/reorder steps, copy/paste, undo/redo, coordinate pick)
- AI scene generation (Beta) with multiple LLM providers
- Blueprint structure capture and custom structure loading
- Multiplayer pull/push sync with conflict handling
- Bidirectional PonderJS conversion
- Resource-pack-format scene pack import/export
- Optional JEI integration for ID field filling

### Multi-platform layout

```
Common/   # shared logic, UI, networking abstraction, mixins, resources
Forge/    # Forge entrypoint and platform services
Fabric/   # Fabric entrypoint and platform services
buildSrc/ # Lotus + annotation transform plugin
```

### Build & run

```bash
./gradlew build
./gradlew :Fabric:build
./gradlew :Forge:build
./gradlew :Fabric:runClient
./gradlew :Forge:runClient
```

Use `gradlew.bat` on Windows.

### License

MIT
