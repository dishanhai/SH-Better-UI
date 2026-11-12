// Author: dishanhai
import { linkProgram } from './webgl-program.mjs';
const FIXTURES = new URL('../fixtures/', import.meta.url);
const SHADERS = new URL('../shaders/', import.meta.url);
const QUALITY = Object.freeze({ FALLBACK: 0, PERFORMANCE: 1, BALANCED: 2, ULTRA: 3 });
const SHAPES = Object.freeze({ ROUNDED: 0, CAPSULE: 0, ELLIPSE: 2, CONTINUOUS: 3 });
const OPTICAL_PROFILES = Object.freeze({ legacy: 0, snell: 1, snellClear: 2 });
const CAPTURE_BUDGET = 32 * 1024 * 1024;

// The scene pass authors the deterministic color attachment sampled by the native shader.
const SCENE_VERTEX = `#version 300 es
precision highp float;
out vec2 TexCoord;
void main() {
  TexCoord = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));
  gl_Position = vec4(TexCoord * 2.0 - 1.0, 0.0, 1.0);
}`;

const SCENE_FRAGMENT = `#version 300 es
precision highp float;
uniform sampler2D Source;
uniform vec2 Size;
uniform vec4 MovingRect;
uniform vec3 MovingColor;
uniform vec4 Crop;
in vec2 TexCoord;
out vec4 Color;
void main() {
  vec2 uv = Crop.xy + vec2(TexCoord.x, 1.0 - TexCoord.y) * Crop.zw;
  vec2 pixel = uv * Size;
  vec2 box = step(MovingRect.xy, pixel) * (1.0 - step(MovingRect.zw, pixel));
  Color = vec4(mix(texture(Source, uv).rgb, MovingColor, box.x * box.y), 1.0);
}`;

async function loadAsset(base, name, signal) {
  const response = await fetch(new URL(name, base), { signal, credentials: 'same-origin' });
  if (!response.ok) throw new Error(`资源加载失败（${name}，HTTP ${response.status}）`);
  return response;
}

export async function openManagedGlass(canvas, { signal } = {}) {
  const gl = canvas.getContext('webgl2', { alpha: true, premultipliedAlpha: false, antialias: false, depth: false, stencil: false });
  if (!gl) throw new Error('浏览器或显卡当前无法创建 WebGL 2 上下文');
  const [fixture, image, vertex, fragment] = await Promise.all([
    loadAsset(FIXTURES, 'liquid-glass.json', signal).then(result => result.json()),
    loadAsset(FIXTURES, 'liquid-glass.png', signal).then(result => result.blob()),
    loadAsset(SHADERS, 'managed-glass.vert', signal).then(result => result.text()),
    loadAsset(SHADERS, 'managed-glass.frag', signal).then(result => result.text()),
  ]);
  const bitmap = await createImageBitmap(image, { colorSpaceConversion: 'none', premultiplyAlpha: 'none' });
  try {
    signal?.throwIfAborted();
    if (bitmap.width !== fixture.width || bitmap.height !== fixture.height || fixture.colorSpace !== 'srgb') {
      throw new Error('场景纹理尺寸或色彩空间与夹具不符');
    }
    return new ManagedGlassRenderer(canvas, gl, fixture, bitmap, vertex, fragment);
  } finally {
    bitmap.close();
  }
}

function uniforms(gl, program, names) {
  return Object.fromEntries(names.map(name => {
    const location = gl.getUniformLocation(program, name);
    if (location === null) throw new Error(`着色器缺少 uniform ${name}`);
    return [name, location];
  }));
}

function allocateTexture(gl) {
  const texture = gl.createTexture();
  if (!texture) throw new Error('无法分配场景纹理');
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  return texture;
}

function rgb(hex) {
  const value = Number.parseInt(hex.slice(1), 16);
  return [(value >> 16 & 255) / 255, (value >> 8 & 255) / 255, (value & 255) / 255];
}

const FALLBACK_LIGHT = rgb('#20252c');
const FALLBACK_DARK = rgb('#e4e8ee');

class ManagedGlassRenderer {
  constructor(canvas, gl, fixture, bitmap, vertex, fragment) {
    this.canvas = canvas;
    this.gl = gl;
    this.fixture = fixture;
    this.movingColor = rgb(fixture.movingObject.color);
    this.maxTextureSize = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    this.maxViewport = gl.getParameter(gl.MAX_VIEWPORT_DIMS);
    this.timerExtension = gl.getExtension('EXT_disjoint_timer_query_webgl2');
    this.metrics = { frames: 0, shaderCompileMs: 0, cpuFrameMs: 0, gpuFrameMs: null, framebufferAllocations: 0, captureBytes: 0 };
    this.closed = false;
    try {
      const start = performance.now();
      this.sceneProgram = linkProgram(gl, '场景', SCENE_VERTEX, SCENE_FRAGMENT);
      this.glassProgram = linkProgram(gl, 'Liquid Glass', vertex, fragment);
      this.metrics.shaderCompileMs = performance.now() - start;
      this.sceneUniforms = uniforms(gl, this.sceneProgram, ['Source', 'Size', 'MovingRect', 'MovingColor', 'Crop']);
      this.glassUniforms = uniforms(gl, this.glassProgram,
        ['Backdrop', 'Viewport', 'Quad', 'Geometry', 'Radius', 'Optics', 'Material', 'Light', 'FlatColor', 'Mode',
          'OpticalProfile', 'BlurMaxLod']);
      this.vertexArray = gl.createVertexArray();
      if (!this.vertexArray) throw new Error('无法分配 WebGL 2 顶点状态');
      gl.activeTexture(gl.TEXTURE0);
      this.sourceTexture = allocateTexture(gl);
      gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL, gl.NONE);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, bitmap);
      if (gl.getError() !== gl.NO_ERROR) throw new Error('无法上传场景纹理');
    } catch (error) {
      this.dispose();
      throw error;
    }
  }

  ensureCapture(width, height) {
    const gl = this.gl;
    if (this.capture?.width === width && this.capture.height === height) return;
    if (width * height * 4 > CAPTURE_BUDGET || Math.max(width, height) > this.maxTextureSize) {
      throw new RangeError('场景超出当前 WebGL 2 捕获预算');
    }
    const replacement = this.allocateTarget(width, height);
    this.releaseTarget(this.capture);
    this.releaseTarget(this.splitTarget);
    this.capture = replacement;
    this.splitTarget = null;
    this.metrics.captureBytes = width * height * 4;
    this.metrics.splitBytes = 0;
  }

  allocateTarget(width, height) {
    const gl = this.gl;
    const texture = allocateTexture(gl);
    const framebuffer = gl.createFramebuffer();
    try {
      if (!framebuffer) throw new Error('无法分配场景 framebuffer');
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, width, height, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
      if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw new Error('场景 framebuffer 不完整');
    } catch (error) {
      gl.deleteTexture(texture);
      gl.deleteFramebuffer(framebuffer);
      throw error;
    }
    this.metrics.framebufferAllocations++;
    return { width, height, texture, framebuffer };
  }

  releaseTarget(target) {
    if (!target) return;
    this.gl.deleteFramebuffer(target.framebuffer);
    this.gl.deleteTexture(target.texture);
  }

  paintBackdrop(width, height, movingRect, zoomed) {
    const gl = this.gl;
    const u = this.sceneUniforms;
    const { x, y, width: movingWidth, height: movingHeight } = movingRect;
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.capture.framebuffer);
    gl.viewport(0, 0, width, height);
    gl.disable(gl.BLEND);
    gl.disable(gl.SCISSOR_TEST);
    gl.useProgram(this.sceneProgram);
    gl.bindTexture(gl.TEXTURE_2D, this.sourceTexture);
    gl.uniform1i(u.Source, 0);
    gl.uniform2f(u.Size, this.fixture.width, this.fixture.height);
    gl.uniform4f(u.MovingRect, x, y, x + movingWidth, y + movingHeight);
    gl.uniform3fv(u.MovingColor, this.movingColor);
    const crop = zoomed ? 0.14 : 0;
    gl.uniform4f(u.Crop, crop, crop, 1 - crop * 2, 1 - crop * 2);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }

  prepareBlur() {
    const gl = this.gl;
    const target = this.capture;
    if (target.blurLevels === 0) return 0;
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.bindTexture(gl.TEXTURE_2D, target.texture);
    if (target.blurLevels === undefined) {
      let { width, height } = target;
      let bytes = width * height * 4;
      // Reserve space for split mode when this size can support it.
      const budget = CAPTURE_BUDGET - (2 * bytes <= CAPTURE_BUDGET ? bytes : 0);
      let levels = 0;
      while (width > 1 || height > 1) {
        width = Math.max(1, Math.floor(width / 2));
        height = Math.max(1, Math.floor(height / 2));
        const next = width * height * 4;
        if (bytes + next > budget) break;
        bytes += next;
        levels++;
      }
      target.blurLevels = levels;
      if (levels === 0) return 0;
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAX_LEVEL, levels);
      gl.generateMipmap(gl.TEXTURE_2D);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
      this.metrics.captureBytes = bytes;
    } else {
      gl.generateMipmap(gl.TEXTURE_2D);
    }
    return target.blurLevels;
  }

  paintSurface(width, height, material, light, highContrast) {
    const gl = this.gl;
    const u = this.glassUniforms;
    const region = this.fixture.surface;
    const scaleX = width / this.fixture.width;
    const scaleY = height / this.fixture.height;
    const unit = Math.min(scaleX, scaleY);
    const halfWidth = region.width * scaleX * 0.5;
    const halfHeight = region.height * scaleY * 0.5;
    const centerX = region.x * scaleX + halfWidth;
    const centerY = height - region.y * scaleY - halfHeight;
    const thickness = Math.min(halfWidth, halfHeight, material.thickness * unit);
    const lightText = material.foreground === 'LIGHT';
    const quality = QUALITY[material.quality];
    const shadow = Math.min(18, thickness * 0.9 + 1);
    const margin = quality === 0 ? 2 : shadow * 4 + 2;
    gl.viewport(0, 0, width, height);
    gl.uniform4f(u.Viewport, 0, 0, width, height);
    gl.uniform4f(u.Quad, Math.max(0, centerX - halfWidth - margin), Math.max(0, centerY - halfHeight - margin),
      Math.min(width, centerX + halfWidth + margin), Math.min(height, centerY + halfHeight + margin));
    gl.uniform4f(u.Geometry, centerX, centerY, halfWidth, halfHeight);
    gl.uniform1f(u.Radius, region.radius * unit);
    gl.uniform4f(u.Optics, material.refraction * unit, material.lensing, material.scattering, material.edgeHighlight);
    gl.uniform4f(u.Material, material.transmission, thickness, material.blurRadius * unit, lightText ? 1 : 0);
    gl.uniform3f(u.Light, light.x, -light.y, light.strength);
    gl.uniform3fv(u.FlatColor, lightText ? FALLBACK_LIGHT : FALLBACK_DARK);
    gl.uniform3i(u.Mode, SHAPES[region.shape], quality, highContrast ? 1 : 0);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }

  render({ frame, a, b, mode = 'single', divider = 50, dpi = this.fixture.dpi, zoomed = false,
    highContrast = false, layerOnly = false, measureGpu = false, opticalProfile = 'legacy' }) {
    if (this.closed || this.gl.isContextLost()) throw new Error('WebGL 2 预览上下文已关闭');
    if (!Object.hasOwn(OPTICAL_PROFILES, opticalProfile)) throw new RangeError('未知的玻璃光学模型');
    const profile = OPTICAL_PROFILES[opticalProfile];
    const start = performance.now();
    const gl = this.gl;
    const split = mode === 'split';
    const width = Math.round(this.fixture.width * this.fixture.guiScale * dpi);
    const height = Math.round(this.fixture.height * this.fixture.guiScale * dpi);
    const outputWidth = split ? 2 * width : width;
    if (outputWidth > this.maxViewport[0] || height > this.maxViewport[1]) throw new RangeError('预览尺寸超过当前显卡限制');
    if (this.canvas.width !== outputWidth) this.canvas.width = outputWidth;
    if (this.canvas.height !== height) this.canvas.height = height;
    if (gl.drawingBufferWidth !== outputWidth || gl.drawingBufferHeight !== height) throw new RangeError('浏览器无法分配此预览尺寸');

    this.pollGpuTiming();
    this.ensureCapture(width, height);
    if (split && !this.splitTarget) {
      if (width * height * 8 > CAPTURE_BUDGET) throw new RangeError('并排对比超出 framebuffer 预算');
      this.splitTarget = this.allocateTarget(width, height);
      this.metrics.splitBytes = width * height * 4;
    }
    const query = measureGpu && this.timerExtension && !this.pendingTimer ? gl.createQuery() : null;
    if (query) gl.beginQuery(this.timerExtension.TIME_ELAPSED_EXT, query);
    gl.bindVertexArray(this.vertexArray);
    gl.activeTexture(gl.TEXTURE0);
    this.paintBackdrop(width, height, frame.movingRect, zoomed);
    const unit = Math.min(width / this.fixture.width, height / this.fixture.height);
    const wideBlur = material => material && QUALITY[material.quality] > 1
      && material.scattering > 0 && material.blurRadius * unit > 2;
    const blurLevel = profile !== 0 && (wideBlur(a) || (mode !== 'single' && wideBlur(b))) ? this.prepareBlur() : 0;

    if (split) {
      // Render both sides in the same origin so float rounding cannot depend on placement.
      this.paintComposite(width, height, frame, a, null, 0, highContrast, layerOnly, profile, blurLevel, this.splitTarget.framebuffer);
      this.blitSplit(width, height, 0);
      this.paintComposite(width, height, frame, b, null, 0, highContrast, layerOnly, profile, blurLevel, this.splitTarget.framebuffer);
      this.blitSplit(width, height, width);
    } else {
      this.paintComposite(width, height, frame, a, mode === 'wipe' ? b : null, divider, highContrast, layerOnly, profile, blurLevel, null);
    }
    if (query) {
      gl.endQuery(this.timerExtension.TIME_ELAPSED_EXT);
      this.pendingTimer = query;
    }
    this.metrics.frames++;
    this.metrics.cpuFrameMs = performance.now() - start;
  }

  paintComposite(width, height, frame, a, b, divider, highContrast, layerOnly, profile, blurLevel, destination) {
    const gl = this.gl;
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER, this.capture.framebuffer);
    gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, destination);
    if (layerOnly) {
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
    } else {
      gl.blitFramebuffer(0, 0, width, height, 0, 0, width, height, gl.COLOR_BUFFER_BIT, gl.NEAREST);
    }
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER, null);
    gl.useProgram(this.glassProgram);
    gl.bindTexture(gl.TEXTURE_2D, this.capture.texture);
    gl.uniform1i(this.glassUniforms.Backdrop, 0);
    gl.uniform1i(this.glassUniforms.OpticalProfile, profile);
    gl.uniform1f(this.glassUniforms.BlurMaxLod, blurLevel);
    if (!layerOnly) {
      gl.enable(gl.BLEND);
      gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    }
    if (b) {
      const boundary = Math.round(width * divider / 100);
      gl.enable(gl.SCISSOR_TEST);
      gl.scissor(0, 0, boundary, height);
      this.paintSurface(width, height, a, frame.light, highContrast);
      gl.scissor(boundary, 0, width - boundary, height);
      this.paintSurface(width, height, b, frame.light, highContrast);
      gl.disable(gl.SCISSOR_TEST);
    } else {
      this.paintSurface(width, height, a, frame.light, highContrast);
    }
    gl.disable(gl.BLEND);
  }

  blitSplit(width, height, offset) {
    const gl = this.gl;
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER, this.splitTarget.framebuffer);
    gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, null);
    gl.blitFramebuffer(0, 0, width, height, offset, 0, offset + width, height, gl.COLOR_BUFFER_BIT, gl.NEAREST);
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER, null);
  }

  pollGpuTiming() {
    const gl = this.gl;
    if (!this.pendingTimer || !gl.getQueryParameter(this.pendingTimer, gl.QUERY_RESULT_AVAILABLE)) return;
    if (!gl.getParameter(this.timerExtension.GPU_DISJOINT_EXT)) {
      this.metrics.gpuFrameMs = gl.getQueryParameter(this.pendingTimer, gl.QUERY_RESULT) / 1e6;
    }
    gl.deleteQuery(this.pendingTimer);
    this.pendingTimer = null;
  }

  diagnostics() {
    this.pollGpuTiming();
    return { ...this.metrics, maxTextureSize: this.maxTextureSize, gpuTimerAvailable: !!this.timerExtension };
  }

  dispose() {
    if (this.closed) return;
    this.closed = true;
    this.releaseTarget(this.capture);
    this.releaseTarget(this.splitTarget);
    this.capture = this.splitTarget = null;
    this.metrics.captureBytes = this.metrics.splitBytes = 0;
    const gl = this.gl;
    if (this.pendingTimer) gl.deleteQuery(this.pendingTimer);
    this.pendingTimer = null;
    gl.bindTexture(gl.TEXTURE_2D, null);
    gl.useProgram(null);
    gl.bindVertexArray(null);
    gl.deleteTexture(this.sourceTexture ?? null);
    gl.deleteProgram(this.sceneProgram ?? null);
    gl.deleteProgram(this.glassProgram ?? null);
    gl.deleteVertexArray(this.vertexArray ?? null);
  }
}
