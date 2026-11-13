<!-- 作者：山海／dishanhai -->
# NeoForge 26.x 开发者图库迁移缺口

基线为 SH-Better-UI `1.0.7-alpha.8`。目标为 Minecraft 26.1.2／NeoForge 26.1.2.126 和 Minecraft 26.2／NeoForge 26.2.0.93。旧版范围为 Forge 1.20.1／NeoForge 1.21.1 共用的内置开发者图库。

按 [旧版登记入口](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/BuiltinPreviews.java) 和 [26.x 样例枚举](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/DeveloperGalleryScreen.java) 核对，26.x 图库有 **76 个入口**，涵盖旧版全部 75 个 ID，另有 `scrollable_sections`。英语、简中和繁中均有 76 个标题。同名样例与旧版的能力差异见后文。

## 2026-11-13：开发者面板与图库宿主

- 设置面板的开发者入口恢复为独立面板，分别进入控件和材质目录；保留确认、通知、底部抽屉及动画演示。目录按旧版分类与顺序排列，额外保留 26.x 的 `scrollable_sections`。材质目录也包含素材目录中关联到控件预览的条目。
- 目录可按本地化名称、拼音、预览 ID、素材 ID、别名及 `#标签` 检索，回到目录时保留搜索和选中项；素材目录快照变化时重建搜索索引。内置样例支持在当前页面放大和退出放大，按 Esc 先退出放大，再返回目录。
- 按钮、开关、胶囊开关、滑块、展开、分区栏、滚动分区栏、底线分页、步骤器、原生确认及系统通知共 11 个样例提供可滚动、复制的 26.x Java 示例。构建中的 `GalleryExamplesTest` 将实际展示的文本编译为 `Screen` 成员。其余样例涉及尚未公开的宿主 API，暂不提供伪造的接入代码。
- `ShBetterUiApi.settings()` 提供 `developerPanel(parent)`、`developerSampleIds()`、`developerSampleFullscreen(parent, id)` 和 `developerMaterialFullscreen(parent, id)`。内置预览接受短 ID 或 `sh_better_ui:` 命名空间；材质入口使用运行中的 `MaterialCatalog` 解析 ID 和别名。全屏重载可接收 `UiPreferences` 和返回焦点 Supplier，适用于父画面返回时会重建控件的宿主。

```java
var settings = ShBetterUiApi.settings();
var available = settings.developerSampleIds();
Screen preview = settings.developerSampleFullscreen(parent, "underlined_tabs");
// 在客户端线程用目标 Minecraft 版本的屏幕切换入口显示 preview。
```

未知预览／材质 ID 或材质缺少可用预览时抛出 `IllegalArgumentException`；内核素材目录不可用时，材质入口抛出 `IllegalStateException`。

## 2026-11-13：预览注册与 UUK 材质路由复查

- `ShBetterUiApi.developerPreviews()` 与 `client().registerDeveloperPreview(...)` 共用可撤销的 `PreviewRegistry`。76 个内置 ID 和第三方声明出现在同一分类、排序和搜索目录；内置 ID 仍可用短名，扩展使用完整命名空间。目录、公开样例入口及 UUK 材质 ID／别名入口读取同一注册表。登记是惰性的，打开场景时才调用工厂。
- 扩展场景使用 26.x 原生 `GuiGraphicsExtractor` 和 `PreviewContext`。`layout` 的控件坐标必须位于 `viewport` 内，`render` 按该区域裁切；重排与放大对同一个场景调用 `layout`，离开时调用 `close`。代码页往返时可用 `saveForRemount` 保存值；上下文过期后不会再附加控件。场景工厂、布局或自绘回调异常显示可旁白的失败提示和重试，日志记录异常。
- 材质路由绑定原始目录、条目和预览登记世代。撤销或替换后，活动独立页在下一次客户端 tick 返回父页；第三方页在此之前也会拦截输入和绘制。关闭登记句柄后，目录随快照更新。
- 26.x 内置声明的场景工厂提供打开原生样例的入口；图库仍直接打开现有交互样例。当前第三方场景使用独立样例页，并非旧版固定尺寸内嵌画廊；`preferredHeight`、`toolbarRightInset`、覆盖层及旧版 `UiApiContext` 尚未接通。26.x 的 `UiFrame` 使用新的提取 API 与鼠标坐标，不能将旧版 JAR 直接用于 26.x。
- 此次接通的是 UUK `MaterialCatalog` 与原生预览的发现及生命周期路由。26.x 的 `GlassService` 原生绘制、全局 `ActivityService` 和通用内核渲染图仍待移植。独立的 Screen／HUD 文字可读性服务已使用原生提取阶段，见 [26.x 文字可读性](TEXT_READABILITY_PHASE_4_5.md#neoforge-26x)。

第三方客户端可在初始化时登记并在模块停止时关闭句柄：

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.network.chat.Component;

var registration = ShBetterUiApi.client().registerDeveloperPreview(new PreviewSpec(
        "example:control", PreviewSpec.Category.CONTROLS, 100, "example.preview.control", () -> host -> {
            var area = host.viewport();
            if (area.width() < 100 || area.height() < 22) return;
            host.add(ShBetterUiApi.controls().button(new Bounds(area.x(), area.y(), 100, 22),
                    Component.translatable("gui.done"), button -> { /* 消费方操作 */ },
                    host.preferences(), false, UiStyle.DEFAULT));
        }));
// 模块结束时：registration.close();
```

预览自绘可实现 `PreviewScene.render(UiFrame)`；场景持有的资源应在 `close()` 释放。调用方须在客户端线程登记控件，不能保留旧布局的 `PreviewContext` 继续使用。

以工作区 `1.0.9-alpha.8` 构建两版并检查打包内容，每版 102 项常规测试及 357 项内核测试通过，包含 11 个示例的实际编译。26.1.2 OpenGL 与 26.2 Vulkan 的隔离客户端通过分类、搜索／清除焦点、代码复制、直接入口、放大与返回、76 项目录和独立样例控件边界检查，也通过模组列表与世界内 F8 入口。26.1.2 另通过 GUI Scale 4 长文案与资源重载布局检查。尚未用实际整合包或其他 GPU 验收。

## 最终审核修复

- `live_activity` 复用 `ActivityService.Content` 和 `SEEK` 动作模型。图库内可用键鼠展开活动详情、预览和提交进度。Esc 可取消拖动，重排保留进度及焦点，关闭后恢复目录焦点。全局活动调度、常驻 HUD、快捷键及网络仍待移植。
- 修正特效网格线段和圆盘的绕序。高对比时停用空间材质、阴影和光照中失效的控件，显示原因，并保留灯光标记的键盘移动和重置。揭露拖动可预览，Esc 恢复进度及播放。滑动确认完成后重排保留完成状态与重置提示。
- 外观和主题的保存、取消和失败提示保留在窄窗页脚，禁用已完成操作的按钮后将焦点移回可操作控件。音乐短视口保留曲名、状态与拖入说明。导入按顺序挑选可打开的音频，解码和设备打开成功后更新列表。失败保留当前音轨及播放位置，结果通过状态和旁白反馈。解码上限为 32 MiB PCM。

两版重新执行 `build`、`verifyLibraryJar`、101 项 `test` 与 355 项 `kernelTest`，零失败。三条打包客户端路线均通过 `SHBUI_GALLERY_CATALOG_PASS count=76`、活动岛、高对比、窄窗反馈、最后 25 项及模组列表／F8 入口验证。以下为 GUI Scale 2 帧缓冲中的特效像素样本数。回退路线模拟 Vulkan 初始化失败，开启减少动效并保留 Vulkan 偏好。

| 客户端路线 | 波形圆点 | 电光轮廓 | 反重力粒子 | 激光撞击耀斑 | 揭露区域内／外变化点 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 26.1.2 OpenGL | 19,108 | 5,893 | 545 | 288 | 75／0 |
| 26.2 Vulkan | 19,106 | 5,894 | 548 | 288 | 75／0 |
| 26.2 Vulkan 初始化失败后回退 OpenGL | 19,008 | 5,829 | 509 | 296 | 75／0 |

交互回归覆盖滑杆编辑、滑动确认捕获／提交／重排、抽屉键盘档位、揭露拖动取消、高对比灯光定位和活动岛进度。260×142 逻辑尺寸下截图验证外观与主题的保存、取消和模拟写入失败。音乐回归向原生 Screen 拖放回调传入根路径、目录、不支持文件、损坏 WAV、超限 WAV 及有效 WAV＋LRC，验证选曲、播放位置、失败提示和资源释放。系统文件管理器拖放与导入结果的实际系统语音仍待实测。

## 最后一批 25 个样例

| 范围 | 样例 ID | 26.x 样例能力 |
| --- | --- | --- |
| 渲染与文字（8） | `radial_gradient`、`spatial_surface`、`spatial_shadow`、`spatial_lighting`、`formatted_text_effects`、`ascii_field`、`shape_waves`、`visual_reveal` | 原生管线绘制可调径向渐变、共享空间场景的深度与光影、区域背景采样与贴图揭露。字体通过原生提取接口展示文字波动和渐变。 |
| 外观与主题（6） | `effect_appearance`、`dynamic_theme`、`click_ripple`、`flow_border`、`breathing_glow`、`capsule_reveal` | 预览强度和效果切换、直接触发、暂停及胶囊反向揭露。动态主题支持固定预设和跟随当前维度。应用／取消只作用于图库预览，应用后写入独立配置。 |
| 动态特效（7） | `dot_matrix`、`pattern_waves`、`light_rays`、`electric_logo`、`antigravity`、`code_rain`、`laser_flow` | 使用原生 GUI 绘制和共享效果数据，支持预设、键盘触发和相应的指针交互。减少动效时使用静态预览。 |
| 音乐与歌词（4） | `music_hud`、`music_island`、`local_music`、`lyrics` | 预览内通过真实 PCM 播放驱动频带和歌词时间轴，支持切曲、暂停、拖动跳播、HUD 样式切换和胶囊折叠。支持拖入 WAV、AIFF 或 AU，并读取同名 LRC 或 TTML；离开时关闭音频和解码器。 |

两版 Gradle `build` 和 `verifyLibraryJar` 均通过。每版测试报告为 101 项 `test` 与 355 项 `kernelTest`，零失败。打包 JAR 在 26.1.2 OpenGL、26.2 默认 Vulkan、26.2 Vulkan 初始化失败后的 OpenGL 三条路线均记录 `SHBUI_GALLERY_FINAL_BATCH_PASS count=25`。回退路线开启减少动效，保存的后端偏好仍为 Vulkan。

客户端在 GUI Scale 2 打开、渲染和关闭全部 25 项，重排至 260×142 逻辑尺寸后检查焦点，复验模组列表配置和世界内 F8 入口。还验证了外观参数应用／取消及重新打开读取、主题草稿取消、胶囊反向、径向渐变和贴图揭露。修正 Vulkan 着色器的阶段接口后，三条路线的揭露均有 75 个区域内取样点变化，区域外 0 个。四个音乐样例通过实际设备进度、播放／暂停、切曲、拖动取消与跳播、WAV＋LRC 导入、歌词跟随和退出释放验证。

## 前一批样例

- 控件与反馈 7 项。`dynamic_status`、`button_feedback`、`loading_spinner`、`loading_progress`、`loading_skeleton`、`attention_indicator`、`gradient_card`。
- 直接操作 5 项。`slide_confirm`、`local_modal`、`bottom_sheet`、`elastic_collision`、`pull_refresh`。
- 转场、通知和布局 6 项。`interruptible_animation`、`page_slide`、`recipe_transition`、`screen_crossfade`、`toast`、`visual_alignment`。
- 材质与图标 7 项。`directional_blur`、`refraction_wave`、`classic_glass_themes`、`glass_panel_shadow`、`glass_text_glow`、`glass_tooltip`、`icon_shader`。
- 26.1.2 和 26.2 完整构建均通过，各运行 101 项 `test`。打包客户端在 26.1.2 OpenGL、26.2 默认 Vulkan 和 Vulkan 初始化失败后的 OpenGL 逐项打开、渲染、重排并返回 25 个样例。验证滑动确认提交与取消、模态焦点圈定、抽屉方向键、转场反向、通知撤销及方向模糊和图标着色像素。回退路线启用减少动效并核对 Vulkan 偏好保留。

## 帧时间轴

- `frame_timeline` 共用旧版 `UiTimeline`。支持播放／暂停、键盘逐帧、拖动定位与取消、重置、重排后保留帧位置。开启减少动效时，播放直接显示末帧，仍可逐帧查看。
- 两版构建均通过 7 项 `UiTimelineTest`。打包 JAR 在 GUI Scale 2 的 26.1.2 OpenGL、26.2 Vulkan 和 Vulkan 初始化失败后的 OpenGL 通过键鼠、重排焦点、原生旁白文本和首末帧像素检查。回退实例同时覆盖减少动效，确认保留 Vulkan 偏好。

## 已有入口仍缺的旧版能力

| 样例 | 26.x 现状和待补细节 |
| --- | --- |
| `backdrop_blur` | 已有全屏背景模糊，强度沿用原版菜单设置。旧样例的 `GlassStyle.BACKDROP` 逐区域即时模糊、参数及区域外不变的验收仍缺。 |
| `liquid_glass` | 已有折射强度与光学开关。旧 [原生材质预览](native-preview-guide.md) 的新旧折射对照、校准图、Schema 参数面板、播放／步进、A/B 草稿、JSON 导入导出和 UUK `GlassService` 的原生绘制／诊断待接通。 |
| `clear_glass`、`frosted_glass` | 已用同帧采样展示两种材质。旧 `GlassStyle.CLEAR`／`FROSTED` 的逐区域表现与参数待对齐，尤其是烟灰毛玻璃目前用 `REGULAR` 光学预设近似。 |
| `search` | 已有图库内的苹果／胡萝卜候选和键盘补全。面向消费方的 `SearchField`、`ShBetterUiApi.search()`、灰字补全、可复用候选呈现及中文 IME 实测待补。 |
| `text_details` | 已有原生展开和详情页。可独立接入的 `UiTextDetails`、旧文本呈现和长内容的通用布局仍待移植。 |
| `dialog` | 已有原生确认／取消、返回焦点和测试通知。旧 `UiOverlayHost` 的局部对话框、同屏遮挡、动画和宿主焦点交接待接通。 |
| `slide_confirm`、`local_modal`、`bottom_sheet`、`elastic_collision`、`pull_refresh` | 样例使用原生 Screen 输入与共享 `PointerDrag`。供其他宿主使用的 `UiPointerCapture` 和内核输入权限衔接待迁移。 |
| `page_slide`、`recipe_transition`、`screen_crossfade` | 样例已有四向切页、反向裁剪、配方速度模糊及两个原生 Screen 的样片淡入淡出。景深样例使用缩放与透明度。旧版三维旋转、玻璃合成、通用 Screen 合成及第三方叠层待补。 |
| `toast`、`visual_alignment` | 样例已有三种通知入口、图库内通知队列和撤销，以及共享对齐求解器、字形与 PNG 边界测量。消费方宿主 API、长资源包文本和实机旁白待验收。 |
| `directional_blur`、`refraction_wave`、`classic_glass_themes`、`glass_tooltip`、`icon_shader` | 26.x 样例已有逐区域采样、对照和高对比回退。旧即时区域接口、自定义波源、`GlassTheme` 和 `GraphicIconShader` 消费方 API 待移植。 |
| `spatial_surface`、`spatial_shadow`、`spatial_lighting`、`visual_reveal` | 26.x 已接入共享空间场景和图库区域 shader。公开空间渲染宿主、任意路径材质、旧即时逐区域揭露 API 及高密度 GPU 性能待补。 |
| `formatted_text_effects` | 原生样例支持字形渐变和波动。旧效果注册表与富文本编辑器接入、复杂文字布局及实际系统旁白待补验。 |
| `effect_appearance`、`dynamic_theme` | 目前保存图库内的效果强度和主题选择。旧版完整参数模板、资源包注册的主题、全局外观配置和独立客户端重启恢复待补验。 |
| `light_rays`、`laser_flow`、`electric_logo` | 光束与激光流使用原生 GUI 网格绘制，电光轮廓使用内建矢量图案。旧 GPU 特效、任意图片的电光轮廓及持续负载测试待补。 |
| `live_activity` | 图库内提供原生活动岛预览。待接入全局 `ActivityService`、活动仲裁、快捷键、通知路由、远端活动与 HUD。 |
| `music_hud`、`music_island`、`local_music`、`lyrics` | 图库使用内建／拖入曲目和本地时间轴。系统媒体会话、系统音频回环、游戏内常驻 HUD 和全局悬浮歌词仍待接入。 |

## 图库和 26.x 共同依赖

| 范围 | 待补内容 |
| --- | --- |
| 图库宿主 | 已有分类搜索、目录返回状态、样例放大、11 个可复制的内置示例和第三方场景登记。仍须补固定尺寸的内嵌预览、样例前后切换、原版代码抽屉、覆盖层和其余可用 API 的示例，并在客户端验证第三方登记、撤销、重载及父页焦点。 |
| 公开路由 | 已提供统一登记表、ID 列表、全屏样例和基于 `MaterialCatalog` 的材质 ID／别名及注册世代路由。需在实际消费模组中验证登记撤销、目录替换和返回路径。 |
| 屏幕和输入 | 补 `ShBetterUiApi.screen()`、统一 `UiScreen`／`UiFrame` 宿主、覆盖层、指针捕获和主题／背景／布局／动效接口。新场景可复用现有 `controls()` 和 `GuiGeometry`，再按需求接通 `interactions()`、`layout()`、`motion()` 等分组。 |
| 原生渲染 | 补 `graphics()`、`materials()`、旧 `blur()` 区域接口以及对应 shader 和资源。把绘制接入 `GuiGraphicsExtractor` 和原生管线，核对姿态、裁剪、层级、同帧采样及释放。共享源码和资源须纳入 [26.x 打包白名单](../gradle/neoforge-26-port.gradle)。 |
| 内核与控制面板 | 26.x 已接通平台视口、材质元数据、项目编辑和基础帧调度。待补内核渲染服务的原生阶段、声明式页面、富文本、HUD／停靠、活动通知和可选网络。面板待补自定义背景／效果编辑和 HUD 玻璃调校，验收应用、取消及重启恢复。 |
| 集成 | 按目标版本核对 JEI、KubeJS、LDLib2，补实际消费模组和整合包验证。涉及网络或同步的数据需复验专用服务端和多人会话。 |

## 尚待实测

- [ ] 涉及启动和窗口的改动另测显式 OpenGL、NeoForge 早期窗口和 GLFW 65540。
- [ ] 复验第三方登记后的直接全屏入口与返回路径。继续覆盖实际 GUI 缩放下的正反向 Tab、方向键、Enter／Space、Esc 和拖动取消。
- [ ] 补各类样例的系统旁白实测，覆盖焦点、候选、状态变化、确认和取消，以及中文语音。alpha.8 的 Windows 进程回环实测覆盖英文「模式 2」，其他场景仍需分别验收。
- [ ] 用实际字体／资源包及整合包复验 GUI Scale 3／4、长标签、中文输入、高对比和减少动效。覆盖资源重载、退出／返回和前景可读性。
- [ ] 补多 GPU、渲染／着色器模组和第三方叠层兼容性。重点检查 `GuiRendererBlurMixin` 的注入共存、局部效果边界和资源重载。高密度玻璃及粒子场需记录 GPU 耗时、显存和持续运行表现。
- [ ] 验证反馈链接经过确认后实际打开浏览器，覆盖原版的允许、禁用和确认设置。

项目进度及现有实测记录见 [TODO.md](../TODO.md) 和 [26.x 构建与验证命令](../README.md#neoforge-26x-alpha)。
