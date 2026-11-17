<!-- 作者：山海／dishanhai -->
# UUK 世界叠加几何 API

`WorldOverlayGeometry` 位于 UUK 的 `kernel.api.render`，与 Minecraft 渲染器无关。它提供视锥裁剪、世界包围盒、旋转钟面和二维线段光栅化。调用方负责把世界顶点转换为相机裁剪空间，并在自己的客户端渲染阶段绘制结果。

NeoForge 26.1.2 当前完整构建仍受共享音乐界面的版本迁移影响。为验证并交付这个独立 API，可用已有、可信且未签名的 NeoForge 26.1.2 SH-Better-UI 成品作为基线，运行：

```bash
./gradlew -p neoforge-26.1.2 \
  -I gradle/world-overlay-compat.init.gradle \
  -PworldOverlayBaseline=/absolute/path/to/sh_better_ui-neoforge-26.1.2.jar \
  worldOverlayCompatibilityJar
```

该构建只编译 Java 17 兼容的 `WorldOverlayGeometry` 和独立几何契约测试；测试通过、基线通过检查后，才在新的 JAR 中替换或加入该类及其嵌套类，并添加 `META-INF/sh_better_ui.world-overlay.properties`。该文件记录基线 SHA-256。产物写入 `neoforge-26.1.2/build/world-overlay-compat/`，不会覆盖基线，也不包含 SHCLIB 的代码。

构建会拒绝已签名的 JAR，检查 NeoForge 元数据和 UUK 控件，且逐字节核对基线中其余所有文件。该产物仅表示基线附加的几何 API 及契约测试已通过；它不表示当前完整 NeoForge 26.1.2 源码构建或游戏内验收通过。消费方应通过自己的 `-PshBetterUiJar=<产物路径>` 指定这份单独的依赖；不要把它打包进消费方 JAR。

