import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, verify as signatureValid } from 'node:crypto';
import { createAccessCodeVerifier, validateCodeManifest, validAccessCodeRequest } from '../src/access-codes.js';
import { createGcsCodeReservations } from '../src/code-redemptions.js';
import { createVerificationServer } from '../src/server.js';

const hash = value => createHash('sha256').update(value).digest('hex');
const family = 'FAMILY-synthetic-test-code-not-for-production';
const codes = Array.from({ length: 100 }, (_, i) => `LAUNCH-synthetic-test-code-${String(i).padStart(8, '0')}`);
const manifest = { schemaVersion: 1, campaignId: 'launch-20261027', offerId: 'launch-2m-half',
  familyCodeSha256: hash(family), discountCodeSha256: codes.map(hash) };
const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const id = i => `10000000-0000-4000-8000-${String(i).padStart(12, '0')}`;
const request = (code, i = 1) => ({ packageName: 'com.moa.babylanguage', installationId: id(i), code });
function memoryReservations() {
  const objects = new Map();
  return { objects, reserve: async ({ codeHash, installationHash }) => {
    if (!objects.has(codeHash)) objects.set(codeHash, installationHash);
    return objects.get(codeHash) === installationHash;
  } };
}
const claims = envelope => JSON.parse(Buffer.from(envelope.payload, 'base64url'));

test('family access has no campaign end, is signed, device-bound and refreshes only a 24h lease', async () => {
  let now = 1_800_000_000_000;
  const reserve = memoryReservations();
  const check = createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: reserve.reserve, clock: () => now });
  const first = await check(request(family));
  assert.equal(signatureValid('sha256', Buffer.from(first.payload, 'base64url'), publicKey, Buffer.from(first.signature, 'base64url')), true);
  assert.equal(claims(first).grantSource, 'invitation');
  assert.equal(claims(first).installationId, id(1));
  assert.equal(claims(first).expiresAtMs - now, 86_400_000);
  now += 365 * 86_400_000;
  assert.equal(claims(await check(request(family, 2))).issuedAtMs, now);
  assert.equal(reserve.objects.size, 0);
});

test('a discount grant contains the approved terms and never an invitation/purchase grant', async () => {
  const check = createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: memoryReservations().reserve });
  const grant = claims(await check(request(codes[0])));
  assert.equal(grant.grantSource, 'launch-offer');
  assert.equal(grant.introductoryPriceMicros, 2_450_000_000);
  assert.equal(grant.introductoryCycles, 2);
  assert.equal(grant.renewalPriceMicros, 4_900_000_000);
  assert.equal(grant.offerId, 'launch-2m-half');
  assert.equal(grant.currency, 'KRW');
  assert.equal(grant.billingPeriod, 'P1M');
  assert.equal(grant.code, undefined);
});

test('one discount code can bind only one installation, including concurrent requests and restart', async () => {
  const reserve = memoryReservations();
  const check = createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: reserve.reserve });
  const results = await Promise.all(Array.from({ length: 12 }, (_, i) => check(request(codes[0], i + 1))));
  assert.equal(results.filter(Boolean).length, 1);
  const restarted = createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: reserve.reserve });
  assert.ok(await restarted(request(codes[0], 1)));
  assert.equal(await restarted(request(codes[0], 2)), null);
});

test('exactly 100 issued discount codes are accepted and invented codes cannot increase the quota', async () => {
  const reserve = memoryReservations();
  const check = createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: reserve.reserve });
  for (let i = 0; i < 100; i++) assert.ok(await check(request(codes[i], i + 1)));
  assert.equal(await check(request('LAUNCH-synthetic-test-code-00000100', 101)), null);
  assert.equal(reserve.objects.size, 100);
});

test('wrong package, extra fields, whitespace, malformed installation and unknown code are rejected before reservation', async () => {
  const reserve = memoryReservations();
  const check = createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: reserve.reserve });
  for (const input of [ { ...request(family), packageName: 'another.package' },
    { ...request(family), paid: true }, { ...request(family), installationId: 'wrong' },
    request('short'), request(' ' + family), request('UNKNOWN-synthetic-test-code-00000000') ]) {
    assert.equal(await check(input), null);
  }
  assert.equal(validAccessCodeRequest(null), false);
  assert.equal(reserve.objects.size, 0);
});

test('invalid manifests and missing durable storage fail closed', () => {
  for (const bad of [null, {}, { ...manifest, discountCodeSha256: codes.slice(0, 99).map(hash) },
    { ...manifest, discountCodeSha256: Array(100).fill(hash(codes[0])) },
    { ...manifest, offerId: 'different-offer' }]) assert.throws(() => validateCodeManifest(bad));
  assert.throws(() => createAccessCodeVerifier({ privateKey, manifest }));
});

test('disabled configuration and revocation do not mint an entitlement', async () => {
  assert.equal(await createAccessCodeVerifier({ privateKey })(request(family)), null);
  const replacement = { ...manifest, familyCodeSha256: hash('OTHER-synthetic-replacement-code-0000000') };
  assert.equal(await createAccessCodeVerifier({ privateKey, manifest: replacement,
    reserveDiscount: memoryReservations().reserve })(request(family)), null);
});

test('storage failures and canceled requests never return a grant', async () => {
  const check = createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: async () => { throw new Error('offline'); } });
  await assert.rejects(check(request(codes[0])));
  const controller = new AbortController(); controller.abort();
  await assert.rejects(check(request(family), { signal: controller.signal }));
});

test('GCS reservations use generation-zero creation; retries read the immutable binding without overwriting', async () => {
  const entries = new Map(); let creates = 0; let metadata = 0;
  const fetchImpl = async (url, options) => {
    if (url.startsWith('http://metadata.')) { metadata++; return Response.json({ access_token: 'test-only', expires_in: 3600 }); }
    assert.equal(options.redirect, 'error');
    assert.equal(options.headers.authorization, 'Bearer test-only');
    if (options.method === 'POST') {
      const parsed = new URL(url);
      assert.equal(parsed.searchParams.get('ifGenerationMatch'), '0');
      const name = parsed.searchParams.get('name');
      const value = JSON.parse(options.body);
      assert.equal(value.code, undefined);
      assert.equal(value.installationId, undefined);
      if (entries.has(name)) return new Response('', { status: 412 });
      creates++; entries.set(name, value); return Response.json({ generation: '1' });
    }
    return Response.json(entries.get(decodeURIComponent(new URL(url).pathname.split('/o/')[1])));
  };
  const reserve = createGcsCodeReservations({ bucket: 'test-redemptions', fetchImpl });
  const base = { codeHash: hash(codes[0]), installationHash: hash(id(1)), campaignId: 'launch-20261027' };
  assert.equal(await reserve(base), true);
  assert.equal(await reserve(base), true);
  assert.equal(await reserve({ ...base, installationHash: hash(id(2)) }), false);
  assert.equal(creates, 1); assert.equal(metadata, 1);
});

test('storage permission/outage errors do not masquerade as a used or accepted code', async () => {
  const reserve = createGcsCodeReservations({ bucket: 'test-redemptions', fetchImpl: async url =>
    url.startsWith('http://metadata.') ? Response.json({ access_token: 'test-only', expires_in: 3600 }) : new Response('', { status: 403 }) });
  await assert.rejects(reserve({ codeHash: hash(codes[0]), installationHash: hash(id(1)), campaignId: 'launch-20261027' }));
});

test('HTTP code route preserves purchase/review isolation and rejects malformed requests', async () => {
  const server = createVerificationServer({ verify: async () => null,
    accessCode: createAccessCodeVerifier({ privateKey, manifest, reserveDiscount: memoryReservations().reserve }) });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const post = (path, body) => fetch(origin + path, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  try {
    assert.equal((await post('/access-code', {})).status, 400);
    const result = await post('/access-code', request(family));
    assert.equal(result.status, 200);
    assert.equal(claims((await result.json()).entitlement).grantSource, 'invitation');
    assert.equal((await post('/verify', request(family))).status, 400);
    assert.equal((await post('/review', { packageName: 'com.moa.babylanguage', installationId: id(1), reviewCode: family })).status, 403);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});
