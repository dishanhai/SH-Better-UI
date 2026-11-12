<!-- 作者：山海／dishanhai -->
# UMIVP 验证与发布记录

验证在 Windows x64、Java 17/21、RTX 4060 Laptop GPU 和 Chromium 157 下进行。Forge 与 NeoForge 的客户端用隔离游戏目录。网页分别在 ANGLE SwiftShader 与 ANGLE D3D11 两条路径运行。

## 目录扩充验证

扩充验证时的目录包含 51 条记录，48 条有原生路由。Forge 1.20.1 与 NeoForge 1.21.1 均完成 `test kernelTest build`，发行 JAR 各核对 185 个公开符号和打包目录字节。目录及接入测试 14 项通过。

两端隔离开发客户端用 `-PkernelSmokeLabel=effect-catalog-final` 逐项打开并绘制原先缺失的 18 个原生预览，检查 Esc 返回、交叉淡出的实际换页与焦点恢复、模糊可用性和正常退出。每端保留六张模糊与空间场景截图。 [Forge 场景清单](../build/kernel-verification/client-forge-effect-catalog-final/native-effect-previews.txt) · [NeoForge 场景清单](../build/kernel-verification/client-neoforge-effect-catalog-final/native-effect-previews.txt)

后续的 `-PkernelSmokeMaterialEffects -PkernelSmokeLabel=uuk-gallery-effects` 在 Forge Java 17 和 NeoForge Java 21 上核对全部 51 个材质路由，打开并绘制 23 项预览，验证交叉淡出的返回焦点、弹性碰撞和下拉刷新的键盘入口及退出清理。两端都留存了新增场景截图。全量 Forge 内核烟测停在 Liquid Glass 既有的高对比像素断言，以上数据来自独立材质烟测。 [Forge 场景清单](../build/kernel-verification/client-forge-uuk-gallery-effects/native-effect-previews.txt) · [NeoForge 场景清单](../build/kernel-verification/client-neoforge-uuk-gallery-effects/native-effect-previews.txt)

`npm --prefix web run test:material-effects-browser` 在 Chromium ANGLE SwiftShader 和 RTX 4060 D3D11 上分别验证 50 项新增 WebGL 场景。50 项各有独立的确定性画面，42 项重放时产生可测的帧差，39 项验证参数响应及 A/B 并排和分界线像素，4 项验证直接输入。受管 Liquid Glass 的独立浏览器回归也在两个后端通过。新增场景的跨端像素基线与更多 GPU 仍待验收。 [硬件运行日志](../build/uuk-effect-coverage/web-effects-hardware.log)

Forge 与 NeoForge 均通过 `kernelTest test build`，各有 330 项内核测试，常规测试分别为 966 和 968 项。两个 1.0.7 发行 JAR 各核对 187 个公开符号及目录快照。目录与接入测试 14 项，Web 单测 8 项均通过。 [Forge 构建](../build/uuk-effect-coverage/forge-build-final.log) · [NeoForge 构建](../build/uuk-effect-coverage/neo-build-final.log)

以下阶段记录对应目录初次交付时的 42 条快照。

## 阶段记录

| 阶段 | 已完成与文件 | 验证 | 限制 |
| --- | --- | --- | --- |
| 审计 | [源码审计](material-catalog-audit.md)、[覆盖报告](material-coverage-report.md) | 对照公开 API、注册器、资源和示例 | 其他系统与 GPU 无实测 |
| 目录、UUK | `catalog`、`kernel/api/material`、`ClientMaterialCatalog` | 42 条、24 个原生路由，别名与参数测试，双 JAR 每端核对 148 个公开符号 | 18 条缺少独立原生场景 |
| 原生、预设 | [画廊与原生使用](native-preview-guide.md)、[预设格式](preset-format.md) | 两端真实客户端校验稳定 ID、短别名、光学绘制、键盘草稿、减少动效、步进、缩放、A/B、重载、关闭。浏览器和原生使用实际导出文件双向导入 | 参数编辑和 A/B 的新原生工作台覆盖受管 Liquid Glass |
| Web | [Web 渲染](web-rendering-guide.md) | 软件及硬件 Chromium 动态读回、九项参数、四关键帧、分屏、滑动对照、缩放、文件/分享、键盘和无 GPU 降级 | 其余 41 条为 Native Only |
| Prompt、接入 | `prompts`、[接入工具与消费示例](integration-guide.md) | 浏览器导出 `refraction=8`，同一个补丁通过两端 `git apply --check`、编译、打包和验包。隔离客户端实际打开补丁后的消费画面并绘制光学玻璃 | 自动补丁要求现有受管 RenderService 回调和显式锚点 |
| 图像、性能 | `catalog/fixtures`、`web/tests/compare.mjs`、[性能](performance-report.md) | 同一场景裁剪，固定 0/800/1600/2400 ms、640×384、1×、默认草稿、固定指针轨迹 | JEI、不同驱动和跨系统场景仍需独立验收 |

## 可重跑命令

```bash
export JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
node catalog/build.mjs --check
node --test catalog/tests/*.test.mjs
npm --prefix web test
npm --prefix web run build
npm --prefix web run test:browser
UMIVP_HARDWARE_GL=1 npm --prefix web run test:browser
./gradle-install/gradle-8.8/bin/gradle build --offline --console=plain
./neoforge/gradlew -p neoforge build --offline --console=plain -Pkotlin.incremental=false
node catalog/verify-jar.mjs build/libs/sh_better_ui-1.0.6.jar neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.6.jar
```

原生运行从项目根目录启动，`runClient` 自动运行烟测并退出。

```bash
./gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle -PkernelSmokeLabel=material-catalog-final runClient --offline --console=plain
./neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle -PkernelSmokeLabel=material-catalog runClient --offline --console=plain -Pkotlin.incremental=false
```

Forge 初始基线直接编译通过。NeoForge 首次增量编译未识别 Kotlin 玻璃类，强制重编译成功。后续一次 Gradle 测试输出目录被占用，带 `-Dorg.gradle.vfs.watch=false` 重试通过。完整原始日志在 `build/material-catalog`。

消费方实际加载使用单独的 `-PkernelSmokeLabel=material-consumer -PkernelSmokeMaterialConsumer`，先把对应开发环境的独立消费 JAR 放入该运行目录的 `mods/`，并在运行目录放入 `consumer-preset.json`。网页反向验证运行 `npm --prefix web run test:browser -- --evidence-subdir=web-final --native-scene=../build/kernel-verification/client-forge-material-catalog-final/material-catalog-liquid-glass-scene.json`。 [实际网页导入](../build/material-catalog/web-final/evidence.json)

## 固定场景判定

原生与网页裁剪使用同一张共享 PNG 和一致的时间、输入、参数及尺寸。Web 同后端重复帧及原生双 Loader 对照要求完全相同。跨图形后端以单通道最多 1 LSB 作为这组固定帧的量化阈值，Alpha 要求完全相同。该阈值对应同一颜色落到相邻的 RGBA8 量化级。

| 四帧对照 | 变化像素数范围 | RGB MAE 范围 | 最大 RGB 通道误差 | Alpha 最大误差 |
| --- | ---: | ---: | ---: | ---: |
| Forge ↔ NeoForge | 0 | 0 | 0 | 0 |
| 原生 ↔ RTX 4060 D3D11 | 56–70 | 0.0000990–0.0001221 | 1 | 0 |
| 原生 ↔ SwiftShader | 10,616–13,051 | 0.015907–0.019122 | 1 | 0 |

20 组对照全部在上述阈值内。证据附带全部图像哈希、RGB MAE/RMSE/SSIM、Alpha、边缘和最大通道误差。 [跨端原始对照](../build/material-catalog/cross-platform-pixels.json)

Web 预览为 Web Approximation。动态世界、GUI 缩放、文字栅格化、采样精度和多 pass 顺序在新场景中须重新对照。原生烟测另验证动态游戏背景、光学回退、Shader 故障恢复及退出后资源释放。

## 验收与产物

最终 `build` 为 Forge 873 项常规测试及 191 项共享内核测试、NeoForge 875 项常规测试及 191 项共享内核测试，均为零失败、零跳过。目录与接入 Node 测试 13 项，网页单元测试 5 项，夹具的确定性重建通过。两个库 JAR 和静态网页的目录字节与生成文件一致，库内夹具也通过字节比对。 [最终构建日志](../build/material-catalog/phase9-final.log) · [发布清单与 SHA-256](../build/material-catalog/release-manifest.json)

| 条目 | 状态 |
| --- | --- |
| 1–5 公开效果覆盖、ID、旧画廊短 ID、公开符号、无 GPU 的目录查询 | 通过。未独立收录与缺失能力逐项记录 |
| 6–9 Forge、NeoForge、Web 构建及已登记 Web 动态渲染 | 通过 |
| 10–13 原生稳定 ID、预设互通、非法预设保护、确定性 A/B | 受管 Liquid Glass 已验证。其他材质按已有预览能力使用 |
| 14–17 第三方作用域撤销、重载、Web 降级、纯键盘 | 注册/资源回归、原生隔离烟测和浏览器核心流程通过。整合包旁白、高对比与长期重载待验证 |
| 18–19 公开 API 补丁和消费工程 | 两端隔离编译、打包和验包通过，隔离客户端验证真实入口、光学绘制与返回 |
| 20 可追溯证据 | 原始日志、截图、JSON 和复现命令已记录 |

- [Forge JAR](../build/libs/sh_better_ui-1.0.6.jar) 与 [NeoForge JAR](../neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.6.jar)。
- [静态站](../web/dist/) 和 [本地启动](web-rendering-guide.md)。
- [浏览器预设](../build/material-catalog/consumer-preset.json)、[生成的 Unified Diff](../build/material-catalog/consumer.patch)。
- [Forge 隔离客户端](../build/kernel-verification/client-forge-material-catalog-final/success.txt)、[NeoForge 隔离客户端](../build/kernel-verification/client-neoforge-material-catalog/success.txt)。
- [Forge 消费方](../build/kernel-verification/client-forge-material-consumer/material-catalog-consumer.txt)、[NeoForge 消费方](../build/kernel-verification/client-neoforge-material-consumer/material-catalog-consumer.txt)。
