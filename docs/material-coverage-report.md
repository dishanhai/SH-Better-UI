<!-- 作者：山海／dishanhai -->
# UMIVP 材质覆盖报告

目录记录的基本单位是公开且可独立调用的效果。共享 renderer 的近似预设可归于同一条，例如 Spatial 表面的六种预设。画廊中使用不同光学参数的透明和磨砂玻璃各自保留 ID。源代码、渲染通道与旧预览的逐项核对见 [审计](material-catalog-audit.md)。完整字段与动态生成的兼容性数据见 [源条目](../catalog/materials/sh_better_ui/) 和 [目录快照](../catalog/generated/catalog.json)。

## 已收录

表中 ID 省略 `sh_better_ui:`。51 条记录均有原生 `PreviewSpec` 路由，共用 50 个场景。材质组合包含的通道和组件在 `renderPassDependencies` 标记。

| 范围 | 目录 ID |
| --- | --- |
| 玻璃与通用表面 | `liquid_glass`、`liquid_glass_surface`、`backdrop_blur`、`clear_glass`、`frosted_glass`、`classic_glass_themes`、`slate_surface`、`network_surface`、`dynamic_theme` |
| 空间、照明与阴影 | `spatial_surface`、`spatial_shadow`、`spatial_lighting`、`glass_panel_shadow` |
| 扭曲、遮罩与合成 | `directional_blur`、`refraction_wave`、`visual_reveal`、`radial_gradient` |
| 动态背景 | `dot_matrix`、`antigravity`、`light_rays`、`laser_flow`、`code_rain`、`ascii_field`、`pattern_waves`、`shape_waves`、`electric_logo`。第十个背景注册 ID `reveal` 通过 `visual_reveal` 查找，背景层附加的底色归 `radial_gradient` |
| 局部反馈与页面过渡 | `click_ripple`、`flow_border`、`breathing_glow`、`attention_indicator`、`page_slide`、`recipe_transition`、`capsule_reveal`、`screen_crossfade` |
| 加载 | `loading_spinner`、`loading_progress`、`loading_skeleton` |
| 图标与文字 | `icon_shader`、`icon_motion`、`formatted_text_effects`、`glass_text_glow`、`floating_lyrics` |
| 音乐响应与交互 | `music_reactive`、`interruptible_animation`、`button_feedback`、`toast_motion`、`swipe_toast`、`elastic_collision`、`bottom_sheet`、`pull_refresh`、`glass_tooltip` |

## 新增的原生预览

| 预览类型 | 目录 ID |
| --- | --- |
| 独立路由与外观草稿 | `breathing_glow`、`capsule_reveal`、`click_ripple`、`flow_border` |
| 模糊、合成和过渡 | `backdrop_blur`、`directional_blur`、`refraction_wave`、`radial_gradient`、`screen_crossfade` |
| 材质、反馈、图标和文字 | `attention_indicator`、`classic_glass_themes`、`formatted_text_effects`、`glass_panel_shadow`、`glass_text_glow`、`icon_shader` |
| 空间 | `spatial_surface`、`spatial_shadow`、`spatial_lighting` |
| 手势 | `elastic_collision`、`pull_refresh` |

`swipe_toast` 与 `toast_motion` 共用 `toast` 场景，可在其中打开浮动或内嵌滑动通知。`visual_reveal` 并排展示区域模糊与贴图递进，`icon_motion` 并排展示旋转、悬浮与弹跳。代码雨／ASCII 的效果链、激光流的贴图显现及浮动歌词的逐字效果，可通过父条目的别名、参数说明和公开符号查询。图库仍可从 `effect_appearance` 打开通用设置预览。

## WebGL 2 适配

51 条内建材质均标为 `web-approximation`。`liquid_glass` 沿用共享光学着色器及其原生参考帧，其余 50 条由按材质 ID 选择的 WebGL 2 场景绘制。游戏专有的屏幕捕获、音频、输入和字体由网页场景提供对应的演示数据。各项真实像素对照和平台差异仍需单独验证。 [Web 渲染指南](web-rendering-guide.md)

## 待核实及未独立收录

| 状态 | 范围与证据 |
| --- | --- |
| 待核实 | 字体/纹理生成的一致性，以及 Linux、macOS 和更多 GPU 的原生视觉能力。 |
| 缺少公开 API | 受管 Glass 的内部捕获、着色程序对象、空间网格和 GPU 查询、图案波的 field FBO、视觉递进的横向中间 pass、电光场纹理及歌词 SDF pass。它们按所属效果保留渲染依赖。 |
| 不适合独立复用 | 画廊专用诊断、配置和编辑器页面，以及按钮、步骤器、局部模态、底部弹层、音乐 HUD、业务实时表面等完整控件和场景。可单独绘制的阴影、动效、文字、玻璃和图标已按公开入口收录。 |
| 已废弃 | 本次源条目均无已核实的弃用标记。旧玻璃与受管玻璃同时存在，兼容入口继续使用自己的材质 ID。 |

Forge 和 NeoForge 兼容字段分别记录。源条目普遍为 `source-verified`，表示共用源码或两端适配已有真实接线。原生历史记录、UMIVP 目录/路由自动化结果与实际 Web 对照独立记录，测试覆盖和内容版本以对应验证结果为准。
