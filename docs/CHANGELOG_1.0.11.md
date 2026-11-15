<!-- 作者：山海／dishanhai -->
# SH-Better-UI 1.0.11

🌐 [English](#english) · [简体中文](#简体中文)

## English

This update covers the main changes since **1.0.6**.

### ✨ Highlights

- **Adaptive text readability:** Text can sample its background and adjust its color or protection to remain readable. Screen and HUD integration is available; supported features vary by platform.
- **More UI controls:** Added HUD sections and vertical scrolling controls, and improved popup layout and transitions.
- **Themes and previews:** Expanded theme effects, reusable icons, the material catalog and its web previews.
- **Developer tools:** Expanded declarative page APIs, added a Kotlin page DSL, and improved KubeJS integration. Markdown and external-link APIs are also available.
- **NeoForge 26.x alpha:** Added separate ports for Minecraft 26.1.2 and 26.2, including a developer gallery, controls, material previews and Screen/HUD text readability.
- **Paper / Spigot bridge:** Added a separate server plugin and SDK for declarative UI pages. Live server integration still needs acceptance testing.

<details>
<summary>📦 Platforms, dependencies and limitations</summary>

| Minecraft | Loader | Java | KotlinForForge |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.16+ | 17 | 4.12.0+ (4.x) |
| 1.21.1 | NeoForge 21.1.251+ | 21 | 5.10.0+ (5.x) |
| 26.1.2 | NeoForge 26.1.2.126+ | 25 | 6.2.0+ |
| 26.2 | NeoForge 26.2.0.93+ | 25 | 6.3.0+ |

Install the JAR matching your Minecraft version and loader. The **26.x builds are `1.0.11-alpha.8`**, with incomplete feature parity: native declarative pages, rich text, global activities and other integrations are still being ported.

The Paper / Spigot bridge is a **separate plugin**; real inventory-container pages are not supported through the bridge. See the [26.x migration gaps](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/NEOFORGE_26_GALLERY_GAPS.md) and [bridge guide](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/paper-spigot-bridge.md).

</details>

📚 [API reference](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/API_REFERENCE.en.md) · [Text readability](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/TEXT_READABILITY_PHASE_4_5.md) · [Project](https://github.com/dishanhai/SH-Better-UI)

---

## 简体中文

本次更新涵盖 **1.0.6** 以来的主要变化。

### ✨ 更新亮点

- **文字自适应可读性：**文字可采样背景，并调整颜色或保护效果，以提升可读性。已接入 Screen 与 HUD，具体支持范围因平台而异。
- **更多界面控件：**新增 HUD 分区与竖向滚动控件，改进弹窗布局和过渡动画。
- **主题与预览：**扩展主题效果、通用图标、材质目录及网页预览。
- **开发工具：**扩展声明式页面 API，新增 Kotlin 页面 DSL，改进 KubeJS 接入，并提供 Markdown 与外部链接 API。
- **NeoForge 26.x 测试版：**新增 Minecraft 26.1.2 和 26.2 的独立移植，包含开发者图库、控件、材质预览及 Screen／HUD 文字可读性。
- **Paper／Spigot 桥接：**新增独立服务端插件和 SDK，用于接入声明式页面；实际服务端联机验收仍待完成。

<details>
<summary>📦 平台、前置与已知限制</summary>

| Minecraft | 加载器 | Java | KotlinForForge |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.16+ | 17 | 4.12.0+（4.x） |
| 1.21.1 | NeoForge 21.1.251+ | 21 | 5.10.0+（5.x） |
| 26.1.2 | NeoForge 26.1.2.126+ | 25 | 6.2.0+ |
| 26.2 | NeoForge 26.2.0.93+ | 25 | 6.3.0+ |

请安装与 Minecraft 版本和加载器对应的 JAR。**26.x 构建版本号为 `1.0.11-alpha.8`**，功能尚未完全对齐旧版；原生声明式页面、富文本、全局活动等仍在移植中。

Paper／Spigot 桥接是**独立插件**，目前不支持通过桥接打开真实物品容器页面。详见 [26.x 迁移缺口](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/NEOFORGE_26_GALLERY_GAPS.md)与[桥接指南](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/paper-spigot-bridge.md)。

</details>

📚 [API 文档](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/API_REFERENCE.md) · [文字可读性](https://github.com/dishanhai/SH-Better-UI/blob/main/docs/TEXT_READABILITY_PHASE_4_5.md) · [项目主页](https://github.com/dishanhai/SH-Better-UI)
