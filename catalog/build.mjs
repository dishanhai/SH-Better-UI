// Author: dishanhai
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compareVersions, isParameterKey, isResource, normalize, parseVersion, resolveMaterial, validateParameters } from './contract.mjs';
import { validateSchema } from './schema.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const catalogDir = join(root, 'catalog');
const target = join(catalogDir, 'generated/catalog.json');
const projectAssets = ['src/main/resources/assets', 'neoforge-1.21.1/src/main/resources/assets'];
const javaRoots = ['src/kernel/java', 'src/main/java', 'neoforge-1.21.1/src/main/java', 'src/main/kotlin', 'neoforge-1.21.1/src/main/kotlin'];
const readJson = path => JSON.parse(readFileSync(path, 'utf8'));
const sha256 = value => 'sha256:' + createHash('sha256').update(value).digest('hex');
const lexical = (a, b) => a < b ? -1 : a > b ? 1 : 0;

function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).sort((a, b) => lexical(a.name, b.name)).flatMap(item => {
    const path = join(directory, item.name);
    return item.isDirectory() ? files(path) : item.isFile() && item.name.endsWith('.json') ? [path] : [];
  });
}

export function canonicalJson(value) {
  if (Array.isArray(value)) return '[' + value.map(canonicalJson).join(',') + ']';
  if (value && typeof value === 'object') {
    return '{' + Object.keys(value).sort(lexical).map(key => JSON.stringify(key) + ':' + canonicalJson(value[key])).join(',') + '}';
  }
  return JSON.stringify(value);
}

export function isRelativeFile(path) {
  return typeof path === 'string' && /^[a-zA-Z0-9_./-]+$/.test(path)
    && path.split('/').every(part => part && part !== '.' && part !== '..');
}

function checkSourceSymbol(symbol) {
  const [className, member, extra] = symbol.split('#');
  if (extra !== undefined || !/^[\w.]+$/.test(className) || member !== undefined && !/^\w+$/.test(member)) {
    throw new Error('Use fully qualified Java source symbol syntax: ' + symbol);
  }
  const parts = className.split('.');
  for (let end = parts.length; end > 0; end--) {
    const stem = parts.slice(0, end).join('/');
    for (const base of javaRoots) for (const suffix of ['.java', '.kt']) {
      const path = join(root, base, stem + suffix);
      if (!existsSync(path)) continue;
      const source = readFileSync(path, 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '');
      const name = parts.at(-1);
      if (!new RegExp('\\b(?:class|interface|record|enum)\\s+' + name + '\\b').test(source)) continue;
      if (!member || new RegExp('\\b' + member + '\\b\\s*(?:[=(;,{]|$)', 'm').test(source)) return;
    }
  }
  throw new Error('Source symbol not found: ' + symbol);
}

function validateEntry(entry, label) {
  validateSchema('material.schema.json', entry, label);
  parseVersion(entry.materialRevision);
  if (!entry.publicSymbols.includes(entry.publicEntrypoint)) throw new Error(label + ' entrypoint must be declared in publicSymbols');
  for (const path of entry.sourceFiles) {
    if (!isRelativeFile(path) || !existsSync(join(root, path))) throw new Error(label + ' missing source file: ' + path);
  }
  entry.publicSymbols.forEach(checkSourceSymbol);
  for (const asset of entry.requiredResources) {
    if (!isResource(asset) || !projectAssets.some(base => existsSync(join(root, base, ...asset.split(':'))))) {
      throw new Error(label + ' missing resource: ' + asset);
    }
  }
  const web = ['web-approximation', 'web-verified'].includes(entry.webCompatibility);
  const webFields = [entry.webRendererId, entry.rendererVersion, entry.previewScene];
  if (web ? !webFields.every(field => field?.trim()) : !webFields.every(field => field === null)) {
    throw new Error(label + ' web renderer, version, scene and compatibility must agree');
  }
  if (entry.rendererVersion) parseVersion(entry.rendererVersion);
  if (entry.replacementId && !entry.deprecated) throw new Error(label + ' only retired entries can be redirected');
  const aliases = entry.aliases.map(normalize);
  if (aliases.some(alias => !alias) || new Set(aliases).size !== aliases.length) throw new Error(label + ' has blank or duplicate aliases');
  for (const [name, param] of Object.entries(entry.parameters)) {
    if (!isParameterKey(name)) throw new Error(label + ' invalid parameter name: ' + name);
    if (param.minimum !== null && param.maximum !== null && param.minimum > param.maximum) {
      throw new Error(label + ' invalid range: ' + name);
    }
    if ((param.type === 'enum') !== Boolean(param.choices.length)) throw new Error(label + ' invalid choices: ' + name);
    for (const dependency of [...param.dependsOn, ...param.constraints.map(c => c.parameter).filter(Boolean)]) {
      if (dependency === name || !Object.hasOwn(entry.parameters, dependency)) {
        throw new Error(label + ' invalid parameter dependency: ' + dependency);
      }
    }
  }
  const defaults = validateParameters(entry);
  if (entry.defaultValues && canonicalJson(defaults) !== canonicalJson(entry.defaultValues)) {
    throw new Error(label + ' has divergent parameter defaults');
  }
  return { ...entry, defaultValues: defaults };
}

export function assembleCatalog() {
  const manifest = readJson(join(catalogDir, 'manifest.json'));
  const materials = files(join(catalogDir, 'materials')).map(path => {
    const label = relative(root, path);
    let entry;
    try {
      entry = validateEntry(readJson(path), label);
    } catch (error) {
      throw new Error(label + ': ' + error.message, { cause: error });
    }
    const [namespace, name] = entry.id.split(':');
    if (namespace !== 'sh_better_ui' || relative(join(catalogDir, 'materials', namespace), path).replaceAll('\\', '/') !== name + '.json') {
      throw new Error('Material file must match namespace and ID: ' + entry.id);
    }
    return entry;
  }).sort((a, b) => lexical(a.id, b.id));
  const migrations = files(join(catalogDir, 'migrations')).flatMap(readJson);
  const payload = { ...manifest, materials, migrations };
  const result = { ...payload, contentHash: sha256(canonicalJson(payload)) };
  validateSchema('catalog.schema.json', result);
  parseVersion(result.catalogVersion);
  parseVersion(result.presetSchemaVersion);
  for (const entry of materials) {
    if (entry.replacementId && !resolveMaterial(result, entry.id)) throw new Error('Missing replacement for ' + entry.id);
    resolveMaterial(result, entry.id);
  }
  const migrationsSeen = new Set();
  for (const migration of migrations) {
    const key = [migration.materialId, migration.fromRevision, migration.fromSchemaVersion].join('/');
    const entry = resolveMaterial(result, migration.materialId);
    if (!entry || migrationsSeen.has(key) || compareVersions(migration.toRevision, migration.fromRevision) < 0
        || compareVersions(migration.toSchemaVersion, migration.fromSchemaVersion) < 0
        || migration.toRevision === migration.fromRevision && migration.toSchemaVersion === migration.fromSchemaVersion) {
      throw new Error('Invalid or duplicate migration: ' + key);
    }
    const destinations = Object.values(migration.rename);
    if (new Set(destinations).size !== destinations.length || destinations.some(k => !isParameterKey(k))) {
      throw new Error('Conflicting migration destinations: ' + key);
    }
    migrationsSeen.add(key);
  }
  return result;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.slice(2).some(arg => arg !== '--check')) throw new Error('Usage: node catalog/build.mjs [--check]');
    const snapshot = assembleCatalog();
    const json = JSON.stringify(snapshot, null, 2) + '\n';
    if (process.argv.includes('--check')) {
      if (!existsSync(target) || readFileSync(target, 'utf8') !== json) throw new Error('Generated catalog is stale. Run node catalog/build.mjs');
    } else {
      mkdirSync(dirname(target), { recursive: true });
      writeFileSync(target, json);
    }
    console.log(snapshot.materials.length + ' material(s), ' + snapshot.contentHash);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
