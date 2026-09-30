import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify as verifySignature } from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';
import { createVerifier, selectEntitlement, signEntitlement } from '../src/verifier.js';

const now = Date.parse('2026-09-30T00:00:00Z');
const installationId = '94be8be6-04c9-4b85-9994-69113bf442d0';
const ec = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 });
const subscription = (state = 'SUBSCRIPTION_STATE_ACTIVE', basePlanId = 'monthly', expiry = now + 5 * 86_400_000) => ({
  subscriptionState: state,
  lineItems: [{
    productId: 'jigeum_album',
    expiryTime: new Date(expiry).toISOString(),
    offerDetails: { basePlanId },
    ...(basePlanId === 'monthly' ? { autoRenewingPlan: { autoRenewEnabled: true } } : { prepaidPlan: {} })
  }]
});

test('only active, grace and unexpired canceled subscriptions are entitled', () => {
  for (const state of ['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED']) {
    assert.equal(selectEntitlement(subscription(state), now)?.basePlanId, 'monthly');
  }
  for (const state of ['SUBSCRIPTION_STATE_ON_HOLD', 'SUBSCRIPTION_STATE_PAUSED', 'SUBSCRIPTION_STATE_PENDING', 'SUBSCRIPTION_STATE_EXPIRED']) {
    assert.equal(selectEntitlement(subscription(state), now), null);
  }
  assert.equal(selectEntitlement(subscription('SUBSCRIPTION_STATE_ACTIVE', 'monthly', now), now), null);
  assert.equal(selectEntitlement(subscription('SUBSCRIPTION_STATE_ACTIVE', 'unknown'), now), null);
  assert.equal(selectEntitlement(subscription('SUBSCRIPTION_STATE_ACTIVE', 'three-month-prepaid'), now)?.basePlanId, 'three-month-prepaid');
});

test('signed entitlement is bound to installation and expires within 24 hours', () => {
  const envelope = signEntitlement({ installationId, basePlanId: 'monthly', subscriptionExpiryMs: now + 3_600_000 }, ec.privateKey, now);
  const payload = Buffer.from(envelope.payload, 'base64url');
  const claims = JSON.parse(payload);
  assert.equal(claims.installationId, installationId);
  assert.equal(claims.expiresAtMs, now + 3_600_000);
  assert.equal(verifySignature('sha256', payload, ec.publicKey, Buffer.from(envelope.signature, 'base64url')), true);
});

test('verification queries Play and refuses forged, expired and mismatched purchases', async () => {
  let result = subscription();
  let calls = 0;
  const fetchImpl = async (url, options) => {
    calls++;
    assert.equal(options.signal instanceof AbortSignal, true);
    if (url.includes('oauth2.googleapis.com')) return new Response(JSON.stringify({ access_token: 'test-access', expires_in: 3600 }), { status: 200 });
    assert.match(url, /subscriptionsv2\/tokens\/valid-token$/);
    assert.equal(options.headers.authorization, 'Bearer test-access');
    return new Response(JSON.stringify(result), { status: 200 });
  };
  const verifier = createVerifier({
    serviceAccount: { client_email: 'test@example.invalid', private_key: rsa.privateKey.export({ type: 'pkcs8', format: 'pem' }) },
    privateKey: ec.privateKey, fetchImpl, clock: () => now
  });
  const request = { packageName: 'com.moa.babylanguage', productId: 'jigeum_album', purchaseToken: 'valid-token', installationId };
  assert.ok(await verifier(request));
  assert.equal(calls, 2);
  result = subscription('SUBSCRIPTION_STATE_ON_HOLD');
  assert.equal(await verifier(request), null);
  assert.equal(calls, 3);
  result = subscription();
  assert.equal(await verifier({ ...request, packageName: 'other.package' }), null);
  assert.equal(await verifier({ ...request, installationId: 'not-uuid' }), null);
  assert.equal(calls, 3);
});

const request = { packageName: 'com.moa.babylanguage', productId: 'jigeum_album', purchaseToken: 'valid-token', installationId };
const serviceAccount = { client_email: 'test@example.invalid', private_key: rsa.privateKey.export({ type: 'pkcs8', format: 'pem' }) };
const configured = options => createVerifier({ serviceAccount, privateKey: ec.privateKey, clock: () => now, ...options });
const tokenResponse = () => new Response(JSON.stringify({ access_token: 'test-access', expires_in: 3600 }), { status: 200 });

test('malformed upstream line items and wrong product/plan type cannot unlock', () => {
  for (const value of [null, {}, { lineItems: 'bad' }, { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', lineItems: [null, {}, 'bad'] }]) {
    assert.equal(selectEntitlement(value, now), null);
  }
  const wrongProduct = subscription(); wrongProduct.lineItems[0].productId = 'other';
  assert.equal(selectEntitlement(wrongProduct, now), null);
  const wrongType = subscription(); delete wrongType.lineItems[0].autoRenewingPlan;
  wrongType.lineItems[0].prepaidPlan = {};
  assert.equal(selectEntitlement(wrongType, now), null);
});

test('20 concurrent duplicate requests share one OAuth and Play lookup; next request sees revocation', async () => {
  let oauth = 0, lookups = 0, revoked = false;
  const verifier = configured({ fetchImpl: async url => {
    await pause(15);
    if (url.includes('oauth2.googleapis.com')) { oauth++; return tokenResponse(); }
    lookups++;
    return new Response(JSON.stringify(subscription(revoked ? 'SUBSCRIPTION_STATE_EXPIRED' : undefined)), { status: 200 });
  } });
  const results = await Promise.all(Array.from({ length: 20 }, () => verifier(request)));
  assert.equal(results.every(Boolean), true);
  assert.equal(oauth, 1);
  assert.equal(lookups, 1);
  revoked = true;
  assert.equal(await verifier(request), null);
  assert.equal(lookups, 2);
});

test('OAuth refresh is single-flight across different purchases', async () => {
  let oauth = 0;
  const verifier = configured({ fetchImpl: async url => {
    await pause(5);
    if (url.includes('oauth2.googleapis.com')) { oauth++; return tokenResponse(); }
    return new Response(JSON.stringify(subscription()), { status: 200 });
  } });
  const results = await Promise.all(Array.from({ length: 20 }, (_, i) => verifier({ ...request, purchaseToken: `token-${i}-valid` })));
  assert.equal(results.every(Boolean), true);
  assert.equal(oauth, 1);
});

test('Google denial is distinguished from permission, quota and outage errors', async () => {
  let status = 404;
  const verifier = configured({ fetchImpl: async url => url.includes('oauth2.googleapis.com') ? tokenResponse() : new Response('{}', { status }) });
  for (const code of [400, 404, 410]) { status = code; assert.equal(await verifier(request), null); }
  for (const code of [401, 403, 429, 500, 503]) { status = code; await assert.rejects(verifier(request), /unavailable/); }
});

test('upstream timeout is bounded, cleaned up and next call recovers', async () => {
  let hang = true;
  const verifier = configured({ upstreamTimeoutMs: 30, fetchImpl: async (url, { signal }) => {
    if (!hang) return url.includes('oauth2.googleapis.com') ? tokenResponse() : new Response(JSON.stringify(subscription()));
    return new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(new Error('upstream timeout')), { once: true }));
  } });
  // AbortSignal.timeout uses an unref timer; keep a bounded test clock alive for the fake transport.
  const keepAlive = setTimeout(() => {}, 1000);
  try { await assert.rejects(verifier(request), /timeout/); } finally { clearTimeout(keepAlive); }
  hang = false;
  assert.ok(await verifier(request));
});

test('invalid configuration, cancellation and input fail without granting access', async () => {
  assert.throws(() => createVerifier({ serviceAccount, privateKey: rsa.privateKey }), /EC P-256/);
  let calls = 0;
  const verifier = configured({ fetchImpl: async () => { calls++; return tokenResponse(); } });
  for (const input of [null, [], {}, { ...request, purchaseToken: 'short' }, { ...request, purchaseToken: 'x'.repeat(4097) }]) {
    assert.equal(await verifier(input), null);
  }
  const controller = new AbortController(); controller.abort();
  await assert.rejects(verifier(request, { signal: controller.signal }), /abort/i);
  assert.equal(calls, 0);
});

test('request-token path is URL encoded and a response is bound separately to each installation', async () => {
  const verifier = configured({ fetchImpl: async url => {
    if (url.includes('oauth2.googleapis.com')) return tokenResponse();
    assert.match(url, /tokens\/valid-token%2F%3Fother%3D1$/);
    return new Response(JSON.stringify(subscription()));
  } });
  const envelope = await verifier({ ...request, purchaseToken: 'valid-token/?other=1' });
  const claims = JSON.parse(Buffer.from(envelope.payload, 'base64url'));
  assert.equal(claims.installationId, installationId);
  assert.equal(claims.packageName, 'com.moa.babylanguage');
});
