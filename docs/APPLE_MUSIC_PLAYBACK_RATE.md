<!-- Author: dishanhai -->
# Official Apple Music browser playback rate

The companion uses the documented public `MusicKit.Instance.playbackRate` property and
`playbackRateDidChange` event. Reference:
https://js-cdn.music.apple.com/musickit/v3/docs/iframe.html?path=/story/reference-javascript-musickit-instance--page#playbackrate

Apple documents default 1.0 and browser-dependent bounds. This integration requests
0.5–2.0, checks actual SDK readback, and reports rejection when the browser clamps,
ignores or throws. Pitch preservation is **not** promised. It does not reach into
internal HTML media elements, decrypt DRM, or expose Music User Tokens to Java.

## Contract

- Authentication alone is not playback support. The browser must be authorized,
  have successfully prepared a catalog song, and expose a writable finite official
  rate property. Playback itself still requires the existing explicit Play gesture.
- Browser controls show actual SDK rate and unavailable/busy/error states.
- `AppleMusicBrowserLogin.supportsPlaybackRate()`, `playbackRate()` and
  `playbackRate(double)` expose the session-scoped bridge. The setter only queues a
  command; `playbackRateResult()` distinguishes PENDING/APPLIED/REJECTED/UNAVAILABLE.
- The latest requested command replaces a pending older one. An old acknowledgement
  cannot complete the new command. Retrying a lost acknowledgement does not repeat
  the SDK write. Actual rate is observational, not proof of entitlement.
- Browser polls each second; capability expires after five seconds without a fresh
  observation. Close/signout clears availability. Background browser throttling
  can legitimately make the Java capability unavailable until observations resume.
- `/playback-rate` requires the per-session header; POST also requires the exact
  origin, strict primitive-only schema and a small body. No arbitrary SDK objects
  or credentials cross this endpoint.

## Integration boundary and verification

This slice does not register an Apple native `OnlineMusicPlayer` or claim native
PCM, EQ, crossfade, download or seek-clock ownership. Native UI must explicitly bind
this companion session, gate its control on capability, and show asynchronous result.
The existing official browser authorization and explicit play flow remain in place.

Focused Java HTTP/controller and browser mock-SDK tests cover bounds, capability,
acknowledgements, cancellation, authorization separation and lost-response retries.
No real subscriber playback has been tested here: valid developer configuration,
accepted origin, supported browser, authorized Apple account and eligible catalog
content/subscription are still needed for live verification.
