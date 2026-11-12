// Author: dishanhai
import { createPreset, importPreset, PRESET_LIMIT, resolveMaterial, searchMaterials, validateParameters } from './catalog-contract.mjs';
import { formatValue, option, parameterLabel, showParameterFields } from './parameter-fields.mjs';
import { PreviewClock, replayFrame } from './preview-clock.mjs';
import { openRenderer } from './renderers/registry.mjs';
import { decodeShare, encodeShare } from './share.mjs';

const get = id => document.getElementById(id);
const canvas = get('previewCanvas');
const stage = get('stage');
const motion = matchMedia('(prefers-reduced-motion: reduce)');
const dark = matchMedia('(prefers-color-scheme: dark)');
const storedKey = 'sh-better-ui:material-presets:v1';
const state = { catalog: null, entry: null, drafts: new Map(), results: [], renderer: null, request: null, clock: null };
let raf = 0;
let benchVisible = true;
let liveAction = null;
let previewPointerId = null;

const categories = { glass: '玻璃', surface: '表面', lighting: '光效', spatial: '空间', distortion: '扭曲',
  particle: '粒子', animation: '动画', masking: '遮罩', composition: '合成', background: '动态背景' };
const webStatus = { 'native-only': '仅原生', 'web-unsupported': 'Web 不可用', 'web-approximation': 'Web 近似', 'web-verified': 'Web 已验证' };
const nativeStatus = { 'source-verified': '源码已核对', 'client-verified': '客户端已验证', unsupported: '不适用', unverified: '待验证' };
const reasonLabels = { id: '稳定 ID', alias: '已登记别名', name: '名称', api: '公开 API', tag: '标签或能力', visual: '用途或视觉特征' };
const glassId = 'sh_better_ui:liquid_glass';

function make(tag, value, className) {
  const node = document.createElement(tag);
  node.textContent = value;
  if (className) node.className = className;
  return node;
}

function materialName(entry) {
  const translated = entry.localizedNames.zh_cn;
  return translated && translated !== entry.name ? `${translated} · ${entry.name}` : entry.name;
}

function report(text, error = false) {
  const feedback = get('feedback');
  feedback.dataset.error = String(error);
  feedback.textContent = text;
}

function message(error) { return (error?.message ?? String(error)).slice(0, 800); }
function side() { return get('draftSide').value; }
function defaults(entry) { return validateParameters(entry); }

function draft(entry = state.entry) {
  if (!state.drafts.has(entry.id)) state.drafts.set(entry.id, { a: defaults(entry), b: defaults(entry) });
  return state.drafts.get(entry.id);
}

function matchDescription(result) {
  if (result.reasons[0]?.kind === 'fallback') return `未精确匹配，展示目录中最接近的候选。缺少 ${result.missing.join('、')} 的匹配证据。`;
  const matched = result.reasons.map(({ kind, value }) => `${reasonLabels[kind] ?? kind}匹配「${value}」`).join(' · ');
  return result.missing.length ? `${matched}。缺少 ${result.missing.join('、')} 的匹配证据。` : matched;
}

function renderResults() {
  const tag = get('filterTag').value;
  const query = get('query').value;
  state.results = searchMaterials(state.catalog, query, {
    category: get('filterCategory').value,
    tags: tag ? [tag] : [],
    loader: get('filterLoader').value,
    capability: get('filterCapability').value,
    dynamic: get('filterDynamic').checked,
    webOnly: get('filterWeb').checked,
  });
  get('resultCount').textContent = state.results.length
    ? `${state.results.length} 条${state.results[0].score === 0 && query.trim() ? '候选' : '结果'}`
    : '当前目录没有符合筛选条件的材质。';
  const items = state.results.map(result => {
    const { entry } = result;
    const item = document.createElement('li');
    const button = make('button', '', 'material-button');
    button.type = 'button';
    button.dataset.material = entry.id;
    button.setAttribute('aria-current', String(entry.id === state.entry?.id));
    button.append(make('strong', materialName(entry)), make('code', entry.id));
    button.append(make('span', entry.useCases.slice(0, 2).join(' · ') || entry.description));
    const matched = matchDescription(result);
    if (matched) button.append(make('span', matched, 'result-reason'));
    button.append(make('span', `${webStatus[entry.webCompatibility] ?? 'Web 待验证'} · Forge ${nativeStatus[entry.loaderCompatibility.forge] ?? '待验证'} · NeoForge ${nativeStatus[entry.loaderCompatibility.neoforge] ?? '待验证'}`));
    button.append(make('span', `${entry.nativePreviewId ? '原生预览入口' : '原生入口待接入'} · ${Object.keys(entry.parameters).length} 项参数 · ${nativeStatus[entry.validationStatus] ?? entry.validationStatus}`));
    const api = make('span', entry.publicEntrypoint.split('.').at(-1));
    api.title = entry.publicEntrypoint;
    const source = make('span', `源码 ${entry.sourceFiles[0]?.split('/').at(-1) ?? '待登记'}`);
    source.title = entry.sourceFiles[0] ?? '';
    button.append(api, source);
    button.addEventListener('click', () => selectMaterial(entry, true));
    item.append(button);
    return item;
  });
  get('results').replaceChildren(...items);
  showMatch();
}

function showMatch() {
  const match = state.results.find(result => result.entry.id === state.entry?.id);
  const reason = match ? matchDescription(match) : '';
  get('matchReason').textContent = reason;
  get('matchReason').hidden = !reason;
}

function showRoutes(entry) {
  const route = get('nativeRoute');
  route.replaceChildren(...(entry.nativePreviewId
    ? [document.createTextNode('在游戏开发者画廊中按 ID 查找 '), make('code', entry.nativePreviewId)]
    : [document.createTextNode('目录尚未登记原生预览入口。')]));
  get('compatibility').replaceChildren(...[['Forge 1.20.1', 'forge'], ['NeoForge 1.21.1', 'neoforge']].map(([name, key]) =>
    make('span', `${name} · ${nativeStatus[entry.loaderCompatibility[key]] ?? '待验证'}`)));
}

function evidenceList(title, values) {
  const list = document.createElement('ul');
  list.append(...values.map(value => {
    const line = make('li', '');
    line.append(make('code', typeof value === 'string' ? value : JSON.stringify(value)));
    return line;
  }));
  return [make('p', title), list];
}

function showEvidence(entry) {
  const evidence = get('evidenceBody');
  evidence.replaceChildren(make('p', `版本 ${entry.materialRevision} · ${nativeStatus[entry.validationStatus] ?? entry.validationStatus}`),
    ...evidenceList('公开入口与符号', entry.publicSymbols),
    ...evidenceList('渲染阶段', entry.renderPassDependencies),
    ...evidenceList('源码与工作树基准', [entry.sourceRepository, entry.sourceRevision, ...entry.sourceFiles]),
    ...evidenceList('验证证据', entry.validationEvidence));
  if (entry.id === glassId) {
    const sample = make('pre', 'context.glass(List.of(new GlassDraw(\n    GlassRegion.rounded(x, y, width, height, 12),\n    GlassMaterial.preset(GlassVariant.REGULAR))));');
    evidence.append(make('p', '在 RenderService.Context 的绘制回调内调用'), sample);
  }
}

function showDiff() {
  const { a, b } = draft();
  const differences = Object.keys(a).filter(name => JSON.stringify(a[name]) !== JSON.stringify(b[name]))
    .map(name => make('li', `${parameterLabel(name)} · A ${formatValue(a[name])} / B ${formatValue(b[name])}`));
  get('diffList').replaceChildren(...(differences.length ? differences : [make('li', '两份草稿相同') ]));
}

function changeParameter(name, readValue) {
  try {
    const sides = draft();
    const selected = side();
    sides[selected] = validateParameters(state.entry, { ...sides[selected], [name]: readValue() });
    report('');
    showDiff();
    requestFrame();
    return true;
  } catch (error) {
    report(`参数未更新。${message(error)}`, true);
    return false;
  }
}

function showDraft() {
  showParameterFields(get('parameterFields'), state.entry, draft()[side()], changeParameter);
  showDiff();
  requestFrame();
}

function closeRenderer() {
  state.request?.abort();
  state.request = null;
  state.renderer?.dispose();
  state.renderer = null;
  state.clock = null;
  liveAction = null;
  if (previewPointerId !== null && canvas.hasPointerCapture(previewPointerId)) canvas.releasePointerCapture(previewPointerId);
  previewPointerId = null;
  canvas.dataset.manual = 'false';
  get('previewPress').setAttribute('aria-pressed', 'false');
  get('previewPress').textContent = '按住预览指针';
  stopFrames();
  canvas.dataset.ready = 'false';
  get('previewControls').disabled = true;
  stage.hidden = true;
}

function showFallback(text, reference = false) {
  stage.hidden = true;
  get('fallback').hidden = false;
  get('previewStatus').textContent = text;
  get('stageSize').textContent = 'sRGB';
  get('reference').hidden = !reference;
}

function rendererFailed(error) {
  closeRenderer();
  showFallback(`${message(error)}。可继续查看原生预览、公开 API 和参数。`, state.entry?.id === glassId);
}

async function mountRenderer(entry) {
  closeRenderer();
  if (!entry.webRendererId) {
    showFallback('此材质的动态预览在 Minecraft 原生画廊中提供。');
    return;
  }
  showFallback('正在加载场景和编译 WebGL 2 着色器…');
  const request = new AbortController();
  state.request = request;
  try {
    const renderer = await openRenderer(entry.webRendererId, canvas, { entry, signal: request.signal });
    if (request.signal.aborted) {
      renderer.dispose();
      return;
    }
    state.renderer = renderer;
    state.request = null;
    state.clock = new PreviewClock(renderer.fixture.durationMs, renderer.fixture.fps);
    state.clock.setPlaybackSpeed(Number(get('playbackSpeed').value));
    if (!motion.matches) state.clock.resume();
    else if (renderer.fixture.stillTimeMs) state.clock.seek(renderer.fixture.stillTimeMs);
    get('time').max = renderer.fixture.durationMs;
    get('previewControls').disabled = false;
    get('fallback').hidden = true;
    stage.hidden = false;
    syncInputControls();
    syncClock();
    requestFrame();
  } catch (error) {
    if (!request.signal.aborted) rendererFailed(error);
  }
}

function selectMaterial(entry, focus = false) {
  const changed = state.entry !== entry;
  state.entry = entry;
  get('material-title').textContent = materialName(entry);
  get('materialId').textContent = entry.id;
  get('materialDescription').textContent = entry.description;
  get('webBadge').textContent = webStatus[entry.webCompatibility] ?? 'Web 待验证';
  get('webBadge').hidden = false;
  get('opticalControl').hidden = entry.webRendererId !== 'managed-liquid-glass';
  canvas.setAttribute('aria-label', `${entry.localizedNames.zh_cn ?? entry.name}预览，可用控件调整指针和时间`);
  for (const button of get('results').querySelectorAll('button')) {
    button.setAttribute('aria-current', String(button.dataset.material === entry.id));
  }
  showMatch();
  showRoutes(entry);
  showEvidence(entry);
  showDraft();
  if (changed) {
    get('shareField').hidden = true;
    mountRenderer(entry);
  }
  if (focus) get('material-title').focus({ preventScroll: matchMedia('(min-width: 47rem)').matches });
}

function syncClock() {
  const clock = state.clock;
  if (!clock) return;
  get('time').value = Math.round(clock.timeMs);
  get('timeValue').textContent = `${(clock.timeMs / 1000).toFixed(3)} s`;
  get('play').textContent = clock.playing ? '暂停' : '播放';
}

function requestFrame() {
  if (!raf && state.renderer && benchVisible && !document.hidden) raf = requestAnimationFrame(renderFrame);
}

function stopFrames() {
  cancelAnimationFrame(raf);
  raf = 0;
  state.clock?.suspend();
}

function renderFrame(realTimeMs) {
  raf = 0;
  if (!state.renderer || !benchVisible || document.hidden) return;
  const clock = state.clock;
  const renderer = state.renderer;
  const pointer = get('inputMode').value === 'manual' ? manualPointer() : null;
  const action = liveAction ? {
    origin: liveAction.origin,
    end: liveAction.end,
    pressed: liveAction.releasedAt === null,
    sincePress: Math.max(motion.matches ? .12 : 0, (realTimeMs - liveAction.pressedAt) / 1000),
    sinceRelease: liveAction.releasedAt === null ? 100 :
      Math.max(motion.matches ? .65 : 0, (realTimeMs - liveAction.releasedAt) / 1000),
  } : undefined;
  const mode = get('compareMode').value;
  const parameters = draft();
  const a = mode === 'single' ? parameters[side()] : parameters.a;
  const divider = Number(get('divider').value);
  const dpi = Number(get('density').value);
  clock.tick(realTimeMs);
  try {
    renderer.render({ frame: replayFrame(renderer.fixture, clock.timeMs, pointer, action), a, b: parameters.b, mode,
      divider, dpi, zoomed: get('sceneMode').value === 'zoom', highContrast: get('highContrast').checked,
      opticalProfile: get('opticalProfile').value });
    canvas.dataset.ready = 'true';
  } catch (error) {
    if (error instanceof RangeError && dpi > 1) {
      get('density').value = '1';
      report(`${message(error)}，已恢复 1× 像素密度。`, true);
      requestFrame();
      return;
    }
    rendererFailed(error);
    return;
  }
  syncClock();
  stage.dataset.mode = mode;
  stage.style.setProperty('--divider', `${divider}%`);
  stage.style.setProperty('--mark-a', a.foreground === 'DARK' ? '#000' : '#fff');
  stage.style.setProperty('--mark-b', parameters.b.foreground === 'DARK' ? '#000' : '#fff');
  const prefix = state.entry.id === glassId ? 'GLASS / ' : '';
  stage.dataset.renderer = state.entry.webRendererId;
  stage.querySelector('.stage-mark-a').textContent = prefix + (mode === 'single' ? side().toUpperCase() : 'A');
  stage.querySelector('.stage-mark-b').textContent = prefix + 'B';
  get('stageSize').textContent = `${canvas.width} × ${canvas.height} · sRGB`;
  if (clock.playing || (pointer && action && !motion.matches && Math.min(action.sincePress, action.sinceRelease) < 1.85)) requestFrame();
}

function manualPointer() { return { x: Number(get('pointerX').value), y: Number(get('pointerY').value) }; }

function syncInputControls() {
  const manual = get('inputMode').value === 'manual';
  const effect = !!state.renderer?.acceptsPress;
  get('pointerX').disabled = get('pointerY').disabled = !manual;
  get('previewPress').hidden = !manual || !effect;
  canvas.dataset.manual = String(manual && effect);
}

function pressPreview() {
  const { x, y } = manualPointer();
  liveAction = { origin: [x, y], pressedAt: performance.now(), releasedAt: null };
  get('previewPress').setAttribute('aria-pressed', 'true');
  get('previewPress').textContent = '松开预览指针';
  requestFrame();
}

function releasePreview(cancelled = false) {
  if (cancelled) liveAction = null;
  else if (liveAction?.releasedAt === null) {
    const { x, y } = manualPointer();
    liveAction.end = [x,y];
    liveAction.releasedAt = performance.now();
  }
  get('previewPress').setAttribute('aria-pressed', 'false');
  get('previewPress').textContent = '按住预览指针';
  requestFrame();
}

function movePreviewPointer(event) {
  const bounds = canvas.getBoundingClientRect();
  let x = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width));
  if (get('compareMode').value === 'split') x = x < .5 ? x * 2 : x * 2 - 1;
  get('pointerX').value = x;
  get('pointerY').value = Math.max(0, Math.min(1, (event.clientY - bounds.top) / bounds.height));
  requestFrame();
}

function applyPreset(text) {
  try {
    const preset = importPreset(state.catalog, text);
    const entry = resolveMaterial(state.catalog, preset.materialId);
    draft(entry)[side()] = preset.parameters;
    selectMaterial(entry);
    report(`已载入 ${preset.materialId} 到草稿 ${side().toUpperCase()}。`);
    return true;
  } catch (error) {
    report(`预设未导入。${message(error)}`, true);
    return false;
  }
}

function presetText() { return JSON.stringify(createPreset(state.catalog, state.entry.id, draft()[side()]), null, 2); }

function readSaved() {
  const raw = localStorage.getItem(storedKey);
  if (!raw) return [];
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) return [];
  return parsed.filter(item => typeof item?.name === 'string' && typeof item?.text === 'string').slice(0, 12);
}

function showSaved() {
  const list = get('savedPresets');
  try {
    list.replaceChildren(option('', '选择预设'), ...readSaved().map(({ name }, index) => option(index, name)));
  } catch {
    list.replaceChildren(option('', '浏览器存储不可用'));
  }
}

function updateSaved(update) {
  try {
    const next = update(readSaved());
    localStorage.setItem(storedKey, JSON.stringify(next));
    showSaved();
    report('浏览器预设已更新。');
  } catch (error) {
    report(`浏览器预设未更新。${message(error)}`, true);
  }
}

function setupPresetEvents() {
  get('importText').addEventListener('click', () => applyPreset(get('presetText').value));
  get('presetFile').addEventListener('change', async ({ target }) => {
    const file = target.files[0];
    if (!file) return;
    try {
      if (file.size > PRESET_LIMIT) throw new Error('预设不能大于 64 KiB');
      applyPreset(await file.text());
    } catch (error) {
      report(`预设未导入。${message(error)}`, true);
    } finally {
      target.value = '';
    }
  });
  get('exportPreset').addEventListener('click', () => {
    try {
      const url = URL.createObjectURL(new Blob([presetText()], { type: 'application/json' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `${state.entry.id.replace(':', '-')}-${side()}.json`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      report('已生成预设 JSON。');
    } catch (error) {
      report(message(error), true);
    }
  });
  get('sharePreset').addEventListener('click', async () => {
    try {
      const url = new URL(location.href);
      url.hash = encodeShare(presetText());
      get('shareUrl').value = url.href;
      get('shareField').hidden = false;
      try {
        await navigator.clipboard.writeText(url.href);
        report('已复制分享链接。');
      } catch {
        get('shareUrl').select();
        report('分享链接已生成，可手动复制。');
      }
    } catch (error) {
      report(`${message(error)}。可改用 JSON 导出。`, true);
    }
  });
  get('savePreset').addEventListener('click', () => {
    try {
      const name = get('presetName').value.trim();
      if (!name) throw new Error('请输入预设名称');
      const text = presetText();
      updateSaved(items => [{ name, text }, ...items.filter(item => item.name !== name)].slice(0, 12));
    } catch (error) {
      report(message(error), true);
    }
  });
  get('loadSavedPreset').addEventListener('click', () => {
    try {
      const selection = get('savedPresets').value;
      if (selection !== '') applyPreset(readSaved()[Number(selection)].text);
    } catch (error) {
      report(message(error), true);
    }
  });
  get('removeSavedPreset').addEventListener('click', () => {
    const selection = get('savedPresets').value;
    if (selection !== '') updateSaved(items => items.filter((_, index) => index !== Number(selection)));
  });
}

function setupPreviewEvents() {
  for (const id of ['sceneMode', 'density', 'compareMode', 'highContrast', 'opticalProfile']) {
    get(id).addEventListener('change', requestFrame);
  }
  get('compareMode').addEventListener('change', () => { get('dividerControl').hidden = get('compareMode').value !== 'wipe'; });
  get('divider').addEventListener('input', () => {
    get('dividerValue').textContent = `${get('divider').value}%`;
    requestFrame();
  });
  get('inputMode').addEventListener('change', () => {
    releasePreview(true);
    syncInputControls();
  });
  for (const id of ['pointerX', 'pointerY']) get(id).addEventListener('input', requestFrame);
  get('playbackSpeed').addEventListener('change', () => state.clock?.setPlaybackSpeed(Number(get('playbackSpeed').value)));
  get('play').addEventListener('click', () => {
    if (!state.clock) return;
    if (state.clock.playing) state.clock.pause();
    else state.clock.resume();
    syncClock();
    requestFrame();
  });
  get('time').addEventListener('input', () => {
    state.clock?.pause();
    state.clock?.seek(Number(get('time').value));
    requestFrame();
  });
  get('step').addEventListener('click', () => { state.clock?.stepFrame(); requestFrame(); });
  get('resetTime').addEventListener('click', () => { state.clock?.resetTime(); requestFrame(); });
  canvas.addEventListener('pointermove', event => {
    if (get('inputMode').value !== 'manual') return;
    if (previewPointerId === null || previewPointerId === event.pointerId) movePreviewPointer(event);
  });
  canvas.addEventListener('pointerdown', event => {
    if (canvas.dataset.manual !== 'true' || event.button !== 0 || previewPointerId !== null) return;
    movePreviewPointer(event);
    previewPointerId = event.pointerId;
    canvas.setPointerCapture(event.pointerId);
    pressPreview();
  });
  for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) canvas.addEventListener(type, event => {
    if (event.pointerId !== previewPointerId) return;
    previewPointerId = null;
    if (type === 'pointerup') movePreviewPointer(event);
    releasePreview(type !== 'pointerup');
  });
  get('previewPress').addEventListener('click', () => {
    if (liveAction?.releasedAt === null) releasePreview();
    else pressPreview();
  });
  get('previewControls').addEventListener('keydown', event => {
    if (event.key !== 'Escape' || !liveAction) return;
    if (previewPointerId !== null && canvas.hasPointerCapture(previewPointerId)) canvas.releasePointerCapture(previewPointerId);
    previewPointerId = null;
    releasePreview(true);
  });
  canvas.addEventListener('webglcontextlost', event => {
    event.preventDefault();
    rendererFailed(new Error('WebGL 2 上下文中断，等待浏览器恢复'));
  });
  canvas.addEventListener('webglcontextrestored', () => {
    if (state.entry?.webRendererId) mountRenderer(state.entry);
  });
  motion.addEventListener('change', event => {
    if (event.matches) state.clock?.pause();
    syncClock();
  });
  document.addEventListener('visibilitychange', () => document.hidden ? stopFrames() : requestFrame());
  const observer = new IntersectionObserver(([entry]) => {
    benchVisible = entry.isIntersecting;
    if (benchVisible) requestFrame();
    else stopFrames();
  });
  observer.observe(get('bench'));
  addEventListener('pagehide', closeRenderer);
  addEventListener('pageshow', event => {
    if (event.persisted && state.entry) mountRenderer(state.entry);
  });
}

function copyDraft(from, to) {
  draft()[to] = structuredClone(draft()[from]);
  showDraft();
}

function updateTheme() {
  const mode = get('theme').value;
  document.documentElement.dataset.theme = mode === 'system' ? (dark.matches ? 'dark' : 'light') : mode;
}

function readShare() {
  if (!location.hash.startsWith('#umivp')) return;
  try {
    applyPreset(decodeShare(location.hash));
  } catch (error) {
    report(`${message(error)}。可使用预设 JSON 导入。`, true);
  }
}

async function start() {
  updateTheme();
  get('theme').addEventListener('change', updateTheme);
  dark.addEventListener('change', updateTheme);
  get('highContrast').checked = matchMedia('(prefers-contrast: more)').matches;
  const response = await fetch(new URL('./catalog.json', import.meta.url));
  if (!response.ok) throw new Error(`目录资源加载失败（HTTP ${response.status}）`);
  state.catalog = await response.json();
  const { materials, catalogVersion } = state.catalog;
  get('catalogVersion').textContent = `CATALOG ${catalogVersion} · ${materials.length}`;
  const addOptions = (id, values, names = {}) => get(id).append(...[...new Set(values)].sort().map(value => option(value, names[value] ?? value)));
  addOptions('filterCategory', materials.map(entry => entry.category), categories);
  addOptions('filterTag', materials.flatMap(entry => entry.tags));
  addOptions('filterCapability', materials.flatMap(entry => entry.requiredCapabilities));
  for (const id of ['filterCategory', 'filterTag', 'filterLoader', 'filterCapability', 'filterDynamic', 'filterWeb']) {
    get(id).addEventListener('change', renderResults);
  }
  get('query').addEventListener('input', renderResults);
  get('draftSide').addEventListener('change', showDraft);
  get('resetDraft').addEventListener('click', () => {
    draft()[side()] = defaults(state.entry);
    showDraft();
    report('已恢复当前草稿的目录默认值。');
  });
  get('copyAToB').addEventListener('click', () => copyDraft('a', 'b'));
  get('copyBToA').addEventListener('click', () => copyDraft('b', 'a'));
  get('swap').addEventListener('click', () => {
    const sides = draft();
    [sides.a, sides.b] = [sides.b, sides.a];
    showDraft();
  });
  setupPreviewEvents();
  setupPresetEvents();
  renderResults();
  showSaved();
  selectMaterial(resolveMaterial(state.catalog, glassId) ?? materials[0]);
  readShare();
  addEventListener('hashchange', readShare);
}

start().catch(error => {
  get('material-title').textContent = '目录加载失败';
  showFallback(`${message(error)}。请通过本地静态服务打开打包目录。`);
});
