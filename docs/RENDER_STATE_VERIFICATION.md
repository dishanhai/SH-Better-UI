<!-- Author: dishanhai -->
# 材质预览渲染状态回归

Forge 1.20.1 和 NeoForge 1.21.1 共用 UUK `GuiRenderBindings`，由 `GuiStateGuard` 组合原有深度、颜色写入和混合状态恢复。背景注册表入口在切换、绘制和提交后恢复宿主；贴图与径向渐变入口同样独立保护。黑洞使用私有颜色目标，离屏绘制完成后恢复宿主写目标，创建和销毁目标时保留宿主帧缓冲。

绑定作用域恢复读写帧缓冲、视口、裁切、清屏颜色、着色颜色、GUI／overlay／lightmap 三个纹理单元及逻辑纹理槽、原活动纹理单元、采样器、像素上传缓冲和顶点绑定。恢复主目标时通过原生 `bindWrite` 通知可选渲染管线，再恢复独立的读目标。

Forge 1.20.1 的 `position_tex` 着色器应用时可能覆盖 HUD 预先设置的混合参数。Forge 专用 Mixin 保留暗角乘色和准心反色。准心贴图中的黑色区域按反色公式保留下方世界像素，白色区域反转世界颜色。

从项目根目录经 Git Bash 调用现有 Windows wrapper：

```bash
MSYS2_ARG_CONV_EXCL='*' cmd.exe /d /c gradlew.bat --offline --no-daemon -I gradle/render-state-smoke.init.gradle renderStateSmoke
MSYS2_ARG_CONV_EXCL='*' cmd.exe /d /c gradlew.bat --offline --no-daemon -I gradle/render-state-smoke.init.gradle runClient
```

- `renderStateSmoke` 创建隐藏 OpenGL 窗口，验证纹理创建、尺寸变化、嵌套作用域、提前返回与异常清理后的状态相等，退出时释放窗口和纹理。
- `runClient` 使用 `build/render-state-smoke/client` 隔离配置，逐个绘制全部注册背景的两个尺寸，并验证贴图模糊与预览区外的像素。另外验证原版暗角乘色、黑洞合成后的准心反色和外围像素、模拟资源重载的目标释放，并注入黑洞写目标失效以覆盖异常路径。客户端自行退出，结果位于 `build/render-state-smoke/client-result`。
- 冒烟源码只通过 init script 加入，不参与正常发行构建。运行客户端冒烟后应重新运行普通 `build` 再使用发行产物。

原生回归修复前可复现活动纹理单元、纹理绑定和清屏颜色泄漏。自动化状态／像素验证不能代替实际整合包中返回世界、切换光影和资源重载的验收，后续项目见 `TODO.md`。

2026-11-17 验证：RTX 4060 原生回归通过；Forge 隔离客户端 21 种背景、两个预览尺寸、贴图模糊、范围外像素及黑洞直接绘制的采样异常清理通过。Forge 常规测试 1672 项、NeoForge 1.21.1 常规测试 1684 项，各跳过 5 项，失败与错误均为 0；共享内核 535 项无失败。两平台 `build` 成功，发行 JAR 已核查包含绑定作用域且未包含冒烟夹具。
