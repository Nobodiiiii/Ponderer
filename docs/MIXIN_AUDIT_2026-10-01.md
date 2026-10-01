# Ponderer 全量 Mixin 审查报告

审查日期：2026-10-01（Asia/Shanghai）。范围：当前工作区，包括未提交的接口捕获、沙盒桥接代码；本次只新增审计文档，没有修改模组实现。

**结论：优先降低“改变整个第三方流程”的侵入性，再减少 Mixin 数量。** 当前风险集中在完整替换世界 tick、全局改变 PonderUI 渲染与按钮行为、沙盒隔离失效后的降级策略，以及 NBT 筛选影响其他模组教程。只读 Accessor 大多不是主要矛盾；将 Redirect 换成另一种注入，也不能自动解决作用范围过大的问题。

## 1. 范围、证据与可信度

### 1.1 实际启用的模块

[settings.gradle](D:/Files/ponderer/settings.gradle) 当前包含 **Common、NeoForge、Fabric**；[gradle.properties](D:/Files/ponderer/gradle.properties) 为 Minecraft **1.21.1**、Ponderer **1.10.6.1**、编译依赖 Ponder **1.0.69**、NeoForge **21.1.219**。未将历史 Forge 目录当作有效模块。

审查开始时两个加载器各注册 30 个 Mixin；期间其他工作新增 `InterfaceItemCaptureMixin`，已补审，并以最终清单为准：

| 指标 | Common | NeoForge | Fabric | 合计/解释 |
|---|---:|---:|---:|---|
| Mixin 源文件 | 24 | 8 | 7 | **39 个文件**，含 Accessor |
| 实际注册的源文件 | 24 | 7 | 7 | **38 个**；Common 在两端复用 |
| 每端客户端注册数 | — | 31 | 31 | 62 条注册，不是 62 个独立实现 |
| `@Inject` | 41 | 19 | 21 | 81；其中 7 个属于未注册的 NeoForge 类 |
| `@Redirect` | 5 | 1 | 0 | 6 |
| `@Accessor` / `@Invoker` | 30 / 1 | 0 | 0 | 31 个访问桥 |
| `@Overwrite` | 0 | 0 | 0 | 不代表没有覆盖式行为，见 R1、R3、R11 |

两份配置都是 `required=true`、`defaultRequire=1`，全部位于 `client`，`mixins` 与 `server` 为空。没有发现配置条目重复或找不到对应源码。NeoForge 的 `AnimationTickHolderMixin` 存在于源码，但**未注册、不会生效**。

完整路径、注解数量、注册归属、源码 SHA-256、产物与日志指纹见 [机器可读清单](D:/Files/ponderer/docs/MIXIN_AUDIT_2026-10-01.inventory.json)。清单记录的是审查快照，不能替代后续构建的来源证明。

### 1.2 上游与产物检查

- 先阅读 [ref/SOURCES.md](D:/Files/ponderer/ref/SOURCES.md)，对照 `ref/1.21.1/Ponder/common/` 与 `neoforge/` 的源码。该目录标注 `mc1.21.1/dev`，是对应 MC 版本的开发线，**不是已证明与 1.0.69 或 1.0.82 完全一致的发行版快照**。
- 参考目录没有独立可确认的 Git 根；在该目录执行 Git 实际回到 Ponderer 根仓库。因此本报告没有把根仓库 HEAD 冒充 Ponder 上游提交。
- 检查了 [Fabric 生成 refmap](D:/Files/ponderer/Fabric/build/classes/java/main/ponderer.refmap.json)，并用 `javap -p -v` 检查本模组 Fabric 成品 JAR 的注解和描述符。没有反编译第三方依赖，也没有使用网页作为依赖行为依据。
- NeoForge 当前配置没有 refmap 字段，构建目录没有查到本模组 refmap。1.21.1 NeoForge 的运行命名体系与旧 Forge 1.20.1 不同，**不能仅据此判定漏配或要求生成 `m_*` 名字**；应验证发布 JAR 的实际命名与运行注入。

### 1.3 已有运行证据与边界

按 [minecraft-crash-triage 技能](C:/Users/Nobodiiiii/.codex/skills/minecraft-crash-triage/SKILL.md) 检查仓库内版本日志。检查期间日志也发生更新，最终保存的证据为：

| 日志 | 可确认事实 | 不能据此确认的事项 |
|---|---|---|
| [Fabric latest.log](D:/Files/ponderer/.minecraft/versions/1.21.1-Fabric/logs/latest.log:242) | Ponder 1.0.69；`interfaces PASS (intercepted=7, taintDrops=0)` | 所有可选注入均命中、NBT 过滤、所有 PonderUI 路径、第三方扩展菜单均兼容 |
| [NeoForge latest.log](D:/Files/ponderer/.minecraft/versions/1.21.1-NeoForge/logs/latest.log:557) | 实际 NeoForge 21.1.244、Ponder 1.0.82+mc1.21.1，安装 Create 6.0.10；同样有接口测试 PASS | 编译基线 21.1.219 的全部行为、未注册的时钟钩子有效、全部渲染组合有效 |

日志没有给出可归因于本次审查 Mixin 的已复现注入崩溃。Fabric 的 Yggdrasil 公钥请求超时，以及 NeoForge 的 Curios 槽位错误、其他模组的可选类/refmap 警告，不能归因于 Ponderer。仓库根 `.minecraft/crash-reports/` 不存在；因已有版本日志，没有以根 `latest.log` 替代。

**本次未重新运行 Gradle 或启动游戏。** 这是文档审计，依据仓库规则无需构建；上面的 PASS 来自检查到的已有运行，不是本轮重新执行的验证。本文分别标注“已确认代码行为”“条件性缺陷”“待运行验证”，不把静态风险写成已发生事故。

## 2. 优先处理的问题

优先级含义：P1 为应优先处理的行为/兼容性问题；P2 为有条件触发的可靠性问题与可控重构；P3 为清理和长期 API 改善。没有证据支持将任何项目评为无条件启动失败的 P0。

### R1 · P1：完整替换所有 Ponder 世界的 tick

**位置：** [PonderLevelMixin.java:25](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderLevelMixin.java:25)。

`HEAD + cancellable` 无条件执行自建循环并取消原方法，覆盖所有命名空间的 PonderLevel。目的合理：实体 tick 期间修改实体表时，快照迭代可避免原始迭代器失效，`finally` 也能恢复 `currentlyTickingEntities`。但实现复制了粒子更新、位置记录、tickCount、实体 tick、坠落销毁和死亡清理等整个流程。

上游 [PonderLevel.tick](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/api/level/PonderLevel.java:208) 原来用迭代器逐个删除；本实现改成最后统一 `removeIf`。后续实体观察到的列表时机也发生变化。上游新增逻辑或其他模组位于原方法内部/RETURN 的钩子，不能指望仍然执行。这是**已确认的全局语义替换**；具体模组冲突尚未复现。

**建议：** 先把修复约束到明确需要它的 Ponderer 场景/实体行为；优先在本模组生成实体的入口延迟增删，到 tick 结束再提交，保留上游循环。若要支持任意第三方实体在 tick 内增删，应推动上游实现统一的安全遍历/变更队列，而不是盲目删除现有保护。过渡补丁也必须保留上游主体和 `finally` 恢复，不能只把 `iterator()` 换成快照迭代器而忘记删除原列表中的实体。

**验收：** 实体自删、新增实体、删除其他实体、嵌套调用、tick 抛异常；核对每个实体 tick 次数及清理时机；普通 Create 教程中的实体和粒子保持原行为。

### R2 · P1：沙盒保护缺失时，检查发生在第三方代码执行之后

**位置：** [SandboxSession.java:132](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SandboxSession.java:132)、[SandboxNetworkFirewallMixin.java:21](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxNetworkFirewallMixin.java:21)、[SandboxScreenGateMixin.java:19](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxScreenGateMixin.java:19)。

网络和换屏钩子都是 `require=0`，注释承诺缺失时只读。但 `build()` 先 `dispatch("open")` 调用菜单/界面代码，再检查 `isLive()`。降为 READ_ONLY 后仍会执行第三方 `render()`、`tick()`；它们也可能发包或换屏。因此，**关闭用户输入不能代替隔离条件**。当任一钩子缺失时，首次构造和之后的只读绘制依然可能触及真实连接/宿主界面。这是有明确触发条件的实现缺口，现有 PASS 并没有覆盖“钩子故意缺失”。

**建议：** 在首次调用任何第三方构造、初始化、tick、render 前检查所有必需隔离能力。缺失时显示本模组自己的说明/快照占位，不执行第三方 UI。区分“隔离完整但交互能力不足的只读模式”和“隔离不可用的静态模式”。世界桥、客户端写入拦截、区块桥也应加入能力合同。启动期可使用不触发真实网络操作的探测，或在 Mixin 应用后登记能力；单纯配置开关不算应用成功证明。

**验收：** 分别禁用网络、换屏和世界桥中的一个钩子；菜单构造、init、render、tick 均不得运行。网络记录不得新增真实包，宿主 screen/player/gameMode 必须保持原对象。

### R3 · P1：PonderUI 全局渲染改写与原生按钮替换

**位置：** [PonderUIMixin.java:61](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:61)、[135](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:135)、[161](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:161)、[635](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:635)。

已确认的行为：

- 在判断是否为 Ponderer 动态场景之前，替换 `slowMode` 回调。原生 [慢速阅读按钮](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/ui/PonderUI.java:389) 切换 comfy reading，现在切换文本面板，影响 Create 等其他教程，且原图标/提示仍描述旧功能。
- `renderWindow` 尾部遍历全部 `children()` 并再次调用可渲染子项。上游 [AbstractSimiScreen.render](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/catnip/gui/AbstractSimiScreen.java:96) 随后还会正常遍历 renderables。两个集合交集内的控件会重复绘制，包含第三方添加的控件；即使第二次被深度遮挡，绘制调用及其副作用仍发生。
- 固定把首个 `PoseStack.translate(FFF)` 的 Z 改成 5000，并把整个 `renderWidgets` 抬高。注释依据包含 1.20.1 投影参数，当前却是 1.21.1；固定 ordinal 和深度数值绑定了上游内部实现。
- `renderWidgets` 用 HEAD push / RETURN pop 成对管理 PoseStack。异常没有 `finally` 保证；其他取消式注入的组合也需要单独验证。

**建议：** 为文本面板增加独立按钮，保留 comfy reading；通过已有 Screen 事件为本模组控件安排单次渲染。把深度补丁约束到拥有相关视图特性的场景或独立预览界面，不重绘任意第三方 children。确需调整原生 3D 深度时，优先对具体参数使用可组合的参数/表达式修改，并限定语义范围；`ordinal=0` 不能独立充当语义定位。离屏目标可作为后续隔离方案，但要实测缓冲刷新、光照、拾取与性能，不能承诺简单替换即可无回归。

**验收：** 原生慢速阅读、第三方按钮每帧仅渲染一次、UI 缩放、场景切换动画、极端缩放、Iris/Sodium 环境、故意抛异常后的下一帧状态。

### R4 · P1：镜头正向与逆向变换不一致，两端缩放策略不同

**位置：** [NeoForge SceneTransformOffsetMixin.java:33](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/SceneTransformOffsetMixin.java:33)、[Fabric SceneTransformOffsetMixinFabric.java:31](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/SceneTransformOffsetMixinFabric.java:31)。

两端都在 `apply` 末尾增加位移；上游 [screenToScene](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/PonderScene.java:595) 手工反算旋转、缩放和底板平移，没有反算新增位移。[sceneToScreen](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/PonderScene.java:620) 则经 `apply` 生成矩阵。新增视图偏移非零时，两者不再互逆；会影响射线、相机位置及第三方坐标转换。拾取期间跳过部分视图指令只是缓解，不能修复通用转换合同。

NeoForge 在调用处替换 scale；Fabric 则在双参数 `apply` 的 **TAIL** 写入 `scaleFactor`。上游实际绘制调用的是双参数版本，缩放此时已使用旧值。Fabric 写入对象字段后不在本次调用结束恢复，而等以后禁用 override 再恢复，可能和 [AdjustViewInstruction](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ponder/AdjustViewInstruction.java:147) 的字段同步相互覆盖。NeoForge 的渲染插值值与逆变换读取的字段值也可能不同。

**建议：** 在 Common 定义唯一的有效视图状态与正/逆变换，包含平移、插值缩放、缓存失效。两端使用同一策略，避免“Fabric 持久写字段、NeoForge 局部改调用”。短期修改特定缩放参数；逆转换同步扣除新增位移并使用同一插值缩放。长期向 Ponder 提供 view transform 扩展点。重入状态不能只用一个实例字段保存 partial tick。

**验收：** 两端对同一场景，验证带深度信息的正反坐标往返；非零平移、非 1 缩放、部分 tick、重播、切场景、嵌套矩阵计算均覆盖。视觉位置与拾取坐标必须一致。

### R5 · P1：NBT 提示过滤可能隐藏其他模组仍然可用的教程

**位置：** [PonderTooltipNbtMixin.java:34](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderTooltipNbtMixin.java:34)、[Fabric 对应实现](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderTooltipNbtMixinFabric.java:26)、[NbtSceneFilter.java:105](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ponder/NbtSceneFilter.java:105)。

`hasVisibleScenes` 只查看 Ponderer 自己登记的 `ALL_SCENES`，而注入取消的是整个 Ponder `updateHovered(stack)`。例如同一物品既有 Create 教程，又有 Ponderer 的 NBT 限定教程：Ponderer 条件不匹配时，本实现会取消整个提示更新，即使 Create 教程仍应可用。

另外，取消发生在上游 [updateHovered](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/PonderTooltipHandler.java:107) 清理 `hoveredStack`、`subject` 等状态之前；条件变化时可能遗留上一帧跟踪状态。`filter()` 又在结果全空时返回原列表，导致“隐藏提示”和“直接打开时仍显示被过滤教程”的规则不同。这些是可由代码构造的条件性缺陷，尚未在游戏内逐一复现。

**建议：** 对实际候选教程列表定义统一可见性策略，默认保留非 Ponderer 教程；提示、打开界面、按键入口均复用结果。只修改资格判断，不在清理之前取消整条状态机；如果没有足够上游信息证明“全部教程不可见”，优先保留提示。不要在 tooltip 每帧编译完整场景，应缓存注册层面的可见性信息并随资源/脚本重载失效。空结果由调用入口显式处理，不回退展示被过滤内容。

**验收：** 同物品的外部教程与本模组教程共存；同一 ItemStack 对象组件变化；标签入口；全空结果；提示进度重置；直接打开与提示结果一致。

### R6 · P1：结构加载的 fallback 实际覆盖其他命名空间

**位置：** [NeoForge PonderSceneRegistryMixin.java:92](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryMixin.java:92)、[Fabric 对应实现:89](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryMixinFabric.java:89)。

`loadSchematic` 的 HEAD 钩子会先读取单人世界 `generated/<namespace>/structures/<path>.nbt`，读取成功就取消上游。它并没有先检查 Ponder 正常资源是否存在。因此只要存在同名生成结构，就会抢在 Create/资源包的 `assets/<namespace>/ponder/<path>.nbt` 前生效。用户在世界里保存一个同名结构即可改变另一模组教程。

**建议：** 默认只处理 `ponderer` 命名空间；跨命名空间导入使用显式授权的资源 ID 映射。确需 fallback 时，应先查询传入 ResourceManager 的 `ponder/<path>.nbt` 是否存在，存在就完全交回上游。不要用“返回的结构为空”推断资源缺失，合法结构也可能为空。中期将动态结构发布为受控虚拟资源包，服从明确的资源优先级。加载业务与缓存放 Common，薄注入只转交结果。

**验收：** 本地生成结构与外部资源同 ID、资源包覆盖、合法空结构、坏 NBT、专用服务器连接下无单人存档路径、资源重载。

### R7 · P2：映射策略不一致，Fabric 单参数镜头钩子存在成品证据

成品检查发现：Fabric `SceneTransformOffsetMixinFabric` 单参数 `apply` 的三个钩子仍保留 `Lcom/mojang/blaze3d/vertex/PoseStack;` 字符串、`remap=false`，而 handler 参数已经是 `net.minecraft.class_4587`；refmap 只有双参数 `apply` 条目。**这是真实产物中的选择器/运行描述符不一致**。因 `require=0`，表现可能是静默失效而不是启动失败。删除这些旧兼容钩子或按正确映射补齐，比继续依赖“一参先调用二参”更可靠。

还需规范化：

- [PonderUIMixin.java:228](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:228) 的 `removed` 和 [290](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:290) 的 `init` 外层 `remap=false`，引用的是继承/覆盖 Minecraft 的方法名。应恢复外层映射，仅对稳定的 `PonderIndex.editingModeActive()` 调用目标保持 `remap=false`。
- 同一个类另有 `init`、`removed` 正确的 refmap 条目。因此不能只看某个 `remap=false` 就断言该钩子已经失败；Mixin 可共用选择器映射。问题是注解合同不一致、移除另一钩子后可能失去偶然依赖。
- `PonderLocalization` 的方法参数含 ResourceLocation；InputWindow 的 sceneSpace/key/item、PonderUI 的 hoveredBlockPos、PonderKeybinds 的 mapping、TextWindow 的 vec 等访问桥含映射类型，应按仓库规则检查。成品 InputWindow Accessor 的类型已正确变成 intermediary，**目前没有证据支持把所有这类 Accessor 判为坏掉**。
- `renderWindow`/`renderWidgets` 等自有方法的裸名字，以及仅涉及稳定 Ponder 类型的调用目标，应与显式包含 Minecraft 类型的描述符区别处理；不要机械地全改 true 或全改 false。
- `RenderSystem.shaderLightDirections` 的字段类型是 JOML；保留与否以实际工具链映射为依据，不仅看包名。

**验收：** 按选择器建立源码、refmap、成品注解、运行命中四层对应表。发布版 Fabric 必须验证 intermediary 描述符；NeoForge 验证实际 runtime 名称，不照搬旧 Forge 检查标准。每次相关修改均执行两端编译和 `quickPlaySmoke`。

### R8 · P2：NBT 的 ThreadLocal 使用 HEAD/RETURN 管理，不能覆盖异常与重入

**位置：** [NeoForge PonderUINbtMixin.java:19](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderUINbtMixin.java:19)、[Fabric 对应实现](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderUINbtMixinFabric.java:15)、[NbtSceneFilter.java:43](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ponder/NbtSceneFilter.java:43)。

`PonderUI.of()` 异常退出时不经过 RETURN，物品上下文会留在线程上；嵌套打开内层会覆盖外层，再直接 remove，无法恢复之前的值。并且 registry `compile(ResourceLocation)` 只是一个入口，不能保证后续标签、重建、集合入口与物品打开路径有同一筛选策略。

**建议：** 使用可重入、`try/finally` 恢复 previous 值的作用域；优先把过滤靠近本模组控制的打开入口，保留对外部 `PonderUI.of(ItemStack)` 的兼容时使用完整方法作用域包装。可参考本项目 [ProjectorRenderContext.runWithFrameTime](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/projector/client/ProjectorRenderContext.java:56) 已有的恢复模式。计划采用 MixinExtras 的 WrapMethod/WrapOperation 前，先验证两端实际打包版本和功能支持，不能假定工具链自带所需版本。

**验收：** 构造抛异常、内外两层物品不同、嵌套完成后外层继续 compile、同线程随后打开非物品入口，均不泄漏上下文。

### R9 · P2：世界桥与网络隔离的边界应明确，不能靠继续扩大原版拦截解决

**位置：** [SandboxWorldBridge.java:13](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SandboxWorldBridge.java:13)、[SandboxNetworkFirewall.java:47](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/interfaces/sandbox/SandboxNetworkFirewall.java:47)。

已有良好限制：世界桥要求 scope 激活、渲染线程、receiver 恰好为 `mc.level` 或其 chunk source；不会无条件把所有 Level 改成沙盒。ClientLevel.setBlock 提前拦截可避免进入预测处理；SandboxScope 用 AutoCloseable 恢复 player/gameMode。

剩余边界：

- scope 内直接访问 `mc.level` 被改向沙盒，但提前缓存的实体/区块、其他世界方法、异步任务不自动受此保护。客户端实体查询统一返回 null、setBlockEntity 直接吞掉，说明它是有限兼容层，不是完整 ClientLevel 实现。
- `getChunk` 未根据 ChunkStatus 提供完整契约；自建 LevelChunk 只重写部分访问方法。直接读 section、实体表或模型数据的界面可能得到不一致结果。
- scope 外把所有连接上 101–127 的特定菜单包视为沙盒包；这是基于原版窗口 ID 分配的约定，并非与其他模组协商过的专用空间。真实模组容器若使用该区间也会被拦。
- 任意异步自定义 payload 没有通用窗口 ID，scope 外不会被这张名单识别；取消 send 也没有执行 PacketSendListener 的完成语义，依赖回调的界面需要单独适配。

**建议：** 以会话、连接身份及已分配窗口记录判断归属；延迟任务只允许经过显式、可撤销的会话调度桥。对依赖 ClientLevel 内部结构或异步真实网络的 UI 提供适配器/快照展示。已由显式 SandboxLevel 参数支持的接口，尽量走对象级实现；全局桥只作为受控兼容通道。不要为每个新模组继续无上限增加 Level 核心方法拦截。

**验收：** scope 外真实容器、多个会话、旧窗口延迟包、第三方自定义 payload、真实连接切换、直接区块访问、GUI 异步任务。性能只应通过采样判断；目前没有数据支持声称桥已造成可测帧率下降。

### R10 · P2：可选注入缺少成组能力验证，配置与实现发生漂移

`require=0` 可用于支持不同版本的可选签名，但现在源码中共 46 处，39 处属于已注册类。关键功能缺一部分时仍可能继续运行。双重 `loadSchematic`、NBT 的 capture/filter/clear、camera 的 capture/scale/translate 必须按**完整功能组**验收，而不只是单个注入允许失败。

NeoForge [AnimationTickHolderMixin.java](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/AnimationTickHolderMixin.java) 未注册，Fabric 已注册。两端共同调用投影仪帧时间作用域，并不代表 NeoForge 实际读取了这个时钟覆盖。是否应补注册需先确认移除原因；不能直接把未注册视为应立即启用。

此外，NeoForge [模组元数据](D:/Files/ponderer/NeoForge/src/main/templates/META-INF/neoforge.mods.toml) 接受很宽的 Ponder 版本，Fabric 的依赖声明未显式给出 Ponder 合同，主要依靠嵌入。对私有字段和具体调用序列的假设应有对应的已验证版本范围；不要用随意固定上限来阻止 Create 正常选用较新 Ponder。

**建议：** 为变体签名用适当的注入分组约束“至少一个受支持入口”；为功能组建立一次性状态诊断和明确降级。纯可选 UI 能力失败可隐藏功能；沙盒保护失败按 R2 禁止执行第三方代码。基础必须钩子继续 fail-fast，不把整个 JSON 改为 `required=false`。用构建检查防止孤立源码/遗漏注册，保留有意未注册项的原因。

### R11 · P2：未注解的方法合并与全局修补，长期冲突成本高

**位置：** [PonderUIMixin.keyPressed](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java:443)、[PonderLevelModelDataMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderLevelModelDataMixin.java:11)、[WorldSectionElementImplMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/WorldSectionElementImplMixin.java:25)。

`keyPressed` 和 `getModelData` 都通过普通方法合并进入目标类；上游将来增加同签名实现、或另一个模组也注入该方法时，兼容性不如明确的调用合同。不能把这类语义覆盖漏掉，仅以“没有 @Overwrite”评价侵入性。`keyPressed` 的最终 super 调用还受 Mixin 层级重绑定影响，单看源码继承 Screen 不足以断言它已经绕过所有 Catnip 父类；应检查应用后的类并测试 Backspace、背包键、控件键盘导航。

WorldSection 的空 selection 防护取消 add/erase，能避免空指针，但全局静默吞掉 add 也会隐藏脚本顺序问题或抢在其他修复之前返回。本项目 [DynamicPonderPlugin.java:1816](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/ponder/DynamicPonderPlugin.java:1816) 已有通过公开 API 初始化 section 的局部修复，可把同样不变量扩展到本模组所有 add/erase 调用点。

**建议：** ESC 处理迁到已有 Screen 事件或自有预览界面；必须保留原生导航链。ModelData 先保留必要能力，推动 Ponder/SchematicLevel 使用公开模型数据接口，或对确定的渲染调用增加局部适配，不能用 `@Unique` 修改 API 方法名来掩盖冲突。WorldSection 在自有脚本编译层校验/修复，再评估删除全局补丁。

### R12 · P2/P3：本地化、捕获与 Accessor 可进一步收敛

- **本地化：** [PonderLocalizationMixin.java:43](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderLocalizationMixin.java:43) 仅在 ponderer 命名空间且不处于 editing mode 时覆盖，这是好的边界。但使用裸方法名试图处理多个重载，handler 不接收格式化参数；若覆盖了格式化调用，会直接返回原模板。上游确有带 `Object...` 的 [getShared/getSpecific](D:/Files/ponderer/ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/registration/PonderLocalization.java:78)。应按完整签名分开处理并保留格式化/资源语言优先级；当前没有证据确认所有重载均命中，也不据此断言必然 InvalidInjectionException。
- **捕获：** Interface 系列只在显式观察时记录，不取消实际菜单创建；NeoForge 按 readerIndex 复制而不推进缓冲，Fabric 有大小限制、异常拒绝和 finally release，方向正确。当前已补 `InterfaceItemCaptureMixin` 记录真实 hand，旧的“只凭主手是否为空猜副手”不再作为单独未修复问题。仍应区分预观察与成功创建，关联返回菜单对象/窗口/世界与本次交互；固定等 20 tick 不等于收到完整模组状态。Fabric 为捕获重跑 codec.encode 有可变对象/有副作用 codec 风险，失败时仅放弃捕获，不能破坏正常打开。
- **冗余事件：** NeoForge 已有 [滚轮/鼠标事件处理](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/neoforge/PondererNeoForgeClient.java:142)，两端已有 client tick 事件。迁走 InterfaceCapture 的 tick、集中输入分发可减少核心 Minecraft 注入。不能直接用 END_CLIENT_TICK 替代必须早于原版消费的 keyPickItem 拦截；保持时序并测试按键重映射。现有取消可能阻止重复路径，不能仅因两处处理就宣称一次输入必然执行两次。
- **Accessor：** PonderScene 的四个只读字段已有公开 getter，两个 basePlate offset setter 在生产源码没有调用者，可删除；通常只需保留 scaleFactor 写桥。TextWindow 的 palette 已有 `getPalette()`。PonderKeybinds 当前访问者确实需要 KeyMapping 以编辑绑定，可考虑从公开 `register(Consumer<KeyMapping>)` 收集引用或向上游申请 getter，不能只用 `isDown()` 代替不同用途。
- **渲染状态恢复：** RenderSystem Accessor 本身侵入性低，使用处深复制两个 Vector3f 是正确做法；但 [物品渲染后的灯光恢复](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/projector/client/ProjectorBlockEntityRenderer.java:1193) 没有由该局部 `finally` 保证。应把灯光、pose 和深度范围恢复纳入同一作用域，而不是仅删除 Accessor。

## 3. 逐类完整清单

表中风险是相对侵入性与维护优先级，不代表每项都有已复现故障。`H/R/T` 分别为 HEAD / RETURN / TAIL；以下 39 个文件均已检查。

### 3.1 Common：24 个，两端共用

| 文件 | 目标与钩子 | 风险 / 建议 |
|---|---|---|
| [PonderUIMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIMixin.java) | PonderUI；init、renderWindow、renderWidgets、replay、scroll、mouseScrolled、renderHoverTooltips、removed、tick、mouseClicked；4 个 editingMode Redirect、1 个 translate Redirect；keyPressed 方法合并 | **高**。R3/R7/R11；拆开 UI 扩展、拾取、深度适配，业务下沉普通类 |
| [PonderLevelMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderLevelMixin.java) | PonderLevel.tick H，完全取消；2 个 Shadow | **高**。R1；从全局复制实现转向局部实体不变量/上游修复 |
| [SandboxWorldBridgeMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxWorldBridgeMixin.java) | Level 的 8 个读写/区块/销毁方法，H 可取消 | **高**。R2/R9；已有线程、scope、receiver 限制，进一步明确能力边界 |
| [SandboxClientLevelMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxClientLevelMixin.java) | ClientLevel.setBlock/getEntity H 可取消 | **中高**。保留预测前写拦截；实体 UI 需要适配，不默认假装支持 |
| [SandboxChunkCacheMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxChunkCacheMixin.java) | ClientChunkCache.getChunk H 可取消 | **中高**。R9；验证区块契约与绕过入口；避免无限扩张全局桥 |
| [SandboxNetworkFirewallMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxNetworkFirewallMixin.java) | Connection.send(Packet, listener, boolean) H 可取消，require=0 | **高**。R2/R9；隔离必需能力，连接归属与延迟包策略需改进 |
| [SandboxScreenGateMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/SandboxScreenGateMixin.java) | Minecraft.setScreen H 可取消，require=0 | **中高**。保护宿主合理；缺失时禁止第三方 UI 执行 |
| [InterfaceCaptureMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/InterfaceCaptureMixin.java) | Minecraft.startUseItem H、tick T；不取消 | **低中**。保留必要的交互前观察；tick 可迁事件 |
| [InterfaceItemCaptureMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/InterfaceItemCaptureMixin.java) | MultiPlayerGameMode.useItem/useItemOn H；不取消 | **低中**。正确使用真实 hand；补显式签名、成功交互关联 |
| [InterfaceMenuCaptureMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/InterfaceMenuCaptureMixin.java) | MenuType.create(int, Inventory) H；不取消 | **低中**。预观察不能等同成功创建；排除无关窗口 |
| [MinecraftNbtPickMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/MinecraftNbtPickMixin.java) | Minecraft.handleKeybinds H、require=0，消费 keyPickItem 点击 | **中**。消费队列本身改变原生输入；统一 NeoForge 事件与键位重映射语义 |
| [MinecraftCoordPickScrollMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/MinecraftCoordPickScrollMixin.java) | MouseHandler.onScroll H 可取消 | **中**。已按模式限制；检查窗口归属和输入时序，NeoForge 优先事件 |
| [PonderLocalizationMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderLocalizationMixin.java) | getShared/getSpecific H 可取消；2 个 Map Shadow | **中**。命名空间限制良好；精确重载、格式化与语言资源优先级 |
| [WorldSectionElementImplMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/WorldSectionElementImplMixin.java) | erase/add H 可取消；section Shadow | **中**。局部脚本初始化优于全局吞操作，见 R11 |
| [PonderSceneTextIndexMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneTextIndexMixin.java) | PonderScene.begin H，清本模组索引 | **低**。不改原流程；优先保留，检查索引生命周期即可 |
| [PonderSceneViewOffsetMixin](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneViewOffsetMixin.java) | PonderScene 添加 Unique 状态、tick H 推进 chaser | **低中**。Unique 与接口桥合理；统一生命周期，未启用场景可不推进动画 |
| [PonderSceneAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneAccessor.java) | scaleFactor、basePlateOffsetX/Z、basePlateSize，共 7 个访问方法 | **低中**。4 个 getter 迁公开 API、2 个未用 setter 删除，保留必要写桥 |
| [PonderUIAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderUIAccessor.java) | hoveredBlockPos、identifyMode、scenes、index、lazyIndex、slowMode，8 个访问方法 | **中**。scenes 暴露可变列表、index/identifyMode 写入影响状态机；只保留必要引用 |
| [TextWindowElementAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/TextWindowElementAccessor.java) | textGetter、bakedText、y、vec、palette，5 个只读桥 | **低**。palette 可迁公开 API；其他字段暂留，推动上游 overlay 数据接口 |
| [InputWindowElementAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/InputWindowElementAccessor.java) | sceneSpace、direction、key、icon、item，5 个只读桥 | **低**。尚无等价完整 getter 集，保留优于反射或复制上游渲染 |
| [PonderKeybindsAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/PonderKeybindsAccessor.java) | PonderKeybinds.mapping 只读 | **低**。需要真实 KeyMapping；公开注册回调/上游 getter 可替代 |
| [RenderSystemShaderLightsAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/RenderSystemShaderLightsAccessor.java) | 静态 shaderLightDirections 只读 | **低**。保留保存/恢复能力；消费者必须 finally 恢复 |
| [MultiLineEditBoxAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/MultiLineEditBoxAccessor.java) | textField Accessor、seekCursorScreen Invoker | **低中**。映射正确进入 Fabric refmap；仅自有编辑器使用，暂留 |
| [MultilineTextFieldAccessor](D:/Files/ponderer/Common/src/main/java/com/nododiiiii/ponderer/mixin/MultilineTextFieldAccessor.java) | selectCursor getter/setter | **低中**。写游标依赖内部选择状态；检查边界，重写整个编辑器得不偿失 |

### 3.2 NeoForge：8 个，7 个注册

| 文件 | 目标与钩子 | 风险 / 建议 |
|---|---|---|
| [PonderSceneRegistryMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryMixin.java) | 两个 loadSchematic 重载 H 可取消、require=0 | **高**。R6；限制资源归属，共享业务迁 Common |
| [PonderSceneRegistryNbtMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryNbtMixin.java) | compile(ResourceLocation) R 替换列表、require=0 | **中**。相对 HEAD 取消更保守；修正候选范围、空结果和上下文生命周期 |
| [PonderUINbtMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderUINbtMixin.java) | 两个 of(ItemStack...) H/R，共 4 个钩子 | **中高**。R8；用可恢复作用域，统一打开入口 |
| [PonderTooltipNbtMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderTooltipNbtMixin.java) | updateHovered H 可取消、require=0 | **高**。R5；保留外部教程和原生状态清理 |
| [SceneTransformOffsetMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/SceneTransformOffsetMixin.java) | 构造 R；apply 双参 H/T；scale Redirect | **高**。R4/R7；局部参数替换优于独占 Redirect，统一正逆变换 |
| [PonderLevelModelDataMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/PonderLevelModelDataMixin.java) | PonderLevel 添加 getModelData(BlockPos) | **中高**。API 同名方法合并；当前上游 Catnip 确实读取 level.getModelData，不能无替代删除 |
| [InterfaceExtendedCaptureMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/InterfaceExtendedCaptureMixin.java) | MenuType.create(..., RegistryFriendlyByteBuf) H | **低中**。保留 loader 专属入口；只读复制正确，大小错误已显式拒绝 |
| [AnimationTickHolderMixin](D:/Files/ponderer/NeoForge/src/main/java/com/nododiiiii/ponderer/mixin/AnimationTickHolderMixin.java) | getTicks/getPartialTicks/getRenderTime 的 7 个 H 钩子 | **未注册**。先确认设计意图；不能把源码存在当作平台功能已实现 |

### 3.3 Fabric：7 个，全部注册

| 文件 | 目标与钩子 | 风险 / 建议 |
|---|---|---|
| [PonderSceneRegistryMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryMixinFabric.java) | 两个 loadSchematic H 可取消、require=0 | **高**。与 NeoForge 同一个 R6；不应继续维护两份加载算法 |
| [PonderSceneRegistryNbtMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderSceneRegistryNbtMixinFabric.java) | compile(ResourceLocation) R、require=0 | **中**。逻辑可共用；Fabric 描述符映射已见 refmap |
| [PonderUINbtMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderUINbtMixinFabric.java) | 两个 of 重载 H/R | **中高**。R8；不要只修改一端 |
| [PonderTooltipNbtMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/PonderTooltipNbtMixinFabric.java) | updateHovered H 可取消 | **高**。R5；业务共用、资格判断局部化 |
| [SceneTransformOffsetMixinFabric](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/SceneTransformOffsetMixinFabric.java) | 构造 R；单参 apply H/T/R；双参 apply T | **高**。R4/R7；旧单参可选钩子成品映射不匹配，TAIL 字段写入太晚 |
| [AnimationTickHolderMixin](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/AnimationTickHolderMixin.java) | 7 个静态时间读取 H 可取消 | **中**。有 ThreadLocal 帧时间范围是优点；忽略 level/includePaused 的语义，要验证第三方渲染与非投影世界 |
| [InterfaceExtendedCaptureMixin](D:/Files/ponderer/Fabric/src/main/java/com/nododiiiii/ponderer/mixin/InterfaceExtendedCaptureMixin.java) | ExtendedScreenHandlerType.create H，重编码打开数据 | **中**。只在观察时运行、缓冲释放良好；额外 codec 调用及非纯编码器需要适配测试 |

## 4. 降低侵入性的实施方案

### 阶段 A：先修行为边界，保持功能完整

1. 沙盒保护检查移到所有第三方代码执行之前；隔离失败仅显示静态占位。先完成此项，再评估是否让更多钩子可选。
2. 保留原生慢速阅读按钮，为文本面板增设自己的入口；停止对全部 children 的重复渲染。
3. 跨命名空间结构加载改为显式导入或真正的资源缺失 fallback。
4. 统一 NBT 资格判定，保留其他模组教程；明确全空结果行为；ThreadLocal 用 finally 恢复前值。
5. 清理 Fabric 单参 apply 的旧映射路径，统一两端镜头正/逆变换；修正混用 remap 的契约。

**完成标准：** 不改变原生教程按钮和资源优先级；不泄漏物品上下文；隔离缺失无法执行第三方 UI；两端镜头行为一致。此阶段不设“删除多少个 Mixin”的数量指标。

### 阶段 B：共享业务与减少内部耦合

1. 将加载、NBT 可见性、视图状态放 Common 普通服务；两端同构 Mixin 可直接共用时合并，否则仅保留薄入口。InterfaceExtendedCapture、ModelData 保持 loader 专属。
2. 用既有 loader Screen/client tick/输入事件替换等价的全局注入。先检查事件时机和取消语义；不存在等价事件的精确入口可以继续用 Mixin。
3. PonderUI 拆成输入、控件扩展和必要渲染适配三个职责；只拆文件不降低侵入性，必须同时缩小作用对象。
4. 清理至少 **7 个明确可替代或无调用的 Accessor 方法**：PonderScene 4 个 getter + 2 个 offset setter，TextWindow palette getter。尽量保留对象封装，不以反射或更多 AccessTransformer 代替。
5. 建立注入能力矩阵、一次性诊断、版本变体分组，以及 JSON 注册与源码一致性检查。

**完成标准：** 核心算法每种行为只有一份；输入只由一个语义入口负责；无未解释的孤立 Mixin；私有字段依赖减少且功能不丢失。

### 阶段 C：推进上游扩展点，消除长期补丁

建议向 Ponder/Catnip提出：安全实体 tick 的变更队列；自定义 view transform 与逆矩阵接口；ItemStack/scene 级可见性扩展；结构资源提供器；文本/输入 overlay 的只读数据接口；受控 UI 扩展槽位和模型数据查询。

优先顺序是“公开 API/自有对象 → loader 事件 → 局部追加或返回值变换 → 可组合的调用包装 → 最小范围取消”。不应把 `priority` 提高、`require=0`、`remap=false` 或 MixinExtras 当成单独的兼容性方案；它们都不能修复错误的对象归属与语义范围。

## 5. 验证与交付要求

### 5.1 本次实际完成的检查

- 枚举有效模块全部 Java 中的 `@Mixin`，包括不叫 `*Mixin.java` 的 Accessor/Invoker；逐类阅读目标与调用方。
- 校验两份 JSON 可解析、无重复注册、每条注册在 Common + 对应 loader 中唯一解析。
- 对照本地 Ponder/Catnip 的 tick、selection、资源加载、UI 渲染/输入、本地化、坐标转换、时钟及模型数据实现。
- 检查 Fabric 生成 refmap 和本模组成品注解；检查本地两端日志、依赖版本与接口 PASS。
- 保存源码与产物指纹；交付前检查报告对所有源文件的覆盖、文档链接及清单与当前源码一致性。

### 5.2 实施修改后的最小验证命令

命令均从 `D:\Files\ponderer` 执行；使用仓库要求的 JDK 21。建议按修改阶段运行，不在仅改文档时启动整套游戏。

```powershell
.\gradlew.bat :Common:compileJava :NeoForge:compileJava :Fabric:compileJava --console=plain
.\gradlew.bat :NeoForge:build :Fabric:build --console=plain
.\gradlew.bat :Common:test --console=plain
.\gradlew.bat quickPlaySmoke -PsmokeTimeout=180 -PsmokeInterfaces --console=plain
```

第一条用于有映射变更时的精确编译/refmap核查；一般代码变更以第二条 scoped build 为默认。`:Common:test` 只在改动有相关现有 JUnit 覆盖时运行，或为抽出的纯逻辑补必要测试；不能假定 loader 的测试任务可用。任何 Mixin 修改都必须执行 quickPlaySmoke；脚本现有 smoke 模式有时间上限和进程清理。构建本身也应由执行器设置合理整体时限。

完成编译后检查：

- `D:\Files\ponderer\Fabric\build\classes\java\main\ponderer.refmap.json` 与 Fabric 发布 JAR 注解；不仅看 dev JAR。
- NeoForge 发布 JAR 的选择器与当前 runtime 目标；若工具链仍不生成 refmap，记录其命名验证依据。
- 两端新的 `latest.log` 中注入失败与功能探针结果。需要 PonderUI 覆盖时，选择确认已登记教程的物品运行 `-PsmokePonderItem=<实际物品ID>`，不能把打开菜单的 PASS 当成全量 Mixin PASS。

### 5.3 必须补足的行为矩阵

| 场景 | 关键断言 |
|---|---|
| Ponderer 单独安装；NeoForge + Create 选用较新 Ponder | 所有必需签名命中；功能组完整；其他命名空间不受意外改变 |
| 原生 Ponder 与自定义教程 | comfy reading、返回键、背包键、焦点、scene 切换与重播正常 |
| 第三方控件 / 渲染状态异常 | 每帧调用次数正确；异常后 pose、灯光、深度范围恢复 |
| 平移缩放动画 + 拾取 + 投影仪 | 正反变换一致、两端一致、多场景时钟相互隔离 |
| 外部教程 + 自定义 NBT 教程 | 外部内容不被隐藏；提示与打开规则一致；无上下文泄漏 |
| 沙盒缺一个保护钩子 | 构造/init/render/tick 不执行第三方代码，真实世界/网络无变化 |
| 延迟/异步菜单行为 | 真实容器不被 101–127 约定误伤；已关闭会话不能继续操作 |
| 捕获双手、扩展菜单、超限、坏 codec、延迟同步 | 正常游戏打开不受影响；捕获失败可解释，不保存半成品 |
| 实体列表在 tick 中增删 | 无迭代异常；清理与 tick 次数符合明确语义 |
| 客户端安装与专用服务端加载 | client-only 配置不触发服务器客户端类加载；此项当前未运行 |

**交付状态：** 已完成 39 个文件的审计、静态证据整理、已有日志检查及分阶段优化方案。未执行修复，未新跑构建或游戏，未把任何尚未专门复现的多模组冲突写成已验证故障。后续最优先的实际改动是 R2、R3、R5、R6 的边界修正，再处理 R1/R4 的较大行为重构。
