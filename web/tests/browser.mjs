// Author: dishanhai
import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { chromium, eventually } from './chromium.mjs';
import { serve } from '../scripts/serve.mjs';
import { decodeShare } from '../src/share.mjs';

const { values } = parseArgs({ options: {
  'evidence-subdir': { type: 'string' },
  'native-scene': { type: 'string' },
  'write-reference': { type: 'boolean' },
} });
const evidencePath = values['evidence-subdir'] ?? (process.env.UMIVP_HARDWARE_GL === '1' ? 'web-hardware' : 'web');
if (!/^[a-z0-9][a-z0-9_-]{0,47}$/.test(evidencePath)) throw new Error('Invalid evidence subdirectory');
const nativePreset = values['native-scene'] ? JSON.parse(await readFile(values['native-scene'], 'utf8')).preset : null;
const output = new URL(`../../build/material-catalog/${evidencePath}/`, import.meta.url);
const dist = new URL('../dist/', import.meta.url);
const referenceName = '/references/liquid-glass-web.png';
const assets = new Map([['/__test__/pixel-metrics.mjs', { data: await readFile(new URL('./pixel-metrics.mjs', import.meta.url)) }]]);
const server = await serve({ root: dist, testAssets: assets });
let browser;

async function probeRenderer() {
  const { openManagedGlass } = await import('./renderers/managed-liquid-glass.mjs');
  const { replayFrame } = await import('./preview-clock.mjs');
  const { pixelMetrics } = await import('./__test__/pixel-metrics.mjs');
  const catalog = await (await fetch('./catalog.json')).json();
  const entry = catalog.materials.find(item => item.id === 'sh_better_ui:liquid_glass');
  const base = entry.defaultValues;
  const canvas = document.createElement('canvas');
  const renderer = await openManagedGlass(canvas);
  const gl = canvas.getContext('webgl2');
  const { fixture } = renderer;
  const { width, height } = fixture;
  const read = (options = {}) => {
    renderer.render({ frame: replayFrame(fixture, 800), a: base, ...options });
    const bytes = new Uint8Array(canvas.width * canvas.height * 4);
    gl.readPixels(0, 0, canvas.width, canvas.height, gl.RGBA, gl.UNSIGNED_BYTE, bytes);
    const error = gl.getError();
    if (error !== gl.NO_ERROR) throw new Error('WebGL error 0x' + error.toString(16));
    return bytes;
  };
  const sample = (rgba, x, y) => [...rgba.subarray(((height - y - 1) * width + x) * 4, ((height - y - 1) * width + x + 1) * 4)];
  const compare = (a, b) => pixelMetrics(a, b, width, height);
  const baseline = read({ measureGpu: true });
  const backdrop = new Uint8Array(baseline.length);
  gl.bindFramebuffer(gl.READ_FRAMEBUFFER, renderer.capture.framebuffer);
  gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, backdrop);
  gl.bindFramebuffer(gl.READ_FRAMEBUFFER, null);
  const snell = read({ opticalProfile: 'snell' });
  const clearMaterial = { ...base, transmission: 0.97, refraction: 4.6, lensing: 0.42, scattering: 0.12,
    edgeHighlight: 0.78, thickness: 4, blurRadius: 1.2 };
  const clear = read({ a: clearMaterial, opticalProfile: 'snellClear' });
  const lens = { ...clearMaterial, scattering: 0, edgeHighlight: 0, refraction: 16 };
  const bent = read({ a: { ...lens, quality: 'PERFORMANCE' }, opticalProfile: 'snellClear' });
  const straight = read({ a: { ...lens, quality: 'PERFORMANCE', refraction: 0 }, opticalProfile: 'snellClear' });
  const dispersed = read({ a: lens, opticalProfile: 'snellClear' });
  const bevel = {
    vsLegacy: compare(baseline, snell), refraction: compare(straight, bent), dispersion: compare(bent, dispersed),
    clearCenter: sample(clear, 320, 192), sceneCenter: sample(backdrop, 320, 192),
  };
  const transparent = { ...base, transmission: 1, scattering: 0, refraction: 0, edgeHighlight: 0, blurRadius: 24 };
  const tuned = overrides => read({ a: { ...transparent, ...overrides }, opticalProfile: 'snell' });
  const sharp = tuned({});
  const lightBlur = tuned({ scattering: 0.25 });
  const strongBlur = tuned({ scattering: 1 });
  const smallRadius = tuned({ scattering: 1, blurRadius: 2 });
  const tinted = tuned({ transmission: 0 });
  const colorDistance = (a, b) => a.slice(0, 3).reduce((total, value, channel) => total + Math.abs(value - b[channel]), 0);
  // The sharp left edge of the moving tile, well inside the glass at 800 ms.
  const edge = image => colorDistance(sample(image, 194, 223), sample(image, 195, 223));
  const tuning = {
    transparentCenter: sample(sharp, 320, 192),
    tintDelta: colorDistance(sample(sharp, 320, 192), sample(tinted, 320, 192)),
    sharp: edge(sharp), light: edge(lightBlur), strong: edge(strongBlur),
    radiusDelta: colorDistance(sample(strongBlur, 184, 223), sample(smallRadius, 184, 223)),
    noRadius: compare(sharp, tuned({ scattering: 1, blurRadius: 0 })),
    refractionCenter: sample(tuned({ refraction: 32 }), 320, 192),
    sharpAfterBlur: compare(sharp, tuned({})),
  };
  const repeated = compare(baseline, read());
  const withoutRefraction = read({ a: { ...base, refraction: 0 } });
  const exaggeratedRefraction = read({ a: { ...base, refraction: 16 } });
  const center = [sample(withoutRefraction, 320, 192), sample(exaggeratedRefraction, 320, 192)];
  const parameters = {
    refraction: compare(withoutRefraction, exaggeratedRefraction),
    lensing: compare(read({ a: { ...base, lensing: 0 } }), read({ a: { ...base, lensing: 1 } })),
    scattering: compare(read({ a: { ...base, scattering: 0 } }), read({ a: { ...base, scattering: 1 } })),
    edgeHighlight: compare(read({ a: { ...base, edgeHighlight: 0 } }), read({ a: { ...base, edgeHighlight: 1 } })),
    thickness: compare(read({ a: { ...base, thickness: 0 } }), read({ a: { ...base, thickness: 14 } })),
    blurRadius: compare(read({ a: { ...base, blurRadius: 0 } }), read({ a: { ...base, blurRadius: 14 } })),
    transmission: compare(read({ a: { ...base, transmission: 0 } }), read({ a: { ...base, transmission: 1 } })),
    quality: compare(read({ a: { ...base, quality: 'PERFORMANCE' } }), read({ a: { ...base, quality: 'ULTRA' } })),
    foreground: compare(read({ a: { ...base, foreground: 'LIGHT' } }), read({ a: { ...base, foreground: 'DARK' } })),
  };
  const a = { ...base, edgeHighlight: 0.1 };
  const b = { ...base, refraction: 21, foreground: 'DARK' };
  const singleA = read({ a });
  const singleB = read({ a: b });
  const split = read({ a, b, mode: 'split' });
  const wipe = read({ a, b, mode: 'wipe', divider: 63 });
  const splitA = new Uint8Array(singleA.length), splitB = new Uint8Array(singleB.length);
  const expectedWipe = new Uint8Array(wipe.length);
  const boundary = Math.round(width * 0.63) * 4;
  for (let row = 0; row < height; row++) {
    const start = row * width * 4;
    const end = start + width * 4;
    splitA.set(split.subarray(start * 2, start * 2 + width * 4), start);
    splitB.set(split.subarray(start * 2 + width * 4, end * 2), start);
    expectedWipe.set(singleA.subarray(start, start + boundary), start);
    expectedWipe.set(singleB.subarray(start + boundary, end), start + boundary);
  }
  const compareModes = [compare(singleA, splitA), compare(singleB, splitB), compare(wipe, expectedWipe)];
  const dark = read({ highContrast: true, a: { ...base, foreground: 'DARK' } });
  const light = read({ highContrast: true, a: { ...base, foreground: 'LIGHT' } });
  const linear = value => value / 255 <= 0.04045 ? value / 255 / 12.92 : ((value / 255 + 0.055) / 1.055) ** 2.4;
  const luma = ([r, g, b]) => 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
  const contrast = { lightTextBackground: luma(sample(light, 420, 180)), darkTextBackground: luma(sample(dark, 240, 180)) };
  const fallback = sample(read({ a: { ...base, quality: 'FALLBACK' } }), 320, 192);
  const layer = read({ layerOnly: true });
  const alpha = [sample(layer, 0, 0)[3], sample(layer, 320, 108)[3], sample(layer, 320, 192)[3]];
  const alphaDiff = compare(layer, read({ layerOnly: true, a: { ...base, thickness: 14 } })).alphaMae;
  const keyframes = [];
  for (const timeMs of fixture.timesMs) {
    const data = read({ frame: replayFrame(fixture, timeMs) });
    keyframes.push({ timeMs, vs800: compare(baseline, data), png: canvas.toDataURL('image/png').split(',')[1] });
  }
  const live = read({ frame: replayFrame(fixture, 800, { x: 0.78, y: 0.38 }) });
  const inputResponse = compare(baseline, live);
  const stepped = read({ frame: replayFrame(fixture, 800 + 1000 / fixture.fps) });
  const stepResponse = compare(baseline, stepped);
  const zoomResponse = compare(baseline, read({ zoomed: true }));
  const beforeDpi = renderer.diagnostics();
  read({ dpi: 2 });
  read({ dpi: 2 });
  const afterDpi = renderer.diagnostics();
  const resolution = [canvas.width, canvas.height];
  const samples = [];
  for (let i = 0; i < 18; i++) {
    renderer.render({ frame: replayFrame(fixture, i * 1000 / fixture.fps), a: base, measureGpu: i % 6 === 0 });
    samples.push(renderer.diagnostics().cpuFrameMs);
    await new Promise(resolve => setTimeout(resolve, 16));
  }
  const timer = renderer.diagnostics();
  const debug = gl.getExtension('WEBGL_debug_renderer_info');
  const device = gl.getParameter(debug?.UNMASKED_RENDERER_WEBGL ?? gl.RENDERER);
  const version = gl.getParameter(gl.VERSION);
  const panel = fixture.surface;
  // Encode horizontal source coordinates in red so a reversed sample order proves the lens folds the image.
  const ramp = new Uint8Array(width * height * 4);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const index = (y * width + x) * 4;
      ramp[index] = Math.min(255, Math.max(0, 16 + (x - panel.x) * 14));
      ramp[index + 3] = 255;
    }
  }
  gl.bindTexture(gl.TEXTURE_2D, renderer.sourceTexture);
  gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, ramp);
  const circle = { x: panel.x, y: panel.y, width: 64, height: 64, radius: 32, shape: 'ELLIPSE' };
  const folds = [panel, circle].map(region => {
    fixture.surface = region;
    const y = region.y + region.height / 2;
    const trace = image => [1, 3, 14].map(depth => sample(image, region.x + depth, y)[0]);
    const frame = replayFrame(fixture, 800);
    frame.movingRect = { x: 0, y: 0, width: 0, height: 0 };
    const options = { frame, opticalProfile: 'snell' };
    return {
      shape: region.shape,
      straight: trace(read({ ...options, a: transparent })),
      bent: trace(read({ ...options, a: { ...transparent, refraction: base.refraction } })),
    };
  });
  fixture.surface = panel;
  const midX = panel.x + panel.width / 2;
  const whiteX = midX + 48;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const index = (y * width + x) * 4;
      const value = Math.abs(x - whiteX) < 4 ? 255 : 48 + 7 * ((x + y) % 24);
      ramp.fill(value, index, index + 3);
    }
  }
  gl.bindTexture(gl.TEXTURE_2D, renderer.sourceTexture);
  gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, ramp);
  const frame = replayFrame(fixture, 800);
  frame.movingRect = { x: 0, y: 0, width: 0, height: 0 };
  const renderSnell = a => read({ frame, a, opticalProfile: 'snell' });
  const dispersedRamp = renderSnell({ ...lens, transmission: 1, quality: 'ULTRA' });
  const midY = panel.y + panel.height / 2;
  const chroma = (x, y) => {
    const channels = sample(dispersedRamp, x, y).slice(0, 3);
    return Math.max(...channels) - Math.min(...channels);
  };
  const edgeChroma = Math.max(...[1, 2, 3, 4].map(offset => chroma(panel.x + offset, midY)));
  const cornerChroma = Math.max(...[6, 7, 8, 9].map(offset => chroma(panel.x + offset, panel.y + 12)));
  const noHighlight = renderSnell({ ...transparent, thickness: 6 });
  const withHighlight = renderSnell({ ...transparent, thickness: 6, edgeHighlight: 1 });
  const highlightAt = y => luma(sample(withHighlight, midX, y)) - luma(sample(noHighlight, midX, y));
  const rimLighting = [highlightAt(panel.y), highlightAt(panel.y + panel.height - 1),
    highlightAt(panel.y + panel.height - 6)];
  const whiteRimDelta = colorDistance(sample(noHighlight, whiteX, panel.y), sample(withHighlight, whiteX, panel.y));
  const resources = { capture: renderer.capture.texture, framebuffer: renderer.capture.framebuffer, program: renderer.glassProgram };
  renderer.dispose();
  const released = !gl.isTexture(resources.capture) && !gl.isFramebuffer(resources.framebuffer) && !gl.isProgram(resources.program)
    && renderer.diagnostics().captureBytes === 0;
  return { repeated, parameters, center, bevel, tuning, folds, edgeChroma, cornerChroma, rimLighting, whiteRimDelta,
    compareModes, contrast, fallback, alpha, alphaDiff, keyframes, inputResponse,
    stepResponse, zoomResponse, beforeDpi, afterDpi, resolution, timer, released, device, version,
    medianCpuFrameMs: samples.toSorted((a, b) => a - b)[Math.floor(samples.length / 2)] };
}

async function waitFor(expression, label) {
  return eventually(() => browser.evaluate(expression).catch(() => false), label);
}

async function navigate(hash = '') {
  await browser.command('Page.navigate', { url: server.url + hash });
  await waitFor("document.querySelector('#materialId')?.textContent === 'sh_better_ui:liquid_glass'", 'catalog material selection');
}

async function setValue(id, value, event = 'change') {
  return browser.call((name, next, type) => {
    const field = document.getElementById(name);
    field.value = next;
    field.dispatchEvent(new Event(type, { bubbles: true }));
  }, id, value, event);
}

async function key(key, code, vk) {
  const text = key === 'Enter' ? '\r' : key === ' ' ? ' ' : '';
  await browser.command('Input.dispatchKeyEvent', { type: text ? 'keyDown' : 'rawKeyDown', key, code,
    windowsVirtualKeyCode: vk, ...(text ? { text, unmodifiedText: text } : {}) });
  await browser.command('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode: vk });
}

async function expectFallback(text) {
  await waitFor(`!document.querySelector('#fallback').hidden && document.querySelector('#previewStatus').textContent.includes(${JSON.stringify(text)})`, text);
  assert.ok(await browser.evaluate("document.querySelector('#nativeRoute').textContent.includes('sh_better_ui:liquid_glass')"));
  assert.ok(await browser.evaluate("!document.querySelector('#reference').hidden && document.querySelector('#parameter-refraction') !== null"));
}

try {
  browser = await chromium();
  await browser.command('Page.enable');
  await browser.command('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await browser.command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 960, deviceScaleFactor: 1, mobile: false });
  await navigate();
  await waitFor("document.querySelector('#previewCanvas').dataset.ready === 'true'", 'app WebGL renderer');
  const firstLoadMs = await browser.evaluate('performance.now()');
  assert.equal(await browser.evaluate("document.querySelector('#time').value"), '0');
  assert.equal(await browser.evaluate("document.querySelector('#play').textContent"), '播放');

  const results = await browser.call(probeRenderer);
  for (const { shape, straight, bent } of results.folds) {
    assert.ok(straight[0] < straight[1] && bent[0] > bent[1] + 2,
      `${shape} bevel did not reverse the backdrop sample order at the default refraction: ${straight} -> ${bent}`);
    assert.equal(bent[2], straight[2], `${shape} bevel displaced the flat face`);
  }
  assert.ok(results.bevel.vsLegacy.changedPixels > 50, 'Snell rendering did not change the optical surface');
  assert.ok(results.bevel.refraction.changedPixels > 50, 'The Snell bevel did not displace background pixels');
  assert.ok(results.bevel.dispersion.changedPixels > 50, 'Balanced Snell glass did not separate color channels');
  assert.equal(results.edgeChroma, 0, 'Snell dispersion colored the center of a straight edge');
  assert.ok(results.cornerChroma >= 3, 'Snell dispersion did not separate channels near a corner');
  const [topLight, bottomLight, innerLight] = results.rimLighting;
  assert.ok(topLight > 0.05 && bottomLight > 0.05, `Expected two lit edges, got ${results.rimLighting}`);
  assert.equal(innerLight, 0, 'The Snell highlight changed the inner backdrop');
  assert.equal(results.whiteRimDelta, 0, 'The Snell highlight tinted an already white rim');
  assert.deepEqual(results.bevel.clearCenter, results.bevel.sceneCenter, 'Clear glass altered the flat center');
  const tuning = results.tuning;
  assert.deepEqual(tuning.transparentCenter, results.bevel.sceneCenter);
  assert.ok(tuning.tintDelta > 50, 'Tint had no visible range');
  assert.ok(tuning.sharp > tuning.light && tuning.light > tuning.strong && tuning.strong < tuning.sharp * 0.18,
    `Full frost retained a sharp source edge: ${JSON.stringify(tuning)}`);
  assert.ok(tuning.radiusDelta > 25, 'Blur radius did not broaden the filter');
  assert.equal(tuning.noRadius.changedPixels, 0);
  assert.deepEqual(tuning.refractionCenter, tuning.transparentCenter, 'Refraction warped the flat center');
  assert.equal(tuning.sharpAfterBlur.changedPixels, 0, 'Turning blur off did not restore sharp sampling');
  assert.equal(results.repeated.changedPixels, 0);
  assert.deepEqual(results.center[0], results.center[1]);
  for (const [name, metrics] of Object.entries(results.parameters)) assert.ok(metrics.changedPixels > 50, `${name} did not affect output`);
  for (const [index, metrics] of results.compareModes.entries()) assert.equal(metrics.changedPixels, 0, `A/B mode ${index}`);
  assert.ok(results.contrast.lightTextBackground <= 0.105);
  assert.ok(results.contrast.darkTextBackground >= 0.29);
  assert.deepEqual(results.fallback, [32, 37, 44, 255]);
  assert.equal(results.alpha[0], 0);
  assert.ok(results.alpha[1] > 0 && results.alpha[1] < 255);
  assert.equal(results.alpha[2], 255);
  assert.ok(results.alphaDiff > 0);
  for (const frame of results.keyframes) if (frame.timeMs !== 800) assert.ok(frame.vs800.changedPixels > 100);
  assert.ok(results.inputResponse.changedPixels > 50);
  assert.ok(results.stepResponse.changedPixels > 100);
  assert.ok(results.zoomResponse.changedPixels > 1000);
  assert.equal(results.beforeDpi.framebufferAllocations, 2);
  assert.equal(results.afterDpi.framebufferAllocations, 3);
  assert.deepEqual(results.resolution, [1280, 768]);
  assert.ok(results.released);
  console.log(`WebGL 2 pixels, optics, A/B, density and disposal passed on ${results.device}`);

  await mkdir(output, { recursive: true });
  const reference = Buffer.from(results.keyframes.find(frame => frame.timeMs === 800).png, 'base64');
  for (const frame of results.keyframes) {
    await writeFile(new URL(`liquid-glass-${frame.timeMs}.png`, output), Buffer.from(frame.png, 'base64'));
    delete frame.png;
  }
  if (values['write-reference']) {
    const target = new URL('../public/references/', import.meta.url);
    await mkdir(target, { recursive: true });
    await writeFile(new URL('liquid-glass-web.png', target), reference);
    assets.set(referenceName, { data: reference });
  }

  for (let i = 0; i < 3; i++) await key('Tab', 'Tab', 9);
  assert.equal(await browser.evaluate('document.activeElement.id'), 'query');
  await browser.command('Input.insertText', { text: '我需要一个类似 Apple Liquid Glass 的透明动态背景，具有背景折射、柔和边缘高光和自然动画。' });
  assert.equal(await browser.evaluate("document.querySelector('#results button').dataset.material"), 'sh_better_ui:liquid_glass');
  await key('Tab', 'Tab', 9);
  await key('Tab', 'Tab', 9);
  assert.equal(await browser.evaluate("document.activeElement.dataset.material ?? document.activeElement.outerHTML.slice(0, 200)"), 'sh_better_ui:liquid_glass');
  await key('Enter', 'Enter', 13);
  assert.equal(await browser.evaluate('document.activeElement.id'), 'material-title');
  await browser.evaluate("document.querySelector('#step').focus()");
  await key(' ', 'Space', 32);
  await waitFor("document.querySelector('#time').value === '17'", 'keyboard single-frame step');

  const preset = await browser.evaluate("fetch('./catalog.json').then(r => r.json()).then(c => import('./catalog-contract.mjs').then(m => m.createPreset(c,'sh_better_ui:liquid_glass',{ refraction: 8 })))");
  await setValue('presetText', JSON.stringify(preset));
  await browser.evaluate("document.querySelector('#importText').click()");
  assert.equal(await browser.evaluate("document.querySelector('#parameter-refraction').value"), '8');
  await setValue('presetText', JSON.stringify({ ...preset, parameters: { refraction: 80 } }));
  await browser.evaluate("document.querySelector('#importText').click()");
  assert.equal(await browser.evaluate("document.querySelector('#parameter-refraction').value"), '8');
  assert.equal(await browser.evaluate("document.querySelector('#feedback').dataset.error"), 'true');
  if (nativePreset) {
    assert.equal(nativePreset.materialId, 'sh_better_ui:liquid_glass');
    await setValue('presetText', JSON.stringify(nativePreset));
    await browser.evaluate("document.querySelector('#importText').click()");
    assert.equal(await browser.evaluate("document.querySelector('#parameter-refraction').value"), String(nativePreset.parameters.refraction));
    assert.notEqual(await browser.evaluate("document.querySelector('#feedback').dataset.error"), 'true');
    await setValue('presetText', JSON.stringify(preset));
    await browser.evaluate("document.querySelector('#importText').click()");
    assert.equal(await browser.evaluate("document.querySelector('#parameter-refraction').value"), '8');
  }
  const downloadDir = new URL('downloads/', output);
  await mkdir(downloadDir, { recursive: true });
  const downloaded = new URL('sh_better_ui-liquid_glass-a.json', downloadDir);
  await rm(downloaded, { force: true });
  await browser.command('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: fileURLToPath(downloadDir) });
  await browser.evaluate("document.querySelector('#exportPreset').click()");
  const exported = await eventually(() => readFile(downloaded, 'utf8').catch(() => false), 'JSON download');
  assert.deepEqual(JSON.parse(exported), preset);
  await browser.evaluate("document.querySelector('#resetDraft').click()");
  const root = await browser.command('DOM.getDocument');
  const fileInput = await browser.command('DOM.querySelector', { nodeId: root.root.nodeId, selector: '#presetFile' });
  await browser.command('DOM.setFileInputFiles', { files: [fileURLToPath(downloaded)], nodeId: fileInput.nodeId });
  await waitFor("document.querySelector('#parameter-refraction').value === '8'", 'JSON file reimport');
  await browser.evaluate("document.querySelector('#sharePreset').click()");
  const share = await waitFor("document.querySelector('#shareUrl').value", 'versioned share link');
  const hash = new URL(share).hash;
  assert.deepEqual(JSON.parse(decodeShare(hash)), preset);
  await browser.evaluate("document.querySelector('#resetDraft').click()");
  await navigate(hash);
  await waitFor("document.querySelector('#parameter-refraction').value === '8'", 'share link reimport');
  await setValue('draftSide', 'b');
  assert.equal(await browser.evaluate("document.querySelector('#parameter-refraction').value"), '3.3');
  await browser.evaluate("document.querySelector('#copyAToB').click()");
  assert.equal(await browser.evaluate("document.querySelector('#parameter-refraction').value"), '8');
  await setValue('compareMode', 'split');
  await waitFor("document.querySelector('#previewCanvas').width === 1280", 'split control');
  await setValue('compareMode', 'wipe');
  await waitFor("!document.querySelector('#dividerControl').hidden && document.querySelector('#stage').dataset.mode === 'wipe'", 'wipe control');
  await setValue('query', '不存在的材质意图xyz', 'input');
  assert.ok(await browser.evaluate("document.querySelector('#resultCount').textContent.includes('候选')"));
  await setValue('query', '', 'input');
  await setValue('theme', 'dark');
  assert.equal(await browser.evaluate('document.documentElement.dataset.theme'), 'dark');
  const screenshot = await browser.command('Page.captureScreenshot', { format: 'png' });
  await writeFile(new URL('workbench-desktop.png', output), Buffer.from(screenshot.data, 'base64'));
  await browser.command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  assert.ok(await browser.evaluate('document.documentElement.scrollWidth <= innerWidth'));
  await browser.evaluate("document.querySelector('#material-title').scrollIntoView()");
  const mobile = await browser.command('Page.captureScreenshot', { format: 'png' });
  await writeFile(new URL('workbench-mobile.png', output), Buffer.from(mobile.data, 'base64'));
  console.log('Browser keyboard navigation, reduced motion, search, preset isolation, A/B UI and responsive layout passed');

  await browser.evaluate("window.loss = document.querySelector('#previewCanvas').getContext('webgl2').getExtension('WEBGL_lose_context'); window.loss.loseContext()");
  await expectFallback('上下文中断');
  await browser.evaluate('window.loss.restoreContext()');
  await waitFor("document.querySelector('#previewCanvas').dataset.ready === 'true'", 'WebGL context restoration');

  const noContext = await browser.command('Page.addScriptToEvaluateOnNewDocument', {
    source: `const originalGetContext = HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext = function (type, ...options) {
        return type === 'webgl2' ? null : originalGetContext.call(this, type, ...options);
      };`,
  });
  await navigate('?no-webgl');
  await expectFallback('无法创建 WebGL 2 上下文');
  await waitFor("document.querySelector('#reference img').naturalWidth === 640", 'static fallback reference');
  await browser.command('Page.removeScriptToEvaluateOnNewDocument', { identifier: noContext.identifier });

  assets.set('/shaders/managed-glass.frag', { data: '#version 300 es\ninvalid shader' });
  await navigate('?bad-shader');
  await expectFallback('着色器编译失败');
  assets.delete('/shaders/managed-glass.frag');
  assets.set('/fixtures/liquid-glass.png', { status: 404, data: 'fixture missing' });
  await navigate('?missing-texture');
  await expectFallback('资源加载失败');
  console.log('WebGL context recovery, missing WebGL, shader error, missing texture and static fallback passed');

  const report = { firstLoadMs, ...results, ui: { keyboard: true, reducedMotion: true, responsive390px: true, presetTransaction: true,
    nativePresetImport: nativePreset ? true : null,
    presetDownloadAndReimport: true, shareRoundTrip: true,
    webglContextRecovery: true, webglDisabled: true, shaderFailure: true, missingTexture: true } };
  await writeFile(new URL('evidence.json', output), JSON.stringify(report, null, 2) + '\n');
  console.log(`Evidence: ${output.pathname}evidence.json`);
} catch (error) {
  if (browser) {
    const state = await browser.evaluate("({status: document.querySelector('#previewStatus')?.textContent, material: document.querySelector('#materialId')?.textContent, focus: document.activeElement?.id})").catch(() => null);
    console.error('Browser state:', state);
  }
  throw error;
} finally {
  await browser?.close();
  await server.close();
}
