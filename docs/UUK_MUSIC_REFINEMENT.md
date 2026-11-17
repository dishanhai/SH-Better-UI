# Music shell refinement: grouped choices and honest status

This continuation starts from `9d4ba7a` and retains the merged Apple catalog/authorization APIs, QQ Aurora/Vinyl themes, and classic control-panel selection. It does not implement additional streaming-provider capability or replace account authentication.

## Design basis

- [Apple HIG: segmented controls](https://developer.apple.com/design/human-interface-guidelines/segmented-controls): use a compact, consistently sized group for closely related choices and make current selection identifiable. Music providers are filters for the same search task, not unrelated top-level destinations.
- [Apple HIG: motion](https://developer.apple.com/design/human-interface-guidelines/motion): short feedback communicates a choice without blocking subsequent input. Reduced motion keeps the final selected state and static busy indication.
- [Apple HIG: materials](https://developer.apple.com/design/human-interface-guidelines/materials): retain foreground hierarchy without obscuring the actual content. This implementation uses existing theme surfaces, not a claim of Apple's Liquid Glass rendering.

No Apple artwork, fonts, logos or native source code is included.

## Reusable UUK surface

`MusicSegmentedControl` is a single keyboard-focus stop with 2–5 same-kind choices. It draws its grouped capsule and selected thumb directly, uses theme colors (including QQ themes), and supports release-to-commit pointer and keyboard interactions. Arrow keys and Home/End select; Escape, hide, disable, focus loss, geometry changes and authoritative selection changes cancel pending input. Hosts must use its visibility/availability setters and cancel before rebinding option identities. The host's Escape route includes the control.

`MusicStatusView` renders explicit caller-supplied title/detail/busy state, not guessed progress. Its scoped regular/semibold typography wraps and clips within the supplied region. `requiredHeight` supports scrollable hosts; `narration()` exposes full text. Font/language/width changes invalidate wrapping, including the client resource-reload hook. Busy dots are decorative; words carry state. It performs no requests or actions.

The search shell uses the real source-switch cancellation path. Missing results, waiting and errors remain distinguishable; it does not fabricate tracks or imply successful playback. Apple catalog and experimental authorization remain their existing separate APIs rather than an invented streaming source.

## Verification

Validation results will be recorded after integration and final builds. Native screenshots, if captured, depict the actual game rather than a web mockup. Provider availability and account authorization remain separate from visual verification.

### Verified local continuation

At `ec7153a`, full Forge 1.20.1 tests: 1647 tests, 0 failures/errors, 4 live-test skips; 533 kernel tests passed. Full NeoForge 1.21.1 tests: 1659 tests, 0 failures/errors, 4 live-test skips; 533 kernel tests passed. A final copy-only refinement at `827aa0f` removes duplicated initial search hints; both loaders then passed compilation and all 18 `OnlineMusicQueueViewTest` tests with no skips or failures. The full suites were not repeated after that copy-only change.

Independent source review found and corrected stale likes after slot identity replacement, outdated searching tooltips, inaccessible clipped empty-queue details, and keyboard source focus being stolen by the query field. Tests cover these interrupted/repeated flows, plus the actual UiScreen Escape dispatch. `git diff --check` passes.

Native NeoForge QA used 1180×812 and an actually resized 784×538 client (GUI scale 2: 590×406 and 392×269). Real NetEase Canon results loaded. Pointer source selection, repeated arrow selection with retained focus, Tab to query, search, compact list scrolling, queue empty explanation, classic panel return and normal world save/exit were exercised. This pass did not attempt login, likes on a real account, audible playback or background playback; those are not inferred from a good screenshot. Forge native game, audible narration, and exhaustive theme/accessibility visual permutations remain unverified.

Final native rerun of `827aa0f` confirmed the initial hint is shown once, with no duplicated detail or pager copy. The final empty-state screenshot was captured through Minecraft F2, then the world was saved and the client closed normally. The wide/compact search screenshots are from the preceding identical interaction implementation; only initial empty-state wording changed afterward.
