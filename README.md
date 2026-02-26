# Ponderer

中文|[English](README-English.md)

Ponderer 是一个 NeoForge 1.21.1 模组，提供数据驱动的 Ponder 场景编写、游戏内编辑、热重载以及客户端/服务端同步能力。

### 运行要求
- Minecraft 1.21.1
- NeoForge 21.1.219+
- Ponder 1.0.60
- Flywheel 1.0.4
- Java 21

### 核心功能
- 在 `config/ponderer/scripts/` 中使用 JSON DSL 定义场景
- 游戏内场景编辑器（新增/编辑/删除/排序/复制粘贴步骤，支持在指定位置插入，Ctrl+Z/Y 撤销重做，所有坐标字段支持从场景中直接选点）
- 从 `config/ponderer/structures/` 加载自定义结构
- 默认蓝图载体物品为"纸"，并内置对应引导思索；手持"书与笔"可直接查看示例思索
- 通过 `nbtFilter` 进行 NBT 场景过滤
- PonderJS 双向转换（导入/导出）
- 客户端与服务端拉取/推送（含冲突处理）
- 场景包导入导出（ZIP 格式，方便分享）
- 物品列表界面展示全部已注册思索物品
- JEI 集成：所有 ID 输入框支持从 JEI 点击或拖放物品自动填入（可选依赖）
- 方块状态属性：放置/替换方块时可指定 BlockState 属性（如 facing、half 等）
- 扩展实体解析：船、矿车、盔甲架等物品类实体可通过 JEI 直接拖入实体字段

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
- `/ponderer export [filename]`：将所有脚本和结构导出为 ZIP 文件到 `config/ponderer/`。
- `/ponderer import <filename>`：从 `config/ponderer/` 中的 ZIP 文件导入脚本和结构。

### 构建
```bash
./gradlew build
./gradlew runClient
```

### Q&A
**Q：为什么不直接使用 PonderJS？**

**A：**PonderJS 本身不支持热重载，编辑反馈链路较长；另外，直接传输 JS 脚本也会带来额外安全风险。Ponderer 采用更安全的数据传输方式，同时提供了与 PonderJS 的双向转换能力，方便你在两种工作流之间切换（其中少量接口是 PonderJS 原生暂不支持的）。

### 许可证
MIT

---
