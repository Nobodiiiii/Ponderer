# JEI 相关 Mixin 与集成链路专项审计

审计日期：2026-10-01（Asia/Shanghai）。依据：[全量 Mixin 审计报告](D:/Files/ponderer/docs/MIXIN_AUDIT_2026-10-01.md)的作用范围、可组合性、映射、生命周期及验证原则。此次新增审计文档和证据清单，未修改 Java、Mixin 配置或构建逻辑。

**结论：当前没有直接注入 JEI 类的 Mixin。应优先修复 JEI 公开 API 调用周围的隔离、菜单归属、输入路由和状态生命周期，再收窄 PonderUI 的全局改写。新增 JEI 内部类 Mixin 不能解决这些问题。**

**后续历史冲突补查：** 在版本目录的 2026-09-01 崩溃报告中确认，旧 Ponderer 1.10.6 的 `JeiGuiEventHandlerMixin` 会因 JEI 19.51 绘制入口改名、注入组零命中而崩溃。该类已在 2026-09-27 的 `2db6e9f2` 提交删除；后续新构建与隔离 19.51 运行验证通过。详见 [19.51 冲突定位与消解验证](D:/Files/ponderer/docs/JEI_19_51_CONFLICT_2026-10-01.md)。下文“未运行游戏”等表述保留为最初专项审计的时间点记录，不含这次后续验证。

## 1. 范围与证据

### 1.1 当前分支事实

- 分支 `1.21.1`；[settings.gradle](D:/Files/ponderer/settings.gradle)启用 **Common、NeoForge、Fabric**；不是仅 NeoForge，也不包含历史 Forge 模块。
- [gradle.properties](D:/Files/ponderer/gradle.properties)的 JEI 编译基线为 **19.21.0.243**。
- 枚举三个有效模块全部 Java 的 `@Mixin`，包括 Accessor：Common 26、NeoForge 8、Fabric 7，合计 **41 个源文件**；40 个实际注册；每端客户端注册 **33 个**。NeoForge 的 `AnimationTickHolderMixin` 未注册。
- 两份 JSON 均可解析、无重复条目，每个注册项在 Common + 对应加载器中唯一解析；`required=true`、`defaultRequire=1`，均为 client 项。
- 没有 `@Mixin` 指向 `mezz.jei.*`，没有 JEI 内部类的 Redirect/Accessor。`compat/jei/` 下的 **10 个 Java 文件都不是 Mixin**。`JeiOverlayController` 使用 JEI 内部 API 的反射，仍属于兼容性审查范围。
- 旧报告的 39 个文件、每端 31 项是旧快照。本次不沿用其数量，也不把旧运行结果当成本次验证。
- 开始时已有 `InterfaceHost.java` 的用户修改；本次保留，按当前工作区审计。完整文件指纹、注册清单、现有产物和日志信息见 [JSON 清单](D:/Files/ponderer/docs/JEI_MIXIN_AUDIT_2026-10-01.inventory.json)。

本次筛查全部 41 个 Mixin，专项逐项审查下表 **13 个功能关联文件**，并追踪全部 10 个 JEI 集成类、加载器输入事件、槽位编辑状态、宿主与沙盒操作。其他 Mixin 的全量业务审计沿用原报告；本次不把所有间接依赖的游戏基础设施都命名为“JEI Mixin”。

### 1.2 版本差异

| 证据 | 实际版本 / 可信边界 |
|---|---|
| 编译依赖 | JEI 19.21.0.243 |
| [ref/SOURCES.md](D:/Files/ponderer/ref/SOURCES.md)及本地 JEI 源码 | 对应 MC 1.21.1 分支；`specificationVersion=19.27.0`，不是编译版本的精确快照 |
| Fabric 实例日志及 JAR 元数据 | JEI 19.27.0.340；Ponder 1.0.69 |
| NeoForge 实例日志及 JAR 元数据 | JEI 19.42.0.385；不能据 19.27 源码证明其所有内部事件时序 |

先查版本化 `ref/`；发现 JEI 小版本差异后，补读本地 Gradle 缓存里的 **19.21.0.243 官方 sources.jar**，确认 `IIngredientType.getUid()`、`ICodecHelper.getTypedIngredientCodec()`、`IIngredientManager.getIngredientCodec()` 已在编译基线存在，及 ghost 目标查询/接收流程。没有联网或反编译依赖实现。

对 NeoForge 安装的 19.42 JAR 仅用 `javap -public` 检查 API 签名：旧版 `IGlobalGuiHandler.getClickableIngredientUnderMouse(double,double)` 仍在，类型 ID 与 codec API 也存在。**API 存在不等于事件行为已跨版本验证。** 参考源码中新的 clickable factory 回调自 19.23.0 才有、`getAllTypedIngredients` 自 19.24.0 才有，不应直接把它们引入仍以 19.21 编译的代码。

### 1.3 运行与映射证据

按 [minecraft-crash-triage](C:/Users/Nobodiiiii/.codex/skills/minecraft-crash-triage/SKILL.md)检查仓库内版本日志：

- [Fabric latest.log:156](D:/Files/ponderer/.minecraft/versions/1.21.1-Fabric/logs/latest.log:156)记录本插件 GUI handler 注册；[210](D:/Files/ponderer/.minecraft/versions/1.21.1-Fabric/logs/latest.log:210)有既有 `interfaces PASS (intercepted=7, taintDrops=0)`。
- [NeoForge latest.log:387](D:/Files/ponderer/.minecraft/versions/1.21.1-NeoForge/logs/latest.log:387)记录本插件 GUI handler 注册。本次读取的 NeoForge `latest.log` 没有上述 interfaces PASS，不能复制旧报告的结论。
- 没有找到可归因于本专项链路的已复现 Mixin 注入崩溃。NeoForge 的账号服务超时、Curios 槽位错误、可选类缺失警告不构成 JEI 集成故障证据。
- `D:/Files/ponderer/.minecraft/crash-reports/` 不存在；已有版本日志，未以根 `latest.log` 替代。
- [Fabric refmap](D:/Files/ponderer/Fabric/build/classes/java/main/ponderer.refmap.json)含 `removed → method_25432`、`setScreen → method_1507`、网络 `send → method_52906`；NBT 的 ItemStack/ResourceLocation 描述符已变为 `class_1799/class_2960`。
- Fabric 现有发布 JAR 的 `PonderUIAccessor.getHoveredBlockPos` 返回类型已为 `class_2338`。因此不能把所有 `remap=false` Accessor 都判为已失效。两端现有 1.10.6.1 发布 JAR 的 Mixin JSON 与源码配置一致；Fabric 内置 refmap 与构建目录 refmap 解析后的 JSON 内容一致，仅原始字节格式不同。
- NeoForge 配置没有 refmap 字段，构建树未发现本模组 refmap；不能套用旧 Forge 的 `m_*` 要求判错。

**本次没有重新构建或启动游戏。** 这是文档审计；上述 PASS、JAR 和 refmap 都是已有产物。以下“已确认”指源码控制流、注册、签名或已有产物可以证明的事实；条件性问题均说明触发条件，不声称已在游戏逐个复现。

## 2. 13 个关联 Mixin 的逐项结论

### 2.1 当前实现：具体在什么场景做什么

阅读约定：**Common 的类在 Fabric 和 NeoForge 都注册；带 Fabric 后缀的类只在 Fabric 使用，NeoForge 版本只在 NeoForge 使用。** 下表每行对应一个实际源文件，不把两端同功能实现省略为“同上”。场景举例用于解释代码触发条件，不代表已在每一种第三方机器上实测。

| Mixin / 平台 | 玩家处于哪个具体场景 | 什么时候执行、具体干什么 | 功能边界 |
|---|---|---|---|
| **PonderUIMixin** · 两端 | 从槽位编辑器回到 Ponder，准备把 JEI 原料拖到嵌入界面的槽位；拖完按 ESC 返回编辑器 | 槽位编辑期间在鼠标旁显示拖拽说明、已有绑定数量和退出提示；ESC 调用 `finishAndReopenEditor()` 将绑定写回表单；PonderUI 被移走时调用 `reset()` 清理编辑状态。此外在普通 Ponder 绘制中抬高场景/文本/按钮层级、重绘 children，并添加编辑入口和文本面板 | **不负责接收 JEI 投放**，投放由 ghost handler 完成。renderWindow/renderWidgets 的层级补丁并非只在 JEI 打开时执行。临时进入配方页也会触发 removed，是 J4 的问题来源 |
| **PonderUIAccessor** · 两端 | 点击槽位编辑的入口后，需要直接回到“正在编辑的那一段教程”，而不是默认第一段 | 普通代码调用其访问方法：读取 scenes，找到匹配场景，写 index，并同步 lazyIndex，随后启动该场景；其他访问方法供拾取状态、hoveredBlockPos 和慢速按钮等功能使用 | **没有自动执行的注入回调**。这是私有字段访问桥；JEI 不调用它。槽位编辑的具体调用方是 `InterfaceSlotEditState.openPonderUIForEdit()` |
| **PonderTooltipNbtMixin** · NeoForge | 鼠标悬停在 JEI 列表或书签中的某个物品，Ponder 正在判断是否显示“按住键查看教程”的提示；物品关联了本模组 NBT 条件教程 | 在 `PonderTooltipHandler.updateHovered(ItemStack)` 开头检查该物品是否有教程和 NBT 过滤；本模组判定没有可见教程时取消原方法 | 背包等产生同一 Ponder 物品提示的入口也受影响；不是 JEI 独占逻辑。当前可能连同外部教程的提示一起取消 |
| **PonderTooltipNbtMixinFabric** · Fabric | 在 Fabric 的 JEI 列表、书签或背包中悬停带 NBT 条件教程的物品 | 在 `updateHovered(ItemStack)` 开头执行教程存在性及 NBT 可见性检查，条件不通过时取消 Ponder hover 更新 | 负责“提示资格”，不会打开教程，也不负责过滤 JEI 原料列表；具有与 NeoForge 版本相同的外部教程误伤风险 |
| **PonderUINbtMixin** · NeoForge | 从一个具体 ItemStack 打开教程，例如对 JEI 中的物品触发 Ponder；需要区分同种物品的不同组件/NBT | 进入 `PonderUI.of(ItemStack)` 或 `of(ItemStack, PonderTag)` 时，把该物品暂存到 ThreadLocal；方法正常返回时清除，让中间的场景编译能读到物品上下文 | **不执行筛选**，只传递上下文。只传 ResourceLocation 的打开路径不经过这两个钩子；异常和嵌套恢复问题见 J11 |
| **PonderUINbtMixinFabric** · Fabric | 在 Fabric 从 JEI 或其他入口用具体 ItemStack 打开普通/标签教程 | 在两个 `of(ItemStack...)` 重载开头设置当前物品、正常返回时清除，供编译阶段读取 | 不控制 JEI 点击，不显示 overlay；与 NeoForge 版本分别注册，业务用途相同 |
| **PonderSceneRegistryNbtMixin** · NeoForge | 上述 ItemStack 打开流程已经开始，Ponder 按物品 ID 编译出候选教程列表，需要剔除不匹配当前物品的教程 | 在 `PonderSceneRegistry.compile(ResourceLocation)` 返回时读取 ThreadLocal；有非空 ItemStack 和候选列表才调用 `NbtSceneFilter.filter`，必要时替换返回列表 | 无物品上下文时不筛选。当前全空结果会回退原列表；它不会改变 JEI 中有哪些原料 |
| **PonderSceneRegistryNbtMixinFabric** · Fabric | Fabric 的 Ponder 编译候选教程完成，打开入口已传入 ItemStack 上下文 | 在 compile 返回处根据当前物品过滤场景列表，将筛选结果交给随后创建的 PonderUI | 功能是“选择显示哪段教程”，不是 JEI 配方筛选；只在所钩住的编译路径执行 |
| **SandboxScreenGateMixin** · 两端 | Ponder 内显示机器界面；试用、投放引起的菜单逻辑或渲染代码调用 `Minecraft.setScreen` 打开子页面/关闭页面 | setScreen 开头登记钩子已生效；在沙盒作用域内取消真实换屏，把请求交给 session：子页面进入内部屏幕栈，null 变为关闭请求，被拒绝的页面转为提示 | 保护外层 PonderUI 不被第三方菜单替换。**JEI 自己在沙盒外打开 R/U 配方页不会被它阻止** |
| **SandboxNetworkFirewallMixin** · 两端 | 在 Ponder 中试用菜单、点击按钮，或 JEI 投放引发槽位/合成更新，第三方代码试图向服务器发包 | 拦截汇总的 `Connection.send(Packet, listener, flush)`；渲染线程沙盒作用域内交给 session 处理并阻止真实发送；作用域外还拦截带约定沙盒窗口 ID 101–127 的指定原版容器包 | 不是只屏蔽 JEI 包。只有触发 send 才有拦截，不表示每次 JEI 投放必然发包；对作用域外任意第三方自定义包并无全面保证 |
| **SandboxWorldBridgeMixin** · 两端 | 嵌入菜单在绘制、判断槽位或试用更新时，通过 `Minecraft.level` 查询方块、方块实体、流体、区块，或尝试改方块 | 对符合渲染线程、沙盒作用域、receiver 条件的 Level 调用，转到沙盒世界；setBlock/移除/销毁在沙盒处理，setBlockEntity 在该桥上直接取消 | JEI 投放通过受控 SessionOps 进入的调用可受保护；当前 JEI 目标查询在 scope 外的缺口不会被它自动补上。普通世界调用不因显示着 JEI 就被重定向 |
| **SandboxClientLevelMixin** · 两端 | 第三方菜单在沙盒执行中对真实 ClientLevel 调用 setBlock，或按实体 ID 查询实体 | 在 ClientLevel 的预测处理前截住 setBlock 并写入沙盒；符合桥接条件的 getEntity 返回 null | 补足 ClientLevel 覆盖方法的入口，避免真实客户端预测记录被修改。返回 null 是当前隔离策略，不等于支持任意实体菜单 |
| **SandboxChunkCacheMixin** · 两端 | 第三方菜单绕过 Level.getChunk，直接从真实客户端的区块缓存取区块，例如检查机器附近环境 | 对符合桥接条件的 `ClientChunkCache.getChunk(x,z,status,required)`，返回沙盒 `lookupChunk` 的结果 | 与 WorldBridge 覆盖不同调用入口；没有 scope 时不生效，也不是 JEI 原料缓存或图标缓存 |

三个常见流程可以这样对应：

1. **JEI 物品 → Ponder 教程：** TooltipNbt 判断提示资格 → UINbt 传递 ItemStack → SceneRegistryNbt 筛选候选教程 → PonderUI 显示。这里三组 NBT Mixin 是各管一段，两端各一份。
2. **编辑器 → JEI 拖拽槽位 → 返回编辑器：** PonderUIAccessor 定位目标教程 → PonderUIMixin 显示编辑提示并处理 ESC/退出 → **PonderUiGhostHandler / InterfaceSlotGhostHandler（普通类）**接收拖拽、保存绑定。编辑 ghost 主要保存显示绑定，不等同于给沙盒实际槽位放入 ItemStack。
3. **Ponder 中试用机器 → 从 JEI 投入物品：** PonderUiGhostHandler 建立目标 → SessionOps 在沙盒内写入 ItemStack/更新合成 → 过程中触发换屏、发包或世界访问时，由五个 Sandbox Mixin 分别守住对应入口。没有发生相应调用的 Mixin 不会因一次拖拽而全部执行。

### 2.2 旧镜像架构：已删除的 JEI 相关 Mixin 原来做什么

以下四个类均为 **旧 NeoForge 实现，已在 `2db6e9f2` 删除，当前不生效**。本轮用 `git show 2db6e9f2^:<原路径>` 核对源码；不能把它们和上表当前类混算。历史兼容性证据见 [旧版冲突报告](D:/Files/ponderer/docs/JEI_19_51_CONFLICT_2026-10-01.md)。

| 旧 Mixin | 具体场景 | 原来执行什么动作 | 当前替代方式 / 边界 |
|---|---|---|---|
| **JeiGuiEventHandlerMixin** | 在 Ponder 内绘制旧镜像容器，或者该宿主的 JEI overlay 由本模组手动绘制 | 在 JEI 自身绘制入口 HEAD，若 `isRenderingEmbeddedMirror()` 或 `shouldRenderPonderUiOverlayManually(screen)` 为真，取消默认 JEI 绘制，避免镜像/宿主重复绘制 JEI | 当前使用公开 GUI handler，JEI 沿宿主正常事件绘制。五个候选方法名是版本兼容入口，不是五个不同功能。它是旧架构中真正直接注入 JEI 类的那个 Mixin |
| **PonderUIMirrorRenderMixin** | Ponder 正展示旧 `show_interface` 镜像，需把容器、JEI 原料和提示插进 Ponder 的图层 | 有可绘制镜像时取消结构 renderScene；在 renderWidgets 中插入镜像、槽位覆盖和手动 JEI overlay，尾部补 JEI/槽位 tooltip；tick 镜像，等待时隐藏进度条；重播、切场景、退出时关闭镜像并清理绑定 | 当前嵌入界面由 InterfaceOverlayElement/SandboxSession 绘制。该类同时管理生命周期，并非只解决 JEI 深度问题 |
| **EmbeddedMirrorContainerScreenMixin** | 镜像容器正在 Ponder 内绘制，背景、槽位、手持物品、文字、tooltip 可能被 Ponder 层遮住 | 在容器各绘制阶段 push/translate/pop 调整 Z；为 `mekanism.client.gui.*` 的自行管理前景界面单独整体抬高，部分标签/tooltip 路径跳过普通抬高 | 不负责原料选择和配方查询。当前不再使用这些容器级旧图层注入 |
| **PonderProgressBarAccessorMixin** | 旧镜像流程需要重置 Ponder 进度显示 | 暴露 PonderProgressBar 私有 LerpedFloat `progress`，供 ClientInputHandler 调用 `startWithValue(0)` | Accessor 本身不定时执行，也不直接隐藏进度条或控制 JEI；隐藏条的逻辑在旧 PonderUIMirrorRenderMixin 中 |

补充验证边界：这次只补充场景说明，核对了当前源码调用方及上述四类的删除前源码，没有重新构建或启动游戏。旧报告中独立完成的 JEI 19.51 验证保留其原有范围，不扩展为上述所有场景均已实测。

### 2.3 对应风险与优化处置

| 文件 | JEI 关联 / 侵入方式 | 建议 |
|---|---|---|
| [PonderUIMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java) | 槽位编辑 ESC、提示和 removed 清理；全局重绘 children、抬高渲染深度；普通 `keyPressed` 方法合并；5 个 Redirect | **重点收窄**。J4/J10/J13；清理必须识别临时配方页面；输入业务下沉控制器；只绘制自有控件 |
| [PonderUIAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIAccessor.java) | 槽位编辑打开时读取 scenes、写 index/lazyIndex；其余字段用于拾取和按钮 | **保留必要桥**。在普通导航服务封装“定位场景”，避免调用方直接更改可变列表与两个索引；无需换成反射 |
| [NeoForge PonderTooltipNbtMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderTooltipNbtMixin.java) | JEI 物品 tooltip 进入 Ponder 时，HEAD 可取消 `updateHovered(ItemStack)`，`require=0` | **P1**。J11；保留外部教程及状态清理，不取消整个 hover 状态机 |
| [Fabric PonderTooltipNbtMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderTooltipNbtMixinFabric.java) | 同上，显式 ItemStack 描述符 | 与 NeoForge 使用同一资格服务；当前 refmap 含所需类型映射 |
| [NeoForge PonderUINbtMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderUINbtMixin.java) | 两个 `of(ItemStack...)` 的 HEAD/RETURN 管理 ThreadLocal | **P2**。J11；异常/嵌套时 finally 恢复 previous，不能只 remove |
| [Fabric PonderUINbtMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderUINbtMixinFabric.java) | 同上，两个重载共 4 个钩子 | 同步改两端；明确 `of(ResourceLocation)` 编辑入口没有物品上下文的语义 |
| [NeoForge PonderSceneRegistryNbtMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryNbtMixin.java) | `compile(ResourceLocation)` RETURN 替换列表 | 相对完整取消更保守，应保留局部返回值变换；J11 修正全空回退 |
| [Fabric PonderSceneRegistryNbtMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryNbtMixinFabric.java) | 同上，`require=0` | 与 NeoForge 基本同构，可评估迁 Common；先确认两端目标和工具链 |
| [SandboxScreenGateMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxScreenGateMixin.java) | 沙盒 scope 内拦截 `Minecraft.setScreen` | **保留**，但 J1 的 scope 外查询不受保护；JEI 自己在 scope 外打开配方页仍会触发宿主 removed |
| [SandboxNetworkFirewallMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxNetworkFirewallMixin.java) | scope 内拦截网络发送，钩子可选 | **保留并前置能力检查**。J1；`require=0` 不是降级能力本身；不得用它掩盖 scope 缺口 |
| [SandboxWorldBridgeMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxWorldBridgeMixin.java) | Level 8 个世界查询/写入口；受线程、scope、receiver 限制 | **保留窄边界**。JEI 查询沙盒 Slot 时也应走受控查询入口，而非把桥扩成全局 |
| [SandboxClientLevelMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxClientLevelMixin.java) | 预测前写入拦截、实体查询 | 保留现有隔离；对依赖实体的槽位规则使用能力声明/适配，不宣称任意菜单支持 |
| [SandboxChunkCacheMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxChunkCacheMixin.java) | 第三方槽位/菜单通过真实 chunk cache 查询时重定向 | 保留 scope/receiver 门控；与上两项共同验收“JEI 悬停时真实世界不被访问/修改” |

边界说明：Interface 捕获系列仅提供输入描述符，SceneTransform/视图偏移影响 3D 场景，PonderLevel/模型数据/时钟影响底层场景运行；没有发现它们直接接管 JEI overlay、点击或 ghost handler。其原报告风险仍然适用，尤其坐标变换，但不能据此认定 JEI 的 2D 槽位坐标必须套用 3D 逆变换。新出现的两个多行文本 Accessor 也没有 JEI 专属注入。

## 3. 优先级与详细优化建议

P1：功能正确性、用户编辑数据或隔离边界应优先修复。P2：条件性可靠性、性能、兼容性重构。P3：维护性清理。没有证据支持 P0。

### J1 · P1：JEI 目标查询在沙盒外执行第三方 Slot 代码

**证据：** [PonderUiGhostHandler.java:55](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/PonderUiGhostHandler.java:55)直接调用 `slot.isActive()`、`slot.mayPlace(dropped)`；该方法由 JEI 调用，外面没有 `SandboxSession.dispatch`。实际放置在 [SessionOps.java:123](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SessionOps.java:123)才进入 scope。[SandboxWorldBridge.java](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SandboxWorldBridge.java)明确在无 scope 时返回 null；screen gate 和网络拦截也依赖 scope。

[IGhostIngredientHandler.java:24](D:/Files/ponderer/ref/1.21.1/JustEnoughItems/CommonApi/src/main/java/mezz/jei/api/gui/handlers/IGhostIngredientHandler.java:24)说明目标查询在悬停 `doStart=false` 时就会执行，并在开始拖拽时再次执行。第三方槽位如果读取 `Minecraft.player/level`、发包、换屏或抛异常，可能在用户尚未投放时触发错误上下文或副作用。**已确认隔离缺口；具体第三方副作用未复现。**

**优化：** 将槽位枚举、规则判断及第三方 `getItem/isActive/mayPlace` 放在 session 的受控查询中，返回不可变的屏幕矩形和槽位身份快照；异常在这里降级并返回空目标。`doStart=false` 不写绑定、不改菜单。不要为包住查询而把整个 JEI 渲染/配方查询放进沙盒，否则会错误拦截 JEI 自己的换屏。

同时落实原报告 R2：[SandboxSession.java:132](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SandboxSession.java:132)先执行 open、之后才检查 guard；缺 guard 后的只读 render/tick 仍执行第三方代码。必需能力缺失时，在构造前转静态占位。不能仅把交互按钮禁用。

**验收：** 制作只在 mayPlace 中查询玩家、写世界、发包、setScreen 或抛异常的测试槽位；分别悬停、拖起、投放。真实世界/网络/宿主不得变化；无 guard 时不执行第三方代码。

### J2 · P1：INGREDIENT 模式点击不能选择流体和化学品

**证据：** [PondererJeiPlugin.java:174](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/PondererJeiPlugin.java:174)先取 `getItemStack()`，为空就 `return true`；判断 `activeMode == INGREDIENT` 在此之后。非 ItemStack 既不写入字段，也不提示，事件被吞掉。相比之下 [JeiAwareGhostHandler.java:28](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiAwareGhostHandler.java:28)先处理通用原料模式，路径不一致。

**优化：** 在普通 Common 服务中统一类型判断与解析：先处理 INGREDIENT，其余 ITEM/BLOCK/ENTITY 再要求 ItemStack。点击和拖拽复用同一解析结果及不兼容提示。字段不存在时拒绝写入并给出可诊断状态，不把“未写任何数据”视为选择成功。

**验收：** 同一字段分别点击和拖入物品、流体、化学品，列表和书签均覆盖；不兼容的 BLOCK/ENTITY 模式有说明，不静默吞事件。不需要新增 Mixin。

### J3 · P1：TriggerEditorScreen 有 JEI 按钮，但没有对应 GUI/ghost 注册

**证据：** [TriggerEditorScreen.java:30](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ui/TriggerEditorScreen.java:30)继承 `AbstractSceneEditorFormScreen`，并非 `AbstractStepEditorScreen`；[63](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ui/TriggerEditorScreen.java:63)将激活动作交给 `JeiCompat.setActiveScreen`。插件仅注册 AbstractStepEditorScreen、CommandParamScreen、AiGenerateScreen、ProjectorConfigScreen、PonderUI，没有 TriggerEditor 或它的公共基类。

本地 [ScreenHelper.java:54](D:/Files/ponderer/ref/1.21.1/JustEnoughItems/Library/src/main/java/mezz/jei/library/gui/helpers/ScreenHelper.java:54)先查实际类，再查注册的可赋值类型；仅实现 `JeiAwareScreen` 不会自动得到 GUI handler。**注册缺口已确认；正常独立安装下该屏没有本插件提供的 overlay 布局/拖拽合同。**

**优化：** 将 GUI 与 ghost handler 注册到 `AbstractJeiAwareFormScreen`，统一 `activeScreen == screen` 门控，并移除由基类覆盖的重复注册；或先显式补 TriggerEditor。不要将全局 `Screen.class` 用作宽泛替代。

**验收：** 对实际所有 JeiAwareScreen 实现/子类建立注册覆盖表；逐屏验证激活、停用、调整窗口、关闭后重开。无需 Mixin。

### J4 · P1：临时查看 JEI 配方可能清空未保存的槽位编辑

**证据：** [PonderUIMixin.java:648](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:648)在任何 `removed` 上调用 `InterfaceSlotEditState.reset()`，发生在 `consumeSuppressNextPonderReturn()` 之前。reset 清空绑定和表单快照。[JEI RecipesGui.java:521](D:/Files/ponderer/ref/1.21.1/JustEnoughItems/Gui/src/main/java/mezz/jei/gui/recipes/RecipesGui.java:521)保存 parentScreen 后 `setScreen(this)`，关闭时返回父屏。

因此当槽位编辑时的 R/U 成功打开配方页，宿主暂时离开也被当成放弃编辑。返回原 PonderUI 不会恢复已清空的状态；设置“抑制返回”也不阻止 reset。另有 `PonderItemGridScreen.returnScreen` 时，removed 中的 `execute` 跳转还可能干扰配方页导航。**清理逻辑已确认，R/U 成功进入该路径后的丢失为条件性缺陷，未游戏复现。**

**优化：** 用自有编辑会话表达“活动、暂挂、提交、取消”，绑定到宿主和场景；进入 JEI 配方页时保存暂挂，返回同一宿主恢复，真正退出/切世界才销毁。明确控制导航，不能用一枚全局 boolean 表达多个嵌套跳转。可以利用公开 `IRecipesGui` 及加载器生命周期；若暂时无法可靠识别临时跳转，先在槽位编辑中限制配方导航并明确提示，避免静默丢数据。

**验收：** 编辑两项后 R/U → ESC 返回，绑定不丢；配方页内继续查配方、调整窗口、断开世界、取消编辑各自有明确结果；returnScreen 不抢走配方页。涉及 Mixin 修改必须 smoke。

### J5 · P2：输入有双入口，且没有统一的鼠标键和屏幕归属判断

**证据：** [Fabric 客户端:148](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/fabric/PondererFabricClient.java:148)每 tick 轮询 GLFW 左键边沿，再调用同一个 handleMouseClick，忽略返回值；[166](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/fabric/PondererFabricClient.java:166)另注册可取消的 allowMouseClick。tick 兜底不能撤销先前 JEI 的配方打开或拖拽。成功路径常因 deactivate 清空 mode 而不再二次提交，**不能夸大成每次必然重复**；无效选择、回调顺序及拖拽仍有组合风险。

[PondererJeiPlugin.java:150](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/PondererJeiPlugin.java:150)没有检查 `button`，并优先使用全局 activeScreen 而非要求它与事件 screen 相同。已有 removed 清理减少了悬挂状态，但回调本身的归属合同仍不完整。NeoForge 本插件点击事件为 HIGH，JEI 本地参考事件为 NORMAL；Fabric 的注册顺序不能直接当成同一保证。

**优化：** 定义一种可取消输入的语义入口；明确允许哪个键直接选择，其余交给 JEI。对 `screen == activeScreen`、目标字段有效、runtime generation 检查后才处理。先验证 Fabric 的事件阶段/顺序再移除兜底；如必须兼容特殊入口，按原始事件去重，不再轮询当前硬件状态来重放操作。输入决策下沉 Common，加载器只做事件转接。

**验收：** 左/右/中键、快速点击、长按拖拽、无效原料、鼠标离开字段、JEI 搜索框焦点、两端回调顺序；一次物理操作最多一次写入，不同时打开配方和提交字段。

### J6 · P1：嵌套容器页面的拖拽目标与实际写入菜单可能不是同一个

**证据：** [PonderUiGhostHandler.java:29](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/PonderUiGhostHandler.java:29)从 `session.currentScreen()` 的 `container.getMenu().slots` 建目标，但 [SessionOps.java:119](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SessionOps.java:119)写的是 `session.menu()`。

[SandboxSession.initNested](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SandboxSession.java:383)会给 `player.containerMenu` 换成嵌套屏菜单，却不替换 session 的 `menu` 字段。第三方子页面若有另一个容器，鼠标指向子菜单第 n 槽，投放却会使用根菜单第 n 槽的规则和内容。不会因此直接写入真实世界，但可能写错沙盒槽位。

此外目标闭包在拖起时保存 index/矩形，accept 没重查当前宿主、页面、模式或布局。SessionOps 已检查 session 可交互和 index，销毁后会拒绝，**不能说它完全没有保护**；仍没有证明 index 属于当前显示菜单。编辑分支还为 inactive 槽位建立目标，而绘制时会跳过 inactive 槽位。

**优化：** 目标携带 session ID、screen/menu 对象身份、槽位对象或稳定键、布局代数。accept 在 scope 内再次验证，并写入同一显示菜单；不支持嵌套菜单时明确禁用该页拖拽。编辑 ghost 可允许显示在输出槽，不应照搬真实 `mayPlace` 限制，但必须排除 inactive/不可见槽位。

**验收：** 嵌套菜单、拖起后切页/切 try-watch/重建 session/调整 GUI 尺寸/切槽位可见性；失效目标拒绝提交，根菜单不被误改。

### J7 · P2：绑定图标每帧全量扫描 JEI 原料

**证据：** [InterfaceSlotOverlayRenderer.java:72](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ui/InterfaceSlotOverlayRenderer.java:72)逐绑定调用 `resolveIngredientById`；[JeiIngredientHelper.java:66](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiIngredientHelper.java:66)枚举类型，[129](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiIngredientHelper.java:129)继续遍历 `getAllIngredients(type)` 找 registry ID。指定 kind 查不到时还会再扫描所有类型。拖着已有绑定时也逐帧解析。

最坏工作量接近 `可见绑定数 × 原料总数 × 帧数`，另有每帧绑定 map 拷贝和 ScreenElement 分配。**遍历已确认，尚无耗时/FPS 基准；不能声称已造成特定帧率下降。**

**优化：** 按 runtime generation 构建 `(type UID, registry ID)` 到候选集的索引，绑定变化时解析并缓存；缓存缺失结果，避免坏 ID 每帧扫表。仅使用 ID 的旧数据需定义候选优先级；完整变体优先用 codec。runtime unavailable、重建、动态原料增删、相关资源重载时失效。不要为加速把不承诺线程安全的 JEI helper 搬到后台线程。

**验收：** 大整合包 1/27/54 个绑定及不存在的 ID；稳定帧不调用 getAllIngredients；测量解析次数、分配和帧耗时，重载后结果更新。

### J8 · P2：类型身份、物品组件和流体数量存在语义损失

**证据：** [JeiIngredientHelper.resolveDescriptor:109](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiIngredientHelper.java:109)只保存 `getResourceLocation + inferKind`；kind 是类名 contains/replace 的启发式结果。[resolveById:66](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiIngredientHelper.java:66)把 preferredKind 当优先项，失败后仍跨类型查找。同 registry ID 的组件变体也只取第一个。

[createFluidScreenElement:43](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiIngredientHelper.java:43)接受 amount 却不使用，也不限制 fluid 类型，实际只按 fluid ID 全局搜索；与 JeiCompat 的注释承诺不一致。字段若只需要 registry ID，这种简化可以合理；用于“还原选中原料”的持久 ghost 时必须另定合同。

**优化：** 区分 ID 字段与完整原料绑定。后者使用 JEI 类型 UID + typed ingredient codec，加入 schema 版本并兼容旧 `{id,kind}`。明确跨加载器类型 UID/流体编码不天然相同，内置 item/fluid 保留规范化适配，未知类型保留原始载荷并显示缺失占位。指定类型缺失时不静默回退成另一类。流体渲染要实际使用 amount，或把 API 改成明确的“代表性图标查询”。

**验收：** 同 ID 的药水/组件变体、同 ID 的不同原料类型、不同流体数量、缺少原料提供模组、旧数据迁移、两端导入。不把类型 UID 字符串自动当成跨平台通用格式。

### J9 · P2：布局避让、悬停命中和显示原料没有共享几何/语义来源

**证据：** [PondererJeiPlugin.java:315](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/PondererJeiPlugin.java:315)只把嵌入菜单矩形作为 PonderUI GUI 范围；PonderUiIngredientHandler 没实现 `getGuiExtraAreas`，Projector 的 extra areas 仅适用于 ProjectorConfigScreen。Ponder 导航按钮、文本面板及沙盒 tabs 没有在这条链路登记避让区。

[PonderUiIngredientHandler.java:26](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/PonderUiIngredientHandler.java:26)忽略传入 mouseX/Y，读取上次 render 的 hoveredSlot，再返回底层真实 ItemStack。该 hover 还可能被脚本 ghost cursor 驱动。显示的是持久 ghost 时，R/U 可能查到被图标覆盖的底层物品；底层空槽的 ghost 则没有可查询原料。上述路径没有使用同一“当前显示的原料”对象。

**优化：** 在有当前 PonderUI/session 时通过公开 `IGlobalGuiHandler.getGuiExtraAreas()` 返回真实占位控件的矩形，其他屏幕返回空集合；避免把整屏都排除。建立单一屏幕坐标快照，供绘制、ghost targets、点击与 R/U 使用。按本次事件坐标命中当前有效槽位，并明确真实鼠标与脚本游标的区别。配方查询优先返回画面实际展示的 typed ghost，否则再取底层物品；尊重高优先级控件遮挡。

**验收：** 左右书签、低分辨率、GUI scale、tabs/文本面板重叠、脚本游标、空槽 ghost、物品及流体 ghost 的 R/U。仅调一个 Z 常量不能修复输入归属。

### J10 · P1/P2：PonderUI 全局深度补丁扩大了 JEI 渲染组合风险

**证据：** [PonderUIMixin.java:135](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:135)在 renderWindow 尾部重绘全部 renderable children；[161](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:161)用 HEAD/RETURN push/pop 抬高整个 renderWidgets；[635](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:635)固定重定向第一个 translate，把 Z 改为 5000。这些不以 JEI、沙盒或本模组场景存在为门槛。原报告 R3 的全局按钮替换也仍在。

JEI 参考实现通过 [GuiEventHandler.onDrawScreenPost](D:/Files/ponderer/ref/1.21.1/JustEnoughItems/Gui/src/main/java/mezz/jei/gui/events/GuiEventHandler.java)绘制 overlay，不是 PonderUI 的 child；因此不能说这里“把 JEI 重画两次”。真正的风险是第三方 children 重绘、不同绘制阶段共享深度/矩阵状态以及视觉顺序与输入顺序不一致。[PonderRuntimeZLayers.JEI_OVERLAY_LAYER](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ui/PonderRuntimeZLayers.java:20)没有调用者，不会自动抬高 JEI。

[JeiIngredientScreenElement.java](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiIngredientScreenElement.java)直接调用第三方 renderer，push/pop 没有 finally；InterfaceSlotOverlayRenderer 也有局部状态恢复缺口。外层有 finally 不足以修复内部多 push 后的栈泄漏。

**优化：** 保留公开 JEI screen/ghost 接口；自有控件只渲染一次。给自有原料 renderer 调用增加局部 finally 和限频失败占位，约定外层 blend/depth/scissor 状态。深度适配约束到真正需要的宿主功能；必要时用可组合的局部参数修改替代独占 Redirect，但先修作用范围。不要新增 JEI drawScreen 的全局抬高 Mixin。

**验收：** 记录每个自有/第三方控件每帧调用次数；普通 Ponder 与嵌入界面均覆盖；异常 renderer 后下一帧矩阵、裁剪和深度恢复；JEI 列表、ghost 高亮、提示与实际命中区域一致。

### J11 · P1/P2：JEI 到 Ponder 的 NBT 提示与打开规则不一致

延续原报告 R5/R8，并已复查两端 6 个 Mixin：

- [NbtSceneFilter.hasVisibleScenes:105](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ponder/NbtSceneFilter.java:105)只看 Ponderer 的 ALL_SCENES，却据此取消整个 Ponder hover 更新；同物品的外部教程可能仍可用。
- HEAD 取消发生在上游 hover 状态清理之前；上一物品的状态可能残留。
- [filter:129](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ponder/NbtSceneFilter.java:129)全空时返回原列表，提示说不可用但打开可重新展示被过滤内容。
- of 的 HEAD/RETURN ThreadLocal 遇异常不清理，嵌套调用结束直接 remove 会丢失外层上下文。

**优化：** 统一“实际候选场景 + 物品组件”的可见性服务，默认保留外部教程；提示、按键打开、标签入口复用。空结果由入口明确处理。用可重入 finally 作用域恢复前值；若需要 MixinExtras 方法包装，先确认两端实际版本支持，不盲目引入。tooltip 热路径使用注册级索引，避免每帧编译场景。保持映射开启的显式 Minecraft 描述符。

**验收：** JEI 与背包中同一个物品得到相同结果；外部教程 + 自定义 NBT 教程、相同 ItemStack 对象组件改变、全空、标签、异常/嵌套打开。两端 refmap/发布命名和 smoke 都要验证。

### J12 · P2：Overlay 强制开启和 runtime 生命周期缺乏所有权

**证据：** [JeiOverlayController.java](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/compat/jei/JeiOverlayController.java)反射 `mezz.jei.common.Internal.getClientToggleState`，使用全局 pushed/previous 布尔状态并吞掉 Throwable。19.21 源码确认当前成员存在，不是已证明反射失效。公开 IIngredientListOverlay 的 `isListDisplayed()` 是查询，不是 setter。

只有槽位编辑 open 会 push；插件 `onRuntimeUnavailable()` 清空 runtime/activeScreen/mode，但没有通知 JeiFieldController 或 overlay 控制器。因此 JEI 重启后，本地按钮仍可能“激活”而插件已无 activeScreen；之前捕获的 overlay 状态也可能应用到另一个 runtime。当前只有一个 push 调用点，嵌套冲突属于未来扩展风险，不能当作已发生问题。

**优化：** 分开“模组已安装”和“runtime 可用”；runtime generation 统一使选择会话、图标缓存及 overlay lease 失效。默认尊重用户显隐设置，可提示实际 JEI 快捷键。必须临时开启时，将内部反射收口成版本能力适配器，记录原状态与所属 runtime，只恢复自己改过且用户未再次变更的状态，失败只诊断一次。不要用猜测的公开 setter 替代，也不要因返回 null 就给 JEI 内部类加 Mixin。

**验收：** JEI 未安装、已安装但 runtime 未就绪、runtime unavailable/available、用户主动切换 overlay、异常退出编辑；按钮、显隐和缓存状态一致。

### J13 · P3：减少重复实现与映射合同隐患

- `StepEditorGhostHandler` 的实例 handler 没有注册，实际只复用其静态 ID 解析；将解析移至独立 resolver，删除重复 handler，避免两个规则继续分叉。
- `InterfaceSlotGhostHandler` 当前实际由 PonderUiGhostHandler 调用静态 createTargets；没有独立 `new InterfaceSlotGhostHandler` 注册。可以简化为目标工厂，不要把它当作另一组已注册 handler。
- `eventRegistered` 只设置布尔值，没有执行事件注册；注释仍写 Forge。清理这类误导状态。
- `JEI_OVERLAY_LAYER`、InterfaceSlotEditState 的 viewport capture/get 方法、InterfaceSlotOverlayRenderer.renderTooltip 在当前源码中没有外部调用者；确认外部 API 承诺后清理，或接入明确的渲染/查询路径。
- PonderUIMixin 的 `removed` 与 `init` 有外层 `remap=false` 的钩子，虽共享 refmap 内已存在的正确映射，仍应按仓库规则规范化；稳定的 `PonderIndex.editingModeActive()` 内层调用可独立保持 false。不要机械地把所有三方方法都改 true 或 false。
- PonderUIAccessor 的 scenes/index/lazyIndex 桥在槽位编辑中有真实消费者，优先封装导航不变量；getHoveredBlockPos 的 Minecraft 类型映射要核查，但已有成品证据不支持宣布失效。

**验收：** 静态引用扫描、编译、两份注册清单唯一解析；映射修改检查 Fabric 发布 JAR/refmap 和 NeoForge runtime 命名，并执行 quickPlaySmoke。

## 4. 全部 JEI 集成类的处置表

| 文件（均在 Common 的 compat/jei） | 处置 |
|---|---|
| PondererJeiPlugin | 保留公开 API 接入；J2/J3/J5/J9/J12 |
| JeiCompat | 保留无 JEI 时的守卫入口；区分 installed/runtimeReady；修正文档与流体实现不一致 |
| JeiAwareGhostHandler | 保留通用 handler；统一选择解析、screen/字段归属及目标生命周期 |
| StepEditorGhostHandler | 抽出 resolver；清理未使用的实例 handler |
| PonderUiGhostHandler | J1/J6；查询进 scope，目标绑定显示菜单及会话 |
| InterfaceSlotGhostHandler | 简化为目标工厂；过滤 inactive，验收 accept 时身份 |
| PonderUiIngredientHandler | J9；统一显示原料与 R/U，公开 API 提供避让区 |
| JeiIngredientHelper | J7/J8；类型索引、缓存、codec 与兼容旧数据 |
| JeiIngredientScreenElement | J10/J12；局部状态恢复、renderer 异常、runtime 代数 |
| JeiOverlayController | J12；优先用户设置，必要反射收口且有生命周期 |

## 5. 实施顺序与验证

### 阶段 A：先修可确认的正确性问题

1. J1 查询进入沙盒并前置保护能力；J6 使用显示菜单的身份提交。
2. J2 修通用原料分支；J3 修注册覆盖；共享选择解析。
3. J4 区分配方暂挂与真正退出；J5 定义单输入入口与归属。
4. J11 统一 NBT 可见性与上下文生命周期。

### 阶段 B：性能与组合行为

1. J7 建索引、缓存及失效机制；先测基线再比较。
2. J9 统一几何、显示原料、避让区与命中策略。
3. J10 收窄深度补丁并保证自有渲染状态恢复。
4. J8 完整原料身份与旧格式迁移；J12 runtime/overlay 生命周期。

### 阶段 C：清理与上游扩展

清理 J13；将可共用的 NBT 薄入口迁 Common 前先确认工具链。向 Ponder 寻求导航/生命周期扩展，向 JEI 寻求明确的临时 overlay 能力，仅在确有缺口时讨论内部兼容适配。不要以增加 priority、require=0 或换一种注入注解代替作用范围修复。

### 本次已完成的验证

- 全模块 @Mixin/注册扫描及唯一解析；10 个 JEI 类枚举；源文件和证据指纹保存。
- 对照本地 JEI/Ponder 源码与 19.21 官方源码包；对安装的 19.42 API 检查公开签名。
- 检查已有 Fabric refmap 和本模组发布类描述符；检查两端实例 JAR 版本及版本日志。
- 报告的本地文件链接、行号有效性、清单计数与源码指纹检查。

### 修改后的最小验证

从 `D:/Files/ponderer` 执行，使用仓库 JDK 21（当前普通 PATH 的 java 指向 Java 8，不能直接假定环境正确）：

```powershell
$env:JAVA_HOME = 'C:\Users\Nobodiiiii\.jdks\ms-21.0.9'
.\gradlew.bat :NeoForge:build :Fabric:build --console=plain
```

修改映射时另做最小编译及生成物检查：

```powershell
.\gradlew.bat :Common:compileJava :NeoForge:compileJava :Fabric:compileJava --console=plain
.\gradlew.bat quickPlaySmoke -PsmokeTimeout=180 -PsmokeInterfaces --console=plain
```

quickPlaySmoke 的 target 是 `both`，脚本负责版本分支适配；已有超时与进程清理。实施时构建也应设置合理整体时限。**任意 Mixin 修改必须 smoke；但现有 InterfaceSmokeTest 没有 JEI handler/ghost/配方交互的专项断言，PASS 不能替代下表。** 本次文档没有修改运行实现，所以未运行这些命令。

Common 显式启用了 JUnit，但当前没有 JEI 专项测试。抽出 resolver、类型/缓存/会话纯逻辑后可补必要用例，并运行 `:Common:test`；现有接口模型测试不证明 JEI 交互正确。GL/真实事件时序应使用受控客户端场景，不以 mock 测试替代。

| 必须补的场景 | 关键断言 |
|---|---|
| JEI 无/未就绪/重启；19.21 基线 + 两端实际运行版本 | 没有客户端可选类加载错误；能力、按钮、缓存同步 |
| 所有 JeiAware 编辑器，含 TriggerEditor | 开关、点击、drag、窗口变化与目标字段一致 |
| 物品/流体/化学品，列表/书签，左右中键 | 类型规则一致；单操作单次提交 |
| 槽位编辑 → R/U → 返回、嵌套配方、真正退出 | 未提交编辑不丢；取消/提交行为明确 |
| 根/嵌套容器，inactive 槽，拖拽中切页/改模式 | 不写错菜单；失效目标拒绝 |
| mayPlace 等查询有副作用/抛异常，缺 guard | 悬停也在隔离内；真实世界/网络无变化 |
| ghost 与底层物品不同、脚本游标、控件遮挡 | 显示、命中、R/U 一致 |
| 大原料库、多绑定、坏 ID、runtime 重载 | 稳定帧不全量扫描；缓存不过期误用 |
| JEI + 普通 Ponder/嵌入界面 + 异常 renderer | 无重复控件绘制；状态恢复；输入与视觉顺序一致 |
| 外部教程共存、NBT 空结果/嵌套/异常打开 | 提示/打开一致，外部内容保留，ThreadLocal 不泄漏 |

交付边界：所有建议尚未实施；没有新跑构建、游戏交互、性能基准或 19.42 内部事件时序测试。下一步应优先实现 J1/J6 的隔离与菜单归属、J2/J3 的确定性功能修复，然后处理 J4 的编辑状态保存。
