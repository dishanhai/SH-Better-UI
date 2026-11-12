<!-- 作者：山海／dishanhai -->
# P10 高级图形审计与二维几何契约

审计基线为 `ab5a80b` 及其上的工作区改动。两端现有项目分别使用 Forge 1.20.1 和 NeoForge 1.21.1，`src/kernel/java` 由两端以 `--release 17` 编译。现有 Rich Text PE0–8、Spatial UI 和材质目录提供可复用入口。表中的既有证据只覆盖其记录的用例，联合 P9/P10 的原生验收须单独重跑。

## P10-01–13

| 轮次 | 现有入口 | 缺口与最早阶段 | 实际验收与证据 |
| --- | --- | --- | --- |
| P10-01 高级图形 | `SpatialUI`、`RenderService.submit`、`LayoutService.bindVisual`、`RichTextView` | Phase 1 建立受管 Pass 依赖和公共几何，Phase 2 接合矢量、图像和文字，Phase 6 加图表及通用 3D。 | 既有双端效果、图片和狐狸见 [PE2–4](RICH_TEXT_TESTING.md#pe24-回歸與原生門禁)。待同一活动 Screen 下核对四类内容的呈现、资源及输入世代。 |
| P10-02 场景树 | `SpatialScene` 的 scope、父子、局部变换、Z、裁剪与命中。`FrameViewSnapshot` 的已呈现布局 | Phase 1 补共享坐标，Phase 5 补图形节点类型、不可变场景快照、即时命令和与布局的依赖，Phase 6 加相机。 | 既有节点契约见 `SpatialSceneTest` 与 [原生空间测试](spatial/Testing.md)。联合嵌套姿态、Clip 和 Overlay 门禁待验证。 |
| P10-03 矢量与路径 | `SpatialGeometry.Shape`、空间 SDF、`RichObjectNode.Icon` | Phase 1 增加同源实心多边形绘制数据及命中，Phase 5 补曲线、孔洞、布尔、描边、SVG、形变和 GPU 缓存。 | `FilledGeometryTest` 验证凹多边形、反射、裁剪、越界与预算。带孔路径在渲染、裁剪、命中和 P9 环绕共用几何的用例待实现。 |
| P10-04 位图与图像 | `RichTextImages`、`ResourcesService`、`AlignmentAssets`、图标库 | Phase 5 补像素采样与缩放、图层和混合、色彩空间和 CPU/GPU 滤镜、素材图集与像素编辑。 | PNG/GIF/APNG 和资源重载已有 [PE5–6](RICH_TEXT_TESTING.md#pe56-雙端驗證) 记录。整数倍最近邻、CPU/GPU 颜色容差及全格式 disposal 待补 Golden。 |
| P10-05 图表与仪表盘 | 富文本固定行盒扩展和 `RichTextExtensions` 数据来源可作嵌入边界 | Phase 6 实现图表模型、带作用域订阅、刻度与非有限值处理、流式背压、降采样与拾取。 | [富文本扩展](RICH_TEXT_EXTENSION.md) 的能量条已有消费样例。待在图表样例断言暂停／恢复、关键极值、原始点引用及订阅关闭。 |
| P10-06 三维可视化 | `RichObjectNode.Item/Block/Entity` 的宿主渲染、空间 SDF 阴影与光照 | Phase 4 定义嵌入接口，Phase 6 补通用 Mesh、离屏管线、相机、深度和拾取。 | 既有狐狸像素和登出清理见 [PE2–4](RICH_TEXT_TESTING.md#pe24-回歸與原生門禁)。待验证透明排序、反投影、世界卸载和禁用 3D 的稳定占位。 |
| P10-07 动画与运动 | `AnimationService` 属性租约、`UiTimeline`、`InterruptibleAnimation`、`Spring` 和原生动画预览 | Phase 6 补可定位、反放、显式时钟的多轨属性编排、FLIP、粒子和数据动画。 | 既有 `AnimationServiceTest`、`UiTimelineTest` 和 `InterruptibleAnimationTest` 可作回归。待固定时钟验证时间轴、重定向、确定性回放与减少动效反馈。 |
| P10-08 程序化图形 | `MaterialCatalog`、原生目录预览、`GaussBlur`、Glass、`VisualReveal` | Phase 1 建受管 Pass 图，Phase 6 补受限材质 IR、能力协商、Shader 编译回退与质量诊断。 | 既有 [材质目录验证](validation-report.md) 和 `RichTextEffectsSmoke`。待验证图环、反馈、Shader 不可用、预算耗尽、GPU 异常及重载释放。 |
| P10-09 Graphics Studio | `DeveloperPanelScreen`、`RichTextEditorScreen`、材质草稿、Web 工作台 | Phase 7 加统一工程视图、图层、路径和 Gizmo、时间轴、图表、3D 与工具 SPI。 | 既有原生编辑、重开与导出见 [PE7–8](RICH_TEXT_TESTING.md#pe78-雙端驗證)。待用同一混合工程走完编辑、撤销、保存、重开和独立消费。 |
| P10-10 图形交互 | `InputService`、`FocusService`、`SpatialScene.hitTest`、`VisualFrameCoordinator` | Phase 2 接合呈现帧与矢量命中，Phase 5 补选择、捕获、吸附、变换与取消事务。 | 既有滑杆取消、拖放、捲动和换页见 [PE2–4](RICH_TEXT_TESTING.md#pe24-回歸與原生門禁)。待新增指针捕获、Ctrl／Shift、Esc／失焦及纯键盘图形编辑用例。 |
| P10-11 节点图 | `DependencyGraph` 提供内核依赖排序，`TokenDependencyGraph` 处理主题依赖。 | Phase 6 增加端口、类型、子图和有界的分域执行，Phase 7 补编辑及 Trace。 | 待验证错误端口、缺失输入、循环、子图深度、取消、步进和无权限节点保留。现有依赖排序作为基础回归。 |
| P10-12 几何与约束 | `SpatialGeometry.Matrix`、`AlignmentGeometry`、`CollisionService` | Phase 1 复用矩阵并引入类型化二维空间及有界细分。Phase 4/5 补共享环绕、路径、吸附与约束。Phase 6 加 3D。 | `FilledGeometryTest` 以固定种子比较同一变换与裁剪后的三角面命中和射线参考。P9 排除轮廓、P10 命中和 Studio 参考线联合用例待完成。 |
| P10-13 图形文档 | `RichDocumentCodec` v1、`MaterialPresetCodec`、现有可选富文本同步 | Phase 1 定义工程修订，Phase 7 补图形文档和 Codec、可移植包、资源清单、迁移、Diff、增量同步及损失报告。 | 既有 [富文本交换](RICH_TEXT_RELEASE.md)、[同步](RICH_TEXT_NETWORK.md) 和材质预设测试。待验证图形语义标准化、资源缺失、旧版迁移及导出损失。 |

## 二维几何 API（Experimental）

`Geometry2` 与 `FilledGeometry` 位于 `src/kernel/java/.../kernel/api/geometry`。两端使用同一份 Java 17 代码，后台线程可处理独立的不可变输入。`Local`、`Gui` 和 `Framebuffer` 在类型层面区分坐标。调用方负责节点及帧的身份，以及实际 framebuffer 与 GUI 的比例。`Transform` 复用 `SpatialGeometry.Matrix`。矩阵参数按 `a, b, c, d, tx, ty` 排列，`x' = a*x + c*y + tx`，`y' = b*x + d*y + ty`。

```java
var local = FilledGeometry.polygon(List.of(
        new Point<Local>(0, 0), new Point<Local>(30, 0),
        new Point<Local>(30, 10), new Point<Local>(10, 10),
        new Point<Local>(10, 30), new Point<Local>(0, 30)), 500);
var pose = new Transform<Local, Gui>(new SpatialGeometry.Matrix(1, 0, 0, 1, 18, 22));
var visible = local.transform(pose).clip(new Bounds<Gui>(0, 0, 100, 80), 500);
// Submit visible.triangles() in draw order. Keep visible with the successfully presented frame.
boolean hit = visible.contains(new Point<Gui>(25, 28));
```

- `rectangle` 接收端点形式的 Bounds。`polygon` 接收隐式闭合的单轮廓，支持两种输入绕序及凹角，输出稳定顺序和正绕序三角形。坐标范围为 ±1,000,000。双精度叉积比较使用四个 ULP 的容差，重复、连续共线、相交及数值退化轮廓以 `IllegalArgumentException` 拒绝。
- `clip` 将轴对齐矩形裁剪直接应用于三角面。网格的 Bounds 为空或包围所有绘制三角面。`contains` 使用闭边界，调用方用呈现顺序处理共享边上的命中优先级。奇异或不可逆变换产生空网格，超出坐标范围的投影抛出 `IllegalArgumentException`。
- 输入至多 256 个顶点，输出至多 4,096 个三角面。`maxChecks` 范围为 1–1,000,000，限制边相交检查、候选耳及顶点检查和裁剪遍历。超限抛出 `FilledGeometry.BudgetExceededException`，调用方可保留最后一次有效呈现或提交占位。返回值只在完整处理后生成。
- 该切片涵盖直线组成的实心单轮廓与轴对齐裁剪。带孔路径、曲线、描边、布尔运算、GPU 缓存、约束求解及三维拾取的公共入口留待对应阶段实现。

## Phase 2 行内矢量接入（Experimental）

可信 Java 模块可在已注册的 `RichTextExtensions.Renderer` 中调用 `context.graphics().fillGeometry(mesh, argb)`。网格点采用文档局部坐标，原点与 `context.bounds()` 和既有 `Canvas.fill` 一致。行内扩展仍通过 `RichExtensionNode` 的固定行盒、版本和作用域登记接入。调用仅在原生渲染线程且 Canvas 有效时允许。

- 网格在记录时裁入行盒，渲染沿用 `VisualCanvas` 的 pose 和 framebuffer scissor。Forge 和 NeoForge 通过现有 `PlatformSpatialVertices` 提交同一批三角面，使用原版 position-color shader 并恢复调用方渲染状态。
- 使用 `fillGeometry` 时，有色网格的并集成为该扩展的命中形状。透明、裁空的网格及伴随的文本和矩形装饰不会扩大命中。`RichTextView.hitTest` 先检查已呈现帧和视口，再经逆变换检查行盒与实际绘制网格。渲染回调抛错或跳过时，当前帧不发布该扩展的可交互命中。注销立即使已呈现扩展的命中失效。
- 普通扩展绘制仍使用矩形命中。编辑器可以在实际绘制后选择未找到注册的占位符。渲染失败仍显示替代文本或占位，关闭、重载或切换绑定时丢弃先前的呈现。
- 每片段限 1,024 个三角面，每次视图绘制限 8,192 个。每条命令按输入与裁后面数中的较大值计费。超额或坐标无效走扩展失败路径。

## 重跑

```bash
./gradle-install/gradle-8.8/bin/gradle kernelTest --tests '*FilledGeometryTest' --offline --console=plain
bash neoforge/gradlew -p neoforge kernelTest --tests '*FilledGeometryTest' --offline --console=plain
./gradle-install/gradle-8.8/bin/gradle test --tests '*RichTextGeometryHitTest' --offline --console=plain
bash neoforge/gradlew -p neoforge test --tests '*RichTextGeometryHitTest' --offline --console=plain
```

本轮用 `javac --release 17` 和本机 JUnit Platform 单独运行 `FilledGeometryTest`，5 项均通过。`javap` 核对 class major 为 61，`jdeps --recursive` 核对生产类只依赖共享 `SpatialGeometry.Matrix` 和 `java.base`。本机记录在 `build/p9-p10-geometry/contracts.txt`。两端构建与原生 Smoke 另以联合验证报告为准。

Forge 与 NeoForge 的客户端单测分别通过 11 项。`RichTextGeometryHitTest` 验证命中并集、不可变快照、注销、空命中和替代路径。同轮执行既有 `RichInlineInteractionTest` 和 `RichTextEffectsTest`。本机构建日志为 `build/p9-p10-geometry/inline-forge.txt` 与 `build/p9-p10-geometry/inline-neoforge.txt`。
