# Ponderer Lib

Minecraft 1.21.1，Java 21，当前支持 NeoForge 与 Fabric。Lib 扩展上游 Ponder，不 fork Ponder。库版本为 `0.1.0`，内容格式版本为 `1`。原场景、物品、方块、语言键和存储 ID 保持不变。

## 构建与分发边界

| Gradle 项目 | 目录 | 职责 |
| --- | --- | --- |
| SceneModel | Common/scene-model | 不依赖游戏的场景模型、格式与编解码 |
| RuntimeCommon | Common/runtime | 公共 API、执行、播放、界面沙盒 |
| RuntimeNeoForge | NeoForge/runtime | NeoForge Lib 入口、平台服务与 Mixin |
| RuntimeFabric | Fabric/runtime | Fabric Lib 入口、平台服务与 Mixin |
| Common | Common/src | 编辑、工作区、AI、同步、导出与产品组合 |
| NeoForge / Fabric | 原平台目录 | 完整 Ponderer 的平台入口与分发 |

两个 Lib JAR 的加载器身份都为 `pondererlib`。完整产品嵌入对应加载器的同一 Lib，不再在外层重复打包场景模型和运行类。Ponder、Flywheel 后备依赖由 Lib 携带；Fabric 的 Ponder 后备包本身携带 ForgeConfigAPIPort。产品自己的配置依赖保留在产品中。Fabric 的 Flywheel 仅客户端加载，因此通用元数据使用建议依赖，客户端入口按其中的版本条件强制检查；专服不被要求加载客户端渲染库。

运行包不依赖产品、编辑器、工作区、AI、同步、投影仪、蓝图或 JEI/AE2/Resourcify 实现。共享叶子工具位于 `runtime.support`，平台 Mixin 位于 `runtime.platform.mixin`，防止 NeoForge 模块层的 split-package 冲突。`api` / `spi` 是消费入口，Mixin 所需的 public 实现类不自动成为稳定 API。

```powershell
.\gradlew.bat :RuntimeNeoForge:build :RuntimeFabric:build :NeoForge:build :Fabric:build :Common:test --offline
.\gradlew.bat :RuntimeNeoForge:publishMavenJavaPublicationToLibStagingRepository :RuntimeFabric:publishMavenJavaPublicationToLibStagingRepository --offline
```

第二条发布到项目内的 `build/lib-repository`，没有上传远端仓库。当前 Maven 坐标为 `com.nododiiiii:RuntimeFabric:0.1.0`、`com.nododiiiii:RuntimeNeoForge:0.1.0`。发布仓库地址仍由维护者决定。

独立宿主必须按加载器嵌入 Lib，保留原身份：

```groovy
// Fabric Loom，repositories 先加入实际发布仓库。
modImplementation(include("com.nododiiiii:RuntimeFabric:0.1.0"))

// NeoForge ModDevGradle。
implementation "com.nododiiiii:RuntimeNeoForge:0.1.0"
jarJar "com.nododiiiii:RuntimeNeoForge:[0.1.0,)"
```

Ponder 的旧坐标 `Ponder-NeoForge-1.21.1` 与新坐标 `ponder-neoforge` 指向同一模组身份。Lib 的 JarJar 元数据统一使用后者，让 Create 携带的较新版参与同一次版本选择。仅 Support 的宿主自行配置上游依赖时也需要处理这项版本协调；不能让旧坐标强选低于 Create 最低要求的 Ponder。

## 三档导出

Java 模组导出界面可切换以下模式：

| 模式 | 产物 | 能力边界 |
| --- | --- | --- |
| JSON + Lib | 内容清单、场景 JSON、NBT 结构、语言和许可资源 | Lib 自动发现、校验、注册和重载；不生成 Java |
| Java + Lib | 原生故事板、类型化步骤对象、结构和资源 | 基础动作直接调用 PonderActions；界面、镜头、文本等通过同一 SceneProgram 执行 |
| Java + Support | 原生故事板、GeneratedPonderSupport、结构和资源 | 不依赖 Lib；不支持的界面、镜头等能力明确报告降级 |

Lib 档保留界面描述、NBT 挂载、运行时物品标签、shared_text、自定义步骤与未知步骤扩展字段。自定义 Java 提供者仍需在宿主编译；JSON 中的数据不作为 Java 代码执行。Support 档继续给出精确到场景/段落/步骤的省略诊断。

Java 两档写入加载器入口；JSON 档只写 Lib 依赖元数据，不添加加载器入口，也不要求 `src/main/java` 或 Java 包名。生成 `.ponderer-export/DEPENDENCIES.md` 提示宿主的 Gradle 接入；它不擅自改写宿主 build.gradle。切换模式必须选中此前导出的全部场景。生成器只移除自身记录的 Lib 依赖，保留宿主手写的依赖声明。切回 Support 时清理未修改的 Lib 扩展骨架；若扩展已有手写内容则保留并拒绝转换，提示先移植或移出。

三档导出及仅导出 Support 均写入 `src/main/resources/META-INF/licenses/ponderer/LICENSE`，包含 Ponderer 的版权声明与完整 MIT 许可文本，随宿主资源打包进入 JAR，不覆盖宿主根目录的 `LICENSE`。文本直接来自本仓库根目录的 `LICENSE`。

Java + Lib 的 `<basePackage>.ponderext.PonderExtensions` 只创建一次。再次导出不会覆盖它。生成源文件的上次文本保存在 manifest；检测到手改后拒绝覆盖或删除，并报告路径。稳定步骤 `id` 保留在数据中，旧文档会分配 `legacy_<序号>`；编辑器复制步骤时分配新 ID，替换步骤时保留原锚点。

`PonderActions` 和 Support 来自 `Common/runtime/src/main/templates/ponderer/generated_ponder_support.java.template`。Gradle 将分区归属和附加结构规划的源实现注入模板，生成正常编译的 Lib 动作类与导出模板。JSON 执行器只适配参数，避免另外维护一套分区、实体与结构算法。

## 纯资源接入

安装或嵌入 Lib 后，在模组资源目录或客户端资源包中放置：

```text
assets/example/ponderer/content/tutorials.json
assets/example/ponderer/scenes/tutorial.json
assets/example/ponder/tutorial.nbt
```

内容清单 `tutorials.json`：

```json
{
  "formatVersion": 1,
  "scenes": ["example:ponderer/scenes/tutorial.json"],
  "extensions": [],
  "attribution": true
}
```

场景文件沿用 `DslScene` 格式：物品/物品标签、NBT 挂载条件、结构、分类及分类的图标/标题/描述、段落、步骤和能力要求全部由数据描述。结构 ID `example:tutorial` 对应 `assets/example/ponder/tutorial.nbt`。场景的注册命名空间由清单所在的 assets 命名空间决定；场景 ID 的路径必须在该命名空间内唯一。资源包需要正常的 `pack.mcmeta` 并由用户启用；仅服务器数据包不会被客户端资源监听器扫描。

```mermaid
flowchart LR
  A[模组资源或客户端资源包] --> B[Lib 扫描 ponderer/content 清单]
  B --> C[校验场景、能力、结构与 ID]
  C --> D[按清单创建独立 RuntimeEngine]
  D --> E[Ponder 场景、分类、文本和 NBT 绑定]
  F[客户端资源重载] --> B
```

两端使用相同的发现与注册实现。首次启动与资源重载完成后，在客户端 tick 重建 Ponder 注册表，避开上游启动注册时序；旧 engine 的会话、NBT 绑定和结构句柄先释放。删除/停用资源包会移除其内容。清单遵循资源包覆盖顺序，不合并同名清单；无效场景记录清单和场景资源位置后跳过，其余有效场景继续注册。清单格式错误或缺少整个清单所要求的扩展时，整个清单不可用。

普通内容无需宿主 Java。新增执行能力仍由已编译的提供者实现：宿主可调用 `ResourceSceneContents.registerExtension("example:machines", engine -> { ... })`，再在清单 `extensions` 引用该 ID；资源不会加载任意 Java 类。导出时保留已有清单的扩展 ID。JSON 档迁移旧版本时必须选择全部旧场景；只删除未手改的生成 Java 和空扩展骨架，遇到手写扩展则保留并报告迁移位置。

## 宿主与扩展

```java
RuntimeEngine engine = new PonderRuntimeEngine(
    "example", new AssetStructureSource("example"),
    IngredientResolver.NONE, RegistrationContribution.NONE, () -> false);
engine.replaceCatalog(() -> List.of(SceneSnapshot.of(document)));
PonderIndex.addPlugin(engine.plugin());
// 在正常 Ponder 生命周期中注册/刷新，不在构造阶段直接调用游戏界面。
```

`StructureSource` 也可由宿主提供内存或工作区来源。普通 Ponder 注册通过各实例独占的内部结构句柄路由；资源类宿主可用 `AssetStructureSource` 加载 `assets/<namespace>/ponder/...nbt`。流由调用方关闭。JSON 内容统一使用客户端 ResourceManager 读取，遵循模组资源和资源包的覆盖优先级。

注册扩展必须早于场景注册和编译：

```java
engine.steps().register("example:pause", new StepType<Integer>() {
    public StepCodec<Integer> codec() {
        return new StepCodec<>() {
            public Integer decode(JsonObject data) { return data.get("ticks").getAsInt(); }
            public JsonObject encode(Integer ticks) {
                JsonObject data = new JsonObject();
                data.addProperty("ticks", ticks);
                return data;
            }
        };
    }
    public void build(Integer ticks, StepBuildContext context) {
        context.builder().idle(Math.max(0, ticks));
    }
});
```

对应数据为 `{"id":"pause_machine","type":"example:pause","parameters":{"ticks":40}}`。注册返回独立关闭句柄；重复 ID 明确报错。`StepBuildContext` 暴露原生 builder/util、分区/实体链接、场景内共享 state 及同一 `PonderActions.Context`。

原生 Java 可用 `engine.program(sceneId, segmentId, builder, util)` 自由交织 builder 调用、PonderActions 和 `program.step(step)`。JSON 与 Java 可通过 `engine.fragments().register(sceneId, segmentId, stepId, BEFORE/AFTER, body)` 在稳定步骤前后插入手写指令。应构建可重播的 Ponder 指令，避免把播放时副作用直接写进故事板构建阶段。

每个 engine 独占目录、步骤、片段、NBT 绑定、结构路由和直接编译会话。`replaceCatalog` 关闭本实例会话；`close()` 释放本实例拥有的注册和资源，不清空其他实例。生成的 `GeneratedPonderIndex.close()` 汇总关闭生成场景；关闭是终止操作，正常资源重载使用原有插件注册流程。上游 Ponder 没有插件移除 API，关闭后的插件保持为空，不能宣称热卸载整个模组。

`formatVersion` 和 `requiredCapabilities` 是内容声明。内建能力为 core、java_steps、view、nbt_bindings、interfaces（均带 `pondererlib:` 前缀）。第三方能力由 `engine.steps().provideCapability(id)` 申明。未知格式、缺步骤提供者、重复锚点、启动时禁用界面均给出位置诊断；依赖界面的整个场景不可用，数据继续保留。

## 启动策略

配置文件为游戏目录的 `config/pondererlib.properties`：

```properties
player=AUTO
interfaces=AUTO
```

也可用 `-Dpondererlib.player=OFF`、`-Dpondererlib.interfaces=OFF` 覆盖，或用 `-Dpondererlib.config=<路径>` 指定配置文件。配置在启动时读取，需要重启。

- 播放器 AUTO：存在至少一个 `RuntimeFeatures.requestPlayerEnhancements(owner)` 申请时启用。Ponderer 申请，导出内容默认不申请。多个申请独立关闭，用户 ON/OFF 优先。单个已打开屏幕在初始化时固定策略，避免中途改变申请导致渲染状态失衡。
- 所有 Mixin 现按启动能力分组：AUTO 没有需求时不应用，ON 请求该组及依赖，OFF 排除该组和依赖它的组。完整产品、Java 导出与资源内容在启动时提供需求，手写 Java 宿主需要声明后才能在客户端初始化时申请播放器。
- 全量分组、声明方式、资源包发现及重启要求见 [Mixin 按需加载](MIXIN_LOADING.md)。
- 界面 OFF：Mixin 插件在目标类转换前排除 Sandbox 系列和界面捕获注入；不安装界面平台、不注册界面事件、不执行第三方菜单/屏幕代码。Fabric 已去掉永久生效的界面 Access Widener，改为同组条件访问器。
- 沙盒打开前要求网络防火墙与屏幕拦截已实际生效，缺失则给不可用占位，不先调用目标代码再降级。渲染缓存维护独立于界面，跟随 render_cache；实体安全 Tick 和缓存记录缩小到接入 Lib 的场景/独立渲染上下文。

## 验证记录与范围

2026-11-14 纯数据接入补充验收（日志同在 `build/lib-split/`）：

- `data-content-build-final.log`：两端 Lib、两端产品、Common 现有测试及本地 Maven 发布通过，AI 5 项通过、真实 LLM 1 项按原条件跳过。
- `data-export-generation.log`：两端旧 JSON 导出迁移成功，删除其记录的生成 Java 和未修改扩展骨架；`data-export-resources-only.log` 确认删除宿主 `src/main/java` 后仍可导出。
- `data-host-build-final-exports.log`、`data-host-build-final-nfexports.log`：两个资源宿主构建通过，Java 编译均为 NO-SOURCE；JAR 外层 0 个 class，嵌入 Lib 含自动发现实现。
- `data-content-override-smoke.log`：两端普通入世界通过；覆盖包将一份场景的物品挂载改为标签、加入数据分类，将另一份场景改为缺少能力。两端日志确认只加载 1 份有效场景，错误定位到另一份资源，物品标签同步后再次刷新注册。
- `data-resource-pack-smoke.log`：两端仅装 Lib 与普通客户端资源包、不装完整 Ponderer 或内容模组，均入世界并加载 2 份场景；Fabric 同时移除 JEI，NeoForge 保留 Create 等原有模组。
- 全部实际启动只用 `quickPlaySmoke -PskipBuild -PnoCopy -PsmokeTimeout=180`，使用存档副本，每次结束恢复 JAR、资源包和 options。首次验收的 NeoForge 因旧副本占用被脚本跳过，后两轮使用新副本实际启动；跳过不计入通过。
- 本轮未自动打开 Ponder/第三方界面；逐帧效果、播放期间重载和停用资源包仍需人工验收。

此前拆库验收：

2026-11-14，最终记录以 `build/lib-split/` 下日志为准：

- `build-21.log`：Fabric Lib、产品及本地发布通过，包含客户端 Flywheel 分侧依赖检查；NeoForge 与 Common 的最终结果见下一项。
- `build-20.log`：两端 Lib、两端产品、现有 Common 测试和本地 Maven 发布最终通过（23 秒）。现有 AI 测试 5 项通过，真实 LLM 1 项按原条件跳过；没有新增测试。
- `export-generation-4.log`、`nf-export-generation-1.log`：使用项目现有 69 步示例和本地界面场景实际导出三档。两档 Lib 无省略；Support 对两个界面步骤给出降级说明。
- `export-target-build-4.log`、`nf-export-target-build-2.log`：六个独立目标 Gradle 工程构建通过；Support 不依赖 Lib。
- `api-observations-2.log`：自定义步骤参数执行为 7；未知字段和 interface 别名往返保留；关闭/重载实例 A 后 B 仍有场景和步骤；播放器无请求关闭、两个请求开启、关闭一个仍开启、全部关闭后关闭。
- `host-smoke-JSON_LIB.log`、`host-smoke-JAVA_LIB.log`：两个 Lib 档都在不安装完整 Ponderer 时，两端普通入世界通过。Fabric 同时移除了 JEI，未安装 Create/AE2/Resourcify。NeoForge 保留现有其他模组，检查与 Create 的共存。
- 独立宿主使用存档副本，实例 JAR 在 finally 中恢复；原存档不用于缺失产品注册的验收。旧投影仪注册在副本中缺失会产生预期的恢复告警，不作为 Lib 播放验证。
- `host-smoke-JAVA_SUPPORT-retry.log`：Support 两端入世界通过（55 秒），没有安装 Lib。第一次 NeoForge 失败由宿主旧 Ponder JarJar 身份导致 Create 最低版本不满足，`nf-support-target-build-3.log` 与重跑确认协调后的结果。
- `export-protection-conversion.log`、`converted-support-build.log`：两端从 Support 切到 Lib 再切回 Support 成功；修改扩展或生成源文件均拒绝覆盖并保留内容；生成器自有依赖与未改动骨架正确清理。转换后两端无 Lib 构建通过。
- `startup-policy-off.log`：用户 OFF 覆盖全部播放器请求；Sandbox 与界面捕获 Mixin 返回不应用，核心桥接保留；界面步骤得到带场景/段落/步骤位置的不可用诊断。
- `smoke-final.log`、`smoke-off-final.log`：最终产品默认策略与两个功能均 OFF 时，两端普通入世界全部通过（57 秒、65 秒），每端上限 180 秒，外层上限 600 秒，进程已退出。
- `smoke-final-client-dependencies.log`：Fabric 分侧依赖修正后的最新产品两端入世界通过（52 秒），两个客户端 PID 均已退出。
- 最终制品检查：Fabric 产品/Lib 分别 744/226 个类，NeoForge 为 743/225；两端外层与 Lib 的类、包交集均为 0，没有产品实现泄漏。六个导出 JAR 的 Lib 档只嵌入运行模组，Support 档不包含 Lib。Mixin 配置所列类全部存在，公共/server Mixin 列表为空；Fabric 结构加载与三个条件访问器的 refmap 含预期 class_*/field_* 映射。

`quickPlaySmoke` 是唯一启动游戏的自动验证，只检查普通入世界、超时与进程清理。遵循当前仓库约束，不添加或恢复自动打开界面、播放或捕获的自检。历史自动界面 smoke 结果不作为最新实现的完整验收。

仍需人工验收原生/JSON 故事板的逐帧效果、界面打开/交互/重播，以及独立服务器实际加载。当前只静态核对客户端入口分侧与 Mixin client 列表；没有用普通客户端入世界代替专用服务器验证。远端发布、版本兼容矩阵和长期 API 兼容承诺也不包含在本次本地验证中。
