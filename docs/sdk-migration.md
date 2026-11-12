<!-- 作者：山海／dishanhai -->
# 声明式页面 SDK 迁移

现有 `ShBetterUiApi`、`UnifiedUI.get()`、`ScreenService`、`LayoutService` 和 KubeJS 分组调用继续提供原有 API。新建声明式页面可以逐页采用 `UnifiedUI.pages()`，既有自绘 Screen 和原生 Menu 可继续使用原接入方式。 [既有 Kernel 迁移指南](migration-guide.md) · [第三方接入](third-party-integration.md)

## 客户端模组

1. 在对应 Loader 工程声明 SH-Better-UI 为外部依赖。保留已有模块清单、命名空间和 `ModuleContext`。
2. 把页面结构改为 `UiPageDefinition` 和稳定的 `UiNode` 树。先用 `UiPageValidator` 验证属性、数据绑定、动作声明，再根据客户端实际能力决定替代控件或是否打开。
3. 从 `UnifiedUI.pages()` 注册页面。每次打开单独传入完整初始数据，读取 `PageService.Instance` 的首帧和结束结果。调用 `PageService.update` 替换声明过的数据字段。
4. 使用 `RegistrationHandle.close()` 撤销注册。通过 `Instance.close()` 关闭一个页面实例。两者在内核所属客户端线程调用。

## 服务端模组和业务插件

- Forge／NeoForge 服务端入口为 `ServerPages`。Bukkit 业务插件依赖 Bridge SDK 并使用 `UiBridgeProvider.get(plugin)`。服务端入口由各平台负责调用线程、连接和命名空间。
- 将余额、奖励和库存的读取与提交保留在业务服务端。把界面显示值映射成 `UiValue` 字段，传给开页或更新方法。远程按钮发送动作请求，服务端处理器使用真实玩家权限和业务状态。
- 按 `UiOpenResult` 的终态处理呈现、超时和拒绝。缺少客户端能力时，业务插件可采用原有菜单或聊天回退。真实容器继续使用原生 Menu／Slot 或 Inventory，声明式页面可以从展示格跳转到独立的原生入口。 [Bridge 接入](paper-spigot-bridge.md)

## KubeJS

保留旧的 `SHUI.open(screen)`。新增 `SHUI.open(id, data)` 用于声明式页面，先以 `SHUI.registerPage` 登记本世代的 `kubejs:` 页面。事件监听器和本地页随客户端脚本重载释放。 [脚本 API 与示例](kubejs-api.md)

## 版本与依赖

- 既有公开 API 沿用 [公开契约说明](public-api.md)。新加入的 `kernel.api.page`、`kernel.api.protocol`、`ServerPages` 与 Bridge SDK 处于 Experimental 阶段。消费代码使用对应版本公开 JAR。
- 静态 Schema 与网络协议分别版本化。页面定义和初始数据可在独立 JVM 验证。运行期以端点协商的页面类型、控件版本、能力与消息预算为准。
- Bukkit 业务插件从 Bridge 插件加载公开类。Java／Kotlin 模组用目标 Loader 的库包。保留依赖声明，避免在消费方 JAR 内复制 Kernel 类。 [页面 Schema](page-schema.md) · [协议](protocol.md)
