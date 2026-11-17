// Author: dishanhai. MusicKit owns all user credentials; this module reports states only.
export function createAuthorizationFlow(music, report) {
  let pending = false;
  let generation = 0;
  let state = 'WAITING';
  const emit = next => { state = next; report(next); };
  return {
    get busy() { return pending; },
    get authorized() { return music.isAuthorized === true; },
    get state() { return state; },
    async connect() {
      if (pending) return;
      pending = true;
      const current = ++generation;
      emit('AUTHORIZING');
      try {
        // Invoke directly from the click gesture; do not await a network callback first.
        await music.authorize();
        if (current !== generation) {
          if (music.isAuthorized) {
            try { await music.unauthorize(); }
            catch { emit('FAILED'); }
          }
          return;
        }
        // MusicKit may resolve undefined after cancellation. A resolved Promise isn't success.
        emit(music.isAuthorized ? 'AUTHORIZED' : 'CANCELLED');
      } catch {
        if (current === generation) emit('FAILED');
      } finally { pending = false; }
    },
    cancel() {
      ++generation;
      emit('CANCELLED');
      // Apple owns its sign-in popup. A late success is disconnected by connect() above.
    },
    async disconnect() {
      if (pending) { this.cancel(); return; }
      pending = true;
      ++generation;
      try { await music.unauthorize(); emit(music.isAuthorized ? 'FAILED' : 'SIGNED_OUT'); }
      catch { emit('FAILED'); }
      finally { pending = false; }
    }
  };
}
