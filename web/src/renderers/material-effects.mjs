// Author: dishanhai
import { PRELUDE, VERTEX } from './effect-shader.mjs';
import { linkProgram } from './webgl-program.mjs';
import { fieldEffects } from './effects/fields.mjs';
import { mediaEffects } from './effects/media.mjs';
import { motionEffects } from './effects/motion.mjs';
import { surfaceEffects } from './effects/surfaces.mjs';

const namespace = 'sh_better_ui:';
const effects = { ...fieldEffects, ...mediaEffects, ...motionEffects, ...surfaceEffects };
const supportedTypes = { float: 'float', integer: 'float', duration: 'float', boolean: 'float', enum: 'float', color: 'vec4' };
const contextOptions = { alpha: true, premultipliedAlpha: false, antialias: false, depth: false, stencil: false };
const TEXTURE_UNITS = { glyphAtlas: 0, fixtureText: 1, nativeIcon: 2 };
const defaultGlyphs = ' .:+=xX0S8#@';
const fixture = {
  width: 640, height: 384, dpi: 1, guiScale: 1, fps: 60, durationMs: 4200,
  surface: { x: 0, y: 0, width: 640, height: 384 },
  pointer: [
    { timeMs: 0, x: .44, y: .49 }, { timeMs: 450, x: .27, y: .41 },
    { timeMs: 1150, x: .69, y: .56 }, { timeMs: 1850, x: .81, y: .33 },
    { timeMs: 2850, x: .35, y: .63 }, { timeMs: 4200, x: .44, y: .49 },
  ],
  inputEvents: [
    { timeMs: 0, type: 'pointerenter' },
    { timeMs: 140, type: 'pointerdown', x: .42, y: .48 },
    { timeMs: 340, type: 'pointerup' },
    { timeMs: 1560, type: 'pointerdown', x: .70, y: .39 },
    { timeMs: 1980, type: 'pointerup' },
  ],
  movingObject: { width: 0, height: 0, points: [{ timeMs: 0, x: 0, y: 0 }] },
};
const stillFrames = { page_slide: 250, pull_refresh: 700, toast_motion: 600, capsule_reveal: 320,
  attention_indicator: 250, loading_progress: 880, swipe_toast: 490, bottom_sheet: 650,
  click_ripple: 100, interruptible_animation: 320, recipe_transition: 280, refraction_wave: 530, live_activity: 1400 };
const pressEffects = new Set(['antigravity','ascii_field','bottom_sheet','button_feedback','click_ripple',
  'code_rain','dot_matrix','elastic_collision','interruptible_animation','light_rays','pattern_waves',
  'pull_refresh','refraction_wave','screen_crossfade','shape_waves','swipe_toast','toast_motion']);

function effectFor(entry) {
  return entry?.id?.startsWith(namespace) && Object.hasOwn(effects, entry.id.slice(namespace.length))
    ? effects[entry.id.slice(namespace.length)] : null;
}

export function supportsMaterialEffect(entry) { return !!effectFor(entry); }

function colorChannels(argb) {
  const value = parseInt(argb.slice(1),16);
  return [value >> 16 & 255,value >> 8 & 255,value & 255,value >>> 24].map(channel => channel/255);
}

function parameterValues(parameters, definitions) {
  return Object.fromEntries(Object.entries(definitions).map(([key, definition]) => {
    const value = parameters[key] ?? definition.defaultValue;
    if (definition.type === 'enum') return [key, Math.max(0, definition.choices.indexOf(value))];
    if (definition.type === 'boolean') return [key, Number(value)];
    if (definition.type === 'color') return [key, colorChannels(value)];
    return [key, value];
  }));
}

function makeTexture(gl, unit) {
  const texture = gl.createTexture();
  if (!texture) throw new Error('无法分配预览纹理');
  gl.activeTexture(gl.TEXTURE0 + unit);
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  return texture;
}

function textureCanvas(width, height) {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('无法生成预览字形纹理');
  ctx.fillStyle = '#fff';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  return ctx;
}

function drawGlyphs(characters) {
  const glyphs = Array.from(characters).slice(0, 128);
  const ctx = textureCanvas(32*glyphs.length, 40);
  ctx.font = '600 29px monospace';
  glyphs.forEach((glyph, index) => ctx.fillText(glyph, index*32+16, 22, 28));
  return ctx.canvas;
}

function drawText(entry, text) {
  const width = 512, height = 80;
  const ctx = textureCanvas(width, height*4);
  const font = typeof text?.fontFamily === 'string' ? text.fontFamily : 'sans-serif';
  const weight = Math.min(950, Math.max(100, finite(text?.fontWeight) || 650));
  const lines = [entry.name, 'Under a waking sky', '微光映过潮汐', typeof text?.text === 'string' ? text.text : 'SH'];
  for (const [index, line] of lines.entries()) {
    const large = index === 3 && entry.id === 'sh_better_ui:electric_logo';
    ctx.font = index === 3 ? String(weight)+' '+(large ? '75' : '49')+'px '+font : '650 47px sans-serif';
    ctx.fillText(line.slice(0,128), width*.5, index*height+height*.52, width*.94);
  }
  return ctx.canvas;
}

function finite(value) { return Number.isFinite(value) ? value : 0; }

export async function openMaterialEffect(canvas, { entry, signal } = {}) {
  const effect = effectFor(entry);
  if (!effect) throw new Error('当前构建没有此材质的效果着色器');
  const gl = canvas.getContext('webgl2', contextOptions);
  if (!gl) throw new Error('浏览器或显卡当前无法创建 WebGL 2 上下文');
  let icon;
  try {
    if (effect.icon) {
      const response = await fetch(new URL('../fixtures/material-shop.png', import.meta.url), { signal, credentials: 'same-origin' });
      if (!response.ok) throw new Error('预览图示加载失败（HTTP '+response.status+'）');
      icon = await createImageBitmap(await response.blob(), { colorSpaceConversion: 'none', premultiplyAlpha: 'none' });
    }
    signal?.throwIfAborted();
    return new MaterialEffectRenderer(canvas, gl, entry, effect, icon);
  } finally {
    icon?.close();
  }
}

class MaterialEffectRenderer {
  constructor(canvas, gl, entry, effect, icon) {
    this.canvas = canvas;
    this.gl = gl;
    this.entry = entry;
    this.effect = effect;
    const name = entry.id.slice(namespace.length);
    this.fixture = { ...fixture, stillTimeMs: stillFrames[name] ?? 440 };
    this.acceptsPress = pressEffects.has(name);
    this.textures = {};
    this.atlases = [{}, {}];
    this.maxViewport = gl.getParameter(gl.MAX_VIEWPORT_DIMS);
    this.uniforms = new Map();
    this.bindings = [];
    this.closed = false;
    this.frames = 0;
    const declarations = [];
    const bind = (name, type, value) => {
      if (!type || !new RegExp('\\b'+name+'\\b').test(effect.body)) return;
      this.bindings.push([name, type, value]);
      declarations.push('uniform '+type+' '+name+';');
    };
    for (const [key, definition] of Object.entries(entry.parameters)) {
      bind('p_'+key, supportedTypes[definition.type], values => values[key]);
    }
    for (const [name, [type, value]] of Object.entries(effect.extra ?? {})) bind(name, type, value);
    const fragment = [
      PRELUDE, ...declarations, effect.helpers ?? '', 'vec3 effect(vec2 p) {', effect.body, '}',
      'void main() { vec3 rgb = effect(pixel()); color = vec4(mix(rgb, clamp((rgb-.5)*1.3+.5,0.,1.),contrast*.7),1.); }',
    ].join('\n');
    try {
      this.program = linkProgram(gl, entry.id, VERTEX, fragment);
      this.vertexArray = gl.createVertexArray();
      if (!this.vertexArray) throw new Error('无法分配预览顶点状态');
      for (const [name, unit] of Object.entries(TEXTURE_UNITS)) {
        if (this.location(name) === null) continue;
        if (name === 'nativeIcon') this.textures[name] = makeTexture(gl, unit);
        else for (const slot of this.atlases) slot[name] = { texture: makeTexture(gl, unit), key: null };
      }
      if (icon) this.upload('nativeIcon', this.textures.nativeIcon, icon);
    } catch (error) {
      this.dispose();
      throw error;
    }
  }

  location(name) {
    if (!this.uniforms.has(name)) this.uniforms.set(name, this.gl.getUniformLocation(this.program,name));
    return this.uniforms.get(name);
  }

  upload(name, texture, source) {
    const gl = this.gl;
    gl.activeTexture(gl.TEXTURE0 + TEXTURE_UNITS[name]);
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL, gl.NONE);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, source);
  }

  set(name, type, value) {
    const location = this.location(name);
    if (location === null) return;
    const gl = this.gl;
    if (type === 'float') gl.uniform1f(location, finite(value));
    else gl['uniform'+type.at(-1)+'fv'](location, value.map(finite));
  }

  setTextures(values, slot) {
    const gl = this.gl;
    const atlas = this.atlases[slot];
    const bind = (name, texture) => {
      const unit = TEXTURE_UNITS[name];
      gl.activeTexture(gl.TEXTURE0 + unit);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.uniform1i(this.location(name), unit);
    };
    if (this.textures.nativeIcon) bind('nativeIcon', this.textures.nativeIcon);
    if (atlas.glyphAtlas) {
      const glyph = atlas.glyphAtlas;
      bind('glyphAtlas', glyph.texture);
      const input = this.effect.glyphs?.(values);
      const characters = typeof input === 'string' && input.length ? input.slice(0,128) : defaultGlyphs;
      if (characters !== glyph.key) {
        this.upload('glyphAtlas', glyph.texture, drawGlyphs(characters));
        glyph.key = characters;
      }
      this.set('glyphCount','float',Array.from(characters).length);
    }
    if (atlas.fixtureText) {
      const label = atlas.fixtureText;
      bind('fixtureText', label.texture);
      const text = this.effect.text?.(values) ?? null;
      const key = JSON.stringify(text);
      if (key !== label.key) {
        this.upload('fixtureText', label.texture, drawText(this.entry, text));
        label.key = key;
      }
    }
  }

  draw(frame, params, zoomed, highContrast, slot = 0) {
    const gl = this.gl;
    const values = parameterValues(params, this.entry.parameters);
    const { width, height } = this.fixture;
    const { action } = frame;
    this.set('sceneSize','vec2',[width,height]);
    this.set('pointer','vec2',frame.pointer ?? [.5,.5]);
    this.set('pressOrigin','vec2',action?.origin ?? [.5,.5]);
    this.set('releasePoint','vec2',action?.end ?? action?.origin ?? [.5,.5]);
    this.set('pressed','float',action?.pressed ? 1 : 0);
    this.set('sincePress','float',action?.sincePress ?? 100);
    this.set('sinceRelease','float',action?.sinceRelease ?? 100);
    this.set('manual','float',action?.manual ? 1 : 0);
    this.set('time','float',frame.timeMs/1000);
    this.set('zoom','float',zoomed ? .72 : 1);
    this.set('contrast','float',highContrast ? 1 : 0);
    this.setTextures(values, slot);
    for (const [name, type, value] of this.bindings) this.set(name,type,value(values));
    gl.drawArrays(gl.TRIANGLE_STRIP,0,4);
  }

  render({ frame, a, b, mode = 'single', divider = 50, dpi = 1, zoomed = false, highContrast = false }) {
    const gl = this.gl;
    if (this.closed || gl.isContextLost()) throw new Error('WebGL 2 预览上下文已关闭');
    const width = Math.round(this.fixture.width*dpi), height = Math.round(this.fixture.height*dpi);
    const split = mode === 'split', outputWidth = split ? width*2 : width;
    if (outputWidth > this.maxViewport[0] || height > this.maxViewport[1]) throw new RangeError('预览尺寸超过当前显卡限制');
    if (this.canvas.width !== outputWidth) this.canvas.width = outputWidth;
    if (this.canvas.height !== height) this.canvas.height = height;
    if (gl.drawingBufferWidth !== outputWidth || gl.drawingBufferHeight !== height) throw new RangeError('浏览器无法分配此预览尺寸');

    gl.bindFramebuffer(gl.FRAMEBUFFER,null);
    gl.bindVertexArray(this.vertexArray);
    gl.useProgram(this.program);
    gl.disable(gl.BLEND);
    gl.disable(gl.SCISSOR_TEST);
    gl.viewport(0,0,width,height);
    if (mode === 'wipe') {
      const edge = Math.round(width*divider/100);
      gl.enable(gl.SCISSOR_TEST);
      gl.scissor(0,0,edge,height);
      this.draw(frame,a,zoomed,highContrast);
      gl.scissor(edge,0,width-edge,height);
      this.draw(frame,b,zoomed,highContrast,1);
      gl.disable(gl.SCISSOR_TEST);
    } else {
      this.draw(frame,a,zoomed,highContrast);
      if (split) {
        gl.viewport(width,0,width,height);
        this.draw(frame,b,zoomed,highContrast,1);
      }
    }
    this.frames++;
  }

  diagnostics() { return { frames: this.frames, closed: this.closed }; }

  dispose() {
    if (this.closed) return;
    this.closed = true;
    if (this.gl.getParameter(this.gl.CURRENT_PROGRAM) === this.program) this.gl.useProgram(null);
    for (const texture of Object.values(this.textures)) this.gl.deleteTexture(texture);
    for (const slot of this.atlases) for (const { texture } of Object.values(slot)) this.gl.deleteTexture(texture);
    if (this.vertexArray) this.gl.deleteVertexArray(this.vertexArray);
    if (this.program) this.gl.deleteProgram(this.program);
  }
}
