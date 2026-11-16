<!-- 作者：山海／dishanhai -->
# CardNav（卡片导航）

通过 `ShBetterUiApi.controls().cardNav(...)` 创建 `UiCardNav`，并用宿主的 `addRenderableWidget` 注册。
开发者面板 → 控件图库 → 卡片导航（`sh_better_ui:card_nav`）提供交互示例和可复制的 Java 接入代码。

## 分层与数据

- UUK `kernel.api.controls.CardNavMotion`：弹簧积分、反向展开、错峰及 Rise / Unfold / Fade 参数；不依赖 Minecraft。
- `CardNavControl`：共享数据、测量、宽窄布局、输入及绘制逻辑。
- `UiCardNav`：Forge 1.20.1 / NeoForge 1.21.1 的原生控件；26.1.2 提供提取式渲染适配，26.2 沿用该适配。

`Link` 接收本地化 `Component`、可选旁白名称、可选 `GraphicIcon` 和 `Runnable`。Logo 可显示文字或图标。
宿主回调负责跳转、页面路由和外部链接的校验／确认。`Item` 接收标题、说明、链接列表及可选背景和字色。
导航不会内置浏览器或自动打开任意 URL。

## 尺寸与配置

原始 React/CSS 尺寸按 0.5 转为 GUI 单位：顶栏 30、内距／间距 4、圆角 9、默认卡高 88、最大宽 400。
宽度小于 280，或多卡片每张不足 76 时改为纵向堆叠；链接换行，CTA 移到末尾。
说明文字按字体测量换行，卡片随内容增长。高度不足时在内容区域滚动，顶栏保持可见；方向键选择会滚入视口。
传入 `Bounds` 的高度是展开可用空间，收起后仅显示顶栏。

`Options` 支持主题、玻璃／实色、点击／悬停触发、三种卡片动效、错峰、回弹、速度、卡高、最大宽、强调色、菜单文字、首次入场和初始展开。
`CardNavMotion.Settings` 沿用源码限制：错峰 0–0.3、回弹 0–1、速度 0.2–3；拒绝非有限值。
`setOptions` 可实时修改配置，`setWidth` / `setHeight` 后下一帧重测布局。

## 宿主输入与生命周期

1. 宿主在每次指针按下、常规分派之前调用 `nav.pointerDown(x, y)`，包括控件外的点击，以收起菜单。
2. 宿主若先处理 Esc，须先调用已聚焦的 `nav.keyPressed(...)`；首次 Esc 收起菜单，后续继续原有返回流程。
3. 原生控件支持方向键、Enter／空格、Esc、Tab 进入／离开及内容滚轮。收起中的链接立即禁用。
4. 按下立即反馈，放开且仍命中同一项才执行。移除、暂停或隐藏时调用 `cancelInteraction()`，避免迟到的放开触发操作。
   26.x 宿主须将左键放开转给聚焦的 `UiCardNav.mouseReleased(event)`，包括指针已经拖到控件外的情况。
5. `setInputEnabled(BooleanSupplier)` 可绑定宿主的交互条件。图库已接入这些规则。

普通状态通过 `setOpen(boolean)` 更新，并向 `onOpenChange` 通知。受控模式使用 `setOpenController(BooleanSupplier)`；
回调只请求状态变化，宿主更新其状态后由下一帧同步；传入 `null` 恢复普通模式。

高对比或背景模糊关闭时使用实色，减少动效时立即呈现目标姿态。强调色文字按源码的线性亮度阈值自动选择深／浅字色。

## 原生绘制差异

游戏使用现有 UUK 玻璃与圆角绘制。Unfold 将源码 X 轴旋转转换为二维纵向投影；Fade 用淡入与字形软化近似 CSS 的整卡模糊。
因此背景采样、阴影和透视并非浏览器 CSS 的逐像素复现。所有实现保留在 SH-Better-UI，SHCLIB 无需重复打包。

实际客户端外观、不同 GUI 缩放、全屏切换、旁白语音及高对比／减少动效验收见项目 `TODO.md`。

## 2026-11-16 验证

Forge 1.20.1、NeoForge 1.21.1、26.1.2、26.2 均完成 `test`、`kernelTest`、`build` 和 `verifyLibraryJar`。
每端运行 5 项 `CardNavInputTest` 与 4 项 `CardNavMotionTest`，均为零失败。
覆盖隐藏链接、指针取消、选择关闭、受控同步、悬停延时、窄屏滚动、动画反向、大量卡片错峰、最大回弹及减少动效。

本次是 CardNav 定向验证，不代表全量测试通过。工作区其他控件持续编辑，曾出现非 CardNav 测试失败、尚未完成的适配及并行输出竞争；
最终使用 `tmp/card-nav-tests.init.gradle` 限定测试编译／发现范围，并为 Forge、1.21.1、26.1.2 设置独立 Java／Kotlin 输出，未回退其他工作。
26.x 测试使用该端的 `Font.Provider` 构造适配，避免依赖客户端字体纹理。

校验后的四端 JAR 集中在 `build/card-nav-delivery-20261116/`。已核对内核动画、共享控件、原生控件、图库场景和语言资源均在 JAR 中。
本次未安装到游戏实例，未执行游戏内验收；本任务启动的构建进程均已退出。
