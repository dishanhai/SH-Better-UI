// Author: dishanhai
const SPRING = /* glsl */ `
  float settle(float t) {
    return 1.-exp(-max(t,0.)*7.5)*(cos(t*16.)+.46875*sin(t*16.));
  }
`;

export const motionEffects = {
  live_activity: { body: /* glsl */ `
    float t=cycle(4.2);
    float expanded=ease((t-.65)/.45)*(1.-ease((t-3.2)/.45));
    if(p_presentation>.5) expanded=step(1.5,p_presentation);
    vec2 halfSize=mix(vec2(109.,22.),vec2(177.,69.),expanded);
    vec2 mid=vec2(center().x,82.+halfSize.y);
    float radius=mix(22.,17.,expanded);
    float d=box(p,mid,halfSize,radius);
    vec3 c=panel(stage(p),p,mid,halfSize,radius,INK);
    vec2 header=vec2(mid.x,104.);
    c=mix(c,TEXT,label(p,header,vec2(186.,29.),0.)*cover(d));
    float detail=smoothstep(.5,.98,expanded)*cover(d);
    c=mix(c,TEXT,label(p,header+vec2(0.,32.),vec2(248.,32.),3.)*.7*detail);
    vec2 track=header+vec2(0.,71.);
    float fillWidth=mix(113.,274.,sat(t/3.2));
    c=mix(c,LINE,cover(box(p,track,vec2(137.,4.),4.))*detail);
    return mix(c,BLUE,cover(box(p,track+vec2((fillWidth-274.)*.5,0.),vec2(fillWidth*.5,4.),4.))*detail);
  `, text: () => ({ text: '示例活动 · 进度' }) },
  attention_indicator: { body: /* glsl */ `
    vec2 mid=center(), left=mid-vec2(113.,0.), right=mid+vec2(113.,0.);
    float u=manual>.5 ? step(mid.x,point().x) : ease(cycle(2.2)/.38)*(1.-ease((cycle(2.2)-1.1)/.38));
    vec2 selected=mix(left,right,u);
    vec3 c=panel(stage(p),p,left,vec2(98.,33.),11.,PANEL);
    c=panel(c,p,right,vec2(98.,33.),11.,PANEL);
    float target=box(p,selected,vec2(97.,32.),10.);
    c=fill(c,vec3(.16,.31,.49),target);
    c=mix(c,BLUE,glow(abs(target),1.5)*.85);
    c=fill(c,BLUE,box(p,selected+vec2(0.,28.),vec2(28.,2.2),2.2));
    c=fill(c,TEXT,box(p,left+vec2((1.-u)*p_contentOffset,0.),vec2(34.,3.),3.));
    return fill(c,TEXT,box(p,right+vec2(u*p_contentOffset,0.),vec2(34.,3.),3.));
  ` },
  breathing_glow: { body: /* glsl */ `
    vec2 mid=center();
    float d=box(p,mid,vec2(128.,38.),13.);
    float focus=manual>.5 ? glow(max(0.,box(point(),mid,vec2(128.,38.),13.)),45.) : 1.;
    float breathing=.5+.5*cos(time*TAU*p_speed/seconds(p_basePeriodMs));
    float halo=glow(d,max(.3,p_haloMargin)*2.)*breathing*focus*p_enabled*p_strength;
    vec3 c=mix(stage(p),BLUE,sat(halo*.72));
    c=fill(c,LINE,d);
    c=fill(c,PANEL,d+1.);
    return mix(c,TEXT,label(p,mid,vec2(233.,34.),0.));
  ` },
  flow_border: { body: /* glsl */ `
    vec2 mid=center(), halfSize=vec2(129.,39.), q=p-mid;
    float d=box(p,mid,halfSize,1.2);
    vec2 edge=abs(q)-halfSize;
    float across=halfSize.x*2., down=halfSize.y*2., perimeter=(across+down)*2.;
    float along=edge.x>edge.y
      ? (q.x>0. ? across+q.y+halfSize.y : across*2.+down+halfSize.y-q.y)
      : (q.y<0. ? q.x+halfSize.x : across+down+halfSize.x-q.x);
    float head=fract(time*p_speed/seconds(p_basePeriodMs))*perimeter;
    float tail=mod(head-along+perimeter,perimeter);
    float amount=glow(abs(d),1.55)*exp(-tail/37.)*p_strength*p_enabled;
    vec3 c=panel(stage(p),p,mid,halfSize,1.2,PANEL);
    c=mix(c,MINT,sat(amount));
    return mix(c,TEXT,label(p,mid,vec2(235.,35.),0.));
  ` },
  click_ripple: { body: /* glsl */ `
    vec2 mid=center(), halfSize=vec2(140.,42.);
    float d=box(p,mid,halfSize,15.);
    float duration=seconds(p_durationMs);
    float life=(manual>.5 ? sincePress : cycle(duration+.55))/duration;
    vec2 start=manual>.5 ? origin() : mid+vec2(-52.,7.);
    float valid=cover(box(start,mid,halfSize,15.))*p_enabled;
    float spread=length(p-start)-ease(life)*230.;
    float ripple=(glow(abs(spread),7.)*.32+cover(spread)*.20)*(1.-sat(life))*p_strength*valid;
    vec3 c=panel(stage(p),p,mid,halfSize,15.,PANEL);
    c=mix(c,BLUE,sat(ripple*cover(d)));
    return mix(c,TEXT,label(p,mid,vec2(246.,35.),0.));
  ` },
  capsule_reveal: { body: /* glsl */ `
    vec2 mid=center();
    float duration=.78/max(.05,p_speed);
    float grow=p_enabled<.5 ? 1. : ease(progress(duration));
    float detail=ease((grow-.56)*2.4);
    vec2 size=mix(vec2(18.),vec2(165.,95.),grow);
    float radius=mix(size.y,15.,sat(grow*1.12));
    float d=box(p,mid,size,radius);
    vec3 c=mix(stage(p),BLUE,glow(d,11.)*.23*p_strength);
    vec3 content=mix(vec3(.12,.26,.43),PANEL,detail);
    content=lines(content,p,mid+vec2(-104.,0.),202.,mix(LINE,BLUE,detail)*(.55+detail*.3));
    content=mix(content,TEXT,label(p,mid+vec2(0.,-43.),vec2(268.,45.),0.)*detail);
    content=mix(content,BLUE,glow(abs(d),1.15)*.58*p_strength);
    return mix(c,content,cover(d));
  ` },
  page_slide: { body: /* glsl */ `
    vec2 mid=center(), clipSize=vec2(194.,110.);
    vec2 dir=p_direction<.5 ? vec2(-1.,0.) : p_direction<1.5 ? vec2(1.,0.) :
      p_direction<2.5 ? vec2(0.,-1.) : vec2(0.,1.);
    float span=p_baseDurationMs*.001/max(p_speed,.05);
    float move=(1.-ease(progress(span)))*p_enabled*p_strength;
    vec2 pos=mid+dir*move*(abs(dir.x)*362.+abs(dir.y)*222.);
    vec2 size=vec2(175.,99.)*(1.-min(.28,move*.20)*p_motion);
    vec3 c=panel(stage(p),p,mid,clipSize,13.,INK);
    if(box(p,mid,clipSize-1.,12.)>0.) return c;
    if(p_progressiveBlur+p_realtimeBlur>.5 && move>.003) {
      float amount=move*(p_progressiveBlur*19.+p_realtimeBlur*13.);
      return (page(c,p-dir*amount,pos,size,BLUE)+page(c,p,pos,size,BLUE)+page(c,p+dir*amount,pos,size,BLUE))/3.;
    }
    return page(c,p,pos,size,BLUE);
  ` },
  recipe_transition: { body: /* glsl */ `
    vec2 mid=center(), size=vec2(172.,98.);
    float transition=ease(progress(.62/max(p_speed,.05)));
    float move=p_enabled<.5 ? 1. : transition;
    float span=365.*p_strength;
    vec3 c=panel(stage(p),p,mid,vec2(196.,116.),13.,INK);
    if(box(p,mid,vec2(195.,115.),12.)>0.) return c;
    vec2 outgoing=mid-vec2(move*span,0.), incoming=mid+vec2((1.-move)*span,0.);
    float trail=sin(move*PI)*13.*p_strength;
    vec3 previous=page(c,p,outgoing,size,VIOLET);
    vec3 next=page(previous,p,incoming,size,MINT);
    next+=.20*(page(previous,p+vec2(trail,0.),incoming,size,MINT)-next);
    float companion=mix(-78.,78.,move);
    return fill(next,MINT,box(p,mid+vec2(companion,108.),vec2(18.,2.),2.));
  ` },
  screen_crossfade: { body: /* glsl */ `
    vec2 mid=center(), size=vec2(186.,106.);
    float t=manual>.5 ? sincePress : cycle(1.85);
    float fade=smoothstep(0.,max(.01,p_fadeEnd),t);
    vec3 c=stage(p);
    vec3 old=page(c,p,mid,size,VIOLET);
    vec3 next=page(c,p,mid,size,BLUE);
    next=fill(next,MINT,box(p,mid+vec2(-115.,48.),vec2(40.,11.),5.));
    return mix(next,old,(1.-fade)*p_enabled*sat(p_strength));
  ` },
  button_feedback: { body: /* glsl */ `
    vec2 mid=center();
    float t=manual>.5 ? sincePress : cycle(1.65);
    bool down=manual>.5 ? pressed>.5 : t<.15;
    bool failed=manual>.5 ? origin().x<.5 : mod(floor(time/1.65),2.)<.5;
    float elapsed=manual>.5 ? sinceRelease : max(0.,t-.15);
    float pulse=down ? 0. : exp(-elapsed*9.);
    float scale=down ? .95 : 1.-pulse*.018;
    vec2 displaced=mid+vec2(failed ? 9.*sin(elapsed*45.)*pulse : 0.,0.);
    vec2 size=vec2(146.,47.)*scale;
    vec3 tone=failed ? vec3(1.,.28,.34) : MINT;
    vec3 c=panel(stage(p),p,displaced,size,14.,mix(PANEL,tone,pulse*.54));
    c=mix(c,tone,glow(abs(box(p,displaced,size,14.)),2.)*pulse*.8);
    return mix(c,TEXT,label(p,displaced,vec2(240.,37.)*scale,0.));
  ` },
  bottom_sheet: { helpers: SPRING, body: /* glsl */ `
    float t=cycle(3.2);
    float initial=165.+74.*ease(t/.55);
    float y=initial-139.*settle(max(0.,t-.72))+97.*settle(max(0.,t-2.1));
    if(manual>.5) {
      float endY=releasePoint.y*sceneSize.y;
      float anchor=endY>216. ? 257. : endY>130. ? 174. : 96.;
      y=sincePress>=100. ? 174. : pressed>.5 ? clamp(point().y,85.,275.) :
        mix(clamp(endY,85.,275.),anchor,settle(sinceRelease));
    }
    vec2 mid=vec2(center().x,y+156.);
    vec3 c=page(stage(p),p,center(),vec2(211.,144.),VIOLET);
    c*=.55+sat(y/sceneSize.y)*.28;
    c=panel(c,p,mid,vec2(196.,156.),20.,PANEL);
    c=fill(c,BLUE,box(p,mid+vec2(0.,-143.),vec2(25.,2.5),2.5));
    c=mix(c,TEXT,label(p,mid+vec2(0.,-113.),vec2(330.,54.),0.));
    return lines(c,p,mid+vec2(-149.,-74.),210.,TEXT*.58);
  ` },
  elastic_collision: { helpers: SPRING, body: /* glsl */ `
    float t=cycle(2.7);
    float force=sin(PI*sat(t/.72));
    if(manual>.5) force=pressed>.5 ? 1.-abs(pointer.x-.5)*1.5 : 1.-sat(settle(sinceRelease));
    else force+=.19*exp(-max(0.,t-.72)*8.)*sin(max(0.,t-.72)*24.);
    vec3 c=stage(p);
    for(int i=0;i<3;i++) {
      float dx=float(i-1);
      vec2 mid=center()+vec2(dx*(114.+force*22.),abs(dx)*force*7.);
      vec2 size=vec2(53.-(1.-abs(dx))*force*14.,40.+(1.-abs(dx))*force*11.);
      vec3 tone=i==1 ? mix(PANEL,BLUE,.3+force*.43) : PANEL;
      c=panel(c,p,mid,size,11.,tone);
      c=fill(c,TEXT*.64,box(p,mid,vec2(17.,2.2),2.));
    }
    return c;
  ` },
  interruptible_animation: { helpers: SPRING, body: /* glsl */ `
    float t=cycle(2.9);
    // Superimposed step responses retain the running spring velocity at each retarget.
    float step1=settle(max(0.,t-.24));
    float step2=settle(max(0.,t-.40));
    float step3=settle(max(0.,t-1.24));
    float u=sat(step1-step2+step3-settle(max(0.,t-2.17)));
    if(manual>.5) u=sat(settle(sincePress)-(pressed>.5 ? 0. : settle(sinceRelease)));
    vec2 mid=center();
    vec3 c=fill(stage(p),LINE,box(p,mid,vec2(188.,2.),2.));
    c=fill(c,LINE,length(p-mid-vec2(-141.,0.))-8.);
    c=fill(c,LINE,length(p-mid-vec2(141.,0.))-8.);
    vec2 pos=mid+vec2(mix(-140.,140.,u),0.);
    c=mix(c,BLUE,glow(box(p,pos,vec2(24.+u*17.,18.),18.),10.)*.55);
    return fill(c,mix(BLUE,MINT,u),box(p,pos,vec2(24.+u*17.,18.),18.));
  ` },
  pull_refresh: { body: /* glsl */ `
    float t=cycle(3.3);
    float pull=manual>.5 ? (pressed>.5 ? sat((pointer.y-pressOrigin.y)*3.7) : 0.) : ease(t/.72)*(1.-smoothstep(.88,1.15,t));
    bool armed=releasePoint.y-pressOrigin.y>.18;
    bool pending=manual>.5 ? sinceRelease<.65 && armed : t>1.0 && t<2.14;
    bool done=manual>.5 ? sinceRelease>=.65 && sinceRelease<1.22 && armed : t>=2.14 && t<2.72;
    vec2 mid=at(.5,.27+pull*.15);
    vec3 c=page(stage(p),p,at(.5,.60+pull*.12),vec2(192.,102.),VIOLET);
    float visibility=max(pull,(pending || done) ? 1. : 0.);
    vec3 tint=done ? MINT : pull>.85 ? vec3(1.,.75,.38) : BLUE;
    vec2 q=p-mid;
    float arc=fract(atan(q.y,q.x)/TAU+1.-(pending ? time*1.4 : .24));
    float indicator=ring(p,mid,14.,1.8)*step(arc,pending ? .73 : max(.12,pull));
    if(done) indicator=cover(min(segment(q,vec2(-8.,0.),vec2(-2.,6.)),segment(q,vec2(-2.,6.),vec2(9.,-7.)))-1.7);
    c=mix(c,PANEL,cover(length(q)-24.)*visibility);
    return mix(c,tint,indicator*visibility);
  ` },
  swipe_toast: { helpers: SPRING, body: /* glsl */ `
    float t=cycle(3.3);
    float moved=-60.*ease((t-.7)/.48)+60.*ease((t-1.22)/.24)-123.*ease((t-2.3)/.36);
    if(manual>.5) {
      float drag=(pointer.y-pressOrigin.y)*sceneSize.y;
      float end=(releasePoint.y-pressOrigin.y)*sceneSize.y;
      moved=pressed>.5 ? drag : abs(end)>50. ? end+sign(end)*173.*ease(sinceRelease/.35) : end*(1.-settle(sinceRelease));
    }
    vec2 mid=at(.5,.27)+vec2(0.,moved), size=vec2(158.,43.);
    vec3 c=panel(stage(p),p,mid,size,11.,PANEL);
    c=fill(c,MINT,length(p-mid+vec2(115.,0.))-10.);
    c=mix(c,TEXT,label(p,mid+vec2(17.,-6.),vec2(233.,37.),0.));
    float fuse=1.-sat((manual>.5 && sincePress<100. ? (pressed>.5 ? 0. : sinceRelease) : t)/3.3);
    vec2 start=mid+vec2(-147.,p_fuse<.5 ? -36. : 36.);
    if(p_fuse<1.5 && fuse>0.) c=fill(c,BLUE,box(p,start+vec2(147.*fuse,0.),vec2(147.*fuse,1.7),1.5));
    return c;
  ` },
  toast_motion: { body: /* glsl */ `
    float t=manual>.5 && sincePress<100. ? sincePress : cycle(2.4);
    float u=ease(t/.48), leave=ease((t-1.72)/.5);
    float type=p_effect;
    vec2 mid=at(.65,.34);
    float phase=1.-u+leave;
    if(type<.5) mid.x+=phase*395.;
    if(type>.5 && type<1.5) mid.y-=phase*218.;
    float alpha=(type>1.5 && type<3.5) ? u*(1.-leave) : 1.;
    float scale=type>2.5 && type<3.5 ? .64+u*.36+.09*sin(u*PI)-leave*.24 : 1.;
    vec3 c=stage(p);
    vec3 toast=panel(c,p,mid,vec2(140.,40.)*scale,12.*scale,PANEL);
    toast=fill(toast,BLUE,length(p-mid+vec2(102.,0.)*scale)-10.*scale);
    toast=mix(toast,TEXT,label(p,mid+vec2(14.,0.)*scale,vec2(216.,38.)*scale,0.));
    return mix(c,toast,alpha);
  ` },
  loading_progress: { body: /* glsl */ `
    vec2 mid=center();
    float phase=fract(time/seconds(p_indeterminateTopCycleMs));
    float x=mix(-sceneSize.x*.25,sceneSize.x*1.25,phase);
    vec3 c=fill(stage(p),BLUE,box(p,vec2(x,p_topHeight),vec2(102.,max(.3,p_topHeight)),1.));
    c=panel(c,p,mid,vec2(167.,72.),15.,PANEL);
    c=mix(c,TEXT,label(p,mid+vec2(0.,-27.),vec2(267.,41.),0.));
    float w=250.;
    float done=manual>.5 ? pointer.x : cycle(2.65)/2.65;
    c=fill(c,INK,box(p,mid+vec2(0.,22.),vec2(w*.5,7.),7.));
    return fill(c,mix(MINT,BLUE,done),box(p,mid+vec2((done-1.)*w*.5,22.),vec2(max(.01,done*w*.5),6.),min(6.,done*w*.5)));
  ` },
  loading_spinner: { body: /* glsl */ `
    vec2 mid=center(), q=p-mid;
    float cycleTime=fract(time/seconds(p_spinPeriodMs));
    float arc=fract(atan(q.y,q.x)/TAU+1.-cycleTime);
    vec3 c=mix(stage(p),LINE,ring(p,mid,37.,4.));
    c=mix(c,BLUE,ring(p,mid,37.,4.)*step(arc,.74)*pow(1.-arc,.52));
    vec2 edge=at(.86,.16), ep=p-edge;
    float edgeArc=fract(atan(ep.y,ep.x)/TAU+1.-cycleTime);
    c=mix(c,MINT,ring(p,edge,max(1.,p_edgeDiameter),2.)*step(edgeArc,.69));
    return mix(c,TEXT,label(p,mid+vec2(0.,81.),vec2(257.,39.),0.));
  ` },
  loading_skeleton: { body: /* glsl */ `
    vec2 mid=center(), size=vec2(186.,106.);
    vec3 c=panel(stage(p),p,mid,size,17.,PANEL);
    float radius=p_defaultBlockCornerRadius*1.75;
    float d=box(p,mid+vec2(-99.,-26.),vec2(55.,49.),min(35.,radius));
    d=min(d,box(p,mid+vec2(67.,-52.),vec2(86.,12.),min(11.,radius)));
    d=min(d,box(p,mid+vec2(38.,-16.),vec2(57.,7.),min(6.,radius)));
    d=min(d,box(p,mid+vec2(46.,11.),vec2(65.,7.),min(6.,radius)));
    d=min(d,box(p,mid+vec2(0.,60.),vec2(154.,10.),min(9.,radius)));
    float wave=fract(time/seconds(p_periodMs));
    float stripe=exp(-pow((p.x+p.y*.42-(mid.x+mid.y*.42+(wave-.5)*550.))/46.,2.));
    return fill(c,mix(LINE,TEXT*.61,stripe*.87),d);
  ` },
};
