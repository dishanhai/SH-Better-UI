<!-- 作者：山海／dishanhai -->
# 液态玻璃分段选择器

此增量依赖基础 `GlassControls` 套件，增加三版本原生单选分段控件，
不是完整 Web 库或多表面液态融合的完成声明。

## 接入

通过 `GlassControls.segmented(area, name, sections, selected, onChange)` 创建并
向原生页面注册控件；套件关闭取消未提交拖动并使控件失效。已有
`UiRubberSegment` 使用者可显式 `setLiquidGlass(true)`，默认外观不变。
程序 `select(index)` 不调用用户回调；指针释放和键盘选择沿用已有语义。

## 光学与输入

轨道与移动滑块的 `GlassDraw` 来自同一帧动画采样。相同左右边界同时用于
滑块材质与文字裁剪，前景文字在所有材质完成后绘制。明确的焦点边框及
选中色层继续存在，不让折射取代选中状态。

Forge 1.20.1/NeoForge 1.21.1 将两区域作为一个批次交给既有 `ClientGlassRender`，
沿用开始/结束、预算、回退及资源生命周期，不为滑块再捕获已经绘制的轨道。
NeoForge 26.1.2 请求既有共享背景边界一次，然后两区域复用 `drawPrepared`。
没有新 GPU 资源池、网页宿主或截图回读通道。

高对比、关闭背景采样及无可用捕获均保留不透明外观；减少动态使用既有
分段动画策略。采样使用本项目既有光学 Shader，不声称此增量移植了上游
整个 Web 渲染器。来源及许可研究见 [基础套件说明](liquid-glass-web-kit.md)。

旧双版的 Rubber Segment 预览以及 26 图库对应示例均使用实际套件，并画出
彩色背景供检查透射。仅截图不能证明帧率、硬件 GPU 表现或所有中断路径。
具体三版测试、实机及限制以本增量 PR 的记录为准。

## Text contrast scrim

Both track and thumb use their actual readable foreground color to select a bounded
scrim opacity. The smallest alpha in 184..255 that retains at least 4.5:1 contrast
against both black and white scene extremes is selected with `ContrastPolicy`.
The resulting background luminance interval must stay wholly on one side of the
opaque foreground luminance, excluding a mid-gray crossing between the extremes.
Each control caches its track/thumb result by fill and ink color, so the bounded
search does not run again on ordinary animation frames. Color changes invalidate
only the affected cached result. Non-optical rendering remains fully opaque;
custom mid-gray themes can also require an opaque scrim to preserve legibility.
