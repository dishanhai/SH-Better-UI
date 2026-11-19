# Music output failure recovery

The shared online player now treats output construction, initial volume setup and
start failures as `Failure.AUDIO`, rather than a network failure or a half-created
output's state. Seek detaches the previous output before replacing it. A failed
replacement cannot leave a closed output exposed as still playing.

Failure preserves the selected track and queue, cancels queue advancement and
clears the failed attempt's cached audio. Any allocated output is asked to close;
a device throwing during close cannot prevent publishing the failure or finishing
stop/session shutdown. The player cannot guarantee that a faulty operating-system
driver actually released its resources after throwing.

Recovery is explicit: select the queued song again or issue `play(track)`. This
resolves the source again and therefore cannot reuse cached bytes to bypass expired
authorization or preview restrictions. Failure does not silently skip songs, retry
indefinitely, resume after stop or add remote library/account capabilities.

Scope: synchronous device-boundary exceptions, shared by the supported loaders.
Asynchronous decoder failures already report AUDIO through the existing output
state. This change does not add codecs, new platform providers, a retry button, or
claim that an unavailable audio device is repaired. Device doubles verify session
state/queue/lifecycle behavior; actual hardware listening is a separate check.

## Validation for this change

- Based on main `a1b5746`; six new deterministic session tests cover output factory,
  volume/start, seek setup/release failures, stop/close, and re-resolving authorization.
- NeoForge 26.1.2: full `test` 879 cases (5 conditional skips), `kernelTest` 541,
  `verifyLibraryJar` passed.
- Forge 1.20.1 and NeoForge 1.21.1: focused music `test` 292 cases each
  (5 conditional skips), `kernelTest` 541 each, `verifyLibraryJar` passed.
  These are not full legacy test runs.
- No failures/errors in those runs. No actual game/hardware listening or live
  platform login was attempted. NeoForge 26.2 was not built in this pass.
