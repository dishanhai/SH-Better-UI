// Author: dishanhai. Session-scoped commands/observations only; never serializes SDK objects.
export function createRateBridge(playback, request) {
  let busy = false, closed = false, ack = 0, status = 'OBSERVED';
  return {
    async poll() {
      if (busy || closed) return;
      busy = true;
      try {
        const command = await request('GET');
        if (closed) return;
        if (Number.isSafeInteger(command.id) && command.id > ack) {
          const applied = await playback.playbackRate(command.rate);
          if (closed) return;
          ack = command.id; status = applied ? 'APPLIED' : 'REJECTED';
        }
        const state = playback.snapshot();
        await request('POST', { supported: state.supportsPlaybackRate, rate: state.playbackRate, ack, status });
      } finally { busy = false; }
    },
    close() { closed = true; }
  };
}
