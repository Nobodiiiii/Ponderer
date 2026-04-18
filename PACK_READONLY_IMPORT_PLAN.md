# Ponderer Resource Pack Readonly/Import Plan

## 背景

当前资源包机制把“安装到 `resourcepacks/` 的资源包”和“导入到本地可编辑目录的副本”混在了一起。

现有问题主要有：

- 新放进 `resourcepacks/` 的包会被自动解包到本地，导致“只想用、并不想编辑”的资源包也进入可写状态。
- 包更新时会触发本地覆盖、备份、重写等逻辑，语义过重。
- 用户一旦想编辑某个资源包内容，系统缺少清晰的“从只读源切换到本地副本”的状态机。
- “读取中的 pack”“本地导入副本”“导出出的 zip”三者边界不清晰。

本方案按新的规则重构：

1. 新装的资源包默认只读，不再自动导入到本地。
2. 资源包更新时，逻辑层只比对版本号。
3. 用户显式导入后，记录导入时的版本号；此后用户编辑的是本地副本，运行时也只加载本地副本，不再加载同资源包的只读内容。
4. 若导入后资源包版本更新，只在聊天区提示，不做自动更改、覆盖或同步。

## 目标

- 明确区分“只读源资源包”和“本地可编辑导入副本”。
- 保证读取规则稳定、可预测。
- 让导入动作变成显式的“创建本地副本”。
- 让更新提示变成轻量提醒，不再自动覆盖用户内容。
- 尽量复用现有本地目录结构，降低迁移和实现成本。

## 非目标

- 不做自动合并。
- 不做自动覆盖。
- 不做基于文件哈希的导入更新判定。
- 不要求资源包更新后立即把新内容同步到本地副本。

## 术语

### 1. Source Pack

放在 `resourcepacks/` 下的 Ponderer 资源包 ZIP。

特性：

- 默认只读。
- 默认直接参与运行时读取。
- 不自动复制到本地编辑目录。

### 2. Imported Local Pack

用户从某个 `Source Pack` 显式导入出来的本地可编辑副本。

特性：

- 位于本地配置目录。
- 允许用户自由修改。
- 一旦存在，对应 `Source Pack` 的运行时内容就被屏蔽。

### 3. Workspace Scene

用户原本就在本地写的普通场景，不属于任何导入 pack。

### 4. Pack Identity

统一用 `packId` 表示资源包身份。`packId` 必须稳定且可用于文件夹名、运行时分组和状态记录。

`displayName` 只用于 UI 展示，不作为唯一键。

## 核心规则

### 规则 A：默认只读，不自动导入

新增资源包 ZIP 后：

- 直接从 ZIP 读取场景和结构。
- 不在 `config/ponderer/scripts/_packs/` 或 `structures/_packs/` 下生成副本。
- 不写入“已导入”状态。

### 规则 B：只读源的更新不做本地覆盖

若一个资源包还未被导入：

- 运行时始终读取当前 ZIP 的最新内容。
- 不做备份。
- 不做自动覆盖。
- 不需要记录“上次加载版本”。

这里“逻辑层只比对版本号”的含义是：

- 对“是否提示更新”“是否判定导入副本落后”只看版本号。
- 只读源本身的运行时读取仍应读取当前 ZIP 内容，而不是依赖旧缓存。

### 规则 C：导入后切换为本地副本优先

用户导入某个 `Source Pack` 后：

- 复制场景和结构到本地可编辑目录。
- 记录 `importedVersion`。
- 运行时对该 `packId` 只加载本地副本。
- 同 `packId` 的只读 ZIP 仍可保留在 `resourcepacks/`，但不再参与运行时加载。

### 规则 D：导入后若源包更新，只提示

若本地已存在导入副本，且发现 `Source Pack.version != importedVersion`：

- 继续加载本地副本。
- 不覆盖本地文件。
- 不写 `.bak`。
- 不自动重新导入。
- 只在聊天区提示“资源包有新版本，本地导入副本保持不变”。

## 状态模型

按 `packId` 划分，运行时只会落入以下几种状态之一。

### 1. Readonly Source

条件：

- `resourcepacks/` 中存在 ZIP。
- 本地没有对应导入副本。

行为：

- 直接读取 ZIP。
- 允许正常展示和播放。

### 2. Imported Local

条件：

- 本地存在导入副本。
- `resourcepacks/` 中可能有，也可能没有 ZIP。

行为：

- 只加载本地副本。
- ZIP 不参与运行时场景注册。

### 3. Imported Local With Newer Source

条件：

- 本地存在导入副本。
- `resourcepacks/` 中存在同 `packId` ZIP。
- `sourceVersion != importedVersion`。

行为：

- 仍只加载本地副本。
- 启动或重扫时提示一次。
- 不做同步。

### 4. Imported Local Without Source

条件：

- 本地存在导入副本。
- `resourcepacks/` 中已不存在原 ZIP。

行为：

- 仍加载本地副本。
- 不提示“孤儿包删除”，因为这不是错误状态。
- UI 中可标记为“已导入，本地独立”。

## 目录与数据文件

为降低改造成本，本方案优先复用现有目录。

### 本地可编辑目录

- `config/ponderer/scripts/`：普通本地场景
- `config/ponderer/structures/`：普通本地结构
- `config/ponderer/scripts/_packs/<packId>/...`：导入的本地场景副本
- `config/ponderer/structures/_packs/<packId>/...`：导入的本地结构副本

### 新状态文件

新增：

- `config/ponderer/.pack_state.json`

用途：

- 记录哪些 pack 已被导入
- 记录导入时版本号
- 记录提示过哪个新版本，避免重复刷屏

建议结构：

```json
{
  "imports": {
    "my_pack": {
      "packId": "my_pack",
      "displayName": "My Pack",
      "sourceFile": "[Ponderer] My Pack.zip",
      "importedVersion": "1.2.0",
      "currentSourceVersion": "1.3.0",
      "lastNotifiedSourceVersion": "1.3.0",
      "importedAt": "2026-04-18T12:00:00"
    }
  }
}
```

字段说明：

- `packId`：唯一身份
- `displayName`：UI 显示名
- `sourceFile`：可选，记录来源 ZIP 文件名
- `importedVersion`：导入当时的版本号，后续比较只看它
- `currentSourceVersion`：最近一次扫描到的源包版本号，可选缓存
- `lastNotifiedSourceVersion`：上次已提示的新版本号，避免重复提示
- `importedAt`：导入时间，仅用于展示和排查

## 读取方案

## 总体优先级

同一个 `packId` 的读取优先级固定为：

1. Imported Local Pack
2. Readonly Source Pack

普通本地 `workspace` 场景不受此规则影响，仍按本地场景读取。

## 启动时读取流程

### 第一步：加载普通本地场景

读取：

- `config/ponderer/scripts/`
- `config/ponderer/structures/`

但跳过：

- `_packs/` 子目录

### 第二步：加载已导入的本地 pack

读取：

- `scripts/_packs/<packId>/`
- `structures/_packs/<packId>/`

并在内存中记下：

- `importedPackIds`

### 第三步：扫描 `resourcepacks/`

对每个 ZIP：

- 读取 `pack.json`
- 解析 `packId`、`displayName`、`version`
- 判断该 `packId` 是否已存在本地导入副本

若未导入：

- 作为 `Readonly Source Pack` 加载

若已导入：

- 不加载其场景
- 只做版本比较与聊天提示

### 第四步：更新提示状态

对每个已导入 pack：

- 如果发现 `sourceVersion != importedVersion`
- 且 `sourceVersion != lastNotifiedSourceVersion`
- 则加入待提示队列

提示后：

- 写回 `lastNotifiedSourceVersion`

## 只读源的资源读取方式

建议新增 `PackContentProvider` 抽象，至少有两类实现：

- `LocalImportedPackProvider`
- `ZipReadonlyPackProvider`

`ZipReadonlyPackProvider` 负责：

- 枚举 ZIP 中的 scene JSON
- 按需读取 structure bytes

这样可以避免为了“只读使用”而在本地落盘一份可编辑副本。

如果底层某些运行时路径强依赖文件系统路径，可允许增加一个只读缓存目录：

- `config/ponderer/.cache/readonly/<packId>/...`

但要明确：

- 该目录只是缓存，不是导入副本
- 可以随时重建
- 不能被 UI 当作用户可编辑内容

缓存失效建议使用 ZIP 文件的：

- 最后修改时间
- 文件大小

或直接每次重扫重新建索引。

注意：

- 版本号比较只用于“逻辑提示”
- 只读缓存正确性不能只依赖版本号，否则同版本替换 ZIP 时会出现脏缓存

## 导入方案

## 导入的定义

`/ponderer import` 或导入界面的新语义：

- 不再是“加载资源包”
- 改为“把只读资源包复制为本地可编辑副本”

## 导入流程

### 1. 选择一个 Readonly Source Pack

前提：

- `resourcepacks/` 中存在合法 Ponderer pack
- 当前 `packId` 还没有导入副本

### 2. 复制到本地可编辑目录

复制内容：

- scenes
- 被该 pack 使用到的 structures

目标目录：

- `scripts/_packs/<packId>/`
- `structures/_packs/<packId>/`

### 3. 写入状态文件

写入：

- `packId`
- `displayName`
- `sourceFile`
- `importedVersion`
- `importedAt`

### 4. 重载运行时目录

导入完成后：

- 本地副本加入运行时加载
- 同 `packId` 的只读 ZIP 立即被屏蔽

## 已导入 pack 的再次导入

默认不做覆盖式再次导入。

若用户对同一个 `packId` 再次点击导入：

- 若 `sourceVersion == importedVersion`，提示“该资源包已导入本地”
- 若 `sourceVersion != importedVersion`，提示“源资源包有新版本，但本地副本不会自动覆盖”

后续如需支持手动“重新导入为新副本”或“覆盖重建”，应设计为独立命令，而不是复用当前导入动作。

## 更新检查与聊天提示

## 判定规则

仅对“已导入 pack”做更新提示。

判定条件：

- `sourceVersion` 存在
- `importedVersion` 存在
- `sourceVersion != importedVersion`

不比较：

- zip hash
- 文件内容
- 时间戳

## 提示文案建议

聊天区提示示例：

```text
[Ponderer] Pack my_pack has a newer source version v1.3.0.
Your local imported copy is based on v1.2.0 and remains unchanged.
```

中文可对应为：

```text
[Ponderer] 资源包 my_pack 检测到新版本 v1.3.0。
当前加载的仍是你基于 v1.2.0 导入的本地副本，系统未做任何覆盖。
```

## 提示频率

同一个 `packId + sourceVersion` 只提示一次。

只有以下情况才允许再次提示：

- `sourceVersion` 再次变化
- 用户删除了 `lastNotifiedSourceVersion`
- 用户移除了导入副本并重新进入只读模式

## 导出方案

导出逻辑要与“只读源/本地副本”规则保持一致。

## 导出的来源范围

导出只面向“可编辑内容”：

- 普通本地场景
- Imported Local Pack 中的本地场景

默认不把只读 `Source Pack` 作为可编辑导出源，因为：

- 它本来就是外部现成资源包
- 用户如果想改它，应先导入再导出

## 导出行为

导出时：

- 只读取当前本地可编辑内容
- 不反向影响 `resourcepacks/` 中的源 ZIP
- 不修改 `.pack_state.json`
- 不改变运行时优先级

## 用户语义

建议 UI 文案明确区分：

- “导入资源包为本地可编辑副本”
- “导出本地场景/导入副本为资源包”

避免把“读取一个只读包”和“导入成可编辑副本”混为同一动作。

## UI 与命令语义调整

## Import Screen

导入界面建议显示每个 pack 的状态：

- `只读可用`
- `已导入本地`
- `源包有新版本`
- `源包缺失，但本地副本仍可用`

按钮行为：

- 对只读可用包显示 `导入为本地副本`
- 对已导入包显示 `已导入`
- 对有新版本包显示 `发现新版本`

## 命令建议

现有 `/ponderer import` 建议改义为：

- 打开“导入为本地副本”界面

后续可补充：

- `/ponderer pack status`
- `/ponderer pack remove_local <packId>`
- `/ponderer pack open_source <packId>`

本方案不要求实现自动重导入命令。

## 数据迁移方案

## 迁移目标

把现有“已解包到 `_packs/` 的旧 pack”视为“已经导入的本地副本”。

## 迁移步骤

### 1. 停止旧的自动解包逻辑

启动时不再把 `resourcepacks/*.zip` 自动解包进 `_packs/`。

### 2. 读取旧注册表

读取旧的：

- `.ponderer_registry.json`

若发现某个 pack 在 `_packs/<packId>/` 下已有本地目录：

- 在新 `.pack_state.json` 中创建 `imports[packId]`
- 把旧 `version` 迁移为 `importedVersion`
- 把旧 `sourceFile` 尽量迁移过来

### 3. 保留现有本地目录

不搬动：

- `scripts/_packs/<packId>/`
- `structures/_packs/<packId>/`

这样迁移最稳，风险最低。

### 4. 废弃旧注册表职责

旧注册表不再承担：

- 自动更新
- 自动覆盖
- orphan 删除提示

新状态文件只负责：

- 是否已导入
- 导入基线版本
- 是否已提示新版本

## 代码结构建议

建议把当前 `SceneStore` 中 pack 相关逻辑拆成四个职责块。

### 1. `PackStateStore`

负责：

- 读写 `.pack_state.json`
- 提供 `isImported(packId)`
- 提供 `getImportedVersion(packId)`
- 提供 `markNotified(packId, sourceVersion)`

### 2. `PackScanner`

负责：

- 扫描 `resourcepacks/`
- 解析 `pack.json`
- 构建只读 source pack 清单

### 3. `PackContentProvider`

负责：

- 从本地导入目录或 ZIP 读取场景和结构

### 4. `PackNotificationService`

负责：

- 比较 `sourceVersion` 与 `importedVersion`
- 生成聊天区提示

这样可避免 `SceneStore` 继续承担扫描、导入、覆盖、注册表、提示、解包等全部职责。

## 实施顺序

### 第一阶段：只读/导入状态切分

- 新增 `.pack_state.json`
- 停止自动解包
- 启动时区分“只读源”和“已导入本地”

### 第二阶段：读取链路改造

- 增加 ZIP 只读 provider
- 对已导入 pack 屏蔽同 `packId` 的 source pack

### 第三阶段：导入语义改造

- 把导入改成“复制为本地副本”
- 写入 `importedVersion`

### 第四阶段：提示与 UI

- 只对已导入 pack 做版本差异提示
- 更新导入界面与聊天提示文案

### 第五阶段：导出约束收口

- 导出只面向可编辑内容
- 明确只读源需先导入后再编辑/导出

## 边界与约束

### 1. 版本号是唯一逻辑比较依据

这是本方案的明确约束。

含义：

- 若作者替换了 ZIP 内容但没改版本号
- 对“已导入 pack 的更新提示”来说，系统会视为没有新版本

这是规则带来的已知限制，不是 bug。

### 2. 只读源的读取正确性不能依赖版本号

因为只读源当前就该读取当前 ZIP。

所以：

- 运行时 zip 内容读取或缓存失效
- 不能只按 `version` 判断

否则同版本替换 ZIP 时会读取到旧缓存。

### 3. 不自动合并用户修改

一旦导入，本地副本就被视为用户资产。

之后无论源包怎么更新：

- 都不覆盖
- 都不合并
- 都不写备份

## 最终效果

落地后，用户体验应变成：

- 新下载的资源包放进 `resourcepacks/` 后，立刻可用，但默认只读。
- 想编辑时，手动执行一次导入，生成本地副本。
- 导入后，系统始终加载本地副本，不再加载同源只读内容。
- 原资源包以后更新，只会提醒“有新版本”，不会改动用户本地内容。
- 导出逻辑只处理用户可编辑内容，不再和只读源包状态互相污染。
