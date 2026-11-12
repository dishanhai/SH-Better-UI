# 可选玻璃效果

作者：山海／dishanhai。

这些接口是 UI 库能力，消费端调用后才显示。包名为 `com.dishanhai.sh_better_ui.client.gui.scaled`。

## 三种效果

- `ClickRipple` + `GlassEffectRenderer.drawRipple`：点击位置向外扩散的亮纹，420ms 后结束。每个按钮持有自己的实例，连续点击重启；不改变按钮操作、网络请求和命中区。
- `drawFlowBorder`：沿矩形四边顺时针流动的细亮边，2 秒一轮，最多 128 个 fill。
- `drawBreathingGlow`：控件外围 3 GUI 像素的柔光，2.4 秒一轮；`focusAmount` 可复用已有 hover 弹簧，0 时完全不画。

`reducedMotion=true` 时，涟漪变为均匀淡出，流光及呼吸保持静态亮度。调用方沿用自己的减少动效设置；库不新增配置文件。

## 组合接入示例

在消费端控件中保留实例，点击事件仍先按原有逻辑确认按钮启用、鼠标按键及命中；只在实际接受点击时触发效果：

```java
private final ClickRipple clickRipple = new ClickRipple();

// 原有 mouseClicked / 点击回调接受左键操作的位置：
clickRipple.trigger(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH,
        System.currentTimeMillis());

// 每帧，沿用原有底板、点击缩放与 hover 弹簧；先画底板，再调用装饰，最后画文字图标：
long nowMs = System.currentTimeMillis();
int accent = 0x809DDCFF; // 示例色；实际使用主题色板颜色。
GlassEffectRenderer.drawBreathingGlow(graphics, buttonX, buttonY, buttonW, buttonH,
        hoverAmount, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawFlowBorder(graphics, buttonX, buttonY, buttonW, buttonH,
        nowMs, reducedMotion, accent);
GlassEffectRenderer.drawRipple(graphics, buttonX, buttonY, buttonW, buttonH,
        clickRipple, nowMs, reducedMotion, accent);

// 原有关闭/移除回调：
clickRipple.clear();
```

多个按钮各持有一个 `ClickRipple`。原点保留为相对坐标；绘制传入控件当前矩形，窗口重排时原点随按钮移动。命中采用左上包含、右下不包含的半开区间。无效点击保持原效果，非法宽高和非有限指针坐标被忽略。

使用 `GuiRenderUtil.withButtonMotion` 时，将三个装饰调用放入原有绘制回调内，使底板、亮纹及边框一起运动；指针坐标仍使用原控件命中坐标。LDLib 在客户端绘制回调中同样可调用这些入口，坐标必须与传入的 `GuiGraphics` 当前 pose 一致。

所有绘制使用现有 `GuiGraphics.fill`，不改 scissor、pose、shader 颜色或全局后处理。外围柔光遵守调用方现有裁切，控件间应留出 3 GUI 像素空间；矩形以外不放宽鼠标命中区。

本效果是按钮内部亮纹，不替换 `GaussBlur.requestWave` 的屏幕折射波。后者仍使用既有全局单波槽；新接口可同时绘制多个局部亮纹，且不占用该槽。

## 视觉递进

Forge 1.20.1 和 NeoForge 1.21.1 共用 `VisualReveal` 与 `RevealProfile`。默认曲线的左侧 20% 使用最大半径 12 GUI 单位，中间 60% 平滑过渡，右侧 20% 保持原样。可通过 `new RevealProfile(radius, fullBlurUntil, sharpFrom)` 自订参数。半径为 0–32，位置满足 `0 <= fullBlurUntil < sharpFrom <= 1`。

```java
RevealProfile profile = RevealProfile.DEFAULT;

// 矩形模式。先绘制区域内容，再立即处理已经画到主帧缓冲的像素。
drawPreview(graphics, x, y, w, h);
VisualReveal.blurRegion(graphics, x, y, w, h, profile);

// 独立贴图模式。先绘制右下暖橙、向外渐变到灰绿的背景，再绘制 RGBA 贴图。
VisualReveal.drawRadialGradient(graphics, x, y, w, h);
VisualReveal.drawTexture(graphics, texture, x, y, w, h, profile);
```

- `blurRegion` 处理矩形内已绘制的所有内容，包括其背景。支持平移、正向轴对齐缩放及当前 GUI 裁切。裁切只缩小处理范围，过渡仍以原矩形定位。文字和 tooltip 可在此调用之后绘制。返回 `true` 表示已执行后处理，停用模糊或资源不可用时返回 `false`。
- `drawTexture` 接收完整的 `ResourceLocation` 贴图，透明区域透出既有背景。需要让模糊轮廓展开时，请在贴图内部留透明边距。半径为零、关闭模糊或 shader 尚未载入时显示原图。
- `drawRadialGradient` 也可传入 `originU, originV, innerArgb, outerArgb`。坐标从左上 `(0, 0)` 到右下 `(1, 1)`，颜色从原点扩展到最远角。渐变绘在贴图下方时，右侧细节保留原色。渐变与模糊设置分开，shader 尚未载入时跳过渐变。

## 背景模糊与移动浮层

Forge 1.20.1 和 NeoForge 1.21.1 共用 `GlassBlur.tryBackdrop`。在客户端渲染线程上先绘制当前主画面的底层，再对浮层的当前矩形取样，最后绘制浮层底板与内容。已有的背景模糊入口仍可使用。

```java
// rect 使用当前 pose 下的 GUI 坐标。每帧传入当前显示位置。
boolean sampled = GlassBlur.tryBackdrop(graphics, rect, 1f, GlassStyle.LIQUID_REGULAR, 10f, 5f);
drawPanelAndContents(graphics, rect, sampled);
```

- 半径范围为 0–32 GUI 单位。半径为零时仍可应用材质色调、颗粒和边缘，圆角为零时采用矩形。支持平移、正向轴对齐缩放与当前 GUI 裁切。
- 返回 `false` 时继续绘制可读的底板。常见情况包括停用全局模糊、矩形完全离开裁切区或 shader 不可用。使用 `UiPreferences.useBackdropBlur()` 可在取样前处理页面的高对比与模糊偏好。
- `LiquidGlassTheme.layeredPopover(..., preferences, blurStrength)` 提供柔影、圆角、半透明底板与不透明回退。每个浮层完成底板与前景后才绘制下一层，上层会取样到下层的可见内容。已在浮层内的按钮可用 `setSampleBackground(false)` 共用面板材质。
- `UiOverlayHost` 的动作用确认框保留原动作表作为下层。上层滑入时，裁切、控件及命中区域使用同一份当前坐标。下层只提供画面，取消会回到它原有的滚动位置和焦点。

## 图标着色器

给 `GraphicIcon` 绑定 shader 后，可以把它传给 `UiButton.setIcon`、`setLeadingIcon` 或 `GraphicDisplay.Entry`，也可以直接调用 `draw`。

```java
button.setLeadingIcon(GraphicIcon.REFRESH.withShader(GraphicIconShader.GRADIENT_SWEEP));
```

| 内置着色器 | 效果 |
| --- | --- |
| `GRADIENT_SWEEP` | 浅色光带沿斜线扫过图标。 |
| `COLOR_CYCLE` | 图标的色相渐变循环流动。 |
| `ENERGY_WAVE` | 波状光带在图标内周期流动。 |
| `PULSE` | 图标亮度平滑起伏。 |

自定义 shader 放在自己的 `assets/<modid>/shaders/core/`。在 Forge 或 NeoForge 客户端的 `RegisterShadersEvent` 中以 `DefaultVertexFormat.POSITION_TEX` 注册，再使用同一个资源 ID 创建图标效果。shader 尚未加载或顶点格式不同，图标会直接显示原贴图。

```java
event.registerShader(new ShaderInstance(event.getResourceProvider(), shaderId,
        DefaultVertexFormat.POSITION_TEX), loaded -> {});

var effect = new GraphicIconShader(shaderId, (shader, frame) -> {
    float phase = frame.reducedMotion() ? 0.5f : Math.floorMod(frame.timeMillis(), 2400L) / 2400f;
    shader.safeGetUniform("IconPhase").set(phase);
});
button.setLeadingIcon(GraphicIcon.REFRESH.withShader(effect));
```

- 顶点属性使用 `Position` 和 `UV0`。绘制时由库设置 `Sampler0`、`ModelViewMat`、`ProjMat` 和 `ColorModulator`，回调用于设置自定义 uniform。
- 片元输出使用直通 alpha，并将采样颜色与 `ColorModulator` 相乘。可丢弃全透明片元，让透明区域保留背景和深度。绘制沿用当前 GUI 裁切。
- 直接绘制时可传入宿主 `UiPreferences`。开启减少动效后，内置着色器显示固定帧。

## 图标动作

`withMotion()` 可单独使用，也可与 `withShader()` 叠加。动作以原有图标位置为基准，沿用同一个 GUI 裁切区域及控件命中范围。

```java
var working = GraphicIcon.REFRESH
        .withMotion(GraphicIconMotion.SPIN)
        .withShader(GraphicIconShader.ENERGY_WAVE);
button.setLeadingIcon(working);
```

`SPIN` 绕图标中心旋转，`FLOAT` 上下浮动，`BOUNCE` 周期弹跳。开启减少动效后，图标恢复原本的位置、角度和大小。传 `null` 给 `withMotion()` 可移除动作并保留已绑定的着色器。

## 格式文字与逐字效果

`UiTextEffects` 移植了 `gt_shanhai` 的动态色板与逐字效果，输入为普通 `Component` 或旧格式字符串。`UiFormattedText.draw` 接收 `Component` 或 `font.split` 得到的 `FormattedCharSequence`。`UiButton`、`GraphicDisplay` 和 `UiText` 会绘制带效果的标签，仍可用 `font.width` 和 `font.split` 测量与换行。

```java
// 使用本地化的 Component 搭配内置色板和纵向波动。
var label = UiTextEffects.apply(Component.translatable("your_mod.status.active"),
        ResourceLocation.tryParse("sh_better_ui:shanhai_dawn"), "~");
UiFormattedText.draw(graphics, font, label, x, y, 0xffffffff, false, preferences);

// 旧格式字符串可直接解析。已有 fcs: 标记的组件可直接交给绘制 API。
var legacyLabel = UiTextEffects.parse(localizedLegacyText);
var normalized = UiTextEffects.normalize(translatedComponentWithLegacyCodes);

// 在客户端初始化时注册带命名空间的色板或逐字回调。
UiTextEffects.registerPalette(paletteId, 0x65d9bf, 0x7ac9f5, 0xad9dfc);
UiTextEffects.registerEffect(effectId, glyph -> new UiTextEffects.Glyph(
        glyph.rgb(), 0f, glyph.reducedMotion() ? 0f : (float) Math.sin(glyph.index() + glyph.timeMillis() / 180.0),
        false, false));
button.setMessage(UiTextEffects.apply(Component.translatable("your_mod.status.active"), effectId));
```

- `&~$山海朝霞-文字` 选择色板并添加纵向波动。`全` 依序切换色板，`随机` 打乱每轮色板并自动添加逐字效果，`全随机` 产生逐字随机颜色。
- `parse` 支持同一字符串中的多个 FCS 段，以及 `{fire}文字{/}`、`{{fire}}文字{{/}}`、`<fire>文字</>`。`&r` 和 `§r` 结束旧效果段，`全` 保留原有明确颜色。未识别的标记保持字面值。
- `normalize(Component)` 保留当前语言的样式与文字分段，剥离识别到的旧格式码。语言切换后可从原翻译组件重新生成。
- 原版字体量宽、换行和批次绘制接入 FCS，包括物品 tooltip。装有 `gt_shanhai` 时由它接管旧标记和第三方旧格式，SHBUI 绘制自己的标记。原版带背景色的字体批次保留背景和着色。可选的 FTB Library 和 GuideME 适配随目标方法加载。
- `colorize(sequence, argb, timeMillis, preferences)` 生成当前颜色帧。`colorizeLines` 让相邻行边界上的同一效果段共享视觉码点索引。用 `Timing` 设置毫秒步长和反向流动，给多段传同一个时间戳即可对齐相位。需要逐字位移和描边时可用带时钟参数的 `draw`。
- `paletteColor` 可读取指定位置的取样色，`gradientFiller` 可生成首尾色之间的填充文字，`reveal` 支持逐字显现和末端混淆。
- `registerPalette` 可指定 `PaletteAnimation`，`registerPaletteVariants` 一次注册多个变体。`styleNames` 枚举内置名称、别名和运行时样式，`unregister` 移除运行时项。
- 色板和回调共享 ID 空间，重复注册会被拒绝。回调在绘制时执行，可读取码点、段内位置、段长度、时间和减少动效偏好。高对比模式使用原生样式。

客户端 KubeJS 可用 `SHUI.text().fcs("~", text, "rainbow")` 构造文本，或用 `SHUI.text().kjsRegister("my_palette", [0x65d9bf, 0x7ac9f5], false, "CYCLE")` 注册当前脚本世代的色板。脚本重载会释放这些注册。未加载 `gt_shanhai` 时，客户端同时提供 `ShanhaiText` 和 `ShanhaiStyleRegistry` 兼容绑定。可迁移的构造入口包括 `fcs`、`styled`、`body`、`format`、`inline`、`fromLegacy` 和元色板快捷方法。

## 视觉组合的即时效果与帧末效果

- Backdrop 在调用时采样已绘制的主目标。沿用 GlassBlur 的区域取样和暂存目标，避免直接读写同一纹理。目标跟随实际 framebuffer 尺寸，采样内容随绘制阶段刷新。shader 无法绘制时，节点结果让调用方选择实色底板。
- Screen Post 消费当前帧的一个受管主请求。旧主请求优先，旧附加请求、点击波和延迟顶层仍走 GaussBlur。消费后按 Z 和登记顺序画清晰前景，模糊禁用、无请求或跳过时也执行。
- 已绘区域的 Reveal 从原始矩形计算过渡，再按继承裁剪限制输出。独立贴图的采样在预乘 alpha 下滤波，合成前转回 straight alpha。贴图资源每次从当前纹理管理器解析，暂存目标随 shader 重载释放。shader 不可用或偏好禁用模糊时画原图。
- 径向渐变使用局部 UV 和 GUI 距离。改变姿态或裁剪时保留中心和半径定义，模糊偏好不影响渐变。shader 不可用时结果为 `UNAVAILABLE`。

开发客户端以 GUI Scale 2 跑同一烟测场景。每端采集 60 帧，从进入绑定到最后一个前景回调，包含布局发布、即时效果、帧末模糊与前景的 CPU 提交。Forge P50/P95 为 2.34/3.54 ms，NeoForge 为 2.28/3.51 ms。更大场景的 GPU 执行时间和分配量留在 TODO。

[效果时序、结果和用法](API_REFERENCE.md#visual-layout--render-composition)

## 验证边界

2026-11-07 主 agent 统一执行完整 `build --offline --no-daemon --console=plain` 成功；全库 107 项测试通过，其中新效果 17 项。测试覆盖时间轴、点击边界、相对原点、重复触发、减少动效、周期计算，以及同包 `Fill` 记录生产算法的绘制几何：四角只覆盖一次、边框完整、亮纹裁切、外光不进内容、调用数量上限。

`releaseJar`／`verifyLibraryJar` 通过，正式包与 `reobfJar/output.jar` SHA256 相同：`F6B72C8DA3EAFD866FAB5E3C35FC6A6843E7D456EF14E8D2F36E4BC45095637D`。未部署。游戏内仍需检查深蓝／烟灰／浅色底板、GUI scale 1～4、多个按钮连续点击、嵌套滚动裁切、现有点击缩放、减少动效和关闭重开。
