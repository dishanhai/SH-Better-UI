<!-- 作者：山海／dishanhai -->
# 液态玻璃控件：原生套件与 Web 参考

## 目标与交付边界

SHBUI 的主要交付是 **Minecraft 游戏内可使用的 UUK 控件**，覆盖 Forge
1.20.1、NeoForge 1.21.1 和 NeoForge 26.1.2。Web 实现用于对照材质、交互、
参数和布局，不代替游戏内入口，也不要求玩家启动浏览器才能操作控件。

首批范围以 Button、Toggle、Slider 及共用玻璃表面为起点，逐步补齐真实的
输入、焦点、取消、主题和生命周期。本文不是“全部控件已完成”的清单；每个
版本实际接入、测试及实机验证情况以对应提交和 PR 的证据为准。

React、DOM、CSS 和 SVG 滤镜不能直接作为 Minecraft Java 控件运行。本项目
采用 Web 原理参考与 UUK 原生适配，不将其描述为嵌入了完整浏览器运行时，
也不把 Web 截图作为 Minecraft 的验收截图。

## 固定来源与许可证

研究日期：2026-11-18。以下版本固定到研究时取得的 Git tree/commit SHA，
后续引入源码时应记录实际使用的文件及版本，不仅链接浮动的 `main`。

| 来源 | 固定版本 | 许可证与版权 | 用途 |
| --- | --- | --- | --- |
| [Liquefy UI](https://github.com/liquefy-ui/liquefy-ui/tree/079981f2710a51af7805966bea3d7d25d8f6da0f) | `079981f2710a51af7805966bea3d7d25d8f6da0f` | [MIT](https://github.com/liquefy-ui/liquefy-ui/blob/079981f2710a51af7805966bea3d7d25d8f6da0f/LICENSE)，2026 liquefy-ui contributors | 边缘折射、清晰中心、交互光照、融合原理 |
| [OpenGlass UI](https://github.com/moekoelueker/open-glass-ui/tree/c5e04fcdd9f3964696ba2ab09f41f10224929006) | `c5e04fcdd9f3964696ba2ab09f41f10224929006` | [MIT](https://github.com/moekoelueker/open-glass-ui/blob/c5e04fcdd9f3964696ba2ab09f41f10224929006/LICENSE)，2026 Moe Luker | 控件分层、可访问状态、清晰前景与材质策略 |

这些是研究来源，不表示其整套源码、依赖或全部控件已经导入 SHBUI。任何
直接复制或翻译的实现必须随分发保留相应 MIT 全文及版权通知，并标出修改
和原始文件出处；仅写“参考来源”链接不能替代许可文本。仅作原理参考的
独立实现也应保留参考说明。两项目均不是 Apple 官方 SDK；不暗示 Apple
授权、背书或视觉/物理上的完全一致。

## 参考的具体原理

### 有限边缘折射，而不是整块文字模糊

Liquefy UI 的
[`lens.ts`](https://github.com/liquefy-ui/liquefy-ui/blob/079981f2710a51af7805966bea3d7d25d8f6da0f/packages/core/src/lens.ts)
利用圆角矩形 SDF 定义边缘带，中心保持平坦，边缘通过有限斜率剖面生成
位移图。其强度约束旨在避免采样在圆角处反折。SHBUI 可借鉴这种边缘与
中心分离的思路，不能仅增加整屏 Gaussian blur 后称为液态折射。

[`lens-filter.ts`](https://github.com/liquefy-ui/liquefy-ui/blob/079981f2710a51af7805966bea3d7d25d8f6da0f/packages/core/src/lens-filter.ts)
把位移、可选 RGB 色散、模糊和饱和度组合为 SVG 滤镜。其
[`renderer.ts`](https://github.com/liquefy-ui/liquefy-ui/blob/079981f2710a51af7805966bea3d7d25d8f6da0f/packages/core/src/renderer.ts)
则负责高光等覆层，本身不采样背景。只移植高光覆层不等于完成背景折射。

### 清晰前景、共享渲染与可中断交互

文字、图标和焦点提示必须保持独立于折射背景的清晰前景。游戏内输入、
命中区域和焦点仍由 UUK 负责，不另建一套绕过生命周期的交互系统。
共用材质参数与渲染资源，避免每个控件各自捕获整帧或无限保留 GPU 资源。
按下、拖动、释放、取消及减少动效状态均是控件契约的一部分。

### 融合属于后续能力，不由单个圆角面板冒充

Liquefy UI 的
[`glass-merge-geometry.ts`](https://github.com/liquefy-ui/liquefy-ui/blob/079981f2710a51af7805966bea3d7d25d8f6da0f/packages/core/src/glass-merge-geometry.ts)
提供 SDF 与平滑最小值组合的纯几何实现，适合研究多表面融合。该原理与
单独的透明卡片不同，只有完成轮廓、光学、输入区域和性能验证后，才能
声称已支持液态融合。

## Web 与原生的差异

- CSS `backdrop-filter: blur(...)` 是背景模糊，不是折射。支持 blur 也不意味着
  浏览器支持 `backdrop-filter: url(...)` 的 SVG 位移链。
- Liquefy UI 的实际背后内容位移路径依赖 Chromium；WebKit/Gecko 使用 CSS
  回退。不能宣传所有浏览器显示相同光学效果。
- OpenGlass UI 的默认自动渲染策略选择 CSS；其 WebGL 路径采样应用提供的
  图片、Canvas 或视频，不是任意 DOM 的通用实时捕获。
- 原生实现应从适配器支持的真实背景纹理取得采样，并明确捕获时序、裁剪、
  坐标尺度和资源释放。无可用采样能力时保持可读的材质回退，报告能力差异，
  不把“半透明填充”标记成已完成真实折射。
- Web 库的默认动画策略不能覆盖玩家已选的减少动效、高对比或减少透明度
  配置。SHBUI 的显式配置优先。

来源的范围说明见 OpenGlass UI 的
[限制](https://github.com/moekoelueker/open-glass-ui/blob/c5e04fcdd9f3964696ba2ab09f41f10224929006/docs/LIMITATIONS.md)
与[浏览器矩阵](https://github.com/moekoelueker/open-glass-ui/blob/c5e04fcdd9f3964696ba2ab09f41f10224929006/docs/BROWSER-SUPPORT.md)。
这些是上游测试声明，不是本项目的测试结果。

## 分批实施与验收

1. **基础套件**：共用材质与 Button、Toggle、Slider；各版本真实入口、
   键鼠操作、禁用/焦点状态、拖动取消及清理；Web 提供对应可运行示例。
2. **光学一致性**：核对每个原生适配器的背景采样、清晰中心、边缘折射与
   高光；补充亮暗及复杂背景、GUI 缩放、资源重载的视觉验证。
3. **扩展控件**：在现有 UUK 语义上逐项增加分段控件、菜单、弹窗等，
   单独验证焦点恢复、关闭和重复操作，而不是一次性宣称移植全部上游控件。
4. **融合和高级动效**：单独验证多表面几何、过渡打断、减少动效以及性能。

自动化测试通过、Web 浏览器验证通过和 Minecraft 实机验证通过必须分开
记录；静态截图不能证明动效帧率、实时采样正确性或硬件 GPU 性能。NeoForge
26.1.2 的适配不能据此推定 26.2 或其他未测版本也受支持。

## 首批使用方式

`ShBetterUiApi.controls().glass(preferences)` 返回由页面拥有的 `GlassControls`。
将 `button(...)`、`toggle(...).button()`、`slider(...)` 返回的原生 widget 注册到
当前页面；在布局重建和页面移除前调用 `close()`。不要跨页面复用已关闭套件。
`setPreferences(...)` 原位更新高对比、减少动态和模糊偏好，不重建焦点或改变
业务值。开关回调是请求，业务成功后调用 `setValue` 确认；滑杆可用 `setOnCommit`
区分预览和提交，既有取消编辑会还原拖动前值。

首批没有引入 React、浏览器内核或上游运行时依赖，也没有复制上游源文件。
使用现有原生背景合成：旧双版 `LiquidGlassTheme`，26 版 `LiquidGlassSurface`。
原版 `UiSlider` 默认不变，套件创建的滑杆显式开启玻璃；玩家关闭背景模糊或
选择高对比时仍使用可读的实色回退。旧双版的液态玻璃预览与 26 图库的液态玻璃
示例使用同一套件，不是仅在测试中构造。

Web 对应示例位于 `web/src/liquid-glass.html`，可复用样式和挂载函数位于
`web/src/liquid-glass/kit.css`、`kit.mjs`。调用 `mountGlassKit(root)` 后，在页面
被替换前调用返回对象的 `dispose()`。它使用原生 HTML 按钮、范围输入、选择框
和 `dialog`，包含键盘栏目导航和关闭回焦点。Web 样例的弹窗/栏目不代表原生
首批也新增了这些控件。此样例的 CSS 表面不声称物理折射，已有材质实验室的
WebGL 预览才是光学对照入口。

验证命令：`cd web && npm run build && npm test && npm run test:liquid-glass-browser`。
浏览器脚本需要可运行的本地 Chromium，并生成桌面/手机截图。原生测试为
`GlassControlsTest`，结合既有滑杆、开关、图库及三个版本构建运行。

### 宿主背景准备

套件本身不复制帧缓冲，也不创建独立的每控件捕获管线。原生宿主必须按该
版本现有渲染时序准备背景；尤其 26 的 `drawPrepared` 在缺少当前帧捕获时
返回回退结果。图库入口已走现有准备路径，第三方页面不能仅因创建了控件
就假定折射可用。无有效捕获时保持实色可读，而非悄悄宣称已渲染折射。
