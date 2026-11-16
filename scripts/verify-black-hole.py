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
parser.add_argument('--benchmark', action='store_true', help='12 warmups + 20 samples per quality, RGBA8 production format')
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


trace_program = program('black_hole_backdrop.vsh', 'black_hole_backdrop.fsh')
composite_program = program('icon_gradient_sweep.vsh', 'black_hole_composite.fsh')
report['checks'].append('trace and bloom composite compile/link passed')
use = bind(G, 'glUseProgram', None, U)
get_uniform = bind(G, 'glGetUniformLocation', I, U, C.c_char_p)
v4 = bind(G, 'glUniform4f', None, I, F, F, F, F)
v2 = bind(G, 'glUniform2f', None, I, F, F)
v1 = bind(G, 'glUniform1i', None, I, I)
mat = bind(G, 'glUniformMatrix4fv', None, I, I, U, C.POINTER(F))
identity = (F * 16)(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)
vao, vbo = U(), U()
bind(G, 'glGenVertexArrays', None, I, C.POINTER(U))(1, C.byref(vao))
bind(G, 'glBindVertexArray', None, U)(vao)
bind(G, 'glGenBuffers', None, I, C.POINTER(U))(1, C.byref(vbo))
bind(G, 'glBindBuffer', None, U, U)(0x8892, vbo)
vertices = []
for x, y in [(0, 0), (0, 1), (1, 1), (0, 0), (1, 1), (1, 0)]:
    vertices.extend([2*x-1, 2*y-1, 0, x, y])
data = (F * len(vertices))(*vertices)
bind(G, 'glBufferData', None, U, C.c_ssize_t, P, U)(0x8892, C.sizeof(data), data, 0x88E4)


def attributes(prog):
    for name, size, offset in [('Position', 3, 0), ('UV0', 2, 12)]:
        location = bind(G, 'glGetAttribLocation', I, U, C.c_char_p)(prog, name.encode())
        require(location >= 0, 'Missing attribute '+name)
        bind(G, 'glEnableVertexAttribArray', None, U)(location)
        bind(G, 'glVertexAttribPointer', None, U, I, U, U, I, P)(location, size, 0x1406, 0, 20, P(offset))


def location(prog, name):
    return get_uniform(prog, name.encode())


def target():
    fbo, texture = U(), U()
    bind(G, 'glGenFramebuffers', None, I, C.POINTER(U))(1, C.byref(fbo))
    bind(G, 'glBindFramebuffer', None, U, U)(0x8D40, fbo)
    bind(G, 'glGenTextures', None, I, C.POINTER(U))(1, C.byref(texture))
    bind(G, 'glBindTexture', None, U, U)(0x0DE1, texture)
    bind(G, 'glTexImage2D', None, U, I, I, I, I, I, U, U, P)(0x0DE1, 0, 0x8814, 768, 768, 0, 0x1908, 0x1406, None)
    for name, value in [(0x2801, 0x2601), (0x2800, 0x2601), (0x2802, 0x812F), (0x2803, 0x812F)]:
        bind(G, 'glTexParameteri', None, U, U, I)(0x0DE1, name, value)
    bind(G, 'glFramebufferTexture2D', None, U, U, U, U, I)(0x8D40, 0x8CE0, 0x0DE1, texture, 0)
    require(bind(G, 'glCheckFramebufferStatus', U, U)(0x8D40) == 0x8CD5, 'Float target incomplete')
    return fbo, texture


scene_fbo, scene_texture = target()
output_fbo, output_texture = target()
viewport = bind(G, 'glViewport', None, I, I, I, I)
draw = bind(G, 'glDrawArrays', None, U, I, I)
finish = bind(G, 'glFinish', None)
read = bind(G, 'glReadPixels', None, I, I, I, I, U, U, P)
bind_fbo = bind(G, 'glBindFramebuffer', None, U, U)
scene_format = 0x8814  # RGBA32F finite-output diagnostics
last_composite_ms = 0.0
quality = {'LOW': (.04, 192, 128), 'BALANCED': (.025, 320, 256), 'HIGH': (.016, 512, 384)}


def pixels(w, h):
    floating = (F * (w*h*4))()
    read(0, 0, w, h, 0x1908, 0x1406, floating)
    require(all(math.isfinite(value) for value in floating), 'Non-finite output')
    data = (C.c_ubyte * (w*h*4))()
    read(0, 0, w, h, 0x1908, 0x1401, data)
    return Image.frombytes('RGBA', (w, h), bytes(data))


def render(w=512, h=256, phase=0, size=1, tilt=.08, inclination=.25, outer=18,
           brightness=1, outline=0, glow=1.2, opacity=1, base_alpha=1, ink_alpha=1, tier='BALANCED'):
    global last_composite_ms
    step, steps, cap = quality[tier]
    ratio = min(1, cap/max(w, h))
    rw, rh = max(1, round(w*ratio)), max(1, round(h*ratio))
    # Match the production reusable 384px capacity and viewport crop.
    bind(G, 'glBindTexture', None, U, U)(0x0DE1, scene_texture)
    bind(G, 'glTexImage2D', None, U, I, I, I, I, I, U, U, P)(0x0DE1, 0, scene_format, 384, 384, 0, 0x1908, 0x1406, None)
    bind_fbo(0x8D40, scene_fbo)
    viewport(0, 0, rw, rh)
    use(trace_program); attributes(trace_program)
    v4(location(trace_program, 'Surface'), w/h, size, phase, tilt)
    v4(location(trace_program, 'Physics'), inclination, outer, brightness, outline)
    v2(location(trace_program, 'Integration'), step, steps)
    v4(location(trace_program, 'InkColor'), 245/255, 245/255, 245/255, ink_alpha)
    v4(location(trace_program, 'BackgroundColor'), 8/255, 8/255, 8/255, base_alpha)
    start = time.perf_counter(); draw(4, 0, 6); finish()
    trace_ms = (time.perf_counter()-start)*1000
    pixels(rw, rh)  # finite-check the numerical trace itself
    bind_fbo(0x8D40, output_fbo); viewport(0, 0, w, h)
    use(composite_program); attributes(composite_program)
    mat(location(composite_program, 'ModelViewMat'), 1, 0, identity)
    mat(location(composite_program, 'ProjMat'), 1, 0, identity)
    v4(location(composite_program, 'ColorModulator'), 1, 1, 1, 1)
    v4(location(composite_program, 'Finish'), 1/rw, 1/rh, glow if brightness > 0 else 0, opacity)
    v1(location(composite_program, 'Scene'), 0)
    v2(location(composite_program, 'ContentScale'), rw/384, rh/384)
    bind(G, 'glBindTexture', None, U, U)(0x0DE1, scene_texture)
    start = time.perf_counter(); draw(4, 0, 6); finish()
    last_composite_ms = (time.perf_counter()-start)*1000
    return pixels(w, h), trace_ms


cases = [('default', {}), ('phase2', {'phase': 2}), ('portrait', {'w': 256, 'h': 512}),
         ('wide', {'w': 512, 'h': 128}), ('odd', {'w': 257, 'h': 129}), ('narrow', {'w': 1, 'h': 257}),
         ('low', {'tier': 'LOW'}), ('high', {'tier': 'HIGH'}),
         ('face-on', {'inclination': 1.4}), ('edge-on', {'inclination': .1}),
         ('outer-min', {'outer': 6.5}), ('outer-max', {'outer': 24}),
         ('glow-zero', {'glow': 0}), ('glow-heavy', {'glow': 3}), ('outline', {'outline': 1}),
         ('size-min', {'size': .5}), ('size-max', {'size': 1.8}),
         ('roll-min', {'tilt': -.7}), ('roll-max', {'tilt': .7}),
         ('brightness-max', {'brightness': 2}),
         ('combined-extreme', {'size': 1.8, 'tilt': .7, 'inclination': .1, 'outer': 24, 'glow': 3, 'outline': 1, 'brightness': 2})]
report['trace_timing_ms'] = {}
for name, parameters in cases:
    image, elapsed = render(**parameters)
    image.save(args.output / (name+'.png'))
    report['trace_timing_ms'][name] = elapsed
    a, _ = render(**dict(parameters, phase=0))
    b, _ = render(**dict(parameters, phase=math.tau))
    delta = max(abs(x-y) for x, y in zip(a.tobytes(), b.tobytes()))
    require(delta <= 1, f'Phase discontinuity in {name}: {delta}')
    report['checks'].append(f'{name}: finite float trace/composite, wrap delta {delta}/255')
require(render(opacity=0)[0].getextrema()[3] == (0, 0), 'opacity zero')
require(render(brightness=0)[0].getextrema() == ((8, 8), (8, 8), (8, 8), (255, 255)), 'brightness zero')
transparent = render(base_alpha=0)[0]
transparent.save(args.output / 'transparent.png')
require(transparent.getextrema()[3][0] < 255, 'Transparent background missing')
report['checks'].append('opacity zero, brightness zero, transparent base passed')
error = bind(G, 'glGetError', U)()
require(error == 0, f'GL error {error:#x}')
report['gl_error'] = error
if args.benchmark:
    scene_format = 0x8058  # production RGBA8; float correctness was tested separately above
    report['benchmark'] = {}
    for tier in quality:
        for _ in range(12):
            render(tier=tier)
        traces, composites, combined, cached = [], [], [], []
        for _ in range(20):
            _, elapsed = render(tier=tier)
            traces.append(elapsed)
            composites.append(last_composite_ms)
            combined.append(elapsed + last_composite_ms)
            start = time.perf_counter(); draw(4, 0, 6); finish()
            cached.append((time.perf_counter()-start)*1000)
        summary = lambda values: {'median_ms': statistics.median(values), 'p95_ms': sorted(values)[18]}
        report['benchmark'][tier] = {'trace': summary(traces), 'composite': summary(composites),
                                     'trace_plus_composite': summary(combined), 'cached_composite_only': summary(cached)}
    report['benchmark_scope'] = 'RGBA8 scene, 512x256 output; 12 warmups/20 samples; each draw includes glFinish; excludes allocation, uniforms and readback; software GL only'
report['timing_scope'] = 'software EGL RGBA32F numerical trace only, draw+glFinish; excludes target allocation/bloom; not Minecraft/hardware FPS'
(args.output / 'results.json').write_text(json.dumps(report, indent=2)+'\n')
for fbo in [scene_fbo, output_fbo]:
    bind(G, 'glDeleteFramebuffers', None, I, C.POINTER(U))(1, C.byref(fbo))
for texture in [scene_texture, output_texture]:
    bind(G, 'glDeleteTextures', None, I, C.POINTER(U))(1, C.byref(texture))
for prog in [trace_program, composite_program]:
    bind(G, 'glDeleteProgram', None, U)(prog)
bind(G, 'glDeleteBuffers', None, I, C.POINTER(U))(1, C.byref(vbo))
bind(G, 'glDeleteVertexArrays', None, I, C.POINTER(U))(1, C.byref(vao))
make_current(display, None, None, None)
bind(E, 'eglDestroyContext', U, P, P)(display, context)
bind(E, 'eglDestroySurface', U, P, P)(display, surface)
bind(E, 'eglTerminate', U, P)(display)
print(json.dumps(report, indent=2))
