# Apple-inspired transport refinement

Fresh implementation on main `0ab67fb`, not recovery of a missing historical checkout.

## Changes
- Mini-player always exposes previous, play/pause, next, stop/cancel, lyrics and queue in a consistent visual and keyboard order.
- Below 320 logical pixels of transport width, title, actions and timeline reflow into three rows within the original 64-pixel reservation. No content area is reduced and no transport action is hidden.
- Current time and remaining time follow the pointer preview. Releasing commits once; Escape, focus loss, track/duration changes and loss of availability preserve the existing cancellation contract.
- Opt-in timeline bubbles show current/remaining time, stay within timeline bounds and use the existing scoped OFL music font. Very narrow controls retain current time first. Volume does not receive a time bubble.
- Thin track and thumb engagement settle over a short frame-independent transition, with immediate reduced-motion feedback and high-contrast treatment.

## Verification for this revision
- All 541 platform-independent kernel tests passed on JDK 17 (including the three new presentation tests), no skips/failures.
- Six actual layout tests passed separately: four existing OnlineMusicLayout tests plus two new exhaustive mini-player tests covering widths 208–1024 at heights 240/340/720.
- Six existing seek transaction tests also passed in the focused JDK 21 run; these are included in the kernel total, not additional tests.
- Independent source review and `git diff --check` passed.

## Unverified / merge gates
- Forge 1.20.1 and NeoForge 1.21.1 build attempts stopped before compilation because ForgeGradle `[6.0,6.2)` and ModDev `2.0.148` could not resolve in the current environment. Retry through the configured environment proxy did not resolve the plugins.
- The renderer/widget and full view have not been compiled against both loaders here. Existing native input tests have not run in this rebuild.
- New in-game screenshots, audible playback, narration audio and real-account actions have not been verified. Historical screenshots and test totals must not be used as evidence for this implementation.
- Keep the PR in draft until dual-loader compilation and in-game pointer/keyboard/compact/high-contrast/reduced-motion validation are complete.

No Apple assets, fonts or proprietary implementation were copied. Existing classic/QQ themes and playback ownership remain unchanged.
