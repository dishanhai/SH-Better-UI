<!-- 作者：山海／dishanhai -->
# 页面与物品容器

`item_display` 接收 `UiValue.ItemReference`，其中只有物品 ID 和显示数量。客户端用当前物品注册表绘制图标。页面数据更新可以替换显示内容，物品所有权始终由游戏服务端管理。

`ghost_slot` 使用候选物品描述与客户端草稿。清空或选中候选项时不会消耗光标或背包物品。需要向服务端提交时，给控件声明动作和一个类型为 `itemReference` 的 `payloadField`，由服务端校验选择。

## Forge 1.20.1 和 NeoForge 1.21.1

服务端用 `ServerPageContainers.openPersistent` 或 `openTemporary` 将 `CONTAINER` 定义绑定到库注册的 `sh_better_ui:page_container` 菜单。服务端和客户端通过原生开窗与容器同步建立同一槽位顺序。定义采用一至六行、九列的静态 `grid`。每个 `container_slot` 声明唯一的存储索引，子节点顺序决定显示顺序。

```java
UiPageDefinition definition = UiPageDefinition.builder(new UiId("example", "sorting_tray"))
        .type(UiPageType.CONTAINER)
        .root("tray", "grid", grid -> {
            grid.property("columns", new UiValue.Int(9));
            for (int index = 0; index < 9; index++) {
                int slot = index;
                grid.child("slot_" + slot, "container_slot",
                        node -> node.property("slot", new UiValue.Int(slot)));
            }
        }).build();

ServerPageContainers.openTemporary(player, definition, Component.translatable("example.sorting_tray"),
        Collections.nCopies(9, ServerPageContainers.SlotRule.ANY), canUseTray);
```

持久容器由调用方提供，长度须与声明槽数相同，关闭后物品继续保存在原存储。临时容器由库创建并独占，关闭时由原生规则退回玩家物品栏或掉落到世界。每个 `SlotRule` 可限制放入、取出及接受的单一物品 ID。服务端每次交互都会检查绑定玩家、容器有效性和调用方权限。依赖方可在权限谓词中检查距离、维度及目标是否仍存在。 [Forge 菜单文档](https://docs.minecraftforge.net/en/1.20.1/gui/menus/) · [NeoForge 菜单文档](https://docs.neoforged.net/docs/1.21.1/gui/menus/)

从声明式页面跳转时，服务端业务处理器在完成鉴权后关闭该页面实例，再打开原生菜单。菜单打开时使用原生容器 ID。点击、拖动、Shift 转移、光标与拆堆走 Minecraft 容器协议。普通远程 Screen 会等待当前原生 Screen 退出，优先模态也受真实容器保护。

## Paper 和 Spigot

桥接 SDK 将 `CONTAINER` 报告为 `CONTAINER_UNAVAILABLE`。插件可在 Bukkit 服务端线程调用 `Player#openInventory(Inventory)` 作为回退，并通过原生点击、拖动和关闭事件维护业务权限和结算。普通页面数据字段适合展示原生库存的摘要。

原生容器回归及实机范围见 [验证记录](third-party-verification.md)。 [桥接接入](paper-spigot-bridge.md)
