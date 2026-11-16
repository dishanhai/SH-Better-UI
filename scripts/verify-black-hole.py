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
create_shader = bind(G, 'glCreateShader', U, U)
shader_source = bind(G, 'glShaderSource', None, U, I, C.POINTER(C.c_char_p), C.POINTER(I))
compile_shader = bind(G, 'glCompileShader', None, U)
shader_status = bind(G, 'glGetShaderiv', None, U, U, C.POINTER(I))
shader_log = bind(G, 'glGetShaderInfoLog', None, U, I, C.POINTER(I), C.c_char_p)
program = bind(G, 'glCreateProgram', U)()
for extension, kind in [('vsh', 0x8B31), ('fsh', 0x8B30)]:
    shader = create_shader(kind)
    code = C.c_char_p((shader_root / f'black_hole_backdrop.{extension}').read_bytes())
    shader_source(shader, 1, C.byref(code), None)
    compile_shader(shader)
    status, log = I(), C.create_string_buffer(8192)
    shader_status(shader, 0x8B81, C.byref(status))
    shader_log(shader, len(log), None, log)
    require(status.value, f'{extension} compile failed: {log.value.decode()}')
    bind(G, 'glAttachShader', None, U, U)(program, shader)
    bind(G, 'glDeleteShader', None, U)(shader)
    report['checks'].append(f'{extension} compile passed')
bind(G, 'glLinkProgram', None, U)(program)
status, log = I(), C.create_string_buffer(8192)
bind(G, 'glGetProgramiv', None, U, U, C.POINTER(I))(program, 0x8B82, C.byref(status))
bind(G, 'glGetProgramInfoLog', None, U, I, C.POINTER(I), C.c_char_p)(program, len(log), None, log)
require(status.value, f'Link failed: {log.value.decode()}')
report['checks'].append('program link passed')
bind(G, 'glUseProgram', None, U)(program)
get_uniform = bind(G, 'glGetUniformLocation', I, U, C.c_char_p)
uniforms = {name: get_uniform(program, name.encode()) for name in
            ['ModelViewMat', 'ProjMat', 'Surface', 'Disk', 'Opacity', 'InkColor', 'BackgroundColor', 'ColorModulator']}
require(all(location >= 0 for location in uniforms.values()), 'Missing active uniform')
uniform_matrix = bind(G, 'glUniformMatrix4fv', None, I, I, U, C.POINTER(F))
uniform4 = bind(G, 'glUniform4f', None, I, F, F, F, F)
uniform1 = bind(G, 'glUniform1f', None, I, F)
identity = (F * 16)(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)
uniform_matrix(uniforms['ModelViewMat'], 1, 0, identity)
uniform4(uniforms['ColorModulator'], 1, 1, 1, 1)
vao, vbo = U(), U()
bind(G, 'glGenVertexArrays', None, I, C.POINTER(U))(1, C.byref(vao))
bind(G, 'glBindVertexArray', None, U)(vao)
bind(G, 'glGenBuffers', None, I, C.POINTER(U))(1, C.byref(vbo))
bind(G, 'glBindBuffer', None, U, U)(0x8892, vbo)
buffer_data = bind(G, 'glBufferData', None, U, C.c_ssize_t, P, U)
for name, size, offset in [('Position', 3, 0), ('UV0', 2, 12)]:
    location = bind(G, 'glGetAttribLocation', I, U, C.c_char_p)(program, name.encode())
    require(location >= 0, f'Missing attribute {name}')
    bind(G, 'glEnableVertexAttribArray', None, U)(location)
    bind(G, 'glVertexAttribPointer', None, U, I, U, U, I, P)(location, size, 0x1406, 0, 20, P(offset))
viewport = bind(G, 'glViewport', None, I, I, I, I)
clear = bind(G, 'glClear', None, U)
draw = bind(G, 'glDrawArrays', None, U, I, I)
read = bind(G, 'glReadPixels', None, I, I, I, I, U, U, P)
finish = bind(G, 'glFinish', None)
bind(G, 'glClearColor', None, F, F, F, F)(0, 0, 0, 0)


def render(w=720, h=360, phase=0, opacity=1, base_alpha=1, size=1, tilt=.08,
           width=.16, lens=1, brightness=1, glow=.55, ink_alpha=1):
    projection = (F * 16)(2/w, 0, 0, 0, 0, -2/h, 0, 0, 0, 0, 1, 0, -1, 1, 0, 1)
    uniform_matrix(uniforms['ProjMat'], 1, 0, projection)
    viewport(0, 0, w, h)
    vertices = []
    for x, y in [(0, 0), (0, 1), (1, 1), (0, 0), (1, 1), (1, 0)]:
        vertices.extend([x*w, y*h, 0, x, y])
    data = (F * len(vertices))(*vertices)
    buffer_data(0x8892, C.sizeof(data), data, 0x88E4)
    uniform4(uniforms['Surface'], w/h, size, phase, tilt)
    uniform4(uniforms['Disk'], width, lens, brightness, glow)
    uniform1(uniforms['Opacity'], opacity)
    uniform4(uniforms['InkColor'], 245/255, 245/255, 245/255, ink_alpha)
    uniform4(uniforms['BackgroundColor'], 8/255, 8/255, 8/255, base_alpha)
    clear(0x4000)
    draw(4, 0, 6)
    floats = (F * (w*h*4))()
    read(0, 0, w, h, 0x1908, 0x1406, floats)
    require(all(math.isfinite(value) for value in floats), 'Non-finite shader output')
    pixels = (C.c_ubyte * (w*h*4))()
    read(0, 0, w, h, 0x1908, 0x1401, pixels)
    return Image.frombytes('RGBA', (w, h), bytes(pixels)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)


# A float test target detects NaN/Inf before conversion into PNG's UNORM pixels.
framebuffer, color_texture = U(), U()
bind(G, 'glGenFramebuffers', None, I, C.POINTER(U))(1, C.byref(framebuffer))
bind(G, 'glBindFramebuffer', None, U, U)(0x8D40, framebuffer)
bind(G, 'glGenTextures', None, I, C.POINTER(U))(1, C.byref(color_texture))
bind(G, 'glBindTexture', None, U, U)(0x0DE1, color_texture)
bind(G, 'glTexImage2D', None, U, I, I, I, I, I, U, U, P)(
    0x0DE1, 0, 0x8814, 768, 768, 0, 0x1908, 0x1406, None)  # RGBA32F
bind(G, 'glFramebufferTexture2D', None, U, U, U, U, I)(
    0x8D40, 0x8CE0, 0x0DE1, color_texture, 0)
require(bind(G, 'glCheckFramebufferStatus', U, U)(0x8D40) == 0x8CD5, 'Float FBO incomplete')


cases = [('default', {}), ('phase2', {'phase': 2}), ('portrait', {'w': 360, 'h': 720}),
         ('wide', {'w': 720, 'h': 180}), ('square', {'w': 512, 'h': 512}), ('tiny', {'w': 64, 'h': 32}),
         ('odd-center', {'w': 721, 'h': 361}), ('narrow', {'w': 180, 'h': 720}),
         ('one-pixel-wide', {'w': 1, 'h': 257})]
for key, low, high in [('size', .5, 1.8), ('tilt', -.7, .7), ('width', .05, .35),
                       ('lens', 0, 1.5), ('brightness', 0, 2), ('glow', 0, 1), ('opacity', 0, 1)]:
    cases.extend([(key+'-min', {key: low}), (key+'-max', {key: high})])
cases.append(('combined-max', {'size': 1.8, 'tilt': .7, 'width': .35, 'lens': 1.5, 'brightness': 2, 'glow': 1}))
for name, parameters in cases:
    image = render(**parameters)
    image.save(args.output / (name+'.png'))
    zero = dict(parameters, phase=0)
    full = dict(parameters, phase=math.tau)
    delta = max(abs(a-b) for a, b in zip(render(**zero).tobytes(), render(**full).tobytes()))
    require(delta <= 1, f'Phase wrapping discontinuity in {name}: {delta}/255')
    report['checks'].append(f'{name}: render and 2pi wrap passed, max delta {delta}/255')
require(render(w=721, h=361).getpixel((360, 180)) == (0, 0, 0, 255),
        'Exact center in an odd-sized viewport must be opaque black')
require(render(opacity=0).getextrema()[3] == (0, 0), 'Zero opacity must be transparent')
require(render(opacity=.5).getextrema()[3] == (128, 128), 'Half opacity must halve alpha')
require(render(brightness=0).getextrema() == ((8, 8), (8, 8), (8, 8), (255, 255)), 'Zero brightness must leave only base')
transparent = render(base_alpha=0)
require(transparent.getextrema()[3][0] < 10 and transparent.getextrema()[3][1] == 255,
        'Transparent base must preserve both empty pixels and the opaque black shadow')
transparent.save(args.output / 'transparent-base.png')
require(render(base_alpha=0, ink_alpha=0).getpixel((0, 0))[3] == 0, 'Transparent colors must not add visible glow')
report['checks'].append('RGBA32F outputs finite in all renders; opacity, base-only brightness and transparent colors passed')
# Shader-only synchronous software GL measurements, excluding upload and readback.
report['timings'] = {}
for label, parameters in [('default', {}), ('no-glow', {'glow': 0}), ('brightness-zero', {'brightness': 0})]:
    render(**parameters)
    for _ in range(12):
        draw(4, 0, 6)
    finish()
    samples = []
    for _ in range(30):
        started = time.perf_counter()
        draw(4, 0, 6)
        finish()
        samples.append((time.perf_counter() - started) * 1000)
    report['timings'][label] = {'median_ms': statistics.median(samples), 'p95_ms': sorted(samples)[28]}
error = bind(G, 'glGetError', U)()
require(error == 0, f'OpenGL error: {error:#x}')
report['gl_error'] = error
report['timing_scope'] = '720x360, 30 draw+glFinish samples after 12 warmups; software EGL, not Minecraft/real GPU'
(args.output / 'results.json').write_text(json.dumps(report, indent=2)+'\n')
preview = Image.new('RGB', (720, 780), (8, 8, 8))
labels = ImageDraw.Draw(preview)
for index, name in enumerate(['default', 'phase2']):
    labels.text((12, index*390+9), name, fill='white')
    preview.paste(Image.open(args.output / (name+'.png')), (0, index*390+30))
preview.save(args.output / 'contact.png')
bind(G, 'glDeleteFramebuffers', None, I, C.POINTER(U))(1, C.byref(framebuffer))
bind(G, 'glDeleteTextures', None, I, C.POINTER(U))(1, C.byref(color_texture))
bind(G, 'glDeleteProgram', None, U)(program)
bind(G, 'glDeleteBuffers', None, I, C.POINTER(U))(1, C.byref(vbo))
bind(G, 'glDeleteVertexArrays', None, I, C.POINTER(U))(1, C.byref(vao))
make_current(display, None, None, None)
bind(E, 'eglDestroyContext', U, P, P)(display, context)
bind(E, 'eglDestroySurface', U, P, P)(display, surface)
bind(E, 'eglTerminate', U, P)(display)
print(json.dumps(report, indent=2))
