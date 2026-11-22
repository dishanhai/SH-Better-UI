# 导入 Listen 1 曲库

1. 在 Listen 1 打开「设置 → 备份」，导出本地备份 `listen1_backup.json`。
2. 在 Minecraft 按右 Shift 打开 SHBUI 播放器，点侧栏「Mu 本地歌单」旁的「＋」，选择「导入 Listen 1 备份」。选择文件或粘贴完整路径，再点「导入」。
3. 从侧栏打开导入的歌单，点曲目开始播放，或使用歌单的播放按钮。导入完成时会提示导入和跳过的数量。

自建歌单按备份顺序保存到游戏实例。网易云、QQ、酷狗和 B 站视频可交给 SHBUI 已有音源播放，B 站分 P 按 CID 选取。酷我和咪咕曲目可导入，直接取流需要启用支持对应平台的自定义音源。收藏的网易云、QQ、酷狗歌单保存为原平台的歌单引用，打开时才读取当时可用的曲目。播放可用性取决于原平台、当前账号和曲目的权限。

可在侧栏右键移除导入的歌单。再次导入同一自建歌单会按其 Listen 1 ID 更新曲目。相同的收藏引用只保留一份。读取备份期间可按 Esc 取消。导入只保留歌单和曲目元数据。千千、B 站音频区和 Listen 1 本地文件条目会跳过。备份上限为 4 MiB，自建和收藏索引各最多 200 项，每张自建歌单最多 1,000 首。

Listen 1 从各音乐平台查询曲库，点播时由曲目来源对应的适配器获取临时播放地址，取流失败时可按用户配置尝试换源。它的收藏记录只保留歌单信息，上游曲目在打开时现取。参见 [Listen 1 播放分派](https://github.com/listen1/listen1_chrome_extension/blob/v2.33.0/js/loweb.js)和[歌单存储](https://github.com/listen1/listen1_chrome_extension/blob/v2.33.0/js/myplaylist.js)。

## 旧版在线音乐界面

1. In Listen 1, use **Settings → Backup → Local backup** to export
   `listen1_backup.json`.
2. In the legacy SHBUI online music interface open **Music → Library → Listen 1** and paste the full local path to
   the backup. Choose **Import**.
3. Open an imported playlist; select a track to play its collection with the
   existing SHBUI queue. The import reports the number of compatible and skipped
   tracks. In the Listen 1 library, **+** imports another backup. Esc or
   Back cancels the pending read or returns to the previous import.

The importer recognizes the local `playerlists` index and the existing
`qqtrack_` / `netrack_` identities. Tracks play through SHBUI's installed
QQ Music or NetEase Music provider, with that provider's usual account and
availability requirements. This does not install Listen 1, aggregate new
providers, bypass a playback restriction, import favorites that have no
inline tracks, sync with Gist, or read the extension's storage directly.

This is a user-initiated, read-only, local import. Full backup data, saved
cookies/tokens, external URLs and artwork are neither retained nor transferred.
A successful import replaces the sanitized, in-memory Listen 1 library for
that player session only, leaving the original backup and other libraries
untouched. It does not alter the queue until a track is explicitly selected.
The existing SHBUI queue may checkpoint those selected tracks in its normal
format; the full import is not saved. Reopen the player in the same game session
to return to the imported library; closing the game discards the import.
Files are capped at 4 MiB, 200 playlists, 1000 tracks per playlist and 32
nesting levels.

Format reference: [Listen 1 (MIT), backup export](https://github.com/listen1/listen1_chrome_extension/blob/3f24efa045125875a609dcb0f3f3f0be4edb8b37/js/controller/navigation.js)
and [playlist storage](https://github.com/listen1/listen1_chrome_extension/blob/3f24efa045125875a609dcb0f3f3f0be4edb8b37/js/myplaylist.js).
No upstream source or creative assets are copied.
