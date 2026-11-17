// Author: dishanhai. Never read, copy, transmit, or log musicUserToken.
import { createAuthorizationFlow } from './auth-flow.mjs';
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
let bridgeFailed = false;
function paint() {
  if (!flow) return;
  connect.disabled = flow.busy || flow.state === 'AUTHORIZED';
  disconnect.disabled = flow.busy || !flow.authorized;
  cancel.style.display = flow.busy ? 'inline-block' : 'none';
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
  flow = createAuthorizationFlow(MusicKit.getInstance(), report);
  status.textContent = labels.WAITING;
  paint();
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
  addEventListener('pagehide', () => clearInterval(heartbeat), { once: true });
} catch {
  status.textContent = '无法初始化官方 MusicKit。需要有效的项目开发者 token，以及 Apple 接受的网页来源/浏览器。请重新从游戏发起；仅登录玩家账户不能替代开发者配置。';
  connect.disabled = true;
}
