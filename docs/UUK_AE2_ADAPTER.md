<!-- 作者：山海／dishanhai -->
# UUK AE2 适配边界

## 版本和装载

目前仅适配 Minecraft 1.21.1 / NeoForge / AE2 **19.2.18**。编译依赖为
`compileOnly('maven.modrinth:ae2:19.2.18') { transitive = false }`，AE2 不打包进 SHBUI。
未安装、版本不符、第三方终端或屏幕子类均保留原屏幕。可选依赖的装载判断必须在引用
`Ae2RuleAdapter` 之前完成；`Ae2Compatibility` 不引用 AE2 类型，可用于前置检查。

已核对官方标签 [neoforge/v19.2.18](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/neoforge/v19.2.18)：

- [ICraftingService](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.18/src/main/java/appeng/api/networking/crafting/ICraftingService.java)：异步规划、提交以及 requester 生命周期契约。
- [ICraftingRequester](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.18/src/main/java/appeng/api/networking/crafting/ICraftingRequester.java)：真实成品回收、任务链接集合。
- [IManagedGridNode](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.18/src/main/java/appeng/api/networking/IManagedGridNode.java)：服务注册、节点创建与销毁。
- [CraftingCpuLogic](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.18/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java)：无 requester 的玩家提交成功返回空链接，不能据此伪造可恢复任务 ID。

## 绑定与权限

`bind(ServerPlayer)` 从服务器当前 `AEBaseMenu` 提取实际目标，仅接受以下精确类型组合：

| 实体终端 | 菜单 | 屏幕 |
| --- | --- | --- |
| `ItemTerminalPart` | `MEStorageMenu` | `MEStorageScreen` |
| `CraftingTerminalPart` | `CraftingTermMenu` | `CraftingTermScreen` |

绑定键为 `dimension|BlockPos.asLong()|side`，客户端不能提交任意网络 ID 或坐标创建绑定。
初次绑定须持有有效的原菜单，且服务器重新检查玩家身份、非旁观模式、同维度、距目标不超过
8 格、区块已加载、`ServerLevel.mayInteract`、外部 `AccessPolicy`、原实体身份与活动节点。
查询库存和提交规划也使用这些检查；规划完成后的真实提交前再次验证。适配器不加载区块。
运行时必须通过 `setSubmissionGuard` 注入实时规则/页面校验；默认拒绝提交。guard 在真实
`submitJob` 前检查暂停、规则修订、页面可见性等条件，覆盖规划期间关闭页面的时间窗口。
`cancelPending(intentId)` 只取消尚未提交的规划，不取消已经交给 AE2 的任务。

AE2 此版本已无旧版 `ISecurityService`。`AccessPolicy` 是服务器保护模组接线位置；
默认的原版 `mayInteract` 与合法菜单绑定**不等于**兼容所有领地/保护模组。
不能为模拟权限检查直接重放可能有副作用的方块交互事件。接入具体保护模组时应使用它的
只读权限 API。恢复绑定只读取可信服务器存档，玩家重新上线且满足上述条件后才恢复授权。

## 真实合成与结果

支持默认数据组件的注册物品 ID，例如 `minecraft:iron_ingot`。带自定义组件、流体和化学品
不在当前规则输入范围。库存来自 `getCachedInventory`；在途量读取实际 CPU 的最终产物计划数量。
AE2 公共 CPU 状态未暴露最终产物的精确剩余量，因此部分交付期间该值是保守上界，可能延后补货，
不会据此扩大补货量；任务完成后归零。`getRequestedAmount` 明确不包含最终产物，不能替代。
规划使用 `REPORT_MISSING_ITEMS`；缺料、超时、
提交前失去权限或 CPU 拒绝不会被标成成功。

`tick()` 只在 `Future.isDone()` 后读取计划，不阻塞服务器，也不创建后台线程。
实际提交使用 `submitJob`。每个任务建立独立的、非世界方块的公开 API managed node，挂接
`ICraftingRequester` 并连接到已授权实体节点；不修改 AE2 代码或注入原终端服务。
返回的真实 `ICraftingLink.getCraftingID()` 才能成为任务 ID。

成品只在 AE2 的 `insertCraftedItems` 回调中插回同一网络，校验实际链接、原物理 host 与 grid；
不会构造物品来冒充合成。玩家退出、关闭页面、离开范围或停止新规则，不应销毁已提交任务的
回收 requester；回收已有成品不需要玩家继续在线。物理 host 卸载/消失或 grid 断开后，状态为
`UNKNOWN`，不能把库存增加、CPU 不忙或链路缺失推断成成功，不能自动补交。

## 存档与恢复

服务端生命周期接线为 `tick()`、`save()`、`restore(CompoundTag)`、`close()`。
`save()` 保存绑定元数据、提交意图回执、真实 managed node NBT 和真实 crafting link NBT。
关服应先取消尚未提交的规划、保存状态，再释放节点；不把玩家退出当作整个适配器关闭。

当前重启恢复保留已确定的完成/失败终态，其余任务一律恢复为 `UNKNOWN` 并暂停自动重投。
AE2 公共 `StorageHelper.loadCraftingLink` 可以反序列化链接，但仅有 NBT 不证明 CPU 已成功重连；
因此当前实现没有把它当作恢复完成的证据。原 CPU 任务可能仍在运行，未知状态需通过原生 AE2
终端核对后处理。服务端提交意图应在调用适配器前持久化，以覆盖提交成功后进程崩溃、回执未落盘的窗口。

同一存活 grid 的多个物理绑定共享会话内 canonical ID，以协调重复规则。此 ID 不跨服务器重启。
一旦出现 `UNKNOWN`，适配器持久化全局新提交屏障，同时取消尚未提交的规划，防止通过同网另一
物理终端或重新启动绕过未知任务去重。已有确定任务继续运行，屏障不会随时间自动清除。
当前没有供客户端清除此屏障的入口；人工对账与受控恢复入口属于未完成事项。

提交幂等缓存仅保留 `ACCEPTED` / `UNKNOWN`；已明确未执行的回执允许同一 intent 在引擎的有限
重试策略下重新规划。历史任务和回执各限制为 4096 条，规划提前预留回执容量；达到容量后拒绝
新规划，仍可查询旧任务和重放已接受回执。不会按时间或终态淘汰可能仍被引擎引用的幂等证据。
超出容量的存档拒绝恢复而不是截断。长期使用的人工核对/归档入口仍待实现。

## 验证范围

`Ae2CompatibilityTest` 覆盖精确版本、实体终端组合和第三方/无线回退；`Ae2ReceiptCacheTest`
覆盖同 intent 重试、幂等证据保留与容量预留。独立 JDK 21 / JUnit 5.10.2 运行 6 项测试全部通过。
构建与共享规则测试由主任务统一执行；本子任务未启动玩家实例。
真实 AE2 CPU 合成、容量不足、同网多玩家、保护模组、断电/区块卸载、重启与原屏幕回退仍需游戏内验收。
