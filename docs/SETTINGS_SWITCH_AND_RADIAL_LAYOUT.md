<!-- Author: dishanhai -->
# 设置行开关与六位轮盘布局

`UiSwitchRow` 是 UUK 的原生设置行：主名称、说明、右侧滑块开关共用一个键鼠焦点与命中区域。主题色来自 `UiTheme`，动效沿用 `InterruptibleAnimation`，减少动效和高对比沿用 `UiPreferences`。文本用 `UiHudRichText.prepare` 后测量及绘制，不附带 Apple 字体或素材。

```java
var row = new UiSwitchRow(bounds, title, description, confirmedValue, preferences, requested -> send(requested));
// 向原生 Screen 注册 row；网络确认后静默同步。
row.setValue(confirmedValue);
row.setPending(waitingForConfirmation);
// 等待、失败或重试的本地化说明由宿主管理。
row.setDescription(description);
```

按下立即显示反馈，放开且仍在行内才提交；拖出、Esc、失焦和宿主取消均不提交。宿主应在鼠标释放时转交 `releasePointer(x, y, button)`，在页面失焦、关闭或重建时调用 `cancelInteraction()`。`setValue` 不触发业务回调，等待期间不重复发出请求。普通键盘激活、原生焦点、完整说明 tooltip 及旁白均保留。

- `kernel.api.controls.SwitchRowState`：与绘制和网络无关的确认值、等待状态与取消手势。
- `kernel.api.layout.SettingsPanelLayout`：分组设置页的侧栏／顶栏、正文行和页脚位置，最小视口 180×180。
- `kernel.api.layout.RadialMenuLayout`：六个稳定的环绕操作位，保留中央文字空间；特别窄或矮时变为两列。每页最多六项，分页与业务动作归宿主。

`SettingsControlsTest` 使用实际布局验证多组 240～1920 像素 GUI 视口、轮盘操作位和中央区域互不相交，以及取消／等待期间的交互边界。它可独立在 Java 17 执行；NeoForge 26 的兼容任务也会运行它。

Forge 1.20.1 与 NeoForge 1.21.1 使用正常完整构建；Forge 须使用已重混淆的发行包安装游戏，开发编译使用 dev JAR。NeoForge 26 的完整源码仍有原有迁移待办，因此本次新增独立任务：

```bash
gradle -p neoforge-26.1.2 -I ../gradle/artisan-controls-compat.init.gradle \
  artisanControlsCompatibilityJar --no-daemon \
  -PartisanControlsBaseline=/absolute/path/to/verified-shbui-world-overlay.jar
```

使用项目可用的 wrapper 执行上述任务，JDK 为 25。任务只允许 26.1.2，核对基线 Loader、Minecraft 版本及签名，独立编译设置 API、运行契约并生成 `build/artisan-controls-compat/*-artisan-controls.jar`。除新增的四组类及构建来源记录外，其余基线条目和 manifest 会逐字节核对。基线仍需包含既有世界覆盖 API，不能将 SHCLIB 内容打包进 SHBUI。

实际 GPU、字体缩放、旁白及联机反馈另做客户端验收，不能由几何契约替代。
