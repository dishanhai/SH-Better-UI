<!-- Author: dishanhai -->
# 富文本图标与效果

入口：开发者面板 → 控件图库 →「富文本与内联图标」。材质图库的「图标着色器」也展示全部十种着色效果。预览提供原生按钮与 Tooltip、本地 PNG、物品、混合样式、换行和单行省略；按钮可用鼠标或键盘切换物品。

仅 `rich:` 命名空间触发内联解析，`<item>` 保持原文。物品格式为 `<rich:item=minecraft:diamond>` 或 `<rich:item>minecraft:diamond</rich:item>`。内置 `<rich:vmaterial-shop>` 使用 `textures/gui/rich/material-shop.png`，原图来自项目的 `web/dist/fixtures/material-shop.png`。

效果通过逗号修饰符选择，例如 `<rich:vmaterial-shop,rndr:energy_wave>`。支持 `gradient_sweep`、`color_cycle`、`energy_wave`、`pulse`、`hologram`、`glitch`、`scanline`、`sparkle`、`dissolve`、`outline`，以及几何动效 `spin`、`float`、`bounce`。未知别名或效果保留原文。

原生 Minecraft 物品模型支持几何动效，例如 `<rich:item=minecraft:diamond,rndr:spin>` 或 `<rich:item,rndr:spin>minecraft:diamond</rich:item>`。像素着色效果用于本地 PNG 别名；物品模型不支持的像素效果保持原标记，不假装已生效。

```java
// 在客户端初始化或 UUK 模块挂载时登记；关闭模块时 close，可交给 scope.track。
var imageRegistration = UiRichText.registerImage("my-icon", "example:textures/gui/icon.png");
var renderRegistration = UiRichIconRenderers.register("example:shop",
        new UiRichIconRenderers.Renderer(UiRichIconRenderers.Effect.HOLOGRAM, GraphicIconMotion.FLOAT));
// 本地化文本使用 <rich:my-icon,rndr:example:shop>。
```

图片和物品固定占宽 10、绘制为 9×9 GUI 单位。已 prepare 的图片快照保留资源映射，撤销注册仅影响后续解析；效果撤销后已有快照改为静态。高对比使用原图，减少动效停止几何动画并冻结着色器相位。只读取本地资源，图片注册不发起网络请求。26.1.2 的图标材质管线已从图库抽到 `client.gui.display.MaterialEffects`，供图库与原生富文本共用；26.2 平台覆盖同步保留。

Forge 1.20.1／NeoForge 1.21.1 在公开 `Font.drawInBatch` 入口绘制内联对象，覆盖序列、组件和字符串调用，避免字体引擎替换私有 `drawInternal` 后只留下空白占位。已用 Modern UI 3.12.0.1 的 Forge 客户端复现并检查修复后的图库；原生 GUI、直接序列、原始组件、字符串、预处理组件、缩放裁切及手动参照共七条绘制路径通过像素检查，无须关闭现代字体。相关替换路径见 [Modern UI 字体实现](https://github.com/BloCamLimb/ModernUI-MC/blob/1.20.1/common/src/main/java/icyllis/modernui/mc/text/mixin/MixinFontRenderer.java)。

26.x 主菜单尚未收到世界注册数据时，原生物品组件不可用，使用 UUK 通用物品图标占位；组件就绪后自动绘制对应物品模型。PNG 及其着色效果在主菜单也可用。

## 网络参考与实现边界

- [The Book of Shaders: Random](https://thebookofshaders.com/10/)：网格内确定性随机数的思路，用于闪烁与故障条带。
- [The Book of Shaders: Noise](https://thebookofshaders.com/11/)：平滑阈值与插值的思路。
- [GL Transitions: RandomSquares](https://github.com/gl-transitions/gl-transitions/blob/master/transitions/RandomSquares.glsl)（MIT）：网格溶解的效果参考；本实现使用单张 RGBA 纹理、循环阈值及独立哈希函数。
- [Khronos GLSL 规范](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.1.20.pdf)：`fract`、`smoothstep` 等语言函数。

新增 GLSL 为本项目编写，未复制上述示例源文件或引入依赖。六种新增效果共用 `shaders/include/rich_icon_effects.glsl`。除描边在图标盒内扩展相邻 Alpha 外，效果不会将透明区域变成不透明背景；溶解只降低原 Alpha。

客户端视觉验收与资源重载、不同缩放、键盘及旁白检查见项目 TODO；构建或离线 GLSL 编译不能替代游戏内验收。
