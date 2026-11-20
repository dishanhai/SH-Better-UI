# Muonium control-center migration inventory

The current embedded player is documented in [MuoniumPlayer 2.6.0](MUONIUM_PLAYER.md). This page records the earlier integration.

Reference inspected: local MuoniumPlayer-multiversion `9b4cc7e7bd143315da716075a6d024a0d4c0c81b`, version `1.21.1/.../screens/hud/HudSettingsModel.java`. This is **not a remote freshness claim**. Counts are static category/group entry slots, including repeated quick-setting references and conditionally hidden entries, not distinct persisted keys.

| Category | Groups | Entries | Dependency and migration boundary |
|---|---:|---:|---|
| Lyric style | 6 | 28 | Lyrics timing, per-word highlighting, glow, desktop/fullscreen renderer; existing SHBUI lyric-specific features require individual adapter mapping, not aliases |
| 3D lyrics | 3 | 31 | World-space glyph rendering, positioning, collision avoidance, cover palette, shader-pack compatibility; substantial new backend |
| Panels/cards | 3 | 9 | Desktop lyric layout and Blossom line/card renderer; generic settings cards alone do not implement this |
| Dynamic island | 3 | 25 | Persistent HUD capsule, downloads/notifications, FPS/ping/clock/music state, ambient rotation and transition state |
| Playback | 5 | 29 | Focus ducking, Voice Chat integration, two-stream Automix, WSOLA/pitch/DSP, Folia model discovery/download/inference; ordinary playback is not equivalent |
| Entertainment | 2 | 22 | Beat-reactive entity transforms, camera/cinematic state, optional network rotation packets; requires version-specific mixin and gameplay-risk review |
| UI/themes | 7 | 33 | Some theme/glass/animation controls map to native UUK; spectrum/system-media bridges, independent UI scaling and renderer selection require explicit adapters |
| Info/cover | 2 | 6 | Song HUD and cover presentation/drag-layout/reset, distinct from a reusable media-card widget |
| Presets | 2 | 4 | Cross-module snapshot storage, export/import validation, undo and partial application |
| Cache/announcements | 2 | 3 | Cache eviction safe for active/preloaded tracks, interrupted artifacts, announcement source/read state |
| **Total** | **35** | **190** | Full migration is not completed by the shell |

## Implemented native shell in this stage

`ControlPanelScreen` retains draft/Apply/Cancel, reset undo, shortcut capture, per-section scroll, developer/config/overview/project feedback. Wide layouts use a navigation rail; compact layouts retain scrollable navigation. Back/forward retains query and scroll, Ctrl+F focuses search, Escape clears search before closing, Enter opens the first matching group, Alt+Left/Right follows history. Search indexes genuine existing groups and descriptive keywords (not every MP setting). Theme grid uses the actual registry and independent archived palettes. Music opens the real normal-user session through SettingsApi. Glass settings use UiSettingCard with independent primary toggle and secondary arrow. UiLayeredMotion controls header/body visibility without moving hit targets.

## Optional installed-Muonium bridge

The separate **Muonium settings** navigation entry opens `MpSettingsScreen`, which reflects the compatible installed upstream declaration model and invokes its real setting providers/actions. This gives access to MP-owned functionality without manufacturing working switches or embedding its Skija/audio/ML stack into SHBUI. Missing or incompatible MP is an explicit state, not a successful migration. The table above identifies backend ownership for a future standalone port; it is not a claim that bridge-exposed MP features run without MP. Upstream settings are a distinct persistence domain and cannot be rolled back by the SHBUI parent draft's Cancel action. Both exact-version compatibility and destructive upstream actions require separate validation.

## Subsequent implementation boundaries

1. Introduce shared settings declarations and direct result anchors for each **actual** backend setting (this stage searches group destinations only).
2. Port desktop lyric/HUD layout, dynamic island event/state bridges, then info-card appearance; test both versions independently.
3. Port world lyrics with native renderer and shader-pack safety review rather than importing Skija wholesale.
4. Port audio transition/DSP separately from model-management and optional Voice Chat adapters. Model download/storage must be explicit and validated.
5. Port gameplay-affecting entertainment/camera features separately; local visuals and server-visible packet actions must not be conflated.
6. Add cross-module preset transactions, validation, undo and safe cache lifecycle after those modules expose stable settings.

No placeholder toggles are represented as working features. Unsupported upstream categories have not been silently counted as implemented.

## Licensing

Reference root LICENSE is Apache-2.0; NOTICE credits Copyright 2026 MuoniumPlayer Team. This shell/navigation model is an original native implementation inspired by the supplied design, without copied rendering code/assets. Any subsequent adapted source must preserve upstream LICENSE/NOTICE/copyright, identify source commit/files and changes. Do not copy unknown third-party fonts/logos/assets, bundle Skija by assumption, or present Muonium branding as SHBUI.

## Static per-group inventory

```text
['"歌词样式"', '"桌面歌词与全屏歌词的文字外观"'] groups 6 entries 28
  "常用" 6
  "当前行" 5
  "其它行" 5
  "发光" 3
  "逐字效果" 4
  "排版与滚动" 5
['"3D 歌词"', '"歌词挂进游戏世界，飘在玩家前方，转身跟着走"'] groups 3 entries 31
  "位置与形态" 8
  "文字" 16
  "发光" 7
['"面板与卡片"', '"桌面歌词的宽高与 Blossom 卡片"'] groups 3 entries 9
  "样式与大小" 3
  "卡片样式" 2
  "毛玻璃卡片" 4
['"灵动岛"', '"顶部胶囊的外观、常驻内容和动画速度"'] groups 3 entries 25
  "外观与尺寸" 8
  "常驻内容" 8
  "动画速度" 9
['"播放"', '"两首歌自动衔接不断片，外加场景音效"'] groups 5 entries 29
  "切出游戏时" 3
  "基本设置" 7
  "衔接手法" 6
  "人声分离模型" 8
  "场景声学" 5
['"fun"', '"娱乐"'] groups 2 entries 22
  "通透模式" 12
  "卡点运镜" 10
['"界面与主题"', '"主题皮肤、频谱、真玻璃和播放器来源"'] groups 7 entries 33
  "音乐频谱" 10
  "设置界面" 1
  "外部播放器" 2
  "窗口与缩放" 2
  "动画" 2
  "配色" 2
  "真玻璃材质" 14
['"信息栏与封面"', '"左上角信息卡、桌面歌词的开关摆放和封面"'] groups 2 entries 6
  "显示与摆放" 4
  "封面与标签" 2
['"presets"', '"模板预设"'] groups 2 entries 4
  "模板管理" 2
  "导入与恢复" 2
['"缓存与公告"', '"音乐缓存占多大盘、公告入口"'] groups 2 entries 3
  "音频缓存" 2
  "公告中心" 1
```

## Integration validation

The optional bridge additionally exposes the installed MP runtime model rather than
copying its music, HUD, DSP, network or native-renderer backends. The model inventory
above is the scope of those live adapters, not a claim that all business features run
without MP. MuoniumConfig shell-only pages are reachable through the explicit upstream
panel route when that separate dependency is installed; see `MP_SETTINGS_BRIDGE.md`.

Independent review found and corrected three P2 issues: confirmation for choices with
server-visible effects, stale focus after live MP row replacement, and accidental
persistence of SHBUI-owned runtime palette IDs. The corresponding regression tests were
added before publication. No user-supplied MP JAR, real login, download, cache deletion,
network-sharing option or server-visible entertainment action was executed for validation.

### Final integration acceptance (2026-11-17 UTC)

Both Forge 1.20.1 and NeoForge 1.21.1 passed `test kernelTest build verifyLibraryJar`
with offline cached dependencies. Forge: 1459 tests; NeoForge: 1471 tests; each has
4 existing conditional skips, zero failures/errors. Kernel: 506 tests per platform,
zero failures/errors/skips. The black-hole EGL shader/animation regression also passed.

Actual NeoForge 1.21.1 gameplay UI at 1180×812 / GUI scale 2 verified the sidebar,
search and history, independent details button, theme grid, applied-theme persistence
across restart, black-hole animation, color draft/cancel, normal music account entry,
and explicit missing-MP state. F2 screenshots were captured without compositing.
Rendering used software Mesa llvmpipe: no hardware frame-rate claim is made. Audio
was unavailable; narration/audio playback and real account operations were not tested.
Forge visual runtime and actual MP/MuoniumConfig joint runtime remain unverified.
The optional dependency's supplied JAR was inspected statically only, not executed.

The source adaptation contains no upstream fonts or opaque binary assets. Bundled
fonts in the separate supplied upstream JAR have their own licenses; the MP Apache
license must not be assumed to grant redistribution rights for those assets.
