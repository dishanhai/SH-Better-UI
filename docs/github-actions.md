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
