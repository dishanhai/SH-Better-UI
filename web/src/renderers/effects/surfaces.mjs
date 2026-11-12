// Author: dishanhai

// Preset values follow GlassStyle. The browser kernel samples the fixture in GUI units.
const glassStyles = [
  [.78,.9,1,.10,.20,.035,.025,.90,5,7], [.78,.9,1,.08,.15,.025,.02,.60,4,6],
  [.78,.9,1,.08,.15,.020,.015,.60,0,7], [.78,.9,1,.05,.10,.020,0,0,0,0],
  [.78,.9,1,.06,0,.030,.01,0,0,0], [.7,.85,1,.12,.25,.020,.03,0,0,0],
  [.80,.83,.85,.025,-.08,.006,.008,.18,.8,2], [.25,.25,.25,.025,-.12,.006,.004,.10,0,1.5],
  [.22,.24,.27,.025,.12,0,.008,.72,2.4,3.5], [.20,.21,.23,.06,-.10,0,.006,.65,1.3,2.4],
  [.05,.09,.15,.30,-.30,.015,0,0,0,0], [.04,.07,.12,.50,-.45,.012,0,0,0,0], [0,0,0,0,0,0,0,0,0,0],
];

function backdrop(style) {
  const configured = style === undefined;
  const values = p => glassStyles[configured ? p.style : style] ?? glassStyles[5];
  return {
    extra: {
      glassTint: ['vec4', p => values(p).slice(0,4)],
      glassOptics: ['vec4', p => values(p).slice(4,8)],
      glassEdge: ['vec2', p => values(p).slice(8)],
    },
    body: BACKDROP.replaceAll('@enabled', configured ? 'p_enabled' : '1.')
      .replaceAll('@corner', configured ? '13.' : 'max(0.,p_cornerRadius)*2.')
      .replaceAll('@radius', configured ? 'p_radiusVirtual*p_blurRadiusScale' : 'p_radiusVirtual')
      .replaceAll('@sampling', configured ? 'p_denseSampling>0.5' : 'true')
      .replaceAll('@tint', configured ? 'p_tintScale' : '1.')
      .replaceAll('@grain', configured ? 'p_grainScale' : '1.')
      .replaceAll('@saturation', configured ? 'p_saturationScale' : '1.')
      .replaceAll('@lift', configured ? 'p_lift' : '0.')
      .replaceAll('@rim', configured ? 'p_rimScale' : '1.')
      .replaceAll('@refraction', configured ? 'p_refractionScale' : '1.'),
  };
}

const BACKDROP = /* glsl */ `
  vec2 mid=center(), halfSize=vec2(192.,111.);
  float d=box(p,mid,halfSize,@corner);
  vec3 base=scene(p);
  if(d>.6 || @enabled<.5 || p_strength<=0.) return base;
  float rim=glow(-d,glassEdge.y);
  vec2 normal=normalize((p-mid)/halfSize+vec2(.00001));
  vec2 src=p+normal*rim*glassEdge.x*@refraction*p_strength;
  float r=max(0.,@radius)*2.*p_strength;
  vec3 glass=.5*(gaussianPass(src,r,vec2(1.,0.),@sampling)+gaussianPass(src,r,vec2(0.,1.),@sampling));
  float luma=dot(glass,vec3(.2126,.7152,.0722));
  glass=mix(vec3(luma),glass,(1.+glassOptics.x)*@saturation);
  glass=mix(glass,glassTint.rgb,sat(glassTint.a*@tint));
  glass+=glassOptics.z+@lift+(hash(floor(p))-.5)*glassOptics.y*@grain;
  glass+=rim*glassOptics.w*@rim*.32;
  return mix(base,glass,cover(d)*sat(p_strength));
`;

const palette = [
  [0x382b25,0x886e55,0xf9e9d6,0xe3b688], [0x283242,0x8ca3bc,0xedf5ff,0x75aeda],
  [0x333439,0x646b76,0xf0f1f2,0xb1bac7], [0x153c3d,0x488f7b,0xe1fff4,0x82e7c0],
  [0x392951,0x8366a3,0xf4eaff,0xc4a1ff], [0x4d283a,0xa26686,0xffeafa,0xeaa6d2],
  [0x4a2c25,0xa36342,0xffeed6,0xffae72], [0x253648,0x7599b9,0xf0f9ff,0x94ccf1],
  [0x17293f,0x3b71a3,0xe2f4ff,0x69baff], [0x252a35,0x525f74,0xe8efff,0x82a0d5],
];
const paletteKeys = ['fill','edge','text','accent'];
const paletteRgb = color => [color >> 16 & 255, color >> 8 & 255, color & 255].map(value => value/255);
function theme(index) {
  return {
    extra: Object.fromEntries(paletteKeys.map((name, i) => ['theme'+name, ['vec3', p => {
      const override = p.colorOverrides?.[name];
      const own = typeof override === 'string' && /^#[a-f\d]{6,8}$/i.test(override);
      return paletteRgb(own ? parseInt(override.slice(-6),16) : palette[index ?? p.themeId][i]);
    }]])),
    body: /* glsl */ `
      vec2 mid=center(), halfSize=vec2(195.,112.);
      float d=box(p,mid,halfSize,15.);
      vec3 back=@back;
      vec3 c=back;
      if(d>.7) return c;
      vec3 base=@base;
      base=mix(base,themeedge,glow(abs(d),1.25)*.85);
      vec2 heading=mid+vec2(0.,-70.);
      base=mix(base,themetext,label(p,heading,vec2(260.,39.),0.));
      vec2 field=mid+vec2(0.,-8.);
      base=fill(base,themeedge,box(p,field,vec2(150.,23.),8.));
      base=fill(base,themefill*.67,box(p,field,vec2(149.,22.),7.));
      base=lines(base,p,field+vec2(-132.,-7.),102.,themetext*.55);
      vec2 button=mid+vec2(86.,66.);
      float hovered=cover(box(point(),button,vec2(66.,20.),8.));
      base=fill(base,mix(themeaccent*.58,themeaccent,.37+hovered*.28),box(p,button,vec2(66.,20.),8.));
      base=fill(base,themetext,box(p,button,vec2(26.,2.),1.));
      return mix(c,base,cover(d)*p_opacity);
    `.replace('@back', index === 7 ? 'scene(p)' : 'stage(p)')
      .replace('@base', index === 7 ? 'mix(softScene(p,15.),themefill,.54)'
        : index === 9 ? 'mix(themefill*.7,themeedge*.62,sat(1.-(p.y-mid.y+112.)/224.))'
          : 'mix(themefill*.91,themefill*1.18,sat(1.-(p.y-mid.y+112.)/224.))'),
  };
}

export const surfaceEffects = {
  backdrop_blur: backdrop(),
  clear_glass: backdrop(6),
  frosted_glass: backdrop(7),
  directional_blur: { body: /* glsl */ `
    float d=box(p,center(),vec2(196.,114.),8.);
    vec2 axis=manual>.5 ? normalize(point()-center()+vec2(.00001)) : vec2(1.,0.);
    return mix(scene(p),gaussianPass(p,28.,axis,true),cover(d));
  ` },
  refraction_wave: { body: /* glsl */ `
    float t=manual>.5 ? sincePress : cycle(1.6);
    vec2 radial=p-(manual>.5 ? origin() : center());
    float distance=length(radial), radius=12.+t*131., band=exp(-pow((distance-radius)/14.,2.));
    float gain=1.-smoothstep(.98,1.6,t);
    vec2 shift=normalize(radial+vec2(.00001))*sin((distance-radius)*.16)*band*12.*gain;
    return scene(p-shift)+vec3(.24,.31,.38)*band*gain;
  ` },
  radial_gradient: { body: /* glsl */ `
    vec2 o=vec2(p_originU,p_originV), q=p/sceneSize;
    float reach=max(.0001,length(max(abs(o),abs(vec2(1.)-o))*sceneSize));
    float amount=smoothstep(0.,reach,length((q-o)*sceneSize));
    vec4 tint=mix(vec4(p_innerArgb.rgb*p_innerArgb.a,p_innerArgb.a),vec4(p_outerArgb.rgb*p_outerArgb.a,p_outerArgb.a),amount);
    return tint.rgb+stage(p)*(1.-tint.a);
  ` },
  visual_reveal: { body: /* glsl */ `
    vec2 q=p/sceneSize;
    float axis=p_direction<.5 ? q.x : p_direction<1.5 ? 1.-q.x : p_direction<2.5 ? q.y : 1.-q.y;
    float radius=p_maxRadius*2.5*(1.-smoothstep(p_fullBlurUntil,max(p_fullBlurUntil+.0001,p_sharpFrom),axis));
    return .5*(gaussianPass(p,radius,vec2(1.,0.),true)+gaussianPass(p,radius,vec2(0.,1.),true));
  ` },
  classic_glass_themes: theme(),
  liquid_glass_surface: theme(7),
  network_surface: theme(8),
  slate_surface: theme(9),
  dynamic_theme: { body: /* glsl */ `
    float side=smoothstep(.42,.58,pointer.x);
    vec3 accent=mix(vec3(.13,.69,.83),vec3(.69,.43,.85),side);
    vec3 base=mix(stage(p),vec3(.86,.91,.96),side);
    if(contrast>.5) accent=mix(vec3(.40,.83,1.),vec3(.23,.09,.38),side);
    vec2 mid=center(), size=vec2(187.,103.);
    vec3 c=panel(base,p,mid,size,15.,mix(vec3(.08,.15,.21),vec3(.98,.98,.99),side));
    c=fill(c,accent,box(p,mid+vec2(0.,-54.),vec2(146.,17.),7.));
    c=mix(c,mix(vec3(.02,.08,.14),vec3(1.),side*.55),label(p,mid+vec2(0.,-54.),vec2(255.,37.),0.));
    return lines(c,p,mid+vec2(-139.,4.),147.,mix(TEXT,INK,side));
  ` },
  glass_panel_shadow: { body: /* glsl */ `
    vec2 mid=center(), halfSize=vec2(154.,87.);
    vec3 c=vec3(.43,.49,.58);
    for(int i=0;i<16;i++) {
      if(float(i)>=p_bands) break;
      float band=float(i+1), amount=.13*(1.-float(i)/max(1.,p_bands));
      float d=box(p,mid+vec2(0.,p_offsetY*2.),halfSize+band*2.7,7.+band);
      c=mix(c,vec3(.10,.14,.21),amount*cover(d));
    }
    c=fill(c,vec3(.84,.89,.94),box(p,mid,halfSize,7.));
    return mix(c,vec3(.25,.33,.43),label(p,mid,vec2(240.,46.),0.));
  ` },
  glass_text_glow: { body: /* glsl */ `
    vec2 mid=center()+vec2(0.,28.), size=vec2(366.,67.);
    vec3 c=stage(p);
    float mask=label(p,mid,size,0.);
    float fringe=0.;
    for(int i=0;i<8;i++) {
      float a=float(i)*TAU/8.;
      fringe+=label(p+vec2(cos(a),sin(a))*2.2,mid,size,0.)*.125;
    }
    c=mix(c,vec3(.63,.80,1.),max(0.,fringe-mask)*.65);
    c=mix(c,TEXT,mask);
    return mix(c,TEXT*.7,label(p,mid-vec2(0.,70.),size,0.));
  ` },
  glass_tooltip: { body: /* glsl */ `
    vec2 mid=clamp(point()+vec2(94.,42.),vec2(121.,74.),sceneSize-vec2(121.,69.));
    vec3 c=scene(p);
    float d=box(p,mid,vec2(119.,46.),9.);
    c=mix(c,INK,glow(d,14.)*.55);
    vec3 tooltip=mix(softScene(p,17.),vec3(.05,.09,.16),.82);
    tooltip=mix(tooltip,vec3(.66,.80,.94),glow(abs(d),.9)*.77);
    tooltip=mix(tooltip,vec3(.97),label(p,mid-vec2(0.,11.),vec2(219.,39.),0.));
    tooltip=lines(tooltip,p,mid+vec2(-87.,14.),110.,TEXT*.75);
    return mix(c,tooltip,cover(d));
  ` },
  spatial_surface: { body: /* glsl */ `
    vec2 mid=center(), q=p-mid;
    float d=box(p,mid,vec2(156.,107.),22.);
    float preset=p_preset;
    float relief=p_depth+step(.5,preset)*.5;
    vec3 c=preset>2.5 && preset<3.5 ? scene(p) : stage(p);
    c=mix(c,INK,.7*glow(d-max(0.,relief)*4.,8.+relief*13.));
    vec3 s=p_color.rgb;
    if(preset>2.5 && preset<3.5) s=mix(softScene(p,12.+p_blur*15.),s,.41);
    if(preset>3.5 && preset<4.5) s*=.68+.52*exp(-pow((q.x+q.y*.5)/65.,2.));
    if(preset>4.5) s=mix(s,.5+.45*cos(vec3(0.,2.1,4.2)+(q.x+q.y)*.032+time),.53);
    float rim=glow(-d,3.+5.*p_roughness);
    float side=normalize(q+vec2(.00001)).y;
    float lightSign=preset>1.5 && preset<2.5 ? 1. : -1.;
    s+=rim*side*lightSign*(.09*relief+p_highlight*.3);
    s=mix(s,TEXT,glow(abs(d),1.2)*p_border*.43);
    return mix(c,s,cover(d)*p_color.a*p_opacity);
  ` },
  spatial_shadow: {
    extra: { shadowLayers: ['vec4', p => {
      const layer = p.shadow?.layers?.[0] ?? {};
      return [layer.offsetX ?? 0, layer.offsetY ?? 8+p.elevation*4,
        layer.blurRadius ?? layer.radius ?? 12+p.elevation*5, layer.opacity ?? layer.alpha ?? .62];
    }] },
    body: /* glsl */ `
      vec2 mid=center(), size=vec2(150.,88.);
      float d=box(p,mid,size,20.), outer=box(p,mid+shadowLayers.xy,size,20.);
      vec3 c=mix(stage(p)+.14,INK,glow(outer,shadowLayers.z)*shadowLayers.w);
      vec3 raised=mix(vec3(.38,.48,.64),vec3(.19,.28,.43),sat((p.y-mid.y+88.)/176.));
      raised=mix(raised,INK,glow(-d,7.)*.39*smoothstep(-40.,90.,p.y-mid.y));
      return mix(c,raised,cover(d));
    `,
  },
  spatial_lighting: {
    extra: { lightSource: ['vec4', p => {
      const light = p.light?.lights?.[0] ?? {};
      return [light.x ?? -1, light.y ?? -1, light.intensity ?? 1, light.radius ?? 125];
    }] },
    body: /* glsl */ `
      vec2 lightAt=lightSource.x<0. ? point() : vec2(lightSource.x,lightSource.y);
      vec3 c=stage(p);
      for(int i=0;i<3;i++) {
        vec2 mid=center()+vec2((float(i)-1.)*126.,sin(float(i)*2.8)*36.);
        vec2 dir=normalize(mid-lightAt+vec2(.00001));
        float d=box(p,mid,vec2(47.,49.),15.);
        float shadow=box(p,mid+dir*(9.+float(i)*5.),vec2(48.,50.),15.);
        c=mix(c,INK,glow(shadow,15.)*.86);
        float face=glow(length(p-lightAt),lightSource.w);
        float rim=glow(-d,6.)*sat(dot(normalize(p-mid+vec2(.00001)),-dir));
        vec3 material=mix(PANEL,BLUE,sat((face*.64+rim*.41)*lightSource.z));
        c=fill(c,material,d);
      }
      return mix(c,vec3(1.,.88,.67),ring(p,lightAt,11.,1.1)+glow(length(p-lightAt),8.)*.8);
    `,
  },
};
