# DSL Step 打开 UI vs Stick 右键打开 UI：调用链差异说明

## 版本一：基于当前主工程实现的差异说明

### A. DSL step（show_interface）打开 UI 的完整调用链

1. `DynamicPonderPlugin.applyStep(...)` 命中 `case "show_interface"`
2. `DynamicPonderPlugin.applyShowInterface(...)`
3. `scene.addInstruction(new ShowInterfaceInstruction(step, duration))`
4. `ShowInterfaceInstruction.firstTick(...)`
5. `PondererServices.PLATFORM.showInterfaceStep(step, durationTicks)`
6. `ForgePlatformHelper.showInterfaceStep(...)`
7. `ForgeShowInterfaceClient.showInterfaceStep(...)`
8. 客户端构造 `BlockSnapshot`（来自 step 的 `blockPos/direction/point` + 当前世界状态）
9. `ClientInputHandler.prepareMirrorReplay(durationTicks)`（设置自动关闭计时）
10. 发送 `SaveSnapshotPacket` + `ReplaySnapshotPacket`
11. 服务端 `SaveSnapshotPacket.handle(...)` -> `SnapshotStorage.save(...)`
12. 服务端 `ReplaySnapshotPacket.handle(...)` -> `SnapshotReplayer.replay(...)`
13. `SnapshotReplayer.runVirtualUse(...)` 用 FakePlayer 执行 `snapshotState.use(...)`
14. 捕获/重映射开界面相关包，必要时封装为 `MirrorForgeOpenPacket`
15. 客户端 `MirrorForgeOpenPacket.handle(...)` -> `MirrorForgeOpenClient.open(...)`
16. `MenuType.create(...)` + `ScreenFactory.create(...)` -> `mc.setScreen(screen)`

### B. stick 右键打开 UI 的完整调用链

1. 事件入口：`ClientInputHandler.onUseKey(InputEvent.InteractionKeyMappingTriggered)`
2. 条件：`event.isUseItem()` 且主手是 stick
3. `event.setCanceled(true)`
4. `ClientInputHandler.prepareMirrorReplay(-1)`（不自动关闭）
5. 发送 `ReplaySnapshotPacket`
6. 服务端 `ReplaySnapshotPacket.handle(...)` -> `SnapshotReplayer.replay(...)`
7. 后续与 DSL 链路相同：虚拟 use -> 捕包/镜像 -> `MirrorForgeOpenPacket` -> `MirrorForgeOpenClient.open(...)` -> `mc.setScreen(screen)`

补充：stick 右键链路依赖“已存在快照”。快照通常来自 `ClientInputHandler.onMousePre(...)` 的中键采样并发送 `SaveSnapshotPacket`。

### C. 两条链路的核心差异（当前实现）

1. 触发源不同
- DSL：Ponder 指令时间轴触发（脚本驱动）。
- stick：玩家输入事件触发（手动交互）。

2. 快照来源不同
- DSL：每次执行 `show_interface` 都即时按 step 上下文构造并上传快照。
- stick：右键只发 replay，请求重放“此前保存的快照”。

3. 自动关闭策略不同
- DSL：`prepareMirrorReplay(durationTicks)`，镜像 UI 可按 step 时长自动关闭。
- stick：`prepareMirrorReplay(-1)`，默认不自动关闭。

4. 上下文校验不同
- DSL：会校验 `blockPos`，并对 step.block 与实际方块不一致打 debug 日志。
- stick：无 step 语义校验，直接依赖用户采样结果。

5. 上游链长不同，下游链基本一致
- 二者在进入 `ReplaySnapshotPacket -> SnapshotReplayer` 之后几乎是同一条底层镜像开屏管线。

---

## 版本二：对照 ref/stickTest 后的修订差异说明

结论：当前主工程中的“stick 右键链路”与 `ref/stickTest` 的核心机制基本同源；主要增量在于“DSL 桥接入口 + 自动关闭策略”。

### A. 与 ref/stickTest 一致的部分

1. `SaveSnapshotPacket / ReplaySnapshotPacket / SnapshotReplayer / MirrorForgeOpenPacket / MirrorForgeOpenClient` 这条核心镜像开屏骨架一致。
2. 都是服务端虚拟 `snapshotState.use(...)` 捕获菜单相关包，再在客户端重建 menu/screen 并 `setScreen(...)`。

### B. 主工程相对 ref/stickTest 的新增/差异

1. 新增 DSL 入口桥接
- 主工程多了 `show_interface` step -> `ShowInterfaceInstruction` -> `ForgeShowInterfaceClient` 这条“脚本触发入口”。
- ref/stickTest 没有 DSL/Ponder 指令侧入口，只有 stick 输入入口。

2. 新增镜像 UI 自动关闭能力
- 主工程 `ClientInputHandler` 有 `prepareMirrorReplay(autoCloseTicks)` 和 `mirrorAutoCloseTicks` 逻辑。
- ref/stickTest 对应版本没有 auto-close 计时逻辑。

3. 主工程对 show_interface 做了更强语义约束
- 例如 step 上下文检查、期望 block 与实际 block 的 mismatch 日志。
- ref/stickTest 不存在这层 DSL 语义检查（因为没有 DSL 入口）。

### C. 最终结论（结合 ref 校验）

1. “DSL step 打开 UI”不是另一套底层开屏系统。
2. 它本质上是“在 stick-snapshot/replay 管线前面加了一个脚本化入口与参数化控制层”。
3. 真正导致行为差异的点，主要在“如何得到快照”和“是否自动关闭”，而不是后半段的 menu/screen 打开机制。

---

## 版本三：本次问题的实锤定位（非猜测）

问题描述：DSL step 打开 UI 后，看到的背包等上下文像真实玩家，而不是虚拟玩家上下文。

### A. 实锤结论

1. DSL 与 stick 在“是否使用虚拟玩家执行方块交互”这件事上没有差异。
- 二者最终都进入 `SnapshotReplayer.runVirtualUse(...)`。
- 这里明确使用 `FakePlayer` 调用 `snapshotState.use(...)`。

2. 真实背包上下文泄漏点在客户端镜像开屏构造处，且是两条链路共用问题，不是 DSL 独有。
- `MirrorForgeOpenClient.open(...)` 里使用 `menuType.create(windowId, mc.player.getInventory(), extraData)`。
- 这会把“真实玩家 Inventory 对象”直接传给镜像菜单，导致菜单读取真实背包数据。

### B. 已落地修复（保持“绝对不影响现实世界”原则）

1. 将镜像菜单构造参数从真实背包改为隔离的 shadow inventory。
- 现在使用 `new Inventory(mc.player)` 构建镜像专用 inventory，再传入 `menuType.create(...)`。
- 目的：切断镜像 UI 对真实玩家背包对象的直接引用，避免现实世界上下文泄漏到镜像 UI。

2. 编译验证
- 已执行 `:Forge:compileJava`，构建成功。

---

## 版本四：重扫后的细节结论（按“除触发/关闭外完全一致”目标）

### A. 重扫发现的微差异

1. 原先还存在一个细节差异：`BlockHitResult.isInside`
- stick 采样路径使用 `hit.isInside()`。
- DSL show_interface 之前固定传 `false`。

### B. 本次已修复

1. 现在已把 `isInside` 在 DSL 链路中贯通到重放快照。
- 采集：`NbtPickState` 记录 `_block_inside`。
- 编辑：`ShowInterfaceScreen` 恢复并随 step 一起保存。
- 回放：`ForgeShowInterfaceClient` 构造 `BlockSnapshot` 时使用该布尔值。

2. 经过这次修复后，除以下两点外，链路行为保持一致：
- 触发方式不同（DSL 指令触发 vs 玩家输入触发）。
- 关闭策略不同（DSL 可自动关闭 vs stick 默认手动关闭）。
