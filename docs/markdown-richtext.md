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

参考图中的游戏物品/流体图标、交互标签、折线图、柱状图不是标准 Markdown；本轮不自动创建这些内容。它们应由可信宿主通过现有 `RichObjectNode` / `RichExtensionNode` 与 `RichTextView` 的显式渲染器集成，不能让网络文本调用游戏 API 或执行任意绘制代码。

## 安全与成本

解析器限制 65,536 UTF-16 单元、4,096 块、32 列和 256 表格行；超限显式拒绝，不静默截断。源文本不会加载资源、执行代码、启动网络请求或指令。布局缓存完整行与样式，绘制只访问可见行；点击位置由同一行几何解析。外链只接受有 host、无 userinfo 的 HTTP(S)，每次动作重新验证。复制源文只响应用户点击。

## 当前边界

示例提供原生链接按钮与“下一链接”按钮，可通过 Tab / Enter 操作并由原生按钮旁白读出 URL。尚无独立全文内容朗读，也没有文本拖选；复制按钮可复制完整 Markdown。宿主若需要完整可访问文档交互，应在原有 UUK richtext 交互层补齐后再代替正式阅读页。图表和图标未包含在此 Markdown 布局中。
