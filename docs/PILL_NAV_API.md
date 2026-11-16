<!-- 作者：山海／dishanhai -->
# PillNav 胶囊导航

入口：SHBUI 控制面板 → 开发者 → 控件图库 →「胶囊导航 · PillNav」。
Forge 1.20.1、NeoForge 1.21.1 和 NeoForge 26.1.2 共用 UUK 状态、布局与绘制逻辑。

`ShBetterUiApi.controls().pillNav(...)` 创建单个原生控件。导航动作通过 `(value, index)` 回调交给宿主，
不会自行打开网页。`Item.value` 对应参考 React 源码的 `href`；标签用 `Component.translatable`。
图标和标志用现有 `GraphicIcon`，也可由调用方提供资源包透明 PNG。

```java
var nav = ShBetterUiApi.controls().pillNav(font, new Bounds(x, y, 360, 260),
        Component.translatable("example.navigation"),
        List.of(new UiPillNav.Item("home", Component.translatable("example.home")),
                new UiPillNav.Item("work", Component.translatable("example.work"))),
        "home", null, PillNavState.Options.DEFAULT, UiPillNav.Palette.DARK,
        ShBetterUiApi.settings().preferences(), (value, index) -> navigate(value), open -> {});
addRenderableWidget(nav);
```

`Bounds` 使用 GUI 坐标，宽度表示可用空间，高度包含标题及下拉菜单预留区。静止时只绘制并命中标题及条目。
菜单过长时按可用高度显示部分行，滚轮浏览；键盘移动焦点会自动滚动到对应行。放不开一行时不展开。
窄窗口或 GUI 缩放导致自然宽度不足时，`AUTO` 会折叠成菜单按钮。`NEVER` 保留横向导航，
键盘聚焦超出区域的条目时会横移到可见位置。

| Options 参数 | 值／默认值 |
| --- | --- |
| layout | SPLIT（默认）／JOINED |
| size | SM：36、MD：44（默认）、LG：52 GUI 单位 |
| surface | GLASS（默认）／SOLID／NONE |
| itemStyle | PILL（默认）／PLAIN |
| hoverEffect | FILL（默认，圆形填充与文字翻滚）／GLIDE（双边弹簧）／NONE |
| indicator | DOT（默认，速度拉伸圆点）／PILL／NONE |
| collapse | AUTO（默认）／ALWAYS／NEVER |
| bounce、stretch | 0–1，默认 0.3、0.5；有限值自动限制范围 |
| intro | 默认开启；关闭再开启可以重播入场 |

`FILL + PILL` 使用每个条目的持续填充；其他悬停模式与 `PILL` 组合使用共享滑块。
圆点始终跟随活动项，滑块可临时跟随悬停或键盘焦点。弹簧使用参考源码的 520／360 刚度、
前后沿差速、240 Hz 子步长及 50 ms 帧间隔上限。入场与菜单切换使用原生 GUI 帧采样；
阴影由现有圆角几何分层绘制，玻璃使用各版本现有的背景取样能力。

`Palette.DARK`／`LIGHT` 对应原始 CSS 配色；`withBarColor`、`withTextColor`、
`withHighlight(argb, nullableInk)` 支持覆盖。省略高亮文字颜色时按原始相对亮度阈值选择黑／白。
高对比模式使用实色背景并重新计算文字对比，关闭模糊或取样不可用时回退实色。
减少动效模式将弹簧、填充、翻滚、菜单和入场直接定位，停止标志旋转。

`select(value)` 设置本地选择而不回调。`setActiveValue(value)` 启用受控选择，点击仍回调，
活动项等待宿主同步；未知值隐藏活动指示。再次调用 `select` 恢复本地选择。
`setOptions`／`setPalette`／`setPreferences` 可在挂载后更新。

方向键移动焦点，Enter／空格执行，Home／End 跳转首尾，Home 可聚焦标志。Esc 先取消按压，再收起菜单。
拖离按下目标或失焦会取消，只有同一目标按下与松开才触发动作。所有菜单关闭路径统一调用 `onMenuToggle`。
长标签省略后仍可用 tooltip 和旁白读取完整内容。宿主卸载时调用 `cancelInteraction()`。

核心无 Minecraft 依赖：`kernel.api.controls.PillNavState`，可单独复用测量、选择与动画。
定向验证：`kernelTest --tests '*PillNavStateTest'`。游戏内的逐帧外观、GUI 缩放及实际旁白仍需客户端验收。
