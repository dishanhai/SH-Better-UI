<!-- Author: dishanhai -->
# SH-Better-UI 视觉对齐 API

Forge 1.20.1 和 NeoForge 1.21.1 共用 `ShBetterUiApi.layout()`。普通整数行列仍可用 `graphics().align()/row()/column()`。每个 Screen、HUD 或子面板持有自己的 `AlignmentLayout`、约束句柄和可选度量缓存，统一在客户端线程更新和读取。

## 与 UUK 视觉组合共用已发布的 View

用 `LayoutService.bindVisual` 绑定页面的 `AlignmentLayout`。每帧依次采样、失效、增量求解、发布。绘制、效果、前景与输入从 `FrameViewSnapshot` 读取同一份不可变 `View`。布局求解后的改动留给下一次合法发布，输入只查询最后呈现。首次呈现前没有快照。

节点变换把局部 GUI 坐标映射到屏幕 GUI 坐标。`renderBounds()` 与 `visualBounds()` 已在屏幕空间，前者是绘制主体，后者包含光学度量和效果外扩。父级裁剪会进入子节点的 `View.clip()`。组合再与宿主视口和已有 scissor 求交，命中仍使用原有 `View.hitTest`。像素投影根据实际主渲染目标和原版未取整 GUI 投影向外取整。

矩形 framebuffer 效果要求正向轴对齐变换。旋转节点可使用局部坐标下的普通绘制、纹理 Reveal、径向渐变与清晰前景。普通原生控件可用 `snapshot.placeWidget` 应用未裁剪的整数平移。 [完整契约和示例](API_REFERENCE.md#visual-layout--render-composition)

## 对齐图标

```java
var api = ShBetterUiApi.layout();
var layout = api.create(viewport); // GraphicDisplayLayout.Bounds
var assets = api.assets(minecraft.getResourceManager());
var container = layout.node("toolbar", AlignmentFrame.box(new Rect(20, 24, 84, 32)));
var localIcon = new Rect(0, 0, GraphicIcon.size(1), GraphicIcon.size(1));
var icon = layout.node("back-icon", container, AlignmentFrame.box(localIcon)
        .withVisual(assets.icon(GraphicIcon.BACK, localIcon))
        .withHit(new Rect(-3, -3, 22, 22)))
        .snap(PixelAlignment.Snap.ORIGIN);

var rule = VisualAlignment.align(icon)
        .relativeTo(container)
        .horizontal(VisualAlignment.Align.CENTER)
        .vertical(VisualAlignment.Align.CENTER)
        .bounds(VisualAlignment.Bounds.VISUAL)
        .opticalCorrection(true)
        .opticalOffset(0.25, 0)
        .offset(0, 0)
        .apply();

layout.resolve();
var view = icon.current(api.pixels(minecraft.getWindow()));
api.draw(graphics, view, g -> GraphicIcon.BACK.draw(g, 0, 0, 1, 0xFFFFFFFF));
boolean over = view.hitTest(mouseGuiX, mouseGuiY);
```

`Rect`、`Anchor`、`Insets` 和 `Transform` 位于 `AlignmentGeometry`。布局类及资源适配位于 `client.gui.layout`。回收单条关系时调用 `rule.close()`。移除节点前，先清除引用它的关系及后代。

## 坐标和边界

| 数据 | 输入坐标 | 含义 |
| --- | --- | --- |
| `LayoutBounds` | 父级局部 GUI 坐标 | 逻辑矩形。边距作用于源布局边缘，内距形成目标内容区域。锚点相对节点原点 |
| `render` | 节点局部 GUI 坐标 | 变换前的绘制主体，例如 PNG 的完整四边 |
| `VisualBounds` | 节点局部 GUI 坐标 | 内容外接矩形、估算重心、人工修正、阴影或发光扩张、可选基线 |
| `hit` | 节点局部 GUI 坐标 | 独立交互区域。默认随自己的姿态变换，`withHitFollowsPose(false)` 可保留悬停前的区域 |

`View` 的四种边界均为屏幕 GUI 空间中的轴对齐包围盒。`hitTest` 先检查继承的裁剪，再逆变换到点击矩形，旋转后也可用。折叠到不可逆比例时命中返回 false。绘制与命中读取同一次发布的 View，光学度量变化不会替换点击矩形。需要保留原生焦点、旁白和鼠标路由的控件可通过 `api.widget(widget)` 测量，在输入前用 `api.placeWidget(widget, view)` 应用整数平移。

父级裁剪在屏幕空间共用。已排好的目标使用裁剪后的可见边界，源节点用局部裁剪参与对齐，最终位置再与父级裁剪相交。这样同一条约束不会因控件前一帧是否越界而来回切换。旋转裁剪以屏幕包围盒为准，`LayoutApi.draw` 用原生整数 GUI scissor 向外取整，自绘命中仍使用精确裁剪。

## 对齐、文字和光学补偿

- 同一轴可以对齐不同边缘，例如 `horizontal(LEFT, RIGHT).offset(6, 0)` 把自身左边贴到目标右边外 6 个 GUI 单位。左右固定，`START/END` 使用宿主设定的 `layout.direction(...)`。
- `relativeToParent()` 在根节点回退到屏幕区域。`relativeToScreen()` 直接对齐该区域。`relativeTo(rect/anchor)` 使用屏幕空间矩形或点。`relativeTo(node)` 支持兄弟节点和宿主实时更新的动态锚点。
- 几何中心取逻辑或渲染矩形中心。可见内容中心取有效可见矩形中心。启用 `opticalCorrection(true)` 后，`VISUAL` 居中使用自动重心加上 `withCorrection(x, y)`。每条约束还可以用 `opticalOffset(x, y)` 额外调整源中心。两个偏移均描述中心本身的位置，`offset(x, y)` 则移动最终放置位置。
- `assets.texture(id, threshold)` 缓存透明度阈值下的有效边缘和 alpha 加权重心。全透明贴图保留空内容和纹理中心。作者给出的 `textureMetadata(id, metrics)` 优先，适合图标形状、描边和视觉重量需要人工校准时使用。动态着色器、阴影和发光由宿主通过 `withEffects(...)` 补充范围。
- `assets.text(font, formattedSequence)` 使用一行文字的视觉顺序、字体集、字形包围盒及样式测量，按字形包围盒面积估算重心。文字应使用同一 Font 和视觉序列绘制。无法读取字形时使用原生文字宽高。基线按两端原生字体的绘制原点约定估算，自定义基线可用 `withBaseline(y)`。需要对齐图标和文字时，可给图标提供自己的基线，也可对齐到指定的锚点。
- 首次加载资源时扫描，后续动画只对归一化结果进行投影。宿主在资源或字体重载后调用 `assets.invalidate()`，再更新相关节点。替换 ResourceManager 时创建新缓存。字形的面积估算和贴图的 alpha 加权重心都可配合人工修正。

## 状态、动态布局与像素

1. 在宿主的状态更新阶段，更新屏幕区域、测量结果和动画状态。等值输入直接复用。输入可以来自 `UiButton` 的缩放、`UiSlideConfirm` 的扩展宽度、`UiTimeline` 的帧或 `MusicRhythm` 的强度。注册节点和约束应在页面初始化或结构变更时完成。
2. 每帧采样一次已有轨道。可以用 `node.sample(track, nowMs, reducedMotion, value -> frameFor(value))`，或者显式调用 `node.update(currentFrame, targetFrame)`。两种状态都以同一个时刻和局部空间提供。纯姿态和尺寸动画也走同一提交入口。
3. 调用 `layout.resolve(nowMs, reducedMotion)`，排序后的脏队列仅重新求解受影响的节点。环路在发布新结果之前抛出带节点路径的异常。每个轴以优先级、登记顺序裁决，缺少有效基线时跳过该条约束，冲突和避让失败可从 `node.debug()` 查询。
4. `CURRENT` 使用本帧的源和目标几何。它适合保持实时视觉锚点。`TARGET` 使用终态几何计算位移补偿，用于预留目标位置。可调用 `node.animateTargetAlignment(Spring.FLUID_PANEL)` 并使用带时钟的 `resolve`，让可见位移从当时的轨道位置接续到新目标。每轴只由获胜约束写入其补偿轨道，尺寸和姿态仍由宿主的动画负责。静态调用 `resolve()` 会立即发布目标补偿。
5. `KEEP_ANCHOR` 让局部缩放或旋转围绕布局锚点进行，保留显式平移。`REFLOW` 沿指定轴把节点放到所有前项之后。`AVOID_OVERLAP` 沿指定轴从阻挡者后方通过。`ALLOW_OVERLAY` 表示允许覆盖。`CLAMP_TO_PARENT` 优先把节点移回父级内容区域。若尺寸放不下或夹取后发生重叠，结果保留诊断。前项使用有向依赖，适合局部行列或可展开面板。
6. 从未取整的结果生成最终 View。先组合父级、局部姿态及布局位移，再按实际 `Window.getGuiScale()` 做一次吸附。可选物理像素原点、物理像素锚点、整数 GUI 原点或保留亚像素。奇数宽度可通过锚点选择吸附中心还是边缘。流式避让的参与节点保留亚像素，以免独立舍入破坏已解出的最小间隙。强制夹取边缘无法满足像素格时保留容器约束。吸附位置始终不回写下一帧的布局。

`AlignmentDebugOverlay.draw` 绘制当前边界、锚点、光学中心、约束关系和目标轮廓。`describe` 输出图例、最近失效原因和诊断。开发者面板 → 控件图库 → 视觉对齐可以反复打断展开并切换叠层。

## 更新成本

拓扑图仅在登记或移除节点及约束时重建。静态帧跳过遍历，局部更新按脏分支和它们的实际后继求解。避让对本节点的前项做一次轴向扫描，局部排序的成本为 O(k log k)，应把前项限制在相关行或列。GUI Scale 改变只重建被读取的绘制 View。资源查找用 `assets.stats()` 查看纹理缓存命中与缺失，布局用 `resolve` 返回的 `Stats` 查看本帧重算和缓存命中数。`VisualAlignmentTest` 的 2,500 节点基准覆盖两条同步动画分支，每帧只求解 4 个节点。
