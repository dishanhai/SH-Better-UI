<!-- 作者：山海／dishanhai -->
# UUK 资源选取器

`UiResourcePicker` 参照 GTSH 的资源选取器，提供左侧来源／模组、中间搜索网格、右侧已选清单及底部确认／取消。使用 UUK `UiScreen`、`UiButton`、`UiTextField`、`UiIngredientSlots` 和原生富文本；不依赖 GTSH 或 SHCLIB。

```java
UiWorkbench.selectItem(owner, title, () -> currentEntry,
        initialStack, true, this::openManualIdEditor, chosen -> {
            // 仅点击确认后调用；chosen 为独立副本，包含数量和原型数据。
            applySelection(chosen);
        });
```

`owner` 必须是当前 UiScreen。`initialStack` 可为空；`quantities` 决定是否允许编辑数量，关闭时固定为 1；`manualEntry` 可为空。取消或 Esc 不提交。当前入口是单选替换，左键激活／替换，右键移除当前项，数量必须为正 int。重新布局会保留查询、来源、排序与选择草稿。

物品目录优先使用 JEI 顺序，再追加注册表缺失项。同一份目录、模组分组与按需生成的搜索索引可在多次打开之间复用。目录版本、语言或连接变化后，下次打开时重新获取。物品栏与 JEI 物品书签的数量和原型在每次打开时重新读取。JEI 未就绪时书签来源为空，26.1.2 尚无书签适配。

搜索按空格组合条件：名称／ID、`@模组`、`#标签`、`*ID`、`$提示文本`。提示文本按需计算并在会话中缓存。排序提供默认、名称、模组、ID、数量及按模组排序。物品网格和模组列表各自支持滚轮和可拖动滚动条，首尾停止。较小高度将三个来源压缩为循环入口。

`UiWorkbench.selectItemSequence` 支持多次加入同一种物品。拖动已选条目可调整顺序，拖到空槽时移到队尾，Esc 可取消进行中的拖动。确认回调收到按当前顺序排列的物品副本。

`ResourcePickerState<T>` 位于纯内核，负责暂存、确认／取消、数量验证、搜索、排序和布局。平台数据读取位于 `UiResourcePickerSources`，不暴露加载器类型。所有可见文案使用语言键。物品名称保留样式；`UiRichText.withItem(id, label)` 隔离行内物品的专用字体，`UiRichText.fit(font, component, width)` 在裁切时保留颜色、粗体与行内图标，按钮图标空间不足时沿用控件的文字优先策略。

`ResourcePickerStateTest` 覆盖选择提交、取消、右键移除、非法数量与溢出、来源切换保留草稿、过滤、排序、提示懒计算和 320×180 布局边界。实际物品栏／JEI 书签、不同模组字体和旁白仍须在消费模组实例中验收。
