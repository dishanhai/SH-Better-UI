<!-- 作者：山海／dishanhai -->
# Spatial UI 客户端验证

空间逻辑由 Java 17 共享源集提供，复用 Unified UI Kernel 的服务注册、作用域和帧阶段。Forge 47.4.16 / Minecraft 1.20.1 使用 Java 17，NeoForge 21.1.251 / Minecraft 1.21.1 使用 Java 21。两套客户端分别验证实际渲染与资源重载。

## 冒烟与截图

在仓库根目录用 Git Bash 执行。每个场景预热 8 帧，采集 2 帧，保存最后一帧并检查固定区域颜色。测试使用专用开发运行目录。

```bash
gradle-install/gradle-8.8/bin/gradle --offline -I gradle/kernel-client-benchmark.init.gradle runClient \
  -PkernelNativeLabel=spatial-check -PkernelNativeScenario=spatial-visual,spatial-reload,spatial-fallback \
  -PkernelNativeWarmup=8 -PkernelNativeSamples=2

cd neoforge
./gradlew.bat --offline -I ../gradle/kernel-client-benchmark.init.gradle runClient \
  -PkernelNativeLabel=spatial-check -PkernelNativeScenario=spatial-visual,spatial-reload,spatial-fallback \
  -PkernelNativeWarmup=8 -PkernelNativeSamples=2
```

用 `-PkernelNativeGuiScale=1` 和另一个标签重跑，可验证另一种 GUI 缩放。默认缩放为 2。

`spatial-visual` 检查前后遮挡、子节点裁剪、旋转、外阴影、内阴影、凹形路径及孔洞的外阴影，以及空间绘制后原版 GUI 填色是否正常。`spatial-reload` 在采集前重载资源包，核对内核资源代次、SDF 和路径阴影能力，再检查同一组像素。RGB 取样避开抗锯齿边缘，允许每个通道最多 8 级差异。

`spatial-fallback` 在专用开发目录加载故意无效的 Shader 描述，检查降级后的几何、裁剪和原版 GUI，然后撤下测试包并检查 Shader 恢复。

使用 `-PkernelNativeLabel=uuk-path-shadow` 在 Forge 1.20.1 和 NeoForge 1.21.1 隔离客户端运行以上三个场景。两端均通过凹形路径和孔洞的像素断言、资源重载、路径阴影降级与恢复，并正常退出。 [Forge 结果](../build/kernel-benchmark/native-forge-uuk-path-shadow/success.txt) · [NeoForge 结果](../build/kernel-benchmark/native-neoforge-uuk-path-shadow/success.txt)

## 压力基准

将 `kernelNativeScenario` 改为 `spatial` 可运行整组。也可用逗号指定场景，例如 `spatial-static-100,spatial-lighting-1000,spatial-clipping-5000`。

| 场景 | 工作负载 |
| --- | --- |
| `spatial-static-N` | 圆角表面与静态多层阴影 |
| `spatial-moving-N` | 每帧移动、旋转并改变视觉高度 |
| `spatial-lighting-N` | 椭圆表面与移动的彩色点光源 |
| `spatial-materials-N` | 混合材质和少量玻璃背景取样 |
| `spatial-clipping-N` | 两级变换后的裁剪 |
| `spatial-retarget-N` | 每帧改写高度目标，测量动画连续打断 |

每组支持 100、500、1000 和 5000 节点，测量时固定使用 HIGH 质量。结果写到 `build/kernel-benchmark/native-<platform>-<label>/`。

- `frames.csv` 记录帧耗时、渲染提交耗时、分配和 GPU 时间。GPU 数据来自 OpenGL 时间戳查询，设备不支持时记录 `-1`。
- `spatial.csv` 记录场景处理时间、可见节点、阴影与绘制命令、光源和缓存统计，以及空间批次、背景采样与提交耗时。
- `environment.txt` 记录 Java、GPU、分辨率、GUI 缩放和采样参数。只有客户端写出 `success.txt` 与 `stopped.txt` 后才视为该次基准完整。

全量测试结果随具体设备和窗口条件变化。比较两次结果时使用相同场景、质量、分辨率、缩放和预热参数。

## 2026-11-10 实测

Windows 11，RTX 4060 Laptop GPU，800 × 480，GUI 缩放 2，HIGH。两端各运行 27 个场景，每组预热 8 帧并采集 20 帧。视觉、常规重载和损坏 Shader 的降级恢复均通过。以下为 5000 节点场景的帧耗时 P95，单位为毫秒。

| 场景 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | ---: | ---: |
| 静态 | 173.35 | 53.51 |
| 动态几何 | 110.99 | 102.90 |
| 移动光源 | 168.06 | 146.42 |
| 混合材质 | 116.86 | 69.92 |
| 嵌套裁剪 | 56.87 | 52.04 |
| 动画打断 | 79.28 | 96.06 |

本轮采集用于定位高密度开销。两个开发快照分别运行，样本量较短。GPU 时间戳夹住提交区间，区间内可能包含等待命令的时间。原始 CSV 和截图在 `native-forge-spatial-full` 与 `native-neoforge-spatial-full`。

两端均另行通过 GUI 缩放 1 的视觉与重载检查。NeoForge 缩放 1 的混合材质场景每帧记录 1 次玻璃背景采样，损坏 Shader 场景记录 0 条阴影命令，撤下测试资源包后恢复。

最终隔离构建通过 Forge 的 821 项和 NeoForge 的 823 项单元测试，以及两端各 105 项内核测试。两个发布 JAR 的 30 个公共空间类具有相同的 Java 17 签名，3 个空间 Shader 资源一致，基准夹具保留在开发源集。缩放 1 开启生产 GPU 计时后，两端各取得 15 帧有效样本。
