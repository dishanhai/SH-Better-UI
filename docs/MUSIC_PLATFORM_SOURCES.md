<!-- Author: dishanhai -->
# 官方平台音源接入边界（2026-11-17）

这项工作与 Apple Music **视觉风格**不同。像 Apple Music 的播放器，不代表拥有 Apple Music 音源播放权限。

## 当前交付

新增公共 `kernel.api.music.platform.AppleMusicCatalog`：

- Apple Music 官方 catalog 搜索，显式 storefront、分页、歌名、作者、专辑、时长、封面和 Apple Music 归属链接。
- 调用方提供短期 developer token 的 Supplier。每次请求重新取 token；不保存、不打印，不接收签名私钥。
- 固定 Apple API HTTPS origin；禁止重定向转发 Authorization；请求超时、响应上限、取消检查。
- 返回独立 `Song` 元数据，不冒充 `OnlineTrack` 或可播放 MP3，不把官方预览当完整歌曲。
- 不自动注册进现有音源下拉框。没有 MusicKit 播放后端前，不能给玩家一个看似可播放但实际失败的源。
- 本次没有新增账号登录、歌单、完整播放或歌词获取。没有凭证，未进行真实 Apple API 网络验收。

```java
// Run on a worker, never the render thread. Obtain the token from your authorized
// integration; do not embed a private signing key in a JAR or commit it to Git.
var catalog = new AppleMusicCatalog("tw", tokenSupplier);
var firstPage = catalog.search("artist or song", 0, 20);
// Preserve song.appleMusicUrl() and platform attribution wherever metadata is displayed.
```

## 平台状态

| 平台 | 官方路径 | 当前缺口 |
| --- | --- | --- |
| Apple Music | MusicKit：Apple 平台、Android、Web；Apple Music API 提供目录和用户库 | 原生 Java MP3 播放器不是 MusicKit。需获授权的 developer token、MusicKit 可用的播放宿主、用户登录/订阅检查、生命周期与事件桥接。目录客户端已实现，但未接 UI/播放。 |
| Spotify | Web API + 官方 Web Playback SDK；完整流通常需要 Premium | 搜索栏提供 **Spotify ↗**，把当前关键词交给 Spotify 官方网页播放器；不接入 Spotify Platform，不读取账号、歌单或流。在游戏内接入 Web API 或 SDK 前，需先明确取得 Spotify 对该用途的许可。 |
| 酷狗 | 酷狗曲库开放计划及官方播放组件/合作接口 | 公开开放组件面向移动 App，当前未取得适用于 Java 桌面游戏的接口协议、SDK 和授权。需确认可用合作接入，不把未公开签名接口作为可长期维护的承诺。 |

现有 `OnlineMusicSource.resolve()` 的契约只允许平台授予的 MP3 流。这三家完整播放不应通过返回网页 URL、HLS manifest、受 DRM 保护地址或预览 URL 来假装满足该契约。

## Spotify 网页入口

在音乐页「搜索」栏输入关键词后按 **Spotify ↗**，Minecraft 会按玩家的网页链接设置处理确认并在系统浏览器打开 Spotify 官方搜索。空白时打开 Spotify 搜索首页；在浏览器完成登录、选曲和播放。这个入口独立于现有网易云与 QQ 搜索，不切换游戏内音源、不操作当前播放，也不把 Spotify 搜索结果导入游戏。如果开启了网页链接确认，返回后会保留搜索内容；尚未完成的原生请求沿用现有后台处理。

未接入 Spotify 的 OAuth、歌单、Spotify Connect、Web Playback SDK、音效或游戏内串流。根据 [Spotify 合规说明](https://developer.spotify.com/compliance-tips)，Spotify Platform 禁止用于游戏功能；这里仅跳转公共网页，不调用开发者接口。

## 下一阶段验收

1. 先确定获平台许可且适用于 Minecraft 桌面的接入方式，再实现独立的授权播放器驱动，不修改 MP3 decoder 来绕过 DRM。
2. 用户明确触发授权；凭证保存仍为可选。签名私钥只在可信服务端，不能打包进客户端。
3. 能力分别声明目录、播放、预览、控制外部播放器、用户歌单和歌词；UI 不显示无效按钮。
4. 真实账号验收必须覆盖取消登录、token 过期、无订阅、地区不可用、切歌/暂停/seek、关闭页面与重连。网络未跑不能称全功能完成。
5. Spotify 许可与酷狗接口资料未取得前，不宣传“已支持三平台音源”。

## 核对的官方资料

- Apple MusicKit: https://developer.apple.com/musickit/
- Apple Music API: https://developer.apple.com/documentation/applemusicapi
- Apple 请求/响应契约: https://developer.apple.com/documentation/applemusicapi/handling-requests-and-responses
- Apple MusicKit Web 用户授权: https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?id=user-authorization--page
- Spotify policy, III.2 / III.5 / III.6: https://developer.spotify.com/policy
- Spotify Web Playback SDK: https://developer.spotify.com/documentation/web-playback-sdk/tutorials/getting-started
- 酷狗开放平台: https://open.kugou.com/
- 酷狗曲库开放计划: https://www.kugou.com/musiclibrary_openplan/
- 酷狗播放组件隐私/平台说明: https://open.kugou.com/docs/mini-player/player_sdk_privacy.html

以上是本次实现的技术与平台限制说明，不代表平台对该模组的许可或法律意见。

## 本次验证

- Forge 1.20.1 编译及 `AppleMusicCatalogTest` 8/8 通过。
- NeoForge 1.21.1 编译及同一测试 8/8 通过；使用既有离线缓存与 binary-artifact 初始化配置。第一次普通 NeoForm 源码反编译步骤失败，改用项目已使用的 binary-artifact 验证路径后通过，非产品源码修补。
- 测试包含请求编码、分页边界、凭证缺失/头注入、每请求 token 更新、响应解析、限制与去重、官方归属链接及不可信链接拒绝。
- 没有执行带真实 token 的网络请求、真实订阅播放、账号授权或游戏内新平台 UI 验收。本次仅后端元数据层，无新增玩家可见音源。

## 玩家使用自己的 Apple 账户（授权桥接，待真实凭证联调）

新增双版本共用 `AppleMusicBrowserLogin` 与官方 MusicKit Web 页面：

```java
// A valid short-lived project developer token is still required.
// Keep this instance owned by the page/session and close it when leaving.
var login = new AppleMusicBrowserLogin(projectDeveloperToken, configuredPort);
// On an explicit player action, open login.browserUri() in their system browser.
// When authorization is needed, the player clicks "使用 Apple 账号登录" in the browser.
// Poll login.state() for UI feedback only, not subscription/playback entitlement.
```

- 只绑定 `127.0.0.1`，10 分钟到期关闭。随机会话值放 URL fragment，页面读取后立刻从地址栏移除。
- 使用 Apple 托管的 MusicKit v3，点击后调用官方 `authorize()`。Apple 自己的窗口处理账号、密码、验证码与授权。不是普通 Sign in with Apple OAuth，也不是模组自己实现 Apple 登录表单。
- Music User Token 由 MusicKit 浏览器会话管理。Java 回调只接受固定状态及授权时可选的双字母 storefront 代码。
- 凭据配置端点需要随机会话头，严格验证 Host；状态回调还验证 Origin。拒绝重定向、外部回调地址、查询参数，响应 no-store。
- 重复点击不会打开多个授权；取消后拒绝迟到成功，并尝试官方 `unauthorize()`；断开也调用 `unauthorize()`。撤销失败明确显示失败，不冒称已退出。
- 关闭 Java 桥接或浏览器页面不等于撤销 Apple 授权。页面明确要求使用“断开”按钮；不承诺绕过 MusicKit 自身的浏览器会话存储。
- `AUTHORIZED` 只表示网页报告 MusicKit 授权成功，不能作为播放/订阅许可、安全认证证明或 native API 的凭据。

### 仍然不能省略的项目配置

玩家自己的 Apple 账号 **不能替代项目的 Apple Developer/MusicKit 配置**。初始化仍需要项目 Media ID/key 对应签发的有效 developer JWT，私钥不能放入模组。

系统浏览器承载的是 MusicKit 官方支持的 Web 运行时，**本机回环 origin 是否被该项目 developer token 和 Apple 授权配置接受，仍需真实联调**。若项目要求固定 HTTPS origin，应改用获授权的固定宿主和其受控通信设计，不能自行去掉 Apple 的 origin 校验。仓库尚无项目 token、获许可宿主或玩家真实授权，因此本次不宣称 Apple 登录已在真实账号成功。

播放器的 Apple Music 账号页通过官方 MusicKit 浏览器伴侣授权。授权后的订阅播放由浏览器播放器承载。

官方依据：
- 初始化与 developer token：https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?path=%2Fstory%2Fget-started--page
- authorize/unauthorize：https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?id=user-authorization--page
- Web 自动管理用户 token：https://developer.apple.com/documentation/applemusicapi/user-authentication-for-musickit
- 授权账户的地区：https://developer.apple.com/documentation/applemusicapi/get-a-user%27s-storefront

游戏会按下节配置绑定固定本地端口。端口被占用时，会显示端口冲突并等待重试。Apple 官方开发者论坛 DTS 回复指出，目前文档未保证 MusicKit 接受本地 origin，Windows Chrome 和 Edge 的授权失败报告仍在调查。实际支持情况以项目令牌和真实账号联调为准。[Apple 开发者论坛](https://developer.apple.com/forums/thread/853567)

### 游戏客户端账户控件接入

在「音乐来源 → Apple Music 卡片上的钥匙 → Apple Music 账号」点击「使用 Apple 账号登录」，随后在浏览器的 Apple 官方窗口授权。游戏自动更新授权状态，并通过 MusicKit 查询个人 storefront 后显示地区。浏览器恢复已有授权时也会同步。MusicKit 管理音乐用户令牌，游戏只接收状态和地区代码。姓名、邮箱属于另一套 Apple 身份授权。玩家可在浏览器使用「断开 Apple Music」退出授权；游戏中的「关闭游戏连接」会清除本地会话和已显示的地区。

游戏内的个人歌单仍使用已有的「旧版 Cookie 登录」。官方授权后的音乐浏览和播放可在浏览器伴侣中使用。

启用项目登录服务的发布版会在点击「使用 Apple 账号登录」时向 SHBUI 申请短期应用令牌，然后打开浏览器。玩家只需在 Apple 官方弹窗授权。项目服务收到的是普通令牌请求，Apple 账号凭据和 Music User Token 由浏览器中的 MusicKit 管理。[Apple 用户授权说明](https://developer.apple.com/documentation/applemusicapi/user-authentication-for-musickit)

#### 发布方启用登录

1. 用项目的 Apple Developer 团队建立启用了 MusicKit 的 Media ID 和签名密钥。[Apple 令牌签发说明](https://developer.apple.com/documentation/applemusicapi/generating-developer-tokens)
2. 在项目服务端设置 `SHBUI_APPLE_MUSIC_TEAM_ID`、`SHBUI_APPLE_MUSIC_KEY_ID` 与 `SHBUI_APPLE_MUSIC_PRIVATE_KEY_FILE`，运行 `node web/scripts/apple-music-token-service.mjs`。服务监听服务器自身的 `127.0.0.1:17833`。通过项目的 HTTPS 反向代理公开 `GET /apple-music/token`，直接返回签名 JWT。服务签发 15 分钟令牌，`origin` 固定为播放器的 `http://127.0.0.1:17832`。
3. 将完整公开 HTTPS 接口地址写入 GitHub Actions 仓库变量 `SHBUI_APPLE_MUSIC_TOKEN_URL`。本地打包可用同名环境变量。各加载器会把地址打进资源文件，发布后客户端可自动领取和更新令牌。签名私钥保存在服务端。

#### 本地联调覆盖项

维护者也可在 Minecraft 实例的 `config/sh_better_ui-apple-music.properties` 配置预先签好的令牌进行联调。保存后点击「使用 Apple 账号登录」重新读取配置。

```properties
developerToken=<项目签发的 MusicKit JWT>
# loopbackPort=17832
```

客户端按 JVM 属性、环境变量、实例文件的优先顺序读取本地设置。JVM 属性为 `shbui.appleMusic.developerToken` 和 `shbui.appleMusic.loopbackPort`，环境变量为 `SHBUI_APPLE_MUSIC_DEVELOPER_TOKEN` 和 `SHBUI_APPLE_MUSIC_LOOPBACK_PORT`。有效的本地令牌优先。省略端口时采用令牌 `origin` 中首个 `http://127.0.0.1:<端口>`，无 `origin` 时使用 `17832`。显式端口须匹配令牌中的本地 `origin`，`0` 仅用于接受随机端口的测试令牌。

### 授权原型验证结果（历史记录）

- 最终源码 Forge 1.20.1、NeoForge 1.21.1 各 13 项 Apple 定向测试通过（目录 8 + 本机 HTTP/生命周期 5），零跳过。
- Node 状态机 7 项通过：点击内同步发起、重复点击、正常授权、resolve 但未授权、取消后迟到成功、撤销失败与敏感错误不外泄。
- 页面脚本语法检查通过。心跳检测游戏连接过期，失败不影响浏览器中手动断开。
- 未对真实 Apple 账号、token、订阅、官方弹窗与 localhost origin 做端到端成功验收；没有默认账户页入口、没有播放驱动，也没有截图冒充真实登录成功。

### 后续浏览器播放契约切片

现已在同一 companion 增加 catalog 歌曲 ID 加载、显式点击播放、暂停和秒级 seek，
详见 [Apple Music 浏览器播放契约切片](APPLE_MUSICKIT_BROWSER_PLAYBACK.md)及[浏览器目录详情与顺序播放](APPLE_MUSICKIT_CATALOG_DETAILS.md)。
这推进了浏览器控制层，但未改变以上真实凭证/origin/订阅/DRM 验收门禁；
不代表 Java MP3 音源、游戏账户入口或真实 Apple 播放已经完成。
