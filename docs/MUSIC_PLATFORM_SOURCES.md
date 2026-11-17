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
| Spotify | Web API + 官方 Web Playback SDK；完整流通常需要 Premium | 当前多平台 Minecraft 播放器与官方政策中游戏、其他服务内容整合限制存在冲突，需先获得 Spotify 对具体用途的许可。未创建假 provider，也未转用抓包/第三方解密接口。 |
| 酷狗 | 酷狗曲库开放计划及官方播放组件/合作接口 | 公开开放组件面向移动 App，当前未取得适用于 Java 桌面游戏的接口协议、SDK 和授权。需确认可用合作接入，不把未公开签名接口作为可长期维护的承诺。 |

现有 `OnlineMusicSource.resolve()` 的契约只允许平台授予的 MP3 流。这三家完整播放不应通过返回网页 URL、HLS manifest、受 DRM 保护地址或预览 URL 来假装满足该契约。

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
var login = new AppleMusicBrowserLogin(projectDeveloperToken);
// On an explicit player action, open login.browserUri() in their system browser.
// The browser then requires a separate click on "使用 Apple Music 登录".
// Poll login.state() for UI feedback only, not subscription/playback entitlement.
```

- 只绑定 `127.0.0.1` 的短期随机端口，10 分钟到期关闭。随机会话值放 URL fragment，页面读取后立刻从地址栏移除。
- 使用 Apple 托管的 MusicKit v3，点击后调用官方 `authorize()`。Apple 自己的窗口处理账号、密码、验证码与授权。不是普通 Sign in with Apple OAuth，也不是模组自己实现 Apple 登录表单。
- Music User Token 完全由 MusicKit 浏览器会话管理。Java 回调只允许固定状态字段，拒绝 token/其他字段。不会转存到现有 QQ/网易 cookie 仓库。
- 凭据配置端点需要随机会话头，严格验证 Host；状态回调还验证 Origin。拒绝重定向、外部回调地址、查询参数，响应 no-store。
- 重复点击不会打开多个授权；取消后拒绝迟到成功，并尝试官方 `unauthorize()`；断开也调用 `unauthorize()`。撤销失败明确显示失败，不冒称已退出。
- 关闭 Java 桥接或浏览器页面不等于撤销 Apple 授权。页面明确要求使用“断开”按钮；不承诺绕过 MusicKit 自身的浏览器会话存储。
- `AUTHORIZED` 只表示网页报告 MusicKit 授权成功，不能作为播放/订阅许可、安全认证证明或 native API 的凭据。

### 仍然不能省略的项目配置

玩家自己的 Apple 账号 **不能替代项目的 Apple Developer/MusicKit 配置**。初始化仍需要项目 Media ID/key 对应签发的有效 developer JWT，私钥不能放入模组。

系统浏览器承载的是 MusicKit 官方支持的 Web 运行时，**本机回环 origin 是否被该项目 developer token 和 Apple 授权配置接受，仍需真实联调**。若项目要求固定 HTTPS origin，应改用获授权的固定宿主和其受控通信设计，不能自行去掉 Apple 的 origin 校验。仓库尚无项目 token、获许可宿主或玩家真实授权，因此本次不宣称 Apple 登录已在真实账号成功。

目前提供公共授权 API 和可运行页面，尚未把需要配置才能工作的按钮挂到默认播放器账户页。没有浏览器播放器驱动，授权成功也不会自动让 Java MP3 后端播放 Apple Music。

官方依据：
- 初始化与 developer token：https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?path=%2Fstory%2Fget-started--page
- authorize/unauthorize：https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?id=user-authorization--page
- Web 自动管理用户 token：https://developer.apple.com/documentation/applemusicapi/user-authentication-for-musickit

Origin 受限的 developer token 可调用 `new AppleMusicBrowserLogin(token, configuredPort)` 使用已配置端口；端口被占用时失败，不悄悄更换 origin。随机端口构造器仅适合 token/授权配置明确支持该来源的测试，不建议为了让它工作而移除 origin 安全限制。Apple 官方开发者论坛 DTS 回复也指出文档没有保证 local origin 是否可接受，并有 Windows Chrome/Edge 授权失败调查，因此仍以真实配置与账号验证为门禁：https://developer.apple.com/forums/thread/853567 。

### 授权原型验证结果

- 最终源码 Forge 1.20.1、NeoForge 1.21.1 各 13 项 Apple 定向测试通过（目录 8 + 本机 HTTP/生命周期 5），零跳过。
- Node 状态机 7 项通过：点击内同步发起、重复点击、正常授权、resolve 但未授权、取消后迟到成功、撤销失败与敏感错误不外泄。
- 页面脚本语法检查通过。心跳检测游戏连接过期，失败不影响浏览器中手动断开。
- 未对真实 Apple 账号、token、订阅、官方弹窗与 localhost origin 做端到端成功验收；没有默认账户页入口、没有播放驱动，也没有截图冒充真实登录成功。
