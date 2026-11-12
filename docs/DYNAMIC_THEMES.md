<!-- 作者：山海／dishanhai -->
# 动态主题

Forge 1.20.1 和 NeoForge 1.21.1 使用同一套主题 API。公共引擎运行于 Java 17，客户端事件由各加载器的 `UiClient` 接入。游戏内可以从开发者图库的「材质 → 动态主题」打开示例。示例可切换深浅色、下界、末地、资源包和环境模式，也可体验高对比、减少动效与局部覆盖，退出时恢复进入前的主题偏好。

从中央引擎读取当前已发布的主题和客户端配置。

```java
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;

if (UnifiedUI.isAvailable()) {
    var engine = UnifiedUI.get();
    var theme = engine.require(UIApis.THEME);
    var snapshot = theme.snapshot();
    var settings = engine.configs().get(UIApis.CLIENT_SETTINGS);
}
```

`ThemeService` 提供同一套运行时的只读快照及可用主题 ID。`ShBetterUiApi.themes()` 提供客户端线程上的注册、切换、作用域与订阅。`UIApis.CLIENT_SETTINGS` 是 Kernel 的 User Global 设置。控制面板的「应用」和 `SettingsApi.save` 提交该设置并等待写入完成。原生主题引擎在下一帧读取设置，控制面板及使用共用调色的背景和控件跟随该帧的主题快照。

在控制面板的显示设置中切换到「液态玻璃」即可预览，选择「应用」后全局生效。主主题为液态玻璃时使用内建主题 `sh_better_ui:liquid_glass`，切回其他主主题后恢复先前的动态主题设置。也可在动态主题页按该 ID 单独选择。Kernel 的 `ThemeTokens.LIQUID_GLASS` 表示该作用域偏好玻璃材质，跟随主题的默认按钮使用现有 Liquid Glass 绘制。

「液态玻璃（HUD）」是独立预设 `sh_better_ui:hud_glass`。浮层复用 HUD 的 Clear 材质、Snell 边缘折射和前景保护。显示设置中的「背景」可预览点阵或其他已注册效果。选中有参数的背景后，可用「配置背景」打开 UUK 编辑器。编辑器的「完成」把参数带回主控面板，点击「应用」后保存。HUD 与自定义主题共用各背景的参数。背景默认关闭，游戏内保留世界画面，主菜单使用纯色底。Kernel 可通过 `ThemeTokens.HUD_GLASS` 读取该材质偏好。

「HUD 玻璃模糊」以 GUI 单位调整玻璃后方的采样半径，范围为 0–12，默认 4。设为 0 时保留边缘折射。滑动时可预览效果，点击「应用」后保存。高对比模式或关闭玻璃背景取样时使用现有回退样式。

控制面板的固定主题和生效中的环境配色优先用于共用渲染。在跟随环境模式下，环境使用默认配色时，共用渲染使用「显示偏好」中的深色、浅色或自定义配色。页面和组件可通过主题作用域覆盖继承的值。

## 注册并使用主题

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.theme.Theme;
import com.dishanhai.sh_better_ui.theme.ThemeBinding;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;

// 两端均在客户端线程执行。第三方令牌应在读取资源包之前注册。
var themes = ShBetterUiApi.themes();
var tint = themes.registerToken(ThemeTokens.color("example:panelTint", 0xff24405a));
themes.register(Theme.builder("example:forest")
        .parent("sh_better_ui:dark")
        .nameKey("example.theme.forest")
        .token(ThemeTokens.ACCENT, ColorValue.rgb(126, 205, 159))
        .derive(ThemeTokens.SURFACE, values -> ColorValue.interpolateOklab(
                values.resolve(tint), values.resolve(ThemeTokens.ACCENT), 0.12))
        .build());
themes.transitionTo("example:forest", TransitionSpec.of(450, Easing.STANDARD));

ShBetterUiApi.screen("example.screen.title")
        .dynamicTheme()
        .onInit(screen -> {
            var panel = screen.widgetThemeScope().child("inventory");
            panel.configure(local -> local.override(tint, ColorValue.rgb(43, 68, 62)));
            var button = screen.button(12, 46, 120, 22, "example.action.open", () -> {});
            button.bindTheme(panel, ThemeBinding.of(ThemeTokens.SURFACE), ThemeBinding.of(ThemeTokens.TEXT));
        })
        .open();
```

在 Forge 的 `FMLClientSetupEvent` 或 NeoForge 的同名客户端生命周期事件中，用 `event.enqueueWork` 安排注册。两端发布构建的客户端入口分别位于根目录和 `neoforge/`。独立组件和单元测试也可以创建自己的 `ThemeManager`，并传入虚拟毫秒时钟，调用 `frame()` 推进。

`ThemeRegistry` 是可跨线程读取的不可变视图。用管理器的 `register`／`unregister` 发布更新。默认拒绝同 ID，显式 `REPLACE_CODE` 仅替换程序注册的定义。内置 ID 由库保留。继承按祖先到子主题合并，缺失祖先保留现有定义和令牌默认值，并记录路径。引用要求两端使用同一个 `TokenType`，派生表达式必须纯粹，实际读取决定依赖边。自定义 `TokenType` 的值也必须不可变。

`ThemeTokens` 包含颜色、表面、文字、状态、边框、圆角、间距、字体、时长、缓动、透明度、模糊、阴影、背板和变换。直接调用 `snapshot().resolve(token)` 读取已经求值的版本。新的主题或局部定义只重算定义发生变化的令牌及其依赖项。未注册令牌的直接快照读取返回其默认值，注册主题、声明绑定或求值引用时会校验目录。

## 作用域、控件和动画

作用域可以嵌套为屏幕、容器、组件与控件节点。局部覆盖传给子节点，`block(token)` 让该令牌回到全局定义，`inheritParent(false)` 忽略全部祖先覆盖。`reparent` 在新分支解析成功后替换父节点。页面退出时 `UiApiContext` 关闭整个树，GUI 重布局只重建控件子树。独立控件在移除或回收时调用 `unbindTheme()`，也可关闭其所有者或作用域。

最终级联从强到弱为无障碍强制值、显式属性、匹配的状态样式、局部覆盖、祖先覆盖、活动主题、系统默认值。状态规则先比较匹配的状态数，再比较是否指定了具体角色，最后由较晚声明的规则覆盖。主题规则的优先级只决定主题层顺序。Slate、液态玻璃和 Network 的 `UiButton` 共用角色 `sh_better_ui:button`，支持绑定文字、底色、状态叠色、圆角、描边和透明度。液态玻璃使用大圆角令牌并沿用模糊取样和膠囊转场。Network 与 Slate 使用小圆角令牌。屏幕内其他控件继续可用兼容调色适配，或自行订阅属性。

任意自绘控件可以用 `scope.invalidationOwner(callback)` 和 `scope.bind(binding, owner, ...)` 订阅属性。`ThemeBinding.of` 读一个令牌，`combine`、`derive` 和 `when` 可组合多个令牌和控件状态。`BoundValue.state` 更新当前状态，`value()` 读取缓存。回调收到当前帧合并后的 `PAINT`、`LAYOUT`、`TEXT_MEASURE`、`RESOURCE`、`COMPOSITE` 集合，可据此进行局部更新。原生立即绘制控件每帧读缓存即可。字体、变换等令牌由各消费控件选用，库不会自动改变所有原生控件的字体或布局。

`setTheme` 立即固定主题。`transitionTo(id)` 读取目标主题的正常时长和缓动，另一重载可指定 `TransitionSpec`。默认 `RETARGET` 从时钟采样得到的当前显示值出发。`SNAP` 立即提交，`QUEUE` 保存至多 32 个请求，`FINISH_THEN_SWITCH` 只保留最后一个候选，`BLEND` 混合旧轨剩余段和新目标。色值使用带透明权重的 OKLab，数值、阴影和变换可连续插值，离散类型在缓动进度 50% 时切换。可调用 `pauseTransition`、`resumeTransition` 和 `cancelTransition`。

控件用当前主题过渡值作为底色，再合成已有 Hover、Press、Focus 和 Selected 反馈。无障碍和可读性在最终绘制时生效。减少动效或低性能模式会结束主题过渡。内置 Spring 是有界的时间曲线，打断保持位置连续。它没有物理速度状态。局部作用域编辑立即应用，局部自定义过渡可由独立管理器或控件动画驱动。

## 环境、偏好和调色板

```java
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.context.ContextKeys;
import com.dishanhai.sh_better_ui.theme.context.ThemeCondition;
import com.dishanhai.sh_better_ui.theme.context.ThemeRule;
import java.util.Set;

themes.registerRule(ThemeRule.builder("example:forest_rule")
        .when(ThemeCondition.of(Set.of(ContextKeys.BIOME),
                ctx -> ctx.get(ContextKeys.BIOME).equals("minecraft:forest")))
        .theme("example:forest")
        .priority(50).stability(250).debounce(100).minActive(400)
        .transition(TransitionSpec.of(500, Easing.EMPHASIZED))
        .build());
themes.preferences(themes.preferences().withMode(ThemePreferences.Mode.FOLLOW_ENVIRONMENT));
```

规则只在声明的依赖变化或注册变更时重新求值。稳定时间可在无新变化时到期。规则合成按优先级升序，同优先级按 ID 排序，较晚的层覆盖所声明的令牌，共同祖先只合并一次。固定主题、指定深色与浅色模式跳过环境选择。默认环境规则识别下界和末地。客户端缺少世界或玩家时，快照用类型化默认值。世界来源字段表示客户端收到的游戏状态，窗口、语言、资源版本与无障碍为客户端本地状态。第三方来源可使用独立的 `ContextKey` 和 `EnvironmentProvider`。

若固定的资源包主题暂时不可用，显示回退到石板深色，偏好仍保留该 ID。资源恢复后会重新应用。第三方可在客户端调用 `MinecraftThemes.publishExternal` 发布带命名空间的外部字段。

在控制面板「设置 → 动态主题」选取已安装主题，或输入带命名空间的 ID 后选择「设为草稿主题」。留空时跟随环境。过渡速度以百分比保存，100% 为原速，0% 为立即切换。低性能模式也会暂停主题音频效果及已绑定按钮的背景模糊。点击「应用」保存草稿，高对比和减少动效沿用显示偏好。应用仍可在 `ThemePreferences` 中设置会话内的临时偏好。默认深浅色调色板校验文字对比。透明材质的实际对比度取决于合成底色，主题提供背景令牌作为估算，高对比使用实色按钮表面。

开发环境可通过 `gradle/theme-smoke.init.gradle` 的 `-PthemeSmokeSettings=write` 和 `-PthemeSmokeSettings=read` 两次 `runClient` 验证面板写入、放弃草稿、重启读回及 Kernel User Global 快照。测试使用独立游戏目录。

`ThemePaletteGenerator.fromSeed` 从固定色或第三方颜色生成调色板，`generate(PaletteSource, light, executor)` 在工作线程采样并发布不可变结果。图片最多 4,194,304 像素并抽样最多 4,096 点。可以用 `ThemePalette.apply(Theme.Builder)` 将结果加入自己的主题。Minecraft 的生物群系和天空调色板由 `MinecraftThemes.environmentPalette` 提供。显式启用 `albumPaletteEnabled(true)` 后可读取当前封面的调色板，停止播放时回到空值。音频采集仍使用库原来的用户授权开关。`audioEffects()` 返回独立的平滑 Glow 和 Pulse 数据，适合每帧自绘使用。

## 资源包与开发热重载

把 JSON 放入 `assets/<namespace>/ui/themes/<path>.json`。ID 来自文件路径，显式 `id` 必须相同。根目录的内置 `aurora.json` 展示继承、别名、颜色混合和状态样式。编辑器可使用 [dynamic-theme.schema.json](dynamic-theme.schema.json)。

- `schemaVersion` 当前为 1。未知字段、重复键、未知令牌、错误值、冲突和循环会拒绝这一批主题。
- 同一路径按 Minecraft 资源包顺序取最高层，覆盖关系写入诊断。代码主题和资源主题的 ID 冲突时整批拒绝。
- 颜色接受 `#RRGGBB` 或 `#AARRGGBB`，持续时间为整数毫秒。`{"ref":"color.accent"}` 表示同类型引用，`{"mix":[a,b,0.1]}` 用于颜色表达式。无前缀令牌名按 `sh_better_ui` 解释。状态样式值使用字面量。
- JSON 每文件 64 KiB，单批获胜文件合计 4 MiB，继承 32 层，表达式深度 8 层。第三方字面量解码器通过 `MinecraftThemes.registerResourceCodec` 注册并在工作线程调用。
- F3 + T 或资源包切换触发原生重载。准备任务仅产出不可变结果。客户端按代际和当前注册表验证后原子替换，过期或失败批次保留上一有效状态。

## 事件与诊断

`observe(observer)` 订阅运行时，`observe(scope, observer)` 在作用域关闭时自动解绑。控件使用令牌绑定接收精确失效。回调顺序为请求或规则解析、目标确定、过渡开始／更新／终止、完成时的 `CHANGED`、这一帧显示令牌差异。`WILL_CHANGE` 的事件快照为变更前的画面。回调发出的新操作排在当前事件批次后，取消订阅即时生效，运行时异常写入诊断。取消和打断各自产生终止事件。

`themes.inspect(scope)` 查看作用域、父级、覆盖、上下文、规则优先级、过渡进度、失效数量和缓存命中。`ThemeInspector.property(scope, role, states, token, explicit)` 返回最终值、获胜层和依赖。背景/图片混合时应使用实际输出做对比度验收。

## 验证命令

在 Git Bash 中从仓库根目录运行，构建守护进程需能找到 Java 17 与 Java 21。

```bash
./gradle-install/gradle-8.8/bin/gradle.bat test verifyLibraryJar
(cd neoforge && ./gradlew.bat test verifyLibraryJar)
./gradle-install/gradle-8.8/bin/gradle.bat -I gradle/theme-benchmark.init.gradle benchmarkDynamicTheme
./gradle-install/gradle-8.8/bin/gradle.bat -I gradle/theme-smoke.init.gradle runClient
(cd neoforge && ./gradlew.bat -I ../gradle/theme-smoke.init.gradle runClient)
```

实机脚本使用独立游戏目录，检测菜单启动、无世界回退、窗口尺寸变化、GUI 缩放、原生按压、页面卸载和 F3 + T 对应的资源重载流程。它对 11 张场景截图进行像素比较，输出位于 `build/dynamic-theme/screenshots`。基线位于 `src/themeSmoke/baseline`，像素通道允许 5 级偏差，控件周围采样点最多 2% 超出容差。基准方法和实测值见 [DYNAMIC_THEME_PERFORMANCE.md](DYNAMIC_THEME_PERFORMANCE.md)。世界和多人验收状态见项目 `TODO.md`。

在任一实机命令中加入 `-PthemeSmokeWorlds`，可在隔离目录创建两个超平坦世界，验证生物群系和天气同步、日夜规则、维度主题、世界切换和断开后的回退。刷新基线时可用 `-PthemeSmokeCaptureOnly` 生成候选截图，再人工审核后替换已跟踪的 PNG。
