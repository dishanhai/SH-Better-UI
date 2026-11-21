<!-- 作者：山海／dishanhai -->
# SHBUI 音乐播放器

右 Shift 打开 SHBUI 播放器，H 打开 HUD 设置。两者也可以从 SHBUI 主控面板进入，键位可以在 Minecraft 的控制设置中修改。首次使用时会出现欢迎引导。旧版 SHBUI 在线音乐服务继续供已有 API 调用方使用。

本次移植基于 [MuoniumPlayer 2.6.0](https://github.com/zihe114514/MuoniumPlayer-multiversion/tree/eb541684e9d681d8145f5f9d681cf31980646dc7)。在一个实例中只装对应平台的 SHBUI，以及该平台已有的必需依赖。独立 MuoniumPlayer 与内置客户端使用相同的类名、按键和网络通道。

| 代码位置 | 职责 |
| --- | --- |
| `src/muonium/java` | 播放器、音源、账号、缓冲、下载、解码、DSP、Automix、歌词、世界渲染、HUD、房间客户端和局域网房间引擎 |
| `src/uuk-muonium` | 原生控件、字体渲染与图标、目录选择器 |
| `src/kernel/java` | UUK 控件主题、触摸及字体宿主契约 |
| `src/muonium-forge` 和各 NeoForge 工程的 `src/muonium` | 对应版本的客户端生命周期、Mixin、网络事件、输入以及世界渲染适配 |

NeoForge 26.2 的 Muonium 图层需要 OpenGL。使用 Vulkan 时，游戏显示图形 API 提示。切换为 OpenGL 并重启后可进入原版 Skija 界面。

## 设置、账号与资源

沿用上游 `config/muonium` 配置和 `MuoniumPlayer` 本地数据目录。全新实例里的 LX 自定义源在玩家选择后启用。切换到汽水音源、安装模型或选择可选工具可能需要下载额外资源。正版平台账号、地区和服务可用性依各音源而定。

四个平台的 Gradle `build` 默认生成轻量发行包。安装轻量包后，下载与系统和架构匹配的 Skija 0.143.17 ZIP，保留包内目录结构并解压到游戏实例的 `config/muonium/native/skija/`。Windows x64 的 DLL 路径为 `config/muonium/native/skija/windows/x64/skija.dll`。 [Skija 伴生包](https://github.com/zihe114514/MuoniumPlayer-updates/tree/main/skija-companion)

在任一版本的 Gradle 工程运行 `buildWithSkija`，会额外在该工程的 `build/libs/` 生成 `*-with-skija.jar`，内嵌全部七套原生库。每个游戏实例只需安装对应版本的其中一个 JAR。

从主菜单、播放器侧栏或 HUD 设置打开 SHBUI 公告中心，可以查看项目发行记录、安装说明和反馈入口。

H 设置页提供“编辑 HUD”“常用”“模组”“预设”“主题”“偏好设置”和“关于”。“模组”保留歌词样式、3D 歌词、面板与卡片、灵动岛、播放、娱乐、界面与主题、信息栏与封面、模板预设、缓存与更新，并提供“界面”标签。用户可在宽屏直接调节滑杆，在窄屏滚动标签或使用键盘。设置和 HUD 编辑使用同一份配置。

“界面”和“偏好设置”提供两个独立选项，全新实例及既有配置默认关闭。

- **跟随 SHBUI 主题**让播放器和 HUD 设置读取主控面板当前的配色和玻璃样式。关闭后重新使用播放器已选的主题，包括原有自定义色。
- **使用 SHBUI 控件**采用 UUK 的开关和滑杆，并在首次引导和播放器首页提供账号、音源或本地歌单的快捷入口。关闭后恢复原控件。默认 H 页面由 SHBUI 绘制，安装 MuoniumConfig 也不会接管该入口。要打开独立应用，可关闭此项并在设置界面选择 OneConfig。外部应用会显示它自己的侧栏品牌，设置树显示 SHBUI。

两个选项只作为本机界面偏好保存。共享预设、房间协议、账号和歌单数据仍沿用 MP 格式。玩家界面使用 SHBUI 品牌，“关于”可查看上游开发团队和项目来源。

正文和图标由 UUK 提供。图标保留上游调用码位并使用 Lucide 图形。Apple 曲目的内置 PCM 路线支持换源和官方试听。在播放器底部点 Apple 歌曲链接，有合法 MusicKit 配置时会把当前歌曲带入浏览器会话，玩家在浏览器中授权、加载和播放。未配置 MusicKit 时打开官方歌曲页。详见[浏览器 MusicKit 接入](APPLE_MUSICKIT_BROWSER_PLAYBACK.md)。

用户可把自己有权使用的网易云逐字歌词放入 `config/muonium/yrc/<数字歌曲ID>.yrc`，播放器在缺少逐字时间轴时读取。

在播放器的音效工作台开启 ViPER 后，可以直接体验以下实测音效。两者均支持 44.1 和 48 kHz。

- **耳机环绕**的五档虚拟扬声器分别位于正前方左右 15°、30°、45°、60° 和 75°。两侧输入按近耳、远耳的实测响应卷积。数据来自 Bill Gardner 和 Keith Martin 的 [MIT KEMAR 测量](https://sound.media.mit.edu/resources/KEMAR.html)。把完整的左、右 F32 文件对放入 `config/muonium/viper/vhe`，即可覆盖相应档位与采样率；只放一侧或文件不可读时，该档旁路。
- **卷积器**的预设库提供 `SHBUI - Surrey Room A` 和 `SHBUI - Surrey Room B`。每个房间使用实测的四条扬声器到耳朵路径，并保留房间尾音。数据来自萨里大学 Institute of Sound Recording 的 [RealRoomBRIRs](https://github.com/IoSR-Surrey/RealRoomBRIRs)。自己的 WAV 或 IRS 放在 `config/muonium/viper/kernels` 后可通过“刷新文件”选用，同名本地文件优先。

原版个人使用卷积核仍可由用户提供。上述实测数据的来源、许可和转换说明分别收录在 JAR 的 `META-INF/third-party/mit-kemar` 和 `META-INF/third-party/surrey-brir`。上游受保护设备凭据不包含在发行包内。

完整来源、修改说明及第三方许可随 JAR 提供，位于 `META-INF/third-party/muonium-player-2.6.0`。SHBUI 既有 MIT 源码与不同许可证的第三方组件各自保留署名。

## 核验成品

运行对应平台的 Gradle `build` 后，可将发布包与固定版本的上游工作树逐类对照。以下命令检查资源、Mixin 类、ZIP 校验和及单独列出的迁移差异。

```text
python scripts/verify-muonium-jar.py neoforge-1.21.1 <发行.jar> --reference <MuoniumPlayer-2.6.0源码目录>
```

### 四端发行包验收（2026-11-21）

下表记录拆分 Skija 产物之前，同一 PR 工作树的发行构建。每端均执行了完整 Gradle 测试和 `verifyLibraryJar`。通过数包含各端的内核测试，1.21.1 另含 13 项脚本工作进程测试。跳过项计入单独一列。四端共 9,590 项通过、24 项跳过、0 项失败。浏览器 MusicKit 模块另有 10 项 Node 测试通过。

| 平台 | 发行版本 | 通过 | 跳过 | 本次本地成品 SHA-256 |
| --- | --- | ---: | ---: | --- |
| Forge 1.20.1 | 1.0.13 | 2,901 | 6 | `ad1593c390699cfd6b86f476f2cca55c0eb76057b6982a580d413c1f82a68dd3` |
| NeoForge 1.21.1 | 1.0.12 | 2,926 | 6 | `e0e0d56b0feef6c6d4a7b3bf99492238fe50d7cbaedb928cf357641659d61a12` |
| NeoForge 26.1.2 | 1.0.13-alpha.8 | 1,884 | 6 | `4de9785721d9d4dfdb12b879e87880c477391e803f2c31b8a290d2376e145e3a` |
| NeoForge 26.2 | 1.0.13-alpha.8 | 1,879 | 6 | `e230a447bedf343539991651d8ee9ce070d8410147bab0d7153e63df61b8cc38` |

逐包对照固定上游检查了类、资源、Mixin、协议源码、原始 LICENSE／NOTICE 与 ZIP CRC。每包包含 20 个耳机响应、4 个房间 WAV、2 份 Surrey 预设、KEMAR 和 Surrey 的许可声明。资源内容与源码目录逐字节一致，包内未发现隔离 QA 或测试类。

Forge 成品在全新目录安装预设，音效总开关默认关闭，脉冲输入按原样输出。启用后，两个房间在 44.1 和 48 kHz 下均产生跨耳输出，切回原采样率可恢复相同响应。两种采样率下五档 VHE 的输出均逐对可区分。离线探针退出码为 0，扬声器试听仍待进行。

## 已验证的游戏路径

- NeoForge 26.2 OpenGL 单人世界通过 H 键打开设置，Tab 到达底栏恢复按钮。弹窗焦点默认在“取消”，取消保留外观与位置，明确确认后外观恢复默认且 HUD 位置保留。色板的方向键、Enter 应用、Tab 离开及返回模块后的方向键导航均通过。

- NeoForge 1.21.1 的中文客户端经过首次欢迎页和快捷开始进入播放器。开启 SHBUI 控件并关闭主题、关闭控件并开启主题，均从世界通过 H 打开本地设置页并通过 Esc 返回。显式选择 OneConfig 时打开独立应用，UUK 中文字体能在其界面显示。
- NeoForge 26.2 的 OpenGL 客户端验证了 11 个标签、两级导航、全部侧栏入口和 640×480 窗口中的键盘选项。3D 歌词关闭时，测试点击从属参数并确认值保持不变，然后打开主开关验证可调状态。
- Forge 1.20.1 的隔离客户端先后挂载了引导、播放器及 HUD 设置，建立 Skija 帧，关闭设置后回到父屏。
- NeoForge 1.21.1、26.1.2 的隔离客户端显示了原版欢迎动画、播放器和设置页。26.1.2 复测了 3D 歌词开关圆点、下拉菜单取消和键盘焦点。26.2 在 OpenGL 世界内切换 3D 歌词，Vulkan 下显示后端提示。
- 1.21.1 以系统原生目录选择器验证了入口，再用测试目录作为选择结果走实际扫描、建单与播放流程。WAV 和带三行内嵌歌词的 FLAC 都产生非零 PCM。FLAC 暂停 25 tick 前后位置均为 521.18 ms，恢复后到 2071.84 ms；当前行从第一行切换到第二行，原文件哈希保持一致。
- SHBUI 既有在线音乐入口的四项实网测试通过。网易云匿名搜索取得可播放的 MP3 并成功解码，QQ 音乐返回搜索结果与歌词，所测第一首未获匿名音频。两平台均返回了搜索建议和二维码，并能轮询到待扫码状态。
- Forge 成品通过本机 TCP 环回联测。真实 DirectClient 完成握手与 UUID 登记，服务端拒绝错误密码。两个协议客户端建房、加入，收到相同的成员列表与播放、跳转序号和时间；一方退房后，房主收到更新后的成员列表。测试还把发送帧同固定上游版本的编码结果逐字节比较。真实账号与跨设备游戏联调列在 [TODO](../TODO.md)。
- 两个独立的 NeoForge 1.21.1 游戏进程使用各自的开发身份和存档。访客通过标准三段邀请自动加入房间，两端打开测试生成的 WAV；跳转后双方读数为 45.031 和 45.047 秒，暂停后均停在 45.093 秒，离房后恢复单人状态。此回归在本机环回网络上使用当前源码编译的修复类，房主经生产建房消息接入隔离服务器。回归后将该类与 1.21.1 发行包的 MpDirect 逐方法比对，JVM 指令一致。
