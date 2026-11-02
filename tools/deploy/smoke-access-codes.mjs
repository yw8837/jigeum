import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createPublicKey, verify, randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { resolve, basename } from 'node:path';

const [directory, publicKeyFile, output] = process.argv.slice(2);
if (basename(resolve(directory ?? '')) !== 'qa') throw new Error('This test may reserve only the separate QA campaign codes');
const base = 'https://jigeum-verifier-th7ce7roma-du.a.run.app';
const key = createPublicKey({ key: Buffer.from(readFileSync(publicKeyFile, 'utf8').trim(), 'base64'), type: 'spki', format: 'der' });
const invitation = readFileSync(resolve(directory, 'friends-private.txt'), 'utf8').split(/\r?\n/)[1];
const discount = readFileSync(resolve(directory, 'launch-100-private.csv'), 'utf8').split(/\r?\n/)[2].split(',')[1];
const deviceFile = resolve(directory, 'smoke-installation.txt');
if (!existsSync(deviceFile)) writeFileSync(deviceFile, randomUUID(), { flag: 'wx', mode: 0o600 });
const device = readFileSync(deviceFile, 'utf8').trim();
const checks = [];
async function request(code, installationId, expected, kind, path = '/access-code') {
  const response = await fetch(base + path, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ packageName: 'com.moa.babylanguage', installationId, code }), signal: AbortSignal.timeout(30000) });
  assert.equal(response.status, expected);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const body = await response.json();
  if (expected === 200) {
    const { payload, signature } = body.entitlement;
    assert(verify('sha256', Buffer.from(payload, 'base64url'), key, Buffer.from(signature, 'base64url')));
    const claims = JSON.parse(Buffer.from(payload, 'base64url'));
    assert.equal(claims.installationId, installationId); assert.equal(claims.grantSource, kind);
    assert.equal(claims.expiresAtMs - claims.issuedAtMs, 86400000);
    if (kind === 'launch-offer') { assert.equal(claims.introductoryCycles, 2); assert.equal(claims.introductoryPriceMicros, 2450000000); assert.equal(claims.renewalPriceMicros, 4900000000); }
  }
}
await request(invitation, device, 200, 'invitation'); checks.push('invitation signed with production public key');
await request(invitation, randomUUID(), 200, 'invitation'); checks.push('shared invitation works on another installation');
await request(discount, device, 200, 'launch-offer'); checks.push('QA discount first reservation and exact prices');
await request(discount, device, 200, 'launch-offer'); checks.push('same installation idempotent');
await request(discount, randomUUID(), 403); checks.push('different installation rejected');
await request('synthetic-unissued-code-00000000000000000', randomUUID(), 403); checks.push('unknown code rejected');
await request(invitation, device, 400, undefined, '/review'); checks.push('invitation is not Play review access');
writeFileSync(output, JSON.stringify({ checkedAt: new Date().toISOString(), base, passed: checks.length, checks,
  customerCodesConsumed: 0, realPlayPurchaseTested: false, privateValuesLogged: false }, null, 2));
console.log(JSON.stringify({ passed: checks.length, output, customerCodesConsumed: 0 }));
