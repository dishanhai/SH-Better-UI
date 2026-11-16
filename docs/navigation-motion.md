# 原生导航与关键帧动画

## 本轮范围

为音乐页的侧栏/窄屏顶栏提供通用 `UiNavigationRail`，替代各自拼装的页面按钮。音源选择、搜索、登录、播放与返回路径保持原有业务逻辑。原有分页、滑块、弹簧动画与时间轴继续使用，不引入浏览器、React 或新的运行时依赖。

### 导航

通过 `controls().navigationRail(...)` 创建并向宿主注册一个控件。`Item` 包含完整名称、可选图标和启用状态，`Orientation` 支持横向/纵向布局。

- Tab 进入/离开整组；方向键与 Home/End 只改变内部焦点，Enter/Space 才激活。这样浏览标签不会意外发起网络请求或改变页面。
- 禁用项不可激活；点击后移出再释放、Escape、失焦、页面暂停可取消按下状态。
- `state().select(index)` 用于宿主静默同步，不回调业务。
- 选中状态与键盘焦点分开表达；完整名称和状态提供给 Minecraft 旁白。
- `setPreferences(...)` 更新减少动效和高对比偏好；`cancelInteraction()` 在暂停/模态遮挡时调用。页面移除时 `close()`，重新布局时关闭旧实例。

音乐页已经接入；导航激活后仍按原有行为把焦点交给搜索框或返回按钮。

### 关键帧 API

`UiTimeline` 适合离散帧播放器，`InterruptibleAnimation` 适合从当前位置与速度平滑转向。新增 `UiKeyframeAnimation` 用于明确的多段数值轨迹，不替代二者。

```java
var pulse = api.motion().keyframes(List.of(
    new UiKeyframeAnimation.Keyframe(0, 0f),
    new UiKeyframeAnimation.Keyframe(120, 1f,
        UiKeyframeAnimation.Easing.EASE_OUT_CUBIC),
    new UiKeyframeAnimation.Keyframe(300, 0f,
        UiKeyframeAnimation.Easing.SMOOTHSTEP)));
long revision = pulse.restart(nowMs);
// 每帧使用同一个单调时钟；不会创建定时器或保留回调。
float amount = pulse.sample(nowMs, preferences.reducedMotion());
if (pulse.takeCompletion(revision)) { /* 当前播放完成，只能领取一次 */ }
// 页面暂停：pulse.pause(nowMs); 恢复：pulse.resume(nowMs);
// 取消冻结当前值：pulse.cancel(nowMs); 立即结束：pulse.finish();
// 页面销毁：pulse.close();
```

NeoForge 26 的精简 API 可直接 `new UiKeyframeAnimation(frames)`。偏移从 0 开始且严格递增；值必须有限。减少动效跳到末值；重播从首值开始并使旧完成令牌失效。快速重定向请使用原有弹簧 API，而不是反复 restart。

## Web 参考与许可边界

仅参考交互/API 概念，新增 Java 实现独立编写，没有复制网页组件源码、样式、资源或 shader。

- [WAI-ARIA Tabs Pattern](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/)：手动激活与键盘导航模式。这里提供原生 Minecraft 旁白，不宣称实现浏览器 ARIA。
- [Radix Tabs](https://www.radix-ui.com/primitives/docs/components/tabs)：方向、激活模式和可组合控件设计。[上游 MIT 许可](https://github.com/radix-ui/primitives/blob/main/LICENSE)，未引入源码。
- [Motion animate](https://motion.dev/docs/animate)：关键帧与播放控制的 API 思路。[上游 MIT 许可](https://github.com/motiondivision/motion/blob/main/LICENSE.md)，未引入源码或 Motion+ 付费资源。

## 客户端验收

自动化测试不能代替 Minecraft 旁白和实际 GPU/GUI 缩放验证。发布前检查音乐页宽/窄布局、长本地化文本、纯键盘、禁用项、连续切换、拖出释放、Esc、窗口失焦、页面重建、高对比和减少动效。确认搜索查询、歌词、账号返回流程与播放状态未被导航控件改变。

## 本轮自动验证（2026-11-16）

- Forge 1.20.1：完整 `test kernelTest build` 及 `verifyLibraryJar` 通过；测试 1183 项（3 项在线音乐实测按原有条件跳过），kernel 504 项。
- NeoForge 1.21.1：完整 `test kernelTest build` 及 `verifyLibraryJar` 通过；测试 1195 项（同样跳过 3 项），kernel 504 项，脚本 worker 13 项。
- 新增导航状态 7 项与关键帧 11 项测试。两份实现交叉独立审查，修复悬停旁白、极小尺寸越界和超长关键帧时长精度问题后重跑最终验证。
- NeoForge 26.1.2 已补共享源码、控件事件/渲染适配与工厂，当前环境无 Java 25，未编译或运行该平台；保持草稿待其构建及客户端验收。没有进行真实客户端视觉、旁白或 GPU 验收。
