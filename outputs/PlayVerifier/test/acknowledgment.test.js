import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, verify as verifySignature } from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';
import { createVerifier } from '../src/verifier.js';
import { createVerificationServer } from '../src/server.js';

const now = Date.parse('2026-09-30T00:00:00Z');
const ec = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 });
const serviceAccount = { client_email: 'ack-qa@example.invalid', private_key: rsa.privateKey.export({ type: 'pkcs8', format: 'pem' }) };
const input = { packageName: 'com.moa.babylanguage', productId: 'jigeum_album', purchaseToken: 'owned-token/?x=1', installationId: '94be8be6-04c9-4b85-9994-69113bf442d0' };
const active = (acknowledgementState = 'ACKNOWLEDGEMENT_STATE_PENDING') => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', acknowledgementState,
  lineItems: [{ productId: input.productId, expiryTime: new Date(now + 86400000).toISOString(), offerDetails: { basePlanId: 'monthly' }, autoRenewingPlan: {} }]
});
const configured = options => createVerifier({ serviceAccount, privateKey: ec.privateKey, clock: () => now, ...options });
const tokenResponse = () => new Response(JSON.stringify({ access_token: 'LOCAL_TEST_ONLY', expires_in: 3600 }));
const mock = (lookup, acknowledge) => async (url, options) => {
  if (url.includes('oauth2.googleapis.com')) return tokenResponse();
  if (url.endsWith(':acknowledge')) return acknowledge(url, options);
  return new Response(JSON.stringify(await lookup(url, options)));
};

test('pending verified purchase is acknowledged before the signed response, using an empty request body', async () => {
  let release;
  let writes = 0;
  const verifier = configured({ fetchImpl: mock(() => active(), async (url, options) => {
    writes++;
    assert.match(url, /purchases\/subscriptions\/jigeum_album\/tokens\/owned-token%2F%3Fx%3D1:acknowledge$/);
    assert.equal(options.method, 'POST');
    assert.equal(options.body, '{}');
    assert.equal(options.headers.authorization, 'Bearer LOCAL_TEST_ONLY');
    assert.equal(options.headers['content-type'], 'application/json');
    assert.ok(options.signal instanceof AbortSignal);
    await new Promise(resolve => { release = resolve; });
    return new Response(null, { status: 200 });
  }) });
  let completed = false;
  const pending = verifier(input).then(value => { completed = true; return value; });
  await pause(20);
  assert.equal(writes, 1);
  assert.equal(completed, false);
  release();
  const envelope = await pending;
  assert.ok(verifySignature('sha256', Buffer.from(envelope.payload, 'base64url'), ec.publicKey, Buffer.from(envelope.signature, 'base64url')));
  const claims = JSON.parse(Buffer.from(envelope.payload, 'base64url'));
  assert.equal(claims.acknowledgedTokenHash, createHash('sha256').update(input.purchaseToken).digest('hex'));
  assert.equal(JSON.stringify(claims).includes(input.purchaseToken), false);
});

test('already acknowledged purchases and ineligible purchases never produce acknowledgment writes', async () => {
  for (const result of [active('ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'),
    { ...active(), subscriptionState: 'SUBSCRIPTION_STATE_PENDING' },
    { ...active(), subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED' },
    { ...active(), lineItems: [{ ...active().lineItems[0], productId: 'other' }] }]) {
    let writes = 0;
    const verifier = configured({ fetchImpl: mock(() => result, () => { writes++; throw new Error('unexpected acknowledgment'); }) });
    const grant = await verifier(input);
    assert.equal(Boolean(grant), result.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED');
    assert.equal(writes, 0);
  }
});

test('missing and unknown acknowledgement states fail without signing or writing', async () => {
  for (const acknowledgementState of [undefined, null, '', 'ACKNOWLEDGEMENT_STATE_UNSPECIFIED', 'ACKNOWLEDGED']) {
    let writes = 0;
    const subscription = active(acknowledgementState);
    if (acknowledgementState === undefined) delete subscription.acknowledgementState;
    const verifier = configured({ fetchImpl: mock(() => subscription, () => { writes++; return new Response(null); }) });
    await assert.rejects(verifier(input), /acknowledgment.*unavailable/i);
    assert.equal(writes, 0);
  }
});

test('twenty concurrent callers share a single lookup and acknowledgment but receive installation bound grants', async () => {
  let reads = 0, writes = 0;
  const verifier = configured({ fetchImpl: mock(async () => { reads++; await pause(10); return active(); }, async () => { writes++; await pause(10); return new Response(null); }) });
  const ids = [input.installationId, 'd4efbfe1-7a41-485a-bda9-7e674bc24850'];
  const results = await Promise.all(Array.from({ length: 20 }, (_, i) => verifier({ ...input, installationId: ids[i % 2] })));
  assert.equal(reads, 1); assert.equal(writes, 1);
  results.forEach((value, i) => assert.equal(JSON.parse(Buffer.from(value.payload, 'base64url')).installationId, ids[i % 2]));
});

test('acknowledgment failures fail temporarily, then a fresh Play query observes completion without another write', async () => {
  for (const status of [400, 401, 403, 409, 429, 500, 503]) {
    let completed = false, reads = 0, writes = 0;
    const verifier = configured({ fetchImpl: mock(() => { reads++; return active(completed ? 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' : 'ACKNOWLEDGEMENT_STATE_PENDING'); }, () => { writes++; return new Response('{}', { status }); }) });
    await assert.rejects(verifier(input), /acknowledgment.*unavailable/i);
    completed = true;
    assert.ok(await verifier(input));
    assert.equal(reads, 2); assert.equal(writes, 1);
  }
});

test('hung acknowledgment has a timeout and releases the shared request for a later successful retry', async () => {
  let hang = true;
  const verifier = configured({ upstreamTimeoutMs: 30, fetchImpl: mock(() => active(), (_, { signal }) => {
    if (!hang) return new Response(null);
    return new Promise((_, reject) => signal.addEventListener('abort', () => reject(new Error('owned timeout')), { once: true }));
  }) });
  const keepAlive = setTimeout(() => {}, 1000);
  try { await assert.rejects(verifier(input), /timeout/); } finally { clearTimeout(keepAlive); }
  hang = false; assert.ok(await verifier(input));
});

test('expiry during acknowledgment denies the later signed response', async () => {
  let clock = now;
  const verifier = configured({ clock: () => clock, fetchImpl: mock(() => active(), () => { clock += 86400000; return new Response(null); }) });
  assert.equal(await verifier(input), null);
});

test('actual localhost adapter returns 503 on acknowledgment failure and recovers with a signed token-free 200 response', async () => {
  let status = 503;
  const verify = configured({ fetchImpl: mock(() => active(), () => new Response(null, { status })) });
  const server = createVerificationServer({ verify });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/verify`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(input) });
    assert.equal(response.status, 503);
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { error: 'verification_unavailable' });
    assert.equal(body.includes(input.purchaseToken), false);
    status = 200;
    const recovered = await fetch(`http://127.0.0.1:${server.address().port}/verify`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(input) });
    assert.equal(recovered.status, 200);
    const envelope = (await recovered.json()).entitlement;
    assert.ok(verifySignature('sha256', Buffer.from(envelope.payload, 'base64url'), ec.publicKey, Buffer.from(envelope.signature, 'base64url')));
    const claims = JSON.parse(Buffer.from(envelope.payload, 'base64url'));
    assert.equal(claims.acknowledgedTokenHash, createHash('sha256').update(input.purchaseToken).digest('hex'));
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});

test('one cancelled caller receives no grant while another caller completes the shared acknowledgment', async () => {
  let release, writes = 0;
  const verifier = configured({ fetchImpl: mock(() => active(), async () => {
    writes++; await new Promise(resolve => { release = resolve; }); return new Response(null);
  }) });
  const controller = new AbortController();
  const interrupted = verifier(input, { signal: controller.signal });
  const observer = verifier(input);
  const denied = assert.rejects(interrupted, /abort/i);
  await pause(20);
  controller.abort(); release();
  await denied;
  assert.ok(await observer);
  assert.equal(writes, 1);
});
