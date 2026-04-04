# Ponderer UI Framework 重构方案

## 1. 目标

本方案的目标不是“继续修补现有几十个 Screen”，而是构建一套新的、覆盖全模组 UI 的统一 framework，并最终完成对现有 UI 的完全替换。

硬性要求如下：

- 初版视觉样式 **完全 copy Catnip config screen**
- 参考 Catnip config UI 的定义方式，重建一套面向本模组的 entry/screen framework
- 实现所有本模组现有的自定义 entry 和交互能力
- 保留未来修改样式、扩展样式、分屏幕定制样式的能力，且 **不能影响 config 类 UI 的稳定性**
- 对本模组所有 UI 生效，包括但不限于：
  - 配置页
  - 列表页
  - 选择器/菜单页
  - 编辑器页
  - 工作流页
  - 导入导出页
  - AI 相关页
- 为每个大类定义独立的基础 screen
- 最终删除旧 UI 体系中的大部分重复实现，完成 UI 的完全替换

## 2. 总体决策

### 2.1 核心结论

不直接把 `BaseConfigScreen` / `SubMenuConfigScreen` 当作所有编辑器页的父类来硬套。

而是：

- **完全复制 Catnip config screen 的视觉和交互壳**
- **借鉴 Catnip 的“entry 驱动定义方式”**
- **重建一套独立于 ForgeConfigSpec 的通用 UI framework**

这样做的原因是：

- Catnip config UI 的数据模型天然绑定 `ForgeConfigSpec.ConfigValue + ValueSpec`
- 本模组大量编辑器页处理的是 `DslScene`、`DslStep`、快照恢复、JEI 注入、坐标拾取、NBT 捕获、Undo/Redo、动态列表等复杂工作流
- 如果直接用 Catnip config screen 原件承载这些业务，最终会产生大量“反向适配胶水”，维护成本会再次上升

所以本次重构的方向应当是：

- **视觉 copy Catnip**
- **结构学习 Catnip**
- **实现独立于 Catnip config 数据模型的 framework**

## 3. 参考基线

初版 UI 的视觉和交互基线，直接以以下 Catnip / Ponder 类为参照对象：

- `net.createmod.catnip.config.ui.ConfigScreen`
- `net.createmod.catnip.config.ui.BaseConfigScreen`
- `net.createmod.catnip.config.ui.SubMenuConfigScreen`
- `net.createmod.catnip.config.ui.ConfigModListScreen`
- `net.createmod.catnip.config.ui.ConfigScreenList`
- `net.createmod.catnip.config.ui.entries.ValueEntry`
- `net.createmod.catnip.config.ui.entries.BooleanEntry`
- `net.createmod.catnip.config.ui.entries.NumberEntry`
- `net.createmod.catnip.config.ui.entries.EnumEntry`
- `net.createmod.catnip.config.ui.entries.StringEntry`
- `net.createmod.catnip.config.ui.HintableTextFieldWidget`

初版需要复制的内容包括：

- 整体背景层
- 中央内容区域布局
- 标题条渐变和位置
- list 宽度、行高、边距、按钮尺寸
- 搜索框样式与位置
- 禁用态、悬停态、激活态
- entry 行结构
- 工具提示风格
- 图标使用方式
- 滚动和滚动条行为
- 保存/重置/返回这类动作按钮的视觉语言

要求是“像”，不是“大致相似”，而是把它当成第一版的视觉规格。

## 4. 现有 UI 范围

当前需要被新 framework 覆盖的 UI 大类如下。

### 4.1 Step 表单编辑页

这类页面当前大多继承 `AbstractStepEditorScreen`，数量最多，也是重复逻辑最集中的部分。

- `ChangeInterfaceSlotScreen`
- `ClearEntitiesScreen`
- `ClickInterfaceScreen`
- `CreateEntityScreen`
- `CreateItemEntityScreen`
- `DestroyBlockScreen`
- `EncapsulateBoundsScreen`
- `HighlightSectionScreen`
- `IdleScreen`
- `IndicateEffectScreen`
- `ModifyBlockEntityNbtScreen`
- `ModifyEntitiesNbtScreen`
- `PlaySoundScreen`
- `ReplaceBlocksScreen`
- `RotateCameraScreen`
- `SceneDescEditorScreen`
- `SectionTransformScreen`
- `SelectionOperationScreen`
- `SetBlockScreen`
- `SharedTextScreen`
- `ShowControlsScreen`
- `ShowInterfaceScreen`
- `ShowStructureScreen`
- `TextStepScreen`
- `TriggerEditorScreen`
- `ZoomSceneScreen`

### 4.2 工作流/编辑器页

- `SceneEditorScreen`
- `FunctionScreen`
- `CommandParamScreen`
- `AiGenerateScreen`
- `ExportPackScreen`

### 4.3 列表/浏览/选择页

- `PonderItemGridScreen`
- `StructureListScreen`
- `ImportPackScreen`
- `StepTypeSelectorScreen`
- `SceneTypeSelectorScreen`
- `PonderItemGridScreen.SceneIdListScreen`

### 4.4 配置页

- `AiConfigScreen`
- 未来如需扩展，本模组所有 `ForgeConfigSpec` 页面都进入这一类

### 4.5 菜单/入口页

本模组后续应具备统一入口 screen 能力，即使当前入口页分散在各处，也统一纳入新 framework 的 menu/hub 体系。

### 4.6 Modal / Toast / 轻提示

这部分也必须纳入新 framework，而不是继续散落在各个 screen 的临时逻辑中。

需要覆盖：

- 确认弹窗
- 三态确认弹窗
- 输入型弹窗
- 非阻塞 toast
- 页内顶部/底部通知条
- 自动消失的状态提示
- 错误/成功/警告提示

## 5. 新 Framework 的设计原则

### 5.1 视觉层和数据层彻底分离

新的 framework 必须拆成三层：

- **Screen Chrome 层**
  - 背景、标题、工具栏、按钮壳、搜索框、滚动区、列表容器
- **Entry / Cell 层**
  - 一行或一个单元如何布局、渲染、响应交互
- **State Adapter 层**
  - 数据从哪里来、如何保存、如何撤销、如何重置、如何验证

任何样式修改，都不能要求改动 ConfigValue、DslStep、PickState 之类的数据层代码。

### 5.2 初版先统一，再个性化

第一阶段不追求“更好看”，只追求：

- 所有页面统一
- 统一后可维护
- 统一后可拓展
- 统一后视觉完整复制 Catnip config screen

个性化视觉是第二阶段能力，不是第一阶段目标。

### 5.3 所有业务 Screen 不再直接手写底层控件

重构完成后，业务 screen 原则上不应直接 `new PonderButton`、`new SoftHintTextFieldWidget`、手写滚动条、手写 scissor、手写行布局。

业务 screen 只能做三件事：

- 声明 sections / entries / actions
- 绑定状态对象
- 处理业务事件

### 5.4 全量替换，不保留双轨长期共存

允许短期迁移期双轨存在，但最终目标必须是：

- 旧 `AbstractStepEditorScreen` 退役
- 大多数旧 screen 的布局逻辑删除
- 新 framework 成为唯一 UI 体系

## 6. 新 Framework 的分层架构

建议新建包，例如：

```text
com.nododiiiii.ponderer.ui.framework
```

建议分层如下：

```text
ui/framework/
  core/
  theme/
  chrome/
  entry/
  list/
  screen/
  adapter/
  bridge/
  modal/
```

### 6.1 `core`

负责通用生命周期和上下文。

建议包含：

- `PondererUiContext`
- `PondererScreenState`
- `ScreenRoute`
- `ScreenSession`
- `DirtyStateTracker`

### 6.2 `theme`

负责样式 token 和 preset。

建议包含：

- `PondererTheme`
- `ThemeTokens`
- `ThemePreset`
- `CatnipBaselineTheme`
- `ThemeVariant`
- `ThemeResolver`

### 6.3 `chrome`

负责“像 Catnip config screen”的外壳。

建议包含：

- `ScreenChromeRenderer`
- `TitleBannerRenderer`
- `ToolbarRenderer`
- `SearchBarRenderer`
- `ActionBarRenderer`
- `ScrollAreaRenderer`
- `ListContainerRenderer`

### 6.4 `entry`

负责通用 entry 定义和渲染。

建议包含：

- `EditorEntry<T>`
- `EntryFactory`
- `EntryLayout`
- `EntrySection`
- `EntryGroup`
- `EntryAction`
- `EntryValidation`
- `EntryRenderer`

### 6.5 `list`

负责列表页、网格页、菜单页、浏览页的 cell 模型。

建议包含：

- `ListCell<T>`
- `GridCell<T>`
- `SelectorOption<T>`
- `ListDataSource<T>`
- `FilterableListModel<T>`
- `SearchController`

### 6.6 `adapter`

负责把不同数据源适配成统一 entry 接口。

建议包含：

- `ValueHandle<T>`
- `ConfigValueHandle<T>`
- `PojoValueHandle<T>`
- `MappedValueHandle<T>`
- `TransientValueHandle<T>`
- `CollectionValueHandle<T>`
- `ValidationResult`

### 6.7 `bridge`

负责把 framework 接进现有复杂工作流。

建议包含：

- `JeiEntryBridge`
- `PickEntryBridge`
- `NbtPickEntryBridge`
- `CoordPickBridge`
- `UndoBridge`
- `SceneEditorBridge`

### 6.8 `modal`

负责弹窗、toast 和轻提示。

建议包含：

- `ModalHost`
- `ModalSpec`
- `ModalAction`
- `FrameworkModalScreen`
- `ToastHost`
- `ToastSpec`
- `ToastRenderer`
- `NotificationCenter`
- `InlineNoticeModel`

## 7. 样式方案

### 7.1 初版样式策略

新增一个锁定的样式预设：

- `ThemePreset.CATNIP_CONFIG_EXACT`

该预设在 V1 中要求：

- 默认所有新 framework 页面都使用该预设
- 视觉规格完全以 Catnip config screen 为准
- 不允许业务 screen 自己改 spacing、按钮尺寸、标题条逻辑

### 7.2 后续可定制策略

在 V1 稳定后，允许额外预设，例如：

- `ThemePreset.CATNIP_CONFIG_EXACT`
- `ThemePreset.PONDERER_EDITOR`
- `ThemePreset.PONDERER_MENU`
- `ThemePreset.PONDERER_COMPACT`

但必须满足：

- config 页面默认继续锁在 `CATNIP_CONFIG_EXACT`
- 编辑器页可选择变体
- 自定义样式只能通过 token / renderer override / preset 组合实现
- 禁止直接改 config screen 专用 renderer 的默认行为来影响所有页面

### 7.3 关键设计点

为了保证“能改样式但不影响 config 能力”，主题系统必须满足：

- theme 决定渲染，不决定状态模型
- entry 决定交互，不决定存储介质
- config 页面和 editor 页面可以共享 entry 类型，但不共享必须写死的样式分支

## 8. Entry 系统设计

新 framework 的核心是：所有表单页、部分工作流页、部分列表页，都通过 entry/cell 定义，而不是每个 screen 手写控件。

### 8.1 直接对齐 Catnip 的基础 entry

以下 entry 需要先做出与 Catnip 对齐的版本：

- `BooleanEntry`
- `NumberEntry`
- `EnumEntry`
- `StringEntry`
- `SubMenuEntry`
- `ActionEntry`

### 8.2 本模组必须补齐的自定义 entry

这些是当前旧 UI 里的真实需求，必须在新 framework 中做成一等公民，而不是临时 patch。

- `TextEntry`
- `TextWithHintEntry`
- `TextWithJeiEntry`
- `TextWithJeiAndBlockPickEntry`
- `TextWithJeiAndHeldItemEntry`
- `TextWithJeiAndNbtPickEntry`
- `NbtTextEntry`
- `NumberTextEntry`
- `ToggleEntry`
- `CycleButtonEntry`
- `TextWithLanguageToggleEntry`
- `XyzEntry`
- `XyzPickEntry`
- `PointPickEntry`
- `BoundsPickEntry`
- `BlockPropertiesEntry`
- `DynamicKeyValueListEntry`
- `NoticeEntry`
- `InfoBannerEntry`
- `SectionHeaderEntry`
- `ToolbarActionEntry`

### 8.3 工作流类 entry / cell

工作流页和列表页还需要专门的 cell，不应复用普通表单 entry。

建议实现：

- `StepRowCell`
- `FunctionRowCell`
- `StructureRowCell`
- `PackRowCell`
- `SceneRowCell`
- `GridItemCell`
- `SelectableTagCell`
- `ScenePreviewCell`
- `MultiActionListCell`

### 8.4 Entry 统一接口

建议统一抽象如下：

```java
interface ValueHandle<T> {
    T get();
    void set(T value);
    boolean isDirty();
    boolean canReset();
    void reset();
    ValidationResult validate(T value);
    void commit();
    void discard();
}
```

然后所有 entry 都只依赖 `ValueHandle<T>`，不直接依赖：

- `ForgeConfigSpec.ConfigValue`
- `DslScene.DslStep`
- 某个 screen 的私有字段

这样可以保证：

- 同一个 entry 可以同时服务配置页和编辑器页
- 样式变化不影响数据模型
- 保存/放弃/撤销可以统一

### 8.5 Modal / Toast 定义方式

modal 和 toast 也必须走 spec 定义，而不是每个页面自己 new 一个临时 screen。

建议定义方式：

```java
ModalSpec.confirm()
    .title("Unsaved Changes")
    .text(...)
    .onConfirm(...)
    .onCancel(...);
```

```java
ToastSpec.info()
    .title("Saved")
    .message(...)
    .duration(2400);
```

toast 的视觉基线直接参考 Catnip 的 `ConfirmationScreen`：

- 沿用同类背景板和文字排布
- 去掉按钮区
- 保留标题/正文/边框语言
- 改为自动消失
- 改为非阻塞显示
- 不接管当前 screen

也就是说，toast 不是重新发明一套视觉，而是把 Catnip confirm dialog 的壳“无按钮化 + 非阻塞化 + 自动消失化”。

## 9. Screen 基类设计

按大类定义基础 screen。

### 9.1 `FrameworkConfigScreen`

用途：

- 面向 `ForgeConfigSpec` 的配置页
- 完全模拟 Catnip config 页行为

负责：

- 搜索
- 保存
- 放弃
- 重置
- dirty 状态
- config path 导航

目标页面：

- `AiConfigScreen`
- 后续所有配置页

### 9.2 `FrameworkFormScreen`

用途：

- 单页表单编辑
- step 编辑器和普通参数编辑页的基础类

负责：

- section / entry 布局
- 可滚动表单区域
- 底部动作栏
- 错误/提示信息
- 快照恢复
- dirty 状态

目标页面：

- 所有当前 `AbstractStepEditorScreen` 子类
- `SceneDescEditorScreen`
- `TriggerEditorScreen`

### 9.3 `FrameworkSelectorScreen`

用途：

- 类型选择、场景选择、结构选择、短菜单页

负责：

- 选项列表
- 搜索
- 选中态
- 小型动作栏
- 快捷键导航

目标页面：

- `StepTypeSelectorScreen`
- `SceneTypeSelectorScreen`
- `StructureListScreen`
- `PonderItemGridScreen.SceneIdListScreen`

### 9.4 `FrameworkListScreen`

用途：

- 搜索 + 列表 / 网格浏览页

负责：

- 列表或网格视图
- 搜索过滤
- 排序
- 单选/多选
- toolbar 动作

目标页面：

- `PonderItemGridScreen`
- `ImportPackScreen`

### 9.5 `FrameworkWorkspaceScreen`

用途：

- 复杂编辑器页面
- 包含列表、预览、工具栏、状态栏、次级表单区域

负责：

- 左右/上下工作区布局
- 工具栏
- 状态栏
- 列表区域
- 详情面板
- 与 undo/redo、JEI、pick 等 bridge 集成

目标页面：

- `SceneEditorScreen`
- `FunctionScreen`
- `CommandParamScreen`
- `AiGenerateScreen`
- `ExportPackScreen`

### 9.6 `FrameworkMenuScreen`

用途：

- 模组入口页
- 功能汇总页
- 操作枢纽页

负责：

- menu cards
- 功能按钮
- 二级跳转
- 模组级导航入口

目标页面：

- 统一后的模组菜单/入口类页面

### 9.7 `FrameworkModalScreen`

用途：

- 小弹窗
- 确认页
- 输入弹框

负责：

- 居中展示
- 简短动作栏
- 统一样式 modal

该基础类初版视觉直接对齐 Catnip 的 `ConfirmationScreen`。

### 9.8 `FrameworkToastHost`

用途：

- 全局或页面级轻提示
- 保存成功、校验失败、同步结果、复制成功等反馈

负责：

- toast 队列
- 自动消失
- 非阻塞叠加显示
- 屏幕边角停靠
- success / warning / error / info 变体

实现原则：

- 直接复用 `ConfirmationScreen` 的视觉壳
- 删除按钮区域
- 不替换当前 screen
- 改为 overlay host 管理

## 10. 现有页面的目标归类

### 10.1 迁移到 `FrameworkConfigScreen`

- `AiConfigScreen`

### 10.2 迁移到 `FrameworkFormScreen`

- `ChangeInterfaceSlotScreen`
- `ClearEntitiesScreen`
- `ClickInterfaceScreen`
- `CreateEntityScreen`
- `CreateItemEntityScreen`
- `DestroyBlockScreen`
- `EncapsulateBoundsScreen`
- `HighlightSectionScreen`
- `IdleScreen`
- `IndicateEffectScreen`
- `ModifyBlockEntityNbtScreen`
- `ModifyEntitiesNbtScreen`
- `PlaySoundScreen`
- `ReplaceBlocksScreen`
- `RotateCameraScreen`
- `SceneDescEditorScreen`
- `SectionTransformScreen`
- `SelectionOperationScreen`
- `SetBlockScreen`
- `SharedTextScreen`
- `ShowControlsScreen`
- `ShowInterfaceScreen`
- `ShowStructureScreen`
- `TextStepScreen`
- `TriggerEditorScreen`
- `ZoomSceneScreen`

### 10.3 迁移到 `FrameworkSelectorScreen`

- `StepTypeSelectorScreen`
- `SceneTypeSelectorScreen`
- `StructureListScreen`
- `PonderItemGridScreen.SceneIdListScreen`

### 10.4 迁移到 `FrameworkListScreen`

- `PonderItemGridScreen`
- `ImportPackScreen`

### 10.5 迁移到 `FrameworkWorkspaceScreen`

- `SceneEditorScreen`
- `FunctionScreen`
- `CommandParamScreen`
- `AiGenerateScreen`
- `ExportPackScreen`

### 10.6 接入 `FrameworkModalScreen` / `FrameworkToastHost`

最终要求是所有页面都接入统一 modal/toast 能力。

典型场景包括：

- 删除确认
- 放弃修改确认
- 导入导出结果提示
- 保存成功提示
- 复制成功提示
- 校验错误提示
- AI 请求结果提示
- 同步/上传结果提示

## 11. 数据与状态适配方案

新的 framework 不应再把数据存储方式写死在 screen 中。

### 11.1 Config 页面

通过 `ConfigValueHandle<T>` 适配：

- `ForgeConfigSpec.ConfigValue<T>`
- `ForgeConfigSpec.ValueSpec`

支持：

- reset
- commit
- discard
- dirty
- tooltip/comment
- range
- metadata annotation

### 11.2 Step 编辑页面

通过 `PojoValueHandle<T>` 或 `MappedValueHandle<T>` 适配：

- `DslScene.DslStep`
- 页面局部状态
- 中间态对象

支持：

- build step
- validate
- snapshot/restore
- delayed commit

### 11.3 工作流页面

通过 `TransientValueHandle<T>` / `CollectionValueHandle<T>` 适配：

- 搜索条件
- 当前选择项
- 多选列表
- URL 列表
- 导出场景列表

### 11.4 Dirty / Save / Discard 统一语义

必须统一定义：

- config 页的 save/discard
- step 编辑页的 confirm/cancel
- 工作流页的 apply/back

建议由 framework 提供统一的 action bar 语义：

- `PRIMARY_COMMIT`
- `SECONDARY_CANCEL`
- `RESET`
- `DISCARD`
- `BACK`
- `APPLY`

## 12. 复杂交互桥接

本模组的复杂编辑器功能不是“附加项”，而是 framework 必须内建支持的能力。

### 12.1 JEI 集成

提供统一 `JeiEntryBridge`：

- entry 声明自己是否可接收 JEI 选择
- framework 统一维护 active target
- 不再让每个 screen 自己写 JEI target 管理

### 12.2 坐标拾取

提供统一 `PickEntryBridge`：

- 支持 point
- 支持 pos1 / pos2
- 支持 half-offset
- 支持返回后恢复表单

### 12.3 NBT 捕获

提供统一 `NbtPickEntryBridge`：

- 支持 block/entity NBT
- 支持 block id / pos / face / hit / inside 等扩展上下文
- 支持 pick 完成后的 entry 回填

### 12.4 Undo / Redo

提供统一 `UndoBridge`：

- `FrameworkWorkspaceScreen` 可选启用
- `SceneEditorScreen` 等工作区页统一接入

## 13. 定义方式

新 framework 的业务定义方式，要求像 Catnip 一样“声明 entry”，而不是继续堆控件。

### 13.1 配置页示意

```java
return ConfigPageSpec.create("AI Config")
    .section("Provider")
    .entry(Entries.enumCycle(handleProvider))
    .entry(Entries.text(handleBaseUrl).hint("https://api.openai.com"))
    .entry(Entries.secretText(handleApiKey))
    .entry(Entries.text(handleModel))
    .section("Network")
    .entry(Entries.text(handleProxy))
    .entry(Entries.toggle(handleTrustAllSsl))
    .entry(Entries.toggle(handleWebUseProxy));
```

### 13.2 Step 表单页示意

```java
return FormPageSpec.create("Show Interface")
    .section("Target")
    .entry(Entries.textWithJeiAndBlockPick(blockHandle, BLOCK, "context.block"))
    .entry(Entries.xyzPick(pointHandle, PickTarget.POINT, true))
    .section("Options")
    .entry(Entries.toggle(centeredHandle))
    .entry(Entries.blockProperties(propsHandle));
```

### 13.3 工作流页示意

```java
return WorkspaceSpec.create("Scene Editor")
    .toolbar(Toolbars.sceneEditor())
    .sidebar(ListPanels.stepList(stepListModel))
    .content(DetailPanels.sceneStepDetail(currentStepModel))
    .status(StatusBars.sceneEditorStatus());
```

## 14. 迁移阶段

### Phase 0: 基线冻结

目标：

- 冻结当前 UI 功能范围
- 记录所有 screen 名单
- 为 Catnip config screen 做视觉基线参考

产出：

- UI screen 清单
- 关键交互清单
- Catnip 基线截图与尺寸记录

### Phase 1: 搭建基础内核

目标：

- 建立 `theme`、`chrome`、`adapter`、`entry`、`modal` 基础层

产出：

- `CatnipBaselineTheme`
- `FrameworkConfigScreen`
- `FrameworkFormScreen`
- `FrameworkSelectorScreen`
- `FrameworkListScreen`
- `FrameworkWorkspaceScreen`
- `FrameworkModalScreen`
- `FrameworkToastHost`

### Phase 2: 完成基础 entry

目标：

- 对齐 Catnip 基础 entry
- 补齐本模组常用 entry

产出：

- `BooleanEntry`
- `NumberEntry`
- `EnumEntry`
- `StringEntry`
- `TextWithJeiEntry`
- `XyzPickEntry`
- `DynamicKeyValueListEntry`
- `TextWithLanguageToggleEntry`

### Phase 3: 迁移 config 与简单表单页

目标：

- 先迁移低风险页面

建议顺序：

- `AiConfigScreen`
- `SceneDescEditorScreen`
- `IdleScreen`
- `RotateCameraScreen`
- `ZoomSceneScreen`

### Phase 4: 迁移复杂表单页

目标：

- 完成所有 step 编辑器页迁移

重点：

- JEI
- Pick
- NBT
- block props
- snapshot/restore

### Phase 5: 迁移列表与菜单页

目标：

- 完成 selector/list/menu 体系统一

重点：

- 搜索
- 排序
- 单选/多选
- 列表项渲染
- modal / toast 接入

### Phase 6: 迁移工作流和编辑器页

目标：

- 完成最复杂页面迁移

重点：

- `SceneEditorScreen`
- `FunctionScreen`
- `CommandParamScreen`
- `AiGenerateScreen`
- `ExportPackScreen`

同时完成：

- 统一确认弹窗替换
- 统一 toast/notice 替换

### Phase 7: 清理旧体系

目标：

- 删除不再需要的旧基类和重复逻辑

重点：

- `AbstractStepEditorScreen` 退役
- 旧页面中手写滚动和按钮代码删除
- 所有页面统一改走 framework

## 15. 风险与应对

### 15.1 风险：直接复制视觉但底层仍然分裂

应对：

- 一开始就把所有新页面强制走 framework
- 不允许出现“新皮旧骨”的第三套 screen

### 15.2 风险：复杂工作流 entry 做不成通用件

应对：

- pick / JEI / NBT 一开始就单独抽 bridge
- 不在业务 screen 中复制桥接逻辑

### 15.3 风险：以后改样式牵动 config 页

应对：

- config 页面锁定 `CATNIP_CONFIG_EXACT`
- editor/menu/list 页面通过 theme variant 扩展
- 任何自定义样式不得直接篡改 baseline preset

### 15.4 风险：Create / Ponder 上游变动影响过大

应对：

- 视觉 copy，不直接依赖其 config screen 运行时逻辑
- 必要时显式声明 Catnip 依赖，不依赖间接带入

## 16. 完成标准

满足以下条件，才算重构完成：

- 本模组所有 UI 都运行在新 framework 上
- 业务 screen 不再直接手写底层控件布局
- step 编辑页、列表页、菜单页、工作流页、配置页都拥有各自稳定的基础 screen
- modal 与 toast 成为 framework 的标准能力
- 初版默认视觉和 Catnip config screen 对齐
- 自定义样式能力存在，但 config 页不受影响
- 旧 `AbstractStepEditorScreen` 及相关重复基础设施被删除或仅保留短期兼容壳
- 所有页面的打开路径都切换到新 screen

## 17. 实施建议

建议本次重构不是“一个个页面修”，而是“先做 framework，再批量迁移页面”。

推荐执行顺序：

1. 先做 theme + chrome + adapter
2. 再做 entry 系统
3. 再做基础 screen 家族
4. 然后批量迁移 step 表单页
5. 最后迁移 workspace 级页面

这条路径最符合本项目当前的痛点：

- 现在的问题不是少几个控件
- 而是 UI 体系已经分裂、重复、难以继续维护

本方案的最终目标，就是把 Ponderer 的 UI 从“许多相似但彼此独立的 Screen”重构成“一套可以覆盖所有页面的统一 framework”。
