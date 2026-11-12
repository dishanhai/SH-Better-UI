// Author: dishanhai
const adapters = new Map([
  ['managed-liquid-glass', () => import('./managed-liquid-glass.mjs').then(module => module.openManagedGlass)],
  ['material-effects', () => import('./material-effects.mjs').then(module => module.openMaterialEffect)],
]);

export async function openRenderer(id, canvas, options) {
  const load = adapters.get(id);
  if (!load) throw new Error('当前构建没有此材质的 WebGL 2 适配器');
  const open = await load();
  return open(canvas, options);
}
