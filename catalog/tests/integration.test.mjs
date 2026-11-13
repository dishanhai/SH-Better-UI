// Author: dishanhai
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test, { after } from 'node:test';
import { fileURLToPath } from 'node:url';
import { assembleCatalog } from '../build.mjs';

const catalog = assembleCatalog();
const versionMatch = readFileSync(new URL('../../gradle.properties', import.meta.url), 'utf8').match(/^mod_version=(.+)$/m);
assert.ok(versionMatch, 'mod_version must be declared');
const version = versionMatch[1].trim();
const forgeJar = fileURLToPath(new URL(`../../build/libs/sh_better_ui-${version}-dev.jar`, import.meta.url));
const neoJar = fileURLToPath(new URL(`../../neoforge-1.21.1/build/libs/sh_better_ui-neoforge-1.21.1-${version}.jar`, import.meta.url));
const source = 'src/main/java/example/Consumer.java';
const fixtures = [];

after(() => {
  const temporary = realpathSync(tmpdir());
  for (const fixtureRoot of fixtures) {
    const target = realpathSync(fixtureRoot);
    assert.equal(dirname(target), temporary);
    rmSync(target, { recursive: true });
  }
});

const java = `package example;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;

final class Consumer implements UIModule {
    void setup() {
        descriptor.requestPermission(UIPermission.REGISTER_RENDER_PASS);
        scope = screens.mount(module, active, id);
    }

    void paint() {
        var region = GlassRegion.capsule(8, 12, 80, 22);
        module.engine().require(UIApis.RENDER).submit(module, scope, RenderService.Surface.SCREEN,
                RenderService.Layer.OVERLAY, 0, pass -> {
                    // shbui-material-anchor: liquid_glass region=region foreground=LIGHT
                    pass.text(title, 12, 18, 0xffffffff);
                });
    }

    void close() { scope.close(); }
}
`;

function fixture(code = java, loader = 'forge', jar = forgeJar) {
  const root = mkdtempSync(join(tmpdir(), 'shbui-integration-test-'));
  fixtures.push(root);
  function write(path, content) {
    const file = join(root, path);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, content);
  }
  const forge = loader === 'forge';
  const mc = forge ? '1.20.1' : '1.21.1';
  const modDependency = forge ? 'mandatory=true' : 'type="required"';
  const metadata = forge ? 'mods.toml' : 'neoforge.mods.toml';
  write('settings.gradle', "rootProject.name = 'example'\n");
  write('gradle.properties', `shBetterUiJar=${jar.replaceAll('\\', '/')}\n`);
  write('build.gradle', `plugins { id '${forge ? 'net.minecraftforge.gradle' : 'net.neoforged.moddev'}' version '1.0' }
java { toolchain.languageVersion = JavaLanguageVersion.of(${forge ? 17 : 21}) }
def shBetterUiJar = file(providers.gradleProperty('shBetterUiJar').get())
dependencies { compileOnly files(shBetterUiJar) }
${forge ? "dependencies { minecraft 'net.minecraftforge:forge:1.20.1-47.4.16' }" : "neoForge { version = '21.1.251' }"}
`);
  write('src/main/resources/META-INF/' + metadata, `modLoader="javafml"
[[mods]]
modId="example"
version="1.0.0"
[[dependencies.example]]
modId="minecraft"
${modDependency}
versionRange="[${mc}]"
[[dependencies.example]]
modId="sh_better_ui"
${modDependency}
versionRange="[${version},)"
side="CLIENT"
`);
  write('src/main/resources/META-INF/sh_better_ui.modules', 'example example.Consumer\n');
  write(source, code);
  return { root, write, options: { catalog, target: root, source, materialId: 'liquid_glass' } };
}

const proposal = async (options) => (await import('../integration.mjs')).proposeIntegration(options);

test('produces an applicable read-only patch for each matching external library',
  { skip: !(existsSync(forgeJar) && existsSync(neoJar)) }, async () => {
    for (const [loader, jar, javaVersion] of [['forge', forgeJar, 17], ['neoforge', neoJar, 21]]) {
      const f = fixture(loader === 'forge' ? java : java.replaceAll('\n', '\r\n'), loader, jar);
      const result = await proposal({ ...f.options, values: { refraction: 4.75 } });
      assert.equal(result.audit.loader, loader);
      assert.equal(result.audit.javaVersion, javaVersion);
      assert.equal(result.audit.dependencyVersion, version);
      assert.match(result.diff, /pass\.glass\(java\.util\.List\.of\(/);
      assert.match(result.diff, /4\.75f/);
      assert.doesNotMatch(result.diff, /kernel\.internal|org\.lwjgl/);
      const before = readFileSync(resolve(f.root, source), 'utf8');
      execFileSync('git', ['apply', '--check', '-'], { cwd: f.root, input: result.diff });
      assert.equal(readFileSync(resolve(f.root, source), 'utf8'), before);
      execFileSync('git', ['apply', '-'], { cwd: f.root, input: result.diff });
      await assert.rejects(() => proposal(f.options), /already integrated/);
    }
  });

test('refuses absent or ambiguous anchors and foreground mismatch', async () => {
  const marker = 'shbui-material-anchor: liquid_glass region=region foreground=LIGHT';
  for (const code of [java.replace(marker, 'host marker'), java.replace(marker, `${marker}\n// ${marker}`)]) {
    await assert.rejects(() => proposal(fixture(code).options), /exactly one anchor/);
  }
  await assert.rejects(() => proposal({ ...fixture().options, values: { foreground: 'DARK' } }), /foreground/);
});

test('refuses an anchor outside the supported scoped rendering callback', async () => {
  const marker = '                    // shbui-material-anchor: liquid_glass region=region foreground=LIGHT\n';
  const detached = java.replace(marker, '').replace('        var region', marker + '        var region');
  await assert.rejects(() => proposal(fixture(detached).options), /managed render callback/);
  await assert.rejects(() => proposal(fixture(java.replace('scope.close()', 'other.close()')).options), /scope cleanup/);
});

test('refuses unsupported material, invalid parameter, and wrong Java toolchain', async () => {
  const f = fixture();
  await assert.rejects(() => proposal({ ...f.options, materialId: 'dot_matrix' }), /reviewed integration/);
  await assert.rejects(() => proposal({ ...f.options, values: { refraction: 33 } }), /refraction/);
  f.write('build.gradle', readFileSync(join(f.root, 'build.gradle'), 'utf8').replace('of(17)', 'of(21)'));
  await assert.rejects(() => proposal(f.options), /Java 17/);
});

test('refuses a different Loader JAR even when the filename is supplied as an override',
  { skip: !existsSync(neoJar) }, async () => {
    await assert.rejects(() => proposal({ ...fixture().options, library: neoJar }), /Loader metadata/);
  });

test('requires active scope cleanup, not a code fragment in a comment',
  { skip: !existsSync(forgeJar) }, async () => {
    const f = fixture(java.replace('scope.close();', '/* scope.close(); */'));
    await assert.rejects(() => proposal(f.options), /scope cleanup/);
  });
