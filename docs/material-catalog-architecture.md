<!-- 作者：山海／dishanhai -->
# UUK 材质目录架构与实施计划

现有的 UUK 注册和生命周期负责发布目录服务。Forge 1.20.1 和 NeoForge 1.21.1 共享一份 Java 17 公共模型、一份发布目录和同一套原生画廊 ID。静态网页读取相同的发布目录，WebGL 资源和渲染状态由浏览器端管理。

| 阶段 | 实现与文件 | 验证门槛 | 依赖 |
| --- | --- | --- | --- |
| 0 仓库审计 | `docs/material-catalog-audit.md` 交叉核对公开符号、PreviewSpec、资源、渲染阶段和示例 | 读取两个 Loader 的源码集与真实调用，保存基线编译结果 | 当前工作区 |
| 1 目录 | `catalog/materials`、`catalog/schema`、`catalog/build.mjs` 和 `catalog/generated/catalog.json` | `node catalog/build.mjs --check` 校验 ID、别名、参数、迁移、出处和可重现构建 | 0 |
| 2 UUK | `kernel/api/material` 定义不可变查询，`ClientMaterialCatalog` 挂到 `ClientUiKernel` 与 `UIApis` | 两端 `kernelTest` 与客户端服务契约测试覆盖查询、作用域、注册撤销和预设 | 1 |
| 3 原生 | `DeveloperSamples`、`DeveloperPreviewDirectory` 和现有画廊路由到 `PreviewSpec`，玻璃预览走受管渲染 | 查询稳定 ID 进入同一画廊。验证草稿隔离、暂停、重载、关闭和返回 | 2 |
| 4 Web | `web/src` 读取生成目录，在 WebGL 2 适配受管玻璃，复用 `catalog/fixtures` | 静态构建、离线查询、键盘操作、WebGL 动态输出与失败降级 | 1 |
| 5 预设 | `catalog/schema/preset.schema.json` 与 `catalog/migrations` 定义两端交换协议 | 合法预设双向导入，校验失败保留草稿，同步 A/B 时间与指针 | 2、3、4 |
| 6 Prompt | `prompts/00-master.md` 至 `04-material-registration.md` 读取目录证据 | 用真实条目走查检索、调参、登记与接入 | 3、4、5 |
| 7 接入 | `catalog/integration.mjs` 审计目标，`examples/material-catalog-consumer` 编译生成的接入 | 核对依赖 JAR 的公开符号、生成可审阅补丁、双 Loader 编译 | 2、5 |
| 8 验证 | 共享场景、截图差分、计时与实际 Loader 客户端运行 | 在同一时间、输入和参数下取证，按后端记录证据和缺口 | 3、4、5、7 |
| 9 构建交付 | 静态产物、双端 mod JAR、用户/API/迁移指南及验证报告 | 再跑全套构建与测试，按验收表区分通过与未验证 | 0–8 |

## 数据流与兼容性

- `catalog/materials/` 保存手工核验的定义。生成器输出按稳定 ID 排序的快照，将参数默认值从同一 Schema 派生，并记录内容哈希。两端 Gradle 打包同一生成文件，网页构建复制同一发布快照。目录查询可在后台线程读取一个完整快照。
- `UnifiedUI.get().require(UIApis.MATERIAL_CATALOG)` 沿用服务解析。原生预览的键仍由 `PreviewRegistry` 解析，目录记录只保存映射和证据。旧短 ID 绑定真实的旧条目，例如 `clear_glass` 是旧玻璃配方，`liquid_glass` 是 UUK 受管光学玻璃。
- 扩展用已核验的模块上下文登记自身命名空间。新快照先完成冲突及参数验证，再一次发布。撤销沿用模块作用域。原生画廊仍需确认当前 PreviewSpec 有效。
- 预设用版本化 JSON 保存当前参数，导入时先迁移、核对版本和完整约束，再把结果交给调用方替换局部草稿。Renderer 仅读取已通过验证的值。
- 原生与 Web 的验证状态分开。受管玻璃的 Web 适配起始标为 Web Approximation。只有可重复的同场景对照能够升级为 Web Verified。

## 阶段验证命令

在 Windows Git Bash 下使用已安装 JDK 21 启动 Gradle。共享内核及 Forge Java 工具链使用 JDK 17。

```bash
export JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
node catalog/build.mjs --check
node --test catalog/tests/*.test.mjs
./gradle-install/gradle-8.8/bin/gradle build --offline --console=plain
./neoforge-1.21.1/gradlew -p neoforge-1.21.1 build --offline --console=plain
```

Forge 产物位于 `build/libs`，NeoForge 产物位于 `neoforge-1.21.1/build/libs`。两者均由消费工程作为外部依赖读取。网页由独立命令生成到 `web/dist`。测试、性能和客户端取证记录分别见 [验证报告](validation-report.md) 与 [性能报告](performance-report.md)。
