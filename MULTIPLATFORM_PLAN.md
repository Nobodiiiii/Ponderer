# Ponderer 多平台迁移方案

> 当前分支: `1.20.1forge` → 目标: Forge + Fabric 双平台 (MC 1.20.1)  
> 架构参考: Ponder-1.20.1 (Lotus) + cloth-config (Architectury)  
> 日期: 2026-03-02

## 一、架构选型

### 对比

| 维度 | Architectury (cloth-config 使用) | Lotus (Ponder 使用) | **选择** |
|------|--------------------------------|---------------------|----------|
| Gradle 插件 | `architectury-plugin` + `dev.architectury.loom` (Loom 分支) + shadow | `lotus-gradle` (0.0.12) + 自定义 buildSrc | **Lotus** |
| 平台桥接 | `@ExpectPlatform` 编译时字节码替换，严格包命名约定 | Java SPI `ServiceLoader`，标准 JDK 机制 | **Lotus** |
| 代码共享 | shadow jar 合并 (`transformProductionForge/Fabric`) | 源码直接编译 (`source(commonProject.sourceSets.main.allSource)`) | **Lotus** |
| 注解转换 | architectury-transformer (单独库) | buildSrc ASM transformer (`@ClientOnly` → `@OnlyIn`/`@Environment`) | **Lotus** |
| 与 Ponder 依赖兼容 | 需额外适配 | 天然一致 (同架构) | **Lotus** |
| 网络抽象 | 三层 API (NetworkManager/Channel/Simple) 重封装 | `BasePacket` + `NetworkHelper` SPI 薄封装 | **Lotus** |

### 结论

**采用 Lotus 架构**。理由:
1. 比 Architectury 轻量 (1 个 buildSrc vs 3 个外部插件)
2. 与项目依赖的 Ponder 库使用同一架构
3. ServiceLoader 是标准 JDK 机制，调试容易
4. 源码直接编译避免 remap/shadow 带来的构建复杂度

## 二、目录结构

```
ponderer/
├── Common/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/nododiiiii/ponderer/
│       │   ├── Ponderer.java               ← 共享初始化 (无 @Mod)
│       │   ├── Config.java                 ← ForgeConfigSpec (两平台通用)
│       │   ├── ModKeyBindings.java         ← 键位常量
│       │   ├── network/
│       │   │   ├── BasePacket.java         ← 网络抽象
│       │   │   ├── ServerboundPacket.java
│       │   │   ├── ClientboundPacket.java
│       │   │   ├── PondererPackets.java    ← 声明 packet ID
│       │   │   ├── UploadScenePacket.java  ← 6 个 packet
│       │   │   ├── SyncRequestPacket.java
│       │   │   ├── SyncResponsePacket.java
│       │   │   ├── DownloadStructurePacket.java
│       │   │   ├── DownloadStructureResultPacket.java
│       │   │   └── UploadResponsePacket.java
│       │   ├── platform/
│       │   │   ├── PondererServices.java   ← ServiceLoader 入口
│       │   │   └── services/
│       │   │       ├── NetworkHelper.java
│       │   │       └── PlatformHelper.java
│       │   ├── ponder/                     ← 全部移入
│       │   ├── ui/                         ← 全部移入
│       │   ├── ai/                         ← 全部移入
│       │   ├── blueprint/                  ← 全部移入 (事件逻辑改为回调)
│       │   ├── mixin/                      ← 全部移入
│       │   └── compat/jei/                 ← 通用 JEI API 文件
│       └── resources/
│           ├── ponderer.mixins.json
│           └── assets/ponderer/
│
├── Forge/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/nododiiiii/ponderer/forge/
│       │   ├── PondererForge.java          ← @Mod 入口
│       │   ├── PondererForgeClient.java    ← 客户端事件
│       │   ├── ForgeNetworkHelper.java     ← SimpleChannel 实现
│       │   ├── ForgePlatformHelper.java    ← FMLPaths/ModList
│       │   ├── PondererForgeNetwork.java   ← SimpleChannel 注册
│       │   └── ForgeRegistration.java      ← DeferredRegister
│       └── resources/
│           ├── META-INF/
│           │   ├── mods.toml
│           │   └── services/               ← SPI 声明
│           └── pack.mcmeta
│
├── Fabric/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/nododiiiii/ponderer/fabric/
│       │   ├── PondererFabric.java         ← ModInitializer
│       │   ├── PondererFabricClient.java   ← ClientModInitializer
│       │   ├── FabricNetworkHelper.java    ← Fabric API 网络
│       │   ├── FabricPlatformHelper.java   ← FabricLoader
│       │   ├── PondererFabricNetwork.java  ← 注册
│       │   └── FabricRegistration.java     ← Registry.register()
│       └── resources/
│           ├── fabric.mod.json
│           ├── META-INF/services/          ← SPI 声明
│           └── pack.mcmeta
│
├── buildSrc/
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── src/main/kotlin/com/nododiiiii/ponderer/gradle/
│       ├── PondererGradlePlugin.kt
│       └── transformers/
│           └── EnvironmentAnnotationTransformer.kt
│
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 三、构建系统

### settings.gradle
```groovy
include "Common", "Forge", "Fabric"
```

### Gradle 插件分工

| 模块 | 插件 |
|------|------|
| Common | `org.spongepowered.gradle.vanilla` 0.2.1-SNAPSHOT |
| Forge | `net.minecraftforge.gradle` [6.0, 6.2) + `org.spongepowered.mixin` 0.7 + parchment |
| Fabric | `fabric-loom` 1.10.+ |
| 全部子项目 | `com.nododiiiii.ponderer.gradle` (自定义, 含 Lotus) |

### buildSrc

复用 Ponder-1.20.1 的 Lotus + ASM 转换模式:
- `lotus-gradle:0.0.12` — 多平台源码编译框架
- `EnvironmentAnnotationTransformer` — `@ClientOnly` → 平台注解

### Common 源码共享

平台模块直接编译 Common 源码 (非 shadow):
```groovy
tasks.withType(JavaCompile).configureEach {
    source(commonProject.sourceSets.main.allSource)
}
processResources {
    from commonProject.sourceSets.main.resources
}
```

## 四、平台服务 (SPI)

### PlatformHelper 接口
```java
public interface PlatformHelper {
    Path getConfigDir();           // Forge: FMLPaths.CONFIGDIR / Fabric: FabricLoader.getConfigDir()
    Path getGameDir();             // Forge: FMLPaths.GAMEDIR / Fabric: FabricLoader.getGameDir()
    boolean isModLoaded(String id); // Forge: ModList / Fabric: FabricLoader
    boolean isClient();            // Forge: FMLEnvironment.dist / Fabric: FabricLoader.getEnvType()
}
```

### NetworkHelper 接口
```java
public interface NetworkHelper {
    void sendToServer(BasePacket packet);
    void sendToPlayer(ServerPlayer player, BasePacket packet);
    void sendToAllPlayers(MinecraftServer server, BasePacket packet);
}
```

### 加载方式
```java
public class PondererServices {
    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final NetworkHelper NETWORK = load(NetworkHelper.class);
    
    private static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz).findFirst()
            .orElseThrow(() -> new ServiceConfigurationError("No impl for " + clazz));
    }
}
```

## 五、网络层

### Common 抽象 (FriendlyByteBuf 模式, 1.20.1)
```java
public interface BasePacket {
    void write(FriendlyByteBuf buf);
    ResourceLocation getId();
}

public interface ServerboundPacket extends BasePacket {
    void handle(@Nullable MinecraftServer server, @Nullable ServerPlayer player);
}

public interface ClientboundPacket extends BasePacket {
    // handle 在静态内部类中,避免服务端加载客户端类
}
```

### 6 个 Packet 迁移

| Packet | 方向 | 变化 |
|--------|------|------|
| `UploadScenePacket` | C→S | 移除 `PacketDistributor` → `PondererServices.NETWORK.sendToPlayer()` |
| `SyncRequestPacket` | C→S | handle 中发送响应改用 NetworkHelper |
| `DownloadStructurePacket` | C→S | 同上 |
| `SyncResponsePacket` | S→C | Handler 内部类隔离客户端类 |
| `DownloadStructureResultPacket` | S→C | 同上 |
| `UploadResponsePacket` | S→C | 同上 |

### Forge 实现
- `PondererForgeNetwork`: `NetworkRegistry.newSimpleChannel()` + `messageBuilder()` 注册
- `ForgeNetworkHelper`: `CHANNEL.send(PacketDistributor.PLAYER.with(...), packet)`

### Fabric 实现
- `PondererFabricNetwork`: `ServerPlayNetworking.registerGlobalReceiver()` + `ClientPlayNetworking.registerGlobalReceiver()`
- `FabricNetworkHelper`: `PacketByteBufs.create()` → `packet.write(buf)` → `ServerPlayNetworking.send()`

## 六、配置系统

保留 `ForgeConfigSpec` 在 Common 层。Fabric 端通过 `forgeconfigapiport-fabric:8.0.0` 提供兼容。

Ponder-1.20.1 已验证此方案可行 (Common 的 `ForgeConfigSpec` 在 Fabric 通过 ForgeConfigAPIPort 正常工作)。

未来可选集成 Cloth Config 作为配置 GUI 层。

## 七、事件系统

| 功能 | Forge | Fabric |
|------|-------|--------|
| 客户端 tick | `TickEvent.ClientTickEvent` + `Phase.END` | `ClientTickEvents.END_CLIENT_TICK` |
| 鼠标滚轮 | `InputEvent.MouseScrollingEvent` + `setCanceled` | Mixin `MouseHandler.onScroll` |
| 鼠标点击 | `InputEvent.MouseButton.Pre` + `setCanceled` | Fabric Screen API |
| 键位注册 | `RegisterKeyMappingsEvent` | `KeyBindingHelper.registerKeyBinding()` |
| 客户端命令 | `RegisterClientCommandsEvent` | `ClientCommandRegistrationCallback.EVENT` |
| 物品注册 | `DeferredRegister` + `RegistryObject` | `Registry.register(BuiltInRegistries.ITEM, ...)` |
| 创造标签 | `BuildCreativeModeTabContentsEvent` | `ItemGroupEvents.modifyEntriesEvent()` |
| 玩家登录 | `PlayerEvent.PlayerLoggedInEvent` | `ServerPlayConnectionEvents.JOIN` |
| Config 注册 | `ModLoadingContext.registerConfig()` | `ForgeConfigRegistry.INSTANCE.register()` |

## 八、JEI 兼容

- 4 个通用文件 (`JeiIngredientHelper`, `JeiIngredientScreenElement`, `JeiAwareGhostHandler`, `StepEditorGhostHandler`) 直接放 Common
- `JeiCompat.isAvailable()` 改用 `PondererServices.PLATFORM.isModLoaded("jei")`
- `PondererJeiPlugin` 中的 Forge `ScreenEvent` 监听需平台分离

Forge: `mezz.jei:jei-1.20.1-forge-api` / Fabric: `mezz.jei:jei-1.20.1-fabric-api`

## 九、版本迁移路径

### 1.20.1 → 1.21.x

| 变更点 | 说明 |
|--------|------|
| Gradle | VanillaGradle → NeoForm; ForgeGradle → NeoForge ModDev; Java 17→21 |
| 网络 | `BasePacket` (FriendlyByteBuf) → `BasePacketPayload` (CustomPacketPayload + StreamCodec) |
| 平台 | `Forge/` 替换为 `NeoForge/`; `net.minecraftforge.*` → `net.neoforged.*` |
| SPI | 接口不变, 仅实现类改包路径 |
| Config | `ForgeConfigSpec` → `ModConfigSpec` |
| Common 共享 | 改用 `configurations {}` 导出方式 |

### backport 到 1.18.2

- SPI 接口不依赖 1.20+ API
- `BasePacket` 的 `FriendlyByteBuf` 在 1.18.2 也存在
- 主要差异: `ResourceLocation` 构造器、部分 GUI API
- 在 Common 层通过版本专属分支处理

## 十、依赖总览

| 依赖 | Common | Forge | Fabric |
|------|--------|-------|--------|
| Minecraft 1.20.1 | ✅ (VanillaGradle) | ✅ (ForgeGradle) | ✅ (Loom) |
| Forge 47.2.6 | - | ✅ | - |
| Fabric API 0.92.3 | - | - | ✅ |
| Fabric Loader 0.16.9 | - | - | ✅ |
| Ponder | compileOnly (Common) | impl + jarJar (Forge) | modImpl + include (Fabric) |
| Flywheel | compileOnly (Common API) | impl + jarJar (Forge) | modImpl + include (Fabric) |
| JEI | compileOnly (common-api) | compileOnly (forge-api) | modCompileOnly (fabric-api) |
| ForgeConfigAPIPort | compileOnly (common) | - (Forge 自带) | modApi + include |
| Porting Lib base | - | - | modApi + include (Ponder 传递) |
| Mixin 0.8.4/0.8.5 | compileOnly | annotationProcessor | (Loom 内置) |
