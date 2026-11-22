// Author: dishanhai
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { once } from 'node:events';
import test from 'node:test';
import { createTokenIssuer, createTokenServer } from '../scripts/apple-music-token-service.mjs';

test('the public endpoint issues a short-lived app JWT for the local MusicKit page', async t => {
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
  let now = 1_700_000_000;
  const issue = createTokenIssuer('ABCD123456', 'EFGH123456', privateKey.export({ type: 'pkcs8', format: 'pem' }), () => now);
  const server = createTokenServer(issue).listen(0, '127.0.0.1');
  t.after(() => new Promise(resolve => server.close(resolve)));
  await once(server, 'listening');
  const url = `http://127.0.0.1:${server.address().port}/apple-music/token`;
  const response = await fetch(url);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const token = await response.text();
  const [header, payload, signature] = token.split('.');
  const decode = value => JSON.parse(Buffer.from(value, 'base64url'));
  assert.deepEqual(decode(header), { alg: 'ES256', kid: 'EFGH123456' });
  assert.deepEqual(decode(payload), { iss: 'ABCD123456', iat: now, exp: now + 900, origin: ['http://127.0.0.1:17832'] });
  assert(verify('sha256', Buffer.from(`${header}.${payload}`), { key: publicKey, dsaEncoding: 'ieee-p1363' },
    Buffer.from(signature, 'base64url')));
  assert.equal(await (await fetch(url)).text(), token);
  now += 60;
  assert.notEqual(await (await fetch(url)).text(), token);
  assert.equal((await fetch(`${url}?origin=https://example.invalid`)).status, 404);
  assert.equal((await fetch(url, { method: 'POST' })).status, 405);
});
