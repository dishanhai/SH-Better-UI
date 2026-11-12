<!-- 作者：山海／dishanhai -->
# Bukkit 兑换页面示例

这套工程提供 Paper 的 Kotlin 入口与 Spigot 的 Java 入口。两者共用同一份页面定义、权威操作和原版聊天回退，可把整个示例目录复制到独立位置构建。构建依赖已发布的 Bridge SDK 和 Spigot API。

在 SH-Better-UI 根目录构建 Bridge 并发布 SDK 到本地构建仓库。

```bash
gradle -p bridge :plugin:jar :sdk:publishSdkPublicationToBuildRepositoryRepository
```

在本示例目录从该仓库构建两个模板。

```bash
gradle -PshbuiSdkRepository=../../bridge/build/repository :paper:build :spigot:build
```

示例可通过 `shbuiSdkRepository` 指定 SDK 仓库的 URL。默认从 Maven Local 解析，SDK 版本可用 `shbuiVersion` 指定。Paper 示例在启动时用 `plugin.yml` 的 `libraries` 加载 Kotlin 标准库。

把 Bridge JAR 和一个示例 JAR 放进服务端的 `plugins/` 目录。示例 JAR 位于 `paper/build/libs/` 或 `spigot/build/libs/`。两个模板共享插件名和命令，安装其中一个。

1. 用装有支持页面协议的 SH-Better-UI 客户端连接服务器。先执行 `/shbetterpages screen` 允许当前连接打开页面，再执行 `/shbui-exchange`。
2. 页面显示服务端保存的余额和兑换目标。点击兑换时，服务端返回对象、数量和后果。确认后，服务端扣除一个测试信用点并发放一个经验点。余额为零时返回失败。
3. 原版客户端执行相同命令时看到余额与聊天回退入口。执行 `/shbui-exchange redeem confirm` 由同一个权威处理器完成兑换。
