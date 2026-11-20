// Author: dishanhai. Browser-only MusicKit transport; never handles user credentials.
export function createPlaybackFlow(music, sdk, report) {
  let pending = false;
  let ready = false;
  let closed = false;
  let message = 'IDLE';
  let songId = '';
  let errors = 0;
  let selectedIndex = null;
  const events = ['playbackStateDidChange', 'playbackTimeDidChange',
    'playbackDurationDidChange', 'playbackRateDidChange', 'authorizationStatusDidChange', 'mediaPlaybackError', 'nowPlayingItemDidChange', 'queuePositionDidChange'];
  function rateSupported() {
    if (closed || !ready || !music.isAuthorized || !Number.isFinite(music.playbackRate)) return false;
    for (let object = music; object; object = Object.getPrototypeOf(object)) {
      const descriptor = Object.getOwnPropertyDescriptor(object, 'playbackRate');
      if (descriptor) return typeof descriptor.set === 'function' || descriptor.writable === true;
    }
    return false;
  }
  function snapshot() {
    return { authorized: music.isAuthorized === true, busy: pending, ready,
      message, songId, nowPlaying: music.isAuthorized === true ? music.nowPlayingItem?.title ?? '' : '', supportsPlaybackRate: rateSupported(), playbackRate: Number.isFinite(music.playbackRate) ? music.playbackRate : 1, state: sdk.PlaybackStates[music.playbackState] ?? 'unknown',
      seconds: Number.isFinite(music.currentPlaybackTime) ? music.currentPlaybackTime : 0,
      duration: Number.isFinite(music.currentPlaybackDuration) ? music.currentPlaybackDuration : 0 };
  }
  function update() {
    if (closed) return;
    if (!music.isAuthorized) { ready = false; songId = ''; selectedIndex = null; message = 'LOGIN_REQUIRED'; }
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
    } catch { if (message !== 'ORDER_UNAVAILABLE' && message !== 'CLICK_TO_START') message = 'FAILED'; }
    finally { pending = false; update(); }
  }
  async function startSelected() {
    try {
      await music.changeToMediaAtIndex(selectedIndex);
      if (music.nowPlayingItemIndex === selectedIndex && music.nowPlayingItem?.id === songId) {
        selectedIndex = null;
      } else if (!closed && music.isAuthorized && message !== 'FAILED') {
        message = 'CLICK_TO_START';
        throw new Error('SDK has not observed the selected item');
      }
    } catch (error) {
      if (error?.name === 'NotAllowedError' && message !== 'FAILED') message = 'CLICK_TO_START';
      throw error;
    }
  }
  return {
    snapshot,
    prepare(id) {
      if (closed || pending) return Promise.resolve();
      if (!/^[0-9]{1,30}$/.test(id)) { message = 'INVALID_ID'; update(); return Promise.resolve(); }
      ready = false; songId = ''; selectedIndex = null;
      return run('LOADING', async () => {
        const queue = await music.setQueue({ song: id, startPlaying: false });
        // v3 returns undefined when playback is unsupported. Do not enable Play then.
        ready = queue != null && music.isAuthorized === true;
        if (!ready) throw new Error('Playback unavailable');
        songId = id;
      });
    },
    playOrdered(songs, index) {
      if (closed || pending) return Promise.resolve();
      const off = sdk.PlayerShuffleMode?.off, noRepeat = sdk.PlayerRepeatMode?.none;
      if (!Array.isArray(songs) || songs.length < 1 || songs.length > 1000 ||
          !songs.every(id => typeof id === 'string' && /^[0-9]{1,30}$/.test(id)) ||
          !Number.isInteger(index) || index < 0 || index >= songs.length || off === undefined) {
        message = 'ORDER_UNAVAILABLE'; update(); return Promise.resolve();
      }
      ready = false; songId = ''; selectedIndex = null;
      const ids = [...songs];
      return run('LOADING', async () => {
        music.shuffleMode = off;
        const queue = await music.setQueue({ songs: ids, startPlaying: false, repeatMode: noRepeat });
        if (closed || !music.isAuthorized) return;
        // v3 may filter unplayable resources. Never let an index silently start a different song.
        if (music.shuffleMode !== off || (noRepeat !== undefined && music.repeatMode !== undefined && music.repeatMode !== noRepeat) ||
            !Array.isArray(queue?.items) || queue.items.length !== ids.length ||
            queue.items.some((item, position) => item.id !== ids[position])) {
          message = 'ORDER_UNAVAILABLE'; throw new Error('SDK queue differs');
        }
        if (message === 'FAILED') throw new Error('SDK playback error');
        ready = true; songId = ids[index]; selectedIndex = index;
        await startSelected();
      });
    },
    play() {
      if (!ready) return Promise.resolve();
      return run('STARTING', () => selectedIndex === null ? music.play() : startSelected());
    },
    pause() { return run('PAUSING', () => music.pause()); },
    seek(seconds) {
      const duration = music.currentPlaybackDuration;
      if (!ready || !Number.isFinite(seconds) || seconds < 0 || !Number.isFinite(duration) || duration <= 0 || seconds > duration) {
        message = 'INVALID_TIME'; update(); return Promise.resolve();
      }
      return run('SEEKING', () => music.seekToTime(seconds));
    },
    async playbackRate(value) {
      if (!Number.isFinite(value) || value < .5 || value > 2) {
        message = 'INVALID_RATE'; update(); return false;
      }
      if (!rateSupported() || pending) { message = 'RATE_UNAVAILABLE'; update(); return false; }
      let applied = false;
      await run('RATE_CHANGING', () => {
        music.playbackRate = value; // Documented MusicKit.Instance property, never an internal media element.
        applied = Math.abs(music.playbackRate - value) < .001;
        if (!applied) throw new Error('SDK did not accept rate');
      });
      return applied && message === 'OBSERVED' && rateSupported();
    },
    close() {
      closed = true;
      for (const event of events) music.removeEventListener(event, event === 'mediaPlaybackError' ? error : update);
    }
  };
}
