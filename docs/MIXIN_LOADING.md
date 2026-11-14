# Mixin 按需加载

Mixin 在目标类转换前决定是否应用，不能在打开场景时安装，也不能在关闭最后一个场景后卸载。Lib 和产品的全部 Mixin 配置使用加载器对应的启动插件；未归组的新增 Mixin 会明确报错。

## 启动需求

启动插件从加载器提供的模组根目录读取 `META-INF/pondererlib.features.properties` 及 `META-INF/pondererlib.features/*.properties`，支持开发目录、独立和嵌套 JAR。多个宿主取并集，不通过加载宿主 Java 类收集需求。

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
| player | view、text | 播放界面、播放控制、进度条、倍速插值 |
| interfaces | view | Sandbox 系列，包括平台访问器和事件隔离 |
| nbt_bindings | core | 两端物品 NBT 上下文、场景过滤、悬浮提示 |
| render_cache | core | 区块渲染缓存记录；与区块空保护分离 |
| detached_rendering | view、text、render_cache | Fabric 帧时间桥接、文字/输入访问器、灯光访问器 |
| ui_extensions | core | 独立键盘扩展桥接；有 player 时复用其入口，两种入口不同时应用 |
| application | core | 产品注册、本地化、文本框和按键访问器；产品结构预览也需要灯光访问器 |
| editor | application、detached_rendering、ui_extensions | 编辑按钮、坐标/NBT 点选；界面捕获还要求 interfaces |
| feedback | 无 | 产品 ActionBar 访问器 |

播放器还有运行时激活层：启动声明让 Mixin 可用，普通客户端初始化时调用 `RuntimeFeatures.requestPlayerEnhancements(owner)` 才激活 AUTO 播放增强；多个申请独立释放。未声明且被裁剪时申请会给出重启提示，显式 OFF 优先。已打开屏幕在初始化时固定激活状态。

## 已加载组的运行范围

- `PonderLevelMixin` 只接管 Lib 引擎/程序接收的世界；其他模组的普通 Ponder 场景沿用上游 Tick。原生手写场景可用 `RuntimeScenes.adopt(builder.getScene())` 显式接入，需先声明 core。
- 渲染缓存只记录接入的世界或独立渲染上下文，不持续跟踪其他普通 Ponder 场景；缓存维护事件跟随 render_cache。
- 镜头的 4 个插值器首次使用时才分配；没有镜头状态时不推进，不因普通场景渲染而分配。
- 播放器关闭后不安装增强相关屏幕事件；界面/镜头通过可选接口读取播放器状态。
- 沙箱网络防火墙在首次发出沙箱窗口 ID 前，不执行作用域外的保留窗口 ID 过滤；使用过的沙箱仍保留延迟包隔离。

## 验证边界

使用现有 Gradle 构建、Common 测试、生成 refmap 检查与 `quickPlaySmoke` 普通入世界验证。没有新增测试文件、自动打开界面或自动播放脚本。普通入世界不能证明全部播放、界面交互和运行中重载效果，后者仍需人工验收。

2026-11-14 本轮记录位于 `build/`：

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
