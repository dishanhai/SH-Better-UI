import test from 'node:test';
import assert from 'node:assert/strict';
import { createCatalog, orderedSelection } from '../../src/main/resources/assets/sh_better_ui/music/apple-login/catalog-flow.mjs';
import { createPlaybackFlow } from '../../src/main/resources/assets/sh_better_ui/music/apple-login/playback-flow.mjs';

const artist = { type: 'artists', id: '3', attributes: { name: 'Artist', artwork: { url: 'https://is1-ssl.mzstatic.com/a/{w}x{h}bb.jpg' } } };
const album = { type: 'albums', id: '8', attributes: { name: 'Featured album' } };
const playlist = { type: 'playlists', id: 'pl.test', attributes: { name: 'Chart playlist', curatorName: 'Apple Music' } };
const song = (id, type = 'songs', playable = true) => ({ type, id, attributes: { name: `Track ${id}`, playParams: playable ? { id } : undefined } });
const root = '/v1/catalog/tw';
function stub(routes) {
  const calls = [];
  const music = { storefrontCountryCode: 'tw', api: { async music(path, params) {
    calls.push([path, params]);
    if (!Object.hasOwn(routes, path)) throw new Error('unexpected route ' + path);
    return { data: routes[path] };
  } } };
  Object.defineProperty(music, 'musicUserToken', { get() { throw new Error('must not access token'); } });
  return { music, catalog: createCatalog(music), calls };
}

test('search and chart distinguish catalog lists from personal recommendations', async () => {
  const {catalog, calls} = stub({
    [root + '/search']: { results: { artists: { data: [artist] }, playlists: { data: [playlist] } } },
    [root + '/charts']: { results: { playlists: [{ data: [playlist] }] } }
  });
  const found = await catalog.search(' Artist & Playlist ');
  assert.deepEqual(found.map(({kind, id}) => [kind, id]), [['artists', '3'], ['playlists', 'pl.test']]);
  assert.equal(found[0].artwork, 'https://is1-ssl.mzstatic.com/a/360x360bb.jpg');
  assert.equal((await catalog.charts())[0].subtitle, 'Apple Music');
  assert.deepEqual(calls, [
    [root + '/search', { types: 'artists,playlists', term: 'Artist & Playlist', limit: 15 }],
    [root + '/charts', { types: 'playlists', limit: 16 }]
  ]);
});

test('personal recommendation fixture uses Apple group/contents/catalog shape and requires existing authorization', async () => {
  const personal = { ...playlist, id: 'pl.pm-fixture', href: root + '/playlists/pl.pm-fixture',
    attributes: { name: 'Made-for-you mix', playlistType: 'personal-mix', curatorName: 'Apple Music for Me' } };
  const recommendations = { data: [{
    id: '6-fixture', type: 'personal-recommendation', href: '/v1/me/recommendations/6-fixture',
    attributes: { resourceTypes: ['playlists'], title: { stringForDisplay: 'Made for You' }, kind: 'music-recommendations' },
    relationships: { contents: { href: '/v1/me/recommendations/6-fixture/contents', data: [
      { type: 'stations', id: 'ra.station', href: root + '/stations/ra.station' },
      album, { ...personal, type: 'library-playlists' },
      { ...personal, href: '/v1/catalog/us/playlists/pl.pm-fixture' }, personal, personal
    ] } }
  }] };
  const { catalog, music, calls } = stub({ '/v1/me/recommendations': recommendations,
    [root + '/playlists/pl.pm-fixture']: { data: [personal] },
    [root + '/playlists/pl.pm-fixture/tracks']: { data: [song('8'), song('2')] }
  });
  await assert.rejects(catalog.recommendations(), /Authorization required/);
  assert.deepEqual(calls, []);
  music.isAuthorized = true;
  const entries = await catalog.recommendations();
  assert.deepEqual(entries.map(entry => entry.id), ['pl.pm-fixture']);
  assert.deepEqual(calls, [['/v1/me/recommendations', undefined]]);
  const detail = await catalog.detail(entries[0].kind, entries[0].id);
  assert.deepEqual(detail.tracks.map(track => track.id), ['8', '2']);
});

test('personal request never substitutes public results on forbidden, unsupported or stale auth', async () => {
  const forbidden = stub({ '/v1/me/recommendations': { errors: [{ status: '403' }] } });
  forbidden.music.isAuthorized = true;
  await assert.rejects(forbidden.catalog.recommendations(), /Catalog unavailable/);
  const empty = stub({ '/v1/me/recommendations': { data: [] } });
  empty.music.isAuthorized = true;
  assert.deepEqual(await empty.catalog.recommendations(), []);
  const pending = empty.catalog.recommendations();
  empty.music.isAuthorized = false;
  await assert.rejects(pending, /session changed/);
});

const personalPath = '/v1/me/recommendations';
const contentsPath = id => personalPath + '/' + id + '/contents';
const group = (id, contents) => ({ id, type: 'personal-recommendation', relationships: { contents } });
const personalList = id => ({ ...playlist, id, href: root + '/playlists/' + id });

test('a recommendation group with only albums on its first page retains playlists from its next page in provider order', async () => {
  const next = contentsPath('6-first') + '?offset=1&l=zh-Hant';
  const first = personalList('pl.first'), second = personalList('pl.second'), last = personalList('pl.last');
  const { music, catalog, calls } = stub({
    [personalPath]: { data: [group('6-first', { data: [album], next }), group('6-later', { data: [first, last] })] },
    [next]: { data: [first, { ...last, href: '/v1/catalog/us/playlists/pl.last' }, album, second] }
  });
  music.isAuthorized = true;
  const lists = await catalog.recommendations();
  assert.deepEqual(lists.map(entry => entry.id), ['pl.first', 'pl.second', 'pl.last']);
  assert.deepEqual(calls, [[personalPath, undefined], [next, undefined]]);
});

test('personal cursors cannot leave the exact Apple contents relationship or loop', async () => {
  const path = contentsPath('6-fixture'), loop = path + '?offset=1';
  for (const next of ['https://api.music.apple.com' + loop, '//example.com' + loop,
    contentsPath('other') + '?offset=1', root + '/playlists/pl.test/tracks?offset=1', loop + '#fragment', loop]) {
    const { music, catalog, calls } = stub({
      [personalPath]: { data: [group('6-fixture', { data: [personalList('pl.first')], next })] },
      [loop]: { data: [personalList('pl.second')], next: loop }
    });
    music.isAuthorized = true;
    await assert.rejects(catalog.recommendations(), /continuation|pagination/);
    assert.deepEqual(calls.map(([path]) => path), next === loop ? [personalPath, loop] : [personalPath]);
  }
});

test('a recommendation continuation failure never returns a partial or empty success', async () => {
  const next = contentsPath('6-fixture') + '?offset=1';
  for (const response of [{ errors: [{ status: '403' }] }, { invalid: true }]) {
    const { catalog, music } = stub({
      [personalPath]: { data: [group('6-fixture', { data: [personalList('pl.first')], next })] },
      [next]: response
    });
    music.isAuthorized = true;
    await assert.rejects(catalog.recommendations(), /Catalog unavailable|Invalid recommendation contents/);
  }
});

test('recommendation continuation budgets cap the whole response', async () => {
  const path = contentsPath('6-fixture'), page = i => path + '?offset=' + i;
  const routes = { [personalPath]: { data: [group('6-fixture', { data: [album], next: page(1) })] } };
  for (let i = 1; i <= 30; i++) routes[page(i)] = { data: [album], next: page(i + 1) };
  const { catalog, music, calls } = stub(routes);
  music.isAuthorized = true;
  await assert.rejects(catalog.recommendations(), /pagination unavailable/);
  assert.equal(calls.length, 31);
  const oversized = stub({
    [personalPath]: { data: [group('6-fixture', { data: Array(1000).fill(album), next: page(1) })] }
  });
  oversized.music.isAuthorized = true;
  await assert.rejects(oversized.catalog.recommendations(), /collection exceeds limit/);
  assert.equal(oversized.calls.length, 1);
});

test('sign-out, storefront changes and superseded routes discard late pages without following their cursor', async () => {
  const path = contentsPath('6-fixture'), first = path + '?offset=1', next = path + '?offset=2';
  for (const action of ['signout', 'storefront', 'revision']) {
    const { music, catalog, calls } = stub({
      [personalPath]: { data: [group('6-fixture', { data: [album], next: first })] }
    });
    const original = music.api.music;
    let entered, done;
    const requested = new Promise(resolve => { entered = resolve; });
    const reply = new Promise(resolve => { done = resolve; });
    music.api.music = (path, query) => {
      if (path !== first) return original(path, query);
      calls.push([path, query]);
      entered();
      return reply;
    };
    music.isAuthorized = true;
    let current = true;
    const pending = catalog.recommendations(() => current);
    await requested;
    if (action === 'signout') music.isAuthorized = false;
    if (action === 'storefront') music.storefrontCountryCode = 'us';
    if (action === 'revision') current = false;
    done({ data: { data: [personalList('pl.first')], next } });
    await assert.rejects(pending, /session changed/);
    assert.deepEqual(calls, [[personalPath, undefined], [first, undefined]]);
  }
});

test('artist detail uses official top song, featured album and featured playlist views', async () => {
  const path = root + '/artists/3';
  const {catalog} = stub({
    [path]: { data: [artist] }, [path + '/view/top-songs']: { data: [song('2'), song('1')] },
    [path + '/view/featured-albums']: { data: [album] }, [path + '/view/featured-playlists']: { data: [playlist] }
  });
  const detail = await catalog.detail('artists', '3');
  assert.equal(detail.item.name, 'Artist');
  assert.deepEqual(detail.tracks.map(track => track.id), ['2', '1']);
  assert.equal(detail.albums[0].id, '8');
  assert.equal(detail.playlists[0].id, 'pl.test');
});

test('playlist preserves page order, duplicate positions, and visible unavailable entries', async () => {
  const path = root + '/playlists/pl.test', tracks = path + '/tracks', next = tracks + '?offset=3';
  const {catalog} = stub({
    [path]: { data: [playlist] },
    [tracks]: { data: [song('7'), song('4', 'music-videos'), song('9', 'songs', false)], next },
    [next]: { data: [song('7'), song('2')] }
  });
  const detail = await catalog.detail('playlists', 'pl.test');
  assert.deepEqual(detail.tracks.map(track => track.id), ['7', '4', '9', '7', '2']);
  assert.deepEqual(orderedSelection(detail.tracks, 3), { songs: ['7', '7', '2'], index: 1 });
  assert.equal(orderedSelection(detail.tracks, 1), null);
  assert.equal(orderedSelection(detail.tracks, 2), null);
});

test('album fetches its own track relationship and optional artist cards may fail independently', async () => {
  const release = root + '/albums/8', person = root + '/artists/3';
  const {catalog} = stub({
    [release]: { data: [album] }, [release + '/tracks']: { data: [song('5')] },
    [person]: { data: [artist] }, [person + '/view/top-songs']: { data: [song('5')] }
  });
  assert.equal((await catalog.detail('albums', '8')).tracks[0].id, '5');
  const detail = await catalog.detail('artists', '3');
  assert.equal(detail.albums, null); assert.equal(detail.playlists, null);
});

test('external, cross-resource, and looping continuations never produce a partial playable result', async () => {
  const path = root + '/playlists/pl.test', tracks = path + '/tracks', loop = tracks + '?offset=2';
  for (const next of ['https://evil.example/', root + '/artists/3?offset=2', loop]) {
    const {catalog, calls} = stub({ [path]: { data: [playlist] }, [tracks]: { data: [song('4')], next }, [loop]: { data: [song('2')], next: loop } });
    await assert.rejects(catalog.detail('playlists', 'pl.test'), /continuation|pagination/);
    assert.equal(calls.length, next === loop ? 3 : 2);
  }
  await assert.rejects(stub({}).catalog.detail('playlists', '../other'), /Invalid catalog/);
});

function transport({ result, failStart, queueReady, noObserved = false, ignoresRepeat = false } = {}) {
  const calls = [];
  const sdk = { PlaybackStates: { 1: 'playing', 0: 'none' }, PlayerShuffleMode: {off: 0}, PlayerRepeatMode: {none: 0} };
  const music = { isAuthorized: true, shuffleMode: 1, repeatMode: 2, addEventListener() {}, removeEventListener() {},
    async setQueue(options) {
      calls.push(['queue', options, music.shuffleMode]);
      if (queueReady) await queueReady;
      if (!ignoresRepeat) music.repeatMode = options.repeatMode;
      music.queue = { items: (result ?? options.songs).map(id => ({ id })) };
      return music.queue;
    },
    async changeToMediaAtIndex(index) {
      calls.push(['index', index]);
      if (failStart && calls.filter(([type])=>type === 'index').length === 1) {
        const error = new Error('activation rejected'); error.name = 'NotAllowedError'; throw error;
      }
      if (!noObserved) {
        music.nowPlayingItemIndex = index; music.nowPlayingItem = music.queue.items[index]; music.playbackState = 1;
      }
    },
    async play() { calls.push(['resume']); }
  };
  return {music, calls, flow: createPlaybackFlow(music, sdk, () => {})};
}

test('ordered transport turns shuffle off, verifies sequence, and starts exactly the chosen duplicate index', async () => {
  const {flow, calls, music} = transport();
  await flow.playOrdered(['7', '8', '7', '4'], 2);
  assert.deepEqual(calls, [
    ['queue', { songs: ['7', '8', '7', '4'], startPlaying: false, repeatMode: 0 }, 0], ['index', 2]
  ]);
  assert.equal(music.nowPlayingItem.id, '7');
  assert.equal(music.queue.items[3].id, '4');
  await flow.play();
  assert.deepEqual(calls.at(-1), ['resume']);
});

test('a queue filtered or reordered by the SDK never starts a different song', async () => {
  for (const result of [['7', '4'], ['4', '8', '7']]) {
    const {flow, calls} = transport({ result });
    await flow.playOrdered(['7', '8', '4'], 1);
    await flow.play();
    assert.equal(flow.snapshot().message, 'ORDER_UNAVAILABLE');
    assert.equal(calls.length, 1);
  }
});

test('persistent repeat mode prevents ordered start when SDK exposes its state', async () => {
  const {flow, calls} = transport({ ignoresRepeat: true });
  await flow.playOrdered(['7', '8', '4'], 1);
  assert.equal(flow.snapshot().message, 'ORDER_UNAVAILABLE');
  assert.equal(calls.length, 1);
});

test('new Play click retries the chosen index if browser blocks activation or confirmation is missing', async () => {
  for (const option of [{ failStart: true }, { noObserved: true }]) {
    const {flow, calls} = transport(option);
    await flow.playOrdered(['7', '8', '4'], 1);
    assert.equal(flow.snapshot().message, 'CLICK_TO_START');
    await flow.play();
    assert.deepEqual(calls.slice(1), [['index', 1], ['index', 1]]);
  }
});

test('close or authorization loss while loading never starts queued audio', async () => {
  for (const end of ['close', 'signout']) {
    let release;
    const {music, flow, calls} = transport({queueReady:new Promise(resolve=>{release = resolve;})});
    const play = flow.playOrdered(['7', '8'], 0);
    if (end === 'close') flow.close(); else music.isAuthorized = false;
    release(); await play;
    assert.equal(calls.length, 1);
  }
});
