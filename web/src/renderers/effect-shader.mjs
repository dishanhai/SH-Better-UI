// Author: dishanhai
export const VERTEX = `#version 300 es
precision highp float;
out vec2 uv;
void main() {
  vec2 corner = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1));
  uv = vec2(corner.x, 1.0 - corner.y);
  gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}`;

export const PRELUDE = `#version 300 es
precision highp float;
in vec2 uv;
out vec4 color;
uniform vec2 sceneSize;
uniform vec2 pointer;
uniform vec2 pressOrigin;
uniform vec2 releasePoint;
uniform float pressed;
uniform float sincePress;
uniform float sinceRelease;
uniform float manual;
uniform float time;
uniform float zoom;
uniform float contrast;
uniform sampler2D glyphAtlas;
uniform sampler2D fixtureText;
uniform float glyphCount;
const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const vec3 INK = vec3(.035,.049,.076);
const vec3 PANEL = vec3(.087,.115,.176);
const vec3 LINE = vec3(.23,.32,.46);
const vec3 TEXT = vec3(.76,.85,.96);
const vec3 BLUE = vec3(.26,.66,1.);
const vec3 MINT = vec3(.29,.92,.73);
const vec3 VIOLET = vec3(.65,.41,1.);
float sat(float x) { return clamp(x, 0., 1.); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
vec2 rotate2(vec2 p, float a) { float c=cos(a), s=sin(a); return mat2(c,s,-s,c)*p; }
float box(vec2 p, vec2 center, vec2 halfSize, float radius) {
  vec2 q = abs(p - center) - max(vec2(0.), halfSize - radius);
  return length(max(q,0.)) + min(max(q.x,q.y),0.) - radius;
}
float cover(float d) { return 1. - smoothstep(-.55,.55,d); }
float ease(float v) { return 1. - pow(1. - sat(v),3.); }
float cycle(float period) { return mod(time,max(.001,period)); }
float progress(float duration) { return sat(cycle(duration+.75) / max(.001,duration)); }
float seconds(float ms) { return max(.01,ms * .001); }
vec3 fill(vec3 under, vec3 over, float d) { return mix(under, over, cover(d)); }
vec3 overlay(vec3 under, vec4 over, float mask) { return mix(under, over.rgb, sat(over.a * mask)); }
float glow(float d, float width) { return exp(-max(d,0.) / max(.15,width)); }
float ring(vec2 p, vec2 center, float radius, float width) { return cover(abs(length(p-center)-radius)-width); }
float segment(vec2 p, vec2 a, vec2 b) {
  vec2 v = b-a;
  return length(p-a-v*sat(dot(p-a,v)/max(dot(v,v),.001)));
}
vec2 center() { return sceneSize * .5; }
vec2 at(float x, float y) { return sceneSize * vec2(x,y); }
vec2 point() { return pointer * sceneSize; }
vec2 origin() { return pressOrigin * sceneSize; }
vec2 pixel() { return center() + (uv-.5) * sceneSize * zoom; }
vec3 stage(vec2 p) {
  vec2 cell = abs(fract(p/24.)-.5);
  float dots = cover(length(cell)*24.-.75);
  float light = glow(length((p-center())*vec2(.55,1.)),150.);
  return INK + vec3(.04,.055,.09)*light + dots * .021;
}
vec3 scene(vec2 p) {
  vec3 c = mix(vec3(.095,.145,.28),vec3(.49,.24,.26),sat(p.x/sceneSize.x));
  float checks = mod(floor(p.x/20.) + floor(p.y/20.),2.);
  c += checks * .08;
  c = fill(c,vec3(.88,.55,.25),length(p-at(.2,.4))-69.);
  c = fill(c,vec3(.15,.69,.67),box(p,at(.75,.56),vec2(106,61),18.));
  c = fill(c,vec3(.86,.89,.92),box(p,at(.52+.12*sin(time*1.3),.63),vec2(49,24),5.));
  float stripe = abs(fract((p.x+p.y*.42)/16.)-.5)*16.;
  c = fill(c,c*.55,stripe-1.4);
  return c;
}
vec3 blurScene(vec2 p, float radius, vec2 axis) {
  if (radius <= .05) return scene(p);
  vec3 total = scene(p) * .227027;
  vec2 step1 = axis * radius * .26;
  total += (scene(p-step1*1.384615)+scene(p+step1*1.384615))*.316216;
  total += (scene(p-step1*3.230769)+scene(p+step1*3.230769))*.070270;
  return total;
}
vec3 softScene(vec2 p, float radius) {
  return .5 * (blurScene(p,radius,vec2(1.,0.)) + blurScene(p,radius,vec2(0.,1.)));
}
vec3 gaussianPass(vec2 p, float radius, vec2 axis, bool dense) {
  vec3 sum = scene(p);
  float weight = 1.;
  for (int i=1; i<=6; i++) {
    if (!dense && i>3) break;
    float x = float(i) / (dense ? 6. : 3.);
    float w = exp(-x*x*3.2);
    vec2 delta = axis * (x*radius);
    sum += w*(scene(p+delta)+scene(p-delta));
    weight += 2.*w;
  }
  return sum / weight;
}
float glyph(vec2 g, float idx) {
  if (min(g.x,g.y)<0. || max(g.x,g.y)>1.) return 0.;
  return texture(glyphAtlas,vec2((floor(mod(idx,max(glyphCount,1.)))+g.x)/max(glyphCount,1.),g.y)).a;
}
float label(vec2 p, vec2 mid, vec2 size, float row) {
  vec2 t=(p-mid)/max(size,vec2(1.))+.5;
  if (min(t.x,t.y)<0. || max(t.x,t.y)>1.) return 0.;
  return texture(fixtureText,vec2(t.x,(row+t.y)/4.)).a;
}
vec3 panel(vec3 c, vec2 p, vec2 mid, vec2 halfSize, float radius, vec3 tint) {
  float d=box(p,mid,halfSize,radius);
  c=mix(c,vec3(.016,.022,.038),.66*glow(d,12.));
  c=fill(c,LINE,d);
  return fill(c,tint,d+1.);
}
vec3 lines(vec3 c, vec2 p, vec2 start, float width, vec3 tint) {
  for(int i=0;i<3;i++) {
    float x=float(i);
    float w=width*(1.-x*.23);
    c=fill(c,tint,box(p,start+vec2(w*.5,x*17.),vec2(w*.5,2.),2.));
  }
  return c;
}
vec3 page(vec3 c, vec2 p, vec2 mid, vec2 halfSize, vec3 accent) {
  float d=box(p,mid,halfSize,11.);
  vec3 body=mix(PANEL,accent*.42,.32);
  body=fill(body,accent*.54,box(p,mid+vec2(0.,-halfSize.y+26.),vec2(halfSize.x-12.,13.),5.));
  body=lines(body,p,mid-halfSize+vec2(14.,69.),halfSize.x*.68,TEXT*.52);
  vec2 image=mid+vec2(halfSize.x*.39,11.);
  body=fill(body,accent*.68,box(p,image,vec2(halfSize.x*.42,33.),8.));
  body=fill(body,accent,abs(p.y-image.y+sin((p.x-image.x)*.09)*11.)-1.5+
    max(0.,abs(p.x-image.x)-halfSize.x*.37)*4.);
  return mix(c,body,cover(d));
}
float icon(vec2 p) {
  float face=box(p,vec2(0.,6.),vec2(21.,20.),5.);
  float handle=max(abs(length(p-vec2(0.,-13.))-10.)-2.2,p.y+12.);
  return min(face,handle);
}
float introAmount(bool enabled, float duration) { return enabled ? mix(.33,1.,ease(time/max(.05,duration))) : 1.; }
`;
