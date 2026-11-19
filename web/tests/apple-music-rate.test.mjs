// Author: dishanhai
import test from 'node:test';
import assert from 'node:assert/strict';
import { createPlaybackFlow } from '../../src/main/resources/assets/sh_better_ui/music/apple-login/playback-flow.mjs';
import { createRateBridge } from '../../src/main/resources/assets/sh_better_ui/music/apple-login/rate-bridge.mjs';
function fixture() {
  const events = new Map();
  const music = {isAuthorized:true,playbackState:3,playbackRate:1,setQueue:async()=>({}),
    addEventListener:(k,f)=>events.set(k,f),removeEventListener:k=>events.delete(k)};
  Object.defineProperty(music,'musicUserToken',{get(){throw Error('No credential access');}});
  return {music,events,flow:createPlaybackFlow(music,{PlaybackStates:{3:'paused'}},()=>{})};
}
test('rate capability requires a prepared authorized writable official instance',async()=>{
  const {flow,music}=fixture();assert.equal(flow.snapshot().supportsPlaybackRate,false);
  await flow.prepare('123');assert.equal(flow.snapshot().supportsPlaybackRate,true);
  music.isAuthorized=false;assert.equal(flow.snapshot().supportsPlaybackRate,false);
  music.isAuthorized=true;Object.defineProperty(music,'playbackRate',{value:1,writable:false});
  assert.equal(flow.snapshot().supportsPlaybackRate,false);assert.equal(await flow.playbackRate(1.5),false);
});
test('valid rate changes read actual SDK state, not just requested rate',async()=>{
  const {flow,music,events}=fixture();await flow.prepare('123');
  assert.equal(await flow.playbackRate(1.5),true);assert.equal(flow.snapshot().playbackRate,1.5);
  music.playbackRate=.75;events.get('playbackRateDidChange')();assert.equal(flow.snapshot().playbackRate,.75);
  for(const value of [NaN,Infinity,-1,0,.49,2.1,'1.5'])assert.equal(await flow.playbackRate(value),false);
  assert.equal(music.playbackRate,.75);
});
test('ignored/clamped/thrown SDK rates do not acknowledge success',async()=>{
  const {flow,music}=fixture();await flow.prepare('123');
  Object.defineProperty(music,'playbackRate',{get:()=>1,set(){},configurable:true});
  assert.equal(await flow.playbackRate(1.5),false);assert.equal(flow.snapshot().playbackRate,1);
  Object.defineProperty(music,'playbackRate',{get:()=>1,set(){throw Error('sensitive');}});
  assert.equal(await flow.playbackRate(1.5),false);assert.equal(flow.snapshot().message,'FAILED');
});
test('close and pending playback reject rate writes; no audio element mutation',async()=>{
  const {flow,music}=fixture();let finish;music.setQueue=()=>new Promise(r=>finish=r);
  const preparation=flow.prepare('123');assert.equal(await flow.playbackRate(1.5),false);
  finish({});await preparation;flow.close();assert.equal(await flow.playbackRate(1.5),false);assert.equal(music.playbackRate,1);
});
test('bridge applies once and retries lost acknowledgement without repeating SDK write',async()=>{
  let writes=0,posts=0;const messages=[];
  const playback={playbackRate:async rate=>{writes++;assert.equal(rate,1.5);return true;},snapshot:()=>({supportsPlaybackRate:true,playbackRate:1.5})};
  const bridge=createRateBridge(playback,async(method,body)=>{
    if(method==='GET')return {id:1,rate:1.5};messages.push(body);if(++posts===1)throw Error('offline');return {};
  });
  await assert.rejects(bridge.poll());await bridge.poll();assert.equal(writes,1);
  assert.deepEqual(messages[1],{supported:true,rate:1.5,ack:1,status:'APPLIED'});
});
test('bridge rejects bad rate and ignores late GET after close',async()=>{
  const {flow}=fixture();await flow.prepare('123');let payload;
  const bridge=createRateBridge(flow,async(method,body)=>method==='GET'?{id:1,rate:100}:(payload=body,{}));
  await bridge.poll();assert.equal(payload.status,'REJECTED');assert.equal(payload.rate,1);
  let finish,calls=0;const delayed=createRateBridge(flow,()=>{calls++;return new Promise(r=>finish=r);});
  const operation=delayed.poll();delayed.close();finish({id:2,rate:2});await operation;assert.equal(calls,1);
});
