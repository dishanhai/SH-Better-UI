<!-- 作者：山海／dishanhai -->
# Unified UI Kernel 交互复查（2026-11-14）

本次以当前工作区源码为准，重点检查声明式页面、共享 Screen 输入与焦点、列表、滚动、远程动作、富文本编辑器和通知。以下是按具体触发条件确认的代码行为；除另有说明的单元探针外，结论来自源码调用链，尚未经过 Minecraft 客户端或联机复现。

适用范围：前 13 项针对 Forge 1.20.1 和 NeoForge 1.21.1 共享的主要客户端实现。NeoForge 26.1.2／26.2 使用独立的原生适配层，当前启动器未注册同一 `ClientPages` 实现，不应直接把声明式页面问题归到 26.x；两版原生交互的独立复查见文末。

## P1：可能误操作或丢失输入

1. **远程动作超时后，重试可能变成第二次业务操作。** 请求等待 8 秒后，客户端把请求状态设为超时、清空请求 ID 并重新启用动作；第一个请求的迟到回执不再匹配。用户再次点击会生成新的 ID，而服务端只按请求 ID 去重。如果首个操作已执行但未及时收到回执，且业务更新为空或未推进页面版本，两次操作都可能被接受。页面虽提示“服务器未确认操作结果”，仍缺少结果核对或安全重试路径。建议保留结果未知的请求身份，提供状态核对或使用原请求 ID 查询／重放回执；关闭页面也应说明结果仍可能发生。[客户端超时与结果](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPages.java#L586) · [新请求 ID](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPageNetwork.java#L490) · [服务端回执去重](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/internal/protocol/UiServerConnection.java#L538)

2. **声明式页面的本地草稿离开时没有保护。** 文本、开关和其他输入写入 `drafts`；`hasUnsavedInput()` 仅跟踪带动作的文本／滑杆，且只用于优先模态能否打断。返回按钮或 Escape 经 `UiScreen.onClose()` 直接移除页面；终态 `Snapshot` 也不包含本地草稿。普通文本字段可没有动作，因此输入后返回会失去当前界面里的编辑内容，除非消费方另行读取和保存。建议按字段用途区分临时搜索和需要保护的草稿，在用户主动离开时提供继续编辑／舍弃；异步提交只在成功确认后标记已保存。[草稿与提交](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageView.java#L488) · [离开与作用域](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPages.java#L693) · [本地草稿 API](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/page/PageService.java#L61)

3. **共享 `UiScreen` 的叠层悬停与点击目标不一致。** 使用 `addClipped(..., stackId)` 注册两个重叠且可点击的控件时，`getChildAt()` 按绘制的反序选上层，而 `mouseClicked()` 按添加顺序调用，可能先触发下层。临时 JUnit 探针已构造真实 `UiScreen` 和两个原生 widget，观测到“命中查询选上层、点击计数落到下层”。建议点击、悬停和滚轮共用同一命中结果，并覆盖不可用上层、裁剪和释放。声明式页面的 `stack` 校验目前禁止交互子项，所以这一结论针对直接使用该共享 Screen 接口的消费方，不应表述为声明式 `stack` 已能触发两个按钮。[叠层命中与点击](../src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreen.java#L334) · [声明式限制](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/page/UiPageValidator.java#L274)

4. **拖动绑定滑杆时，权威数据变化会抹掉用户正在拖动的值。** 以服务端字段绑定滑杆并在拖动中收到不同值时，`input()` 会删除对应草稿和未提交标记；随后的控件更新直接设置权威值。指针尚未放开时滑块可能跳动，随后释放可能提交用户并未选中的值。建议保留拖动起点和当前指针草稿，缓冲外部更新，在释放／取消后明确解决冲突，不要静默覆盖。[权威值覆盖](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageView.java#L515) · [滑杆预览与提交](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageInputs.java#L61)

## P2：阻断流程、焦点和反馈

5. **同步超时直接关闭页面。** 数据版本不匹配时客户端请求快照并重试一次，8 秒内仍未同步就调用 `page.close()`；当前页面没有显示可理解的失败原因或让用户决定保留／重试。若页内有草稿，也与问题 2 叠加。建议停留在可读的失败状态，提供重试、返回及安全的草稿处理。[同步重试与关闭](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPageNetwork.java#L210)

6. **等待动作回执会清除键盘焦点。** 进入 `PENDING` 后带动作的控件被禁用；`refresh()` 将失效控件的焦点清空。成功、失败或超时后没有按节点恢复焦点。若确认挑战在焦点清空之后到达，保存确认前焦点也可能得到空值。建议保留操作来源的稳定节点 ID，结束等待或关闭确认后恢复到可用控件。[禁用与清焦点](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageView.java#L384) · [确认前保存焦点](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPages.java#L651)

7. **动作失败原因被压成统一文案，本地错误也可能误称服务器拒绝。** 远程结果的错误码除了版本不匹配外没有传给页面显示，界面只显示“服务器拒绝了此操作”；本地动作回调抛异常也走同一 `FAILED` 状态。用户无法区分权限、输入、繁忙等原因或知道下一步。建议保留可安全展示的错误类别、本地化原因和对应操作；结果未知的超时不能简单呈现为失败或直接重复提交。[结果接收](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPageNetwork.java#L343) · [本地异常](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPages.java#L550) · [状态绘制](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageView.java#L365)

8. **列表筛选后可能落在结果底部，空结果是空白。** 已滚至列表底部时输入筛选词，列表只把旧偏移夹紧到新最大值；例如现有测试明确断言从 440 夹至 50，而不是显示第一条匹配项。清除筛选也不会恢复原来的未筛选位置。零条结果时只绘制底板，没有“无匹配”或清除筛选入口。建议筛选变化后显示结果起点，同时保留清除筛选前的位置，并区分空数据和无匹配。[列表更新](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageList.java#L44) · [空列表绘制](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageList.java#L112) · [现有测试](../src/test/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageControlsTest.java#L15)

9. **声明式页面的滚动条按下时会跳，拖动没有原位取消。** 列表和页面滚动条都在按下时直接把滑块中心对齐指针；按住滑块边缘会先跳一段。两者也没有保存按下前的位置供取消恢复。建议复用已实现抓取偏移和取消的 `VerticalScroll`／`UiVerticalScrollBar`，并验证按下、拖动、释放、Escape 和失焦。[列表滚动条](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageList.java#L76) · [页面滚动条](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageView.java#L708) · [已有滚动模型](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/layout/VerticalScroll.java#L36)

10. **下拉选项混用滚轮和键盘时，焦点与可见位置脱节。** 滚轮只修改首个可见行，不更新键盘 `cursor`；随后按方向键会从原来的选项重新定位，列表可能跳回去，Enter 也会提交原本的不可见选项。另外，点击弹层外会继续交给底层控件，选项弹层本身不会因该点击收起。`UiLocalModal` 支持局部非阻塞交互，这里应针对下拉菜单定义关闭和焦点策略，不应改变所有局部浮层的语义。[下拉选项输入](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/NativePageOptions.java#L130) · [局部浮层外点击](../src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiLocalModal.java#L206)

11. **富文本属性错误没有字段级定位。** 宽高、颜色、纹理／字体 ID 等在应用时可能解析失败，但异常统一变成“属性无效，或已达到文档大小限制”，没有说明是哪个字段或正确格式，也不聚焦错误输入。切换标签会先尝试应用，因此无效输入还会阻止切换。建议保留草稿，按字段返回本地化原因，并在用户尝试应用时定位首个错误。[属性解析](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/RichTextEditorProperties.java#L70) · [应用与错误](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/RichTextEditorScreen.java#L343)

12. **自动消失的通知没有内建的事后查看入口。** 通知默认 4 秒，显式时长为 0.6～30 秒；到期后从运行时活动集合移除。现有活动面板显示活动与预览，不是通知历史。重要反馈若只通过通知发布，玩家错过后无法从这条默认界面路径找回。建议提供有界、可关闭的历史，或将关键结果挂在可持续查看的活动／业务页面，并保留聊天等消费方回退。[通知时长契约](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/live/NotificationService.java#L30) · [到期与移除](../src/kernel/java/com/dishanhai/sh_better_ui/kernel/internal/LiveSurfaceRuntime.java#L208) · [活动面板](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/LiveActivityScreen.java#L60)

13. **通用子页默认返回焦点依赖旧控件实例。** `open()` 保存父页面当前 widget；返回时父页若在 `init` 重建控件，旧实例已不在 `children()`，焦点不会恢复。API 已提供 `returnFocus(Supplier)` 供消费方解决，现有部分调用也显式使用了它；问题是默认路径不会自动跨重建恢复。建议在公开入口强调稳定 ID／供应器，并为常用导航提供可靠的恢复机制。[保存与恢复](../src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreen.java#L157) · [现有供应器](../src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreenBuilder.java#L49)

## 清单适用性与已经存在的保护

- 用户清单中的 2、4、8、9、13、19、21、22、24、26 及拖动取消，分别对应上述具体发现或风险。不能把每一项清单直接当成所有页面均存在的缺陷。
- 主控面板和富文本编辑器已有未保存离开确认；高风险远程动作的确认提示包含对象、数量与后果，普通待处理动作也有状态文字和防连点。以上不消除声明式草稿、超时回执及焦点问题。[主控确认](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/ControlPanelScreen.java#L1095) · [编辑器确认](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/RichTextEditorScreen.java#L557) · [远程确认](../src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientPages.java#L651)
- 密码／银行卡、分页重置、多选批量撤销等不是本次声明式组件中已确认的具体场景；不据此声称整个项目不存在。当前列表筛选发生在客户端本地，不能直接套用“每字请求服务器”的结论。
- 当前原生 GUI 坐标不是浏览器 CSS 像素；44 × 44 px、系统边缘手势和固定移动端安全区不能不加换算地套用。触屏、不同 GUI Scale、高对比、旁白、键盘、动画及音频行为均需针对实际支持的平台验收；本次未证明这些项全部合格。

## 本次验证与后续复现

- Forge 1.20.1 和 NeoForge 1.21.1 分别运行 `UiScreenTest`、`UiLocalModalTest`、`NativePageControlsTest`：每端 31 项通过。
- 两端分别运行 `UiPageSchemaTest` 和 `UiServerConnectionTest`：每端 24 项通过。这些既有测试证明现有契约，并未覆盖本报告的全部缺陷；其中列表测试目前固化了问题 8 的偏移行为。
- Forge 运行过一次临时 JUnit 叠层命中探针，确认问题 3；探针源码和专属编译产物已清理，尚需在修复时加入持久回归测试。
- 未启动 Minecraft 实例，未进行真实客户端、多玩家、弱网、GUI Scale、旁白或触屏验收。修复时应先覆盖结果未知与草稿，再覆盖命中、拖动、焦点及提示，并分别在实际适配层复测。

## NeoForge 26.1.2／26.2 原生交互复查

两版的大部分控件、图库和 `UiScreen` 共用 26.1.2 源码；26.2 通过自己的 `NativePort` 等文件适配平台。此处的发现针对两版当前源码，不表示旧版声明式页面的每个问题也存在于 26.x。`ClientUiKernel` 当前没有注册旧版 `ClientPages`、原生活动通知和相应网络运行时；其未实现部分应继续按迁移待办处理，而非当成这些页面已经发生的交互缺陷。[26.2 源码集](../neoforge-26.2/build.gradle#L11) · [26.x 服务注册](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientUiKernel.java#L65)

### P1：可能误操作或丢失输入

1. **通用 `UiScreen` 的 Escape 会先离开页面，控件无法先取消正在进行的编辑。** 两版实际 NeoForge 补丁源码中的 `Screen.keyPressed` 先处理 Escape 并调用 `onClose()`，之后才把其他键交给焦点控件。26.x `UiScreen` 没有覆盖此顺序，且直接返回父页；它是 `final`，Builder 也没有离开前的拦截／确认回调。消费方通过该 Builder 挂载草稿或配置了取消回调的 `UiSlider` 时，Escape 默认不会先触发滑杆的 Escape 取消，也没有内建的未保存确认。`onRemoved` 是移除通知，不能充当离开前的确认。建议先让活动手势消费取消，再由宿主按草稿状态选择继续编辑或放弃；保留消费方明确指定的导航语义。主控面板和图库样例各自覆盖了部分 Escape 处理，不能将此结论扩展为它们都没有取消保护。[通用页面](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreen.java#L41) · [Builder](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreenBuilder.java#L22) · [滑杆取消](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiSlider.java#L153) · [主控面板保护](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/ControlPanelScreen.java#L505)

2. **图库中两个可保存的预览编辑器返回时会丢弃未应用草稿。** `effect_appearance` 与 `dynamic_theme` 都有独立的已保存值、草稿及应用／取消按钮；更改后按返回或 Escape，继承的 `GallerySampleScreen.onClose()` 直接回到图库。再次打开时会新建样例并从已保存属性读取，未应用更改丢失。动态主题使用隔离管理器，因此这里说的是图库预览设置，而非全局主题已被保存。建议在真正离开前提供继续编辑／放弃，缩放和打开内部子页时保留草稿。[外观草稿](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/GalleryAppearanceSamples.java#L80) · [动态主题草稿](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/GalleryDynamicThemeSample.java#L66) · [返回路径](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/GallerySampleScreen.java#L214) · [重新创建](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/DeveloperGalleryScreen.java#L649)

3. **主控面板的“撤销重置”会一起覆盖重置之后的新编辑。** 重置时保存整份旧草稿，之后修改任意设置不会清除或更新 `beforeReset`；再次点击“撤销重置”直接用旧草稿替换当前草稿。例如重置后再打开减少动效，撤销重置也会丢掉这次新选择。按钮提示说明会恢复重置前草稿，但没有提示之后的新编辑也会丢失。建议将撤销限制为仍可安全回滚的状态，或提供可恢复的新编辑历史及明确提示。[编辑和撤销](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/ControlPanelScreen.java#L464)

4. **通用 Screen 的重叠控件按先添加者命中，可能点到视觉下层。** `UiScreen.add()` 直接注册原生控件；两版补丁后的 `Screen` 按注册顺序提取可绘制项，而 `ContainerEventHandler.getChildAt()` 按相同顺序选择第一个命中项。若消费方将后添加的交互控件绘制在前者之上，点击重叠区仍会交给先添加的控件。临时探针使用原生事件容器和两个测试监听器，确认两版都命中先添加者；实际视觉层次和具体页面尚未在客户端验证。建议统一绘制与命中层次，或明确禁止／隔离重叠交互区，并覆盖弹层关闭及下层误触。[26.x 注册入口](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreen.java#L52)

### P2：取消、发现性和焦点

5. **默认滑杆消费 Escape，却不一定恢复按下前的值。** `UiSlider` 只有设置 `setOnCommit(commit, cancel)` 后才保存按下前的值；未设置时，拖动已通过 `onChange` 更新消费方，Escape 仍返回已处理但不会回滚。图库提供的可复制滑杆示例只设置 `onChange`，因此可能让消费方误以为已具备可取消拖动。两版临时探针均复现；内置主控面板等调用显式配置了取消回调，不属于这个默认调用场景。建议默认提供一致的回滚语义，或让 API 和示例清楚区分即时提交与可取消草稿。[滑杆](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiSlider.java#L100) · [示例](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/GalleryExamples.java#L50)

6. **普通按钮在按下时已执行动作，拖出后放开不能取消。** `UiButton.onClick()` 调用原生 `AbstractButton.onClick()`，后者立即调用 `onPress`；`onRelease()` 只清除按住状态。应用、重置及消费方普通按钮因而在按下时执行。导航或即时动作可能有意沿用 Minecraft 原生语义，但对资源消耗、删除等有后果的消费方动作，这不提供按住后移出取消的机会。建议明确这一契约，为需要取消的动作提供释放时提交的控件，并继续为重大后果提供具体确认。[按钮输入](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiButton.java#L197)

7. **通用 `UiScreen` 返回后默认不会恢复父页原控件焦点。** 当父页也是 `UiScreen` 时，从子页返回会触发父页的 `repositionElements()`，它重建控件；原生 `rebuildWidgets()` 会清除焦点并重新设置初始焦点。Builder 未提供稳定的返回焦点供应器，默认路径不会记住原来的控件。独立开发者面板／图库有各自的恢复逻辑，此结论不覆盖它们。建议为通用导航提供稳定 ID 或供应器，并验证父页重排和隐藏控件后的回退。[页面重建](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreen.java#L47) · [图库已有返回焦点入口](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/SettingsApi.java#L42)

8. **下划线标签在窄宽或长译文下会静默截断名称。** 空间不足时标签均分宽度，绘制时按宽度截取文字；控件没有为截断标签提供省略提示或悬停完整名称。旁白可读取当前选中的完整名称，但用户可能需要先选中才知道该标签代表什么。建议提供完整名称提示或可滚动布局，并在长译文及较大 GUI Scale 下验证。[标签布局与绘制](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiUnderlinedTabs.java#L50)

### 本轮验证与边界

- 两版分别使用 JDK 25 离线运行 `UiSliderInputTest`、`UiScrollableSectionBarInputTest`、`ControlPanelSettingsTest`，各 10 项通过；加入两项临时行为探针时，各 12 项通过。探针确认默认滑杆取消行为和原生事件容器的先添加命中顺序，不等于真实屏幕的绘制和点击验收。临时探针源码已移除。
- 同时检查了两版 `minecraft-patched-*-sources.jar` 中的 `Screen`、`ContainerEventHandler` 和原生按钮输入路径。普通单元测试没有 Minecraft 单例，不能据此声称完整 Screen 交互已在客户端复现。
- 本轮未启动游戏、部署产物或运行多人／触屏测试，也没有修改实现。仍需分别在 26.1.2、26.2 客户端复现草稿离开、重置后编辑、叠层、按下／拖出／放开／取消、返回焦点、长译文和 GUI Scale；第三方预览及未来声明式页面也需在实际消费方复验。
