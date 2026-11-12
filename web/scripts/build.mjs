// Author: dishanhai
import { cp, mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { adaptShader } from './shader-adapter.mjs';
import { supportsMaterialEffect } from '../src/renderers/material-effects.mjs';

const root = new URL('../../', import.meta.url);
const source = new URL('../src/', import.meta.url);
const dist = new URL('../dist/', import.meta.url);
const fixtureDirectory = new URL('catalog/fixtures/', root);
const shaders = new URL('src/main/resources/assets/sh_better_ui/shaders/', root);
const opticalNotices = new URL('src/main/resources/META-INF/third-party/liquid-glass-optics/', root);
const files = {
  'catalog.json': new URL('catalog/generated/catalog.json', root),
  'catalog-contract.mjs': new URL('catalog/contract.mjs', root),
  'fixtures/liquid-glass.json': new URL('liquid-glass.json', fixtureDirectory),
  'fixtures/liquid-glass.png': new URL('liquid-glass.png', fixtureDirectory),
  'fixtures/material-shop.png': new URL('src/main/resources/assets/sh_better_ui/textures/gui/display_64/shop.png', root),
  'shaders/managed-glass.vert': new URL('managed_glass.vsh', shaders),
  'shaders/managed-glass.frag': new URL('managed_glass.fsh', shaders),
  'LICENSE.txt': new URL('LICENSE', root),
  'third-party/liquid-glass-optics/NOTICE': new URL('NOTICE', opticalNotices),
  'third-party/liquid-glass-optics/LICENSE-MIT': new URL('LICENSE-MIT', opticalNotices),
};
const assets = await Promise.all(Object.entries(files).map(async ([name, url]) => {
  const data = await readFile(url);
  return [name, name.startsWith('shaders/') ? adaptShader(data.toString('utf8')) : data];
}));
const extensionDirectories = [];
for (let index = 2; index < process.argv.length; index += 2) {
  const directory = process.argv[index + 1];
  if (process.argv[index] !== '--extension' || !directory || directory.startsWith('--')) {
    throw new Error('Usage: npm run build -- [--extension <directory>]…');
  }
  extensionDirectories.push(resolve(directory));
}
let catalog = JSON.parse(assets[0][1]);
if (extensionDirectories.length) {
  const { mergeExtensions } = await import(new URL('catalog/extension.mjs', root));
  catalog = await mergeExtensions(catalog, extensionDirectories);
  assets[0][1] = JSON.stringify(catalog, null, 2) + '\n';
}
if (!Array.isArray(catalog.materials) || !catalog.materials.some(entry => entry.webRendererId === 'managed-liquid-glass')) {
  throw new Error('The generated catalog does not register the managed Liquid Glass adapter');
}
for (const entry of catalog.materials) {
  const managed = entry.webRendererId === 'managed-liquid-glass' && entry.previewScene === 'liquid-glass';
  const effect = entry.webRendererId === 'material-effects' && entry.previewScene === 'material-effects' && supportsMaterialEffect(entry);
  if (entry.webRendererId && !managed && !effect) {
    throw new Error(`Unreviewed renderer or fixture for ${entry.id}`);
  }
}

await Promise.all(['fixtures/', 'shaders/', 'third-party/liquid-glass-optics/'].map(path => mkdir(new URL(path, dist), { recursive: true })));
await cp(source, dist, { recursive: true });
try {
  await cp(new URL('../public/', import.meta.url), dist, { recursive: true });
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
}
await Promise.all(assets.map(([path, data]) => writeFile(new URL(path, dist), data)));
console.log(`Built ${catalog.materials.length} catalog entries and the source-backed WebGL 2 adapter to web/dist.`);
