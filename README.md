# SH-Better-UI（焕界 UI 库）

**简体中文** · [繁體中文](README.zh-TW.md) · [English](README.en.md)

作者：山海／dishanhai。

SH-Better-UI 是 Forge 1.20.1 与 NeoForge 1.21.1 的 Minecraft 客户端 UI 库。两个平台共用玻璃材质、模糊、动画、搜索与物品／流体选取组件，并各自提供独立模组 jar。接入方模组以外部依赖接入，更新时替换对应平台的 UI 库 jar。

## Forge 1.20.1 环境与依赖

- Minecraft 1.20.1、Forge 47.4.16 或更高、Java 17（运行端）。
- [本机音乐 API 与 Windows 桥构建](docs/local-music.md)
- 必需 KotlinForForge 4.12.0（2.2.21 runtime），由整合包单独提供；本库不内嵌任何 jar。
- 使用 LDLib 接口时另装 `ldlib-forge-1.20.1-1.0.33.b.jar`；原生 Screen 用户无须安装 LDLib。
- 开发时将 `kotlinforforge-4.12.0-all.jar` 及 `ldlib-forge-1.20.1-1.0.33.b.jar` 放入 `libs/`。

## 构建

Minecraft 26.1.2 的 NeoForge 移植使用独立构建入口，当前为 [alpha 开发版](#neoforge-2612-alpha)。

使用 `build-local.ps1` 单独构建 Forge，或执行 `簡易構建.bat` 依次构建 Forge 与 NeoForge。两个脚本以 JDK 21 启动 Gradle。Forge 生产类使用 JDK 17 工具链，现有测试使用 JDK 21。Forge 使用 Gradle 8.8，构建包含 JUnit 与 reobfJar。批处理脚本会清理两个平台各自的 `build/libs`，并列出新产物。

首次构建时先移除命令中的 `--offline`，让 Gradle 下载 JEI 编译用 API。缓存建立后可恢复离线构建。

`簡易構建.bat` 使用 UTF-8 无 BOM／Windows CRLF 换行，请保留此格式。双击执行后会保留结果窗口；自动化调用可传入 `--no-pause`，退出码沿用构建结果。

```powershell
.\gradle-install\gradle-8.8\bin\gradle.bat build --offline --no-daemon --console=plain
```

正式包为 `build/libs/sh_better_ui-1.0.3.jar`，原始重映射结果为 `build/reobfJar/output.jar`。开发包为 `build/libs/sh_better_ui-1.0.3-dev.jar`，只能用于编译与开发环境。`raw` 包是构建中间产物，不能放入游戏。

## 接入方接入

编译时使用 dev 包，发行时同时安装独立正式 UI 库。可通过 `publishToMavenLocal` 发布 `com.dishanhai:sh_better_ui:1.0.3:dev` 及 sources，或使用本地文件依赖：

```groovy
implementation files('../SH-Better-UI/build/libs/sh_better_ui-1.0.3-dev.jar')
```

接入方 `mods.toml` 声明 `sh_better_ui` 版本范围 `[1.0.2,2.0.0)`，不得再打入同一份 UI 类或 shader。`1.0.1` 提供星律烟灰材质及双样式 tooltip API；原 `1.0.0` 不具备这些入口。`1.0.2` 将烟灰面板调为中等模糊（12 GUI 单位半径），保留其他样式。接入方在客户端初始化时以 `GaussBlur.allowScreen(predicate)` 注册允许的界面；库负责唯一的帧末事件与资源生命周期。

`gt_shanhai:ultimate_terminal` 的设置与报告界面、`gt_shanhai:pattern_generator` 的生成界面使用本库的玻璃材质、模糊、弹簧与按钮反馈。换页时间轴由 `PageTransition` 提供；生成预览的错峰入场及滚动模糊强度由 `GuiMotion` 提供。界面的字段、物品数据与操作流程仍由 `gt_shanhai` 处理。

资源重载会在渲染线程释放旧管线；若目前仍开着已注册的玻璃界面，立即以新资源预热管线，无须关闭重开。登出则只释放资源。游戏验收应包含保持界面开启时 F3+T，确认模糊恢复且文字／图示仍清晰。

`1.0.3` 新增 `GlassTheme.SMOKE`，共用星律的 `GlassColors.SMOKE`。本次材质维护改用 16 GUI 单位的密采样背景模糊，保留微量提亮与边缘柔光。接入方使用此主题时，最低依赖版本须为 `[1.0.3,2.0.0)`。商店可并存经典、深蓝动态玻璃与中性烟灰；选择器跟随所选主题，烟灰内层控件不重复模糊。

### 玻璃材质接口

通用接口位于 `com.dishanhai.sh_better_ui.client.glass`、`client.blur` 与 `client.gui.scaled`。在面板内容之前绘制玻璃底，再画文字与图示：

```java
var theme = GlassTheme.GLASS;
theme.rootShadow(graphics, x, y, width, height);
theme.backdrop(graphics, x, y, width, height);
theme.box(graphics, x, y, width, height, GlassPalette.ROOT_EDGE, theme.panelBg);
```

`compat.ldlib.GlassPanelTexture` 提供 `root()`、`panel()`、`display()`、`page()` 与 `button()` 材质工厂。只有 LDLib 接入方引用此包；每个按钮各自建立 `button()`，让 hover 弹簧保持独立。

### 客户端毛玻璃配置

Forge 和 NeoForge 客户端首次启动后，在 `config/sh_better_ui-client.toml` 的 `[glassBackdrop]` 段调整毛玻璃背景。配置重新加载后，接下来的绘制会采用新值。

| 设置 | 默认值 | 范围与效果 |
| --- | --- | --- |
| `enabled` | `true` | 设为 `false` 跳过毛玻璃背景后处理，保留半透明底板。 |
| `blurRadiusScale` | `1.0` | `0.0–2.0`，乘以各材质原有的模糊半径。设为 `0.0` 时，仍可显示色调和颗粒。 |
| `strength` | `1.0` | `0.0–1.0`，缩放整体效果强度。设为 `0.0` 跳过毛玻璃背景后处理。 |
| `tintScale` | `1.0` | `0.0–2.0`，缩放材质原有的色调混合量。 |
| `grainScale` | `1.0` | `0.0–2.0`，缩放材质原有的颗粒强度。 |

设置适用于通过本库绘制的毛玻璃背景，包括根面板、分区、按钮与提示框。经典主题沿用自己的绘制方式，换页模糊和点击折射波各自独立。

### 主控面板的反馈与标签栏配置

F8 主控面板使用彩虹点阵背景，顶层分为「配置」「开发配置」「模组概览」。配置页包含毛玻璃、显示偏好、动效与反馈、标签栏和快捷键；开发配置提供采样质量、控件底板模糊及开发者面板入口。支持草稿预览、应用、重置及撤销重置，切换标签保留草稿。设置保存在客户端配置的 `[ui]` 段，Forge 与 NeoForge 使用相同键。

模组概览每秒刷新可观测的模组详情、运行状态、总体状态、模块状态、系统与设备信息；硬件和加载器元数据首次读取后缓存。长设备名称自动换行，各信息组支持键盘聚焦和完整旁白。可选依赖只显示安装状态，未将其标记为健康检查结果。

| 配置键 | 默认值 | 作用 |
| --- | --- | --- |
| `hoverAnimation` | `true` | 悬停颜色过渡与卡片抬升；关闭后保留静态悬停反馈。 |
| `pressAnimation` | `true` | 按钮按压缩放与释放过渡；关闭后保留按下颜色。 |
| `selectionPulse` | `true` | 新选中按钮的短暂强调动画；选中标记始终保留。 |
| `tooltipDelayMs` | `0` | 按钮、滑杆、滚动标签栏的悬停提示延迟，范围 `0–2000` 毫秒。 |
| `tabWheelEnabled` | `true` | 在标签栏上使用滚轮浏览，不改变选择。 |
| `tabDragEnabled` | `true` | 拖动浏览，超过阈值后放开不会误选；Esc 取消并恢复原位置。 |
| `tabAutoReveal` | `true` | 自动显示选中项；关闭后键盘焦点仍会滚入视口。 |
| `tabScrollStep` | `32` | 每格滚轮移动的 GUI 单位，范围 `8–128`，支持小数滚轮输入。 |

「减少动效」优先于各项动效开关。`ClientUiSettings.feedback()` 与 `tabs()` 分别返回独立的 `ControlFeedbackSettings`、`TabScrollSettings`；编辑一组设置会保留其余组。已有 `ClientUiSettings` 三／四参数和 `UiPreferences` 三参数构造器仍可使用，新增项取默认值。控件使用创建时传入的偏好快照；已打开的调用方画面可用 `setPreferences()` 和 `setSettings()` 更新。

### 可滚动标签栏 API

`UiScrollableSectionBar` 是一个原生控件，内部复用 `UiButton` 与 `UiSectionBar.Section`。宿主只注册标签栏一次；`buttons()` 供读取按钮位置与状态，不再单独注册内部按钮。

```java
var tabs = ShBetterUiApi.controls().scrollableSectionBar(
        new Bounds(x, y, width, 22), UiPreferences.configured(), sections,
        selectedIndex, index -> requestedSection = index);
addRenderableWidget(tabs);
```

`sections` 为含本地化名称与图标的 `List<UiSectionBar.Section>`。默认标签宽度为 104 GUI 单位；工厂重载可显式传入 `TabScrollSettings` 与标签宽度。原有等宽 `UiSectionBar` 继续可用，适合不会溢出的少量标签。

标签放不下且有空间容纳箭头时，标签栏两侧显示浏览按钮。箭头显示相邻标签并移动焦点，当前页保持不变；到达首尾时对应按钮禁用。

宿主将 `mouseScrolled` 的单轴／双轴参数转给标签栏。鼠标按下立即反馈，放开提交选择，拖动不会切页；Tab 进入标签组后，用左右键／Home／End 定位并切换，Enter／空格提交当前焦点。若宿主继承原生 `Screen`，应将捕获期间的 `mouseReleased` 显式转给 `tabs`，确保在栏外放开也能结束；在调用 `super.keyPressed` 前让标签栏处理拖动期间的 Esc，避免关闭整个画面。关闭宿主时调用 `cancelDrag()`。主控面板包含这一接入实现。

`select(index)` 更新已确认选择而不回调，`focus(index)` 移动键盘焦点并确保可见，`scrollTo(offset)` 与 `reveal(index)` 可接自定义输入，`setBounds(area)` 更新布局。绘制、鼠标命中和提示均限制在视口内。

只需滚动计算时，使用 `ShBetterUiApi.interactions().tabScrollState()` 或直接创建 `TabScrollState`。通过 `resize(viewportWidth, contentWidth)` 设置范围，再使用 `scrollBy`、`scrollTo`、`reveal`、`beginDrag`／`drag`／`endDrag` 和 `cancelDrag`；该状态不依赖 Minecraft，拖动读取复用 `PointerDrag`。

### 中性浅色玻璃

参考 `src_collection/collection/liquid-glass-react-master` 的背景模糊、边缘折射与受光亮边思路，沿用本库既有 shader 实现。浅色版改为柔和浅灰，`GlassStyle.CLEAR` 的提亮由 0.035 降至 0.008，保留较弱的受光亮边与折射；面板／独立按钮模糊半径为 14／8 GUI 单位。浅灰与烟灰底使用 65 点高斯核，旧有飞行及切页模糊保留原核。`GlassSurface.panel()` 与 `control()` 共用 `GlassColors.CLEAR`，面板内按钮只薄叠色；独立按钮保留深色文字所需的底色。shader 不可用时仍有浅灰半透明底。

```java
GlassSurface.panel(graphics, x, y, width, height);
GlassSurface.drawString(graphics, font, title, x + 8, y + 8, GlassColors.CLEAR.TEXT);
```

LDLib 可用 `GlassPanelTexture.clearPanel()` 与 `clearButton()`；按钮已位于毛玻璃面板内时用 `clearButtonOverlay()`，避免每个按钮重复执行背景模糊。`GlassColors.select(clear)` 提供相应文字、卡片与控制项颜色。原有深色入口与色板保持不变。

`GlassSurface.drawString()` 复用原版字形描边，以低透明度白色柔光托住字形，主文字保持清晰；可传入 `String` 或已排版的 `FormattedCharSequence`。烟灰主题的 `GlassTheme.drawCentered()` 已使用此入口。接入方自行调用原版 `graphics.drawString()` 的文字需改用上述接口才会有字形柔光；底板材质不会拦截其他模组的文字渲染。

主模组设置页新增「终极终端与样板生成工具使用浅色玻璃」选项，写入本机 `config/gt_shanhai/ui-client.toml`，默认关闭，重新开启相关界面后生效；终极终端的设置与报告界面共用此选项。

库不包含交易、机器、配方、AE 或 GTCEu 逻辑。游戏内外观、第三方 shader 相容及多种 GUI scale 仍须实机验收。

### Slate 暗黑模式

`client.gui.scaled.DarkModeTheme` 提供 Forge／NeoForge 共用的原生绘制接口。`background()` 画不透明的 Slate 900 底色，`card()`、`button()`、`input()` 分别画圆角卡片、按钮与输入框。以 `State.titleText()`、`secondaryText()`、`controlText()` 取得文字色，提示文字可用 `TEXT_MUTED`。主要操作与焦点用蓝色，成功状态可搭配 `SUCCESS` 与文字或图示。

每个控件保留一个 `Interaction.card()` 或 `Interaction.control()`，每帧传入实际悬停、按住、焦点与启用状态。以按钮为例：

```java
private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();

// 由原生控件提供状态，message 为已翻译的 Component。
var state = motion.sample(hovered, pressed, focused, enabled, reducedMotion, PageTransition.nowMs());
DarkModeTheme.button(graphics, x, y, width, height, state, true, () -> {
    int textX = x + (width - font.width(message)) / 2;
    int textY = y + (height - font.lineHeight) / 2;
    graphics.drawString(font, message, textX, textY, state.controlText(), false);
});
```

卡片悬停时抬升 2 个 GUI 单位，文字过渡 200 毫秒，卡片底板过渡 300 毫秒。按钮在按下的第一帧即缩至 98%，放开后以 200 毫秒复位，取消时用 `motion.cancelPress(nowMs)` 即时清除反馈。焦点环为 2 单位蓝边加 2 单位深色间隔。控件周围预留至少 4 个 GUI 单位，卡片上方预留 6 个，避免裁切焦点环。输入框先画主题底板，再由原生控件画文字、光标与选取区。界面负责原生焦点、键盘导航、讲述人及命中区。传入 `reducedMotion=true` 时，颜色与内凹反馈即时更新，省去抬升与缩放。

### Liquid Glass

Forge 1.20.1 与 NeoForge 1.21.1 的原生 `Screen` 可调用 `client.gui.scaled.LiquidGlassTheme`。先画后方内容，再调用 `toolbar()`、`popover()`、`input()` 或 `button()`，最后画前景文字与图标。工具列和按钮使用偏通透的圆角材质，输入框与浮层增加底色以稳定文字对比。按钮可沿用 `DarkModeTheme.Interaction.control()` 的状态采样。

```java
var preferences = UiPreferences.DEFAULT;
var search = new SearchField<>(font, x, y, width, 20, index, UiStyle.LIQUID_GLASS, preferences);
var button = new UiButton(x, y + 24, width, 20, label, onPress, preferences, true, UiStyle.LIQUID_GLASS);
button.setBackgroundBlurRadius(12f);
```

`UiButton.setBackgroundBlurRadius(float)` 设置液态玻璃按键的背景模糊半径，单位为 GUI 单位，默认 `12`。`0` 关闭取样并使用实色底板，大于 `32` 的值按 `32` 处理，负数及 NaN／Infinity 会抛出 `IllegalArgumentException`。`getBackgroundBlurRadius()` 可读取当前值。自绘按钮可使用 `LiquidGlassTheme.button(..., preferences, sampleBackground, blurRadius, primary, selected, emphasis, content)`，原有签名使用默认半径。

确认与取消按钮使用库内透明 PNG 图标，通过 `setIcon()` 居中绘制。本地化按钮名称用于悬停提示和旁白。

```java
confirmButton.setIcon(GraphicIcon.SUCCESS);
cancelButton.setIcon(GraphicIcon.CLOSE);
```

按钮宽高相等时呈圆形，宽高不等时呈胶囊形。轮廓、描边与焦点环由解析距离 shader 计算像素覆盖率，GUI 缩放及按下动画会自动调整抗锯齿宽度。先绘制背景，再绘制按钮，最后绘制文字与图标。背景采用密集高斯取样与圆角覆盖率过渡，按钮底色保留可见的背景颜色。`setSampleBackground(false)` 会跳过独立取样，`UiPreferences` 与全局玻璃设置继续决定模糊是否启用。

旧构造函数继续使用各控件的默认外观。位于已绘制玻璃工具列里的控件可调用 `setSampleBackground(false)` 共用底层取样。搜索候选浮层仍单独取样，继续通过 `renderSuggestions()` 在普通控件与现有后处理之后绘制。高对比、关闭背景模糊或后处理不可用时使用不透明底板。减少动效时保留静态的悬停、按下和选中反馈。

### 按钮与界面切换提示

公开的 `UiButton` 提供悬停高亮、原生功能提示及 `setSelected` 选中标记。用 `setPreview(Component.translatable(...))` 指定功能说明，默认提示显示完整按钮名称。`UiAttention` 让页签指示条连续移动，并提供新内容的短距淡入与目标亮边。减少动效时立即显示目标，保留选中标记。库内展开控件与 JEI 面板已接入。其他界面的接入、坐标换算与验证范围见 [切换反馈说明](docs/attention-feedback.md)。

### 直接拖拽、滑动与缩放

- 每个拖拽区持有一个 `PointerDrag`。用 `DarkModeTheme.dragHandle()` 画把手，以同一矩形命中。若把手画在会抬升的卡片内，命中也要计入 `state.cardOffset()`。在子控件未消耗事件时调用 `begin(button, mouseX, mouseY)`，用 `active()` 显示按住状态，再将 `offsetX()/offsetY()` 加到对象原坐标，让预览跟随指针。拖动期间可用 `DarkModeTheme.dropTarget()` 标记经界面验证的落点。`release()` 会纳入放开位置，界面在提交前应重新验证。失焦、关闭或取消时调用 `cancel()`。
- 列表拖动时，将 `move()` 返回的 `Delta.y()` 交给 `ScrollPageTurn.dragOffset(scrollOffset, delta.y(), maxScroll)`，并立即套用新偏移。放开时也套用 `release()` 返回的最后一段位移。超出边界的位移会被截去，反向时列表立即跟手；需要滚动换页的区域仍可使用下面的滚轮入口。
- `ViewportTransform.zoomAt(anchorX, anchorY, factor, minScale, maxScale)` 接收滚轮或平台手势提供的相对比例，维持锚点下的内容位置。将同一个结果交给 `GuiRenderUtil.applyViewport()` 绘制，并用 `contentX()/contentY()` 将指针换回内容坐标做命中测试。界面保留既有裁切与键盘操作，并负责接收设备手势。

### 用户掌控与无障碍

`UiPreferences` 将高对比度、减少动效与背景模糊设置交给共用绘制入口。由接入方界面读取可用的设置并传入 `DarkModeTheme.Interaction`、`ButtonFeedback.visual()`、`PageTransition.advance()` 或 `InterruptibleAnimation.sample()`。高对比度使用清楚的边界与不透明底板，减少动效时保留可辨识的状态。文字排版沿用原生字体，界面在 GUI 比例变更时重算版面。

- `UiHistory<S>` 可保存返回和本地撤销需要的独立快照。返回与撤销各用一个实例。切入子页前保存搜索、筛选和滚动位置；提交可逆编辑前保存编辑状态。由界面套用 `restore()` 取出的快照。服务器数据的撤销则由业务回执确认。
- `UiConfirmation.open()` 在当前界面上开启原生模态确认。`Prompt` 由界面传入本地化标题、操作对象与后果，以及确认和取消按钮名称。确认回调只执行一次；取消与 Escape 回到父界面，并用提供的焦点查询找到重建后的控件。可提供仅绘图的背景回调保留原界面脉络，传 `null` 时使用纯色底。长内容支持滚轮及 Page Up／Page Down。
- `UiDisclosure` 提供有原生键盘操作与讲述人的展开按钮。界面重排时可重建按钮，保留 `UiDisclosure` 与草稿，回调只切换可见性与版面。
- `ButtonFeedback.status()` 提供等待、成功、部分完成、失败、取消及超时等状态。可通过 `Component.translatable(status.translationKey())` 显示本地化文字，或在状态变更时通知讲述人。收到取消回执后再调用 `resolveCancelled()`。

### 对话框、气泡提示、动作面板与下拉刷新

`UiOverlayHost` 提供原画面的提示框、确认框、底部动作面板与控件锚点气泡。`UiPullRefresh` 提供列表顶部下拉、键盘刷新、请求去重及回执状态。Forge 与 NeoForge 原生 `Screen` 共用这些接口，另有可选的 Forge LDLib 1.x Widget 和 NeoForge LDLib2 `UIElement` 适配。示例见[画面交互 API](docs/SCREEN_INTERACTIONS.md)。

### 搜索、预测与自动填充

`client.gui.search.SearchIndex<T>` 和 `SearchField<T>` 由 Forge 1.20.1／NeoForge 1.21.1 共用。索引以已本地化的纯文字名称、任意数量的 ID 和别名建立快照。多词查询要求每个词都命中，词可落在不同字段。比对忽略大小写与多余空白。

索引会为名称和别名中的连续汉字生成拼音。输入 `tieding`、`tie ding` 或 `td` 都可以找到「铁锭」和「鐵錠」，也可和汉字或 ID 一起搜索。字面名称和 ID 优先出现在联想中，选中拼音候选时填入原名称。多音字采用默认读音，调用方可以用别名补充其他读音。

输入 `@` 联想模组，输入 `#` 联想 tag，例如 `铁锭 @mine #forge:ingots/`。模组 ID 从条目 ID 的命名空间取得；需要按模组名称或 tag 搜索时，使用 `Entry(value, name, ids, aliases, modName, tags)`，其中名称来自调用方本地化，tags 为完整的 `namespace:path` 字符串。旧四参数构造继续可用。`@` 支持模组 ID、名称、汉字全拼和首字母；含空格的模组名称可写成 `@"Applied Energistics 2"`。`#` 比对完整 tag 的片段。筛选词与普通词共同限制结果；没有前缀的普通搜索保持原有含义。

模组和 tag 候选按精确、前缀、包含、拼音匹配排序并去重，只来自当前范围内且符合其余查询词的条目。填充保留此前输入与空白，仅替换最后一个词；`Suggestion.label()` 提供候选行文字，`completion()` 提供填入的完整查询。JEI 面板和自建素材来源共用这一行为，素材或标签版本更新后随索引重建刷新。

名称中作为普通文字使用的 `@`、`#` 可放在双引号内，例如 `"Block #1"`；自动填充会按需引用并转义名称，保证填充后仍能匹配原条目。

- `search(query, scope)` 按来源顺序返回命中项目，空查询返回范围内全部项目。`suggest(query, scope, limit)` 依名称、ID 的精确／前缀／包含命中排序，最后列出别名或跨字段命中。自绘输入框可用 `Suggestion.suffixFor(query)` 取得可直接追加的灰字后缀。
- `SearchField` 沿用原生 `EditBox`。空间足够时在右侧显示 16×16 放大镜，较窄或较矮的输入框把空间留给文字。输入变更会更新联想，光标在末端且没有选取文字时才显示灰字。上下键选择，`Tab` 或点击填充，`Esc` 收起联想并保留输入。`setOnSubmit` 接收 `Enter` 提交的当前查询，`Ctrl+Z`（Mac 为 `⌘Z`）或 `undoCompletion()` 恢复填充前的查询。
- `setResponder` 在查询实际变更时通知一次。分类切换时以 `setScope` 重新提供范围，数据或语言变更时以 `setIndex` 替换索引；两者都保留输入文字。

原生 `Screen` 接入示例，`refreshResults` 和 `submitSearch` 为界面自己的方法。

```java
// import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
// import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
private SearchField<String> search;

// 在 init() 建立；名称来自游戏本地化。
var entries = List.of(new SearchIndex.Entry<>("iron",
        Component.translatable("item.minecraft.iron_ingot").getString(),
        List.of("minecraft:iron_ingot"), List.of("tieding", "td")));
var index = new SearchIndex<>(entries);
search = addRenderableWidget(new SearchField<>(font, 16, 16, 168, 20, index));
search.setResponder(query -> refreshResults(index.search(query)));
search.setOnSubmit(this::submitSearch);

// 在 render() 的普通控件绘制完成后。
search.renderSuggestions(graphics, mouseX, mouseY, height);

// 在 mouseClicked() 的普通分派前。
if (search.mouseClickedSuggestion(mouseX, mouseY, button, height)) return true;

// 在 mouseScrolled() 的普通分派前；NeoForge 请传入垂直滚动量。
if (search.mouseScrolledSuggestions(mouseX, mouseY, verticalDelta, height)) return true;

// 在 keyPressed() 的 super 调用前。Screen 默认会先处理 Esc 关闭。
if (keyCode == GLFW.GLFW_KEY_ESCAPE && search.dismissSuggestions()) return true;
```

搜索框与联想清单使用同一套 GUI 坐标。重建界面时可先保存 `getValue()`，再用 `setValue()` 还原，并以同一个范围条件更新结果和联想。

### JEI 接入与配方预览

JEI 为可选依赖。Forge 1.20.1 以 JEI `15.49.0.200` 编译，NeoForge 1.21.1 以 JEI `19.69.0.456` 编译。两版各自发布，接入方模组可以只依赖 SH-Better-UI 的公开接口。未安装 JEI 时，共用搜索仍可使用。

- `client.jei.JeiIntegration` 提供素材快照、图示与提示、配方查询及开启 JEI 的接口。使用 `isAvailable()` 判断 JEI 是否已加载当前世界的数据，`revision()` 变更时重新取得 `ingredients()`。使用 `Ingredient.key()` 区分同一注册 ID 的变体。`recipes(ingredient, direction, limit)` 查询可见配方，`open(ingredient, direction)` 开启 JEI 的完整取得方式或用途。
- `client.gui.search.JeiSearchPanel` 提供可嵌入原生 `Screen` 的搜索与只读预览。用名称、ID、别名及模组筛选找物品／流体，选中结果后查看原生提示或配方。在配方区切换「取得方式」和「用途」，也可开启完整 JEI 页面。若调用端设置 `setOnChoose`，玩家可用「选用」把素材交回调用端。

嵌入方式如下。面板需要 `144 × 132` GUI 像素，建议预留 `320 × 200` 以上让结果和配方并排。`panel` 由界面持有，界面调整尺寸时可保存 `snapshot()`，重建后用 `restore(snapshot)` 还原搜索和选取。

```java
// 在 Screen.init()，panelLeft、panelTop、panelWidth、panelHeight 由界面排版决定。
panel = new JeiSearchPanel(font, panelLeft, panelTop, panelWidth, panelHeight);
for (var widget : panel.widgets()) addRenderableWidget(widget);
JeiIntegration.setExtraAreas(this, panel::extraAreas);

// 在 Screen.render()，先绘制面板，再由 Screen 绘制原生控件，最后画覆盖层。
panel.renderBackground(graphics);
super.render(graphics, mouseX, mouseY, partialTick);
panel.renderOverlays(graphics, mouseX, mouseY, height);

// 在 Screen.tick()。
panel.tick();

// 在 Screen.mouseClicked() 和 mouseScrolled() 的普通分派前。
if (panel.mouseClicked(mouseX, mouseY, button, height)) return true;
if (panel.mouseScrolled(mouseX, mouseY, verticalDelta, height)) return true;

// 在 Screen.keyPressed() 的 super 调用前。
if (keyCode == GLFW.GLFW_KEY_ESCAPE && panel.dismissSuggestions()) return true;
```

`setExtraAreas` 让 JEI 侧栏避开当前界面的面板和联想清单。界面提供者应使用 `panel::extraAreas`，面板永久移除时可传入 `null` 撤销。NeoForge 的 `mouseScrolled` 需传入垂直滚动量。

### 物品与流体筛选、选取

`client.gui.filter.IngredientFilter` 是 JEI 与自建素材库共用的不可变 `Predicate<Ingredient>`。`withKinds`、`withIds`、`withMods`、`withTags` 采完整值比对，同类任一符合，跨类全部符合，空集合解除该类限制。自定义条件使用 `and`、`or`、`negate`。名称、ID 片段与别名仍沿用 `SearchIndex`，`JeiSearchPanel.setFilter` 同步限制搜索结果和联想。

```java
var allowed = IngredientFilter.items()
        .withMods(Set.of("minecraft", "gtceu"))
        .withTags(Set.of(ResourceLocation.tryParse("forge:ingots/iron")));

var request = new IngredientPicker.Request(pickerTitle, IngredientCatalog.automatic(),
        allowed, 8, initialSelection, uiPreferences);
IngredientPicker.open(this, request, () -> rebuiltPickerButton, selected -> {
    // 回到父界面后提交草稿；空清单可用来清除筛选。
    applySelection(selected);
});
```

`IngredientPicker`、`IngredientCatalog`、`IngredientValues` 和 `IngredientSelection` 位于 `client.gui.selection`。`Request.single` 和 `Request.multiple` 提供默认值，完整构造函数可设置业务条件、初始候选和 `UiPreferences`。选取器可依种类、名称／ID、模组及标签缩小结果。玩家可切换已选视图、撤销本地草稿修改，并通过确认返回不可变清单。取消及 Escape 放弃草稿。从 JEI 配方返回或调整 GUI 比例后保留草稿与搜索条件。

| 来源 | 素材范围 |
| --- | --- |
| `IngredientCatalog.jei()` | 目前 JEI 可见的物品与流体 |
| `IngredientCatalog.registry()` | 原生物品默认形态及源流体 |
| `IngredientCatalog.automatic()` | 优先使用 JEI，未就绪时使用原生注册表 |
| `IngredientCatalog.of(candidates)` | 调用端提供的固定候选快照 |

`IngredientValues.item(stack)` 和 `fluid(stack)` 可用原生 stack 建立自定义候选。Forge 和 NeoForge 各自接收其平台的 `FluidStack`。`Ingredient.copy(ItemStack.class)` 或 `copy(FluidStack.class)` 返回保留 NBT／数据组件及数量的副本。`Ingredient.key()` 用于目前运行时的形态辨识，持久化请保存原生 stack 数据。初始选取应使用当前来源的候选 key。动态来源可自行实现 `IngredientCatalog`，在素材、语言或标签变更后推进 `revision()`，并维持来源内同一形态的 key 稳定。JEI 候选的标签遵循其素材 helper，原生候选读取物品或流体注册表标签。

嵌入式界面可在 `JeiSearchPanel` 构造时传入素材来源，或以 `panel.setCatalog(source)` 切换，再用 `panel.setFilter(allowed)` 套用条件。`panel.setChosen(...)` 让宿主标记草稿，`refreshSelection()` 更新选用／移除按钮。`IngredientSelection` 可供宿主维护草稿、限制选取数与重新验证候选。既有 `setOnChoose` 仍在明确选用时返回单项。

### 滚动到边界时换页

每个分页滚动区各自建立一个 `client.gui.scaled.ScrollPageTurn`。指标命中滚动区、子控件未消耗事件时，用本次滚轮事件算出并限制目标偏移，再交给库判断。向下卷到页底时切下一页，向上卷到页顶时切上一页。内容不足一屏时可直接用滚轮换页。首尾页停在边界，同方向的连续输入需间隔至少 180 毫秒才能再次换页。

```java
private final ScrollPageTurn pageTurn = new ScrollPageTurn();

// 在滚动区的滚轮回调中。页码从 0 开始，偏移向下递增。
double target = Math.clamp(scrollTarget - wheelDelta * pixelsPerStep, 0.0, maxScroll);
int targetPage = pageTurn.targetPage(wheelDelta, target, maxScroll,
        currentPage, pageCount, PageTransition.nowMs());
if (targetPage != currentPage) {
    // 使用原换页入口。下一页从顶端开始，上一页从底端开始。
    openPage(targetPage, targetPage < currentPage);
    return true;
}
scrollTarget = target;
```

由界面在换页后计算新页面的滚动范围、定位并更新现有转场。更换列表来源或重新开启滚动区时调用 `pageTurn.reset()`。

### 点阵动画背景

`client.gui.scaled.UiDotMatrix` 可用于整页或局部面板。每块背景持有一个实例，在宿主底板之后、前景控件之前调用 `render()`。`DotMatrixProfile.VIOLET` 提供黑底紫色方点和柔光，`setProfile()` 可在运行时换配色或调整动画。

从主控面板进入「开发配置 → 开发者面板 → 材质图库」，点当前样例标题，在目录选择「点阵动画」。此样例演示彩虹渐变与随机淡出。基础设置可实时调节色相、效果、浮动概率和数量上限。标题右侧的「动效设置」可调节淡出概率、色板、流向和渐变周期。点击预览区或聚焦后按 Enter 可触发波纹。点「查看 Java 代码」打开抽屉，里面可以查看完整代码和复制示例。

```java
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotMatrix;
import java.util.List;

private final UiDotMatrix dots = new UiDotMatrix(DotMatrixProfile.VIOLET
        .withPalette(new DotMatrixProfile.Palette(0xFF030A11, 0xA02A6984, 0xF07AD5FF, 0.55f))
        .withMotion(new DotMatrixProfile.Motion(DotMatrixProfile.Effect.WAVE, 2800, 0.12f, 24, 9f))
        .withFade(new DotMatrixProfile.Fade(0.25f, 2200))
        .withGradient(new DotMatrixProfile.Gradient(List.of(
                new DotMatrixProfile.ColorStop(0f, 0xFFFF71AA),
                new DotMatrixProfile.ColorStop(0.22f, 0xC079D5FF),
                new DotMatrixProfile.ColorStop(0.66f, 0xFFB2FFB5)),
                3600, (x, y, phase) -> phase - x * x - 0.35f * y)));

// 在画面的背景绘制阶段调用
Bounds area = new Bounds(0, 0, width, height);
dots.render(graphics, area, mouseX, mouseY);

// 宿主处理点击或键盘激活时，可从事件位置或已激活控件的中心触发
// dots.pulse(area, x, y);
```

原生 `Screen` 在 Forge 1.20.1 可于 `render()` 先画底图和点阵，再由 `super.render()` 绘制控件。NeoForge 1.21.1 的 `super.render()` 会调用四参数的 `renderBackground()`，将底图和点阵画在该方法中即可。

- `Palette` 使用 ARGB，依次设置底色、点色、高亮色和光晕强度。透明底色可露出已绘制的内容。
- `Grid` 设置间距、方点大小、密度和随机种子。区域过大时自动放宽间距，使网格采样不超过 16,384 格。方点及光晕裁切在 `Bounds` 内。
- `Motion` 设置效果、周期毫秒数、每轮每点浮动概率、同时浮动上限和最大上浮高度。概率、上限或上浮高度设为零即可停用浮动。可选常亮 `STEADY`、闪烁 `TWINKLE`、行波 `WAVE`，也可实现 `Effect.brightness()` 自行计算亮度。
- `Fade` 单独设置随机淡出再浮现的概率（0–1）和周期毫秒数。`Fade.NONE` 关闭淡出。
- `Gradient` 可使用 `Gradient.RAINBOW`，也可传入自定义 ARGB 色标。首个位置为 `0`，后续位置严格递增且小于 `1`。各段按色标位置插值，末色接回首色。渐变提供方点的 RGB，色标 Alpha 乘以原有透明度，仍可叠加亮度、浮动和互动。`Gradient.NONE` 使用原配色，`VIOLET` 默认关闭渐变与随机淡出。
- 渐变方向可使用 `Flow.RIGHT`、`DOWN`、`DIAGONAL` 和 `OUTWARD`，也可实现 `Flow.position(x, y, phase)`。坐标和周期进度归一化，返回的位置按 1 循环。示例用横坐标的平方改变横向色带宽度，并叠加纵向偏移。
- `Interaction` 设置悬停和波纹半径，各自设为零即可关闭。背景使用宿主给定的指针与点击坐标，沿用宿主的命中、焦点和事件回传。显式时钟的 `render()` 和 `pulse()` 须使用同一个单调毫秒时钟。
- 减少动效时方点静止、随机淡出暂停，渐变冻结在起始相位。悬停高亮和固定范围的点击淡出仍可使用。高对比时使用纯色底板。模态覆层打开时，宿主可传入 `Double.NaN` 暂停悬停，并用 `clearPulse()` 清除波纹。

### 可打断动画

`client.gui.scaled.InterruptibleAnimation` 复用 `Spring`，提供保留位置与速度的开关、反向及换目标，并支持延迟换目标、停止、立即完成与减少动效模式。Forge／NeoForge 共用同一接口；每个控件独立持有实例，事件与绘制使用相同的单调毫秒时钟。本次亦修正 Spring 延迟换目标时冻结旧轨迹的问题。完整接入及生命周期示例见[可打断动画](docs/interruptible-animation.md)。

```java
// 控件字段；相关类型均位于 client.gui.scaled。
private final InterruptibleAnimation motion = new InterruptibleAnimation(Spring.PANEL);
// 开关事件：motion.setOpen(open, PageTransition.nowMs());
// 每帧绘制：float progress = motion.sample(PageTransition.nowMs(), reducedMotion);
// 关闭途中再次 setOpen(true, ...) 会从当时的位置与速度继续。
```

### 配方切换、模式指示与实时状态

- `UiRecipePageTransition.start(direction, viewport, nowMs, preferences)` 使用配方修改器的 640 ms 双页滑动。每帧取 `sample()`，将只负责绘制的旧页和新页回调交给 `draw()`。侧栏可读取 `Frame.companion()`，在遮罩最深时换内容。只显示当前页的场景用 `enter()` 和单回调 `draw()`，鼠标坐标减去同一帧的 `incomingX()`、`incomingY()`。
- `UiButton.setModeIndicators(count, selectedIndex)` 用零基索引标记当前模式。开关可用 `UiToggleButton.setModeIndicatorsVisible(true)`，显示关和开两档并随 `setValue()` 同步。
- `UiDynamicStatus` 接收 `Supplier<Snapshot>`。`Snapshot` 可组合 `ButtonFeedback.Status`、自定义 `Component` 和 0–100 的整数进度。每次 `render()` 只读取一次来源，返回的快照也可用于旁白。状态图标可通过 `UiDynamicStatus.icon(status)` 单独使用。

这些 API 可在游戏内的开发者图库预览并复制 Java 示例。

### 加载提示

- 首页卡片等待数据时用 `UiSkeleton.card()` 占位。自定义布局可用 `UiSkeleton.draw()` 传入与内容位置对应的占位块。
- 列表正在刷新或请求下一批数据时，用 `UiLoading.edgeSpinner()` 在顶部或底部显示小圆环。`UiPullRefresh` 在等待回执期间已接入顶部圆环。
- 上传、下载与可测量的加载用 `UiLoading.progressBar()` 或 `topBar()` 直接绘制完成量除以总量。总量未知时，顶部加载条可使用 `nowMs` 重载。
- 整理任务可把真实完成比例传给 `UiLoading.spinner()` 的 `float` 重载，弧长表达进度。传入 `nowMs` 则绘制未知进度的圆环。减少动效时未知进度提示停留在静态位置，真实进度仍随数据更新。

### 可选按钮与玻璃动效

`client.gui.scaled` 提供三项可组合的装饰接口，接入方调用后才显示：

- `ClickRipple` + `GlassEffectRenderer.drawRipple`：每个按钮独立持有点击涟漪，420ms 扩散淡出；连点会重启，原点随控件重排。
- `GlassEffectRenderer.drawFlowBorder`：沿矩形四边循环的细流光，2 秒一轮。
- `GlassEffectRenderer.drawBreathingGlow`：控件外侧 3 GUI 像素柔光，2.4 秒一轮，强度可直接使用已有 hover 弹簧。

点击回调确认操作被接受后，调用 `ripple.trigger(mouseX, mouseY, x, y, w, h, nowMs)`；每帧先画底板，再画装饰，最后画文字与图示：

```java
GlassEffectRenderer.drawBreathingGlow(g, x, y, w, h, hover, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawFlowBorder(g, x, y, w, h, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawRipple(g, x, y, w, h, ripple, nowMs, reducedMotion, accent);
```

`ripple` 为控件自己的 `new ClickRipple()`，`accent` 使用当前主题色；点击与绘制使用相同毫秒时钟。关闭／移除时调用 `ripple.clear()`。装饰与底板放在同一个 `withButtonMotion` 回调中，沿用原控件的命中区、点击行为及裁切。

`reducedMotion=true` 时，涟漪改成均匀淡出，流光与呼吸改成静态亮度。这些接口只提交原生 GUI 矩形，不新增后处理、材质或依赖；局部涟漪与 `GaussBlur.requestWave` 的屏幕折射波可分别使用。完整组合与 LDLib 接入示例见[可选玻璃效果](docs/effects.md)。

### 文字与布局对齐

`client.gui.display` 提供 `UiText` 和 `UiLayout`，Forge 与 NeoForge 使用相同接口。坐标和间距均为 GUI 逻辑单位。

```java
var area = new GraphicDisplayLayout.Bounds(12, 20, 160, 24);
UiText.drawCentered(graphics, font, Component.translatable("your_mod.title"), area, 0xFFFFFFFF, false);

var buttons = UiLayout.row(area, List.of(new UiLayout.Size(28, 18), new UiLayout.Size(44, 20)),
        4, UiLayout.Horizontal.RIGHT, UiLayout.Vertical.CENTER);
```

`drawLeft`、`drawCentered`、`drawRight` 将单行文字垂直居中，并按原生字体宽度水平对齐。可使用 `draw` 传入动态的水平对齐值。

`UiLayout.align` 定位单个元件。`row` 水平对齐整组、垂直对齐各项，`column` 垂直对齐整组、水平对齐各项。两者都返回与输入顺序一致的不可变 `Bounds` 列表。空间不足时保留原尺寸。若界面启用裁切，绘制与命中均使用容器与对应 `Bounds` 的交集。

### 图形化显示

Forge 与 NeoForge 的原生 `Screen` 可使用 `client.gui.display` 下的图标与状态面板。状态和翻页图标有 `INFO`、`SUCCESS`、`WARNING`、`ERROR`、`PREVIOUS`、`NEXT`。通用操作图标有 `ADD`、`REMOVE`、`SEARCH`、`SETTINGS`、`REFRESH`、`CLOSE`。可用 `new GraphicIcon(textureId)` 引用自己的 16×16 透明 PNG，白色图形可按 ARGB 着色。给 `UiButton` 设置图标时仍应传入本地化按钮名称，供悬停提示和旁白使用。

内置图标的 64×64 透明副本位于 `sh_better_ui:textures/gui/display_64/<图标名>.png`，与 16×16 原图同名。副本按最近邻放大 4 倍，适用于按 64×64 读取完整纹理的调用方。内置 `GraphicIcon` 常数引用 16×16 原图。

商城界面可用 `PRODUCT_CARD`（商品卡片）、`PRODUCT`（商品）、`PRODUCT_OUTPUT`（商品产出）、`PRODUCT_INPUT`（商品输入）、`MY_PROFILE`（我的）、`MY_FAVORITES`（我的收藏）、`HIDE`（隐藏）、`SELECT_ALL`（全部）和 `SELECT_PARTIAL`（部分）。工具与功能入口可用 `RENDER`（渲染）、`TOOL`（工具）、`TOOLBAR`（工具栏）、`INSTALL`（安装）、`COMPATIBILITY`（兼容）和 `QUANTUM`（量子）。

文件类图标有 `FOLDER`（文件夹）、`FILE`（通用文件）、`DOCUMENT`（文档）、`FILE_EXE`（.exe）、`FILE_MD`（.md）、`FILE_JAVA`（.java）和 `FILE_KT`（.kt）。模组与配方界面可用 `KUBEJS`（按 [KubeJS 项目图标](https://modrinth.com/mod/kubejs)重绘）、`AE2_PATTERN`（AE2 样板）和 `CRAFTING`（合成）。商店入口可用 `SHOP`。

分类和统计可用 `CATEGORY`、`STATISTICS`，计划和目标可用 `PLAN`、`GOAL`。数学标记可用 `EQUATION`（方程）和 `FUNCTION`（函数）。撤销操作可用 `UNDO`，权限入口和拒绝访问状态分别用 `PERMISSIONS`、`ACCESS_DENIED`。

内容卡片、通用频道、会员和地点可用 `CARD`、`CHANNEL`、`MEMBERSHIP`、`LOCATION`。浏览器入口使用带地址栏的 `BROWSER`，网页搜索使用地球加放大镜的 `WEB_SEARCH`。

输入工具栏可用 `EMOJI`（表情）、`SYMBOL`（符号）、`TRANSLATE`（翻译）和 `CLIPBOARD`（剪贴板）。文本输入框和数字输入分别使用 `TEXT_INPUT`、`NUMBER_INPUT`。

数据流向可用 `INPUT`（输入）和 `OUTPUT`（输出）。配置和模组入口可用 `CONFIG`、`MOD`。`GUIDEME` 按 [GuideME 模组图标](https://modrinth.com/mod/guideme)重绘，通用指南可用 `GUIDE`。界面相关入口可用 `GUI`（带指针的操作窗口）和 `UI`（带侧栏的布局面板）。

HUD 行首可使用按 8 像素网格设计的 `COMPACT_SIGNAL`、`COMPACT_CLOCK`、`COMPACT_CALENDAR`、`COMPACT_WORLD`、`COMPACT_BIOME`、`COMPACT_LOCATION`、`COMPACT_CHUNK` 和 `COMPACT_TORCH`。用 0.5 倍 PoseStack 绘制时，8 个 GUI 像素即可保留完整轮廓。

其他操作可用 `FILTER`、`SORT`、`EDIT`、`COPY`、`DELETE`、`FAVORITE`、`LOCK`、`UNLOCK`、`EXPAND`、`COLLAPSE`、`UPLOAD` 和 `DOWNLOAD`。例如用 `button.setLeadingIcon(GraphicIcon.FILTER)` 在筛选按钮的文字旁显示漏斗。

导航用 `BACK`（返回）、`FORWARD`（前进），列表翻页用 `PREVIOUS` 和 `NEXT`。工作流按钮可用 `SWITCH`（切换）、`MERGE`（合并）、`NEW`（新建）、`SAVE`（保存）、`EXPORT`（导出）、`COMMENT`（评论）和 `DELETE`（删除）。`COMMENT` 与 `BUBBLE` 共用气泡图案。

提示和取消可用 `WARNING`（警告）、`CRITICAL_WARNING`（严重警告）、`CANCEL`（普通取消，与 `CLOSE` 共用叉号）和 `CRITICAL_CANCEL`（严重取消）。严重状态使用八边形轮廓，在单色主题中也能区分。维护与显示操作可用 `REPAIR`（修复）、`RESET`（重置）、`DISABLE`（禁用）、`ENABLE`（启用）、`HIDE`（隐藏）、`SHOW`（显示）、`ORGANIZE`（整理）和 `SORT`（排序）。

工具和分类图标包括 `CACHE`（缓存）、`LOG`（日志）、`MONITOR`（监控）、`TODO`（待办）、`DOCUMENT`（文档）、`CODE`（代码）、`TEST`（测试）、`DEVELOPMENT`（开发）、`COLLECTION`（集合）和 `SEPARATE`（分离）。`SEPARATE` 的箭头从一条路径分成两条，与 `MERGE` 配对使用。

记录、账户和设备图标包括 `HISTORY`（历史记录）、`USER`（单个用户）、`SYSTEM`（系统）、`PHONE`（手机）和 `COMPUTER`（电脑）。成员列表仍可用 `MEMBERS`，监控面板可用 `MONITOR`。

操作系统图标可用 `WINDOWS`、`MACOS`、`IOS`、`ANDROID` 和 `LINUX`，均提供可着色的 16×16 原图和 64×64 副本。

任务控制按钮可用 `RUN`（运行）、`PAUSE`（暂停）和 `RESUME`（继续）。继续图标在播放三角左侧保留一条暂停线，便于区分开始运行与恢复运行。

游戏和设备图标包括 `MINECRAFT`（苦力怕脸）、`GAMEPAD`（手柄／游玩）、`MOUSE`（鼠标）、`KEYBOARD`（键盘）、`HEADPHONES`（耳机）和 `MICROPHONE`（麦克风／语音）。加载器可用 `FORGE`（铁砧）和 `NEOFORGE`（狐狸头）。游戏和加载器标识是适合主题着色的单色像素图案。

模组图标包括 `AE2`（ME 控制器通电正面）、`MEKANISM`（齿轮与闪电）、`FTB_QUESTS`（束带任务书）、`KUBEJS`（拼图方块）和 `GREGTECH`（GTCEu 徽记）。图标由本库随包提供，可直接用于 `UiButton`。

内容分类用 `BLOCK`（立体方块）和 `ITEM`（锭形物品）。详情入口用 `ATTRIBUTES`（属性）、`DESCRIPTION`（描述）、`TOOLTIP`（悬停提示）和 `INTERACTION`（交互）。

归档和安全操作用 `COMPRESS`（压缩）、`DECOMPRESS`（解压）、`ENCRYPT`（加密）和 `DECRYPT`（解密）。插件与资料入口用 `PLUGIN`（插件）、`GITHUB`（GitHub）、`MCMOD`（MC 百科）和 `WIKI`（通用 Wiki）。

管理入口可用 `SETTINGS`（设置）、`CONFIG`（配置）、`MANAGEMENT`（管理）、`PERMISSIONS`（权限）、`LIST`（列表）、`DETAILS`（详情）、`EDIT`（编辑）、`MOD`（模组）和 `TEMPLATE`（模板）。基础设施用 `DATA`（数据）、`NETWORK`（网络）、`SERVER`（服务器）和 `DATABASE`（数据库）。运维用 `BACKUP`（备份）、`MIGRATE`（迁移）、`UPGRADE`（升级）、`SECURITY`（安全）、`AUDIT`（审计）和 `REPORT`（报告）。

执行与调度用 `QUEUE`（队列）、`POLLING`（轮询）、`ASYNC`（异步）、`CONCURRENCY`（并发）、`THREAD`（线程）、`PROCESS`（进程）、`PARALLEL`（并行）和 `SCHEDULER`（调度）。任务与奖励用 `TASK` 和 `REWARD`。

GitHub 仓库入口可用 `GITHUB_REPO`（仓库）、`GITHUB_PULL_REQUEST`（Pull requests）、`GITHUB_ISSUE`（Issues）、`GITHUB_DISCUSSION`（Discussions）、`GITHUB_ACTIONS`（Actions）、`GITHUB_README`（README）、`GITHUB_RELEASE`（Releases）和 `GITHUB_COMMITS`（Commits）。两种尺寸取自 GitHub 的 Octicons，授权声明随 JAR 一起提供。[Octicons](https://github.com/primer/octicons)

项目许可用 `LICENSE`（通用许可）、`LICENSE_OPEN_SOURCE`（开源许可）和 `LICENSE_CLOSED_SOURCE`（闭源许可）。项目信息入口可用 `AUTHORIZATION`（授权）、`CONTRIBUTORS`（贡献者）、`AFDIAN`（爱发电品牌）、`SUPPORT`（发电／支持创作者）和 `ACKNOWLEDGMENTS`（鸣谢）。

AI 服务入口可用 `CHATGPT`、`CLAUDE`、`DEEPSEEK`、`GROK` 和 `GEMINI`。图标提供 16×16 与 64×64 两种透明尺寸，Gemini 使用白色像素绘制的内凹四角星。

联络和获取支持的入口可用 `QQ`、`WECHAT`（微信）、`X`（原 Twitter）、`DISCORD`、`TELEGRAM`、`WHATSAPP` 和 `FACEBOOK`。邮件入口可用 `EMAIL`（通用信封）、`QQ_MAIL`、`ICLOUD_MAIL` 和 `GMAIL`。调用方可通过 `UiButton.setLeadingIcon` 将图标与本地化按钮文字配合使用。

视频平台入口可用 `BILIBILI`（哔哩哔哩）、`DOUYIN`（抖音）、`KUAISHOU`（快手）和 `YOUTUBE`。

通用入口可用 `MUSIC`（音乐）、`APP`（应用）、`QUESTION`（疑问）、`CALCULATE`（计算）、`CPU`、`GPU`（显卡）、`DISK`（磁盘）和 `MAP`（地图）。AE2 的 ME 网络频道使用 `AE_CHANNEL`，图案取自智能线缆的频道指示线。

- 商店和奖励入口使用 `SHOP`（商店）、`BANK`（银行）、`MEMBERSHIP`（会员）、`POINTS`（积分）、`REDEEM`（兑换）、`COUPON`（优惠券）、`POWER`（电力）、`SPARK`（星火）和 `CURRENCY`（货币）。
- 交易和账务操作使用 `TRADE`（交易）、`PAYMENT`（支付）、`BILL`（账单）、`SETTLEMENT`（结算）、`WITHDRAW`（提现）、`TRANSFER`（转账）、`BALANCE`（余额）、`BUY`（购买）和 `SELL`（出售）。
- 商业导航可用 `CURRENCY`（货币中心）、`REDEEM`（兑换中心）、`MEMBERSHIP`（银行会员中心）和 `SHOPPING_CART`（购物车）。收藏和筛选使用 `FAVORITE` 和 `FILTER`。

- 内容字段可用 `TITLE`（标题）、`TAG`（标签）、`COVER`（封面）、`CATEGORY`（分类）、`CHINESE`（中文）、`SUMMARY`（简述）、`BODY_TEXT`（正文）和 `TEXTURE`（材质）。
- 模组入口可用 `SOPHISTICATED_BACKPACKS`（精妙背包）和 `SHBUI`（焕界 UI 库）。`SHBUI` 的白色轮廓取自模组彩色封面 `cover.png`。
- 主题入口可用 `DARK_MODE`（深色模式）、`LIGHT_MODE`（浅色模式）和 `CUSTOM_THEME`（自定义主题）。

```java
// 面板内框使用 Screen 的逻辑坐标。entries 是当前页的 List<GraphicDisplay.Entry>。
var area = new GraphicDisplayLayout.Bounds(x, y, width, height);
var layout = GraphicDisplayLayout.arrange(area, entries.size(), 28, pageCount > 1);

// 在 init 中注册控件。changePage 更新当前页、布局及两个按钮的 active。
var previous = addRenderableWidget(GraphicDisplay.pageButton(
        layout.previous(), false, preferences, button -> changePage(-1)));
var next = addRenderableWidget(GraphicDisplay.pageButton(
        layout.next(), true, preferences, button -> changePage(1)));
previous.active = previous.visible && pageNumber > 1;
next.active = next.visible && pageNumber < pageCount;

// 在 render 中使用当前页的 layout，页码从 1 开始。
GraphicDisplay.drawRows(graphics, font, layout, entries, 1, textColor, rowColor);
GraphicDisplay.drawPageNumber(graphics, font, layout, pageNumber, pageCount, textColor);
super.render(graphics, mouseX, mouseY, partialTick);
GraphicDisplay.drawRowTooltip(graphics, font, layout, entries, mouseX, mouseY);
```

默认在宽度达 240 GUI 单位时排两列，窄版排一列。需要自定义列数时，将 `Grid(最小列宽, 最多列数)` 传给 `arrange`。例如 `new Grid(68, 4)` 会随可用宽度在一至四列之间重排。

```java
var grid = new GraphicDisplayLayout.Grid(68, 4);
int capacity = GraphicDisplayLayout.pageCapacity(area, 28, true, grid);
var layout = GraphicDisplayLayout.arrange(area, entries.size(), 28, true, grid);
```

`pageCapacity` 用相同的内框、行高、分页开关及网格规则计算可完整显示的条目数，可用于切分当前页。容量为 0 时需给内容留出足够空间。切页或缩放后按当前内框和条目数重算布局。使用 2× 图标时应预留至少 40 GUI 单位行高。状态文字传入本地化 `Component`，超出行高可悬停查看完整标签。如果宿主还套了一层视口裁切，只在鼠标位于宿主可见区时显示提示。底板和页码状态由宿主管理，翻页控件沿用 `UiButton` 的点击、键盘焦点和旁白。

### 网络风格图形化 UI API

`client.gui.scaled.NetworkTheme` 提供深色半透明圆角面板、蓝色描边的标题／设置行、原生编辑框和描边页签。Forge 与 NeoForge 的原生 `Screen` 使用同一套接口。所有边界均为 GUI 逻辑坐标，缩放后在 `init()` 重算。

- `NetworkTheme.drawPanel()` 绘制面板，`drawField()` 显示标题或只读设置行。`labelColor()` 可给标签部分着色，数值部分保留自己的颜色。
- `NetworkTheme.tab()` 返回 `UiButton`。以 `setSelected()` 标记当前页，标签传入本地化 `Component`，供提示与旁白读取。图标按钮预留至少 20×20 GUI 单位。内置图标为 16×16 px 透明 PNG，依次为 `HOME`、`LIST`、`WIRELESS`、`TOPOLOGY`、`STATISTICS`、`MEMBERS`、`SETTINGS` 和 `ADD`，也可以传入自己的 `GraphicIcon`。
- `NetworkTheme.editBox()` 返回原生 `EditBox`。先用 `setValue()` 写入初值，再注册 `setResponder()`。用 `setFilter()` 限制输入，用接入方自己的确认操作提交。
- `NetworkToggle` 由 `setValue()` 更新显示。鼠标、Enter 或空格会请求取反。接入方等待回包时可设置 `active = false`，收到结果后更新显示值并恢复操作。开关行至少 40×14 GUI 单位。
- 状态图文沿用 `GraphicDisplay.drawRows()`，可通过 `Component` 样式为不同数值着色。`UiPreferences` 控制高对比与减少动效。

```java
// init() 中注册控件。Bounds 是 GraphicDisplayLayout.Bounds。
var home = addRenderableWidget(NetworkTheme.tab(new Bounds(x + 12, y - 24, 20, 20),
        Component.translatable("example.tab.home"), GraphicIcon.HOME, preferences,
        button -> showHomePage()));
home.setSelected(isHomePage());

var name = addRenderableWidget(NetworkTheme.editBox(font, new Bounds(x + 12, y + 38, 160, 18),
        Component.translatable("example.setting.name"), preferences));
name.setValue(savedName);
name.setResponder(value -> nameDraft = value);

var bypass = addRenderableWidget(new NetworkToggle(new Bounds(x + 12, y + 100, 160, 18),
        Component.translatable("example.setting.bypass"), serverBypassEnabled, preferences,
        enabled -> requestBypassChange(enabled)));

// render() 中先画底板、标题与状态行，再让 super.render(...) 绘制原生控件。
NetworkTheme.drawPanel(graphics, new Bounds(x, y, 184, 132), preferences);
NetworkTheme.drawField(graphics, font, new Bounds(x + 12, y + 10, 160, 18), networkTitle, preferences);
var statusLayout = GraphicDisplayLayout.arrange(new Bounds(x + 12, y + 62, 160, 30),
        statusEntries.size(), 30, false, new GraphicDisplayLayout.Grid(160, 1));
GraphicDisplay.drawRows(graphics, font, statusLayout, statusEntries, 1, DarkModeTheme.TEXT, 0);
```

示例中的翻译键、页面切换、草稿、状态条目与回包逻辑由接入方提供。

### 整页网络布局与 Screen 模板

`client.gui.display.NetworkScreen` 可以直接传给 `Minecraft#setScreen`。模板会布置 1–7 个顶部图标页签、独立的右侧新增按钮、网络标题、设置行、状态图文和开关。画面有返回图标，也支持 Escape 返回父画面。每个页签保留自己的页码，窄窗口自动折行，内容过高时按完整行分页。

```java
// 在客户端线程执行。以下翻译键及 state、otherTabs 由接入方提供。
var overview = new NetworkScreen.Tab(
        Component.translatable("your_mod.network.overview"), GraphicIcon.HOME, state::networkTitle,
        List.of(
                new NetworkScreen.Edit(Component.translatable("your_mod.network.name"), box -> {
                    box.setMaxLength(40);
                    box.setValue(state.nameDraft());
                    box.setResponder(state::setNameDraft);
                }),
                new NetworkScreen.Field(Component.translatable("your_mod.network.priority"), state::priorityText),
                new NetworkScreen.Field(Component.translatable("your_mod.network.limit"), state::limitText),
                new NetworkScreen.Status(state::transferAndBufferEntry),
                new NetworkScreen.Toggle(Component.translatable("your_mod.network.surge"),
                        state::surgeEnabled, state::canChangeSurge, state::requestSurge),
                new NetworkScreen.Toggle(Component.translatable("your_mod.network.bypass"),
                        state::bypassEnabled, state::canChangeBypass, state::requestBypass),
                new NetworkScreen.Toggle(Component.translatable("your_mod.network.chunk_loading"),
                        state::chunkLoadingEnabled, state::canChangeChunkLoading, state::requestChunkLoading)
        ));

var tabs = new ArrayList<NetworkScreen.Tab>();
tabs.add(overview);
tabs.addAll(otherTabs); // 最多再加 6 页，每页提供自己的标题和内容行。
var add = new NetworkScreen.AddAction(Component.translatable("your_mod.network.create"), state::openCreate);
Minecraft.getInstance().setScreen(new NetworkScreen(parent,
        Component.translatable("your_mod.network.screen"), preferences, tabs, add));
```

`Status` 返回一个 `GraphicDisplay.Entry`。它的标签可用 `CommonComponents.NEW_LINE` 连接传输与缓存两行，并分别设置数值颜色。`Edit` 的配置会在切页、翻页或缩放后重建控件，草稿保存在接入方的状态里。`Toggle` 每帧读取已确认的值，提交回调只发出请求。等待确认时让 `canChange...` 返回 `false`。

已有画面可单独调用 `NetworkLayout.arrange(width, height, tabCount, hasAdd, rowKinds, font.lineHeight, pageIndex)`。传入的行类型为 `FIELD`、`STATUS`、`TOGGLE`，返回的 `Bounds` 可直接交给 `NetworkTheme`、`GraphicDisplay` 或原生控件。结果的 `firstRow` 是当前页对应的原始行下标，`pageIndex` 从 0 开始。空间不足以容纳完整控件时返回空值。

### 小弹窗提醒

在 Forge 或 NeoForge 的客户端收到任务结果后，调用 `client.gui.scaled.UiToast`。库会在当前界面或游戏 HUD 上显示提示，不占用鼠标或键盘焦点。

```java
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;

UiToast.taskCompleted(Component.translatable("your_mod.task.example"));
UiToast.show(UiToast.Kind.WARNING,
        Component.translatable("your_mod.notice.warning"),
        Component.empty(), UiToast.Effect.POP);
```

默认从屏幕右侧外滑入左上角，停留后向右退出。可选 `DROP_FROM_TOP`（从顶部滑落）、`FADE`（淡入淡出）、`POP`（缩放弹出）和 `NONE`（直接显示）。状态有 `INFO`、`SUCCESS`、`WARNING` 和 `ERROR`。

提示依次显示，默认基础停留时间为 3500 毫秒，受 Minecraft 通知时长设置影响。完整 `show` 重载可传入停留时间与 `UiPreferences`，启用减少动效时会直接显示。视觉内容最多六行，建议传入本地化短句。系统旁白启用时会朗读完整内容。

#### 可滑动关闭的通知

在原生 `Screen` 内使用 `UiSwipeToast`。`BOTTOM_RIGHT` 固定在画面右下角；`INLINE` 使用调用方传入的槽位宽度，可用 `occupiedHeight()` 排列后续项目。两种位置都要注册成可渲染控件，才能绘制并参与原生键盘焦点。开发者面板 → 控件图库 → 浮动通知提供两种示例。

```java
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;

// 在 Screen 子类内创建。restore 是由调用方提供的撤销操作。
var toast = addRenderableWidget(new UiSwipeToast(this, UiSwipeToast.Placement.BOTTOM_RIGHT,
        new UiSwipeToast.Content(Component.translatable("your_mod.archive.title"),
                Component.translatable("your_mod.archive.details"), null,
                new UiSwipeToast.Action(Component.translatable("your_mod.archive.undo"), restore)),
        new UiSwipeToast.Options(4000, true, true, true), UiSwipeToast.Style.DEFAULT,
        UiPreferences.configured(), reason -> onToastClosed(reason)));
```

向下拖过阈值或快速轻甩可关闭。短拖、反向释放及失焦取消会归位。Tab 聚焦后可用左右键选择操作或关闭，按 Enter／空格激活，或按 Esc 关闭支持滑动的通知。悬停暂停可配置；拖动、聚焦和窗口失焦时暂停。`Fuse.TOP`、`BOTTOM` 和 `NONE` 只影响进度线的绘制；`setDurationMs(0)` 让通知持续显示。

`setOpen(false)` 主动关闭；关闭回调在退出动画和行内槽位收合结束后触发一次，可区分 `TIMEOUT`、`SWIPE`、`ACTION`、`CLOSE`、`ESCAPE` 和 `PROGRAMMATIC`。控件由画面渲染推进，请保留到关闭回调后再移除。`setOpen(true)` 可重新打开并重新计时。行内模式用 `setInlineBounds(x, y, availableWidth)` 指定 GUI 坐标。

可直接使用 `SwipeGesture`（位移、速度和向下滑动判定）和 `PausableCountdown`（暂停、重置和剩余比例）。两者接收调用方提供的单调毫秒时间，不依赖 Screen 或通知。`ShBetterUiApi.interactions()` 同样提供 `swipeGesture()`、`countdown(...)` 和 `swipeToast(...)`。

### 中性玻璃渲染预览

左为调整前，右为调整后。玻璃使用实际 shader 离屏渲染与实际色板；背景、字体与排版为测试示意，并非 Minecraft 实机界面。文字柔光的接入范围及验证结果见[玻璃维护记录](docs/neutral-glass-maintenance.md)，游戏验收项目见 [TODO](TODO.md)。

![中性玻璃渲染对照](docs/previews/2026-11-07-neutral-glass-comparison.png)

## NeoForge 1.21.1

`neoforge/` 是独立构建，使用 Java 21、NeoForge 21.1.251、ModDevGradle 2.0.148 与 Gradle 9.2.1。NeoForge 共用玻璃色板、面板、动效与 shader，并以平台来源处理模组入口、客户端事件、tooltip、模糊管线与原生素材转接。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:TEMP = 'C:\jtmp'
$env:TMP = 'C:\jtmp'
.\neoforge-1.21.1\gradlew.bat -p neoforge-1.21.1 test build --no-daemon --console=plain
```

正式包为 `neoforge-1.21.1/build/libs/sh_better_ui-neoforge-1.21.1-<version>.jar`。NeoForge 使用 Mojang 命名，不需要 Forge 1.20.1 的 devJar／reobfJar 分流；接入方项目编译与游戏端均依赖此正式 jar。

游戏端另外安装 **KotlinForForge NeoForge 5.10.0 或相容的 5.x**，不可使用 Forge 1.20.1 的 4.x。已验证前置 5.10.0 使用 Kotlin 2.2.20，与本库 Kotlin compiler 2.2.21 同一条版本线。前置由整合包提供，本库不内嵌 Kotlin／KFF。`FTB Quest Nexus/neoforge` 直接引用此项目产物提供玻璃 UI。

两平台共用的 shader 将 `WaveOrigin` 声明为 GLSL `vec2`，匹配 Minecraft 的 `float/count=2` 上传。资源测试检查各平台的实际构建输出。

NeoForge 版本提供原生 `Screen` API 和可选的 `compat.ldlib2.LdLib2UiBridge`。使用桥接时安装 LDLib2 `2.2.44`，首次构建时联网解析编译依赖。Forge 1.20.1 的 `compat.ldlib` 套件随 Forge 产物发布。测试与待验项目见 `docs/neoforge-verification.md` 与 `TODO.md`。

## NeoForge 26.x alpha

`neoforge-26.1.2/` 面向 Minecraft 26.1.2、NeoForge 26.1.2.126 和 Kotlin for Forge 6.2.0。`neoforge-26.2/` 面向 Minecraft 26.2、NeoForge 26.2.0.93 和 Kotlin for Forge 6.3.0。用 JDK 25 启动 Gradle。共享内核仍编译为 Java 17 字节码，构建会使用项目原有的 JDK 21 编译工具链和 JDK 17 内核测试运行时。

```bash
bash neoforge-1.21.1/gradlew -p neoforge-26.1.2 build --no-daemon --console=plain
bash neoforge-1.21.1/gradlew -p neoforge-26.2 build --no-daemon --console=plain
```

开发包位于各目标的 `build/libs/`，版本带 `-alpha.8`。两版共享内核、平台视口、素材目录、项目编辑服务、搜索索引、页面模型和 Kotlin DSL。扩展模块须在 `ModuleDescriptor.platforms` 中声明对应的 `NEOFORGE_26_1_2` 或 `NEOFORGE_26_2`。

使用 `ShBetterUiApi.controls()` 创建按钮、开关、整数滑块、步骤器、固定或可滚动分区栏、下划线标签页和展开控件。将原生控件注册到 `Screen`，开关注册 `button()`，固定分区栏逐一注册 `buttons()`。宿主可在关闭画面前先将 Esc 交给焦点控件，以取消滑块编辑或标签栏拖动。自绘界面可在 `extractRenderState` 中调用 `GuiGeometry.fill` 提交 `FilledGeometry<Geometry2.Local>`，使用当前 GUI 姿态、裁剪和 ARGB 颜色。

### 背景模糊、液态玻璃与控制面板

进入世界后按 F8，或从 NeoForge 的模组列表打开 SH-Better-UI 配置。面板提供玻璃、显示和外观设置，可预览后应用模糊、折射、边缘高光、主题、高对比和减少动效。应用成功后写入当前实例的 `config/sh_better_ui/control-panel.properties`。恢复默认可以撤销，离开未应用的草稿会要求确认。消费模组可通过 `ShBetterUiApi.settings()` 读取已应用设置或打开面板。

「工具」页可查看模组版本、帧率、堆内存、窗口缩放和实际启用的图形后端。反馈链接遵循 Minecraft 聊天设置的网页链接和确认选项。「打开面板」快捷键通过 Minecraft 的按键绑定界面修改，返回时保留面板草稿。

从「工具 → 开发者图库」可打开 76 个样例，涵盖旧版全部 75 个样例和 26.x 新增的滚动分区栏。原生样例包含控件与转场、空间材质、动态主题、粒子特效、真实音乐播放、同步歌词和图库内活动岛。图库可单独保存外观预览，返回后保留控制面板草稿。各版本覆盖范围见 [26.x 图库迁移清单](docs/NEOFORGE_26_GALLERY_GAPS.md)。

宿主完成背景提取后，可调用 `ShBetterUiApi.blur().background(graphics)`。全屏模糊沿用 Minecraft 视频设置中的菜单背景模糊强度，后续提取的前景保持清晰。要在玻璃控件内局部采样，先调用 `ShBetterUiApi.blur().prepareBackdrop(graphics)`，再提取按钮、滑块或 `LiquidGlassSurface`。需要两种效果时先请求全屏模糊。玻璃通过同帧 GPU 快照实现局部模糊、折射和边缘高光，高对比模式使用实色底板。

### Vulkan 与 GLFW 65540

Minecraft 26.1.2 使用 OpenGL。Minecraft 26.2 提供原生 Vulkan 和 OpenGL，两者使用同一套控件提取代码。未保存后端偏好的 26.2 实例在关闭 NeoForge 早期加载窗口后优先尝试 Vulkan，初始化失败时由 Minecraft 回退到 OpenGL。已保存的图形 API 和启动参数继续生效。

NeoForge 26.2 的早期加载窗口使用 OpenGL，Vulkan 无法复用该窗口。修复版在早期窗口存在时以 OpenGL 启动，保留已保存的偏好。若要在 26.2 使用 Vulkan，退出客户端，在该实例的 `config/fml.toml` 中设置 `earlyWindowControl = false`，再从视频设置选择优先 Vulkan 并重启。开发环境的 `runClient` 会为新建的 `run-vulkan` 目录预设此配置，`runClientOpenGl` 使用独立的 `run-opengl` 目录。 [NeoForge 上游问题](https://github.com/neoforged/NeoForge/issues/3230)

隔离客户端烟测会加载打包 JAR，验证控件和图库输入、模糊与玻璃像素、设置应用、模组列表配置按钮和世界内 F8 键盘回调。最后一条命令只在该烟测进程中把 Vulkan 驱动清单指向不存在的文件，以验证真正的初始化失败回退。游戏自动退出，截图保存在各自的 `build/widget-smoke/` 实例目录。

```bash
bash neoforge-1.21.1/gradlew -p neoforge-26.1.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeBackend=opengl
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeBackend=vulkan -PwidgetSmokeEarlyWindow
bash neoforge-1.21.1/gradlew -p neoforge-26.2 -I ../gradle/neoforge-26-widgets-smoke.init.gradle runWidgetSmoke -PwidgetSmokeVulkanFallback
```

给烟测命令追加 `-PwidgetSmokeLayoutReview` 可在独立的 `-layout` 实例中验证原生 GUI Scale 3、窄布局、键盘翻页焦点、折射拖动取消，以及 F8 和模组列表入口。较窄的逻辑尺寸通过 `Screen.resize` 在同一客户端中测试。

用 `-PwidgetSmokeLayoutReview=4` 可在 `-layout-4` 实例以 1280 × 960 窗口复验，并加载长文案测试语言。此模式额外检查 Tab／Shift+Tab、长标签提示换行和原生旁白生成的文本。控制面板与图库的标题、状态，以及图库按钮和滑杆的默认标签，超出空间时会显示省略号，悬停可读完整文案。

给标准烟测命令追加 `-PwidgetSmokeNarrator`，可在独立的 `-narrator` 实例启用 Minecraft 系统旁白，经游戏键盘回调切到「模式 2」并触发朗读。烟测结束时恢复该实例的旁白与音量设置。Windows 实测还可通过 `-PwidgetSmokeNarratorAudio` 接入进程音频回环录制器，检查客户端 JVM 输出的语音波形。

旧版图库其余样例、其他面板页面、统一屏幕宿主、着色图标、文本可读性、富文本、声明式页面、网络和可选模组集成仍在移植范围内。详见 [TODO.md](TODO.md)。

### 反重力粒子 API

[文档索引](docs/README.md)

通过 `ShBetterUiApi.graphics().antigravity()` 创建粒子场，独立输入跟踪使用 `ShBetterUiApi.interactions().pointerTracker()`。开发者控件图库提供四种形状、三种配色、暂停与重播。[接入与参数说明](docs/antigravity-api.md)。
