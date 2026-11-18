# Music library navigation and original outline icons

This change follows the supplied music-panel reference's useful structural idea: content browsing belongs in the music navigation, while account management stays focused on authorization. It does not copy the reference's artwork, branding or private playlists.

## Scope

- A first-class Library entry exposes each supported provider's playlist and liked-song reads rather than hiding those inside account settings.
- Logged-out states must explicitly lead to the corresponding authorization screen. No placeholder songs, fabricated account state or unsupported Apple streaming/source are permitted.
- Account pages retain login, authorization, credential preference and sign-out responsibilities.
- Original scalable outline music icons replace heavy silhouettes. Rendering stays inside UUK, preserves current theme contrast/focus semantics, and does not use a platform logo or proprietary icon font.
- Preserve the merged background-playback lifecycle, classic control-panel selection, QQ themes and Apple catalog/authorization API.

## Acceptance

Verify provider/group switching cancels stale requests; playlist detail Back retains its parent page; narrow navigation remains reachable; logged-out reads do not issue authorized requests or expose fake data; all controls have keyboard labels and safe press cancellation. Tests and real-client outcomes are recorded below after validation.

## Implemented behavior

The navigation order is Search, Library, Lyrics, Queue, Account. Wide panels use labeled rows; compact panels use original geometry icons with complete tooltips/narration. Sidebar item spacing is bounded by the available height. Library is a books icon, not a heart.

Library filters actual available built-in sources (NetEase and QQ); no-source and single-source hosts do not invent another provider. Separate source and playlist/liked selection state is independent of account management. Logged-out or expired authorization presents a login action outside the pager hit area. Playlist detail Back restores its parent page and offset. Completed library context survives ordinary account visits; new credentials/sign-out invalidate the corresponding content. Source/group departure cancels old requests and pending input, including like/unlike controls when a widget slot changes identity.

`MusicControlRenderer.Icon` is an original UUK outline vocabulary, rendered from rounded SDF capsules, rings and bounded curve subdivisions. It includes search, back, heart, account, home, library, history, settings, playlist, add, close, remove and refresh. `MusicTextControl` preserves old caller APIs through mapping and also accepts the new icon enum. Refresh is a distinct circular arrow; pending refresh becomes cancellation, never a misleading magnifying glass.

## Validation (2026-11-17)

- Forge 1.20.1: 1,680 tests, zero failures/errors, five skipped; 533 kernel tests passed.
- NeoForge 1.21.1: 1,692 tests, zero failures/errors, five skipped; 533 kernel tests passed.
- Final code revision tested: `31e091a`. `git diff --check` passed.
- Native Neo client launched and entered the isolated creative QA world. The process exited with code 137 before final music navigation and screenshots could be verified. No old screenshots are offered as evidence for this change. Native wide/compact interaction and visual verification remain pending.
- No real-account login, authenticated playlist read, like mutation or credential persistence was exercised. Tests use controlled fake services. This change does not claim new provider/backend support.

## Guest authorization follow-up (2026-11-17)

- A player who has never signed in now sees a neutral, provider-specific sign-in invitation for playlists and liked songs, including the login control's tooltip. They are no longer told their session expired.
- A provider's `LoginRequired` response still asks for reauthorization. The wording does not assume expiration: missing permissions and revoked authorization can produce the same response. Network and unsupported-operation failures remain failures, not authentication claims.
- The existing session invalidates rejected credentials and protects newer credentials from stale requests. The new guest regression verifies that account, playlist and liked-track requests do not contact a provider before credentials are supplied, and that a later sign-in enables a real request.
- Background queue advancement is deliberately not bundled with this fix. `SessionPlayQueue` is view-owned; its observer runs from `OnlineMusicViewImpl.render`, and `suspend` disarms it. Moving only the observer would not preserve queue ownership, lifecycle or stop/skip semantics. This requires a separate session-owned queue/end-of-track change and lifecycle tests.

Validation: shared authorization feedback compiles with `javac --release 17`; all three focused JUnit tests pass. The 19 account-session JUnit tests also pass against the real session classes, compiled with Java 17 compatibility and the JNA/Gson/ICU dependencies (no stubs). Forge and NeoForge Gradle tests were both attempted but stopped at plugin resolution (`net.minecraftforge.gradle` and `net.neoforged.moddev` respectively), before compiling the mod. No in-game screenshot, real-account request or full dual-loader success is claimed.
