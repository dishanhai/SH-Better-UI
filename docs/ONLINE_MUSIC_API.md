<!-- 作者：山海／dishanhai -->
# 在线音乐与同步歌词

在开发者面板的「材质图库 → 在线音乐」选择网易云或 QQ 音乐，输入歌曲／歌手后搜索。选择结果开始加载，支持暂停、继续、停止、进度跳转和歌词／译文显示。空间不足时打开全屏预览；搜索结果按可用空间分页。画面暂时隐藏会暂停，关闭示例会取消任务并释放音频设备。

首版在 Forge 1.20.1、NeoForge 1.21.1／26.1.2／26.2 共享同一套 UUK 播放服务，26.x 仅替换文字绘制与滑杆状态查询。构造会话不联网、不打开设备；用户搜索或选择歌曲后才请求对应平台。服务不需要 Minecraft 服务端。

## 完整播放器页面

页面结构移植自同一上游版本的 `NCMScreen`、`NavigateBar`、`ControlsBar`、`MusicLyricsPanel` 和 `AccountManagerOverlay`：宽窗口左侧导航与音源、中间搜索及歌曲列表、底部常驻播放栏；歌词页将曲目信息与上下文歌词并排，账号页先展示两家平台卡片，再进入扫码详情。窄窗口改用顶部导航；返回搜索页保留查询、结果和分页位置。歌词行可点击跳转；试听偏移沿用会话的真实时间轴。未提供封面地址时使用音乐图标占位，不加载额外远程图片。

```java
var music = UnifiedUI.get().require(OnlineMusicService.KEY);
var view = music.view();
// 也可 music.view(music.open(customSources))；页面接管该会话的生命周期。
view.layout(previewContext); // 宿主用 PreviewContext.add 注册原生控件
view.render(uiFrame);       // 每帧在原生控件之前绘制页面内容
view.suspend();             // 隐藏时暂停、取消搜索和扫码任务
view.close();               // 离开时释放整个会话
```

`OnlineMusicView` 和纯几何 `OnlineMusicLayout` 均位于 UUK `kernel/api/music`，完整页面实现在 `kernel/internal/music/view`；图库只保留挂载适配器。最小可操作区域为 208 × 240 GUI 单位，宽度至少 520 且高度至少 264 时显示侧栏。二维码放不下时提示打开全屏预览，保持整数缩放和白色静区。搜索、歌词、账号与播放栏沿用 SHBUI 原生控件、焦点、主题和工具提示。

## UUK 接入

```java
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService;

var player = UnifiedUI.get().require(OnlineMusicService.KEY).open();
// 会话由调用方持有，亦可交给自身 module.scope().track(player)。
var search = player.search("netease", "歌曲或歌手", 1); // QQ 使用 "qq"，页码从 1 开始
search.thenAccept(results -> {
    // 回调在后台运行；通过宿主自己的 UI 执行器更新结果控件。
    // 用户选择某项后调用 player.play(results.get(index))。
});
var snapshot = player.snapshot(); // 同一帧只读一次
player.pause();
player.resume();
player.seek(30_000);
player.stop();
// 离开实际消费功能时：
player.close();
```

`open(List<OnlineMusicSource>)` 可使用接入方提供的音源。音源只负责搜索、取得被平台授权的 MP3 地址和歌词；网络／解析方法运行在后台。会话隔离播放、搜索、凭据和歌词。每个会话同时只播放一首歌，消费者应避免无意同时开启多个播放器。

目前将音频完整载入有界内存后播放，单首上限 32 MiB、下载总时限 60 秒、连接／读取等待 8 秒。MP3 使用独立 Java 线程解码，Java Sound 输出；无需 FFmpeg、Skija 或额外原生库。跳转从已载入 MP3 重新解码至目标位置，长曲跳转存在准备时间。搜索取消、换曲、停止和关闭都会使旧任务失效；底层套接字最迟在读取超时后释放。原始音频不持久化，不上传。

## 账号与可播放范围

默认以游客访问公开接口。平台仍会按地区、版权、付费权益和登录状态决定是否返回音频；没有音频时显示不可播放，不会跨平台替换成其他录音。网易云返回试听片段时显示「试听」，歌词使用片段的真实偏移；QQ 取流明确要求登录时显示需登录。

图库的「账号」打开双平台账号卡片，选择平台后进入扫码详情。网易云使用网易云音乐 App 扫码；QQ 使用手机 QQ 扫码并确认 QQ 音乐授权。页面区分获取中、等待扫码、等待手机确认、登录成功、过期和失败，并提供刷新、返回和退出登录。切换页面保留播放栏；登录成功应用凭据后停止旧播放，返回并重新选择歌曲。网易云二维码在本地编码；QQ 图片直接取自登录端点，不调用第三方二维码服务。

```java
var login = player.login("netease"); // QQ 使用 "qq"；替换本会话上一次扫码尝试
var state = login.snapshot(); // state.state() 与不可变的单色 state.image()
// 每帧读取快照并绘制二维码；凭据不会出现在快照中。
// CONFIRMED 表示凭据已交给当前 player，后续搜索和取流自动使用。
boolean hasCredentials = player.authenticated("netease");
login.close(); // 取消未完成尝试；已成功的登录保持到显式退出或 player.close()
player.credentials("netease", ""); // 退出该平台，不影响另一个平台
```

登录任务每 2 秒轮询一次，最长 3 分钟；终态自动停轮询并清除二维码和临时授权 Cookie。刷新、返回、隐藏／重布局或关闭示例时取消旧尝试，迟到结果不能提交凭据。每个播放器只使用一个登录工作线程，连续刷新会移除已取消的排队任务；若旧套接字尚未释放，新尝试在其结束后开始。底层连接／读取等待上限各 8 秒，取消后不再发起下一轮。QQ 在手机确认后继续完成 `check_sig → OAuth → QQLogin` 换票，授权跳转使用 HTTPS 域名白名单与随机 `state` 校验；Cookie 按域名和路径隔离，每次尝试独立。自定义音源可实现 `OnlineMusicSource.qrLogin()`，阻塞操作始终运行在登录线程；`QrChallenge.close()` 必须可与请求并发执行。

「导入 Cookie」保留为备用入口，只有点击该按钮才读取剪贴板。QQ 识别 `uin`／`qqmusic_uin` 与 `qm_keyst`／`qqmusic_key`；网易云使用该平台完整 Cookie。宿主也可以调用 `player.credentials(sourceId, cookie)`。凭据仅保留在会话内，不写配置、不记录日志、不传给音频 CDN 或另一个音源；空字符串或空剪贴板清除。成功扫码或导入凭据会停止当前播放。`authenticated()` 只表示本会话收到了凭据，平台仍可能撤销或使其失效。不要把真实凭据写进脚本或代码仓库。

目前不包含凭据续期、私人歌单、无损／AAC／OGG、跨源换曲或自动连续队列。账号凭据的实际有效性和会员权益仍由平台判定。

## 歌词

网易云优先使用实际返回的 YRC 逐字时间；没有 YRC 时使用 LRC。QQ 使用逐行 LRC 与译文，保留平台提供的时序。无歌词或纯音乐不影响音频播放。换曲时清空旧歌词，迟到结果不得附着到新曲目。

`player.lyricFeed()` 兼容既有 `LyricFeed`。Forge 1.20.1／NeoForge 1.21.1 消费者可以直接接入原有逐字渲染器：

```java
var renderer = ShBetterUiApi.lyrics().renderer(player.lyricFeed());
ShBetterUiApi.lyrics().draw(renderer, uiFrame, font, lyricBounds);
```

完整页面使用共享歌词时间轴显示当前行、前后歌词和译文；单词开始时使用主题强调色。26.x 复用相同时间轴并通过原生文字提取管线绘制。普通 LRC 不伪造逐字数据。

## 来源与验证

协议与 MP3 解码源码来自 [MuoniumPlayer-multiversion](https://github.com/zihe114514/MuoniumPlayer-multiversion/tree/9b4cc7e7bd143315da716075a6d024a0d4c0c81b)。移植的协议代码使用 Apache-2.0，JavaLayer 解码部分保留 LGPL，并将包名重定位以避免与原模组冲突。本地二维码编码器使用 [Project Nayuki QR Code generator](https://github.com/nayuki/QR-Code-generator/tree/bcfe1b018cec5f1f91b5705eca5bb4cdd34e9f8d)，保留 MIT 许可。完整许可、来源和修改说明打包在 `META-INF/third-party/{muonium-player,jlayer,qrcodegen}/`；对应源码随源码 JAR 提供。

常规测试覆盖平台结果解析、试听时长、HTTPS 与凭据输入校验、YRC 时间轴、MP3 静音帧解码、取消、暂停、跳转、关闭和迟到任务。显式设置环境变量 `SHBUI_MUSIC_LIVE=1` 后，`OnlineMusicLiveTest` 会请求两家平台的公开搜索／歌词，下载并解码实际获得授权的 MP3；不打开音频设备，不使用真实登录凭据。实网接口与权限会随平台变化，不能用构建通过代替客户端或账号验收。

新增登录测试覆盖两家状态码、Cookie 域名／路径与临时会话隔离、QQ callback/hash33、OAuth 跳转换票、登录凭据交接、取消后迟到确认、刷新替换、超时和关闭清理。`QrLoginLiveTest` 使用同一实网开关，仅获取未扫描的二维码并验证等待状态，不输出二维码、key 或 Cookie。手机实际扫码、账号确认、权益取流与游戏内二维码可读性需要本人在客户端验收。

2026-11-16 页面移植验证结果（本次未启用实网探针）：

| 平台 | 完整构建与测试 |
| --- | --- |
| Forge 1.20.1 | 完整构建通过；1162 项常规测试、504 项内核测试通过；跳过未启用的实网探针 3 项 |
| NeoForge 1.21.1 | 完整构建通过；1174 项常规测试、504 项内核测试通过；跳过未启用的实网探针 3 项 |
| NeoForge 26.1.2 | 完整构建通过；191 项常规测试、504 项内核测试通过；跳过未启用的实网探针 3 项 |
| NeoForge 26.2 | 本次 `compileJava` 复核仍被既有资源选取器的 11 处 `Minecraft.screen`／`setScreen` 适配错误阻塞 |

三份新发布 JAR 均已检查 UUK 服务、会话、MP3 解码器、图库、双平台扫码实现与许可条目，本次完整构建的发布包完整性检查通过。此前同日实网探针取得并解码网易云 MP3；QQ 搜索和歌词请求成功，当时首个测试结果未获游客音频地址。扫码测试共 15 项，其中 13 项确定性测试通过，2 项实网探针此前均验证二维码获取与待扫码轮询。

页面新增 2 项几何测试，覆盖最小区域、侧栏断点、短窗口、分页与播放栏不重叠。26.1.2 已在隔离客户端执行 `runWidgetSmoke -PwidgetSmokeOnlineMusic`（使用 `gradle/neoforge-26-widgets-smoke.init.gradle`）：固定曲目／歌词／二维码图案，不联网、不打开音频设备；验证原生 Enter 搜索、页面返回保留草稿、账号卡片进入扫码详情、窄窗口、明暗主题和关闭释放。测试输出 `SHBUI_ONLINE_MUSIC_LAYOUT_PASS`，结束后实例退出。六张截图位于 `neoforge-26.1.2/build/widget-smoke/1.0.11-alpha.8/default-online-music/online-music-{search,lyrics,accounts,qr,light,compact}.png`，完成凭证为同目录 `online-music-layout.txt`。二维码截图是不可登录的测试图案。

尚未在新页面完成本人手机扫码确认后的真实账号、实际扬声器、各平台 GUI 缩放／旁白／高对比验收；未更新任何正式游戏实例。客户端验收和后续范围见根目录 `TODO.md`。
