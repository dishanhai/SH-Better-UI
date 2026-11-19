// Author: dishanhai. Browser-only MusicKit transport; never handles user credentials.
export function createPlaybackFlow(music, sdk, report) {
  let pending = false;
  let ready = false;
  let closed = false;
  let message = 'IDLE';
  let songId = '';
  let errors = 0;
  const events = ['playbackStateDidChange', 'playbackTimeDidChange',
    'playbackDurationDidChange', 'authorizationStatusDidChange', 'mediaPlaybackError'];
  function snapshot() {
    return { authorized: music.isAuthorized === true, busy: pending, ready,
      message, songId, state: sdk.PlaybackStates[music.playbackState] ?? 'unknown',
      seconds: Number.isFinite(music.currentPlaybackTime) ? music.currentPlaybackTime : 0,
      duration: Number.isFinite(music.currentPlaybackDuration) ? music.currentPlaybackDuration : 0 };
  }
  function update() {
    if (closed) return;
    if (!music.isAuthorized) { ready = false; songId = ''; message = 'LOGIN_REQUIRED'; }
    report(snapshot());
  }
  const error = () => { errors++; message = 'FAILED'; update(); };
  for (const event of events) music.addEventListener(event, event === 'mediaPlaybackError' ? error : update);
  async function run(action, operation) {
    if (closed || pending) return;
    if (!music.isAuthorized) { update(); return; }
    const previousErrors = errors;
    pending = true; message = action; update();
    try {
      // Call SDK before the first await, preserving the Play button's user gesture.
      await operation();
      message = errors === previousErrors ? 'OBSERVED' : 'FAILED'; // Only SDK state, not Promise resolution, can say "playing".
    } catch { message = 'FAILED'; }
    finally { pending = false; update(); }
  }
  return {
    snapshot,
    prepare(id) {
      if (closed || pending) return Promise.resolve();
      if (!/^[0-9]{1,30}$/.test(id)) { message = 'INVALID_ID'; update(); return Promise.resolve(); }
      ready = false; songId = '';
      return run('LOADING', async () => {
        const queue = await music.setQueue({ song: id, startPlaying: false });
        // v3 returns undefined when playback is unsupported. Do not enable Play then.
        ready = queue != null && music.isAuthorized === true;
        if (!ready) throw new Error('Playback unavailable');
        songId = id;
      });
    },
    play() { return ready ? run('STARTING', () => music.play()) : Promise.resolve(); },
    pause() { return run('PAUSING', () => music.pause()); },
    seek(seconds) {
      const duration = music.currentPlaybackDuration;
      if (!ready || !Number.isFinite(seconds) || seconds < 0 || !Number.isFinite(duration) || duration <= 0 || seconds > duration) {
        message = 'INVALID_TIME'; update(); return Promise.resolve();
      }
      return run('SEEKING', () => music.seekToTime(seconds));
    },
    close() {
      closed = true;
      for (const event of events) music.removeEventListener(event, event === 'mediaPlaybackError' ? error : update);
    }
  };
}
