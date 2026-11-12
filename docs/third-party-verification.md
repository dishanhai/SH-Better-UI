<!-- 作者：山海／dishanhai -->
# 第三方页面验证记录

记录日期为 2026-11-11。SH-Better-UI 为 1.0.6，声明式页面 Schema 和 PLAY 协议各为 1.0.0。以下保留首轮 SDK 验证，并记录 Prompt v2 续作的实机与基准证据。

## 构建与自动测试

| 范围 | 结果 |
| --- | --- |
| Forge 1.20.1 完整 Gradle `build` | 304 项 Java 17 内核测试、942 项主测试通过。通过重映射和发布 JAR 检查。 |
| NeoForge 1.21.1 完整 Gradle `build` | 304 项 Java 17 内核测试、944 项主测试通过。通过发布 JAR 检查。 |
| Bridge | Spigot 1.20.1 API／Java 17 与 Spigot 1.21.1 API／Java 21 各 8 项测试通过。构建插件和 SDK，检查 Java 17 字节码及依赖边界。 |
| 独立模组和插件示例 | Forge、NeoForge 模组示例及 Paper Kotlin、Spigot Java 示例均完成构建。模组示例额外通过兑换定义、初始数据和资源包离线检查。 |
| Java／Kotlin SDK | 分别用 Forge 和 NeoForge 正式 JAR 运行两种构建器。每端都输出 499 字节，逐字节比较通过。 |
| Schema／KubeJS | 离线 Schema CLI 验证兑换定义。实际 Rhino 2001 和 2101 在替身宿主中运行同一脚本，通过对象／数组转换、事件回调、局部更新和取消订阅探针。 |

Java 17 内核回归覆盖固定树验证、组件降级、权威字段原子更新、修订缺口与快照恢复、双向握手、帧尺寸与分片、重复和乱序拒绝、超时、按页操作权限和幂等收据。

## 原生游戏冒烟

Forge 47.4.16／Java 17 和 NeoForge 21.1.251／Java 21 隔离开发客户端各自启动整合服务端。最终复跑都生成了 `success.txt` 和 `stopped.txt`。

- 通过原生点击触发本地动作，更新字段，撤销注册并回到原 Screen。
- 从未授权状态开页并收到拒绝，使用客户端命令授权，再经真实 PLAY 通道获得首次显示回执。
- 点击远程按钮后，服务端执行一次权威动作，客户端显示返回的字段更新。
- 被动 HUD 和通知分别等到现有实时表面真正绘制后才完成显示回执。结束时检查模块停止和 Kernel 门面撤回。

`runClient` 使用 `gradle/kernel-smoke.init.gradle` 与 `-PkernelSmokePages`。Forge 初次因资源下载失败，后续用默认语言缓存执行并跳过 `downloadAssets`。中间测试定位到世界加载的渲染重入、实时活动的资格标志与实际绘制时点不同、窗口失焦时实时表面暂停，以及一次 Forge 开发运行时类缺失。冒烟按 HUD、通知顺序测量，在窗口处于活动状态时等待绘制。

## Prompt v2 续作实机

Forge 47.4.16／Java 17 与 NeoForge 21.1.251／Java 21 各用隔离开发客户端和整合服务端执行。两端验收目录位于 `build/kernel-verification/client-<loader>-third-party-v2-final-pages-kubejs/`，保留游戏日志、`success.txt` 及 `stopped.txt`。

| 场景 | 验证 |
| --- | --- |
| 原生控件与 Studio | 页面按钮、开关、中文文本草稿、幽灵槽的键盘选择、嵌套滚动、Tab、GUI Scale 2 → 1 → 2 和草稿保留。Studio 检查布局和协议页签、刷新及返回。 |
| 远程开页与确认 | 未授权拒绝、授予权限、首次显示回执、取消时无服务端动作、确认后仅执行一次。优先模态关闭后恢复同一原页面及权威修订，重连后重新需要授权。 |
| 方块目标令牌 | 在整合服用世界中的实际方块，经客户端原生 use 钩子的入口发出目标，验证错目标拒绝、正确目标开页及重放拒绝。 |
| 原生容器 | 通过原生点击包验证 Shift 转移、槽位过滤、右键拆堆和拖动。关闭后剩余 13 圆石与 3 苹果，再结算也保持总量。另在 5 铜锭留槽、8 铜锭留光标时断开，重开同一存档确认总量为 13 铜锭，当前菜单为原生背包。 |
| KubeJS | Forge 使用 KubeJS 2001.6.5-build.26、Rhino 2001.2.3-build.10、Architectury 9.2.14。NeoForge 使用 KubeJS 2101.7.2-build.381、Rhino 2101.2.7-build.81。两端加载真实模组，验证注册、点击、字段修订、脚本重载和重新打开，服务端脚本确认 `SHUI` 不可见。 |
| HUD 与通知 | 被动 HUD 和通知分别等待实际绘制。关闭客户端时完成模块与 GPU 资源清理检查。 |

冒烟通过 `gradle/kernel-smoke.init.gradle` 加上 `-PkernelSmokePages` 和 `-PkernelSmokeKubeJsMods=<隔离依赖目录>` 启用。测试调用 KubeJS 两版 F3+T 钩子使用的脚本重载入口，随后执行 Minecraft 资源重载。发行包不包含冒烟代码与脚本。全量构建和图形客户端采用串行复跑，避开了同机并行时出现的一次 Windows 测试进程异常退出。

## 协议基准

`pageProtocolBenchmark` 使用 Java 17，每种负载各预热及采样 2,000 次，包含编码、分片、重组与解码。以下是本机一轮 CPU 样本，字节数包含协议分片头。

| 负载 | 字节 | 分片 | p50 | p95 | 平均分配 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 打开 12 个文本节点 | 4,204 | 1 | 46.8 µs | 118.4 µs | 142,786 B |
| 打开 192 个文本节点 | 65,347 | 3 | 568.5 µs | 932.8 µs | 2,084,169 B |
| 单字段更新 | 115 | 1 | 6.9 µs | 9.4 µs | 3,752 B |

## 待验收

- Paper 与 Spigot 的 1.20.1 和 1.21.1 四套真实服务端均已运行到 EULA 提示，随后退出。Forge、NeoForge 和原版客户端的专服联机矩阵等测试服接受 EULA 后继续。 [桥接实机准备](paper-spigot-bridge.md#本机联机矩阵准备)
- Forge／NeoForge 独立专服、多玩家并发、实体交互、目标整合包、真实第三方丢弃拦截和持久容器仍需额外验收。
- 窄视口、多档 GUI Scale、旁白、高对比、减少动效、HUD 拖动和通知合并的完整验收仍在待办。CPU 协议基准覆盖三种负载，长期帧时间和分配量另需实测。

[接入文档](third-party-integration.md) · [协议](protocol.md) · [容器接入](container-integration.md) · [待办](../TODO.md)
