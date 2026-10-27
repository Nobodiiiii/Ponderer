# 阶段 1–6 的实际边界与归属

本清单对应 Minecraft 1.21.1 的 `Common / NeoForge / Fabric`，依据当前 `settings.gradle`。`Forge/`、历史 `ref/`、生成目录及测试代码不计入发布归属。仍沿用原三个 Gradle 项目；没有新增独立运行库 JAR，也未实施阶段 7。

[完整归属表](MODULARIZATION_OWNERSHIP.csv) 逐项列出 579 个活动主源码文件、64 个资源文件、1 个加载器元数据模板、83 个两端 Mixin 配置项、8 个 SPI 提供项、34 个 payload ID、29 个配置键及游戏注册 ID。相同共享类可出现在两个加载器的 Mixin 配置中，83 是配置项数量，不是不同类数量。该表是源码快照，不是构建产物清单。

## 实际职责

| 逻辑所有者 | 对外边界与状态 | 实现/装配位置 |
| --- | --- | --- |
| `scene` | DSL、文本、界面描述、步骤词汇、codec、标准化、不可变 `SceneSnapshot` | 不引用游戏类、工作区或功能服务；`sourceFile` 作为来源元数据单独保留 |
| `workspace.api` | `Workspace`、布局、场景/结构/资源包仓库、结果、包状态与注册表 | `workspace.local` 持有实例状态，构造时传入目录、编辑策略和删除回调；`bootstrap.Workspaces` 创建产品实例 |
| `runtime.api / spi` | 场景目录、编译请求/结果、播放会话；场景/结构/配料/菜单/注册贡献来源 | `runtime.core / interfaces / viewer / render` 仅消费场景、底层支持和游戏依赖；不依赖产品、工作区、编辑、同步或 AI |
| `editor.api / session` | 编辑命令、历史、草稿、采集回调、可关闭的安装句柄 | `editor.capture / preview / mixin`；采集通过 `SceneCaptureContext` 返回，JEI 呈现通过 `IngredientPresentation` 注入 |
| `application` | 保存用例、刷新顺序、命令、产品 UI、权限策略、协议路由与跨功能接入 | `application.ui` 明确承担产品组合职责；不作为可复用编辑内核的一部分 |
| `sync` | 客户端拉取/冲突/待处理更新，服务器目录/发送队列，协议与处理器 | 仓库通过 `workspace.api` 获取；UI 更新发布事件，具体屏幕响应放在应用层 |
| `ai` | 提供商、工具、提示、预算、生成管线与不可变候选 | 管线显式接收 `GenerationResources` 和轮数策略；取消、保存与刷新由 `application.ai` 和 `bootstrap.ProductGeneration` 组合 |
| `exporter` | Java 扫描/生成、PonderJS 解析/生成 | Java 导出接收固定场景快照与只读结构来源；PonderJS 文件操作和命令反馈在 `application.conversion` |
| `features.projector / blueprint / trigger` | 各自的状态、服务、协议与注册供应器 | 投影平台能力为消费方 `ProjectorPlatform`；产品入口保留原注册顺序与配方 ID |
| `feedback` | 显示优先级、交互提示、请求关联和反馈 envelope codec | envelope 位于 `feedback.protocol`；产品响应路由与固定顺序的协议表由装配安装；资源包提示位于 `application.feedback` |
| `support / nbt / util` | 结构、效果、基础控件、生命周期清理、NBT 与路径能力 | 不读取产品目录或运行目录；工作区错误文案由 `application.ui.WorkspaceMessages` 转换 |
| `product.catalog` | 品牌归属与自定义分类的共享定义/呈现 | 自定义分类显式接收场景集合，导出不再读取实时全局目录 |
| `bootstrap / registry` | 产品构造、可选能力安装、刷新/世界生命周期、统一注册顺序 | 允许组合多项功能；加载器实现仍留在活动平台模块 |

`public` 可见性还承担 Mixin、加载器及跨包内部协作，不等于所有 public 类都承诺作为第三方扩展 API。通常的消费边界是 `api/spi`；实例构造仅在宿主/产品装配处接触实现。`application.api.RuntimeAccess` 和 `workspace.api.WorkspaceAccess` 是显式安装的宿主绑定，不创建默认产品实例；未安装时立即报错。

原方案的两项调整：产品屏幕需要同时组合保存、同步、AI 和功能配置，因此集中在应用层；独立编辑服务只持有草稿、预览与采集。反馈 envelope 属于反馈传输能力，不再反向依赖产品屏幕/业务处理器。品牌分类属于产品共享元数据，避免把产品目录查询藏在底层支持工具中。

## 宿主资源与播放会话

构造点使用 `runtime.core.PonderRuntimeEngine`，其他调用使用 `RuntimeEngine`。宿主实现 `StructureSource.loadSchematic(ResourceLocation)` 和 `open(String, String)`，可返回自身的结构模板及内存/只读资源流；打开的流由调用方关闭，缺失资源返回 `null`。

```java
// 在宿主的装配入口：resources 是宿主实现的 StructureSource。
RuntimeEngine engine = new PonderRuntimeEngine(
    "host_namespace", resources,
    IngredientResolver.NONE, RegistrationContribution.NONE, () -> true);
engine.replaceCatalog(() -> List.of(SceneSnapshot.of(document)));
// 正常 Ponder 插件生命周期可以注册 engine.plugin()。
CompilationResult compiled = engine.compile(
    new CompileRequest(SceneSnapshot.of(document), 0));
if (compiled != null) {
    PlaybackSession playback = compiled.playback();
    // compiled.scene() 交给宿主呈现；按需调用 previewStep / resumeTimeline。
    playback.close();
}
engine.close();
```

普通 Ponder 注册使用运行实例内部结构句柄，再由运行 Mixin 路由到该实例的 `StructureSource`，与直接编译消费同一来源。内部句柄不写入场景 JSON；产品来源继续按原 ID 加载，保持本地资源优先级。NBT 绑定由插件独占，注册或关闭一个插件不清空其他实例。

上述独立宿主 API 使用示意未执行；产品宿主普通播放已通过现有 NeoForge Ponder smoke。运行仍需要 Minecraft 客户端环境、Ponder 和必要平台/渲染注入，不是无游戏环境的 JVM 运行库。产品宿主通过 `ProductRuntime` 提供工作区资源和可选兼容适配；运行实现无需读取工作区布局。场景目录进出均复制文档；编译消费 `SceneSnapshot`，编辑草稿不与播放对象共享。会话关闭释放界面和场景渲染缓存，运行实例关闭会关闭其存活会话并清空目录/映射。

## 安装与清理

- `ClientCatalogBindings.install()` 保持完整产品默认装配；`stopEditor / stopSync / stopAi / stopProjector` 分别释放对应客户端能力。完整 `close()` 释放安装句柄，`install()` 可再次安装。
- `EditorModule` 管理按键扩展、槽位变化订阅、拖拽与输入锁；关闭时清理预览、点选、NBT 与界面捕获。普通播放使用空编辑扩展。
- `SyncClientModule` 关闭后停止客户端拉取处理并丢弃连接内存状态，保留已经持久化的数据与冲突记录。
- AI 停止会取消活动任务并改变投递代次，旧任务不再向已离开的界面投递；最终写盘在取得仓库锁后验证作用域，并与取消操作有确定先后顺序；保留已有草稿、日志和文件。
- `ProjectorClientModule` 关闭时取消目录订阅并释放播放缓存；不再准备新的投影播放对象。
- `ClientFeatureLifecycle` 观察连接和世界身份变化；应用装配重置编辑工具、界面沙盒、反馈请求/提示、同步会话、AI 任务、触发状态、蓝图选区及投影缓存。清理帮助类会尝试每个动作并汇总异常。

配置开关允许下次启动生效，不要求热卸载全部能力。这些是客户端能力安装边界；关闭它们不撤销服务器方块/物品注册，不声称动态卸载模组。服务端持久化状态不随客户端关闭而删除。

## 配置、注册与资源

`Config` 继续是产品配置容器，现有键和默认值保持原有定义；键的功能归属逐项列于 CSV。默认可编辑策略与播放进度偏好由装配传给运行层。`FeatureAvailability.Snapshot` 保存服务器启动时捕获的蓝图/投影可用性，未捕获时沿用原回退时机。

蓝图物品由 `BlueprintRegistration` 注册，投影方块/方块实体/菜单由 `features.projector.registry` 注册；`registry.ModItems.init()` 保留蓝图、方块、创造标签、方块实体、菜单的原顺序。`ModCreativeModeTabs` 仍组合完整产品内容。游戏 ID 继续为 `ponderer:blueprint`、`miniature_projector`、`life_size_projector`、`projector` 与创造标签 `ponderer`（均在 `ponderer` 命名空间），按注册表区分重复路径。

Java 导出支持模板归导出器；内置脚本和结构归工作区默认资源；AI 提示归 AI；蓝图/投影配方、模型与纹理归对应功能；归属标记的观察者模型/纹理归 `product.catalog`。语言、动作图标与元数据仍由产品统一打包，语言键没有按功能拆成多个文件。

## Mixin、SPI 与加载器入口

两端五份 Mixin 配置的逐项实际所有者在 CSV：运行注入归运行层，编辑采集注入归编辑层，产品窗口/返回/本地化和组合输入路由归应用层，反馈访问器归反馈。平台专用渲染/网络/采集实现继续留在平台模块；没有复制为 Common 游戏实现。

每端保留四个 Java SPI 提供项：`PlatformHelper`（环境）、`NetworkHelper`（传输）、`RegistrationHelper`（注册）、`InterfaceSandboxPlatform`（界面沙盒）。投影菜单通过安装既有平台实现所实现的 `ProjectorPlatform` 接口提供，不额外发现第二个有状态平台实例。`PondererServices` 仅承担加载器 SPI 发现，没有场景目录或功能业务状态；它是保留的平台机制，不是待删除的迁移门面。

Fabric 的主/客户端/ModMenu/JEI 入口已按实际包名列出；NeoForge 继续通过加载器注解发现入口和 JEI 插件，元数据模板保持原发布方式。`fabric.mod.json`、服务文件名/提供类、Mixin 相对包名已同步迁移。访问扩大器文件仍归产品平台元数据，未按未来 JAR 拆分。Mixin 配置分别限定为平台、runtime、editor、application、feedback 的实际 mixin 包，避免声明整个产品包造成入口类无法加载。

## 源码检查与未验证范围

执行了 Java 语法解析（仅 `JavacTask.parse`，不做类型分析或编译）、重复签名和项目类引用检查、跨包静态访问审阅、Mixin/SPI 声明解析、网络 codec 源码对照以及 `git diff --check`。34 个 payload 的 ID 保持原值；89 个既有 codec/type 方法与基线源码对照未发现内容差异。没有新增测试。

阶段实施时按用户要求未运行构建与验收；后续已按新请求完成两端 build、Fabric refmap 检查与两端 `quickPlaySmoke -PskipBuild -PsmokeTimeout=180 -PsmokeInterfaces`。构建和界面 smoke 均通过，两个客户端已退出；结果、日志和自检跳过项/空场景日志限制见 [后续验证记录](MODULARIZATION_IMPLEMENTATION.md#后续构建与-smoke2026-10-27)。未运行 Common JUnit、LLM、完整人工验收、专用服务器或独立运行宿主，不能据此证明全部功能等价。

本轮审计修复已使两端 build 与既有 Common JUnit 全部通过（61 项、1 项原条件跳过）；双端界面 smoke 和 NeoForge 普通 Ponder 播放 smoke 通过。F1–F6 修复、链路比较、日志和未实测范围见 [修复记录](MODULARIZATION_AUDIT.md#修复状态2026-10-27)。未新增测试文件、用例或脚本。上文阶段实施时的未运行说明保留为历史记录。
