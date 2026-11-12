// Author: dishanhai
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, readFile, realpath, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { basename, dirname, join } from 'node:path';
import { chromium, eventually } from './chromium.mjs';
import { serve } from '../scripts/serve.mjs';
import { importDocument } from '../src/rich-text-document.mjs';

const output = new URL('../../build/rich-text/web/', import.meta.url);
await mkdir(output, { recursive: true });
const scratch = await mkdtemp(join(fileURLToPath(output), 'browser-'));
const server = await serve();
let browser;
try {
  browser = await chromium();
  const { command, call } = browser;
  const press = async (key, code, windowsVirtualKeyCode, modifiers = 0) => {
    const details = { key, code, windowsVirtualKeyCode, modifiers };
    await command('Input.dispatchKeyEvent', { type: 'keyDown', ...details });
    await command('Input.dispatchKeyEvent', { type: 'keyUp', ...details });
  };
  const click = selector => call(selector => document.querySelector(selector).click(), selector);
  const focus = selector => call(selector => document.querySelector(selector).focus(), selector);
  const read = selector => call(selector => document.querySelector(selector)?.textContent, selector);
  const property = (selector, name) => call((selector, name) => document.querySelector(selector)?.[name], selector, name);
  const set = (selector, text) => call((selector, text) => {
    const input = document.querySelector(selector);
    input.value = text;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, selector, text);
  const screenshot = async name => {
    const { data } = await command('Page.captureScreenshot', { format: 'png' });
    await writeFile(new URL(name, output), Buffer.from(data, 'base64'));
  };
  const desktop = () => command('Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 960, deviceScaleFactor: 1, mobile: false,
  });
  const download = async name => {
    const path = join(scratch, name);
    await mkdir(path);
    await command('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: path });
    await click('#rich-download');
    return eventually(() => readFile(join(path, 'rich-document.json'), 'utf8').catch(() => ''), 'JSON download');
  };
  await command('Page.enable');
  await desktop();
  await command('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'dark' }] });
  await command('Page.navigate', { url: server.url });
  await eventually(() => call(() => Boolean(document.querySelector('a[href="./rich-text.html"]'))), 'material workbench link');
  await click('a[href="./rich-text.html"]');
  await eventually(async () => (await read('#rich-content'))?.includes('地质考察手记'), 'rich text workbench');

  const text = '#rich-properties [name="text"]';
  assert.match(await read('#rich-compatibility'), /原生 RichDocument v1 Codec 读取/);
  await set(text, '地质考察手记 · 水晶样本');
  await click('#rich-apply');
  assert.equal(await read('#rich-content [data-index="0"]'), '地质考察手记 · 水晶样本');
  const starter = await download('starter-download');
  const starterEntries = importDocument(starter);
  assert.deepEqual(starterEntries.map(entry => entry.value.type), ['text', 'paragraph', 'text', 'localized', 'image']);
  assert.ok(starterEntries.every(entry => !entry.requirement));
  assert.equal(starterEntries[0].value.text, '地质考察手记 · 水晶样本');
  await writeFile(new URL('workbench-native-v1.json', output), starter);
  await screenshot('workbench-desktop.png');

  await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
  await eventually(() => call(() => innerWidth === 390), 'mobile viewport');
  assert.deepEqual(await call(() => [...document.querySelectorAll('body *')].filter(node =>
    node.getBoundingClientRect().right > innerWidth).map(node => [node.tagName, node.id, node.getBoundingClientRect().right])), []);
  assert.equal(await call(() => document.documentElement.scrollWidth === document.documentElement.clientWidth), true);
  await command('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true,
    clip: { x: 0, y: 0, width: 390, height: await call(() => document.documentElement.scrollHeight), scale: 1 },
  }).then(({ data }) => writeFile(new URL('workbench-narrow.png', output), Buffer.from(data, 'base64')));
  await desktop();

  const unsafeText = '<img src="https://example.invalid/a.png" onerror="window.payloadRan=true"> 中文 👩‍🔬 العربية';
  const entries = [
    { id: 'demo:run', type: 'text', text: unsafeText, style: { color: 13369344 } },
    { id: 'demo:break', type: 'paragraph', spacing: 12 },
    { id: 'demo:translation', type: 'localized', key: 'demo.key', fallback: '本地回退', arguments: ['42'] },
    { id: 'demo:image', type: 'image', texture: 'demo:textures/item/icon.gif', width: 16, height: 12, alt: '本地图片替代' },
    { id: 'demo:extension', type: 'extension', extension: 'demo:status', version: 1, width: 20, height: 8, logicalHeight: 8,
      alt: '能量条占位', payload: 'https://example.invalid/api' },
  ];
  const opaque = '{ "id":"demo:future", "type":"future", "alt":"未知节点占位", "counter":18446744073709551617 }';
  const source = `{"version":1,"nodes":[${entries.map(node => JSON.stringify(node)).join(',')},${opaque}]}`;
  const file = join(scratch, 'fixture.json');
  await writeFile(file, source, 'utf8');

  await click('.rich-exchange summary');
  const { root } = await command('DOM.getDocument');
  const { nodeId } = await command('DOM.querySelector', { nodeId: root.nodeId, selector: '#rich-file' });
  await command('DOM.setFileInputFiles', { nodeId, files: [file] });
  await eventually(async () => (await read('#rich-import-status'))?.includes('6'), 'local file import');
  assert.equal(await read('#rich-content [data-index="0"]'), unsafeText);
  assert.equal(await call(() => document.querySelectorAll('#rich-content img, #rich-content a, #rich-content script').length), 0);
  assert.equal(await call(() => getComputedStyle(document.querySelector('#rich-content [data-index="0"]')).color), 'rgb(204, 0, 0)');
  await click('#rich-contrast');
  assert.equal(await call(() => {
    const preview = document.querySelector('#rich-content');
    return getComputedStyle(preview.firstElementChild).color === getComputedStyle(preview).color;
  }), true);
  await click('#rich-contrast');
  assert.match(await read('#rich-content'), /本地回退.*本地图片替代.*能量条占位.*未知节点占位/u);
  assert.match(await read('#rich-compatibility'), /2 个受保护节点/);
  assert.equal(await call(() => window.payloadRan === undefined
    && performance.getEntriesByType('resource').every(entry => new URL(entry.name).origin === location.origin)), true);

  await set('#rich-json', source.replace('"version":1', '"version":2'));
  await click('#rich-import');
  assert.equal(await call(() => document.querySelector('#rich-import-status').dataset.error), 'true');
  assert.equal(await read('#rich-node-count'), '6');
  await set('#rich-json', source);
  await click('#rich-import');
  assert.equal(await read('#rich-import-status'), '已导入 6 个节点。');
  await click('.rich-exchange summary');

  await set(text, '地质报告 · 矿物样本 👩‍🔬');
  await focus(text);
  await press('Escape', 'Escape', 27);
  assert.equal(await property(text, 'value'), unsafeText);
  await set(text, '地质报告 · 矿物样本 👩‍🔬');
  await set('#rich-properties [name="color"]', '#89ced0');
  await click('#rich-properties [name="bold"]');
  await focus(text);
  await press('Enter', 'Enter', 13, 2);
  assert.equal(await read('#rich-content [data-index="0"]'), '地质报告 · 矿物样本 👩‍🔬');
  assert.equal(await call(() => getComputedStyle(document.querySelector('#rich-content [data-index="0"]')).fontWeight), '700');
  await focus('#rich-nodes [data-index="0"]');
  await press('z', 'KeyZ', 90, 2);
  assert.equal(await read('#rich-content [data-index="0"]'), unsafeText);
  await press('Z', 'KeyZ', 90, 10);
  assert.equal(await read('#rich-content [data-index="0"]'), '地质报告 · 矿物样本 👩‍🔬');
  await press('ArrowDown', 'ArrowDown', 40);
  assert.equal(await property('#rich-properties [name="spacing"]', 'value'), '12');

  await click('#rich-nodes [data-index="5"]');
  assert.equal(await property('#rich-delete', 'disabled'), true);
  assert.equal(await property('#rich-properties textarea', 'value'), opaque);
  await click('#rich-up');
  assert.match(await read('#rich-nodes [data-index="4"]'), /demo:future/);
  await click('#rich-undo');
  assert.match(await read('#rich-nodes [data-index="5"]'), /demo:future/);
  await click('#rich-add');
  assert.equal(await read('#rich-node-count'), '7');
  await click('#rich-delete');
  assert.equal(await read('#rich-node-count'), '6');
  await click('#rich-nodes [data-index="0"]');
  await click('.rich-stage details summary');

  const exported = await download('protected-download');
  assert.ok(exported.endsWith(`${opaque}]}`));
  assert.deepEqual(JSON.parse(exported).nodes.slice(1, 5), entries.slice(1));
  assert.deepEqual(JSON.parse(exported).nodes[0].style, { color: 0x89ced0, bold: true });
  await screenshot('workbench-protected.png');
  console.log('Rich text Chromium checks passed. Evidence in build/rich-text/web/.');
} finally {
  try {
    await browser?.close();
  } finally {
    await server.close();
    const target = await realpath(scratch);
    if (dirname(target) === await realpath(output) && basename(target).startsWith('browser-')) {
      await rm(target, { recursive: true, force: true });
    }
  }
}
