<!-- 作者：山海／dishanhai -->
# 浮层布局与胶囊动画

`PopupArea.leftOf` 按屏幕、锚点和宿主矩形选择左侧可用区域。传入目标宽高时，优先选择能以更大比例容纳面板的区域；无空间时区域尺寸可为零。宿主应将自身和其他界面的实际边界传入，并自行决定空间不足时是否隐藏内容。

每个浮层持有一个 `PopupMotion`。每帧用同一单调毫秒时钟调用 `sample(open, x, y, width, height, now, reducedMotion)`；返回的矩形从右下角胶囊展开到目标范围，目标变化可中断并从当前值继续。关闭后可在进度到零前继续绘制；宿主销毁时调用 `reset`。绘制和输入命中应限制在当前矩形及宿主允许区域内，内容可按 `progress` 渐进揭示。减少动效时所有轨道立即到达目标。

由 UUK 局部模态管理器承载的菜单，可在调用 `UiLocalModal.open` 时传入 `UiExpansionController.Config.CENTERED_MENU`。布局先确定完整菜单的位置和尺寸，再以该矩形的中心为锚点展开。液态玻璃主题会在过渡中增加背景模糊并绘制一圈逐渐消退的边缘泛光。启用高对比时保留清晰的静态描边，启用减少动效时立即显示完整菜单。声明式 GlideSelect 和开发者图库的同名样例共用此预设。

NeoForge 26.1.2 和 26.2 的原生 Screen 可通过 `ShBetterUiApi.controls().centeredMenu(...)` 创建控件。注册 `menu.trigger()`，在宿主控件提取完成后调用 `menu.extract(graphics, mouseX, mouseY)`，并在宿主的鼠标按下、释放及按键回调中优先处理菜单。离开、失焦或重排时调用 `menu.reset()`。宿主提供触发按钮与菜单的 GUI 边界，菜单高度可通过 `UiCenteredMenu.menuHeight(optionCount)` 计算。使用 `UiStyle.DEFAULT` 跟随主题，使用 `UiStyle.LIQUID_GLASS` 直接预览液态玻璃。

从「开发者面板 → 控件图库」搜索 `glide_select`，点击格式按钮即可预览。方向键移动，Enter 或 Space 确认，Esc 收起。

`UiHudPanel.drawReveal` 接收目标内容位置、缩放、选中行和动画矩形：先使用与静态 HUD 相同的材质绘制一次当前背景，再在矩形内揭示内容。调用方应将动画矩形裁到可用区域，不要额外叠加浮层底板。选中行仍单独使用强调背景。关闭背景采样或无法取样时沿用对应主题回退；背景绘制必须发生在内容之前。Forge 1.20.1、NeoForge 1.21.1 与 NeoForge 26.1.2 均提供此接口，26 使用原生 GUI extractor。
