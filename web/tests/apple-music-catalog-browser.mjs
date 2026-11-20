// Author: dishanhai. Local interaction smoke test with a simulated MusicKit SDK, no Apple accounts or network.
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { readFile } from 'node:fs/promises';
import { chromium, eventually } from './chromium.mjs';

const asset = new URL('../../src/main/resources/assets/sh_better_ui/music/apple-login/', import.meta.url);
const mock = `
const id = 'pl.fixture', artist = { id: '3', type: 'artists', attributes: { name: 'Fixture Artist' } };
const playlist = { id, type: 'playlists', attributes: { name: 'Fixture Playlist' } };
const personal = { id: 'pl.pm-fixture', type: 'playlists', href: '/v1/catalog/tw/playlists/pl.pm-fixture',
  attributes: { name: 'Personal fixture', playlistType: 'personal-mix' } };
const album = { id: '99', type: 'albums', attributes: { name: 'Fixture Album' } };
const song = (id, name, type = 'songs') => ({ id, type, attributes: { name, playParams: { id } } });
const data = {
    '/v1/catalog/tw/search': { results: { artists: { data: [artist] } } },
    '/v1/catalog/tw/charts': { results: { playlists: [{ data: [playlist] }] } },
    '/v1/catalog/tw/artists/3': { data: [artist] },
    '/v1/catalog/tw/artists/3/view/top-songs': { data: [song('4', 'Artist Song')] },
    '/v1/catalog/tw/artists/3/view/featured-albums': { data: [album] },
    '/v1/catalog/tw/artists/3/view/featured-playlists': { data: [playlist] },
    '/v1/catalog/tw/albums/99': { data: [album] },
    '/v1/catalog/tw/albums/99/tracks': { data: [song('9', 'Album Song')] }
};
data['/v1/me/recommendations'] = { data: [{ type: 'personal-recommendation', relationships: { contents: { data: [personal] } } }] };
data['/v1/catalog/tw/playlists/pl.pm-fixture'] = { data: [personal] };
data['/v1/catalog/tw/playlists/pl.pm-fixture/tracks'] = { data: [song('3', 'Personal fixture song')] };
data['/v1/catalog/tw/playlists/' + id] = { data: [playlist] };
data['/v1/catalog/tw/playlists/' + id + '/tracks'] = { data: [song('7', 'First'), song('2', 'Video', 'music-videos'), song('8', 'Second'), song('7', 'Third')] };
const player = new EventTarget();
Object.assign(player, {
  isAuthorized: false, storefrontCountryCode: 'tw', shuffleMode: 1, playbackState: 0, playbackRate: 1, repeatMode: 2,
  api: { music: async (path) => {
    window.fixtureRequests.push(path); return { data: data[path] ?? { errors: ['unexpected path'] } };
  } },
  async authorize() { this.isAuthorized = true; this.dispatchEvent(new Event('authorizationStatusDidChange')); },
  async unauthorize() { this.isAuthorized = false; this.dispatchEvent(new Event('authorizationStatusDidChange')); },
  async setQueue(options) {
    window.fixtureCalls.push(['queue', options, this.shuffleMode]);
    this.repeatMode = options.repeatMode;
    this.queue = { items: options.songs.map(id => ({ id, title: 'Selected title' })) }; return this.queue;
  },
  async changeToMediaAtIndex(index) {
    window.fixtureCalls.push(['index', index]);
    this.nowPlayingItemIndex = index; this.nowPlayingItem = this.queue.items[index]; this.playbackState = 1;
    this.dispatchEvent(new Event('nowPlayingItemDidChange'));
  }
});
window.fixtureCalls = []; window.fixtureRequests = [];
window.MusicKit = { configure: async () => {}, getInstance: () => player, PlaybackStates: { 0: 'none', 1: 'playing' },
  PlayerShuffleMode: { off: 0 }, PlayerRepeatMode: { none: 0 } };
document.dispatchEvent(new Event('musickitloaded'));
`;
const routes = new Set(['/login.mjs','/auth-flow.mjs','/playback-flow.mjs','/catalog-flow.mjs','/catalog-view.mjs','/rate-bridge.mjs','/']);
const server = createServer(async (request, response) => {
  const path = request.url;
  if (path === '/config' || path === '/state' || path === '/status' || path === '/playback-rate') {
    response.setHeader('Content-Type', 'application/json');
    response.end(path === '/config' ? '{"developerToken":"test.only.placeholder"}' : '{}');
  } else if (path === '/fixture-musickit.js') {
    response.setHeader('Content-Type', 'text/javascript'); response.end(mock);
  } else if (routes.has(path)) {
    const name = path === '/' ? 'index.html' : path.slice(1);
    const content = await readFile(new URL(name, asset), 'utf8');
    response.setHeader('Content-Type', path === '/' ? 'text/html; charset=utf-8' : 'text/javascript');
    response.end(content.replace('https://js-cdn.music.apple.com/musickit/v3/musickit.js', '/fixture-musickit.js'));
  } else { response.statusCode = 404; response.end(); }
});
let browser;
try {
  server.listen(0,'127.0.0.1'); await once(server,'listening');
  browser = await chromium();
  const { command, call } = browser;
  const click = selector => call(selector => document.querySelector(selector).click(), selector);
  const visible = selector => call(selector => Boolean(document.querySelector(selector)?.getClientRects().length), selector);
  await command('Page.enable');
  await command('Page.navigate', { url: `http://127.0.0.1:${server.address().port}/#${'a'.repeat(43)}` });
  await eventually(() => call(() => !document.querySelector('#catalog-charts')?.disabled && Boolean(window.fixtureCalls)), 'mocked companion');
  await call(() => {
    document.querySelector('#catalog-term').value = 'Fixture';
    document.querySelector('#catalog-browse form').requestSubmit();
  });
  await eventually(() => visible('[data-catalog-entry="artists/3"]'), 'artist result');
  await click('[data-catalog-entry="artists/3"]');
  await eventually(() => visible('[data-catalog-entry="albums/99"]'), 'representative album');
  assert.equal(await call(() => document.activeElement.textContent), 'Fixture Artist');
  assert.equal(await call(() => document.querySelector('#catalog-detail [data-play-row]').disabled), true);
  await click('[data-catalog-entry="albums/99"]');
  await eventually(() => call(() => document.activeElement.textContent === 'Fixture Album'), 'album detail');
  await click('#catalog-detail > button');
  assert.equal(await call(() => document.activeElement.dataset.catalogEntry), 'albums/99');
  await click('[data-catalog-entry="playlists/pl.fixture"]');
  await eventually(() => visible('.catalog-tracks [data-play-row="2"]'), 'artist curated playlist detail');
  assert.deepEqual(await call(() => window.fixtureCalls), []);
  await click('#connect');
  await eventually(() => call(() => !document.querySelector('.catalog-tracks [data-play-row="2"]').disabled), 'authorized ordered playback');
  await click('.catalog-tracks [data-play-row="2"]');
  await eventually(() => call(() => /Selected title/.test(document.querySelector('#playback-status').textContent)), 'observed SDK playback');
  assert.deepEqual(await call(() => window.fixtureCalls), [
    ['queue', { songs: ['7','8','7'], startPlaying: false, repeatMode: 0 }, 0], ['index', 1]
  ]);
  await call(() => { document.querySelector('#playback-rate').value = '1.5'; });
  await click('#apply-rate');
  await eventually(() => call(() => /1.5/.test(document.querySelector('#rate-status').textContent)), 'rate on catalog song');
  await click('#catalog-detail > button');
  await click('#catalog-detail > button');
  assert.equal(await call(() => document.querySelector('#catalog-term').value), 'Fixture');
  await click('#catalog-charts');
  await eventually(() => visible('[data-catalog-entry="playlists/pl.fixture"]'), 'storefront chart');
  await click('[data-catalog-entry="playlists/pl.fixture"]');
  await eventually(() => visible('.catalog-tracks'), 'chart playlist detail');
  await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
  assert.equal(await call(() => document.documentElement.scrollWidth === document.documentElement.clientWidth), true);
  assert.equal(await call(() => window.fixtureCalls.length), 2); // metadata navigation never touched playback
  assert.equal(await call(() => window.fixtureRequests.includes('/v1/me/recommendations')), false);
  await click('#catalog-detail > button');
  await click('#catalog-recommendations');
  await eventually(() => visible('[data-catalog-entry="playlists/pl.pm-fixture"]'), 'opted-in personalized playlist');
  await click('[data-catalog-entry="playlists/pl.pm-fixture"]');
  await eventually(() => call(() => document.activeElement.textContent === 'Personal fixture'), 'personal playlist detail');
  await click('#disconnect');
  await eventually(() => call(() => document.querySelector('#catalog-detail').hidden &&
    document.querySelector('#catalog-recommendations').disabled && !document.body.textContent.includes('Personal fixture')), 'clear personalized content on disconnect');
  console.log('Simulated MusicKit browser detail, ordered playback and opt-in recommendations passed.');
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
