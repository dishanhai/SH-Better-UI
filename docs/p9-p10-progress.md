<!-- 作者：山海／dishanhai -->
# P9 / P10 联合实施进度

本轮以本地 `ab5a80b` 和启动时的工作区为基线。基线包含已有的富文本 PE0–8、原生视觉合成和材质目录改动。联合 Phase 0–8 对应 P9 专业排版、P10 高级图形以及两者的集成门禁。 [原富文本进度](RICH_TEXT_PROGRESS.md)

## 阶段

| 阶段 | 本轮证据 | 余项 |
| --- | --- | --- |
| 0 仓库审计 | 双端构建、测试、原生富文本烟测、独立消费端、Java 17 字节码、公开签名基线、固定 CPU/GPU 样本。42 轮需求见本页和分域矩阵。 | 完整混合工程的基准随功能推进采样。 |
| 1 共享基础（部分） | `ProjectEdits` 经 UUK 服务发现，以宿主验证的模块和作用域绑定会话。Render Graph 支持单写入者依赖和确定顺序。二维空间和几何在共享 Java 17 层。 | 工程级资源清单、图形文档、帧回执和资源租约的联合门禁。 |
| 2 图文闭环（部分） | 独立 SDK 原生示例接入工程视图、现有图文与受管绘制，覆盖行内凹形矢量。 | P10 场景内的 P9 排版、双端拖拽/取消、完整缩放和热重载矩阵。 |
| 3 专业文字 | Unicode 17 字素边界接入现有换行、编辑和选择，跑官方分段夹具。 | HarfBuzz、字体实例、段落级 Bidi/视觉光标、竖排、全局断行、字体包与原生 Golden。 |
| 4 复杂文档与编辑 | 现有富文本已有行盒、编辑和媒体可复用。 | 环绕、网格、多栏分页、Ruby、数学、真实 OS IME 和增量重排。 |
| 5 矢量、像素与几何交互 | 共享二维几何可细分、变换、裁剪和命中实心单轮廓。 | 持久场景树、曲线、孔洞、布尔、图像滤镜和 Gizmo。 |
| 6 数据、3D、运动与图执行 | 现有原生物品/实体预览、材质和帧采样可作为接入点。 | 流式图表、3D 管线、时间轴及分域图执行。 |
| 7 互操作、Studio 与 SDK | 现有 RichDocument v1 Codec、编辑器和独立 Java 示例提供兼容基线。 | 联合工程 Codec、多人事务、统一 Studio 及 Java/KubeJS/JSON 生成。 |
| 8 优化与发行 | 记录隔离构建制品与验证范围。 | 正式 Forge SRG 客户端、独立专用服、多 GPU、实际整合包和最终发行验收。 |

[P9 十五轮矩阵](p9-typography.md) · [P10 十三轮矩阵](p10-graphics.md)

## 联合架构十四轮

下表中的双端状态以共同源码和验证证据为准。现有富文本能力的游戏内基线由各端隔离启动确认。新功能沿用同一适配路径，具体新增能力见本轮验证。

| 轮次 | 现有入口与本轮落点 | 最早阶段与剩余验收 |
| --- | --- | --- |
| J-01 中央协调 | `UnifiedUI`、`KernelHost`、`LayoutService.bindVisual`。项目编辑从 UUK 查找 | 0/1。组合 P9/P10 项目在同一帧消费 |
| J-02 模块与 API | 两端共享 Java 17 `kernel` 源集，原生适配在 main。新增 `project`、`geometry`、`render` | 0/1。扩展模块和兼容运行矩阵 |
| J-03 帧事务 | `VisualFrameCoordinator` 按既有阶段采样。`RichTextView` 只从已绘制且当前的富文本快照命中 | 1/2。跨视图的准备、回执、失败回退和旧帧失效 |
| J-04 空间组合 | `AlignmentGeometry`、`VisualProjection`。`Geometry2` 复用 `SpatialGeometry.Matrix`，提供局部、GUI 和物理像素空间 | 1/2。书写轴、3D、旋转裁剪、高 DPI |
| J-05 GPU 与材质 | `RenderService`、`GlassService`、GaussBlur。`RenderGraph` 校验依赖、能力和预算并运行宿主绘制顺序 | 1/2/5。Framebuffer 附件声明、租约、渲染回执和回退 |
| J-06 输入编辑 | `InputService`、`RichInlineInteraction` 和本地编辑器。`ProjectEdits` 原子提交文档和节点选择，保留 ID | 1/2/4。跨引擎编辑、真实 IME、焦点及取消矩阵 |
| J-07 资源 | `ResourcesService.version/observe`、`RichTextImages`、现有作用域清理。独立指南用资源世代提交项目补丁 | 1/2。统一清单、异步代际回执、GPU 资源租约 |
| J-08 双平台 | Forge 47.4.16 / Java 17，NeoForge 21.1.251 / Java 21，公共 core 为 Java 17 | 0/1/8。正式 Forge 生产客户端、高 DPI、专用服与渲染模组 |
| J-09 文档与协作 | `RichDocumentCodec` v1。工程本地会话、修订、幂等提交、撤销和重做 | 1/7。工程 Codec、图形引用、迁移、持久与协作 |
| J-10 权限与预算 | 宿主验证模块及作用域。事务结构配额。Render Graph 拒绝反馈和无生产者读取 | 1/5/7。跨域格式、GPU 与多人授权和压力验收 |
| J-11 工程质量 | `kernelTest`、`verifyLibraryJar`、旧富文本基准与烟测，分域 Unicode/几何用例 | 0/8。混合工程 Golden、回归门限、跨 GPU |
| J-12 Studio 和 SDK | 开发者面板、富文本原生编辑器、Web v1 和独立模块。独立指南接入项目服务 | 2/7。统一工具、时间轴、Diff、生成及独立 KubeJS |
| J-13 渐进集成 | 基线、Java 17 切片、独立消费端按顺序验证 | 0–8。后续阶段见上表 |
| J-14 交付 | 双端隔离 JAR、阶段证据与 [TODO](../TODO.md#p9--p10-联合实施) | 8。最终发行矩阵和目标实例验收 |

## 第三方接入

`ProjectEdits` 为 Experimental。客户端模块使用 `module.engine().find(ProjectEdits.KEY)` 探测，传入当前模块和已挂载的自有作用域。服务在内核所有者线程使用。公开的 `ProjectDocument`、`Stamp` 和 `RichDocument` 可保存为只读快照。初始值用现有 v1 Codec 解码，使用相同文档 ID 更新内容。

```java
var edits = module.engine().require(ProjectEdits.KEY);
var session = edits.open(module, scope, projectId, Map.of(documentId, initial));
var revision = edits.current(module, session).orElseThrow().document().stamp();
var result = edits.commit(module, session, new ProjectEdits.Patch(revision, operationId,
        List.of(new ProjectEdits.PutDocument(documentId, replacement)), Optional.empty()));
```

会话作用域关闭即释放文档和历史。保存与同步仍由宿主负责。每个项目最多 32 个文档、4,096 个节点、256 Ki 个 UTF-16 单元，单次补丁最多 128 个操作。服务最多 8 个活动会话，每会话保留 16 条撤销/重做记录和 64 个提交重试键。相同提交 ID 和原始修订在窗口内返回 `ALREADY_APPLIED`。窗口外的旧修订返回 `CONFLICT`。失败结果可用当前 stamp 发起新提交。节点 ID 在文档中唯一，跨文档移动保留 ID，目标存在同名节点时整笔拒绝。

`RenderGraph` 为 Experimental 的 Java 17 计划器。宿主根据可用后端和工作量预算编译，按阶段、依赖和 ID 稳定执行。Runner 在现有受管渲染回调内调用，返回 false 停止后续工作。现有宿主负责 GL 恢复、资源释放和命中发布。独立 SDK 的行内充能条用两个有顺序依赖的绘制步骤，沿用 `RichTextExtensions.Canvas` 提交。

## 重跑

在仓库根的 Git Bash 中使用 JDK 21 启动 Gradle。Forge 游戏与共享契约分别使用 Java 17 工具链。

```bash
./gradle-install/gradle-8.8/bin/gradle build kernelTest verifyLibraryJar --no-daemon --console=plain
bash neoforge/gradlew -p neoforge build kernelTest verifyLibraryJar --no-daemon --console=plain
./gradle-install/gradle-8.8/bin/gradle -p examples/kernel-extension/forge runClient -PexampleSmoke
bash neoforge/gradlew -p examples/kernel-extension/neoforge runClient -PexampleSmoke
```

缓存齐全时可加 `--offline`。Forge 开发烟测可在已有可用资源缓存时加 `-x downloadAssets`。其独立开发启动器使用 Mojmap 库和样例，发布制品由 `releaseJar` 生成。NeoForge 独立烟测使用正式库和样例。

基线日志、ABI、JAR 校验和基准在 `build/p9-p10-phase0-baseline/`，本轮隔离集成日志在 `build/p9-p10-development/`。旧富文本 3,000 节点布局基线 p95 为 Forge 5.182 ms、NeoForge 5.096 ms，每次约分配 15.45 MB。RTX 4060 Laptop 单场景原生完整效果 GPU p95 为 Forge 102.40 µs、NeoForge 97.28 µs。上述样本用于同场景比较，完整 P9/P10 混合负载随阶段补测。

## 本轮制品与测试

使用启动时的隔离基线和本轮差异构建，工作区中的同期 Dock/Collapse 改动留在原位。构建日志为 `forge-verified-build.log` 和 `neoforge-verified-build.log`。两端都通过 `verifyLibraryJar` 和独立示例的 `verifyExampleJar`。Forge 额外通过 `reobfJar` 和 `releaseJar`。

| 平台 | `kernelTest` | `test` | 正式库 SHA-256 |
| --- | ---: | ---: | --- |
| Forge | 246 | 913 | `48ad857021b2c135e3d2a347d214ec030c905da2e5cadfcc964ebb74662fd346` |
| NeoForge | 246 | 915 | `600bd8b617c4c82688baec6824a2825771a8fd4c6346f8b374fcf79c39d28a0c` |

零失败和零跳过。JAR 通过 ZIP CRC、条目去重和 Unicode 许可检查。两个 JAR 各包含 557 个共享核心类，均为 Java 17 字节码，`jdeps` 仅报告 `java.base`。抽查范围覆盖既有 Facade 和公共内核 API，共保留对照的 324 个公开类型及成员签名。统计和原始签名在 `test-results.json`、`verified-release-audit.log` 与 `release-audit/`。

正式库位于隔离目录 `build/p9-p10-development/source/` 内的 `build/libs/sh_better_ui-1.0.6.jar` 和 `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.6.jar`。使用上表校验本轮制品。

两个独立原生客户端均退出 0，产生 `success.txt`、`stopped.txt` 和缩放前后的截图。检查覆盖打包 v1 指南、原版钻石贴图、计量条更新、矢量轮廓内外的 framebuffer 像素和命中、资源重载后项目修订、注入 renderer 异常后的绘制隔离和旧命中失效，以及 Esc 返回、焦点恢复和会话清理。Forge 客户端使用 Mojmap 开发库和样例，NeoForge 客户端使用正式库和样例。 [Forge 原生日志](../build/p9-p10-development/forge-verified-sdk.log) · [NeoForge 原生日志](../build/p9-p10-development/neoforge-verified-sdk.log)
