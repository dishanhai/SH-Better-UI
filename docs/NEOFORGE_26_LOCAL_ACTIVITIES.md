<!-- 作者：山海／dishanhai -->
# NeoForge 26.x 本地实时活动首批宿主

## 已接入的路径

`ClientLiveActivities` 复用共享 `LiveSurfaceRuntime`，将 `ActivityService`、`NotificationService` 和 `LiveSurfaceService` 暴露为同一运行时，避免通知和活动各占一个 HUD。不是另建绕过 UUK 的任务列表。

- 真实发布的活动经过既有 namespace/scope 所有权校验；CRITICAL 仍需要内核授予权限。
- 每个内核 MEASURE 阶段推进仲裁、暂停/恢复、超时和观察者。隐藏 HUD、菜单打开和拖动详情均不暂停生命周期时钟。
- 游戏内 HUD 显示当前主活动的标题、详情、可选本地资源图标和进度；使用公开保留区域的避让算法，窄窗口装不下时不溢出。
- 打开详情页显示当前真实活动，不发布演示数据。详情支持原生滚动、键盘焦点、最多四个声明控制、可选 seek 进度条和隐藏操作。动作交回已有内核调度器，不在渲染回调执行消费方代码。
- 页面移除取消未提交的拖动。活动关闭/过期/重新发布后，旧控件凭快照的 instance/revision 被运行时拒绝。
- 隐藏普通活动仅撤下展示，所有者可 `showPresentation()` 恢复；隐藏通知沿用通知的终结语义。

## 集成点

构造 `ClientLiveActivities(kernel, liveSettings)`，将三个 service accessor 注册到相应 capability。

- `advance()`：内核 `UIPhase.MEASURE`。
- `drawHud(GuiGraphicsExtractor)`：HUD 提取阶段。
- `details(parent)`：返回真实详情 `Screen`，供设置/工具入口打开。
- `close()`：内核停止之前调用；然后由 KernelHost 关闭所有 owner scope。
- `settings(next)`：加载新的实时活动设置时更新仲裁策略。

## 明确范围

这是本地服务首批宿主，不含网络远程活动、服务端通信、全局手柄/Alt 手势、双活动 split 画面、空间阴影、弹簧过渡或全局拖拽停靠。为避免声明双卡片却漏画，运行时 split 策略显式关闭，后续活动仍由共同仲裁器排队轮换。详情页之外不接管游戏鼠标。

目前 HUD 采用紧凑尺寸与单张静态圆角卡片，高对比优先，支持内容自带调色板。不声称已完全对齐旧版 LiveSurfacePainter 的尺寸/主题动画和布局障碍识别（bossbar/计分板尚未登记）；显式 `LiveSurfaceService.reserve` 保留区已避让。

验证以本轮真实构建和实机结果为准。新增 `NativeLiveActivityTest` 检查原生宿主共用运行时、权限拒绝、scope 关闭、超时、旧实例动作拒绝及隐藏恢复。截图与硬件输入验收未由本文声称完成。
