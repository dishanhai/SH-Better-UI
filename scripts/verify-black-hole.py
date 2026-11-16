#!/usr/bin/env python3
"""Compile and render SHBUI's original black-hole shader with software EGL.

Requires Python 3, Pillow, libEGL and libGL (Mesa surfaceless support).
Run from anywhere: python scripts/verify-black-hole.py --output /path/to/results
This is not Minecraft, real GPU validation, or a frame-rate guarantee.
"""
import argparse
import ctypes as C
import json
import math
import os
from pathlib import Path
import statistics
import time

from PIL import Image, ImageDraw

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--benchmark', action='store_true')
parser.add_argument('--width', type=int, default=2048)
parser.add_argument('--height', type=int, default=1280)
parser.add_argument('--before-ref', default='290c8f6', help='Git revision for matched-resolution old renderer comparison')
parser.add_argument('--animation', action='store_true', help='Write a side-by-side GIF of the actual shaders')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
# Software rendering keeps this test independent from a desktop login/session.
os.environ['LIBGL_ALWAYS_SOFTWARE'] = '1'
os.environ['MESA_SHADER_CACHE_DISABLE'] = 'true'
E, G = C.CDLL('libEGL.so.1'), C.CDLL('libGL.so.1')
P, I, U, F = C.c_void_p, C.c_int, C.c_uint, C.c_float


def bind(lib, name, result, *arguments):
    function = getattr(lib, name)
    function.restype, function.argtypes = result, arguments
    return function


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


get_proc = bind(E, 'eglGetProcAddress', P, C.c_char_p)
address = get_proc(b'eglGetPlatformDisplayEXT')
require(address, 'EGL_EXT_platform_base is unavailable')
platform_display = C.CFUNCTYPE(P, U, P, C.POINTER(I))(address)
display = platform_display(0x31DD, None, None)  # EGL_PLATFORM_SURFACELESS_MESA
major, minor = I(), I()
require(bind(E, 'eglInitialize', U, P, C.POINTER(I), C.POINTER(I))(
    display, C.byref(major), C.byref(minor)), 'Cannot initialize software EGL')
require(bind(E, 'eglBindAPI', U, U)(0x30A2), 'Cannot bind OpenGL API')
attributes = (I * 13)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3021, 8, 0x3025, 0, 0x3026, 0, 0x3038)
config, count = P(), I()
require(bind(E, 'eglChooseConfig', U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))(
    display, attributes, C.byref(config), 1, C.byref(count)) and count.value,
    'Cannot find RGBA8 pbuffer configuration')
context_attributes = (I * 7)(0x3098, 3, 0x30FB, 2, 0x30FD, 1, 0x3038)
context = bind(E, 'eglCreateContext', P, P, P, P, C.POINTER(I))(
    display, config, None, context_attributes)
require(context, 'Cannot create OpenGL 3.2 core context')
surface_attributes = (I * 5)(0x3057, 768, 0x3056, 768, 0x3038)
surface = bind(E, 'eglCreatePbufferSurface', P, P, P, C.POINTER(I))(
    display, config, surface_attributes)
require(surface, 'Cannot create pbuffer')
make_current = bind(E, 'eglMakeCurrent', U, P, P, P, P)
require(make_current(display, surface, surface, context), 'Cannot make context current')
get_string = bind(G, 'glGetString', C.c_char_p, U)
report = {'renderer': get_string(0x1F01).decode(), 'gl': get_string(0x1F02).decode(),
          'egl': f'{major.value}.{minor.value}', 'checks': []}

shader_root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/sh_better_ui/shaders/core'

def program(vertex, fragment):
    result = bind(G, 'glCreateProgram', U)()
    for name, kind in [(vertex, 0x8B31), (fragment, 0x8B30)]:
        shader = bind(G, 'glCreateShader', U, U)(kind)
        code = C.c_char_p((shader_root / name).read_bytes())
        bind(G, 'glShaderSource', None, U, I, C.POINTER(C.c_char_p), C.POINTER(I))(shader, 1, C.byref(code), None)
        bind(G, 'glCompileShader', None, U)(shader)
        status, log = I(), C.create_string_buffer(8192)
        bind(G, 'glGetShaderiv', None, U, U, C.POINTER(I))(shader, 0x8B81, C.byref(status))
        bind(G, 'glGetShaderInfoLog', None, U, I, C.POINTER(I), C.c_char_p)(shader, len(log), None, log)
        require(status.value, f'{name}: {log.value.decode()}')
        bind(G, 'glAttachShader', None, U, U)(result, shader)
        bind(G, 'glDeleteShader', None, U)(shader)
    bind(G, 'glLinkProgram', None, U)(result)
    status, log = I(), C.create_string_buffer(8192)
    bind(G, 'glGetProgramiv', None, U, U, C.POINTER(I))(result, 0x8B82, C.byref(status))
    bind(G, 'glGetProgramInfoLog', None, U, I, C.POINTER(I), C.c_char_p)(result, len(log), None, log)
    require(status.value, f'Link failed: {log.value.decode()}')
    return result


# Reconstruct the real baseline shader from this repository, never from a UI screenshot.
import subprocess
import tempfile
from PIL import ImageChops, ImageStat
repo = Path(__file__).resolve().parents[1]
current_root = shader_root
baseline_dir = tempfile.TemporaryDirectory(prefix='blackhole-before-')
for name in ['black_hole_backdrop.vsh', 'black_hole_backdrop.fsh', 'black_hole_composite.fsh', 'icon_gradient_sweep.vsh']:
    content = subprocess.check_output(['git', 'show', args.before_ref+':src/main/resources/assets/sh_better_ui/shaders/core/'+name], cwd=repo)
    (Path(baseline_dir.name)/name).write_bytes(content)
shader_root = Path(baseline_dir.name)
before_trace = program('black_hole_backdrop.vsh', 'black_hole_backdrop.fsh')
before_composite = program('icon_gradient_sweep.vsh', 'black_hole_composite.fsh')
shader_root = current_root
trace_program = program('black_hole_backdrop.vsh', 'black_hole_backdrop.fsh')
shade_program = program('black_hole_backdrop.vsh', 'black_hole_shade.fsh')
bloom_program = program('black_hole_backdrop.vsh', 'black_hole_bloom.fsh')
composite_program = program('icon_gradient_sweep.vsh', 'black_hole_composite.fsh')
report['checks'].append('baseline and all four revised programs compiled/linked')
use = bind(G, 'glUseProgram', None, U)
get_uniform = bind(G, 'glGetUniformLocation', I, U, C.c_char_p)
v4 = bind(G, 'glUniform4f', None, I, F, F, F, F)
v2 = bind(G, 'glUniform2f', None, I, F, F)
v1 = bind(G, 'glUniform1i', None, I, I)
mat = bind(G, 'glUniformMatrix4fv', None, I, I, U, C.POINTER(F))
identity = (F*16)(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)
vao, vbo = U(), U()
bind(G, 'glGenVertexArrays', None, I, C.POINTER(U))(1, C.byref(vao))
bind(G, 'glBindVertexArray', None, U)(vao)
bind(G, 'glGenBuffers', None, I, C.POINTER(U))(1, C.byref(vbo))
bind(G, 'glBindBuffer', None, U, U)(0x8892, vbo)
vertices = []
for x, y in [(0, 0), (0, 1), (1, 1), (0, 0), (1, 1), (1, 0)]:
    vertices.extend([2*x-1, 2*y-1, 0, x, y])
data = (F*len(vertices))(*vertices)
bind(G, 'glBufferData', None, U, C.c_ssize_t, P, U)(0x8892, C.sizeof(data), data, 0x88E4)


def location(prog, name):
    return get_uniform(prog, name.encode())


def attributes(prog):
    use(prog)
    for name, size, offset in [('Position', 3, 0), ('UV0', 2, 12)]:
        at = bind(G, 'glGetAttribLocation', I, U, C.c_char_p)(prog, name.encode())
        require(at >= 0, 'Missing attribute '+name)
        bind(G, 'glEnableVertexAttribArray', None, U)(at)
        bind(G, 'glVertexAttribPointer', None, U, I, U, U, I, P)(at, size, 0x1406, 0, 20, P(offset))


targets = []


def target(w, h, internal=0x8058, nearest=False):
    fbo, texture = U(), U()
    bind(G, 'glGenFramebuffers', None, I, C.POINTER(U))(1, C.byref(fbo))
    bind(G, 'glBindFramebuffer', None, U, U)(0x8D40, fbo)
    bind(G, 'glGenTextures', None, I, C.POINTER(U))(1, C.byref(texture))
    bind(G, 'glBindTexture', None, U, U)(0x0DE1, texture)
    bind(G, 'glTexImage2D', None, U, I, I, I, I, I, U, U, P)(0x0DE1, 0, internal, w, h, 0, 0x1908, 0x1406, None)
    for name, value in [(0x2801, 0x2600 if nearest else 0x2601), (0x2800, 0x2600 if nearest else 0x2601), (0x2802, 0x812F), (0x2803, 0x812F)]:
        bind(G, 'glTexParameteri', None, U, U, I)(0x0DE1, name, value)
    bind(G, 'glFramebufferTexture2D', None, U, U, U, U, I)(0x8D40, 0x8CE0, 0x0DE1, texture, 0)
    require(bind(G, 'glCheckFramebufferStatus', U, U)(0x8D40) == 0x8CD5, 'Target incomplete')
    targets.append((fbo, texture))
    return fbo, texture


W, H = args.width, args.height
require(0 < W <= 4096 and 0 < H <= 4096, 'Dimensions must be 1..4096')
geometry = target(1024, 1024, 0x881A, True)  # production RGBA16F, discrete NEAREST fate
scene = target(1024, 1024)
bloom_a, bloom_b = target(256, 256), target(256, 256)
old_scene = target(384, 384)
output = target(W, H, 0x8814)  # float diagnostics before conversion to PNG
viewport = bind(G, 'glViewport', None, I, I, I, I)
draw = bind(G, 'glDrawArrays', None, U, I, I)
finish = bind(G, 'glFinish', None)
read = bind(G, 'glReadPixels', None, I, I, I, I, U, U, P)
quality = {'LOW': (.04, 192, 384), 'BALANCED': (.025, 320, 768), 'HIGH': (.016, 512, 1024)}
last_geometry = None
trace_calls = 0


def activate(target_value, w, h, prog):
    bind(G, 'glBindFramebuffer', None, U, U)(0x8D40, target_value[0])
    viewport(0, 0, w, h)
    attributes(prog)


def sampler(prog, name, texture, unit=0):
    bind(G, 'glActiveTexture', None, U)(0x84C0+unit)
    bind(G, 'glBindTexture', None, U, U)(0x0DE1, texture)
    v1(location(prog, name), unit)


def color(prog, ink_alpha=1, base_alpha=1):
    v4(location(prog, 'InkColor'), 245/255, 245/255, 245/255, ink_alpha)
    v4(location(prog, 'BackgroundColor'), 8/255, 8/255, 8/255, base_alpha)


def pixels(finite=False):
    if finite:
        floats = (F*(W*H*4))()
        read(0, 0, W, H, 0x1908, 0x1406, floats)
        require(all(math.isfinite(v) for v in floats), 'Non-finite composite output')
    data = (C.c_ubyte*(W*H*4))()
    read(0, 0, W, H, 0x1908, 0x1401, data)
    return Image.frombytes('RGBA', (W, H), bytes(data))


def build_geometry(size=1, tilt=.08, inclination=.25, outer=18, tier='BALANCED'):
    global last_geometry, trace_calls
    key = (W, H, size, tilt, inclination, outer, tier)
    step, steps, cap = quality[tier]
    ratio = min(1, cap/max(W, H))
    rw, rh = max(1, round(W*ratio)), max(1, round(H*ratio))
    if key == last_geometry:
        return rw, rh
    activate(geometry, rw, rh, trace_program)
    v4(location(trace_program, 'Surface'), W/H, size, 0, tilt)
    v4(location(trace_program, 'Physics'), inclination, outer, 0, 0)
    v2(location(trace_program, 'Integration'), step, steps)
    bind(G, 'glEnable', None, U)(0x0C11)
    rows = max(1, 16384//rw)
    times = []
    for y in range(0, rh, rows):
        count = min(rows, rh-y)
        require(count*rw <= 16384, 'Geometry stripe exceeds frame budget')
        bind(G, 'glScissor', None, I, I, I, I)(0, y, rw, count)
        start = time.perf_counter(); draw(4, 0, 6); finish()
        times.append((time.perf_counter()-start)*1000)
        trace_calls += 1
    bind(G, 'glDisable', None, U)(0x0C11)
    floats = (F*(rw*rh*4))()
    read(0, 0, rw, rh, 0x1908, 0x1406, floats)
    require(all(math.isfinite(v) for v in floats), 'Non-finite half-float geometry')
    require(all(floats[i] in (-1, 0, 1) for i in range(3, len(floats), 4)), 'Corrupt discrete fate')
    last_geometry = key
    report.setdefault('geometry_builds', []).append({'tier': tier, 'extent': [rw, rh], 'stripes': len(times),
        'first_ms': times[0], 'median_ms': statistics.median(times), 'p95_ms': sorted(times)[max(0, math.ceil(len(times)*.95)-1)], 'sum_ms': sum(times)})
    return rw, rh


def render(phase=0, size=1, tilt=.08, inclination=.25, outer=18, brightness=1, outline=0,
           glow=1.2, opacity=1, base_alpha=1, ink_alpha=1, tier='BALANCED', finite=False, capture=True):
    rw, rh = build_geometry(size, tilt, inclination, outer, tier)
    start = time.perf_counter()
    activate(scene, rw, rh, shade_program)
    sampler(shade_program, 'Geometry', geometry[1])
    v2(location(shade_program, 'ContentScale'), rw/1024, rh/1024)
    v4(location(shade_program, 'Motion'), phase, brightness, outline, 0)
    color(shade_program, ink_alpha, base_alpha)
    draw(4, 0, 6)
    ratio = min(1, 256/max(rw, rh)); bw, bh = max(1, round(rw*ratio)), max(1, round(rh*ratio))
    if glow > 0 and brightness > 0:
        spread = 1+glow*1.5
        for dst, source_texture, sw, sh, capacity, dx, dy, threshold in [
            (bloom_a, scene[1], rw, rh, 1024, spread/bw, 0, .35),
            (bloom_b, bloom_a[1], bw, bh, 256, 0, spread/bh, 0)]:
            activate(dst, bw, bh, bloom_program)
            sampler(bloom_program, 'Source', source_texture)
            v2(location(bloom_program, 'ContentScale'), sw/capacity, sh/capacity)
            v2(location(bloom_program, 'Texel'), 1/sw, 1/sh)
            v4(location(bloom_program, 'Filter'), dx, dy, threshold, 0)
            draw(4, 0, 6)
    activate(output, W, H, composite_program)
    sampler(composite_program, 'Scene', scene[1]); sampler(composite_program, 'Bloom', bloom_b[1], 1)
    mat(location(composite_program, 'ModelViewMat'), 1, 0, identity); mat(location(composite_program, 'ProjMat'), 1, 0, identity)
    v4(location(composite_program, 'ColorModulator'), 1, 1, 1, 1)
    v4(location(composite_program, 'ContentScale'), rw/1024, rh/1024, bw/256, bh/256)
    v4(location(composite_program, 'Finish'), 1/rw, 1/rh, glow if brightness > 0 else 0, opacity)
    draw(4, 0, 6); finish()
    elapsed = (time.perf_counter()-start)*1000
    return pixels(finite) if capture else None, elapsed


def render_before(phase=0):
    ratio = min(1, 256/max(W, H)); rw, rh = max(1, round(W*ratio)), max(1, round(H*ratio))
    activate(old_scene, rw, rh, before_trace)
    v4(location(before_trace, 'Surface'), W/H, 1, phase, .08)
    v4(location(before_trace, 'Physics'), .25, 18, 1, 0)
    v2(location(before_trace, 'Integration'), .025, 320); color(before_trace)
    draw(4, 0, 6)
    activate(output, W, H, before_composite)
    sampler(before_composite, 'Scene', old_scene[1])
    mat(location(before_composite, 'ModelViewMat'), 1, 0, identity); mat(location(before_composite, 'ProjMat'), 1, 0, identity)
    v4(location(before_composite, 'ColorModulator'), 1, 1, 1, 1)
    v2(location(before_composite, 'ContentScale'), rw/384, rh/384)
    v4(location(before_composite, 'Finish'), 1/rw, 1/rh, 1.2, 1)
    draw(4, 0, 6)
    return pixels()


def delta(a, b):
    diff = ImageChops.difference(a.convert('RGB'), b.convert('RGB'))
    bands = diff.split(); maximum = ImageChops.lighter(ImageChops.lighter(bands[0], bands[1]), bands[2])
    histogram = maximum.histogram()
    return {'mean_rgb_absolute_255': sum(ImageStat.Stat(diff).mean)/3,
            'max_channel_255': max(hi for lo, hi in diff.getextrema()),
            'fraction_pixels_over_3': sum(histogram[4:])/(W*H)}


before0 = render_before(); before1 = render_before(.18)
after0, _ = render(finite=True); calls = trace_calls
after1, _ = render(.18, finite=True); frozen, _ = render(0)
require(trace_calls == calls, 'Phase change incorrectly triggered geometry work')
require(frozen.tobytes() == after0.tobytes(), 'Frozen phase must be deterministic')
wrap, _ = render(math.tau)
require(delta(after0, wrap)['max_channel_255'] <= 2, 'Periodic phase discontinuity')
for name, image in [('before-0s', before0), ('before-1s', before1), ('after-0s', after0), ('after-1s', after1)]:
    image.save(args.output/(name+'.png'))
report['comparison'] = {'dimensions': [W, H], 'baseline_ref': args.before_ref, 'elapsed_seconds': 1,
                         'speed_radians_per_second': .18, 'before': delta(before0, before1), 'after': delta(after0, after1),
                         'geometry_work_for_phase_change': trace_calls-calls}
report['checks'].extend(['phase changes reuse geometry', 'frozen phase identical', '2pi wrap continuous', 'finite RGBA16F geometry with discrete fate'])
for name, parameters in [('transparent', {'base_alpha': 0}), ('opacity-zero', {'opacity': 0}), ('brightness-zero', {'brightness': 0}),
                          ('outline', {'outline': 1}), ('glow-heavy', {'glow': 3}), ('glow-zero', {'glow': 0})]:
    image, _ = render(**parameters, finite=True)
    image.save(args.output/(name+'.png'))
    if name == 'opacity-zero': require(image.getextrema()[3] == (0, 0), 'Zero opacity failed')
    if name == 'brightness-zero': require(image.getextrema() == ((8, 8), (8, 8), (8, 8), (255, 255)), 'Zero brightness failed')
if args.animation:
    frames = []
    for frame in range(32):
        seconds = frame/10
        old = render_before(seconds*.18).convert('RGB').resize((512, 320), Image.Resampling.LANCZOS)
        new = render(seconds*.18)[0].convert('RGB').resize((512, 320), Image.Resampling.LANCZOS)
        pair = Image.new('RGB', (1024, 350), (8, 8, 8)); pair.paste(old, (0, 30)); pair.paste(new, (512, 30))
        painter = ImageDraw.Draw(pair); painter.text((12, 9), 'BEFORE / 256px + whole-scene blur', fill='white')
        painter.text((524, 9), 'AFTER / cached 768px geometry + moving emission', fill='white')
        frames.append(pair)
    frames[0].save(args.output/'motion-before-after.gif', save_all=True, append_images=frames[1:], duration=100, loop=0)
if args.benchmark:
    report['steady_frame_ms'] = {}
    for tier in quality:
        for i in range(6): render(i*.018, tier=tier, capture=False)
        calls = trace_calls
        samples = [render(i*.018, tier=tier, capture=False)[1] for i in range(20)]
        require(trace_calls == calls, 'Steady benchmark retraced geometry')
        report['steady_frame_ms'][tier] = {'median': statistics.median(samples), 'p95': sorted(samples)[18], 'geometry_calls': 0}
error = bind(G, 'glGetError', U)()
require(error == 0, f'GL error {error:#x}')
report['gl_error'] = error
report['timing_scope'] = 'software EGL; geometry stripes include glFinish, steady shade+bloom+composite excludes allocation/readback; not Minecraft/hardware FPS'
(args.output/'results.json').write_text(json.dumps(report, indent=2)+'\n')
for fbo, texture in targets:
    bind(G, 'glDeleteFramebuffers', None, I, C.POINTER(U))(1, C.byref(fbo))
    bind(G, 'glDeleteTextures', None, I, C.POINTER(U))(1, C.byref(texture))
for prog in [trace_program, shade_program, bloom_program, composite_program, before_trace, before_composite]:
    bind(G, 'glDeleteProgram', None, U)(prog)
bind(G, 'glDeleteBuffers', None, I, C.POINTER(U))(1, C.byref(vbo))
bind(G, 'glDeleteVertexArrays', None, I, C.POINTER(U))(1, C.byref(vao))
make_current(display, None, None, None)
bind(E, 'eglDestroyContext', U, P, P)(display, context)
bind(E, 'eglDestroySurface', U, P, P)(display, surface)
bind(E, 'eglTerminate', U, P)(display)
baseline_dir.cleanup()
print(json.dumps(report, indent=2))
