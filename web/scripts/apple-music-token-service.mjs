// Author: dishanhai
import { createPrivateKey, sign } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

const origin = 'http://127.0.0.1:17832';
const lifetimeSeconds = 15 * 60;
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');

export function createTokenIssuer(teamId, keyId, pem, clock = () => Math.floor(Date.now() / 1000)) {
  if (!/^[A-Z0-9]{10}$/.test(teamId) || !/^[A-Z0-9]{10}$/.test(keyId) || !pem) {
    throw new Error('MusicKit signing settings are incomplete');
  }
  const key = createPrivateKey(pem);
  if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails.namedCurve !== 'prime256v1') {
    throw new Error('MusicKit requires a P-256 private key');
  }
  const header = encode({ alg: 'ES256', kid: keyId });
  let token, issuedAt;
  return () => {
    const now = clock();
    if (token && now >= issuedAt && now - issuedAt < 60) return token;
    const payload = encode({ iss: teamId, iat: now, exp: now + lifetimeSeconds, origin: [origin] });
    const content = `${header}.${payload}`;
    const signature = sign('sha256', Buffer.from(content), { key, dsaEncoding: 'ieee-p1363' });
    token = `${content}.${signature.toString('base64url')}`;
    issuedAt = now;
    return token;
  };
}

export function createTokenServer(issue) {
  return createServer((request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('Content-Type', 'text/plain; charset=us-ascii');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    if (request.url !== '/apple-music/token') {
      response.writeHead(404).end();
    } else if (request.method !== 'GET') {
      response.setHeader('Allow', 'GET');
      response.writeHead(405).end();
    } else {
      try { response.end(issue()); }
      catch { response.writeHead(503).end(); }
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { SHBUI_APPLE_MUSIC_TEAM_ID, SHBUI_APPLE_MUSIC_KEY_ID, SHBUI_APPLE_MUSIC_PRIVATE_KEY_FILE } = process.env;
    const issue = createTokenIssuer(SHBUI_APPLE_MUSIC_TEAM_ID, SHBUI_APPLE_MUSIC_KEY_ID,
      readFileSync(SHBUI_APPLE_MUSIC_PRIVATE_KEY_FILE));
    createTokenServer(issue).listen(17833, '127.0.0.1', () => console.log('MusicKit token service listening'));
  } catch {
    console.error('Unable to start the MusicKit token service. Check the project signing settings.');
    process.exitCode = 1;
  }
}
