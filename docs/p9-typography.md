<!-- 作者：山海／dishanhai -->
# P9 专业排版审计与字素边界

审计使用联合实施提示的 P9-01–15。表中的 Phase 指 P9/P10 联合阶段。当前工作树中的 [Rich Text PE0–8 记录](RICH_TEXT_PROGRESS.md) 提供了可复用入口和历史验证。P9 的阶段结论以当前实现及实测为准。

| 需求 | 当前入口与行为 | 缺口与最早阶段 | 联合验收及证据 |
| --- | --- | --- | --- |
| P9-01 专业排版 | `RichTextLayout` 生成纯 Java 行盒。两端 `RichTextView` 共享原版字体适配、图文和呈现命中。 | Phase 2/3。专业模式、能力协商、诊断和算法 SPI。 | 同份混合文档走原版、专业和能力降级路径，核对语义、行盒和诊断。原版基线见 [PE1 证据](RICH_TEXT_TESTING.md#phase-1-驗收矩陣)。 |
| P9-02 塑形 | `RichTextView.VanillaFont` 量测和绘制共用样式，含双向片段时借助原版视觉排序。共用核心使用 Unicode 17 扩展字素。 | Phase 3。HarfBuzz、Java 塑形、脚本分段、OpenType 轴与特性、簇级字体回退、真实字形映射及图集。 | 已验证 Indic、Emoji 等字素边界。仍需 Arabic、Indic 视觉塑形、拉丁连字、RTL 数字、缺字、资源包和强制禁用 native 的两端证据。 |
| P9-03 段落断行 | `RichTextLayout` 逐 run 使用 JDK 行断点，窄行在扩展字素边界紧急折行。图像和对象保留完整盒。 | Phase 3/4。段落级断行与全局优化、语言禁则、断词、两端对齐、分页约束、预算回退和局部重排。 | 已验证紧急折行及只去除完整空白簇。待测合法断点、约束无解、预算切换与取消，记录断点代价及重排范围。 |
| P9-04 竖排与方向 | `RichTextView.needsBidi` 转交原版片段视觉排序。该路径暂停视觉 caret 映射。 | Phase 3/4。段落级 Bidi、横竖书写模式、竖排字形、逻辑轴投影、视觉导航、IME 及竖排分栏。 | 使用三种书写模式混合数字、西文、图片和注音，比对绘制、命中、候选框和键盘导航。 |
| P9-05 图文环绕 | `RichTextLayout.Flow.object` 根据比例和行高量测不可分行内盒。 | Phase 4，矢量几何在 Phase 5 补齐。共享轮廓、浮动锚点、响应避让和增量求解。 | 拖动带孔或凹形浮图跨栏，比较同帧排除区、裁剪和命中，记录 resize 预算。既有同行图片见 [行盒说明](RICH_TEXT_LAYOUT.md)。 |
| P9-06 约束网格 | `AlignmentLayout` 布置宿主内容盒，`RichTextLayout` 计算盒内单向流。 | Phase 4。版面模板、网格/区域、跨栏连续流、共享基线、栏平衡、约束诊断和 SPI。 | 窄窗单栏切换三栏，核对源顺序、阅读顺序、基线及冲突诊断。宿主入口见 [对齐说明](VISUAL_ALIGNMENT.md)。 |
| P9-07 分页 | `RichLayoutSnapshot.visibleLines` 以二分法查询视口行，量测仍生成全量行盒。 | Phase 4。页模板、页码、保持规则、脚注、跨页对象、虚拟分页、稳定页索引与导出。 | 编辑前置页面并检查仅重排受影响页链、远端定位及重开和导出的一致性。视口证据见 [Rich Text 测试](RICH_TEXT_TESTING.md)。 |
| P9-08 数学科学 | `RichExtensionNode` 可承载有界行内提供方，`RichDocument` 暂无公式语义。 | Phase 4/6。公式树、受限 MathML/LaTeX、MATH 度量、布局与语义操作、和图表共享数据帧。 | 同一公式从两种格式解析，参数更新同帧影响公式与图表，检查缺表回退及递归上限。 |
| P9-09 东亚排版 | `LocalizedText`/原版 `Language` 提供本地化文本，`RichTextLayout` 使用 JDK 断点。 | Phase 3/4。Ruby 模型、各语言注音、竖排、CJK 字距、压缩和禁则、选择和编辑。 | `zh-CN`、`zh-TW`、`ja`、`ko` 同文档 Golden，覆盖注音溢出、跨行和提交/撤销。 |
| P9-10 字体管理 | `RichStyle.font` 支持原版资源 ID，`VanillaFont` 缺字体定义时回退默认。存在字体定义但缺字时采用原版缺字行为。 | Phase 3。字体世代与实例轴、MATH/颜色表、簇回退、异步栅格化、专业度量及预算。 | 两端热重载和字体轴变化触发实际度量及图集失效，测缺表、坏字体、超额和稳定回退。旧路径见 [行盒说明](RICH_TEXT_LAYOUT.md)。 |
| P9-11 行内对象 | `ImageNode`、`RichObjectNode`、`RichExtensionNode` 和 `RichInlineInteraction` 提供稳定占位、媒体、原生物品/方块/流体、预览和控件。 | Phase 2/4/6。通用基线协商、P10 矢量/数据/3D 引用、嵌套、方向与跨引擎输入。 | 图表可操作，矢量可选中，3D 按行盒裁剪。按稳定 ID 删除、拖放与复原。已有用例见 [行内扩展](RICH_TEXT_EXTENSION.md)。 |
| P9-12 编辑与 IME | `RichTextSelection` 逻辑选择与 `RichTextEdit.replace` 单 run 原子替换，原生工作台另持有文档历史。 | Phase 4。视觉 caret、跨结构事务、组合输入/候选窗口、多选、富文本剪贴板和锚点恢复。 | 已验证 Indic 簇内部位置无法用于选取或替换。真实 OS IME、双向与竖排、重挂载取消和一次提交一次复原待测。 |
| P9-13 增量/GPU | `RichTextView` 缓存当前布局和原版文字序列，按可见行绘制。共享核心基准统计布局、视口与分配。 | Phase 3/4/6。脏依赖传播、局部重排、后台塑形、共享 Glyph Atlas 与 GPU 合批和预算。 | 静态、单字编辑、仅姿态、字体重载四组报告重算范围和 CPU/GPU。当前下方仅有纯 Java 全文布局对照。 |
| P9-14 Studio | `/shbetterricheditor` 的 WYSIWYG、节点属性、媒体预览、历史和 v1 导出。已有 Web 工作台。 | Phase 7。统一工程工具、排版检查器、Profiler、RTL/竖排、排版与图形事务和 Diff。 | 双端原生打开同一工程，切换工具、编辑、回滚、导出，由独立消费模组加载。现行入口见 [编辑器说明](RICH_TEXT_RELEASE.md)。 |
| P9-15 文档互操作 | `RichDocumentCodec` 提供 v1 JSON/DFU，`RichTextMarkup` 提供受限被动标记，`RichSyncProtocol` 支持可选权威同步。 | Phase 1/4/7。联合工程、类型补丁、P10 双向嵌入、格式和清单、迁移/损失报告、并发历史和消费 SDK。 | 跨端往返保留 ID、源偏移和资源引用，检查未知扩展、降级和失败恢复。现行路径见 [SDK](RICH_TEXT_SDK.md) 与 [同步](RICH_TEXT_NETWORK.md)。 |

## Phase 3 先行切片

两端 `RichTextView.prepare` 仍进入 `RichTextLayout.layout`。共用 Java 17 核心以固定 Unicode 17.0.0 属性表扫描每个 run 的扩展字素边界。软折行只在字素边界处落断点，去掉的分隔空白以整簇计。超宽字素沿用既有裁剪行为。相同边界用于 `RichTextSelection.caret`、选区校验和 `RichTextEdit.replace`。例如「क्ष」在窄行保持一个簇，选区内部的 virama 后偏移会被拒绝。

格式 v1 和既有 Java 方法签名继续适用。若调用方保存了旧的 run 内偏移，恢复时应以当前文档校验。旧规则能落在 Indic 簇内部的位置，现在可能被拒绝。Unicode 实现与属性表是内部实现。行断点仍由 JDK 的 `BreakIterator` 提供，塑形和原版字体资源仍由 Minecraft 适配器负责。跨 run 合簇及 RTL 视觉选取待实现。

| 验证 | 结果 |
| --- | --- |
| 正常布局与编辑入口 | RED 中两项新增回归按预期失败。Indic 连字分成两行，Arabic prepend 加空格的末尾被截断。GREEN 中既有及新增布局测试 12 项通过。 |
| Unicode 17 官方数据 | 766 例全部通过。对每例核对边界数组和每个 UTF-16 偏移，另测空字符串与越界偏移。 |
| 测试运行时 | Eclipse Temurin 17.0.18+8，隔离编译使用 `javac --release 17` 与 JUnit 5.10.2。共 14 项通过，零失败和跳过。 [RED](../build/p9-typography/red.log) · [GREEN](../build/p9-typography/green.log) |

从项目根用 Git Bash 重跑。两端目标共用同一测试代码和 Java 17 工具链。

```bash
./gradle-install/gradle-8.8/bin/gradle kernelTest --offline --console=plain --tests '*RichTextLayoutTest' --tests '*RichTextGraphemeConformanceTest'
bash neoforge/gradlew -p neoforge kernelTest --offline --console=plain --tests '*RichTextLayoutTest' --tests '*RichTextGraphemeConformanceTest'
```

### 纯 Java 布局性能

同机用 Oracle JDK 17+35、G1、固定 512 MiB 堆和 4 个可用处理器，使用既有 `RichTextBenchmark` 各预热 150 次并采集 400 次。旧类来自 Phase 0 冻结快照，新类来自本次隔离 Java 17 编译。每组在新 JVM 中运行，顺序为旧、新、新、旧。两组的 3,000 文本节点均为 1,996 行，4,000 混合节点均为 1,999 行。表中为两次运行的 P95 区间及线程平均分配。

| 工作量 | 旧 P95 | 新 P95 | 旧 / 新分配 |
| --- | ---: | ---: | ---: |
| 3,000 文本节点 | 6.408–14.137 ms | 3.601–4.751 ms | 15,449,424 / 5,682,800 B |
| 4,000 图文节点 | 9.571–9.891 ms | 6.999–7.197 ms | 15,804,584 / 6,028,328 B |

墙钟样本有波动。此对照只覆盖固定度量器的全文布局。原生字体、重载及 GPU 性能仍待采集。 [旧首轮](../build/p9-typography/before.log) · [新首轮](../build/p9-typography/after.log) · [新重跑](../build/p9-typography/after-repeat.log) · [旧重跑](../build/p9-typography/before-repeat.log)

```bash
./gradle-install/gradle-8.8/bin/gradle richTextBenchmark --offline --console=plain
bash neoforge/gradlew -p neoforge richTextBenchmark --offline --console=plain
```

### 数据来源和再生成

代码按 Unicode 17.0.0 的默认扩展规则使用 Grapheme_Cluster_Break、Indic_Conjunct_Break 和 Extended_Pictographic 属性。离线构建使用随源码提供的 `UnicodeGraphemeData.java` 与 `UnicodeGraphemeTestData.java`。更新数据时把下列四个同版文件放进同一目录，再运行生成脚本。 [UAX #29 §3.1.1](https://www.unicode.org/reports/tr29/tr29-47.html#Grapheme_Cluster_Boundary_Rules) · [Unicode-3.0 许可](../src/main/resources/META-INF/third-party/unicode/LICENSE)

- `GraphemeBreakProperty.txt` SHA-256 `d6b51d1d2ae5c33b451b7ed994b48f1f4dc62b2272a5831e7fd418514a6bae89`。 [官方数据](https://www.unicode.org/Public/17.0.0/ucd/auxiliary/GraphemeBreakProperty.txt)
- `DerivedCoreProperties.txt` SHA-256 `24c7fed1195c482faaefd5c1e7eb821c5ee1fb6de07ecdbaa64b56a99da22c08`。 [官方数据](https://www.unicode.org/Public/17.0.0/ucd/DerivedCoreProperties.txt)
- `emoji-data.txt` SHA-256 `2cb2bb9455cda83e8481541ecf5b6dfda66a3bb89efa3fa7c5297eccf607b72b`。 [官方数据](https://www.unicode.org/Public/17.0.0/ucd/emoji/emoji-data.txt)
- `GraphemeBreakTest.txt` SHA-256 `e2d134d2c52919bace503ebb6a551c1855fe1a1faec18478c78fff254a1793ec`。 [官方数据](https://www.unicode.org/Public/17.0.0/ucd/auxiliary/GraphemeBreakTest.txt)

```bash
node scripts/generate-grapheme-data.mjs build/p9-typography/ucd
```
