<!-- Author: dishanhai -->
# 自定义主题

Forge 1.20.1、NeoForge 1.21.1：进入「主控面板 → 配置 → 显示偏好」，选择「自定义」。

- **主题配色**：选择已注册主题（包括 Muonium 透明主题）作为基底，修改背景、面板、边框、文字、强调色。点击颜色名称/色样打开可视化 RGB 子页，也可继续输入 `#RRGGBB`；留空继承当前主题。非法颜色会禁用完成按钮。
- **选择背景**：直接选择纯色、图片及已注册动态背景。选择立即预览，不必通过修改参数激活背景；支持没有参数表单的背景。
- **配置当前背景**：编辑已选背景的参数。图片背景的「图片路径」接受本地 PNG/JPEG 文件路径，或资源包 ID，例如 `my_pack:textures/background.png`。
- **控件与动效**：分页选择玻璃、控件材质、反馈、标签栏及过渡参数，沿用现有效果编辑器。

本地图片可以使用绝对路径，例如 `C:\Pictures\background.png`，或游戏目录下的相对路径，例如 `config/sh_better_ui/backgrounds/background.jpg`。支持覆盖裁切、完整包含和拉伸。图片上限为 32 MiB、单边 8192、总像素 16777216；后台解码，加载中和失败分别提示。修改图片后使用 F3+T 重载。

子页「完成并存入草稿」仅更新主控面板的草稿，点击主控面板「应用」才保存。取消丢弃本次子页修改；Esc 离开有修改的子页会询问是否放弃。恢复默认可撤销。关闭、切换图片及资源重载会释放旧纹理与请求。

## UUK API

```java
var appearance = UnifiedUI.get().require(UIApis.APPEARANCE);
var editor = appearance.editTheme(parent, settings,
        AppearanceService.ThemeSection.BACKGROUNDS,
        theme -> updateHostDraft(settings.withTheme(theme)));
```

`ThemeSection` 提供 `COLORS`、`BACKGROUNDS`、`EFFECTS`。调用方负责展示返回的 Screen，并持有和保存自己的草稿。新背景通过现有 `AppearanceService.backgrounds()` 注册，选择页自动显示；图片参数使用 `BackgroundImage.ID` 对应的效果组，沿用 `ThemeOverrides` 持久化，不添加独立配置文件。

NeoForge 26.x 的独立精简面板尚未移植这一外观服务和图片背景，跟踪于根目录 `TODO.md`。

## 验证入口

```bash
./gradlew.bat --no-pause build --offline --no-daemon --console=plain
./neoforge-1.21.1/gradlew.bat -p neoforge-1.21.1 build --offline --no-daemon --console=plain
./gradlew.bat --no-pause -I gradle/custom-theme-smoke.init.gradle runClient --offline --no-daemon --console=plain
```

客户端夹具使用独立的 `build/custom-theme-smoke/client`，不修改消费实例。截图、断言结果位于 `build/custom-theme-smoke/screenshots`。客户端测试后重新执行正常构建，保证发布 JAR 不包含测试夹具。

2026-11-16：Forge 隔离客户端通过配色键盘输入、图片参数编辑、屏幕左右像素颜色、子页完成与主面板应用、取消不改草稿、背景与动效分页、实际 GUI 缩放 3，以及资源重载后恢复显示的检查。截图已人工查看；测试客户端自动退出。NeoForge 1.21.1 已通过完整构建，尚未进行该页客户端交互验收。

## 注册主题与可视化配色

主题选择来自实际注册表，不修改内建或资源包定义。每个主题的五项颜色覆盖独立保存；切回旧主题恢复其覆盖，恢复继承不应覆盖其他主题。注册主题的透明度、模糊和材质标记仍由主题定义继承，RGB 编辑只改颜色，不把玻璃变为不透明面板。资源包主题暂时缺失时使用安全基底，但保留其选择与覆盖以便重新加载。

颜色子页的拖动/键盘修改只预览；确认写回外层草稿，取消不回写。单项「继承」保留空值语义。子页展示面板、文字和强调色的实际主题示例；空间较小时保留十六进制输入与确认/取消。外层完成及主控面板应用仍是独立保存边界。

目前按主题独立保存的是五项配色覆盖，不是任意 token/样式规则的通用编辑器；源主题的材质与样式继承不被改写。背景、HUD 和效果覆写继续沿用现有配置作用域。运行时派生主题不出现在用户选择列表中。

自定义注册主题即使具有 HUD 玻璃材质，也使用本页「选择背景」配置；独立的「HUD 玻璃」模式仍使用其专用 HUD 背景设置。两者字段不会因材质切换互相覆盖。
