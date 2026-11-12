<!-- 作者：山海／dishanhai -->
# Java 和 Kotlin 页面定义示例

两种实现定义同一份兑换页。Java 使用 `UiPageDefinition.builder`，Kotlin 使用 SH-Better-UI 的 `uiPage`、`pageRoot` 和 `node` 扩展。示例验证页面 Schema 和初始数据，Kotlin 入口另外检查两种定义的编码逐字节相同。

使用包含页面 SDK 的 SH-Better-UI JAR。独立工程只通过发布包依赖公共 API，可以在 SH-Better-UI 源码目录外构建。Forge JAR 使用 Java 17，NeoForge JAR 使用 Java 21 时传 `-PexampleJava=21`。

```bash
gradle run runJavaExample -PshBetterUiSdkJar=/path/to/sh_better_ui.jar
gradle run -PshBetterUiSdk=group:artifact:version
```

自定义 Maven 仓库用 `-PshBetterUiSdkRepository` 指定。 `run --args=exchange.uup` 将已验证的定义写入文件。示例覆盖离线定义和编码，业务服务器负责兑换权限、余额与发放奖励。在线开页使用平台 SDK 的异步回执。
