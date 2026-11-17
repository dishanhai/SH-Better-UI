# Markdown 富文本（实验性本地实现）

`ShBetterUiApi.markdown().parse(text)` 仍返回原有 Minecraft Component。
新增 `parseInline(text)` 只解析内联格式，不误将表格单元格开头的 `#` / `-` 解释为块标记。
新增 `document(text)` 返回平台无关的 `RichMarkdown.Document`。

```java
var document = ShBetterUiApi.markdown().document(markdownSource);
var view = new MarkdownView(font, document, panelWidth);
// 在源文本、字体/语言/资源包、宽度变化时重建；不要每帧解析。
graphics.enableScissor(left, top, right, bottom);
view.render(graphics, left, top - scroll, top, bottom, 0xffe0e6ef);
graphics.disableScissor();
```

打开 `/shbetterrichtext`，点击 `Markdown`。示例具有滚轮、方向键、Page Up/Down、Home/End、返回、复制源文按钮。宿主仍负责控制页面布局、滚动状态和资源包刷新。

## 实现范围

- ATX 标题（粗体层级标签，暂不缩放字号）、段落与显式换行。
- 粗体、斜体、删除线、行内代码及反斜线转义：沿用现有有限内联解析器。
- 单层引用、列表行及只读任务标记；围栏代码块不进行语法高亮。
- 分隔线及管道表格，支持左/中/右对齐、多行折行和动态行高。
- 每列不足 48 GUI 像素时，表格转换为逐项的“列标题：值”，而不是让列重叠。
- HTTP(S) 链接需点击，经现有 LinkApi 验证并遵守游戏链接开关和确认设置。

这不是完整 CommonMark/GFM 实现。不支持 HTML 执行、脚注、引用链接、嵌套块结构、LaTeX、自动远程图片。图片语法仅保留 alt 文本；未知语法保留原文。任务复选框是内容展示，不改变游戏状态。

参考图中的游戏物品/流体图标、交互标签、折线图、柱状图不是标准 Markdown，不由文本自动创建。

新增可信示例：打开 `/shbetterrichtext`，点击左上角 `图表 / 图标`（GUI 宽度至少 210）。`TrustedRichTooltipDemo` 通过原生 `RichObjectNode` 显示熔炉、铜/铁/金锭与水流体；通过 scoped `RichTextExtensions` 显式注册分隔线、带坐标轴的效率曲线、产出比较条。曲线和数值仅是清楚标注的演示数据，不代表真实游戏配方。完整数值以文字/替代文本保留，可悬停读取；页面复用滚动、剪裁、文本选择，并给内容区旁白提供全文与替代文本。

这些渲染器仅在 `RichTextExampleScreen.trustedDemo(module, parent)` 工厂创建的页面注册，不接受 Markdown 标签或任意 payload。普通导入文档不会自动获得扩展渲染器。宿主集成时沿用 `RichObjectNode` / `RichExtensionNode` 与 `RichTextView` 的显式类型 API，不能让网络文本调用游戏 API 或执行任意绘制代码。

## 安全与成本

解析器限制 65,536 UTF-16 单元、4,096 块、32 列和 256 表格行；超限显式拒绝，不静默截断。源文本不会加载资源、执行代码、启动网络请求或指令。布局缓存完整行与样式，绘制只访问可见行；点击位置由同一行几何解析。外链只接受有 host、无 userinfo 的 HTTP(S)，每次动作重新验证。复制源文只响应用户点击。

## 当前边界

示例提供原生链接按钮与“下一链接”按钮，可通过 Tab / Enter 操作并由原生按钮旁白读出 URL。尚无独立全文内容朗读，也没有文本拖选；复制按钮可复制完整 Markdown。宿主若需要完整可访问文档交互，应在原有 UUK richtext 交互层补齐后再代替正式阅读页。图表和图标位于独立的可信 UUK 示例中，不属于 Markdown 语法或 `MarkdownView` 的自动布局。交互标签与真实机器数据接入尚未实现。

## 验证记录

- Forge 1.20.1 / Java 17：完整 `compileJava`、`compileTestJava`、`jar` / `reobfJar` 成功；Markdown 内联、安全、可信示例及背景钩子回归共 7 项通过。
- NeoForge 1.21.1 / Java 21：完整项目 `compileJava` 和相同 7 项回归通过。开发环境使用 ModDev 官方二进制 Minecraft artifacts 模式，未跳过项目编译或测试；纯 Java 块解析器另有 7 项测试通过。
- NeoForge 1.21.1 独立游戏实例实际验证 Markdown、表格、图标/流体、效率曲线与比较条；验证内容滚动、返回重入、外链确认及取消。修复了 NeoForge `Screen.render()` 再次调用背景钩子导致正文被模糊的问题。
- 尚未执行 Forge 游戏内视觉验证、窄窗口截图、资源包热重载或性能基准；编译通过不代表这些场景已验收。
