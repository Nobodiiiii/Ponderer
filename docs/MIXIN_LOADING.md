# Mixin 按需加载

Mixin 在目标类转换前决定是否应用，不能在打开场景时安装，也不能在关闭最后一个场景后卸载。Lib 与本体分别使用 Runtime/Product 启动插件，各自管理自身 Mixin；未归组的新增 Mixin 会明确报错。

## 启动需求

启动插件从加载器提供的模组根目录读取 `META-INF/pondererlib.features.properties` 及 `META-INF/pondererlib.features/*.properties`，支持开发目录、独立和嵌套 JAR。多个宿主取并集。普通声明与 JSON 内容不加载宿主 Java 类。

需要自行管理开关的已编译宿主可用 `META-INF/pondererlib.startup.properties` 的 `provider=<类名>` 显式注册 `StartupFeatureProvider`。它在任意一方 Mixin 插件首次初始化 Lib 时执行、提交通用能力需求，先于能力冻结，不依赖插件先后顺序。提供者只能引用 JDK 和启动 API，不能初始化游戏类。只有已安装模组可提供该入口，资源包不能加载 Java。本体通过这个入口读取自身配置；Lib 不包含本体开关、依赖组合或产品 Mixin 名称。

```properties
# 手写宿主声明。生成器使用另一个文件，不覆盖此文件。
features=core,view,interfaces
```

纯资源内容不需要 Java 声明：插件检查模组中的 `assets/<namespace>/ponderer/content/*.json` 和其引用的场景，推导所需能力；同时检查 `options.txt` 已选中的本地目录/ZIP 资源包。声明与场景数据可以分属不同包；同一引用的不同资源贡献取能力并集，避免覆盖包漏装注入。普通资源重载仍有效；运行中首次加入需要额外 Mixin 的内容，应声明需求或设对应能力为 ON 后重启。缺少能力的场景给出不可用诊断，不尝试晚安装注入。

完整 Ponderer 声明自身模块需求。Lib 单独安装、没有场景或宿主声明且所有策略为 AUTO 时，不应用任何本项目 Mixin。Java + Lib 导出把所需能力并集写入 `META-INF/pondererlib.features/ponderer_export.properties`，部分导出保留未选场景的需求，转 Support 时删除生成声明。旧导出清单缺少需求字段时保守保留运行能力，再次完整导出后精确收敛。

## 策略和依赖

游戏目录 `config/pondererlib.properties` 的每项均支持 AUTO、ON、OFF；也可通过 `-Dpondererlib.<能力>=OFF` 覆盖。`features=core,view` 或 `-Dpondererlib.features=core,view` 可补充需求。`-Dpondererlib.config=<文件>` 更换配置文件。配置与能力集合在启动时固定。

- AUTO：只有宿主/内容申请，或其他已申请能力依赖它时才安装。
- ON：主动申请该组及依赖。
- OFF：排除该组；依赖它的组也不应用，避免留下失效的强制转换或注入。
- 关闭应用核心后不能继续使用产品/引擎 API；关闭可选能力后，依赖它的场景不可用，数据保留。

| 能力键 | 直接依赖 | Mixin 范围 |
| --- | --- | --- |
| core | 无 | 实体安全 Tick、结构来源、区块空保护、NeoForge ModelData、场景/UI/指令访问器 |
| view | core | 场景镜头状态和坐标变换 |
| text | view | 文字索引/覆盖层重播清理 |
| timeline | core | 可由嵌入预览独立使用的交互进度条 |
| player | view、text、timeline | 播放界面、播放控制、倍速插值 |
| interfaces | view | Sandbox 系列，包括平台访问器和事件隔离 |
| nbt_bindings | core | 两端物品 NBT 上下文、场景过滤、悬浮提示 |
| render_cache | core | 区块渲染缓存记录；与区块空保护分离 |
| shader_lights | 无 | 通用渲染灯光状态访问器 |
| detached_rendering | view、text、render_cache、shader_lights | Fabric 帧时间桥接、文字/输入访问器 |
| ui_extensions | core | 独立键盘扩展桥接；有 player 时复用其入口，两种入口不同时应用 |

`application`、`editor`、`feedback` 仅属于本体，配置文件为 `config/ponderer.properties`，支持 AUTO、ON、OFF（安装本体时 AUTO 默认申请）。可用 `-Dponderer.<开关>=OFF` 覆盖，`-Dponderer.config=<文件>` 指定配置文件。优先级为新 JVM 参数、新配置文件、旧 JVM 参数、旧 Lib 配置文件；旧 `pondererlib.application/editor/feedback` 参数和 Lib 配置中的同名键继续由本体读取。旧全局 `features` 列表中的这三个名称在安装本体时仍可识别。只安装 Lib 时不提供这些产品能力。

| 本体开关 | 直接依赖 | 本体 Mixin 范围 |
| --- | --- | --- |
| application | Lib core、shader_lights | 产品注册、本地化、文本框和按键访问器 |
| editor | 本体 application；Lib detached_rendering、ui_extensions、timeline | 编辑按钮、坐标/NBT 点选；界面捕获还要求 Lib interfaces |
| feedback | 无 | 产品 ActionBar 访问器 |

本体 OFF 只移除本体需求，不撤销其他宿主需求。Lib 的 OFF 仍优先：禁用本体模块的必要通用能力也会使该模块不可用。`application=OFF` 会同时关闭 editor，不能据此将完整产品当成可正常使用的纯运行库。

播放器还有运行时激活层：启动声明让 Mixin 可用，普通客户端初始化时调用 `RuntimeFeatures.requestPlayerEnhancements(owner)` 才激活 AUTO 播放增强；多个申请独立释放。未声明且被裁剪时申请会给出重启提示，显式 OFF 优先。已打开屏幕在初始化时固定激活状态。

编辑器的离屏预览不调用完整 `PonderUI.init()`，通过 `PonderProgressBarAccess` 单独启用进度条的点击、拖动、吸附和提示。进度条 Mixin 跟随通用 timeline 能力，本体 editor 申请该能力；player=OFF 不影响编辑器时间线，也不会因此为普通 Ponder 屏幕启用播放器增强。

## 已加载组的运行范围

- `PonderLevelMixin` 只接管 Lib 引擎/程序接收的世界；其他模组的普通 Ponder 场景沿用上游 Tick。原生手写场景可用 `RuntimeScenes.adopt(builder.getScene())` 显式接入，需先声明 core。
- `RuntimeScenes.adopt()` 同时通过 Ponder 的公开 `setChunkSource()` 接口安装场景区块源。区块查询将当前可见方块同步到真实区段，覆盖场景还原、遮罩、锚点和实体 Tick 的可见性变化，兼容锂直接读取区段的碰撞优化；不依赖锂，也不向锂注入 Mixin。仅处理 Lib 接收的场景，其他模组的普通 Ponder 场景不受此修复覆盖。
- 渲染缓存只记录接入的世界或独立渲染上下文，不持续跟踪其他普通 Ponder 场景；缓存维护事件跟随 render_cache。
- 镜头的 4 个插值器首次使用时才分配；没有镜头状态时不推进，不因普通场景渲染而分配。
- 播放器关闭后不安装增强相关屏幕事件；界面/镜头通过可选接口读取播放器状态。
- 沙箱网络防火墙在首次发出沙箱窗口 ID 前，不执行作用域外的保留窗口 ID 过滤；使用过的沙箱仍保留延迟包隔离。

## 验证边界

使用现有 Gradle 构建、Common 测试、生成 refmap 检查与 `quickPlaySmoke` 普通入世界验证。没有新增测试文件、自动打开界面或自动播放脚本。普通入世界不能证明全部播放、界面交互和运行中重载效果，后者仍需人工验收。

2026-11-15 本体开关迁移：

- `:RuntimeNeoForge:build :RuntimeFabric:build :NeoForge:build :Fabric:build :Common:test --offline` 通过，见 `build/product-feature-build-final.log`。既有 AI 测试 5 项通过、真实 LLM 1 项按原条件跳过。
- `build/product-feature-policy-observations.log` 为 Java 21 中直接检查启动策略和全部 90 个配置项的记录：仅 Lib 无需求为 0/0；产品默认为 Fabric 45、NeoForge 43；播放器 OFF 且编辑器开启为 42/40；播放器、编辑器、反馈关闭为 30/28。新旧配置、JVM 优先级、application 依赖关闭、timeline 依赖关闭和旧 features 列表均已核对。没有新增测试文件或脚本。
- 两端 JAR 的 Mixin 配置均指向各自 Runtime/Product 插件，对应类全部存在；产品按键访问器已移出 Lib，Lib 不含产品启动实现。Fabric refmap 保留预期的 class_*/field_* 映射；NeoForge 当前工具链不生成 refmap，使用实际启动补充验证。
- `build/product-feature-smoke-default.log`、`product-feature-smoke-player-off.log`、`product-feature-smoke-editor-off.log` 记录三组双端实际入世界通过（58 秒、51 秒、60 秒），均无占用跳过。使用 `quickPlaySmoke -PskipBuild -Pworld=<隔离副本> -PsmokeTimeout=180 --offline`；每端上限 180 秒、外层上限 420 秒，客户端均退出，临时配置已恢复。启动日志确认本体开关不再进入 Lib 的 requested/enabled 集合，player OFF 时 editor 仍申请 timeline。
- 普通入世界未覆盖编辑预览的实际拖动、界面捕获和专用服务器加载，这些仍需人工验收。

以下为 2026-11-14 迁移前记录（包含旧能力名称与计数），位于 `build/`：

| 记录 | 结果 |
| --- | --- |
| mixin-demand-build-final.log | 两端 Lib、两端产品 build 和 Common:test 通过；现有 AI 测试 5 项通过、真实 LLM 1 项按条件跳过 |
| mixin-demand-policy-observations.log | Java 21 中检查全部 90 个配置项，覆盖无需求、core、NBT、player、interfaces、资源内容、完整产品、播放器关闭、依赖关闭和最小产品配置；无未归组项 |
| mixin-demand-export.log | 复用已有两份场景，在隔离副本实际导出 JSON + Lib 和 Java + Lib，均成功、无跳过；生成声明为 core/interfaces/render_cache/text，view 由依赖补齐 |
| mixin-demand-smoke-default.log | 完整产品默认配置，两端实际入世界通过，47 秒 |
| mixin-demand-smoke-minimal.log | 关闭十项可选能力、只保留 core/application，两端实际入世界通过，49 秒 |
| mixin-demand-smoke-standalone.log | 仅 Lib、无内容宿主/需求，两端实际入世界通过，47 秒；启动时观察到 requested/enabled 均为空 |
| mixin-demand-smoke-resource-packs.log | 最终跨包发现实现：仅 Lib，目录包只放内容声明、ZIP 包只放场景数据；两端实际入世界通过，48 秒 |
| mixin-demand-resource-fabric.log / mixin-demand-resource-neoforge.log | 两端均只启用 core/view/text/interfaces/render_cache，成功加载两份场景、注册一份内容清单 |

按插件决策统计：无需求时两端均为 0；仅 core 时 Fabric/NeoForge 为 6/7；两份资源示例为 18/17。完整产品为 45/43，播放器 OFF 为 41/39，core/application 最小产品为 12/13。分母分别为 Fabric 46、NeoForge 44 个配置项，包含互斥的键盘扩展后备入口，不是注入点数量。

Fabric 生成 refmap 中新增 renderLayer 钩子包含 class_4597/class_1921/class_332，镜头变换描述符包含 class_4587/class_243/class_241；平台访问器保留 field_17409。当前 NeoForge ModDev 构建不生成 refmap 文件，未宣称检查不存在的文件；两端实际启动用于补充验证。所有 smoke 每端上限 180 秒、外层上限 480 秒，普通入世界后退出；临时配置、产品 JAR、options 和资源包均已恢复，仅保留隔离存档副本与构建日志。
