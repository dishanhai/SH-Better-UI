// Author: dishanhai
import assert from 'node:assert/strict';
import { chromium, eventually } from './chromium.mjs';
import { serve } from '../scripts/serve.mjs';

const staticEffects = new Set(['classic_glass_themes','network_surface','slate_surface','radial_gradient',
  'glass_panel_shadow','glass_text_glow','spatial_shadow','spatial_surface']);
const changes = {
  antigravity: { particleShape: 'BOX', count: 480 },
  ascii_field: { cellSize: 19, glyphs: { characters: 'ABCD' } },
  attention_indicator: { contentOffset: 13 },
  backdrop_blur: { enabled: false },
  breathing_glow: { strength: .2 },
  capsule_reveal: { speed: .5 },
  classic_glass_themes: { themeId: 'aurora' },
  click_ripple: { enabled: false },
  clear_glass: { strength: .1 },
  code_rain: { color: '#FFFDA930' },
  dot_matrix: { density: .8 },
  electric_logo: { coreArgb: '#FFFFD384' },
  floating_lyrics: { sungColor: '#FFFFD084' },
  flow_border: { speed: .32 },
  formatted_text_effects: { timing: { stepMillis: 105, reverse: true } },
  frosted_glass: { radiusVirtual: 3 },
  glass_panel_shadow: { offsetY: 9 },
  icon_motion: { periodsMs: { SPIN: 2500, FLOAT: 600, BOUNCE: 850 } },
  icon_shader: { periodsMs: { GRADIENT_SWEEP: 710, COLOR_CYCLE: 780, ENERGY_WAVE: 620, PULSE: 970 } },
  laser_flow: { beamPosition: .36 },
  light_rays: { raysOrigin: 'BOTTOM_RIGHT' },
  live_activity: { presentation: 'EXPANDED' },
  liquid_glass_surface: { opacity: .25 },
  loading_progress: { topHeight: 12 },
  loading_skeleton: { defaultBlockCornerRadius: 10 },
  loading_spinner: { spinPeriodMs: 2000 },
  network_surface: { colorOverrides: { fill: '#FF763546' } },
  page_slide: { enabled: false },
  pattern_waves: { pattern: 'PLUS' },
  radial_gradient: { originU: .15 },
  recipe_transition: { strength: .4 },
  screen_crossfade: { enabled: false },
  shape_waves: { textStyle: { text: 'SH', fontWeight: 750 } },
  slate_surface: { opacity: .2 },
  spatial_lighting: { light: { lights: [{ x: 45, y: 25, intensity: .9, radius: 245 }] } },
  spatial_shadow: { elevation: 'FLOATING' },
  spatial_surface: { preset: 'METALLIC', highlight: .6 },
  swipe_toast: { fuse: 'NONE' },
  toast_motion: { effect: 'POP' },
  visual_reveal: { direction: 'TOP_TO_BOTTOM' },
};

async function renderMaterial(id, change) {
  const catalog = window.testCatalog;
  const entry = catalog.materials.find(entry => entry.id === 'sh_better_ui:'+id);
  const { openRenderer } = await import('./renderers/registry.mjs');
  const { replayFrame } = await import('./preview-clock.mjs');
  const canvas = window.testCanvas ??= document.createElement('canvas');
  const renderer = await openRenderer(entry.webRendererId,canvas,{ entry });
  const gl = canvas.getContext('webgl2');
  const frameAt = (ms, pointer, action) => replayFrame(renderer.fixture,ms,pointer,action);
  const start = { screen_crossfade: 200, click_ripple: 90, page_slide: 125, toast_motion: 260 }[id] ?? 530;
  const dpi=.3;
  const base = entry.defaultValues;
  const read = (options = {}) => {
    renderer.render({ frame: frameAt(start), a: base, dpi, ...options });
    const bytes = new Uint8Array(canvas.width*canvas.height*4);
    gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,bytes);
    const err = gl.getError();
    if (err !== gl.NO_ERROR) throw new Error('WebGL error 0x'+err.toString(16));
    return bytes;
  };
  const difference = (a,b) => a.reduce((sum,v,i) => sum+(v!==b[i]),0);
  try {
    const a = read(), repeat = read(), later = read({ frame: frameAt(id==='toast_motion' ? 1950 : 1470) });
    const width = canvas.width;
    const colors = new Set();
    let checksum = 2166136261;
    for(let i=0;i<a.length;i+=4) {
      const rgb=a[i]*65536+a[i+1]*256+a[i+2];
      colors.add(rgb);
      checksum=Math.imul(checksum ^ rgb,16777619);
    }
    let parameter, compare;
    if(change) {
      const b = { ...base,...change };
      const changed = read({ a:b });
      parameter = difference(a,changed);
      const wipe=read({ a:base,b,mode:'wipe',divider:50 });
      const split=read({ a:base,b,mode:'split' });
      let mismatch=0;
      for(let row=0;row<canvas.height;row++) {
        for(let x=0;x<width*4;x++) {
          const idx=row*width*4+x, splitIndex=row*width*8+x;
          mismatch += +(split[splitIndex] !== a[idx]) + +(split[splitIndex+width*4] !== changed[idx]);
          mismatch += +(wipe[idx] !== (x<width*2 ? a[idx] : changed[idx]));
        }
      }
      compare=mismatch;
    }
    let input;
    if(id==='bottom_sheet' || id==='refraction_wave' || id==='directional_blur' || id==='pull_refresh') {
      const center=read({ frame: frameAt(start,{x:.5,y:.46},{origin:[.5,.3],pressed:true,sincePress:.25}) });
      const moved=read({ frame: frameAt(start,{x:.36,y:.76},{origin:[.5,.3],pressed:true,sincePress:.43}) });
      input=difference(center,moved);
    }
    if(id==='dot_matrix') {
      read({ a: { ...base, gradient: { stops: { unexpected: true } }, pointerLens: { radius: { toString: 0 } } } });
    }
    return { colors:colors.size, checksum, repeat:difference(a,repeat), animated:difference(a,later), parameter, compare, input };
  } finally {
    const textures = [...Object.values(renderer.textures),
      ...renderer.atlases.flatMap(slot => Object.values(slot).map(({ texture }) => texture))];
    renderer.dispose();
    if(gl.isProgram(renderer.program)) throw new Error('Effect program retained after disposal');
    if(textures.some(texture => gl.isTexture(texture))) throw new Error('Effect texture retained after disposal');
  }
}

const server = await serve();
let browser;
try {
  browser=await chromium();
  await browser.command('Page.enable');
  await browser.command('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  await browser.command('Emulation.setDeviceMetricsOverride',{width:1440,height:960,deviceScaleFactor:1,mobile:false});
  await browser.command('Page.navigate',{url:server.url});
  await eventually(() => browser.evaluate("document.querySelector('#previewCanvas')?.dataset.ready === 'true'"),'managed adapter loaded');
  const names=await browser.evaluate("fetch('./catalog.json').then(r=>r.json()).then(c=>{window.testCatalog=c;return c.materials.filter(m=>m.webRendererId==='material-effects').map(m=>m.id.split(':')[1]);})");
  assert.equal(names.length,51);
  const checksums=new Set();
  const warnings=[];
  let parameterChecks=0, motionChecks=0, inputChecks=0;
  for(const id of names) {
    try {
      const result=await browser.call(renderMaterial,id,changes[id] ?? null);
      assert.ok(result.colors>8,'insufficient visible color range: '+result.colors);
      assert.equal(result.repeat,0,'fixture is nondeterministic');
      assert.ok(!checksums.has(result.checksum),'matches a previously rendered material');
      checksums.add(result.checksum);
      if(!staticEffects.has(id)) {
        assert.ok(result.animated>0,'fixture time and pointer made no visible change');
        motionChecks++;
      }
      if(changes[id]) {
        assert.ok(result.parameter>0,'editing the material made no visible change');
        assert.equal(result.compare,0,'split/wipe output differs from independent drafts');
        parameterChecks++;
      }
      if(result.input !== undefined) {
        assert.ok(result.input>0,'manual pointer/action state made no visible change');
        inputChecks++;
      }
    } catch(error) {
      warnings.push(id+': '+error.message);
    }
  }
  assert.deepEqual(warnings,[]);
  await browser.evaluate("document.querySelector('[data-material=\"sh_better_ui:live_activity\"]').click()");
  await eventually(() => browser.evaluate("document.querySelector('#previewCanvas').dataset.ready === 'true' && document.querySelector('#materialId').textContent === 'sh_better_ui:live_activity'"),'activity island catalog entry mounted');
  await browser.evaluate("document.querySelector('[data-material=\"sh_better_ui:bottom_sheet\"]').click()");
  await eventually(() => browser.evaluate("document.querySelector('#previewCanvas').dataset.ready === 'true' && document.querySelector('#stage').dataset.renderer === 'material-effects'"),'effect adapter mounted by catalog UI');
  assert.equal(await browser.evaluate("document.querySelector('#play').textContent"),'播放');
  assert.equal(await browser.evaluate("document.querySelector('#opticalControl').hidden"),true);
  await browser.evaluate("document.querySelector('#inputMode').value='manual';document.querySelector('#inputMode').dispatchEvent(new Event('change'));document.querySelector('#previewPress').click()");
  assert.equal(await browser.evaluate("document.querySelector('#previewPress').getAttribute('aria-pressed')"),'true');
  await browser.evaluate("document.querySelector('#previewPress').click()");
  assert.equal(await browser.evaluate("document.querySelector('#previewPress').getAttribute('aria-pressed')"),'false');
  await browser.evaluate("document.querySelector('#previewPress').click();document.querySelector('#previewPress').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));");
  assert.equal(await browser.evaluate("document.querySelector('#previewPress').getAttribute('aria-pressed')"),'false');
  const backend=await browser.evaluate("(() => { const gl=window.testCanvas.getContext('webgl2'); const ext=gl.getExtension('WEBGL_debug_renderer_info'); return gl.getParameter(ext?.UNMASKED_RENDERER_WEBGL ?? gl.RENDERER); })()");
  console.log('WebGL 2 material effects on '+backend+': '+names.length+' distinct renders, '+motionChecks+' moving fixtures, '+
    parameterChecks+' parameter and A/B comparisons, '+inputChecks+' direct input probes; catalog mount, keyboard toggle and cancel passed.');
} finally {
  await browser?.close();
  await server.close();
}
