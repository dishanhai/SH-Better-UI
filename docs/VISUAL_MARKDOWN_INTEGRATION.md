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
