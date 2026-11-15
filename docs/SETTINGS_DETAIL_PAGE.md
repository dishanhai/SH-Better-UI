<!-- Author: dishanhai -->
# 二级配置页 / Settings detail page

`UiSettingsDetailPage` 是原生 Java 子页面组件，供设置分组的小箭头打开附属配置。
只提供通用 UI，不包含 SHCLIB 的 HUD 业务项，也不改主配置页。
采用 Card / Field 组合理念，不依赖 React，不复制 shadcn 源码。
参考：[Card](https://ui.shadcn.com/docs/components/radix/card)、[Field](https://ui.shadcn.com/docs/components/radix/field)。

## 交互与生命周期

- 显式 `themeBackground()` 绘制主题背景，再绘制卡片。原生固定返回/标题栏不会跟随内容滚动。
- 内容单列；窄窗口把标签和控件上下排列。长标签裁切并在控件 tooltip 保留完整字段名。
- 滚轮、拖动滚动条、键盘滚动；键盘获得焦点时同步滚入可见区，不等待下一渲染帧。
- 滚动使当前控件完全不可见时清除焦点；`UiScreen` 额外阻断裁切区外焦点的按键和字符输入。
- 单实例仅挂载一张 Screen；resize 保留滚动和编辑 session。一个新的编辑会话应新建页面与 session。
- `returnFocus` 在父页重建后取得小箭头；父页自身负责保留原滚动位置和主草稿。
- 工厂接收 Bounds 和 UiApiContext，返回 AbstractWidget。工厂负责动态状态和完整旁白标签；控件回调若使用 UiToggleButton，须显式 `setValue` 同步状态。

## 分组事务：取消 / 应用 / 恢复默认

向构造器传入 `Actions` 可显示固定操作栏。`SettingsDraftSession<K,V>` 管理局部编辑：

1. **编辑**只改 session，不直接改主草稿。
2. **恢复默认**只改当前分组，支持 `undoReset()` 撤销；取消也能丢弃恢复默认。
3. **取消、Esc、返回**：有修改则确认放弃或继续编辑。确认后只取消本 session，其他分组草稿不变。
4. **应用**：`session.apply` 仅发送实际修改过的 scoped keys。宿主须原子合并到当前主草稿，不替换整个旧快照。
5. `Actions.apply` 返回 null 表示成功并返回父页；返回 Component 表示失败，留在当前页、显示并旁白错误。回调不应执行不可回滚的部分外部写入。
6. 二级页应用是否落盘由宿主明确决定。SHCLIB 接入使用「仅合并主草稿」，最终仍由主控中心原有应用保存。可用 `footerHint` 显式说明。
7. 强制移除或永久 dispose 会清理 session，不允许关闭 guard 阻止资源释放。正常修改的离开确认使用原画面模态框，不卸载编辑页。

不传 Actions 的构造器仍可用于宿主自行控制持久化的普通配置视图；它不会自动创建事务或保存配置。

## UUK 接入示例

以下选项只演示接入，不宣称是实际 HUD 配置。`draft` 是宿主持有的主草稿，`replaceGroupPatch` 必须原子合并：

```java
var session = new SettingsDraftSession<String, Boolean>(
        Map.of("name", draft.showName()), Map.of("name", true));
var field = new UiSettingsDetailPage.Field(Component.translatable("your_mod.show_name"), (bounds, context) -> {
    var holder = new UiToggleButton[1];
    var toggle = new UiToggleButton(bounds, session.get("name"), context.preferences(),
            UiStyle.LIQUID_GLASS,
            value -> Component.translatable(value ? "your_mod.name_shown" : "your_mod.name_hidden"),
            value -> { session.edit("name", value); holder[0].setValue(value); });
    holder[0] = toggle;
    return toggle.button();
});
var actions = new UiSettingsDetailPage.Actions(
        () -> Component.translatable(session.canUndoReset() ? "your_mod.undo_reset" : "your_mod.reset"),
        CommonComponents.GUI_CANCEL, Component.translatable("your_mod.apply"),
        () -> { if (!session.undoReset()) session.resetDefaults(); },
        session::cancel,
        () -> { session.apply(draft::replaceGroupPatch); return null; },
        session::dirty);
var page = new UiSettingsDetailPage(Component.translatable("your_mod.player_settings"),
        List.of(new UiSettingsDetailPage.Section(Component.translatable("your_mod.basic_display"), List.of(field))), actions)
        .footerHint(Component.translatable("your_mod.apply_to_parent_draft"));
var screen = screens.create(module, page.builder().parent(parent).returnFocus(arrowSupplier));
Minecraft.getInstance().setScreen(screen);
```

不要覆盖 `builder()` 的 onInit / onRender / onRemoved，否则会替换组件生命周期。
控件可用条件、存档保存及外部同步仍属于宿主。此 API 尚无经验证的正式发行版，不能假设已有 1.0.11 包含它。

## 验证入口

- `SettingsDetailLayoutTest`：卡片间距、窄布局、空页、非法尺寸、行索引与滚动边界。
- `SettingsDraftSessionTest`：取消、局部 patch、跨组保护、默认撤销、失败保留、重入拒绝。
- `UiScreenTest`：同步焦点回调、不可见键盘/字符防护、dispose 绕过关闭 veto。
- 完整构建：Forge 根目录、`neoforge-1.21.1` 分别运行 `compileJava`、`kernelTest`、`test`。
- 游戏验收：320/640 宽 GUI、长中英文、纯键盘、滚轮/拖拽后 Enter/Space/输入、反复返回、resize、取消确认、应用失败、默认撤销、减少动效、高对比。
