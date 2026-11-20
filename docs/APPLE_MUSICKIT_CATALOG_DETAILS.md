<!-- Author: dishanhai -->
# Apple Music 官网页端目录详情与顺序播放

从现有 MusicKit 伴侣页的「探索 Apple Music」搜索艺人或歌单，或打开「浏览热门歌单」。
艺人页显示 Apple 提供的热门歌曲、代表专辑和艺人精选歌单；后两者可进入相应详情。
榜单按当前 storefront 获取，与个人推荐分开。点击「浏览热门歌单」后可切换 Apple
为该地区提供的榜单曲风；曲风列表读取失败时，地区总榜仍可独立读取。
只有授权后主动按「为你推荐」，才通过 MusicKit 请求 `/v1/me/recommendations`。
推荐按 Apple 的分组标题与顺序显示；如 Apple 提供推荐理由，则一并显示。
每组的内容按 Apple 续页链接读取，发生续页错误时提示无法读取完整推荐，不将其当成空结果。
只显示可打开的当前地区目录歌单，不将电台、专辑或私人资料库项目冒充歌单。
推荐资料仅留在当前页面，退出授权或地区切换时清除。

仅打开或返回目录不改变当前音频；从头播放从此列表首个可播位置开始，点曲目从该位置开始，
后续按页面顺序。SDK 使用同一首歌的重复位置，不将其转换成歌曲 ID 的唯一查找。
曲目关系分页按 Apple 顺序收齐（安全上限 1,000 条及 30 页）后才允许加入队列；
失败会停在能返回或重试的详情状态，不播放部分结果。非歌曲或目录未标可播的项目可见但不入队。

授权后显式点击时，使用 MusicKit v3 的公开 `shuffleMode` 和 `setQueue({songs, startPlaying: false})`。
MusicKit 会按地区或账号过滤某些候选；若 SDK 返回的列表与请求不完全一致，本次拒绝播放，
不会用原有索引误点另一首。验证后才调用 `changeToMediaAtIndex(index)`。
如果浏览器在异步加载后拦截播放，可在上方「浏览器播放器」点「播放 / 继续」重新确认所选项。
实际播了什么以 MusicKit 观察结果为准，并非仅由命令回执推断。

此项仅限现有官方 Apple Music 浏览器伴侣页，不添加原生 Java PCM 播放或默认游戏内入口。
伴侣业务代码不读取或保存 Music User Token；只有 MusicKit 向 Apple 发送授权请求。
不提取音频，不修改传统单歌 ID 播放流程。
需要已配置的开发者 token、Apple 支持的来源与浏览器、授权及有资格播放该目录歌曲的账户。

## 官方参考

- [Apple Music API 浏览分页](https://developer.apple.com/documentation/applemusicapi/fetching-resources-by-page)
- [艺人目录视图](https://developer.apple.com/documentation/applemusicapi/fetch-a-view-on-this-resource-by-name-4kow5)
- [歌单关系](https://developer.apple.com/documentation/applemusicapi/fetch-a-relationship-on-this-resource-by-name-707nb)
- [个人推荐](https://developer.apple.com/documentation/applemusicapi/get-all-recommendations)
- [MusicKit Web 授权管理](https://developer.apple.com/documentation/applemusicapi/user-authentication-for-musickit)
- [目录排行榜](https://developer.apple.com/documentation/applemusicapi/charts)与[地区曲风](https://developer.apple.com/documentation/applemusicapi/get-multiple-genres)
- [MusicKit JS v3 Queue Options](https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?path=/story/reference-javascript-queue--page#queueoptions)
- [MusicKit JS v3 Instance](https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?path=/story/reference-javascript-musickit-instance--page#changetomediaatindex)
