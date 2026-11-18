// Author: dishanhai
import { mountGlassKit } from './kit.mjs';
const kit = mountGlassKit(document.querySelector('#glass-kit'));
const button = document.querySelector('#play');
button.addEventListener('click', () => {
  const playing = button.getAttribute('aria-pressed') !== 'true';
  button.setAttribute('aria-pressed', String(playing));
  button.textContent = playing ? '暂停演示' : '播放演示';
  document.querySelector('#play-status').textContent = playing ? '演示播放中 · 无音频' : '演示已暂停';
});
button.setAttribute('aria-pressed', 'false');
window.addEventListener('pagehide', () => kit.dispose(), { once: true });
window.glassKit = kit;
