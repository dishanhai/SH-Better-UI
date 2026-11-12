// Author: dishanhai
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { assembleCatalog, canonicalJson, isRelativeFile } from '../build.mjs';
import { createPreset, importPreset, resolveMaterial, searchMaterials, validateParameters } from '../contract.mjs';
import { validateSchema } from '../schema.mjs';

const catalog = assembleCatalog();
const liquid = resolveMaterial(catalog, 'sh_better_ui:liquid_glass');
const legacy = readFileSync(new URL('../fixtures/presets/liquid-glass-draft.json', import.meta.url), 'utf8');

test('the generated release is deterministic and points to source-backed entries', () => {
  const { contentHash, ...payload } = catalog;
  assert.equal(contentHash, 'sha256:' + createHash('sha256').update(canonicalJson(payload)).digest('hex'));
  assert.deepEqual(catalog, assembleCatalog());
  assert.deepEqual(JSON.parse(readFileSync(new URL('../generated/catalog.json', import.meta.url))), catalog);
  assert.ok(catalog.materials.length >= 10);
  for (const entry of catalog.materials) {
    assert.deepEqual(validateParameters(entry), entry.defaultValues);
    assert.ok(entry.publicSymbols.includes(entry.publicEntrypoint));
  }
  assert.equal(isRelativeFile('../outside'), false);
  assert.equal(isRelativeFile('C:/outside'), false);
});

test('stable IDs, old short IDs and alias collisions are deterministic', () => {
  assert.equal(resolveMaterial(catalog, 'Ｌｉｑｕｉｄ＿Ｇｌａｓｓ'), liquid);
  assert.equal(resolveMaterial(catalog, ' liquidGlass '), liquid);
  assert.equal(resolveMaterial(catalog, 'clear_glass').id, 'sh_better_ui:clear_glass');
  assert.equal(resolveMaterial(catalog, 'toast').id, 'sh_better_ui:toast_motion');
  assert.equal(resolveMaterial(catalog, 'swipe_toast').nativePreviewId, 'sh_better_ui:toast');
  assert.equal(resolveMaterial(catalog, 'elastic_collision').nativePreviewId, 'sh_better_ui:elastic_collision');
  assert.equal(resolveMaterial(catalog, 'pull_refresh').nativePreviewId, 'sh_better_ui:pull_refresh');
  assert.equal(resolveMaterial(catalog, '活动岛').nativePreviewId, 'sh_better_ui:live_activity');
  assert.equal(resolveMaterial(catalog, 'arbitrary path shadow').id, 'sh_better_ui:spatial_shadow');
  assert.equal(resolveMaterial(catalog, 'imaginary_material'), undefined);
  assert.throws(() => resolveMaterial({ materials: [liquid, { ...liquid, id: 'other:glass' }] }, 'liquid_glass'), /Conflicting alias/);
});

test('offline search explains natural-language and API matches', () => {
  const results = searchMaterials(catalog, '我需要类似 Apple Liquid Glass 的透明动态背景，具有背景折射、柔和边缘高光和自然动画');
  assert.equal(results[0].entry, liquid);
  assert.ok(results[0].reasons.some(reason => reason.kind === 'name'));
  assert.equal(searchMaterials(catalog, 'GlassOptics')[0].entry, liquid);
  assert.deepEqual(searchMaterials(catalog, '', { webOnly: true }).map(({ entry }) => entry.id).sort(),
    catalog.materials.map(entry => entry.id).sort());
  assert.equal(searchMaterials(catalog, 'quasar999999')[0].reasons[0].kind, 'fallback');
});

test('subeffects resolve through their parent material and older presets fill new defaults', () => {
  for (const [query, parent, field] of [
    ['click burst', 'code_rain', 'supportedEffects'],
    ['ascii cursor scramble', 'ascii_field', 'supportedEffects'],
    ['laser texture reveal', 'laser_flow', 'pointerReveal'],
    ['lyric wave lift', 'floating_lyrics', 'supportedUnitEffects'],
  ]) {
    const entry = resolveMaterial(catalog, query);
    assert.equal(entry.id, 'sh_better_ui:' + parent);
    const preset = createPreset(catalog, entry.id);
    delete preset.parameters[field];
    assert.deepEqual(importPreset(catalog, JSON.stringify(preset)).parameters[field], entry.defaultValues[field]);
  }
});

test('presets round trip and migrate legacy keys before replacing drafts', () => {
  const created = createPreset(catalog, liquid.id, { transmission: 0.42, quality: 'ULTRA' });
  validateSchema('preset.schema.json', created);
  assert.deepEqual(importPreset(catalog, JSON.stringify(created)), created);
  const migrated = importPreset(catalog, legacy);
  assert.equal(migrated.parameters.blurRadius, 7.25);
  assert.equal(migrated.parameters.foreground, 'DARK');
  assert.equal(migrated.parameters.lensing, liquid.defaultValues.lensing);
  assert.equal(migrated.materialId, liquid.id);
  assert.equal(migrated.schemaVersion, '1.0.0');
});

test('presets reject malformed documents, out-of-range values and incompatible releases', () => {
  const valid = createPreset(catalog, liquid.id);
  for (const overrides of [
    { parameters: { blurRadius: -1 } },
    { parameters: { foreground: 'BLUE' } },
    { parameters: { foreground: null } },
    { parameters: { blurRadius: '6' } },
    { parameters: { unknown: true } },
    { parameters: JSON.parse('{"__proto__":{"polluted":true}}') },
    { catalogVersion: '2.0.0' },
    { materialRevision: '1.0.2' },
    { schemaVersion: '2.0.0' },
    { unexpected: true },
  ]) assert.throws(() => importPreset(catalog, JSON.stringify({ ...valid, ...overrides })));
  assert.throws(() => importPreset(catalog, 'x'.repeat(65537)), /64 KiB/);
  assert.throws(() => importPreset(catalog, '{'));
  assert.throws(() => importPreset(catalog, legacy.replace('"opticalBlur": 7.25', '"opticalBlur": 7.25, "blurRadius": 2')), /Conflicting/);
  assert.equal(valid.parameters.blurRadius, 3.6);
});

test('parameter constraints and resource allowlists are validated transactionally', () => {
  const constrained = structuredClone(liquid);
  constrained.parameters.refraction.constraints = [{ op: 'gte', parameter: 'thickness' }];
  assert.throws(() => createPreset({ ...catalog, materials: [constrained] }, constrained.id, { refraction: 1 }), /Constraint failed/);
  constrained.parameters.image = {
    ...constrained.parameters.quality, type: 'texture', defaultValue: constrained.requiredResources[0], choices: [],
  };
  assert.throws(() => validateParameters(constrained, { image: 'other:outside.png' }), /image/);
  assert.throws(() => validateParameters(constrained, { image: 'sh_better_ui:../outside.png' }), /image/);
});
