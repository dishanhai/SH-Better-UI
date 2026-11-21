// Author: dishanhai
import test from 'node:test';
import assert from 'node:assert/strict';
import { createAuthorizationFlow } from '../../src/main/resources/assets/sh_better_ui/music/apple-login/auth-flow.mjs';
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b; }); return { promise, resolve, reject }; };

test('official authorize is invoked synchronously within the click and success requires SDK flag', async () => {
  const states = []; let calls = 0;
  const sdk = { isAuthorized:false, authorize() { calls++; this.isAuthorized=true; return Promise.resolve('secret-must-not-be-reported'); } };
  const flow = createAuthorizationFlow(sdk, state => states.push(state));
  const pending = flow.connect();
  assert.equal(calls, 1);
  await pending;
  assert.deepEqual(states, ['AUTHORIZING', 'AUTHORIZED']);
  assert.equal(flow.busy, false);
});
test('resolved cancellation is not mistaken for authorization', async () => {
  const flow = createAuthorizationFlow({isAuthorized:false, authorize:async()=>undefined}, ()=>{});
  await flow.connect(); assert.equal(flow.state, 'CANCELLED');
});
test('repeated click does not open two authorization windows', async () => {
  const wait = deferred(); let count = 0;
  const sdk = {isAuthorized:false, authorize(){count++;return wait.promise;}};
  const flow = createAuthorizationFlow(sdk, ()=>{});
  const first = flow.connect(); await flow.connect(); assert.equal(count,1);
  wait.resolve(); await first;
});
test('cancel then late authorization disconnects and does not report success', async () => {
  const wait = deferred(); const states=[]; let disconnects=0;
  const sdk = {isAuthorized:false,authorize:()=>wait.promise,async unauthorize(){disconnects++;this.isAuthorized=false;}};
  const flow = createAuthorizationFlow(sdk, state=>states.push(state));
  const pending = flow.connect(); flow.cancel();
  assert.equal(flow.busy,true); // Do not allow a new login to race the previous cleanup.
  sdk.isAuthorized=true;wait.resolve('private-token');await pending;
  assert.equal(disconnects,1);assert.equal(flow.authorized,false);
  assert.deepEqual(states,['AUTHORIZING','CANCELLED']);
});
test('late cleanup failure remains visible and allows explicit disconnect', async () => {
  const wait=deferred();const sdk={isAuthorized:false,authorize:()=>wait.promise,async unauthorize(){throw Error('secret');}};
  const flow=createAuthorizationFlow(sdk,()=>{});const pending=flow.connect();flow.cancel();sdk.isAuthorized=true;wait.resolve();await pending;
  assert.equal(flow.state,'FAILED');assert.equal(flow.authorized,true);assert.equal(flow.busy,false);
});
test('disconnect invokes official unauthorize and only then reports success', async()=>{
  const sdk={isAuthorized:true,async unauthorize(){this.isAuthorized=false;}};
  const flow=createAuthorizationFlow(sdk,()=>{});await flow.disconnect();
  assert.equal(flow.state,'SIGNED_OUT');assert.equal(flow.authorized,false);
});
test('errors report no error details or credentials',async()=>{
  const states=[];const flow=createAuthorizationFlow({isAuthorized:false,async authorize(){throw Error('sensitive-secret');}},s=>states.push(s));
  await flow.connect();assert.deepEqual(states,['AUTHORIZING','FAILED']);
});

test('login and restored authorization read the personal storefront without reporting other data', async () => {
  for (const restored of [false, true]) {
    const reports = [], paths = [];
    const sdk = {
      isAuthorized: restored,
      async authorize() { assert.equal(restored, false); this.isAuthorized = true; return 'private-token'; },
      api: { async music(path) {
        paths.push(path);
        return { data: { data: [{ type: 'storefronts', id: 'tw', personal: 'private-data' }] } };
      } }
    };
    const flow = createAuthorizationFlow(sdk, (state, storefront) => reports.push({ state, storefront }));
    if (restored) flow.observe();
    else await flow.connect();
    await Promise.resolve();
    assert.deepEqual(paths, ['/v1/me/storefront']);
    assert.deepEqual(reports.filter(report => report.state === 'AUTHORIZED'), [
      { state: 'AUTHORIZED', storefront: undefined }, { state: 'AUTHORIZED', storefront: 'tw' }
    ]);
  }
});

test('a storefront reply arriving after the SDK signs out is discarded', async () => {
  const pending = deferred(), reports = [];
  const sdk = { isAuthorized: true, api: { music: () => pending.promise } };
  const flow = createAuthorizationFlow(sdk, (...args) => reports.push(args));
  flow.observe();
  sdk.isAuthorized = false;
  flow.observe();
  pending.resolve({ data: { data: [{ type: 'storefronts', id: 'us' }] } });
  await Promise.resolve();
  assert.deepEqual(reports, [['AUTHORIZED'], ['SIGNED_OUT']]);
});
