// Author: dishanhai
const argb = (value, fallback) => {
  const hex = typeof value === 'string' ? parseInt(value.replace(/^#/, ''),16) : value;
  return Number.isFinite(hex) ? [hex >> 16 & 255, hex >> 8 & 255, hex & 255, hex >>> 24].map(v => v/255) : fallback;
};
const gradient = (colors, index) => argb(colors?.[Math.min(index,colors.length-1)], [.54,.34,1,1]);
const stops = gradient => Array.isArray(gradient?.stops) ? gradient.stops : [];
const shapeText = p => typeof p.textStyle?.text === 'string' && p.textStyle.text.length ? p.textStyle : null;
const GLOW_WAVE = /* glsl */ `
  float pulseRing(vec2 p,vec2 mid,float age,float reach) {
    if(age>1.3) return 0.;
    return exp(-pow((length(p-mid)-age*reach)/max(3.,reach*.1),2.))*exp(-age*3.);
  }
`;

const code = {
  helpers: GLOW_WAVE,
  glyphs: p => p.glyphs?.characters,
  body: /* glsl */ `
    float cell=max(4.,p_cellSize);
    vec2 grid=p/cell, id=floor(grid), pos=fract(grid);
    float key=p_seed*.013;
    float column=hash(vec2(id.x,key));
    float tick=time*p_speed*(.72+column*.65);
    float rows=ceil(sceneSize.y/cell);
    float span=rows+p_trailLength;
    float head=mod(column*span+tick,span)-p_trailLength*.35;
    float trail=mod(head-id.y+span,span);
    float onRain=pow(sat(1.-trail/max(1.,p_trailLength)),1.35);
    float random=hash(id+key);
    float onAscii=(.38+.57*sin(random*TAU+tick*.62)*sin(id.x*.24+id.y*.36+tick*.13))*
      smoothstep(0.,.14,min(p.y/sceneSize.y,1.-p.y/sceneSize.y));
    float mode=step(.5,p_mode);
    float strength=mix(onRain,onAscii,mode)*step(random,p_density);
    float mouse=glow(length(p-point()),35.)*.45;
    float wave=pulseRing(p,origin(),sincePress,185.);
    float change=floor(tick*.69)+floor(mouse*13.+wave*19.);
    float symbol=floor(hash(id+vec2(change*.097,key))*max(1.,glyphCount));
    float g=glyph(pos,symbol);
    vec3 ink=p_color.rgb;
    ink=mix(ink,vec3(.91,1.,.93),(1.-mode)*step(.83,onRain)*.88);
    return mix(vec3(.016,.03,.036),ink,sat((strength+mouse+wave*.74)*g*p_color.a));
  `,
};

export const fieldEffects = {
  code_rain: code,
  ascii_field: code,
  dot_matrix: {
    extra: {
      lens: ['vec4', p => [p.pointerLens?.radius,p.pointerLens?.bulgeStrength,p.pointerLens?.twistDegrees,p.pointerLens?.glowRadius]],
      lensGlow: ['vec4', p => argb(p.pointerLens?.glowArgb,[0,0,0,0])],
      lensAmount: ['vec2', p => [p.pointerLens?.glowIntensity,p.pointerLens?.holdToZoom ? 1 : 0]],
      pointerWave: ['vec2', p => [p.pointerWave?.radius,p.pointerWave?.lift]],
      fade: ['vec2', p => [p.fade?.probability,p.fade?.periodMs]],
      gradientInfo: ['vec3', p => [stops(p.gradient).length,p.gradient?.periodMs,
        Math.max(0,['RIGHT','LEFT','DOWN','UP'].indexOf(p.gradient?.flow))]],
      gradientA: ['vec4', p => {
        const first = stops(p.gradient)[0];
        return argb(first?.argb ?? first?.color,[0,0,0,0]);
      }],
      gradientB: ['vec4', p => {
        const last = stops(p.gradient).at(-1);
        return argb(last?.argb ?? last?.color,[0,0,0,0]);
      }],
    },
    helpers: GLOW_WAVE,
    body: /* glsl */ `
      vec2 delta=p-point();
      float distance=length(delta), reach=1.-smoothstep(0.,max(.1,lens.x),distance);
      float lensEnabled=lensAmount.y>.5 ? pressed : 1.;
      vec2 q=point()+rotate2(delta,radians(lens.z)*reach*lensEnabled)/(1.+max(-.8,lens.y)*reach*lensEnabled);
      float pitch=max(2.,p_spacing), period=seconds(p_periodMs);
      vec2 cell=floor(q/pitch), pointIn=fract(q/pitch)*pitch-pitch*.5;
      float seed=p_seed*.0019, k=hash(cell+seed);
      float hover=1.-smoothstep(0.,max(1.,p_hoverRadius),distance);
      float wave=pulseRing(p,origin(),sincePress,max(1.,p_rippleRadius));
      float lift=(1.-smoothstep(0.,max(.01,pointerWave.x),distance))*pointerWave.y;
      float floatChance=min(p_floatProbability,p_maxFloatingDots/max(1.,sceneSize.x*sceneSize.y/(pitch*pitch)));
      pointIn.y+=step(k,floatChance)*p_floatDistance*sin(k*TAU+time*TAU/period)*.39+lift;
      float pulse=p_motion<.5 ? .71 : p_motion<1.5 ? .53+.47*cos(k*TAU+time*TAU/period)
        : .61+.39*sin((cell.x+cell.y)*.47-time*TAU/period);
      float fadeMask=mix(1.,.5+.5*sin(time*TAU/seconds(fade.y)+k*TAU),step(k,fade.x));
      float d=length(pointIn)-max(.4,p_dotSize*.5)*(1.+hover*.34+wave*.5);
      float dotMask=step(k,p_density)*fadeMask;
      vec4 tint=mix(p_dotArgb,p_highlightArgb,sat(pulse*.36+hover*.5+wave));
      if(gradientInfo.x>.5) {
        float axis=gradientInfo.z<.5 ? p.x/sceneSize.x : gradientInfo.z<1.5 ? 1.-p.x/sceneSize.x :
          gradientInfo.z<2.5 ? p.y/sceneSize.y : 1.-p.y/sceneSize.y;
        tint=mix(tint,mix(gradientA,gradientB,.5-.5*cos((axis-time/seconds(gradientInfo.y))*TAU)),.78);
      }
      vec3 bg=overlay(INK,p_backgroundArgb,1.);
      bg=overlay(bg,lensGlow,glow(distance,max(.1,lens.w))*lensAmount.x*step(.1,lens.w));
      return overlay(bg,tint,dotMask*(cover(d)+glow(d,2.7)*p_glow*.4));
    `,
  },
  antigravity: {
    extra: Object.fromEntries(['a','b','c'].map((key,i) => ['stop'+key,['vec4',p=>gradient(p.colors,i)]])),
    helpers: GLOW_WAVE,
    body: /* glsl */ `
      float t=time*p_autoAnimate;
      float pitch=sqrt(sceneSize.x*sceneSize.y/max(1.,p_count));
      vec2 mid=center(), target=point();
      vec2 q=mid+rotate2(p-mid,-t*p_rotationSpeed*.31);
      vec2 v=q-target;
      float radius=length(v), range=max(1.,p_magnetRadius*15.);
      float magnet=exp(-radius/range)*p_interactive;
      q-=normalize(v+vec2(.00001))*magnet*(radius-p_ringRadius*9.)*p_fieldStrength*.041;
      float burst=pulseRing(p,origin(),sincePress,159.)*p_clickBurst;
      q+=normalize(p-origin()+vec2(.00001))*burst*11.;
      q.y+=sin(q.x*.018*max(1.,p_waveCount)-t*p_waveSpeed*TAU)*p_waveAmplitude*6.;
      vec2 id=floor(q/pitch), local=(fract(q/pitch)-.5)*pitch;
      float k=hash(id), z=mix(.5,1.5,k);
      local-=vec2(hash(id+7.)-.5,hash(id-7.)-.5)*pitch*.56;
      local=rotate2(local,k*TAU+t*.25);
      float size=max(.25,p_particleSize)*mix(1.,z,min(1.,p_particleVariance))*mix(1.,z,sat(p_depthFactor));
      float d=p_particleShape<.5 ? segment(local,vec2(-size,0.),vec2(size,0.))-size*.68 :
        p_particleShape<1.5 ? length(local)-size :
        p_particleShape<2.5 ? box(local,vec2(0.),vec2(size),.08) :
        max(abs(local.x)*.83+local.y*.48,-local.y)-size;
      float angle=atan(q.y-mid.y,q.x-mid.x)/TAU+.5;
      float u=fract((p_colorMode<.5 ? angle : p_colorMode<1.5 ? length(q-mid)/188. : k)+t*p_colorSpeed);
      vec4 tint=u<.5 ? mix(stopa,stopb,u*2.) : mix(stopb,stopc,(u-.5)*2.);
      float twinkle=.55+.45*sin(k*TAU+t*p_pulseSpeed);
      float presence=p_ambient+twinkle*.58+magnet*.31+burst*.25;
      float response=mix(.8,1.12,sat(p_lerpSpeed*8.));
      vec3 bg=mix(INK,vec3(.92,.93,.97),p_lightMode);
      return overlay(bg,tint,(cover(d)+.24*glow(d,2.3))*presence*response*p_opacity*introAmount(p_intro>.5,.68));
    `,
  },
  shape_waves: {
    text: shapeText,
    extra: { textEnabled: ['float', p => shapeText(p) ? 1 : 0] },
    helpers: GLOW_WAVE,
    body: /* glsl */ `
      float pitch=max(4.,p_cellSize), a=radians(p_direction);
      vec2 q=center()+rotate2(p-center(),a);
      q.x-=time*p_flow*10.;
      vec2 grid=q/pitch, id=floor(grid), local=(fract(grid)-.5)*pitch;
      vec2 world=center()+rotate2((id+.5)*pitch+vec2(time*p_flow*10.,0.)-center(),-a);
      float depth=sin((id.x*.26+sin(id.y*.37)*1.8)*p_scale-time*p_speed*1.7);
      float level=sat(.5+depth*.5*p_contrast);
      float splash=(glow(length(world-point()),max(1.,p_splashRadius))*.52+
        pulseRing(world,origin(),sincePress,p_splashRadius*4.))*p_interactive*p_splashStrength;
      float sz=max(.2,p_dotSize*pitch*(.15+level*.23+splash*.15));
      float kind=p_shapes<.5 ? floor(hash(id)*3.) : p_shapes-1.;
      float d=kind<.5 ? box(local,vec2(0.),vec2(sz),.2) : kind<1.5 ? length(local)-sz :
        max(abs(local.x)*.86+local.y*.5,-local.y)-sz*.8;
      vec4 tint=mix(p_color,p_hoverColor,sat(splash*1.6));
      float edge=min(min(p.x,p.y),min(sceneSize.x-p.x,sceneSize.y-p.y));
      float mask=textEnabled>.5 ? label(world,center(),sceneSize*vec2(.86,.50)*p_textSize*1.65,3.) : 1.;
      float fade=p_fade>0. ? smoothstep(0.,p_fade*117.,edge) : 1.;
      float light=(p_brightness+level*.75+splash)*fade*mask*introAmount(p_intro>.5,p_introDuration);
      vec3 bg=overlay(INK,p_backgroundColor,1.);
      return overlay(bg,tint,light*(cover(d)+glow(d,2.)*p_glow*.53));
    `,
  },
  pattern_waves: {
    glyphs: p => p.glyphs?.characters,
    helpers: GLOW_WAVE,
    body: /* glsl */ `
      vec2 mid=center(), q=rotate2(p-mid,radians(p_direction));
      float pitch=max(3.,p_spacing);
      vec2 cell=floor(q/pitch), local=(fract(q/pitch)-.5)*pitch;
      vec2 k=cell*p_scale*.20;
      float t=time*p_speed*TAU;
      float wave=p_wave<.5 ? sin(k.x+t+sin(k.y*.9)*1.9)*cos(k.y*.37-t*.22) :
        p_wave<1.5 ? (sin(k.x+k.y*.33+t)+sin(k.y-k.x*.35-t*.42))*.5 : cos(length(k)*1.2-t);
      float cursor=(glow(length(p-point()),max(1.,p_cursorSize))+
        pulseRing(p,origin(),sincePress,p_cursorSize*3.))*p_cursorStrength*p_interactive;
      float level=sat(.46+(wave*p_depth+cursor)*.43*p_contrast);
      float radius=p_markSize*pitch*(.12+.29*level);
      float kind=p_pattern;
      float mark=kind<.5 ? cover(length(local)-radius) :
        kind<1.5 ? cover(box(local,vec2(0.),vec2(radius),.12)) :
        kind<2.5 ? cover(min(max(abs(local.x)-radius,abs(local.y)-radius*.28),max(abs(local.y)-radius,abs(local.x)-radius*.28))) :
        kind<3.5 ? cover(segment(local,vec2(-radius,0.),vec2(radius,0.))-.95) :
        glyph(local/pitch+.5,floor(level*max(0.,glyphCount-1.)));
      vec2 normal=p/sceneSize;
      float edge=min(min(normal.x,normal.y),min(1.-normal.x,1.-normal.y))*2.;
      float fade=p_fade<.5 ? 1. : p_fade<1.5 ? edge : p_fade<2.5 ? 1.-edge :
        p_fade<3.5 ? 1.-normal.y : normal.y;
      fade=smoothstep(0.,max(.001,p_fadeSize),fade);
      vec3 bg=overlay(INK,p_backgroundColor,1.);
      float illumination=sat(.22+level*.59+p_light*.34+pow(level,3.)*p_shine*.4);
      return overlay(bg,p_color,mark*illumination*fade*p_opacity*introAmount(p_intro>.5,.65));
    `,
  },
  light_rays: {
    helpers: GLOW_WAVE,
    body: /* glsl */ `
      float o=p_raysOrigin;
      vec2 source=o<.5 ? vec2(.08,-.10) : o<1.5 ? vec2(.5,-.10) : o<2.5 ? vec2(.92,-.10) :
        o<3.5 ? vec2(-.08,.50) : o<4.5 ? vec2(1.08,.50) :
        o<5.5 ? vec2(.08,1.10) : o<6.5 ? vec2(.50,1.10) : vec2(.92,1.10);
      source+=p_followMouse*(pointer-.5)*p_mouseInfluence;
      vec2 q=(p/sceneSize-source)*vec2(sceneSize.x/sceneSize.y,1.);
      vec2 inward=normalize((vec2(.5)-source)*vec2(sceneSize.x/sceneSize.y,1.));
      inward=rotate2(inward,radians(p_tilt));
      float depth=length(q), angle=atan(q.x*inward.y-q.y*inward.x,dot(q,inward));
      float t=time*p_raysSpeed;
      float spread=exp(-pow(angle/max(.07,p_lightSpread*1.48),2.));
      float waves=angle*(17.+p_rayDensity*35.)+p_distortion*sin(depth*11.-t*1.6);
      float beam=pow(sat(.55+.30*sin(waves+t*.77)+.15*sin(waves*1.71-t*.48)),1.+p_raySharpness*5.);
      float decay=exp(-depth/max(.045,p_rayLength*p_fadeDistance*.29));
      float haze=p_haze*exp(-depth*1.9)*p_atmosphere;
      float pulse=mix(1.,.70+.30*sin(t*2.1),p_pulsating);
      float shadow=1.-glow(length(p-point()),29.)*p_cursorShadow*.64;
      float impact=pulseRing(p,origin(),sincePress,205.)*p_clickPulse*.42;
      float grain=(hash(floor(p)+floor(time*24.))-.5)*p_noiseAmount*.33;
      float intensity=((beam*spread*decay+haze+p_glow*.25*exp(-depth*6.))*pulse*shadow+impact+grain)*p_intensity;
      vec3 tint=mix(vec3(dot(p_raysColor.rgb,vec3(.2126,.7152,.0722))),p_raysColor.rgb,p_saturation);
      vec3 base=mix(vec3(.026,.041,.071),vec3(.86,.89,.93),p_lightMode);
      vec3 target=mix(tint,tint*.53,p_lightMode);
      return mix(base,target,sat(intensity*p_raysColor.a*p_opacity*introAmount(p_intro>.5,.63)));
    `,
  },
  laser_flow: {
    extra: { reveal: ['vec2', p => [p.pointerReveal?.radius,p.pointerReveal?.opacity]] },
    body: /* glsl */ `
      float t=time*p_speed*(1.-p_paused);
      vec2 surface=at(p_beamPosition,clamp(p_surfaceLevel*.83,.19,.96));
      vec2 q=p-surface;
      float spread=max(.2,p_spread);
      float width=max(1.,p_beamWidth*3.)*(1.+sat(-q.y/270.)*.51);
      float pulse=1.+.15*p_pulse*sin(t*4.);
      float beam=exp(-pow(q.x/width,2.))*(1.-smoothstep(-4.,11.,q.y));
      float flare=exp(-length(q*vec2(.48,2.9))/max(2.,25.*p_flare));
      float spill=exp(-abs(q.x)/(57.*spread)-abs(q.y)*.19)*p_spill;
      float fog=exp(-abs(q.x)/(18.*spread+abs(q.y)*.14)-abs(q.y)*.004)*p_fog;
      float grid=11., seed=p_seed*.026;
      vec2 world=p+vec2(0.,t*22.);
      vec2 cell=floor(world/grid), local=fract(world/grid)-.5;
      float k=hash(cell+seed);
      float dust=step(.83,k)*cover(length(local*grid-vec2(sin(k*TAU),cos(k*TAU))*2.)-1.2)*p_dust;
      float stream=pow(sat(sin(q.x*.76+sin(floor(q.x*.25)+seed)*2.)),14.)*
        pow(.5+.5*sin(q.y*.071+t*5.+q.x*.28),3.)*p_streaks;
      float field=exp(-abs(q.x)/(37.*spread))*(1.-smoothstep(-15.,21.,q.y));
      float lift=glow(length(p-point()),max(2.,reveal.x*.36))*reveal.y*p_mouseInteraction;
      vec3 base=mix(INK,vec3(.89,.92,.98),p_lightMode);
      base=mix(base,scene(p),lift*.17*field);
      float amount=(beam*.68+flare*.65+spill*.33+fog*.81+field*(dust*.22*p_dots+stream*.19))*p_intensity*pulse;
      vec3 tint=mix(p_color.rgb,vec3(.94,.96,1.),sat(beam*.78+flare*.34));
      return mix(base,tint,sat(amount*p_color.a*introAmount(p_intro>.5,.58)));
    `,
  },
};
