# GitHub Actions 自动构建

- **[Build SH-Better-UI (Push)](https://github.com/dishanhai/SH-Better-UI/actions/workflows/build.yml)**：
  向本仓库的任意分支推送提交时自动构建，无需提交 PR。仅在本地提交不会触发云端。
  外部 fork 发往 `main` 的 PR 仍执行检查；本仓库的 PR 使用对应分支的推送构建，避免重复执行。
- **[Build SH-Better-UI (Manual)](https://github.com/dishanhai/SH-Better-UI/actions/workflows/manual-build.yml)**：
  进入 Actions 后选该工作流，点击 **Run workflow**，选择分支并开始。无需提交 PR。
  工作流定义必须先进入默认分支；要从旧分支手动构建，请先同步这两个工作流文件。

手动与自动入口运行相同的四平台构建：Forge 1.20.1（Gradle 8.8），以及 NeoForge 1.21.1、26.1.2、26.2（Gradle 9.2.1）。
构建使用 JDK 21 或 25，另保留 JDK 17 执行共享内核测试。各平台运行 `test kernelTest build verifyLibraryJar`。

构建成功后，在该次运行的 **Artifacts** 下载对应的 `shbui-<平台>`，解压即可得到模组 JAR。
产物保留 14 天，不会自动发布 Release。四个平台均会核对结果；失败的平台不会上传 JAR。

## 只运行回归测试

在 **[Test SH-Better-UI (Regression)](https://github.com/dishanhai/SH-Better-UI/actions/workflows/regression-tests.yml)** 选择 **Run workflow** 和目标分支，不用开 PR。测试基础设施的文件变更也会触发；日常提交仍走原来的 Push 构建。手动入口须先合入默认分支，目标分支也须含测试工作流和脚本。

- 四个游戏版本执行完整 `test` 和 `kernelTest`；另跑服务端桥接、Web 单元／fixture、四套无头 Chrome 测试，以及 Python 字体工具回归。依赖独立 GTSH 工作树的迁移契约与游戏内手动验收不包含在这个独立工作流。
- 不调用模组或桥接的 `build`、`verifyLibraryJar`，不输出或上传发行 JAR。NeoForge 1.21.1 的脚本沙箱回归仍会临时创建供测试启动的独立 worker JAR。
- Actions 日志列出 JVM 失败的类名、方法名、错误与堆叠；运行摘要列出通过、失败和跳过数。JUnit XML 和 HTML 报告保留 14 天，测试失败也会上传已有报告。
