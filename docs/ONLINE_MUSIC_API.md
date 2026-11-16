<!-- 作者：山海／dishanhai -->
# 在线音乐与同步歌词

在开发者面板的「材质图库 → 在线音乐」选择网易云或 QQ 音乐，输入歌曲／歌手后搜索。选择结果开始加载，支持暂停、继续、停止、进度跳转和歌词／译文显示。空间不足时打开全屏预览；搜索结果按可用空间分页。画面暂时隐藏会暂停，关闭示例会取消任务并释放音频设备。

首版在 Forge 1.20.1、NeoForge 1.21.1／26.1.2／26.2 共享同一套 UUK 播放服务，26.x 仅替换文字绘制与滑杆状态查询。构造会话不联网、不打开设备；用户搜索或选择歌曲后才请求对应平台。服务不需要 Minecraft 服务端。

## 完整播放器页面

扫码详情页的状态与说明按实际字体行高分开布局，并在窄布局中为状态行预留空间。2026-11-16 已用 26.1.2 隔离客户端验证 GUI 缩放 2／3：两行之间的留白像素断言先复现旧版失败，再验证修复通过。执行截图测试时可加 `-PwidgetSmokeOnlineMusicScale=3`；默认仍为 2，缩放 3 的截图另存到 `default-online-music-scale-3` 配置目录。

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

## 账号前端与失败恢复

账号总览区分本次登录与保存登录凭证的状态，已登录账号可直接进入歌单或喜欢列表。账号详情的保存选项可在登录前或后切换；歌单、喜欢和账号资料重试入口不依赖扫码成功后的自动跳转。账号资料加载失败不会自动开启新二维码，也不会把网络失败误报为凭据过期。

喜欢列表与本次成功写入的结果才可作为已确认的喜欢状态；未知歌曲不显示为「未喜欢」。切换平台、退出账号或离开详情会取消不再相关的请求，迟到结果不得覆盖新页面。保存登录凭证、歌单读取、喜欢写入均是用户主动操作，不在构造播放器时自动发送请求。

同一 JVM 中，保存新账号会使旧会话的磁盘写租约失效；旧会话仍可保持内存登录，但不能覆盖或因过期删除另一个会话新保存的账号。恢复保存凭证只执行一次，且不覆盖用户已经显式设置的会话登录。此机制仍不提供跨游戏进程的同步，磁盘加密方式与同用户访问限制不变。

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

「导入 Cookie」保留为备用入口，只有点击该按钮才读取剪贴板。QQ 识别 `uin`／`qqmusic_uin` 与 `qm_keyst`／`qqmusic_key`；网易云使用该平台完整 Cookie。宿主也可以调用 `player.credentials(sourceId, cookie)`。凭据默认只保留在会话内，不记录日志、不传给音频 CDN 或另一个音源；空字符串或空剪贴板退出该平台并删除已保存文件。成功扫码或导入凭据会停止当前播放。`authenticated()` 只表示本会话收到了凭据，平台仍可能撤销或使其失效。不要把真实凭据写进脚本或代码仓库。

账号详情先选择「保存登录凭证」，再点击获取二维码或导入；也可登录后开启保存。默认关闭。开启后将各音源的 Cookie 分别加密保存到实例工作目录的 `config/sh_better_ui/music-accounts/`，下次打开播放器仅从磁盘恢复，不自动联网校验。Windows 使用当前系统用户的 DPAPI；支持 POSIX 权限的平台使用 AES-GCM 和仅当前用户可读写的本地随机密钥。同一系统用户下运行的程序仍可能读取凭据，不应把该目录分享给别人。关闭保存会删除该音源的文件，当前会话继续登录；关闭播放器只清除内存凭据，保留用户选择保存的文件。文件写入失败不会伪装保存成功；损坏一个平台的文件不会阻止另一平台恢复。JVM 内的撤销版本阻止其他旧会话重新写入已关闭保存的凭据，不提供跨游戏进程的偏好同步。

```java
player.rememberCredentials("netease", true); // 显式选择后再扫码或导入
boolean savedByChoice = player.remembersCredentials("netease");
var account = player.account("netease");
var lists = player.playlists("netease", 1);
lists.thenAccept(items -> {
    if (!items.isEmpty()) player.playlist(items.get(0), 1).thenAccept(tracks -> { /* 更新列表 */ });
});
var liked = player.liked("netease", 1); // QQ 使用 "qq"
if (player.supportsLikes(selectedTrack.source())) player.like(selectedTrack, true);
player.like(selectedTrack, false); // 取消喜欢；失败通过 future 传回
player.rememberCredentials("netease", false); // 删除已保存文件，保留本次登录
```

账号、歌单和喜欢列表均为后台操作，一页二十项，从 1 开始。页面在账号详情提供歌单和喜欢列表入口，歌单可继续进入曲目并分页播放，返回保留歌单页码和位置；曲目行可执行喜欢或取消喜欢。网易云与 QQ 均实现喜欢写入，`supportsLikes("qq")` 返回 `true`，页面沿用现有能力判断启用操作。明确的登录失效响应会清理当前凭据和保存副本；其他网络或业务失败不当作退出登录。新旧账号请求用凭据快照隔离，切换账号取消旧结果；已经到达平台的写请求不能保证撤销。

QQ 的 `like(track, target)` 先读取 `IsSongFanByMid`，已处于目标状态时不写入；否则按 MID 取详情，核实数字歌曲 ID 与类型后，仅向固定的「我喜欢」201 目录发送 `AddSonglist`／`DelSonglist`。写入采用同一份 JSON 的 zzc 签名与 ag-1（随机 12 字节 IV、AES-128-GCM）编码，固定请求 `https://u6.y.qq.com/cgi-bin/musics.fcg`，禁止重定向、自动重发及明文回退。HTTP、外层、业务块和 `retCode` 均成功，且再次读回目标状态后 future 才成功完成；超时或读回不一致返回失败，平台可能已经接受写入，可重新读取喜欢列表确认。`2000`（签名要求）和业务拒绝不作为账号失效。凭据快照共享可中断的写锁，避免同一播放器的相反操作交错；取消后不再进入下一阶段。

协议按 MIT 授权的 [qmweb-sign 固定版本](https://github.com/jixunmoe/qmweb-sign/tree/15305a3cc39f80eb1646ab2bae46468231765736) 移植，使用 JDK 加密能力；发布包附完整许可与来源。确定性测试覆盖上游签名／密文向量、同文签名与加密、随机 IV、逐层响应码、身份核实、幂等、读回不一致、超时不重试、重定向拒绝和并发取消。尚未使用本人账号执行真实红心写入或客户端按钮验收。

2026-11-16 此轮 QQ 写入补齐后的最终验证：新增 `QQFavoritesTest` 的 9 项测试在三端通过；Forge 1.20.1／NeoForge 1.21.1／26.1.2 的完整 `build --offline --no-daemon` 和发布包检查通过。三端常规测试分别为 1252／1264／253 项，均无失败，各有 4 项实网测试默认跳过；内核测试各 504 项通过。三个发布 JAR 均核对包含 `QQFavorites`、`QQWebProtocol` 与 qmweb-sign 完整许可、固定来源。本轮未启动客户端、未使用真实账号、未部署；26.2 沿用已有构建阻塞记录，未重复验证。

搜索框停输 300 ms 后请求建议，可通过原生按钮点击、Tab 聚焦及 Enter 选择。`player.suggest(source, query)` 合并音源在线建议与本会话已加载曲目的本地候选，最多八项。曲名、歌手和专辑复用项目现有 ICU 拼音索引，支持中文、全拼及首字母；索引按音源隔离，最多保留最近加载的五百首，退出或更换凭据时清空。搜索第一页优先保留完整远端页，以本地匹配补足空位；网络失败时已有本地匹配可作为第一页结果。拼音能力不意味着已完整索引平台曲库或账号所有未加载歌单。

自定义音源通过 `requestSnapshot()` 在请求提交时固定认证上下文；可变账号音源应返回独立快照，方法不得联网。未实现的新能力默认显式返回不支持，不影响既有搜索／播放实现。目前不包含凭据续期、无损／AAC／OGG、跨源换曲或自动连续队列。账号凭据的实际有效性和会员权益仍由平台判定。

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

2026-11-16 账号后端补齐后的最终验证：Forge `build --offline --no-daemon` 全量通过，1195 项常规测试中 1191 项通过、4 项实网测试默认跳过；NeoForge 1.21.1 的 1207 项常规测试中 1203 项通过、4 项默认跳过；26.1.2 的 224 项常规测试中 220 项通过、4 项默认跳过。三端的 504 项内核测试均通过，完整构建及发布包检查通过。三个当前 `1.0.11` 发布 JAR 均包含新增账号／歌单 API、加密存储、拼音索引与页面实现。

另以 `SHBUI_MUSIC_LIVE=1` 单独运行 `OnlineMusicSuggestionLiveTest`，网易云和 QQ 对 `zhoujielun` 均返回非空建议，1 项测试通过，无账号凭据或音频输出。确定性音乐后端测试覆盖凭据加密、大小／路径校验、单音源损坏隔离、跨会话撤销保存、失败回滚、账号切换后的旧写请求与迟到结果、失效回调重入、异步回调链、歌单解析、喜欢和拼音匹配。26.2 的 `compileJava` 再次被已有资源选取器的 11 处 `screen`／`setScreen` 兼容错误阻塞。未启动客户端、未做真实账号验收、未部署；Windows DPAPI 已实测，POSIX 加密与权限分支尚未实机验证。

### 账号前端与可靠性补充验证（2026-11-16）

本次新增 22 项离线回归：8 项 provider 合成响应、5 项凭证会话/存储、9 项实际 view 异步处理与原生布局/按钮回调。Forge 1.20.1 共 1383 项常规测试、NeoForge 1.21.1 共 1395 项，各 4 项既有条件跳过，均无失败；两端 kernelTest 各 504 项通过。两端完整 `test kernelTest build verifyLibraryJar` 通过。独立静态复审无剩余阻断。

没有使用真实凭据、登录账号、联网读取个人歌单或执行实际喜欢写入；截图中的具体服务端失败原因仍未通过真实响应确证。未进行游戏内视觉/扫码验收；本次未验证 26.x。
