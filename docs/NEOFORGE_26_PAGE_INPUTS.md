<!-- 作者：山海／dishanhai -->
# NeoForge 26.x 声明式页面：布局与本地输入

26.1.2 原生页面在已有 `column`、`text`、`button`、`spacer` 上增加 `row`、`grid`、`toggle`、`slider`。26.2 复用这组源代码，但需分别构建、实机验证，不能以源码复用代替验收。

## 布局

- `row` 按可见子节点均分当前可用宽度。`grid` 读取 schema 的 `columns`（默认 2）。两者保留 `gap`、`padding`、可选宽高和 inherited `enabled`。
- 子节点的显式宽度不会超过所在格子。余下像素由前面的格子分配，整个行不会因为舍入超出边界。
- 极窄区域先缩小间距，再减少每行格数；外层页面继续负责纵向滚动。未满的最后一行仍使用同样的列宽。
- 数据更新后，页面按稳定节点 ID 恢复焦点和滚动。仅支持已发布的组件，不将 `modal` 等尚未移植的类型伪装成普通容器。

## 输入契约

`toggle` 使用已有原生 `UiToggleButton`；`slider` 使用已有原生 `UiSlider`，schema 的 `[0,1]` 映射到百分比 0–100。滑块低于 9×16 GUI 单位时不创建不可交互的滑块。标签和数值沿用本地化旁白。

```java
var instance = pages.open(context, pageId, initialData, actionId -> {
    // 回调中通过已保存的 Instance 引用读取 localValues()。
});
var input = instance.localValues(); // Map<节点 ID, UiValue>，不可变快照
```

输入值是页面实例内的草稿，不会自动写回数据 schema 或磁盘。无 action 的控件仍可编辑；有 action 时开关提交后触发，滑块释放或键盘调整后触发。拖动预览不重复提交 action；Esc、失焦、页面移除及数据刷新重建会取消未完成的拖动。输入仅在当前页面 ACTIVE 且对应数据 revision 未过期时接受。

`checked`／`value` 的 bound 字段变化时以新权威值为准，其他字段变化不清空该控件草稿。未绑定草稿在布局重建时保留。宿主可用 `PageService.update` 确认新的值。

本批仍只支持 `SCREEN` 和无载荷 `LOCAL` 动作。`payloadField` 会在注册时明确拒绝，而不是触发缺少值的动作；远程动作、模态/HUD 页面、富文本页面宿主和自定义组件工厂不在本批范围。

## 验证范围

新增 `NativePageScreenInputTest` 覆盖组件声明、schema 解析、本地草稿隔离/不可变快照、权威字段同步、窄布局边界和载荷拒绝。既有 `UiSliderInputTest` 覆盖原生滑块取消及键盘提交。是否通过以本次真实 Gradle 输出为准；本文件不声称完成实例交互、旁白音频或 26.2 图形后端验收。
