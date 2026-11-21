# MP native UI migration: verified incremental scope

The current embedded player is documented in [MuoniumPlayer 2.6.0](MUONIUM_PLAYER.md). This page records the earlier integration.

Base: SHBUI main `be59814` (PRs 19, 21 and the island/About successor PR 22), plus the two local Markdown/rich-tooltip commits. Reference: MuoniumPlayer-multiversion `9b4cc7e7`.

This is a native SHBUI adaptation, **not a complete distribution of MP's UI or backend**. The existing MP reflection settings bridge remains optional and is not counted as native implementation.

## Implemented in this stage

- All sixteen MP color presets registered. Native accessible text/foreground adjustments, shared page chrome, transparent material fallbacks, 16-step theme undo and dirty-draft leave confirmation.
- Native Themes, Favorites, Mods and Preferences share the page chrome. Preferences include scrollable descriptions, local preview, defaults and undo; Apply/Cancel and parent draft boundaries remain intact.
- Eleven stable native island style IDs (three existing plus eight additions). Native glass/highlight/card presentations, music status and progress, pause timeout, delayed-hover state policy and an editor/gallery style chooser. Existing style IDs are unchanged.
- A view-owned playback queue built from the currently loaded search/playlist page: previous/next, locate, remove/clear/undo, safe end-of-track advancement, pause/suspend guards and responsive scrolling. It is not an account-synchronized playlist.
- Spring-following lyric viewport, immediate manual browsing, delayed return to follow mode, active-line typography and reduced-motion behavior.
- Optional `followArtwork` fluid-background palette, default off. It uses already-published system media artwork with bounded asynchronous sampling, cancellation and stale-result rejection. It does not enable audio capture or fetch remote artwork.

## Deliberate limitations / next stages

- No claim of complete MP Discovery, radio, together-listening, downloads/transcoding, Automix/DSP, model installation or multi-provider backend migration.
- No complete onboarding/account/source flow beyond existing real SH backend capability.
- Native island gameplay presentation remains non-interactive: no mouse capture while playing. Delayed-hover policy is available to the editor/widget host; no player morph or transport command is invented for the read-only MusicApi. Native bounce geometry is clamped and is not an exact upstream overshoot match.
- Native transparency/highlights are not Skia refraction. Sixteen palettes do not mean every upstream shader/material is reproduced.
- The ten upstream lyric renderers, world lyrics and Folia/Skia effects are not all ported. System-artwork backgrounds are not yet connected to independent online-player URL artwork caches.
- Existing MP settings bridge writes MP-owned settings independently of a SH parent Cancel; this bridge is not described as a transactional native implementation.

## License

Upstream Apache-2.0 LICENSE and unmodified NOTICE remain in `META-INF/third-party/muonium-player`; SOURCE describes these modifications. No Apple SF/PingFang fonts, unknown-license assets, Skija or Skia native dependencies were added.

## Validation

Initial integrated full suites passed: Forge 1.20.1 1564 main tests + 513 kernel tests; NeoForge 1.21.1 1576 main tests + 513 kernel tests. A later review found and fixed a queue suspend/end-of-track race; final reruns and actual screenshot evidence are recorded by the coordinating task. Pure-model and compile tests do not prove provider authentication or live media integration.

### Final review and game verification

- Cross-review fixed the queue suspend/end-of-track race. Final focused reruns passed on both loaders: 15 queue model/view tests each, zero failures. Compilation reran against the final sources.
- Reconciled current upstream main `ccef02d` after Markdown PR 23 merged. The local merge changed no source content relative to the tested integration (`ac01504`), preserving the existing Markdown implementation without a second content change.
- NeoForge 1.21.1 actual client: entered an isolated creative test world, opened native themes/preferences/player/island editor and the material gallery. Captured five native F2 PNGs at 1180×812.
- Verified light and transparent theme previews; Undo restored the old palette; Cancel requested confirmation for dirty theme state and discarded it. Verified preferences high-contrast preview, undo and cancel. Cycled all eleven island appearance IDs and entered placement editor.
- Online provider search returned an explicit network failure in this environment. No live track playback/account authentication was therefore verified. System media reported unsupported. The queue screenshot is the real empty state; the capsule screenshot is explicitly the gallery's **Silent demo**, not evidence of a connected media provider or interactive world HUD.
- Forge client runtime was not launched in this stage; Forge has compile and automated-test coverage only.
- One early client attempt was killed with exit 137 while another agent ran Gradle. After serializing memory-heavy work, the full screenshot pass completed and the world/client exited normally.

Screenshot filenames (not bundled in the source repository):
`MP-透明主题-游戏实机.png`, `MP-浅色主题-游戏实机.png`, `MP-偏好设置-游戏实机.png`, `MP-播放队列空态-游戏实机.png`, `MP-音乐胶囊静音演示-游戏实机.png`.
