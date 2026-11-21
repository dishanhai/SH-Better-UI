// Author: dishanhai
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { createPlaybackFlow } from '../../src/main/resources/assets/sh_better_ui/music/apple-login/playback-flow.mjs';
import { createAuthorizationFlow } from '../../src/main/resources/assets/sh_better_ui/music/apple-login/auth-flow.mjs';
const states = { PlaybackStates: { 0:'none', 1:'loading', 2:'playing', 3:'paused' } };
function fixture() {
  const calls = []; const reports = []; const listeners = new Map();
  const music = { isAuthorized:true, playbackState:0, currentPlaybackTime:0, currentPlaybackDuration:60,
    addEventListener:(event,fn)=>listeners.set(event,fn), removeEventListener:(event,fn)=>{assert.equal(listeners.get(event),fn);listeners.delete(event);},
    setQueue(options){calls.push(['queue', options]);return Promise.resolve({});},
    play(){calls.push(['play']);return Promise.resolve();}, pause(){calls.push(['pause']);return Promise.resolve();},
    seekToTime(time){calls.push(['seek',time]);return Promise.resolve();} };
  Object.defineProperty(music, 'musicUserToken', { get(){ throw Error('Must not access token'); } });
  return { music, calls, reports, listeners, flow:createPlaybackFlow(music, states, value=>reports.push(value)) };
}
test('never autoplays on construction or queue completion; Play calls SDK synchronously', async()=>{
  const {flow,calls}=fixture();assert.deepEqual(calls,[]);
  await flow.prepare('123');assert.deepEqual(calls,[['queue',{song:'123',startPlaying:false}]]);
  const operation=flow.play();assert.equal(calls.at(-1)[0],'play');await operation;
  assert.equal(flow.snapshot().state,'none'); // resolved SDK call is not proof of playback
});
test('SDK events, not command promises, drive playback state and time',async()=>{
  const {flow,music,listeners,reports}=fixture();await flow.prepare('123');await flow.play();
  music.playbackState=2;music.currentPlaybackTime=4.5;listeners.get('playbackStateDidChange')();
  assert.equal(reports.at(-1).state,'playing');assert.equal(reports.at(-1).seconds,4.5);
  await flow.pause();assert.equal(flow.snapshot().state,'playing');
  music.playbackState=3;listeners.get('playbackStateDidChange')();assert.equal(flow.snapshot().state,'paused');
  await flow.seek(12);assert.equal(flow.snapshot().seconds,4.5);
});
test('requires auth and valid numeric song IDs; rejects unsupported queues',async()=>{
  const {flow,music,calls}=fixture();music.isAuthorized=false;await flow.prepare('123');assert.deepEqual(calls,[]);
  music.isAuthorized=true;for(const id of ['','https://music.apple.com/song/123','12x'])await flow.prepare(id);
  assert.deepEqual(calls,[]);music.setQueue=async()=>undefined;await flow.prepare('123');
  assert.equal(flow.snapshot().ready,false);assert.equal(flow.snapshot().message,'FAILED');await flow.play();assert.deepEqual(calls,[]);
});
test('repeated inputs cannot overlap a queue load or issue playback early',async()=>{
  const {flow,music,calls}=fixture();let finish;
  music.setQueue=()=>new Promise(resolve=>{finish=resolve;calls.push(['queue']);});
  const operation=flow.prepare('123');await flow.prepare('456');await flow.play();assert.deepEqual(calls,[['queue']]);
  finish({});await operation;assert.equal(flow.snapshot().ready,true);
});
test('seek uses SDK seconds and rejects unavailable/out-of-range timeline',async()=>{
  const {flow,calls,music}=fixture();await flow.prepare('123');
  for(const time of [-1,NaN,Infinity,61])await flow.seek(time);
  assert.equal(calls.length,1);await flow.seek(12.5);assert.deepEqual(calls.at(-1),['seek',12.5]);
  music.currentPlaybackDuration=0;await flow.seek(0);assert.equal(calls.length,2);
});
test('authorization remains separate; signout invalidates readiness and does not relogin',async()=>{
  const {flow,music,calls,listeners}=fixture();await flow.prepare('123');
  music.unauthorize=async()=>{music.isAuthorized=false;listeners.get('authorizationStatusDidChange')();};
  const auth=createAuthorizationFlow(music,()=>{});await auth.disconnect();
  assert.equal(auth.state,'SIGNED_OUT');assert.equal(flow.snapshot().ready,false);await flow.play();assert.equal(calls.length,1);
});
test('errors are sanitized and close removes listeners, including late completions',async()=>{
  const {flow,music,listeners,reports}=fixture();await flow.prepare('123');
  music.play=async()=>{throw Error('private-token');};await flow.play();
  assert.equal(flow.snapshot().message,'FAILED');assert.ok(!JSON.stringify(reports).includes('private-token'));
  let finish;music.pause=()=>new Promise(resolve=>{finish=resolve;});const pending=flow.pause();
  flow.close();const length=reports.length;finish();await pending;assert.equal(listeners.size,0);assert.equal(reports.length,length);
});
test('companion serves playback module and reports only account state and optional storefront',async()=>{
  const base=new URL('../../',import.meta.url);
  const login=await readFile(new URL('src/main/resources/assets/sh_better_ui/music/apple-login/login.mjs',base),'utf8');
  const server=await readFile(new URL('src/main/java/com/dishanhai/sh_better_ui/kernel/api/music/platform/AppleMusicBrowserLogin.java',base),'utf8');
  assert.match(login,/playSong\.addEventListener\('click'/);assert.match(login,/JSON.stringify\(\{ state, storefront \}\)/);
  assert.match(server,/case "\/playback-flow.mjs" -> "playback-flow.mjs"/);
});

test('new game selections prefill only once and never issue playback commands', async()=>{
  const login=await readFile(new URL('../../src/main/resources/assets/sh_better_ui/music/apple-login/login.mjs',import.meta.url),'utf8');
  const start=login.indexOf('let selectionRevision = 0;');
  const end=login.indexOf('\nconst playbackLabels',start);
  assert.ok(start>=0 && end>start);
  const input={value:''};
  const select=runInNewContext(`${login.slice(start,end)}\napplySelection`,
    {document:{getElementById:id=>{assert.equal(id,'song-id');return input;}}});
  select({catalogId:'123',selectionRevision:1});assert.equal(input.value,'123');
  input.value='player typing';
  select({catalogId:'123',selectionRevision:1});assert.equal(input.value,'player typing');
  select({catalogId:'999',selectionRevision:0});assert.equal(input.value,'player typing');
  select({catalogId:'bad',selectionRevision:2});assert.equal(input.value,'player typing');
  select({catalogId:'456',selectionRevision:2});assert.equal(input.value,'456');
  assert.match(login,/applySelection\(config\)/);
  assert.match(login,/applySelection\(await response\.json\(\)\)/);
});

test('SDK media error is retained even when the command resolves successfully',async()=>{
  const {flow,music,listeners}=fixture();await flow.prepare('123');
  music.play=async()=>{listeners.get('mediaPlaybackError')({message:'private'});};
  await flow.play();assert.equal(flow.snapshot().message,'FAILED');
});
