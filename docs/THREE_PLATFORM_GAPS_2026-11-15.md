<!-- 作者：山海／dishanhai 项目维护记录 -->
# 三条平台实现线的能力缺口

对比日期：2026-11-15。范围为当前工作区的 Forge 1.20.1、NeoForge 1.21.1 和 NeoForge 26.x；其中 26.x 包含 26.1.2 与 26.2 两个目标。结论包含当时尚未提交的工作区改动，不代表已发布版本的能力。

Forge 1.20.1 与 NeoForge 1.21.1 共用大部分 UI 实现；26.x 已迁移基础控件、部分内核服务和大量图库样例，但完整业务服务、消费方 API 和模组集成仍有较大缺口。NeoForge 1.21.1 另有尚未移植到其他两条实现线的自动化运行时。

## 能力总览

| 能力 | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.x |
| --- | --- | --- | --- |
| 基础控件、设置、模糊 | 已有 | 共用主体并做平台适配 | 已有一批原生实现 |
| 声明式页面、富文本工作台、停靠、活动通知 | 已接入原生运行时 | 已接入原生运行时 | 本地声明式 Screen 已有基础运行时，其余页面类型、富文本、停靠和活动通知待移植 |
| 自适应文字 | Screen、HUD 及旧版页面已接入 | Screen、HUD 及旧版页面已接入 | Screen、HUD 服务已有；声明式页面和富文本接入待补 |
| JEI、KubeJS、LDLib | 已有对应版本集成 | 已有对应版本集成 | 对应版本的完整集成与验证待补 |
| AE2 自动化运行时 | 缺少本批平台运行时 | 已有实现增量 | 缺少本批平台运行时 |
| 开发者图库 | 旧版图库 | 共用旧版图库 | 有大量对应样例，部分宿主能力和公开 API 尚未对齐 |

## NeoForge 26.x 相对旧两端的缺口

### UUK 页面与运行时

- 26.x 的 UUK 客户端发布了 `PageService.KEY`。消费模组可注册并打开本地 `SCREEN`，使用 `column`、`text`、`button`、`spacer`、字段绑定和无载荷的 `LOCAL` 动作。运行时按作用域管理注册及实例，正文随布局滚动，支持按节点 ID 恢复焦点。
- 继续补其他页面控件和表面类型、富文本、停靠窗口、全局活动与通知，以及相关可选网络。
- 26.x 已有共享内核、页面模型、Screen／HUD 基础服务和文字可读性服务。声明式页面与原生富文本控件的文字策略接入仍待补。
- 参考：[26.x 页面接入与烟测](page-schema.md#neoforge-26x-本地声明式页面)、[26.x 待办](../TODO.md)、[26.x 内核服务注册](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/kernel/internal/ClientUiKernel.java)、[文字可读性待办](../TODO.md#uuk-文字可讀性)。

### 消费方 API 和控件

- 旧版 `interactions()`、`search()`、`ingredients()`、`materials()`、`graphics()`、`layout()`、`motion()`、音乐和歌词等 API 分组，尚未在 26.x 统一入口对齐。
- 26.x 已有 `screen(Component)` 和基础 `UiScreen`，但其宿主能力比旧版少。
- 图库中的动态状态、加载、局部模态、转场、玻璃和空间效果等，部分仍缺可供外部宿主使用的 API，例如旧版 `textDetails`、状态／加载绘制、通用指针捕获、逐区域即时模糊和材质绘制。图库样例存在不等于消费模组可以使用旧接口接入。
- 参考：[旧版 API 入口](../src/main/java/com/dishanhai/sh_better_ui/client/api/ShBetterUiApi.java)、[26.x API 入口](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/ShBetterUiApi.java)、[旧版控件 API](../src/main/java/com/dishanhai/sh_better_ui/client/api/ControlsApi.java)、[26.x 控件 API](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/ControlsApi.java)。

### 图库宿主与调试工具

- 26.x 已有分类搜索、第三方预览发现、独立样例和部分代码示例。旧版的固定尺寸内嵌预览、前后切换、完整代码抽屉、覆盖层、诊断及页面工作台入口仍待补。
- 旧两端新加入的 `text_readability` 图库预览，在当前 26.x 内置样例登记中未找到对应入口；26.x 的文字可读性服务本身已经移植。
- 参考：[旧版内置预览](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/BuiltinPreviews.java)、[26.x 图库](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/DeveloperGalleryScreen.java)、[图库迁移清单](NEOFORGE_26_GALLERY_GAPS.md)。

### 音乐、歌词和活动

26.x 图库能播放本地音频、展示歌词和活动岛；系统媒体会话、系统音频回环、常驻音乐 HUD、全局悬浮歌词和全局活动调度等仍待接入。详见 [26.x 待办](../TODO.md)。

### 模组集成

旧两端已有 JEI、KubeJS 和对应版本的 LDLib 适配。26.x 尚需针对目标版本确认 API、实现并用实际消费模组验证。Forge 1.20.1 使用 LDLib 1.x，NeoForge 1.21.1 使用 LDLib2，这是依赖平台不同造成的适配差异。

参考：[项目接入说明](../README.md#neoforge-1211)、[26.x 待办](../TODO.md)。

## Forge 1.20.1 和 NeoForge 26.x 相对 1.21.1 的缺口

本批自动化运行时和第三方服务端能力发现目前只在 NeoForge 1.21.1 有平台实现，包括 AE2 适配、规则入口和独立脚本 worker。Forge 1.20.1 与 26.x 虽共享部分内核模型，尚未接通相应平台运行时。

1.21.1 的实现仍有任务重启核对与恢复、容量归档、权限策略、worker 进程总资源限制及完整实机验收待完成。参考：[自动化实现增量](../TODO.md#uuk-自动化实现增量2026-11-14)、[实现记录](UUK_AUTOMATION_IMPLEMENTATION_2026-11-14.md)。

## 旧两端相对 26.x 的接口差异

26.x 的 `controls().centeredMenu(...)` 直接公开 `UiCenteredMenu`；旧两端通过局部模态实现 GlideSelect 样例和声明式页面交互，但没有同名工厂。若需要跨版本统一消费方调用，应设计对应接口，并考虑两种宿主实现的差异。

参考：[26.x 控件工厂](../neoforge-26.1.2/src/main/java/com/dishanhai/sh_better_ui/client/api/ControlsApi.java)、[旧版 GlideSelect 样例](../src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/GlideSelectPreview.java)。

## 26.1.2 与 26.2 的平台差异

26.2 复用 26.1.2 的大部分源码，并覆盖少量渲染和平台适配。26.1.2 使用 OpenGL；26.2 支持 Vulkan 和 OpenGL，并对 NeoForge 早期加载窗口及 Vulkan 回退做了专门处理。这是目标 Minecraft 版本和图形后端的差异。

参考：[26.2 构建配置](../neoforge-26.2/build.gradle)、[26.x 构建与后端说明](../README.md#neoforge-26x-alpha)。

## 建议顺序

1. 继续扩展 26.x 声明式页面。优先补常用布局与表单控件、适配页面文字策略，随后接通模态、HUD、通知及可选网络。
2. 按实际消费需求补控件、材质、输入及统一 Screen 宿主 API，并接入可复用的 UUK 能力。
3. 补图库宿主、诊断工具和模组集成，分别验证消费模组与实际整合包。
4. 将自动化移植作为独立阶段，先解决 1.21.1 尚未闭合的恢复、权限、资源限制和验收问题。

## 核查范围

平台差距的初始盘点来自静态源码、源码集、打包配置与现有记录。本轮运行记录见 [26.x 页面接入与烟测](page-schema.md#neoforge-26x-本地声明式页面)。其余项目沿用各自文档的历史验证范围。
