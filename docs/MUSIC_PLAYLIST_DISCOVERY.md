# Native playlist discovery

QQ Music and NetEase Cloud Music have a **Library → Discover** entry. Choose a source, open the platform’s official browse page in your browser, and paste the full playlist-detail URL back into the game. Short share URLs must first be opened in the browser. SHBUI parses the selected provider’s URL as a playlist ID; it does not fetch the pasted URL, resolve redirects, scrape the page, or receive browser credentials.

- QQ Music takes `https://y.qq.com/n/ryqq_v2/playlist/<id>` and the earlier `/n/ryqq/playlist/<id>` form.
- NetEase accepts `https://music.163.com/playlist?id=<id>` or `https://music.163.com/#/playlist?id=<id>`.
- To read the detail in-game, sign in to the corresponding source in SHBUI. Opening the official page does not log in to SHBUI. These pages are external discovery surfaces, not an embedded personalized feed or fallback app-generated ranking.

The detail is prepared before any playback starts. It retains duplicate occurrences and provider order; selecting a row starts exactly there with sequential continuation. Requests run under the active provider-account generation; switching sources, signing out, replacing the account or leaving the view cannot start a late result. Up to 1,000 tracks are supported. Incomplete, unverified, oversized or changed upstream results fail rather than enabling a partial queue. Playback still depends on platform track entitlements; fetching metadata does not resolve audio. QQ paginates the already-used playlist RPC; it rejects inconsistent identity/size, short pages and identical full pages that may indicate an ignored offset. The provider does not expose a transaction across QQ page requests, so avoid editing the list while loading it. NetEase captures the existing detail endpoint’s ordered ID snapshot and resolves the same IDs in bounded batches.

The native player exposes `supportsPlaylistDetail(source)` and `playlistDetail(source, playlistId)`. Source adapters can implement the optional complete-detail capability separately from the legacy paged account playlist methods. A complete detail must match the requested identity, declared count, source and maximum size. The companion Apple Music personalized feed is a separate integration.

Primary references: [QQ Music’s public browse page](https://y.qq.com/n/ryqq_v2/category) and [Tencent’s IoT H5 SDK music service](https://cloud.tencent.com/document/product/1081/67456). The latter requires a device serial and a separate authorized H5 environment; SHBUI does not equate it with its native account login.
