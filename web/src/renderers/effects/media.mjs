// Author: dishanhai
const ICON = /* glsl */ `
  uniform sampler2D nativeIcon;
  vec4 shop(vec2 p) {
    vec2 t=p/86.+.5;
    if (min(t.x,t.y)<0. || max(t.x,t.y)>1.) return vec4(0.);
    return texture(nativeIcon,t);
  }
  vec3 rainbow(float v) {
    return .57+.43*cos(TAU*(v+vec3(0.,.67,.34)));
  }
`;

function periods(...names) {
  return { periods: [names.length===3 ? 'vec3' : 'vec4', p => names.map(name => p.periodsMs?.[name])] };
}

export const mediaEffects = {
  icon_motion: {
    icon: true, helpers: ICON, extra: periods('SPIN','FLOAT','BOUNCE'),
    body: /* glsl */ `
      vec3 c=stage(p);
      for(int i=0;i<3;i++) {
        float ix=float(i);
        vec2 mid=center()+vec2((ix-1.)*146.,0.);
        float phase=time/seconds(periods[i])*TAU;
        vec2 local=p-mid;
        if(i==0) local=rotate2(local,-phase);
        if(i==1) local.y+=sin(phase)*14.;
        if(i==2) local.y+=abs(sin(phase*.5))*28.;
        c=mix(c,LINE,glow(length(p-mid-vec2(0.,45.)),9.)*.74);
        vec4 iconColor=shop(local);
        c=overlay(c,iconColor,1.);
      }
      return c;
    `,
  },
  icon_shader: {
    icon: true, helpers: ICON, extra: periods('GRADIENT_SWEEP','COLOR_CYCLE','ENERGY_WAVE','PULSE'),
    body: /* glsl */ `
      vec3 c=stage(p);
      for(int i=0;i<4;i++) {
        vec2 mid=center()+vec2((float(i)-1.5)*122.,0.);
        vec2 q=p-mid;
        float t=time/seconds(periods[i]), phase=fract(t);
        vec4 texel=shop(q);
        float s=exp(-pow((q.x+q.y*.51-mix(-58.,58.,phase))/14.,2.));
        float wave=.5+.5*sin(q.x*.19+q.y*.15-t*TAU);
        vec3 shaded=i==0 ? texel.rgb+vec3(.45,.66,.95)*s :
          i==1 ? mix(texel.rgb,rainbow(t),.72) :
          i==2 ? mix(texel.rgb,vec3(.16,.85,1.),wave*.79)+wave*.16 :
          texel.rgb*(.60+.60*(.5+.5*sin(t*TAU)));
        c=overlay(c,vec4(shaded,texel.a),1.);
      }
      return c;
    `,
  },
  electric_logo: {
    helpers: /* glsl */ `
      float bolt(vec2 p, float t, float seed) {
        return sin(p.y*.12+t*2.+seed)*1.9+sin(p.x*.38+p.y*.21-t*3.1+seed)*1.15+
          (hash(floor(p*.34)+vec2(floor(t*18.),seed))-.5)*2.3;
      }
    `,
    body: /* glsl */ `
      vec2 mid=center(), size=vec2(424.,205.)*max(.10,p_scale);
      float t=time*p_speed;
      vec2 q=p-mid;
      float cursor=glow(length(p-point()),max(.2,p_cursorRadius)) * p_cursorIntensity * p_interactionEnabled;
      float noise=bolt(p,t,0.);
      vec2 local=p+vec2(noise,bolt(p.yx,t,.91))*p_bend*(.7+p_crackle*.58+cursor*.7);
      float face=label(local,mid,size,3.);
      float core=0., glowSum=0.;
      float strands=clamp(p_strands,1.,16.);
      for(int i=0;i<16;i++) {
        if(float(i)>=strands) break;
        float a=float(i)*TAU/strands+t*.3;
        vec2 dir=vec2(cos(a),sin(a));
        float wid=max(.35,p_thickness);
        float arc=max(0.,bolt(p,t,float(i)))*p_arcs;
        float sample1=label(local+dir*(wid*1.2+arc),mid,size,3.);
        float sample2=label(local-dir*(wid*2.+arc),mid,size,3.);
        core=max(core,abs(sample1-face));
        glowSum=max(glowSum,abs(sample2-face));
      }
      float flicker=1.-p_flicker*.28*hash(vec2(floor(t*25.),floor(q.y*.1)));
      vec3 bg=mix(INK,vec3(.88,.89,.94),p_theme);
      bg=overlay(bg,p_glowArgb,sat((glowSum*.57+face*p_fill*.29)*(p_glow+cursor)*flicker));
      return overlay(bg,p_coreArgb,sat((core+.38*face*p_fill)*p_intensity*flicker*(1.+cursor)));
    `,
  },
  formatted_text_effects: {
    glyphs: () => 'SHANHAI / DAWN',
    extra: { timing: ['vec2', p => [p.timing?.stepMillis,p.timing?.reverse ? 1 : 0]] },
    body: /* glsl */ `
      vec2 mid=center(), start=mid+vec2(-210.,-53.);
      float pitch=30.;
      float idx=floor((p.x-start.x)/pitch);
      float total=glyphCount;
      float phase=time*3.+idx*.62;
      float elapsed=timing.x>0. ? cycle(timing.x*.001*total+.9) : 100.;
      float order=mix(idx,total-idx-1.,timing.y);
      float reveal=timing.x>0. ? smoothstep(order*timing.x*.001,order*timing.x*.001+.12,elapsed) : 1.;
      vec3 c=stage(p);
      if(idx>=0. && idx<total) {
        float shift=sin(phase)*7.;
        float mask=glyph(vec2(fract((p.x-start.x)/pitch),(p.y-start.y-shift)/40.),idx);
        vec3 palette=mix(MINT,VIOLET,sat(idx/max(1.,total-1.)+.23*sin(phase)));
        c=mix(c,palette,mask*reveal);
      }
      float staticMask=label(p,mid+vec2(0.,49.),vec2(421.,56.),0.);
      return mix(c,mix(MINT,VIOLET,sat((p.x-mid.x+210.)/420.)),staticMask);
    `,
  },
  floating_lyrics: {
    text: p => ({ text: 'Over the quiet tide', fontWeight: 540+(p.weightPercent ?? 0)*5 }),
    extra: { useLift: ['float', p => p.supportedUnitEffects?.defaultChain?.includes('WaveLiftEffect') ? 1 : 0] },
    body: /* glsl */ `
      float t=max(0.,time+p_offsetMs*.001);
      float phrase=mod(floor(t/2.1),2.);
      float phase=mod(t,2.1);
      float trans=seconds(p_lineTransitionMs);
      float slide=(1.-ease(phase/trans))*20.;
      float width=min(sceneSize.x-2.*p_padXPx,sceneSize.x*p_maxWidthRatio);
      float height=52.*p_lineScale;
      vec2 mid=at(.5,.47+p_anchorRatio*.30)+vec2(0.,slide);
      float karaoke=sat(phase/1.75);
      float xpos=sat((p.x-mid.x)/max(1.,width)+.5);
      vec2 wave=vec2(0.,sin((xpos-karaoke)*20.)*glow(abs(xpos-karaoke),.09)*8.*useLift);
      float row=phrase<.5 ? 3. : 1.;
      float sung=p_feather>.5 ? smoothstep(-.028,.028,karaoke-xpos) : step(xpos,karaoke);
      float alpha=label(p+wave,mid,vec2(width,height),row);
      vec3 c=stage(p);
      if(p_shadow>.5) {
        float shadow=label(p+wave-vec2(1.,2.),mid,vec2(width,height),row);
        if(p_softShadow>.5) shadow=max(shadow,label(p+wave-vec2(3.,4.),mid,vec2(width,height),row)*.55);
        c=mix(c,INK,shadow*.88*p_opacity);
      }
      if(p_stroke>.5) {
        float outline=0.;
        for(int i=0;i<8;i++) {
          float angle=float(i)*TAU/8.;
          outline=max(outline,label(p+wave+vec2(cos(angle),sin(angle))*p_strokePx,mid,vec2(width,height),row));
        }
        c=overlay(c,p_strokeColor,outline*p_strokeAlphaPercent*.01*p_opacity);
      }
      c=overlay(c,mix(p_unsungColor,p_sungColor,sung),alpha*p_opacity);
      if(p_lineCount>1.5) {
        vec2 next=mid+vec2(0.,height*.70+p_lineGapPx*2.);
        c=overlay(c,p_translationColor,label(p,next,vec2(width,height*.66),2.)*p_opacity);
        if(p_wrap>.5) c=overlay(c,p_unsungColor,label(p,next+vec2(0.,height*.53),vec2(width*.64,height*.53),1.)*.64*p_opacity);
      }
      if(p_lineCount>2.5) {
        vec2 last=mid-vec2(0.,height*.80+p_lineGapPx);
        c=overlay(c,p_unsungColor,label(p,last,vec2(width*.86,height*.65),phrase<.5 ? 1. : 3.)*.48*p_opacity);
      }
      return c;
    `,
  },
  music_reactive: {
    body: /* glsl */ `
      // Recorded-looking, deterministic bass and onset envelope for the fixed gallery score.
      float t=time, beat=fract(t*2.1), onset=exp(-beat*8.);
      float bass=.22+.66*onset+.1*sin(t*1.72);
      vec2 mid=center(), size=vec2(190.,101.), q=p-mid;
      float d=box(p,mid,size,22.);
      vec3 c=mix(stage(p),VIOLET,glow(d,19.)*onset*.38);
      c=panel(c,p,mid,size,22.,PANEL);
      if(d>0.) return c;
      float columns=32., stepSize=10.;
      float cell=floor((q.x+160.)/stepSize), dx=mod(q.x+160.,stepSize)-stepSize*.5;
      float band=exp(-cell*.049)*bass+.13+.15*sin(t*7.1-cell*.56)*sin(t*3.+cell*.2);
      if(cell>=0. && cell<columns) {
        float h=max(4.,band*65.);
        float bar=box(vec2(dx,q.y-14.),vec2(0.),vec2(2.5,h),2.);
        c=fill(c,mix(MINT,BLUE,cell/columns),bar);
      }
      float ripple=ring(p,mid+vec2(-144.,-61.),10.+beat*21.,1.7)*onset;
      c=mix(c,MINT,ripple);
      c=fill(c,BLUE,length(q-vec2(-144.,-61.))-8.-onset*3.);
      c=mix(c,TEXT,label(p,mid+vec2(18.,-60.),vec2(248.,37.),0.));
      return mix(c,BLUE,glow(-d,3.)*onset*.58);
    `,
  },
};
