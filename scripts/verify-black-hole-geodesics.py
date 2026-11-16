#!/usr/bin/env python3
"""Execute production GLSL RK4 in Mesa OpenGL and compare with Java double oracle.

Requires Python 3, libEGL/libGL (surfaceless Mesa), JDK 17+ (JAVA_HOME or PATH).
Run: python scripts/verify-black-hole-geodesics.py --output /tmp/bh-geodesics
This validates actual shader arithmetic on software OpenGL, not physical hardware,
Minecraft, the complete disk/composite renderer, or an unrestricted orbit budget.
"""
import argparse
import ctypes as C
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
root = Path(__file__).resolve().parents[1]
shader_path = root / 'src/main/resources/assets/sh_better_ui/shaders/core/black_hole_backdrop.fsh'
source = shader_path.read_text()


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def extract_function(name):
    # Balanced braces: fail on missing/renamed production functions rather than silently
    # executing a separately maintained mathematical implementation.
    match = re.search(r'\bvec2\s+' + name + r'\s*\([^)]*\)\s*\{', source)
    require(match, 'Production function missing: '+name)
    end, depth = match.end(), 1
    while depth and end < len(source):
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    require(depth == 0, 'Unbalanced GLSL function')
    return source[match.start():end]


constants = re.findall(r'const float (?:CAMERA|OBSERVER_F)\s*=\s*[^;]+;', source)
require(len(constants) == 2, 'Finite-camera constants missing')
initializer = re.search(r'vec2 state\s*=\s*vec2\(1\.0 / CAMERA,[^;]+;', source)
require(initializer, 'Production finite-camera initializer changed; update diagnostic adapter')
horizon = re.search(r'if \(next\.x >= ([\d.]+)\)', source)
escape = re.search(r'if \(next\.x <= ([\d.]+)\)', source)
require(horizon and escape, 'Production termination bounds missing')
functions = '\n'.join(extract_function(name) for name in ('derivative', 'rk4'))
# Only the diagnostic adapter is new. RK4, derivative, constants, initial state and
# termination bounds above come verbatim from the production shader on this run.
fragment = '''#version 150
uniform float Impact;
uniform float StepSize;
uniform int StepCount;
out vec4 fragColor;
''' + '\n'.join(constants) + '\n' + functions + '''
void main() {
    float impact = Impact;
    float sineAlpha = impact * sqrt(OBSERVER_F) / CAMERA;
    float cosineAlpha = sqrt(1.0 - sineAlpha*sineAlpha);
    ''' + initializer.group() + '''
    float phi = 0.0;
    float fate = 0.0;
    for (int i=0; i<512; i++) {
        if (i >= StepCount) break;
        vec2 next = rk4(state, StepSize);
        state = next;
        phi += StepSize;
        if (next.x >= ''' + horizon.group(1) + ''') { fate=1.0; break; }
        if (next.x <= ''' + escape.group(1) + ''') { fate=2.0; break; }
    }
    fragColor = vec4(state, phi, fate);
}
'''
(args.output / 'diagnostic.fsh').write_text(fragment)
os.environ['LIBGL_ALWAYS_SOFTWARE'] = '1'
os.environ['MESA_SHADER_CACHE_DISABLE'] = 'true'
E, G = C.CDLL('libEGL.so.1'), C.CDLL('libGL.so.1')
P, I, U, F = C.c_void_p, C.c_int, C.c_uint, C.c_float


def bind(lib, name, result, *arguments):
    f = getattr(lib, name)
    f.restype, f.argtypes = result, arguments
    return f


address = bind(E, 'eglGetProcAddress', P, C.c_char_p)(b'eglGetPlatformDisplayEXT')
require(address, 'Missing EGL platform extension')
display = C.CFUNCTYPE(P,U,P,C.POINTER(I))(address)(0x31DD,None,None)
major, minor = I(), I()
require(bind(E,'eglInitialize',U,P,C.POINTER(I),C.POINTER(I))(display,C.byref(major),C.byref(minor)), 'EGL initialize failed')
require(bind(E,'eglBindAPI',U,U)(0x30A2), 'OpenGL API unavailable')
a = (I*13)(0x3033,1,0x3040,8,0x3024,8,0x3021,8,0x3025,0,0x3026,0,0x3038)
config, count = P(), I()
require(bind(E,'eglChooseConfig',U,P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I))(display,a,C.byref(config),1,C.byref(count)) and count.value, 'No config')
a = (I*7)(0x3098,3,0x30FB,2,0x30FD,1,0x3038)
context = bind(E,'eglCreateContext',P,P,P,P,C.POINTER(I))(display,config,None,a)
a = (I*5)(0x3057,1,0x3056,1,0x3038)
surface = bind(E,'eglCreatePbufferSurface',P,P,P,C.POINTER(I))(display,config,a)
require(context and surface, 'Context/surface creation failed')
require(bind(E,'eglMakeCurrent',U,P,P,P,P)(display,surface,surface,context), 'Context bind failed')
program = bind(G,'glCreateProgram',U)()
vertex = '#version 150\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}'
for kind, text in ((0x8B31,vertex),(0x8B30,fragment)):
    shader=bind(G,'glCreateShader',U,U)(kind)
    code=C.c_char_p(text.encode())
    bind(G,'glShaderSource',None,U,I,C.POINTER(C.c_char_p),C.POINTER(I))(shader,1,C.byref(code),None)
    bind(G,'glCompileShader',None,U)(shader)
    status, log=I(),C.create_string_buffer(8192)
    bind(G,'glGetShaderiv',None,U,U,C.POINTER(I))(shader,0x8B81,C.byref(status))
    bind(G,'glGetShaderInfoLog',None,U,I,C.POINTER(I),C.c_char_p)(shader,len(log),None,log)
    require(status.value,log.value.decode())
    bind(G,'glAttachShader',None,U,U)(program,shader)
    bind(G,'glDeleteShader',None,U)(shader)
bind(G,'glLinkProgram',None,U)(program)
status=I()
bind(G,'glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8B82,C.byref(status))
require(status.value,'Diagnostic link failed')
bind(G,'glUseProgram',None,U)(program)
vao,fbo,texture=U(),U(),U()
bind(G,'glGenVertexArrays',None,I,C.POINTER(U))(1,C.byref(vao))
bind(G,'glBindVertexArray',None,U)(vao)
bind(G,'glGenFramebuffers',None,I,C.POINTER(U))(1,C.byref(fbo))
bind(G,'glBindFramebuffer',None,U,U)(0x8D40,fbo)
bind(G,'glGenTextures',None,I,C.POINTER(U))(1,C.byref(texture))
bind(G,'glBindTexture',None,U,U)(0x0DE1,texture)
bind(G,'glTexImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x8814,1,1,0,0x1908,0x1406,None)
bind(G,'glFramebufferTexture2D',None,U,U,U,U,I)(0x8D40,0x8CE0,0x0DE1,texture,0)
require(bind(G,'glCheckFramebufferStatus',U,U)(0x8D40)==0x8CD5,'RGBA32F FBO incomplete')
bind(G,'glViewport',None,I,I,I,I)(0,0,1,1)
loc=lambda n: bind(G,'glGetUniformLocation',I,U,C.c_char_p)(program,n.encode())
impact_loc,step_loc,count_loc=loc('Impact'),loc('StepSize'),loc('StepCount')


def gpu(b,h,n):
    bind(G,'glUniform1f',None,I,F)(impact_loc,b)
    bind(G,'glUniform1f',None,I,F)(step_loc,h)
    bind(G,'glUniform1i',None,I,I)(count_loc,n)
    bind(G,'glDrawArrays',None,U,I,I)(4,0,3)
    values=(F*4)()
    bind(G,'glReadPixels',None,I,I,I,I,U,U,P)(0,0,1,1,0x1908,0x1406,values)
    require(bind(G,'glGetError',U)()==0,'GL error')
    require(all(math.isfinite(v) for v in values),'Non-finite diagnostic result')
    return list(values)


def java_tool(name):
    home=os.environ.get('JAVA_HOME')
    return str(Path(home)/'bin'/name) if home else shutil.which(name)


report={'renderer':bind(G,'glGetString',C.c_char_p,U)(0x1F01).decode(),
        'shader_sha256':hashlib.sha256(source.encode()).hexdigest(),
        'scope':'production RK4/derivative/finite-camera initializer on software OpenGL vs independent Java double oracle; not complete rendering or physical GPU',
        'samples':[]}
critical=3*math.sqrt(3)
with tempfile.TemporaryDirectory(prefix='shbui-geodesics-') as temporary:
    oracle=root/'src/test/java/com/dishanhai/sh_better_ui/client/gui/scaled/BlackHoleGeodesicReference.java'
    subprocess.run([java_tool('javac'),'--release','17','-d',temporary,str(oracle)],check=True)
    for tier,h,budget in [('LOW',.04,192),('BALANCED',.025,320),('HIGH',.016,512)]:
        for b in [.1,3,5,5.15,critical-.001,critical+.001,5.3,6,8,12]:
            raw=subprocess.check_output([java_tool('java'),'-cp',temporary,
                'com.dishanhai.sh_better_ui.client.gui.scaled.BlackHoleGeodesicReference',
                '30',str(b),str(h),str(budget)],text=True)
            rows=[[float(v) for v in row.split(',')] for row in raw.splitlines()[1:]]
            terminal=next((int(r[0]) for r in rows if r[2]>=.5 or r[2]<=0),budget)
            # Initial state, early state, middle and terminal/budget checks test the
            # invariant throughout sampled paths, not just matching a final image.
            for n in sorted({0,1,min(25,terminal),terminal//2,terminal}):
                expected=rows[n]
                actual=gpu(b,h,n)
                fate=1 if expected[2]>=.5 else 2 if expected[2]<=0 else 0
                require(round(actual[3])==fate,f'Fate mismatch {tier} b={b} n={n}')
                errors=[abs(actual[0]-expected[2]),abs(actual[1]-expected[3]),abs(actual[2]-expected[1])]
                require(errors[0]<2e-4 and errors[1]<2e-4 and errors[2]<1e-4,
                        f'CPU/GPU state mismatch {tier} b={b} n={n}: {errors}')
                energy=actual[1]**2+actual[0]**2-2*actual[0]**3
                relative_energy=abs(energy-1/(b*b))/(1/(b*b))
                require(relative_energy<5e-5,f'Null invariant failed {tier} b={b} n={n}: {relative_energy}')
                report['samples'].append({'quality':tier,'impact':b,'steps':n,'gpu':actual,
                    'cpu':[expected[2],expected[3],expected[1],fate],
                    'absolute_errors':errors,'relative_energy_error':relative_energy})
            if abs(b-critical)<.002:
                require(terminal==budget and fate==0,'Near-critical budget must remain unresolved')
report['sample_count']=len(report['samples'])
report['max_absolute_errors']=[max(s['absolute_errors'][i] for s in report['samples']) for i in range(3)]
report['max_relative_energy_error']=max(s['relative_energy_error'] for s in report['samples'])
report['passed']=True
(args.output/'geodesics-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='samples'},indent=2))
# The command owns its context; process teardown also releases resources on failure.
bind(E,'eglMakeCurrent',U,P,P,P,P)(display,None,None,None)
bind(E,'eglDestroySurface',U,P,P)(display,surface)
bind(E,'eglDestroyContext',U,P,P)(display,context)
bind(E,'eglTerminate',U,P)(display)
