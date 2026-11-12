<!-- 作者：山海／dishanhai -->
# Paper／Spigot Bridge

Bridge 是独立的 Bukkit 插件，业务插件通过 `UiBridgeProvider.get(plugin)` 获取 `UiBridge` 服务。SDK 和插件都以 Java 17 字节码发布，基线是 Spigot API 1.20.1。Paper 与 Spigot 的 1.20.1、1.21.1 共用同一份桥接插件和协议。运行服务端时使用该发行版要求的 Java。

## 构建与安装

```bash
gradle -p bridge :plugin:test :plugin:jar :sdk:publishSdkPublicationToBuildRepositoryRepository
```

- 服务端安装 `bridge/plugin/build/libs/sh-better-ui-bridge-<version>.jar`。
- 业务插件用 `compileOnly("com.dishanhai.sh_better_ui:sh-better-ui-bridge-sdk:<version>")` 声明 SDK，另加目标 Bukkit API 作为编译依赖。SDK 可从 `bridge/build/repository` 或发布该坐标的 Maven 仓库获取。Bridge 在运行时提供 SDK 类。
- 在业务插件的 `plugin.yml` 中加入 `depend: [SHBetterUIBridge]`，让 Bukkit 按依赖顺序加载 SDK 和业务插件。SDK JAR 是编译依赖，服务端的 `plugins/` 目录安装 Bridge JAR。 [Paper plugin.yml 文档](https://docs.papermc.io/paper/dev/plugin-yml/)

## 注册与开页

使用 Java 或 Kotlin 构造不可变 `UiPageDefinition`。页面和操作 ID 的命名空间与插件名的小写形式一致。注册时绑定开页权限和权威操作处理器。注册句柄支持查询与释放，Bukkit 停用业务插件时 Bridge 关闭其页面并撤销注册。

```java
UiBridge bridge = UiBridgeProvider.get(plugin);
UiBridge.Registration registration = bridge.registerPage(plugin, page,
        player -> player.hasPermission("example.exchange"),
        Map.of(exchangeActionId, UiBridge.ActionHandler.confirmed(
                context -> new UiActionPrompt("Test credit", 1, "Receive one experience point."),
                context -> redeem(context.player()))));

bridge.openPage(plugin, player, registration.pageId(), initialData)
        .thenAccept(result -> {
            if (result.status() == UiOpenResult.Status.DISPLAYED) {
                rememberDisplayedPage(result.instanceId());
            }
        });
```

在 Bukkit 主线程调用注册、开页、更新和关闭。注册允许传入额外控件的公开 Schema，客户端仍独立检查本地注册、版本和远程权限。通过 `openPage` 的进度回调读取 `RECEIVED`、`AUTHORIZED` 和 `CONFIRMED`，用返回阶段读取 `DISPLAYED`、拒绝、关闭或超时。Bridge 调用进度回调并完成返回阶段时位于服务器线程，后续异步任务访问 Bukkit 前须调回该线程。

原版客户端缺少可用握手时返回 `CLIENT_UNAVAILABLE`。业务插件可以根据结果提供原版聊天命令或菜单。客户端确认显示前，业务插件应继续视开页为处理中。玩家拒绝远程页面、缺少能力和协议不兼容均有各自的结果。断线或通道注销会关闭连接和会话。

## 权威操作与原生容器

Bridge 根据 Bukkit 提供的 `Player` 绑定身份，并对每个业务动作重新检查 `shbetteruibridge.use` 与页面权限。`CONFIRMED` 动作用 `ActionHandler.confirmed` 提供服务端确认预览，显示具体对象、数量与后果。业务处理器收到固定类型的动作参数、会话和请求 ID，并负责读取真实余额、检查业务前置条件与提交事务。示例将余额放在服务端玩家持久化数据中，通过 Bukkit 发放经验。生产业务可使用请求 ID 在自己的事务边界去重。

展示格与幽灵槽依客户端能力运行，仅传递显示数据。Bridge 对真实容器页面返回 `CONTAINER_UNAVAILABLE`。业务插件可打开独立的 Bukkit `Inventory` 作为回退入口，继续由 Bukkit 管理光标、拖动、Shift 转移和关闭结算。远程页面 ID 和数据补丁无法建立 Bukkit 槽位所有权。 [Spigot Inventory API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/inventory/Inventory.html)

## 通道与验证边界

Bridge 在启用时用 Bukkit `Messenger` 注册 `sh_better_ui:pages`。Spigot 1.20.1 和 1.21.1 的目标 CraftBukkit 提交在 PLAY 登录时发送 `minecraft:register`，其中包括所有传入频道的 UTF-8 名称，每个名称后带 NUL。客户端也宣告该频道，Bridge 收到客户端 `HELLO` 后建立协议连接，并按 Bukkit 的客户端频道订阅检查决定是否发送数据。每帧上限 24 KiB，分片与协商消息大小由共享协议检查。 [Spigot 1.20.1 登录补丁](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/nms-patches/net/minecraft/server/players/PlayerList.patch?at=3f9263ba3a726846a9466e12da95d73229af4ad9) · [Spigot 1.21.1 登录补丁](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/nms-patches/net/minecraft/server/players/PlayerList.patch?at=19bf846569f71fa9b64bf50b43154945124e44bc) · [Bukkit Messenger](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/plugin/messaging/Messenger.html)

Paper 1.20.1 build 196 与 1.21.1 build 133 的服务端补丁保留了这条 CraftBukkit 注册路径。四个真实服务端组合与原版客户端登录仍需联机验收。 [Paper 1.20.1 build 196](https://fill.papermc.io/v3/projects/paper/versions/1.20.1/builds/196) · [Paper 1.21.1 build 133](https://fill.papermc.io/v3/projects/paper/versions/1.21.1/builds/133)

桥接的八项自动化测试覆盖原版回退、能力握手、开页确认、注销清理、所有权、同名操作的页面隔离、权限撤销、服务端确认令牌和容器回退。Spigot 1.20.1 API／Java 17 和 Spigot 1.21.1 API／Java 21 均通过这组测试。使用 Spigot 1.21.1 API 构建时以 Java 21 启动 Gradle，并传入 `-PspigotApiVersion=1.21.1-R0.1-SNAPSHOT`。两个示例从本地发布的 SDK 坐标独立构建。

### 本机联机矩阵准备

测试目录在 `bridge/build/acceptance/`，四套实例各自使用 `run/` 下的世界、配置与插件。Paper 通过官方服务获取并核对 SHA-256，Spigot 使用官方 BuildTools #201 编译。 [Paper Downloads Service](https://docs.papermc.io/misc/downloads-service/) · [Spigot BuildTools #201](https://hub.spigotmc.org/jenkins/job/BuildTools/201/)

| 实例 | 对应客户端 | 本机地址 | 实际启动结果 |
| --- | --- | --- | --- |
| Paper 1.20.1 build 196 | Forge 1.20.1 和原版 1.20.1 | `127.0.0.1:25621` | 到达 EULA 提示后退出 |
| Spigot 1.20.1 | Forge 1.20.1 和原版 1.20.1 | `127.0.0.1:25622` | 到达 EULA 提示后退出 |
| Paper 1.21.1 build 133 | NeoForge 1.21.1 和原版 1.21.1 | `127.0.0.1:25623` | 到达 EULA 提示后退出 |
| Spigot 1.21.1 | NeoForge 1.21.1 和原版 1.21.1 | `127.0.0.1:25624` | 到达 EULA 提示后退出 |

各实例的 `run/license-check.log` 记录启动提示。需由测试服务端的操作者接受 EULA，才能继续插件启用、加入服务器和联机交互。相应客户端的验收顺序为默认拒绝、允许开页、真实界面显示与权威确认、扣除一点测试信用并获得一点经验、撤销后重开被拒绝。原版客户端需验证聊天回退与无自定义页面显示。 [Minecraft 服务端设置](https://help.minecraft.net/hc/en-us/articles/360058525452-How-to-Setup-a-Minecraft-Java-Edition-Server)

从同一个公开 SDK 构建 [Paper Kotlin 和 Spigot Java 兑换示例](../examples/paper-spigot/README.md)。
