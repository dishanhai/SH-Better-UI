<!-- 作者：山海／dishanhai -->
# WebGL 2 材质目录

静态站使用目录生成物和共享的检索、参数、预设契约。安装 Node.js 22 或更新版本后，从项目根目录运行以下命令。构建及本地访问均可离线完成。

```bash
node catalog/build.mjs
npm --prefix web run build
npm --prefix web run serve
```

本地服务监听 `127.0.0.1:4173`。浏览器通过 HTTP 加载 ES 模块、目录 JSON 和纹理。部署时把整个 `web/dist/` 放在同一个静态站目录，并为 `.mjs` 配置 JavaScript MIME 类型。页面支持部署在子路径，所有资源 URL 相对于页面或模块。生产页面只访问本地站点资源。

## 浏览与调参

- 输入名称、稳定 ID、画廊别名、公开 API 或视觉描述。结果展示匹配理由、能力缺口和来源。用 Loader、分类、标签、动态效果及渲染能力缩小范围。
- 51 项内建材质都可在 WebGL 2 预览。预览支持像素密度、暂停与播放、定位时间、步进 1/60 秒和调整播放速度。指针可以按固定轨迹回放，也可以在画面上移动或用方向键调节指针滑块。系统减少动效时从静态帧开始。
- 草稿 A 和 B 都来自目录默认值，参数修改只影响所选草稿。并排对比显示两个完整画面，滑动对比在同一画面用分界划分两个版本。可以查看差异、复制和交换，或恢复所选草稿的默认值。
- 可导出 JSON 文件、导入文件、粘贴 JSON，或将带名称的预设存在当前浏览器。导入完成版本和参数验证后才更新草稿。分享采用 `#umivp1` 版本化片段，包含最多 6000 字节的 UTF-8 预设和用于检测损坏的校验值。目录版本与材质版本随预设一起传递。
- 当前 Chromium 支持实测见 [验证报告](validation-report.md)。无 WebGL 2、着色器失败或纹理缺失时会显示原因。受管 Liquid Glass 另提供经实际渲染取得的静态参考帧。原生入口和参数仍可查看。

## 其余材质的 WebGL 场景

`material-effects` 适配器按稳定材质 ID 选择表面、背景、内容或动效着色程序，使用同一份时间、指针、参数和 A/B 契约。选择手动输入后，可以直接在画布按下或拖动。键盘可以用指针滑块和预览按键操作。浏览器用固定图形、字形和演示音频数据近似游戏内效果。目录的 `web-approximation` 状态表示可独立预览。与原生画面的颜色、字体、采样、音频响应和交互时序仍可分别比较。

## Liquid Glass 实现

目录的 `managed-liquid-glass` 适配器直接使用 `src/main/resources/assets/sh_better_ui/shaders/managed_glass.{vsh,fsh}`。构建把 GLSL ES 3.00 版本指令置于首行并声明 `highp` 浮点和整数精度，保留其余着色器源码。受审查的注册表只加载此仓库内的适配器模块。

网页调参界面默认使用 Snell 新折射，可在「光学、指针与对比度」里切换到原版校准。适配器的 `render` 支持 `opticalProfile: 'snell'` 和 `'snellClear'`。浏览器像素烟测覆盖通透、模糊强度与半径、斜边位移、色散和 Clear 平坦中心。宽模糊在同一场景纹理生成 mip 层级，捕获、mip 层级和并排对比目标共同受 32 MiB 预算约束。网页产物在 `third-party/liquid-glass-optics/` 附带上游署名和 MIT 许可。

背景通过 `RGBA8` framebuffer 写入纹理，使用线性采样和边缘钳位。场景颜色按 sRGB 编码，着色器内部对前景对比计算进行线性化。合成采用与原生玻璃相同的 `SRC_ALPHA, ONE_MINUS_SRC_ALPHA` RGB 因子和 `ONE, ONE_MINUS_SRC_ALPHA` alpha 因子。原生玻璃的形状、厚度、折射、散射、光照、阴影和前景对比运算都由共享着色器执行。CSS 负责界面布局与前景标签。

画布逻辑尺寸取自固定夹具，实际像素尺寸再乘以 GUI scale 和显式像素密度。默认 1× 用于跨端取证。指针原点位于左上，转换为材质局部坐标后把 Y 取反传给 GLSL。滑动比较在同一个零原点 framebuffer 中按分界裁剪。并排比较复用一个零原点的额外合成目标，在合成完成后复制到画布两侧。该方式避免绝对 `gl_FragCoord` 偏移导致折射采样舍入差异。两种模式共用一个只读背景和同一时间、指针输入。

捕获和对比目标按尺寸复用，像素密度超出限制时页面报告回退到 1×。预览离开可见区域时停止推进与绘制。切换材质会取消资源读取并释放 framebuffer、纹理、程序及顶点状态。上下文恢复后重新加载 GPU 资源，参数草稿保留。

## 固定场景与验证

`catalog/fixtures/liquid-glass.json` 定义了 640×384 尺寸、sRGB、GUI scale、DPI、种子、材质版本、关键帧、形状、指针和移动方块轨迹。背景 PNG 包含高对比位图字、彩色渐变、棋盘格、高频纹理、明暗交界、半透明叠层和像素物品栏。输入和时间不依赖操作系统字体或墙上时钟。第二个 Web 场景放大同一素材，跨端指标以综合校准模式为基准。

```bash
npm --prefix web test
npm --prefix web run build
npm --prefix web run test:browser
node web/tests/compare.mjs build/material-catalog/web/liquid-glass-0.png path/to/native-crop.png
```

浏览器测试启动隔离的本机 Chromium，默认使用 ANGLE SwiftShader。可用 `CHROME_PATH` 指向已安装的浏览器，或设置 `UMIVP_HARDWARE_GL=1` 尝试默认图形后端，并将证据单独写入 `build/material-catalog/web-hardware/`。测试写入像素读回、关键帧 PNG、桌面和移动截图、加载与编译耗时、CPU 提交时间和可用的 GPU timer query 结果。`compare.mjs` 对齐同尺寸的 sRGB PNG，计算差异像素、RGB MAE、RMSE、8×8 窗口 SSIM、Alpha MAE、相邻像素的边缘误差，以及最大 RGB 通道和 Alpha 误差。比较同一夹具时使用无外层画廊界面、无浏览器前景标签的原始场景裁剪。

目录标记为 `web-approximation`。Windows Chromium 已在 ANGLE SwiftShader 和 RTX 4060 D3D11 后端分别验证动态光学参数响应和同浏览器重复一致性。固定夹具 0 ms 的 Forge 裁剪与这两个后端的最大 RGB 通道差均为 1，Alpha 一致。游戏 framebuffer 捕获、GPU 浮点精度、颜色管理、文字栅格化和视口缩放均会影响跨端比较，完整原生证据以验证报告为准。Linux、macOS 及其他浏览器需要分别运行验证。

## 构建期扩展

构建接受一个或多个 `--extension` 参数。

```bash
npm --prefix web run build -- --extension /path/to/native-only-bundle
```

扩展目录在构建时经 Schema、版本、命名空间、相对路径和资源哈希校验。当前扩展目录只登记本地数据及原生入口，WebGL 适配器由本仓库受审查的静态注册表提供。
