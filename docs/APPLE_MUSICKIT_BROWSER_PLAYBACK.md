<!-- Author: dishanhai -->
# Apple Music 浏览器播放契约切片

## 范围与入口

基于已有 `AppleMusicBrowserLogin` companion，不修改 Java 音频、歌词、HUD 或原生账号页面。
项目提供有效短期 developer JWT、合规且 Apple 接受的 origin 后，显式打开
`login.browserUri()`。原有授权按钮和 `/state` 的单字段协议不变。

1. 玩家点击 Apple 官方授权按钮。授权成功只说明 SDK 浏览器会话已授权。
2. 在 companion 输入该地区可用的 **catalog song ID**，点击“加载歌曲”。
   调用 `setQueue({ song: id, startPlaying: false })`；不会自动播放。
3. 加载完成后，玩家另行点击“播放 / 继续”。`play()` 在该点击调用栈内立即调用，
   不先等待 HTTP、授权或目录请求，避免丢失用户手势。
4. “暂停”和按秒“跳转”分别调用 `pause()`、`seekToTime(seconds)`。
   显示 SDK 的播放状态、当前秒数和时长，不以 Promise resolve 或授权状态冒充实际正在播放。

声音属于浏览器 MusicKit 宿主，**不是 Minecraft/Java 完整播放接入**。加载 ID 不等于
订阅、地区或完整曲目许可通过；失败可能来自这些前提，也可能来自浏览器、DRM 或来源配置。
不启用 preview-only，不提取或转发音频 URL，不绕过 DRM，不自动打开订阅购买。

本页不读取 Music User Token，不向 Java 发送播放事件、SDK 错误对象或凭据。
SDK 错误只转换为固定失败文案。浏览器内事件监听器随 pagehide 释放；
关闭 Java bridge 不会撤销 Apple 授权，也不会主动停止浏览器播放器。播放器仍由浏览器管理。
需要停止声音时先暂停；需要撤销授权时点击原有“断开 Apple Music”。

## 与既有授权的兼容

- `auth-flow.mjs` 保持原样：重复登录、取消后迟到授权撤销、断开和错误脱敏沿用已有实现。
- HTTP 层只新增 `/playback-flow.mjs` 静态白名单，不增加 API、凭据字段、CORS 或宽松来源校验。
- 播放操作等待期间禁止重复命令；授权期间禁用播放操作。授权丢失后清空 ready，必须重新加载歌曲。
- 请求已返回与正在播放分别展示。即使 SDK 吞掉内部异常并通过事件报告，也保留可见失败提示。
- 新歌曲 ID 的输入不代表已切歌，界面另显示实际加载的 ID。

## 核对的官方 SDK 契约

- 官方文档入口：<https://developer.apple.com/musickit/web/>
- 官方 v3 SDK：<https://js-cdn.music.apple.com/musickit/v3/musickit.js>

实现时直接核对 Apple 托管的 v3 SDK：`setQueue` 支持 `song` 描述符及显式
`startPlaying: false`，不支持播放时可能返回 undefined；实例公开 `play`、`pause`、
`seekToTime`、`playbackState`、`currentPlaybackTime`、`currentPlaybackDuration`；
通过 `PlaybackStates` 映射状态，监听 `playbackStateDidChange`、`playbackTimeDidChange`、
`playbackDurationDidChange`、`authorizationStatusDidChange`、`mediaPlaybackError`。
未将 Apple SDK 下载件打包到模组；运行时仍从原有官方 CDN 加载。

## 可重复验证与限制

- `node --test web/tests/apple-music-{auth,playback}.test.mjs`：16 项通过。
- `npm --prefix web test`：24 项通过，fixture 校验通过。
- Node 语法检查：login、auth-flow、playback-flow。
- `AppleMusicBrowserLoginTest`：独立 Java 17 字节码编译、JUnit HTTP 测试，包含新资源路由与原有鉴权边界。

这些是 **SDK 替身的浏览器逻辑契约测试** 和实际本机 HTTP 测试，不是 Apple 真实播放成功证明。
没有使用真实用户、真实 token 或订阅；未执行真实 MusicKit DRM 播放、真实浏览器手势/弹窗验收，
也未执行完整 Forge/NeoForge 构建或游戏截图。

真实验收仍需：获授权的项目 developer JWT、Apple 接受的宿主 origin、支持的浏览器及 DRM、
玩家亲自授权和订阅、所在 storefront 可用的歌曲 ID。验收至少覆盖无订阅、不可用歌曲、
授权取消、实际播放/暂停/seek、断开与关闭、短期 Java bridge 到期；这些门禁通过前不得宣传完整接入。
