<!-- 作者：山海／dishanhai -->
# UUK Animated List

原生移植用户提供的 React `AnimatedList`：保留 pop/slide/fade/none、620 ms 弹性入场、300 ms 淡入淡出、50 ms 初次错峰、半行可见阈值、animateOnce、悬停选择、键盘导航、滚动渐隐和可中断弹性选框。无需 React、CSS 或 Hugeicons 依赖。

## 原生入口

```java
var items = List.of(new UiAnimatedList.Item<>(
        "assets", myValue,
        Component.translatable("my_mod.assets"),
        Component.translatable("my_mod.assets.description"),
        Component.translatable("my_mod.assets.meta"), GraphicIcon.FOLDER));
var list = ShBetterUiApi.controls().animatedList(font,
        new Bounds(x, y, 300, 240), Component.translatable("my_mod.files"),
        items, -1, AnimatedListState.Options.DEFAULT, UiAnimatedList.Style.DEFAULT,
        ShBetterUiApi.settings().preferences(), (value, index) -> openFile(value));
addRenderableWidget(list);
```

`Bounds` 使用 GUI 单位，提供控件的宽与视口高度上限；宿主负责按可用区域限制尺寸。列表默认行高 56、间隔 8、圆角 14；完整 `Item` 构造器支持独立行高（最小 24），以 `GraphicIcon` 包装原生纹理并用 `image=true` 保留图片颜色。用户文本均为宿主传入的 `Component`。

`setItems` 按唯一 ID 保留仍存在的选择及已播放状态，删除所选项后清空选择。重复 ID 和无效尺寸会拒绝，更新期间取消尚未提交的按下/拖动。`setSelectedIndex` 只更新选择，回调仅在鼠标同项按下后放开或 Enter/空格激活时触发。拖离原项、失焦、窗口失活、卸载取消均不触发激活。

`setOptions` 可切换动画、悬停、方向键、循环、一次播放、错峰和边缘渐隐。`Style` 可配置圆角、间隔、外框、滚动条、CURRENT/DARK/LIGHT 主题和强调色。`setRenderer` 提供行内容绘制，UUK 仍统一处理行变换、透明度、视口裁切和选框；绘制器可能在多个遮罩条带中调用，应保持无副作用。

宿主移除控件时调用 `cancelInteraction()`；有嵌套预览或模态层时用 `setInputEnabled(host::interactive)` 限制输入。Tab 焦点、上下方向键、Home/End、Enter/空格和原生滚轮可用。滚动条直接拖动，Esc 或失焦恢复拖动前的位置；只有焦点列表接收键盘。停留的鼠标不会抢回键盘选择，实际移动后恢复悬停选择。

## 可独立使用的核心 API

均位于 `src/kernel/java`，Java 17，无 Minecraft/加载器依赖：

| API | 职责 |
| --- | --- |
| `kernel.api.layout.ViewportReveal` | 单元素半可见阈值、上下隐藏姿态、错峰、一次播放与连续重定向；也可同步装饰覆盖层 |
| `kernel.api.layout.VerticalEdgeFade` | 按剩余滚动距离产生上下渐隐宽度，采样 smootherstep 透明度 |
| `kernel.api.motion.SelectionIndicator` | top/height 两条可中断弹簧；复用现有 `Spring`，参数为 ω=30、ζ=0.78 |
| `kernel.api.controls.AnimatedListState` | 按 ID 更新、可变行高、选择与键鼠切换、可见性采样、布局与动画命中检测 |

所有时间参数使用同一单调毫秒时钟。`AnimatedListState` 复用 `VerticalScroll` 与 `SmoothVerticalScroll`；直接改变 `scroll()` 后调用 `synchronizeScroll()`，然后逐帧 `advance(now, reducedMotion)`。`indexAt` 是内容布局命中，`hit` 是经过缩放/移动及视口裁切的视觉命中。

## 渲染及平台边界

共享 `UiAnimatedList`/`AnimatedListPaint` 负责交互与绘制。Forge 1.20.1 与 NeoForge 1.21.1 用 GuiGraphics；26.1.2/26.2 用 GuiGraphicsExtractor，屏幕访问差异由独立桥接适配。三端资源复用同一组语言文件，26.2 复用 26.1.2 的其余实现。

CSS 材质通过现有 UUK 玻璃、圆角、渐变和图标管线绘制；透明边缘直接衰减内容 RGBA，包含图标和选框，采用 GUI 单位条带裁切。图片来自原生纹理，图标复用 UUK 图库。阴影采用原生分层近似，不包含浏览器 CSS 的离屏阴影滤镜。减少动效时取消平移/缩放和滚动/选框弹性，仅保留短淡变；高对比时使用实色底、明确描边与全透明度文字，并关闭边缘淡出。

图库 ID：`sh_better_ui:animated_list`，提供源码的 15 项示例及接入代码。

## 验证

核心契约覆盖输入切换、可变行高滚动边界、ID 更新、空列表、循环导航、无效布局、可见阈值、错峰、动画中断、减效、视觉命中、渐隐与拖动取消。绘制测试检查明色文字、实际内容渐隐、高对比和自定义绘制异常后的裁切/矩阵恢复。

尚未进行游戏内客户端验收；GUI Scale、实际旁白、玻璃取样、快速拖动和四种平台的最终画面需按项目 TODO 验收。

2026-11-16 验证结果：为避开共享工作区中的其他构建，使用各模块独立的 `tmp/animated-list-verification` 输出并顺序执行。

| 平台 | 验证结果 |
| --- | --- |
| Forge 1.20.1 | 474 项 kernelTest、3 项 AnimatedListPaintTest、编译、重映射、发行 JAR 和 verifyLibraryJar 通过 |
| NeoForge 1.21.1 | 474 项 kernelTest、3 项 AnimatedListPaintTest、编译、发行 JAR 和 verifyLibraryJar 通过 |
| NeoForge 26.1.2 / 26.2 | 每端 474 项 kernelTest、编译、assemble、verifyLibraryJar 通过 |

每端的产物位于该模块 `tmp/animated-list-verification/libs/`，没有部署到游戏实例。核心字节码为 Java 17。完整工作区测试未全绿：隔离 Forge 全量 1085 项中，既有 GraphicIconShaderTest 仍按旧的 4 项着色器清单断言，目前清单已为 10 项；26.x 完整测试编译曾受其他并行控件的 Font 构造器影响。具体待办保留在项目 TODO。
