<!-- 作者：山海／dishanhai -->
# KubeJS 扩展支持

SH-Better-UI 在 `kubejs/client_scripts/` 中提供全局对象 `SHUI`。它与 Java 的 `ShBetterUiApi.client()` 共用通用分组、界面构建器和协作上下文。KubeJS 是可选依赖，由调用方在客户端安装对应版本。

| 平台 | 编译适配基线 |
| --- | --- |
| Forge 1.20.1 | KubeJS `2001.6.5-build.26`，Rhino `2001.2.3-build.10` |
| NeoForge 1.21.1 | KubeJS `2101.7.2-build.381`，Rhino `2101.2.7-build.81` |

插件通过 jar 根目录的 `kubejs.plugins.txt` 发现，入口带 `client` 标记，仅向客户端脚本绑定 `SHUI`。两端保留各自的 KubeJS 插件实现，共用 UI 代码。KubeJS、Rhino 均作为编译期依赖，发布 jar 只包含本库实现。

## 创建界面

把以下内容放入 `kubejs/client_scripts/shui_demo.js`，从已有的客户端按键或事件回调调用 `global.openShuiDemo()`。示例复用 Minecraft 自带的翻译键，可直接替换为你的本地化键。

```javascript
global.openShuiDemo = () => {
  SHUI.screen('gui.done')
    .onInit(screen => {
      const area = screen.viewport()
      screen.button(area.x() + 12, area.y() + 12, 100, 20,
        'gui.done', () => screen.onClose())
    })
    .open()
}
```

默认返回顶栏提供返回按钮及 Esc 操作，关闭后回到打开前的父页。原生按钮支持键盘与旁白。`screen.viewport()` 返回 GUI 逻辑单位的可用区域，窗口或 GUI 比例改变时 `onInit` 重新布局。父页会重建控件时，用 `returnFocus(() => currentButton)` 提供新的返回焦点。`parent(null)` 指定返回游戏，`pausesGame(true)` 可暂停单人世界。

`SHUI.text('your_mod.ui.title', argument)` 创建带参数的可翻译 `Component`。自定义语言键由你的资源包或 `kubejs/assets/<namespace>/lang/zh_cn.json`、`en_us.json` 提供。

## 新 API 接入与合作

通过 `SHUI.registerExtension(id, factory)` 注册脚本扩展。工厂接收 `UiApiContext`，可访问既有分组，也可通过 `api.extension(id)` 取得其他扩展。每个上下文会缓存自己的扩展实例。

```javascript
SHUI.registerExtension('kubejs:wave_background', api => {
  return api.motion().patternWaves()
})

global.openShuiWaves = () => {
  SHUI.screen('gui.done')
    .onRender((screen, frame) => {
      const waves = screen.context().extension('kubejs:wave_background')
      waves.draw(frame.graphics(), frame.viewport(),
        frame.mouse(), frame.preferences())
    })
    .open()
}
```

本例将动效、鼠标快照、布局及显示配置组合在一起。`onRender` 与 `onForeground` 共用本帧的鼠标、时间和配置。自定义 `onRender` 负责内容区域的背景，所有内容均裁切在 `frame.viewport()` 内。

扩展也可以返回脚本对象，将多个独立 API 组合为自己的业务能力。工厂中的 `api.extension('other_mod:dependency')` 按需解析依赖，循环依赖会报出路径。ID 必须包含命名空间，同一时间的重复注册会报错。工厂失败后，下次访问会重新尝试。

## 被动富文本预览

从客户端按键或事件回调调用 `global.openRichText()`。此示例沿用 Minecraft 的语言键和钻石贴图。预览复用原生富文本界面，可滚动并返回原页面。

```javascript
global.openRichText = () => {
  const rich = SHUI.richText()
  const document = rich.markup('kubejs:diamond_preview',
    '[tr=item.minecraft.diamond;Diamond] [img=minecraft:textures/item/diamond.png;16;16;]')
  rich.open(document)
}
```

`rich.encode(document)` 导出被动 JSON，`rich.json(json)` 读取同一格式。脚本门面采用默认被动解析策略，接受文字、样式、翻译和本地 PNG。加载器仍沿用上面的可选插件发现方式。Java 扩充规则见 [富文字擴充](RICH_TEXT_EXTENSION.md)。

## 常用入口

| 调用 | 用途 |
| --- | --- |
| `SHUI.settings()` | 配置快照与内置面板 |
| `SHUI.controls()` | 按钮、滑块、开关、状态及进度 |
| `SHUI.interactions()` | 鼠标、手势、覆层、刷新和轻提示 |
| `SHUI.motion()` | 时间轴、弹簧与独立动效 |
| `SHUI.audio()` | 独立的音频采集与分析工厂 |
| `SHUI.graphics()` / `materials()` / `blur()` | 布局、图形、材质和模糊 |
| `SHUI.search()` / `ingredients()` | 搜索、筛选和素材选择 |
| `SHUI.platform()` | 当前加载器的适配接口 |
| `SHUI.registerExtension(id, factory)` | 按脚本周期管理自定义扩展 |
| `SHUI.registerDeveloperPreview(spec)` | 按脚本周期登记开发者图库示例 |
| `SHUI.context()` | 创建需要自行关闭的独立上下文 |
| `SHUI.run(task)` | 在客户端线程执行回调 |
| `SHUI.open(screen)` | 打开现成的客户端界面 |

各组件的输入、绘制顺序及可选依赖约定沿用 [API 总览](API_REFERENCE.md)。例如 `Java.loadClass('com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile')` 可取得波面配置类。

## 生命周期

- `onInit` 可能执行多次，在其中按当前区域重建控件。界面同一次停留期间共用上下文，尺寸变化保留扩展状态。
- 离开界面后执行 `onRemoved` 并关闭上下文，返回该界面时创建新上下文。跨页面保存的业务草稿放在调用方持有的对象中。
- 返回 `AutoCloseable` 的 Java 扩展会自动释放。其他资源通过 `screen.context().manage(resource)` 登记，按创建依赖的相反顺序释放。
- 脚本重载同步撤销扩展和开发者预览登记，随后在客户端线程释放旧界面及上下文，旧世代排队中的 `SHUI.run` 回调会跳过。父子界面一并失效，返回最近的有效父页。图库在帧尾清理已撤销的预览，`PreviewSpec` 的字段参见 [登记开发者预览](API_REFERENCE.md#登记开发者预览)。
- 构建器生命周期及 `screen.button(...)` 回调发生脚本运行时异常时，默认记录日志、关闭出错页面并释放上下文。可用 `onError((screen, failure) => ...)` 自定义报告。直接调用其他 API 时由调用方处理其异常。
- `SHUI.context()` 创建的独立上下文应在使用完后 `close()`。`SHUI.extensions().register(...)` 是底层注册接口，返回的句柄由调用方管理。脚本通常使用带重载清理的 `SHUI.registerExtension(...)`。

普通脚本对象可用 `context.onClose(() => resource.release())` 登记清理函数，也可在 `onRemoved` 中释放。实际绘制、客户端输入和界面实例均在客户端线程使用。

## 实现与版本依据

[Forge 插件](../src/main/java/com/dishanhai/sh_better_ui/compat/kubejs/ShBetterUiKubeJsPlugin.java) 与 [NeoForge 插件](../neoforge/src/main/java/com/dishanhai/sh_better_ui/compat/kubejs/ShBetterUiKubeJsPlugin.java) 分别处理对应版本的绑定和重载时序。[脚本门面](../src/main/java/com/dishanhai/sh_better_ui/compat/kubejs/client/KubeJsUiApi.java) 负责每代脚本的注册与资源。

编译接口以官方发行包及源码为准。[KubeJS 6 发行包](https://maven.latvian.dev/releases/dev/latvian/mods/kubejs-forge/2001.6.5-build.26/) · [KubeJS 7 发行包](https://maven.latvian.dev/releases/dev/latvian/mods/kubejs-neoforge/2101.7.2-build.381/)
