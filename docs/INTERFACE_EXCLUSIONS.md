# 嵌入界面的控件排除规则

规则用于插入界面、界面面板及框选页中的沙盒界面。整合包可用客户端资源包提供，模组可直接放入自己的 assets。规则不影响正常打开的机器界面、编辑器的 JEI 原料选择器或宿主 Ponder 页的 JEI 投放。

## 添加与覆盖

文件位置为 `assets/<命名空间>/ponderer/interface_exclusions/<规则名>.json`。不同文件的规则同时生效；同一命名空间和路径按资源包优先级覆盖整个文件，不合并数组。支持 F3+T 资源重载，加载器归属与类匹配缓存也会随之刷新。规则是客户端资源，不放在服务器数据包的 `data/` 目录。

例如，整合包要排除 `examplemod` 提供的外来控件，可在资源包内添加 `assets/my_pack/ponderer/interface_exclusions/examplemod.json`：

```json
{
  "format_version": 1,
  "mod_id": "examplemod"
}
```

资源包根目录需有 `pack.mcmeta`。Minecraft 1.21.1 示例：

```json
{
  "pack": {
    "pack_format": 34,
    "description": "My interface exclusion rules"
  }
}
```

模组作者使用相同 JSON 格式，放在 `src/main/resources/assets/<modid>/ponderer/interface_exclusions/`。

## 精确限制范围

```json
{
  "format_version": 1,
  "mod_id": "examplemod",
  "enabled": true,
  "classes": ["com.example.client.GlobalSidebar"],
  "packages": ["com.example.client.overlay."],
  "exclude_own_screens": false
}
```

| 字段 | 默认值与含义 |
| --- | --- |
| `format_version` | `1`；当前支持的格式版本。 |
| `mod_id` | 必填。无类/包选择器时，按加载器提供的该模组根目录是否含有控件的具体类文件判定，不猜测包名。 |
| `enabled` | `true`；设为 `false` 关闭该文件的规则。 |
| `classes` | 空列表。Java 类全名，包含该类的子类；内部类使用 `$`。 |
| `packages` | 空列表。Java 包名前缀，必须以 `.` 结束，避免误匹配同名前缀。 |
| `exclude_own_screens` | `false`；默认保留该模组自身 Screen 内的控件。设为 `true` 才同时过滤该模组自己的界面。 |

`classes` 和 `packages` 是“或”的关系。只要任一列表非空，就使用这些显式选择器限定排除范围；两者都为空时才按 `mod_id` 匹配整模组。显式选择器仍使用 `mod_id` 判断是否是该模组自身界面。关闭规则应使用 `enabled: false`，清空选择器会恢复整模组匹配。

不按初始化事件的先后判断控件归属。未知控件默认保留，其他模组通过事件给自己的界面加按钮不会因此被自动排除。按模组归属识别的是类文件来源：某模组直接创建的原版 `Button` 仍是原版类，不能据此反推出创建者；多个模组 ID 共用同一根目录时也无法仅凭类文件区分。此类情况应提供明确的自定义控件类，或使用精确选择器，避免笼统过滤原版控件基类。

## 内置 JEI 与 FTB 规则

两者都由同一资源规则加载器控制。标准控件使用 `InterfaceExclusions.isExcluded`，JEI 的 NeoForge 兼容沿用原有容器事件路径，由整模组规则提供开关；不对 JEI 类使用 Mixin。

| 资源文件 | 默认行为 |
| --- | --- |
| `assets/pondererlib/ponderer/interface_exclusions/jei.json` | 排除 JEI 标准控件；NeoForge 安装了 JEI 且存在启用的 JEI 整模组规则时，启用原有的沙盒容器渲染事件屏蔽。覆盖为 `enabled: false` 可关闭。Fabric 保留原有 JEI 事件路径。 |
| `assets/pondererlib/ponderer/interface_exclusions/ftb.json` | 只排除 FTB Library 的 `dev.ftb.mods.ftblibrary.sidebar.SidebarGroupGuiButton` 及其子类，不排除 FTB Library 的其他界面控件。FTB Teams 等模组共享的侧栏由此规则处理。 |

例如，想保留 FTB 侧栏，在更高优先级资源包的 **同一路径** `assets/pondererlib/ponderer/interface_exclusions/ftb.json` 写入：

```json
{
  "enabled": false
}
```

关闭 JEI 排除同理，覆盖 `jei.json`。换一个命名空间添加 `enabled: false` 不会关闭内置文件；禁用只影响被覆盖的那条规则，其他文件中仍启用的匹配规则继续生效。

JEI 的 NeoForge 事件开关使用 `InterfaceExclusions.isWholeModExcluded("jei")`：启用的 `mod_id: "jei"` 规则必须没有类/包选择器。仅限定具体类或包的规则只过滤相应标准控件，不开启整组事件屏蔽。`exclude_own_screens` 用于标准控件判定，不改变这条原有事件路径。未安装 JEI 时，该事件兼容不启用。

这条兼容路径仍注入 Minecraft 的 `AbstractContainerScreen`，在沙盒内跳过发布 `ContainerScreenEvent.Render`；不会注入 JEI 类或引用其内部绘制方法。开启时该阶段的其他模组监听器也不会执行，这是原有整组事件屏蔽的范围；宿主屏幕自己的事件照常发布。

## 生效边界

被匹配的标准控件不参与面板显示边界、`Screen.render` 的子控件绘制、`AbstractWidget.render` 绘制及标准 children 输入路由，已持有的排除控件焦点也会在下次沙盒调用前清除。原始控件列表、菜单槽位和脚本槽位编号不被删除或改写。更改排除规则会改变完整显示边界，之前保存的裁剪范围可能需要重新框选。

JEI 使用上文的加载器兼容开关，保留原有路径。其他模组若绕过标准控件、直接在事件中自绘，JSON 本身无法取消它的任意 Java 绘制；该事件处理器可主动接入同一个判定，例如在绘制前调用：

```java
if (InterfaceExclusions.isExcluded(this, screen)) return;
```

该判定只在对应嵌入界面的沙盒作用域中返回排除；正常界面及宿主外层渲染不受影响。模组不存在时无需安装它，JSON 中的类名不会触发可选依赖类加载。

非法 JSON、未知字段或无效字段会在日志输出 `Invalid interface exclusion rule <资源位置>`，跳过该文件；其他文件仍正常加载。

## 验证

使用运行库、NeoForge/Fabric 的 scoped build，检查 Fabric refmap，并运行已有 `quickPlaySmoke` 验证普通进世界与进程清理。冒烟日志中的 `Ponderer Lib loaded ... interface exclusion rules` 可确认资源加载器已运行。规则开关、F3+T 后的界面变化、焦点及输入、FTB 视觉边界和其他模组自绘兼容性仍需游戏内手动检查；不添加自动打开界面的测试。
