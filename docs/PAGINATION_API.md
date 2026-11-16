<!-- Author: dishanhai -->
# Pagination

实现参考：[shadcn/ui 官方 Pagination 源码](https://github.com/shadcn-ui/ui/blob/main/apps/v4/registry/bases/base/ui/pagination.tsx)。

将给定 shadcn Pagination 组合映射为原生 UUK 控件。`href` 对应宿主的换页回调；数据获取和界面路由由宿主负责。复用 UUK 按钮、主题、图标、提示和旁白，不依赖 React 或 Next.js。

```java
var data = new PaginationState(250, 25, 2);
var pagination = ShBetterUiApi.controls().pagination(font, new Bounds(x, y, 360, 22),
        Component.translatable("example.pagination"), data, preferences,
        page -> refreshRows(data.fromIndex(), data.toIndex()));
addRenderableWidget(pagination);
```

- `PaginationState`：无 Minecraft 依赖的一基页码、数据数量、每页数量和索引区间。空数据为第 0 页／共 0 页，索引区间为 `[0, 0)`。越界选择钳制；首尾导航不循环。变更每页数量保留原首项所在页，数据减少时钳制到末页。支持 `long` 总数。
- `PaginationRange.items(count, current, siblings, boundaries)`：独立生成有序页码及省略号。`Item.page() == 0` 表示不可交互的省略号；只省略至少两个缺失页。复杂度取决于配置范围，不取决于总页数；siblings 和 boundaries 各为 0–100。
- `UiPagination`：作为单个原生控件注册。`STANDARD` 显示前后导航和页码；`SIMPLE` 仅页码；`ICONS_ONLY` 仅前后图标。窄区域先隐藏导航文字，再缩减页码范围，最后保留前后按钮。宽度连最少按钮都容纳不了时不绘制或接收点击。高度至少 22 GUI 单位。
- `setContent(List<Item>)`：按顺序组合 `Item.previous(text)`、`Item.link(page)`、`Item.ellipsis()`、`Item.next(text)`；`setContent(null)` 恢复自动范围。自定义项最多 128 项，空间不足时不绘制，宿主负责提供合适组合。当前页高亮由共享状态决定；不可用的页码和边界按钮禁用。
- `setLabels(previous, next)`、`setNumberFormatter(LongFunction<Component>)`：替换本地化文案及页码表示。`setDirection(AUTO/LTR/RTL)` 控制排列和前后图标；AUTO 跟随游戏语言，RTL 中左右键按屏幕物理顺序移动焦点。
- 宿主通过 `state()` 同步数量、页码和每页数量，无回调；用户真正换页后才触发 `LongConsumer`。状态在回调前更新，不应直接把回调当作服务端加载成功。
- 交互支持按下反馈、同一目标松开提交、拖出／Esc／失焦取消、左右键和 Home／End 聚焦、回车／空格提交。卸载时调用 `cancelInteraction()`；图库宿主可用 `setInputEnabled` 限制输入。最小按钮宽度为 22，标签使用原生提示显示完整文字，主题设置跟随现有 UUK。

开发者面板 → 控件图库 →「分页导航」包含标准、纯页码、图标、RTL 四组实例，可切换空数据及 10／25／50／100 每页行数，并查看与复制 Java 示例。
可用高度不足时显示示例切换按钮，四种布局仍可逐一操作。

文字两侧的图标复用 `UiButton.setLeadingIcon` 和新增的 `setTrailingIcon`，其他控件也可独立使用；分页根据 RTL 方向选择图标位置。
