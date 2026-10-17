# 粒子效果步骤

在步骤选择器的“效果”页添加“粒子效果”。旧的 `indicate_success` 和
`indicate_redstone` JSON 仍可直接播放，不需要迁移；打开旧步骤编辑时，会载入对应
预设，保存后变为 `spawn_particles`，保留原来的位置和关键帧设置。

预设包括成功提示、红石提示、烟雾、飞溅、物品碎屑和方块碎屑。选择预设会填入
粒子及基础属性，保留发射位置；所有属性均可继续修改。

## 自定义粒子

直接修改“粒子 ID / 参数”即可使用原版或已安装模组的粒子。
使用 Minecraft 1.21.1 `/particle` 的粒子参数语法，不包含命令本身：

- 普通粒子：`minecraft:flame`
- 红色尘埃：`minecraft:dust{color:[1.0f,0.0f,0.0f],scale:1.0f}`
- 物品碎屑：`minecraft:item{item:{id:"minecraft:iron_ingot",count:1}}`
- 方块碎屑：`minecraft:block{block_state:{Name:"minecraft:pink_wool"}}`

保存时根据当前世界的注册表校验 ID 和参数。对于需要额外参数的粒子，只填写 ID
会显示校验错误。修改物品、方块、尘埃颜色或大小时，编辑对应的 SNBT 参数。

## 基础属性

| 字段 | 含义 | 省略时的值 |
| --- | --- | --- |
| `particle` | 粒子 ID，可紧跟 SNBT 参数 | 必填 |
| `pos` | 发射中心 `[x,y,z]`，支持小数 | 必填 |
| `distribution` | `point` 定点、`box` 方盒内部均匀分布、`sphere` 球体内部均匀分布 | `point` |
| `spread` | 各轴半边长 / 椭球半径，必须非负 | `[0,0,0]` |
| `count` | 每游戏刻发射的数量，0–4096 | `10` |
| `duration` | 发射持续刻数，1–72000 | `1` |
| `motion` | 传给粒子的初始速度 `[x,y,z]` | `[0,0,0]` |
| `attachKeyFrame` | 是否添加关键帧 | `false` |

`spread:[0.5,0.5,0.5]` 的方盒总宽度为一格，中心两侧各半格。定点模式忽略范围。
球体的三个半径不同时，形成椭球；某轴为零时可以形成平面。

20 刻等于一秒。总发射数量为 `count × duration`。持续时间控制发射器，单个粒子的
寿命和实际运动由粒子实现决定。粒子步骤不暂停时间线；需要等待效果播放时，添加
`idle` 步骤。

```json
{
  "type": "spawn_particles",
  "particle": "minecraft:large_smoke",
  "pos": [2.5, 1.5, 3.5],
  "distribution": "box",
  "spread": [0.5, 0.25, 0.5],
  "count": 1,
  "duration": 40,
  "motion": [0, 0.05, 0],
  "attachKeyFrame": true
}
```

新步骤支持场景 JSON 存储、资源包同步和 Java 模块导出。PonderJS 转换器目前未提供
通用粒子步骤转换，导出时会标记 `TODO_UNSUPPORTED_STEP: spawn_particles`。

## 预设来源

预设基于本地 1.21.1 参考源码，不要求运行时安装 Create：

- `ref/1.21.1/Ponder/common/src/main/java/net/createmod/ponder/foundation/PonderSceneBuilder.java`：
  `createRedstoneParticles`，红色及绿色尘埃、方块内分布、每刻 10 个、持续 2 刻。
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/infrastructure/ponder/scenes/FanScenes.java`：
  烟雾与飞溅粒子。
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/infrastructure/ponder/scenes/CartAssemblerScenes.java`：烟雾。
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/infrastructure/ponder/scenes/ProcessingScenes.java`：
  根据加工输入物品产生碎屑；预设使用铁锭作为可修改的示例物品。
- `ref/1.21.1/Create/src/main/java/com/simibubi/create/infrastructure/ponder/scenes/DeployerScenes.java`：
  粉色羊毛碎屑。
