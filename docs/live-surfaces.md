<!-- 作者：山海／dishanhai -->
# Adaptive Live Surface Engine 接入指南

Forge 1.20.1 和 NeoForge 1.21.1 的客户端都从 `UnifiedUI.get()` 取得同一个内核。第三方发布时使用 `UIModule.start` 提供的 `ModuleContext`。`ActivityService.KEY`、`NotificationService.KEY` 和 `LiveSurfaceService.KEY` 的公共契约版本都是 1。可选集成用 `context.engine().find(key)`，模块有硬依赖时声明对应的 `CAPABILITY`。消费模组分别编译对应 Loader 的依赖包。 [公开 API](public-api.md) · [模块开发](module-development.md)

## 发布实时活动

```java
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.*;
import java.time.Duration;
import java.util.List;

final class MachineActivity {
    static Handle start(ModuleContext module, ScopeHandle owner, Runnable openRecipe) {
        var id = UiId.parse("example:machine/42");
        return module.engine().require(ActivityService.KEY).publish(module, owner,
                new Request(id, content(0), Priority.NORMAL, Duration.ofMinutes(5), true,
                        action -> openRecipe.run()));
    }

    static Content content(double progress) {
        return new Content(Text.key("example.machine.running"), Text.key("example.machine.recipe"),
                null, progress, false, List.of(new Control(UiId.parse("example:open_recipe"),
                        Text.key("example.machine.open_recipe"))), List.of());
    }
}
```

在 UI 线程发布、暂停、恢复、完成、取消和观察。后台可以读 `handle.snapshot()` 或调用 `handle.update(MachineActivity.content(progress))`。每个句柄保留一个待处理完整内容，中央状态阶段提交最新值。普通单活动更新间隔至少 16 ms，同值省略修订。仅进度或律动变化标记绘制，标题、控件、尺寸令牌或内容结构变化也标记布局与命中。多活动可用 `updateAll(module, scope, Map.of(a, nextA, b, nextB))` 提交同一作用域的批次。中央任务先验证全部成员再提交，观察者读取该批次完成后的快照。

同一个 ID 同时只有一条发布，复用 ID 会取得新的实例和句柄。活动的发布 ID、空间保留 ID 和通知去重键都使用模组 namespace。无授权的 `CRITICAL`、跨作用域访问和伪造句柄会被拒绝。队列满、作用域关闭或句柄终结时，提交会抛出明确异常。引用旧句柄仍可查询最终快照。 [权限](permissions.md) · [调度](scheduler.md)

- 用模组作用域绑定本地长期活动。用 `ScreenService.session(module, id)` 绑定真实连接。用挂载的 Screen 树绑定随画面退出的活动。范围关闭会终止这些发布、待提交内容和订阅。
- 用 `handle.observe(listener)` 观察以后的修订和终结状态。订阅可关闭，失效作用域不会再交付回调。后台生产者收到关闭异常后可以停止向该句柄提交。
- `dismissPresentation()` 只改变展示资格。任务仍可继续更新，调用 `showPresentation()` 可以恢复。 `requestPresentation(FLOATING)` 等提示仍受可用空间、当前 Screen 和用户操作约束。
- `Content.withAppearance(tokens)` 可为自己的活动或通知提供颜色、前景和阴影色、边距、间距、尺寸、圆角和高度。用户高对比设置优先。图标使用完整纹理 ID，例如 `example:textures/gui/machine.png`。文本可以引用语言键，通知和活动共用内容模型。

## 通知、仲裁与输入

```java
var notices = module.engine().require(NotificationService.KEY);
var notice = notices.publish(module, scope, new NotificationService.Request(
        UiId.parse("example:machine_done"), Content.label(Text.key("example.machine.done")),
        Priority.HIGH, Duration.ofSeconds(3), UiId.parse("example:machine_done"),
        NotificationService.Merge.COUNT, null));
```

去重只匹配同一作用域世代的键。 `KEEP` 保留原内容，`REPLACE` 更新内容、回调和优先级，`COUNT` 增加可见计数。默认每个模块每秒最多接收 8 条新通知，已存在的去重发布可以继续合并。展示时钟在通知实际绘制到可见后开始，等待超过一分钟的通知会清理。用户可启用静默模式或覆盖指定 ID 的普通优先级。

内核综合优先级、通知紧急度、有限的更新时间加权、连续等待时间、用户固定和交互保护来选出主活动、次活动和等待队列。普通抢占保持最短展示时间。 `LiveSurfaceService.view()` 同时公开仲裁计划、当前目标、上一收敛状态、过渡标志和实际绘制几何。等待区和已隐藏活动继续接收业务状态。

游戏内按 F7 打开可交互的实时活动 Screen。HUD 显示活动时，单按并松开左 Alt 或右 Alt 可打开此 Screen 并展开当前主活动。在活动 Screen 里单按 Alt 也能展开主活动。按 Alt+~（反引号键）直接进入鼠标模式，点击或拖动活动岛，按 Esc 收合后返回。紧凑和最小活动的快捷键提示在可展示时长过半时收回，快捷键仍可使用。常驻活动按默认通知时长的一半收回提示。减少或关闭动效时，提示到点直接隐藏。

也可在可用的原生 Screen 中操作表面。点击或长按展开，双击或 P 固定，右键、Delete 或横向拖动关闭展示。Tab 和方向键切换焦点，Enter 或 Space 执行。进度条启用 seek 时，拖动预览跟随指针，放开才提交归一化值。活动 Screen 支持 GLFW 已映射手柄的方向键、确认、返回、关闭和固定。指针捕获由内核租约管理，失焦、切屏、模态和作用域关闭会撤销捕获。

开发者面板的「控件」图库可找到「活动岛」（`sh_better_ui:live_activity`）。F7 面板当前没有活动时也可打开示例。示例通过 `ActivityService` 发布一条仅随预览画面存活的活动，可展开、拖动进度和收合。网页材质实验室可搜索「活动岛」，查看紧凑和展开两种形态的 WebGL 示意。

## 避让与渲染

```java
var surfaces = module.engine().require(LiveSurfaceService.KEY);
var reservation = surfaces.reserve(module, scope, UiId.parse("example:recipe_hud"),
        new SurfacePlacement.Rect(8, 6, 96, 24));
reservation.update(new SurfacePlacement.Rect(8, 6, 112, 24));
// 该区块关闭时可以提前释放，关闭 scope 也会释放。
reservation.close();
```

坐标使用缩放后的 Minecraft GUI 单位。宽度和高度取自 `UIPlatform.viewport()`，引擎在提交原生绘制时使用真实 GUI Scale。布局会保护窗口边距、Boss Bar、聊天、快捷栏和经验区域、侧栏计分板、容器和可见原生控件。计分板和聊天使用保守边界。JEI、REI、EMI 与自绘 HUD 可由拥有这些区域的模组显式预约。求解器在宽度不足时尝试缩小或改用紧凑及最小形态，再尝试移位。

内核使用现有 Spatial SDF 场景绘制表面、圆角和阴影。内容在实际动画轮廓内绘制，鼠标只命中最后绘制成功的形状和控件。减少动效时使用较少几何变化，关闭动效时直接提交几何。连续超出绘制预算时暂时关闭装饰性阴影并简化动效。Shader 不可用时使用 Spatial 的基础绘制，关闭 Spatial 场景时使用基础圆角。 [Spatial 验证](SPATIAL_VERIFICATION.md)

## 可选服务端同步

`ServerLiveActivities` 是带 `ServerPlayer` 的原生适配 API。服务端在自己的游戏线程调用 `publish`、`remove` 或 `snapshot`，对每个玩家独立同步。先用 `supported(player)` 判断对端能否接收。普通本地活动只依赖客户端三个公开服务。

```java
var data = new NetworkLiveActivity(UiId.parse("example:server_machine"),
        "Machine processing", "Batch 42", NetworkLiveActivity.Status.RUNNING,
        0.5f, new NetworkLiveActivity.ActionOffer(UiId.parse("example:inspect"), "Inspect"));
var ownerId = player.getUUID();
ServerLiveActivities.publish(player, data, new ServerLiveActivityAction<>(
        sender -> sender.getUUID().equals(ownerId) && sender.hasPermissions(2),
        sender -> sender.sendSystemMessage(Component.literal("Batch 42 is running"))));
```

示例回调同时核对请求者和当前权限。实际游戏操作的业务验证由服务端回调执行。信道只传有界纯文本、状态、进度和可选操作 ID。协议核对连接、会话、序号、修订、请求 ID 以及实际发起操作的玩家。首条消息是快照，后续使用创建、更新或移除增量，快照可修复序号缺口。服务器操作的结果会回到同一会话。会话关闭清除客户端镜像和服务端操作绑定。Forge 使用可选 SimpleChannel，NeoForge 使用可选 PLAY payload。

## 配置

首次启动生成 `config/sh_better_ui-live.toml`。 `kernel.enabled` 影响内核安装，改动后重启客户端。其他选项在成功应用资源重载时读入，也会镜像到 `UIApis.LIVE_SURFACE_SETTINGS`。现有的客户端无障碍和动态主题偏好仍生效。配置版本为 1，缺失版本或版本 0 会在内存中读取旧格式，用户文件保留。

| 配置 | 生效范围 |
| --- | --- |
| `kernel.enabled`、`diagnostics`、`strictValidation` | 内核安装、Spatial 调试采样和配置诊断。无效字段使用默认值。 |
| `island.enabled`、`anchor`、`scale`、`maxActivities` | 展示、首选锚点、0.75–1.5 的相对尺寸与活动容量。 |
| `island.compact.enabled`、`expanded.enabled`、`split.enabled` | 允许的表面形态。 |
| `island.motion.enabled`、`reducedMotion`、`spring.response`、`spring.damping` | 动效偏好和物理参数。 |
| `island.theme.mode`、`background`、`accent` | 深色、内核主题或自定义 ARGB 配色。 |
| `island.shadow.enabled`、`quality` | 表面阴影与质量。全局 Spatial 设置仍可降级。 |
| `island.notifications.enabled`、`deduplication`、`defaultDuration`、`maxNotifications`、`quiet` | 通知策略，时间单位毫秒。 |
| `island.input.longPressThreshold`、`doubleClickThreshold`、`dragThreshold` | 手势阈值，时间单位毫秒，距离使用 GUI 单位。 |
| `island.performance.adaptive`、`frameBudget` | 装饰性绘制预算，单位毫秒。 |
| `[activities.priority]` | 以带引号的活动 ID 覆盖普通优先级。 |

服务端的访问规则和动作绑定由发布模组设置。压测及开发用启动参数见基准和验证记录。 [五个外部示例](../examples/kernel-extension/README.md) · [性能基准](live-surface-benchmark.md) · [验证记录](live-surface-verification.md)
