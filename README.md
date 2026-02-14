# Ponderer

## 中文

Ponderer 是一个 NeoForge 1.21.1 模组，提供数据驱动的 Ponder 场景编写、游戏内编辑、热重载以及客户端/服务端同步能力。

### 运行要求
- Minecraft 1.21.1
- NeoForge 21.1.219+
- Ponder 1.0.60
- Flywheel 1.0.4
- Java 21

### 核心功能
- 在 `config/ponderer/scripts/` 中使用 JSON DSL 定义场景
- 游戏内场景编辑器（新增/编辑/删除/排序步骤）
- 从 `config/ponderer/structures/` 加载自定义结构
- 默认蓝图载体物品为“纸”，并内置对应引导思索；手持“书与笔”可直接查看示例思索
- 通过 `nbtFilter` 进行 NBT 场景过滤
- PonderJS 双向转换（导入/导出）
- 客户端与服务端拉取/推送（含冲突处理）
- 物品列表界面展示全部已注册思索物品

### 命令
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

### 构建
```bash
./gradlew build
./gradlew runClient
```

### 许可证
MIT

---

## English

Ponderer is a NeoForge 1.21.1 mod that provides data-driven Ponder scene authoring, in-game editing, hot-reload, and client/server sync.

### Requirements
- Minecraft 1.21.1
- NeoForge 21.1.219+
- Ponder 1.0.60
- Flywheel 1.0.4
- Java 21

### Key Features
- JSON DSL scene definition in `config/ponderer/scripts/`
- In-game scene editor for add/edit/delete/reorder steps
- Custom structure loading from `config/ponderer/structures/`
- The default blueprint carrier item is `paper`, with a built-in matching guide scene; hold a `writable_book` to view the demo scene directly
- NBT-based scene filtering via `nbtFilter`
- PonderJS conversion (to/from)
- Client-server pull/push with conflict handling
- Item list UI for all registered ponder items

### Q&A
**Q: Why not use PonderJS directly?**

**A:** PonderJS does not provide hot-reload in this workflow, which makes iteration slower. Directly transmitting JS scripts also introduces additional security risks. Ponderer uses a safer data transfer approach and still provides bidirectional conversion with PonderJS, so you can switch workflows when needed (with a few APIs that are not natively supported by PonderJS).

### Commands
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

### Build
```bash
./gradlew build
./gradlew runClient
```

### Q&A
**Q：为什么不直接使用 PonderJS？**

**A：**PonderJS 本身不支持热重载，编辑反馈链路较长；另外，直接传输 JS 脚本也会带来额外安全风险。Ponderer 采用更安全的数据传输方式，同时提供了与 PonderJS 的双向转换能力，方便你在两种工作流之间切换（其中少量接口是 PonderJS 原生暂不支持的）。

### License
MIT
