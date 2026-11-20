# GitHub Actions 自动构建

推送到 `main`，或向 `main` 提交、更新 PR 时，[Build SH-Better-UI](https://github.com/dishanhai/SH-Better-UI/actions/workflows/build.yml)
会分别构建 Forge 1.20.1（Gradle 8.8），以及 NeoForge 1.21.1、26.1.2、26.2（Gradle 9.2.1）。默认分支也支持从 Actions 手动构建。
构建使用 JDK 21 或 25，另保留 JDK 17 执行共享内核测试。各平台运行 `test kernelTest build verifyLibraryJar`。

构建成功后，在该次运行的 **Artifacts** 下载对应的 `shbui-<平台>`，解压即可得到正式模组 JAR。
产物保留 14 天，不会自动发布 Release。26.2 仍有仓库 TODO 中记录的迁移问题，其构建失败不会阻止其他三个平台，失败时不会上传该平台的 JAR。
