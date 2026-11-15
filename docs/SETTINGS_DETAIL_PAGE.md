<!-- Author: dishanhai -->
# 二级配置页 / Settings detail page

`UiSettingsDetailPage` 是客户端通用子页面组件，不包含 SHCLIB 的 HUD 业务项。
采用 Card / Field 组合理念，原生 Java 渲染，不依赖 React，也没有复制 shadcn 源码。
参考：[Card](https://ui.shadcn.com/docs/components/radix/card)、[Field](https://ui.shadcn.com/docs/components/radix/field)。

- 原生固定返回/标题栏；Esc 和返回按钮回到父页。
- 内容按单列卡片组织；窄窗口把标签和控件上下排列。
- 滚轮、拖动滚动条、键盘滚动以及焦点进入后的自动滚动。
- 单个实例保留本子页滚动位置，重布局重建控件；不能同时挂载到多个 Screen。
- 用 `returnFocus` 取得父页重新创建的小箭头控件。
- 所有配置值由宿主草稿持有。返回不隐式应用也不丢弃；宿主仍负责应用、撤销、校验和恢复默认。
- `Section`、`Field` 接收翻译 Component；控件工厂须使用给定 Bounds，并返回原生 AbstractWidget。控件自己的动态消息应包含字段含义和状态，以便旁白读取。

## UUK 接入示例

在入口处构建一个通用子页面；`draft`、`module`、`screens`、`parent` 和 `arrowSupplier` 均属于宿主。
下面示例的选项仅演示接入，不宣称是实际 HUD 配置：

```java
var page = new UiSettingsDetailPage(Component.translatable("your_mod.player_settings"), List.of(
    new UiSettingsDetailPage.Section(Component.translatable("your_mod.basic_display"), List.of(
        new UiSettingsDetailPage.Field(Component.translatable("your_mod.show_name"), (bounds, context) -> {
            var holder = new UiToggleButton[1];
            var toggle = new UiToggleButton(bounds, draft.showName(), context.preferences(),
                    UiStyle.LIQUID_GLASS,
                    value -> Component.translatable(value ? "your_mod.name_shown" : "your_mod.name_hidden"),
                    value -> {
                        draft.setShowName(value);
                        holder[0].setValue(draft.showName());
                    });
            holder[0] = toggle;
            return toggle.button();
        })
    ))
));
var screen = screens.create(module, page.builder()
        .parent(parent)
        .returnFocus(arrowSupplier));
Minecraft.getInstance().setScreen(screen);
```

不要覆盖 `builder()` 的 onInit / onRender / onRemoved，否则会替换组件生命周期。
父页必须保留自己的草稿与滚动状态。若宿主选择关闭整个编辑会话，离开前的未保存确认由宿主完成。
配置项的启用条件、数据同步和失败处理也属于宿主，而不是 UI 控件自行写文件。

## 验证

`SettingsDetailLayoutTest` 覆盖卡片间距、窄布局、空页、非法尺寸、行索引与滚动边界。
完整构建：分别在 Forge 根目录与 `neoforge-1.21.1` 运行 `compileJava`、`kernelTest` 和 `test`。
游戏验收还需覆盖 320/640 宽 GUI、长中文/英文标签、纯键盘遍历、滚轮/拖拽、重复进入与返回、窗口重设大小、减少动效和高对比。
本组件不改现有主配置页，也不自动接管 SHCLIB 的入口；消费者需要依照上面示例接入。
