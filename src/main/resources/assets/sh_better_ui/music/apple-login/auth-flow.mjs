// Author: dishanhai. MusicKit owns all user credentials.
export function createAuthorizationFlow(music, report) {
  let pending = false;
  let generation = 0;
  let state = 'WAITING';
  const emit = next => { state = next; report(next); };
  async function reportStorefront(current) {
    try {
      const { data } = await music.api.music('/v1/me/storefront');
      const storefront = data?.data?.find(item => item?.type === 'storefronts')?.id;
      if (current === generation && state === 'AUTHORIZED' && music.isAuthorized === true
          && typeof storefront === 'string' && /^[a-z]{2}$/.test(storefront)) {
        report('AUTHORIZED', storefront);
      }
    } catch { /* A storefront lookup failure does not invalidate MusicKit authorization. */ }
  }
  function authorized(current) {
    emit('AUTHORIZED');
    void reportStorefront(current);
  }
  return {
    get busy() { return pending; },
    get authorized() { return music.isAuthorized === true; },
    get state() { return state; },
    observe() {
      if (pending) return;
      if (state === 'WAITING' && music.isAuthorized === true) authorized(++generation);
      else if (state === 'AUTHORIZED' && music.isAuthorized !== true) {
        ++generation;
        emit('SIGNED_OUT');
      }
    },
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
        if (music.isAuthorized === true) authorized(current);
        else emit('CANCELLED');
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
