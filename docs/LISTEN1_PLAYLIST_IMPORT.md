# Listen 1 local playlist import

1. In Listen 1, use **Settings → Backup → Local backup** to export
   `listen1_backup.json`.
2. In SHBUI open **Music → Library → Listen 1** and paste the full local path to
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
