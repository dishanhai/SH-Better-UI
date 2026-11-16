<!-- Author: dishanhai -->
# Dither 背景

Forge 1.20.1 和 NeoForge 1.21.1 的共享背景目录提供 `dither`。开发者图库中可打开「图像抖动」并在效果设置中调整图案、颜色、适配和动画。默认示例使用用户提供的 [Unsplash 图片](https://images.unsplash.com/photo-1493246507139-91e8fad9978e?q=80&w=2670&auto=format&fit=crop)，随包保存以支持离线预览。

调用方可通过 `appearance.backgrounds().layer(null)` 使用注册背景；视图移除时关闭 layer。默认效果由 `Dither.Options.DEMO` 定义，主题覆盖可使用现有 appearance 编辑和草稿接口。

需要其他资源图像时，每个视图持有一个 `DitherBackground`，构造时传入资源包中的 `ResourceLocation`，绘制时传入 `Dither.Options`、区域、减少动效设置和当前时间，并在移除时调用 `close()`。无 Minecraft 依赖的 `Dither.apply` 处理已经适配到目标尺寸的 ARGB 像素；`Fit` 的图像缩放由客户端背景完成。

与浏览器参考的差异：图片输入是资源包标识而非 URL；示例离线打包；使用 CPU 生成并缓存动态纹理，噪声动画最多每 50 毫秒更新；绘制分辨率最大为 2048×2048 逻辑像素；缩放采用 Java 双线性插值而非 Canvas。高对比度沿用背景层的关闭规则，减少动效时暂停动画。图片在背景实例首次绘制时读取，资源重载后需重新创建背景实例。NeoForge 26.x 尚无对应背景层实现。
