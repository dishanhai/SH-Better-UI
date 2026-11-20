// Author: dishanhai. Never read, copy, transmit, or log musicUserToken.
import { createAuthorizationFlow } from './auth-flow.mjs';
import { createPlaybackFlow } from './playback-flow.mjs';
import { createRateBridge } from './rate-bridge.mjs';
import { createCatalog } from './catalog-flow.mjs';
import { createCatalogView } from './catalog-view.mjs';
const status = document.getElementById('status');
const connect = document.getElementById('connect');
const cancel = document.getElementById('cancel');
const disconnect = document.getElementById('disconnect');
const session = location.hash.slice(1);
history.replaceState(null, '', '/');
const labels = {
  WAITING: '已准备。点击按钮，在 Apple 官方窗口授权。',
  AUTHORIZING: '请在 Apple 窗口完成授权。',
  AUTHORIZED: 'Apple Music 浏览器会话已授权。尚未验证游戏内播放或订阅能力。',
  SIGNED_OUT: '已断开 Apple Music 授权。',
  CANCELLED: '本次授权已取消。请关闭 Apple 弹窗；若它仍完成授权，会立即尝试撤销。',
  FAILED: '授权未完成。请检查弹窗权限、开发者配置或稍后重试；不会显示敏感错误详情。'
};
async function request(url, options) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 4000);
  try { return await fetch(url, { ...options, signal: controller.signal }); }
  finally { clearTimeout(timer); }
}
let flow;
let playback;
let catalogView;
const loadSong = document.getElementById('load-song');
const playSong = document.getElementById('play-song');
const pauseSong = document.getElementById('pause-song');
const seekSong = document.getElementById('seek-song');
const rateInput = document.getElementById('playback-rate');
const rateApply = document.getElementById('apply-rate');
const playbackStatus = document.getElementById('playback-status');
const playbackLabels = { IDLE: '等待加载歌曲', LOGIN_REQUIRED: '请先授权', INVALID_ID: '请输入数字目录歌曲 ID',
  INVALID_RATE: '倍率范围为 0.5–2.0', RATE_UNAVAILABLE: '当前浏览器播放器不支持倍率或正在忙碌', RATE_CHANGING: '正在请求倍率',
  ORDER_UNAVAILABLE: 'MusicKit 无法保留这份列表的顺序，未播放其他歌曲', CLICK_TO_START: '浏览器要求再次点击。请点「播放 / 继续」播放所选曲目',
  INVALID_TIME: '请输入当前歌曲时长以内的秒数', LOADING: '加载中', STARTING: '已请求播放',
  PAUSING: '已请求暂停', SEEKING: '已请求跳转', OBSERVED: '命令已返回，以 SDK 状态为准',
  FAILED: '操作失败。请检查订阅、地区、歌曲可用性及浏览器播放支持；不会显示敏感错误详情' };
function paintPlayback() {
  if (!playback) return;
  const state = playback.snapshot();
  const blocked = flow.busy || state.busy || !state.authorized;
  loadSong.disabled = blocked;
  playSong.disabled = blocked || !state.ready;
  pauseSong.disabled = blocked || (!state.ready && !['playing', 'loading'].includes(state.state));
  seekSong.disabled = blocked || !state.ready || state.duration <= 0;
  rateInput.disabled = rateApply.disabled = blocked || !state.supportsPlaybackRate;
  document.getElementById('rate-status').textContent = state.supportsPlaybackRate
    ? `SDK 实测倍率：${state.playbackRate}×` : '倍率不可用：需要已授权、已加载且支持此属性的 MusicKit 播放器';
  const message = `${playbackLabels[state.message]} · MusicKit 状态：${state.state}${state.nowPlaying ? ` · ${state.nowPlaying}` : state.songId ? ` · 已加载歌曲 ${state.songId}` : ''}`;
  if (playbackStatus.textContent !== message) playbackStatus.textContent = message;
  catalogView?.update(blocked, state.authorized);
  document.getElementById('playback-time').textContent = `${state.seconds.toFixed(1)} / ${state.duration.toFixed(1)} 秒`;
}
function transport(action) {
  if (!playback || flow.busy) return;
  const operation = action(); paint(); operation.finally(paint);
}
loadSong.addEventListener('click', () => transport(() => playback.prepare(document.getElementById('song-id').value.trim())));
playSong.addEventListener('click', () => transport(() => playback.play()));
pauseSong.addEventListener('click', () => transport(() => playback.pause()));
rateApply.addEventListener('click', () => transport(() => playback.playbackRate(rateInput.valueAsNumber)));
seekSong.addEventListener('click', () => transport(() => playback.seek(document.getElementById('seek-time').valueAsNumber)));
let bridgeFailed = false;
function paint() {
  if (!flow) return;
  connect.disabled = flow.busy || playback?.snapshot().busy || flow.state === 'AUTHORIZED';
  disconnect.disabled = flow.busy || playback?.snapshot().busy || !flow.authorized;
  cancel.style.display = flow.busy ? 'inline-block' : 'none';
  paintPlayback();
}
// Serialize state notifications so an older AUTHORIZING callback cannot overwrite AUTHORIZED.
let notifications = Promise.resolve();
function report(state) {
  status.textContent = labels[state];
  notifications = notifications.then(async () => {
    try {
      const response = await request('/state', { method: 'POST', credentials: 'omit', cache: 'no-store',
        headers: { 'Content-Type': 'application/json', 'X-SHBUI-Session': session }, body: JSON.stringify({ state }) });
      if (!response.ok) throw new Error();
    } catch { bridgeFailed = true; }
    if (bridgeFailed) status.textContent = labels[flow.state] + ' 游戏连接已失效，请重新从游戏开启；本页仍可断开 Apple 授权。';
  });
}
connect.addEventListener('click', () => { const operation = flow.connect(); paint(); operation.finally(paint); });
cancel.addEventListener('click', () => { flow.cancel(); paint(); });
disconnect.addEventListener('click', () => { const operation = flow.disconnect(); paint(); operation.finally(paint); });
try {
  if (!/^[A-Za-z0-9_-]{43}$/.test(session)) throw new Error();
  const configResponse = await request('/config', { credentials: 'omit', cache: 'no-store', headers: { 'X-SHBUI-Session': session } });
  if (!configResponse.ok) throw new Error();
  const config = await configResponse.json();
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error()), 20000);
    document.addEventListener('musickitloaded', () => { clearTimeout(timer); resolve(); }, { once: true });
    const script = document.createElement('script');
    script.src = 'https://js-cdn.music.apple.com/musickit/v3/musickit.js';
    script.async = true;
    script.onerror = () => { clearTimeout(timer); reject(new Error()); };
    document.head.append(script);
  });
  await MusicKit.configure({ developerToken: config.developerToken, app: { name: 'SH-Better-UI', build: '1' } });
  const music = MusicKit.getInstance();
  flow = createAuthorizationFlow(music, report);
  playback = createPlaybackFlow(music, MusicKit, () => paint());
  catalogView = createCatalogView(document.getElementById('catalog'), createCatalog(music), music,
    (songs, index) => transport(() => playback.playOrdered(songs, index)));
  status.textContent = labels.WAITING;
  paint();
  const rateBridge = createRateBridge(playback, async (method, body) => {
    const response = await request('/playback-rate', { method, credentials: 'omit', cache: 'no-store',
      headers: { 'Content-Type': 'application/json', 'X-SHBUI-Session': session },
      ...(body ? { body: JSON.stringify(body) } : {}) });
    if (!response.ok) throw new Error('Rate bridge unavailable');
    return response.json();
  });
  const ratePoll = setInterval(() => rateBridge.poll().catch(() => {}), 1000);
  const heartbeat = setInterval(async () => {
    try {
      const response = await request('/status', { credentials: 'omit', cache: 'no-store', headers: { 'X-SHBUI-Session': session } });
      if (!response.ok) throw new Error();
    } catch {
      bridgeFailed = true;
      status.textContent = labels[flow.state] + ' 游戏连接已失效，请重新从游戏开启；本页仍可断开 Apple 授权。';
      clearInterval(heartbeat);
    }
  }, 5000);
  addEventListener('pagehide', () => { clearInterval(heartbeat); clearInterval(ratePoll); rateBridge.close(); catalogView.close(); playback.close(); }, { once: true });
} catch {
  status.textContent = '无法初始化官方 MusicKit。需要有效的项目开发者 token，以及 Apple 接受的网页来源/浏览器。请重新从游戏发起；仅登录玩家账户不能替代开发者配置。';
  connect.disabled = true;
}
