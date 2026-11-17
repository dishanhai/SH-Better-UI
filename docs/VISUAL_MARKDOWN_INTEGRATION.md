# 可视化与 Markdown 混合文档：第一阶段

`RichMarkdownContent` 将被动 Markdown 和可信 Java 显式提供的行内节点合成同一个 `RichTextComponents.Snapshot`。宿主交给现有 `RichTextView.prepare(snapshot, font, width)`，由同一份排版快照处理绘制、裁剪、命中、选择及旁白投影。无额外 Screen 或渲染引擎。

```java
var content = RichMarkdownContent.builder(UiId.parse("example:guide"))
    .markdown("# Guide\n**Local resource** below:")
    .node(new ImageNode(UiId.parse("example:picture"),
        UiId.parse("minecraft:textures/item/diamond.png"), 24, 24, "Diamond"))
    .markdown("## Next\nText continues after the visual.")
    .build();
// In the existing layout sampling callback:
view.prepare(content, font, availableWidth);
```

## 能力与边界

- Markdown 标题、段落、引用、列表、代码及表格投影为现有富文本节点，保留行内样式。标题为粗体，不改变字号。
- 表格在此桥接中始终使用“列标题：值”的记录行，完整保留每个值及空表标题。需要列网格请继续使用 `MarkdownView`；这不是列网格的等价视觉替代。
- `node`、`document`、`snapshot` 允许宿主插入已有图片、物品、帧动画、按钮、滑杆、扩展图表节点。扩展 renderer、交互回调和远程媒体仍须由宿主在自己的作用域显式绑定，composer 不授予任何新能力。
- 图片 Markdown 只产生替代文字，不发出下载；代码保持字面内容。HTML、游戏对象标记不自动生成对象，不能注册图表 renderer 或运行脚本。
- 链接的原生 metadata 保留于 Snapshot，解析/构建不执行动作。宿主应使用已有 RichTextView 交互链路及 LinkApi 的校验、游戏链接开关和确认流程，不能直接执行 click metadata。
- 输入总计最多 65,536 UTF-16 单元，表格重复标题等展开前按保守投影限制 131,072 单元，混合节点最多 8,192。每次追加失败不会写入部分内容；重复 ID 和孤立 metadata 拒绝。输出不可变；相同组合顺序产生稳定 ID。更改文档/语言/资源或宽度后重新 prepare，不应每帧 parse。
- 桥接产生的原生样式携带保留的 literal insertion 标记，阻止原生 Font 管线再次将代码中的 `<rich:…>` 或旧特效标记解释为图标/效果。这个标记属于内存 Snapshot，不添加到纯数据文档。
- 原始 `RichDocument` 不承载原生链接 metadata。必须向视图传整个 Snapshot，不只取 `.document()`；后者同时丢失防二次解析标记，不能作为被动 Markdown 安全呈现入口。

## 可运行示例

`/shbetterrichtext` → “图表 / 图标” 现在在同一个富文本视口混排 Markdown、原生物品/流体、可信图表和本机钻石/绿宝石帧动画。动画是演示资源轮播，不表示真实游戏状态；减少动效仍保持首帧。图表仍是明确标注的静态演示数据。不会将导入文档自动升级为可信示例。

## 验证

测试覆盖混合顺序/样式、稳定 ID、被动内容安全、链接 metadata、字面代码、表格投影、追加原子性、不可变结果、累计文本和节点预算、孤立 metadata。双平台构建/定向测试结果由本次交付报告列明。尚不表示完整 CommonMark、交互标签语法、真实机器数据绑定、动态图表或可视化材质全目录已完成。

2026-11-17 本地验证：Forge 1.20.1 和 NeoForge 1.21.1 均完成 `compileJava`，同一组 33 项测试通过（mixed 10、Markdown 5、可信示例 1、原生 rich 8、特效 9），无失败/跳过。NeoForge 使用官方二进制 Minecraft artifacts 模式，仅跳过游戏反编译，不跳过项目编译。独立只读复核两项安全修复通过。尚未运行本阶段的游戏内截图、资源包热重载/旁白或性能验收；这不是全量测试通过声明。literal insertion 标记暂沿用保守绘制路径，未启用现有跨片段合批优化。

## 原创程序材质

`BoilerSchematicRenderer::render` 可由可信宿主绑定到自己 scope 的扩展节点，绘制原创水→加热容器→蒸汽示意图。它不是现有机器的贴图，也不是热力学模拟。示例明确标注静态示意，不再以熔炉图标冒充锅炉。所有几何均在节点尺寸内，最多 19 个矩形与 3 个标签；小于 160×64 时使用简化容器图形，标签按实际宽度裁剪，完整含义保留在外部文字与节点 alt。无需图片下载或外部素材授权，不改变 Markdown 的被动解析边界。

追加实机检查：NeoForge 1.21.1 已实际进入测试世界并打开混排页，检查图标、折线图、柱图、全文滚动与返回；字面 `<rich:item=...>` 未被二次解析。发现并修复示例末尾缺段落导致下一节标题连行的问题。程序锅炉示意图的实机检查另行记录。

原创程序材质追加验收：Forge/NeoForge 各 35 项定向测试通过，0 失败/错误/跳过。NeoForge 1.21.1 实机确认水→锅炉→蒸汽图形及标签正确显示，滚动后阅读说明独立成段、末尾代码保持字面文本；已留原生 F2 截图。未宣称 Forge 实机、全部 GUI 缩放、热重载、旁白或性能基准通过。

## 第二阶段：受限动态指标（可信宿主）

`BoundedMetricSource` 提供不可变数值快照与 revision；宿主显式写入 `value/min/max`，拒绝非有限数值、无效范围和越界值。同值更新不改变 revision。用 `registry.registerData(sourceId, source)` 绑定到当前 screen/tree 的作用域，再将 renderer 显式绑定为 `context -> BoundedMetricRenderer.render(context, sourceId)`。renderer 只使用本帧 `context.data(sourceId)`，不按文档 payload 查找任意来源，缺失/坏数据显示明确后备。

示例右上原生按钮可用鼠标或 Tab 后 Enter/Space 操作，每次将演示负载增加 20，100 后回到 0。事件在 UI 线程同时更新 source、正文、节点 alt、旁白消息及按钮标签，再失效布局；数值未变化时不需要重解析文档。实际业务宿主也应在同一 UI 更新边界同步正文/alt 与 source，不能只修改图形数据而留下旧的无障碍描述。renderer 不自主采集系统、游戏、网络或账号数据。

本阶段为显式事件驱动的数据绑定，不冒充真实机器监测或自动采样仪表盘。数据注册跟随原有 tree 关闭；重新打开新示例会重建独立状态。已有图片/帧资源走原有 reload 处理，动态指标与程序锅炉不依赖外部贴图。热重载、旁白实际发声和所有缩放尺寸仍须单独实机验证。

第二阶段验证：Forge 1.20.1 与 NeoForge 1.21.1 各 40 项定向测试通过，无失败/错误/跳过。NeoForge 实机确认鼠标点击 40→60、Enter 激活 60→80，按钮/正文/条形图一致；关闭再打开恢复独立初值 40，Tab 焦点+Space 更新至 60。已留 80% 原生截图。F3+T 未确认触发，不能视为资源热重载通过；本阶段未测试真实设备数据源或旁白语音。
