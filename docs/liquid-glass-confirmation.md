<!-- 作者：山海／dishanhai -->
# 三版独立玻璃确认弹窗

本切片直接基于 main，独立于媒体卡/分段控件分支，复用已有确认服务和原生
光学通道，不引入浏览器、整个 Overlay 框架或新的 GPU 资源池。

```java
ShBetterUiApi.interactions().confirmGlass(parent,
        new UiConfirmation.Prompt(title, details, confirmLabel, cancelLabel),
        preferences, this::drawInertContext,
        () -> currentTriggerButton, this::confirmOnce);
```

- 仅从当前父页面打开，新的原生 Screen 隔离背景输入。context 只负责绘图，
  禁止在其中转发输入或改变页面；null 使用纯色背景。
- 初始焦点为取消，Tab/Shift+Tab 在两个按钮间循环，Escape 取消。
- 恢复父页面后才查询新的触发控件，目标必须仍属于父页面且可见/可用。
  查询或恢复焦点期间切换页面时，不继续执行旧确认动作。
- 确认只能执行一次；外部切页、取消、已关闭弹窗均不能触发旧回调。
- 长文支持滚轮及 Page Up/Page Down；旁白包含完整标题和正文。
- 光学底板之后覆盖可读性遮罩，标题/正文对未知背景保持至少 4.5:1。
  玻璃模式移除文本中的自定义颜色，保留文字和强调格式，避免颜色覆盖安全
  前景；原默认确认样式不变。高对比或禁用背景采样采用纯色回退。

图库新增“玻璃确认弹窗”，只修改演示状态。26 示例显示确认次数与背景点击
次数，便于检查模态隔离；不修改文件或账号。

这不是完整嵌套弹窗、菜单、底部抽屉或任意内容焦点管理库。性能和多比例
覆盖范围以 PR 中实际测试和截图记录为准，不能把已有 Web/CSS 示例当成原生
完成度。依赖许可及 Web 设计参考沿用仓库既有研究，本片没有复制上游代码。
