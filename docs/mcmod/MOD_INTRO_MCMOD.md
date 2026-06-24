<body class="view" contenteditable="true" spellcheck="false" style="cursor: text;">
<p>[h1=Ponderer 模组介绍（MC百科版）]</p>
<p>Create 制作组在 6.0 版本将 Ponder 拆分为独立模组，然而较高的使用门槛使这一优秀功能未能被更多玩家所体验。Ponderer 正是为此而生。</p>
<p>Ponderer 是一个面向玩家与整合包作者的<strong>「游戏内思索（Ponder）制作工具」</strong>。你不需要离开游戏，也不需要先写脚本，就可以直接在世界里搭建、录制和调整思索教学流程。</p>
<p>[h1=你可以用它做什么]</p>
<p>[h2=思索的游戏内编辑]</p>
<ul style="list-style-type: disc;" class=" list-paddingleft-2">
<li><p><strong>可视化场景编辑</strong>：在游戏内创建、编辑、删除和排序 Ponder 思索，支持 <strong>热重载</strong>，<strong>在游戏中按 V 打开模组菜单</strong>。</p></li>
<li><p><strong>丰富步骤类型</strong>：覆盖 结构展示/叠加结构、文本、实体与掉落物、镜头旋转/缩放、高亮区域、控制提示、声音、方块修改、区段移动/旋转 等绝大部分常用教程动作。</p></li>
<li><p><strong>结构与蓝图素材</strong>：使用蓝图工具保存选区结构，并从 config/ponderer/structures/ 加载自定义结构素材。</p></li>
<li><p><strong>JSON DSL 存储</strong>：场景以 <strong>数据驱动 JSON</strong> 存放在 config/ponderer/scripts/，便于版本管理、手动调整和工具链处理。</p></li>
</ul>
<p>[h2=导入导出与多人游戏同步]</p>
<ul style="list-style-type: disc;" class=" list-paddingleft-2">
<li><p><strong>客户端/服务端同步</strong>：通过 /ponderer pull 与 /ponderer push 在客户端和服务端之间同步场景，支持 <strong>冲突处理</strong> 与强制/保留本地策略。</p></li>
<li><p><strong>远程工作区管理</strong>：提供远程场景/结构浏览、拉取、删除、历史记录与回滚能力，适合服务器或整合包团队协作维护。</p></li>
<li><p><strong>场景包导入导出</strong>：将场景与结构打包为 <strong>资源包格式 ZIP</strong>，支持版本信息与自动加载，方便分发到 Modrinth / CurseForge 或整合包。</p></li>
<li><p><strong>PonderJS 双向转换</strong>：支持 Ponderer JSON 与 <strong>PonderJS</strong> 格式互相导入/导出，方便在脚本工作流和游戏内编辑之间切换。</p></li>
<li><p><strong>权限与功能同步</strong>：服务端可同步功能可用性，并配合权限管理控制上传、编辑和部分功能开关。</p></li>
</ul>
<p>[h2=AI 场景生成]</p>
<ul style="list-style-type: disc;" class=" list-paddingleft-2">
<li><p><strong>多提供商支持</strong>：可配置 <strong>Claude / ChatGPT</strong> 等 LLM 提供商，用自然语言生成 Ponder 场景草稿。</p></li>
<li><p><strong>结构感知生成</strong>：结合结构描述、注册表映射和用户提示词，让 AI 更容易引用正确方块、物品、坐标与演示步骤。</p></li>
<li><p><strong>游戏内生成流程</strong>：在游戏内填写需求、生成草稿并继续进入编辑器微调，避免在外部文件和游戏之间反复切换。</p></li>
</ul>
<p>[h2=思索投影仪]</p>
<ul style="list-style-type: disc;" class=" list-paddingleft-2">
<li><p><strong>两种投影仪方块</strong>：提供 <strong>微缩投影仪</strong> 与 <strong>实景投影仪</strong>，将源物品对应的 Ponder 场景投射到世界中。</p></li>
<li><p><strong>播放与触发控制</strong>：支持非常丰富的可配置项，满足各场景需求。</p></li>
<li><p><strong>多人服务器管理</strong>：服务端可同步并管控投影仪功能开关，关闭后投影仪不再可放置、配置或播放，适合纯原版服务器。</p></li>
</ul>
<p>[h2=思索内体验优化]</p>
<ul style="list-style-type: disc;" class=" list-paddingleft-2">
<li><p><strong>文本进度条</strong>：在思索显示文本进度板，解决思索不能手动拖动进度条的拖沓问题。</p></li>
<li><p><strong>界面思索</strong>：支持展示界面、修改槽位和模拟点击流程，用于演示容器、菜单、物品栏或自定义 UI 的交互逻辑。</p></li>
<li><p><strong>内置引导与配置</strong>：包含内置示例场景、快捷键设置、界面缩放与模组配置页，便于上手和按需调整体验。</p></li>
</ul>
<p>[h1=适合哪些人]</p>
<ul style="list-style-type: disc;" class=" list-paddingleft-2">
<li><p>想给自己整合包做引导教程的作者。</p></li>
<li><p>想给服务器玩家制作上手教学的管理员。</p></li>
<li><p>想用更直观方式维护、体验 Ponder 内容的普通玩家。</p></li>
</ul>
<p>[h1=核心体验]</p>
<p>Ponderer 的目标是：<strong>把“写教程”变成“在游戏里直接搭教程”</strong>。</p>
<p>从创建、编辑、预览到同步，整个流程尽量保持在 Minecraft 内完成，让思索内容的制作更快、更直观。</p>
<p>[h1=画廊]</p>
<table><tbody><tr class="firstRow"><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><strong>丰富的内置步骤</strong></td><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><strong>使用蓝图保存结构并在思索中使用</strong></td><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><strong>AI 驱动的思索自动生成</strong></td></tr><tr><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><strong>微缩思索投影仪</strong></td><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><strong>实景思索投影仪</strong></td><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><strong>服务器多端同步与远程管理</strong></td></tr></tbody></table>
<p>[h1=Q&amp;A]</p>
<p>[h2=1. 版本支持计划？]</p>
<table><tbody><tr class="firstRow"><th style="word-break: break-all;">游戏版本</th><th style="word-break: break-all;">Forge</th><th style="word-break: break-all;">NeoForge</th><th style="word-break: break-all;">Fabric</th></tr><tr><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><strong>26.1</strong></td><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><span style="color: rgb(192, 0, 0);"><strong>无支持计划</strong></span></td><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><span style="color: rgb(112, 48, 160);"><strong>即将支持</strong></span></td><td align="center" valign="middle" colspan="1" rowspan="1" style="word-break: break-all;"><span style="color: rgb(112, 48, 160);"><strong>即将支持</strong></span></td></tr><tr><td style="word-break: break-all;" align="center" valign="middle"><strong>1.21.1</strong></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(192, 0, 0);"><strong>无支持计划</strong></span></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(0, 176, 80);"><strong>维护中</strong></span></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(0, 176, 80);"><strong>维护中</strong></span></td></tr><tr><td style="word-break: break-all;" align="center" valign="middle"><strong>1.20.1</strong></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(0, 176, 80);"><strong>维护中</strong></span></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(192, 0, 0);"><strong>无支持计划</strong></span></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(0, 176, 80);"><strong>维护中</strong></span></td></tr><tr><td style="word-break: break-all;" align="center" valign="middle"><strong>1.12.2</strong></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(112, 48, 160);"><strong>计划支持</strong></span></td><td style="word-break: break-all;" align="center" valign="middle"><strong>-</strong></td><td style="word-break: break-all;" align="center" valign="middle"><strong>-</strong></td></tr><tr><td style="word-break: break-all;" align="center" valign="middle"><strong>1.7.10</strong></td><td style="word-break: break-all;" align="center" valign="middle"><span style="color: rgb(112, 48, 160);"><strong>计划支持</strong></span></td><td style="word-break: break-all;" align="center" valign="middle"><strong>-</strong></td><td style="word-break: break-all;" align="center" valign="middle"><strong>-</strong></td></tr></tbody></table>
<p>考虑到老版本本身存在不少限制，将本模组移植到更早版本这件事<strong>非常困难</strong>。如果你喜欢这个模组，或希望能更早看到移植版本，欢迎考虑请我喝杯咖啡。非常感谢你的支持与喜爱。</p>
<p>[h2=2. 为什么不直接使用 PonderJS？]</p>
<p>本模组提供思索多端同步能力，直接传输 JS 脚本会引入额外的安全隐患。Ponderer 采用更安全的数据传输方式，并提供与 PonderJS 的双向转换能力。你可以在两种工作流之间按需切换。同时，Ponderer 提供了大量 PonderJS 原生暂不支持的接口。</p>
<p><strong>非 1.20.1Forge 版本可能不会经过非常详尽的测试，如果有 Bug 请留言反馈。</strong></p>
<p><strong>交流群：818569077</strong></p>
</body>
