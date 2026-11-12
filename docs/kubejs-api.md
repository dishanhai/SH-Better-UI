<!-- 作者：山海／dishanhai -->
# KubeJS 统一页面 API

Forge 1.20.1 的 KubeJS 6 与 NeoForge 1.21.1 的 KubeJS 7 继续在客户端脚本注入 `SHUI`。两端共用声明解析器和页面运行时。客户端脚本重载会撤销旧世代注册和事件监听，并关闭该世代打开的页面。原有 `SHUI.screen`、`SHUI.open(screen)`、`SHUI.text` 和 `SHUI.richText` 继续可用。

把 [unified_page.js](../examples/unified-pages/kubejs/client_scripts/unified_page.js) 复制到客户端 `kubejs/client_scripts/`，进入世界后打开示例。按钮增加本地演示计数，脚本通过 `dataChanged` 读取修订号。

| 调用 | 行为 |
|---|---|
| `SHUI.registerPage(id, definition)` | 验证并登记当前脚本世代的定义。 |
| `SHUI.open(id, initialData)` | 由客户端线程打开本世代的页面。返回的阶段在首帧呈现后完成。 |
| `SHUI.update(instanceId, patch)` | 替换当前世代实例的本地字段。 |
| `SHUI.closePage(instanceId)` | 关闭当前世代打开的实例。 |
| `SHUI.on(type, callback)` | 订阅当前脚本世代的页面事件，调用返回句柄的 `close()` 可取消订阅。 |

`definition` 包含 `type`、`title`、`fields`、`actions`、`root`。页面和动作 ID 使用 `kubejs:`。节点用 `id` 与 `type` 指定稳定 ID 和内置控件，可通过 `properties`、`bindings`、`children`、`action` 配置。脚本可用 `text`、`label` 和 `bind` 简写常用文本属性。所有动作在 `actions` 数组声明，在当前客户端执行。

`fields` 的值可使用 `"boolean"` 等类型名称，或者使用 `{ type: "integer", min: 0, max: 100 }` 等带约束的对象。支持 `boolean`、`integer`、`long`、`double`、`string`、`enum`、`list`、`object` 与 `item_reference`。长整数使用 JavaScript 安全整数范围，数组和对象字段整体替换。

事件类型为 `pageOpened`、`pageClosed`、`actionResult` 和 `dataChanged`。回调接收 `pageId()`、`instanceId()`、`actionId()`、`state()`、`revision()` 和 `data()`。本地 `actionResult` 对应已分派的脚本动作，服务端权威结果通过服务端协议交付。脚本只能更新和关闭自己打开的页面。

## 文字可读性

`screen` 和 `modal` 页面可在定义和节点上添加 `textReadability`。子节点逐字段继承父节点和页面的设置。适用控件包括原生按钮、采用同一按钮的切换控件、文本、富文本标签、徽章和列表行。列表行身份在筛选和滚动时保持稳定，数据更换时重新分配。

```js
SHUI.registerPage("kubejs:readability", {
    type: "screen",
    title: "Readability",
    textReadability: {adaptiveColor: true, foregroundProtection: true, contrast: 4.5},
    root: {id: "body", type: "text", text: "A moving background",
        textReadability: {lockHue: true, sampling: "important"}}
});
```

| 字段 | 值 |
| --- | --- |
| `adaptiveColor`、`foregroundProtection` | 布尔值。分别启用选色和帧末清晰前景。 |
| `contrast` | `1` 到 `21` 的比值，或 `"classified"` 按文字用途分类。 |
| `lockHue` | 布尔值。保留文字的原色相。 |
| `sampling` | `"default"`、`"important"`、`"hovered"`、`"focused"` 或 `"declared_only"`。 |
| `samplingMode` | `"auto"` 或 `"explicit_only"`。后者要求控件明确指定取样优先级。 |
| `localColor` | 布尔值，传给 Java 策略。开启后，单行 Minecraft 字形序列可按可测量的视觉字形簇各自选色。 |

自绘页面在 `onInit` 绑定当前脚本世代拥有的 `UiScreen`，在 `onRender` 使用当前帧。下例通过同一个适配器提交页面文字、绘制清晰前景并打开附着弹层。

```js
const textApi = SHUI.textReadability();
const control = "kubejs:readability_demo";
let page;
textApi.screen("Readability").onInit((screen) => {
    page = textApi.page(screen, {adaptiveColor: true, foregroundProtection: true});
    page.control(control, {contrast: 4.5, lockHue: true, sampling: "important"});
    const button = page.button(20, 44, 132, 20, "Open preview", () => {
        page.openPopup("kubejs:preview", button, 132, 60, {inheritPage: true, contrast: 7}, (popup) => {
            const box = popup.bounds();
            popup.submit("kubejs:preview_label", "kubejs:popup_text",
                Text.of("Preview"), box.x() + 26, box.y() + 24, 0xffffffff);
        });
    });
}).onRender((screen, frame) => {
    page.render(frame, (draw) => {
        draw.submit(control, "kubejs:body", Text.of("Readable text"), 20, 75, 0xffffffff);
        draw.foreground(10, (now) =>
            now.drawNow(control, "kubejs:caption", Text.of("Foreground caption"), 20, 90, 0xffffffff));
    });
}).open();
```

两种绘制方法均接收 `(controlId, textId, Component, x, y, argb)`。ID 使用 `kubejs:`，请为每处文字和每行分别使用稳定的 ID。原生弹层以其所在 Screen 的 UI 主题为默认值。设置 `inheritPage: true` 会在弹层自己的设置之前应用页面的文字覆盖项。弹层坐标是 GUI 绝对坐标，受面板和视口共同裁剪。Escape 或 `page.closePopup(id)` 启动关闭，关闭动画完成后弹层作用域失效。绘制回调返回后上下文即失效，错误阶段返回 `INVALID_STAGE`，作用域关闭返回 `SCOPE_CLOSED`，无效 ID、坐标或颜色返回 `INVALID_REQUEST`。配置字段错误在配置时抛出 `IllegalArgumentException`。预算不足、取样不可用或关闭实际取样时，服务仍按 Java 契约应用声明背景或未知背景保护。 [脚本验收](../src/kernelSmoke/kubejs/client_scripts/page_acceptance.js)

启用自适应选色时，每次提交需要一个原色一致的 `Component`。包含多种行内颜色的序列返回 `INVALID_REQUEST`。按已有排版的样式边界拆开提交，并为每个片段提供自己的坐标与稳定 ID。
