import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify as verifySignature } from 'node:crypto';
import { createVerificationServer } from '../src/server.js';
import { createVerifier, validRequest, selectEntitlement, signEntitlement } from '../src/verifier.js';

// All external responses are mocked. HTTP tests use ephemeral loopback ports,
// and these generated keys never leave the test process or touch real accounts.
const now = Date.parse('2026-09-30T00:00:00Z');
const day = 86_400_000;
const input = {
  packageName: 'com.moa.babylanguage',
  productId: 'jigeum_album',
  purchaseToken: 'test-token-local',
  installationId: '94be8be6-04c9-4b85-9994-69113bf442d0'
};
const otherInstallationId = 'd4efbfe1-7a41-485a-bda9-7e674bc24850';
const ec = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 });
const serviceAccount = {
  client_email: 'qa@example.invalid',
  private_key: rsa.privateKey.export({ type: 'pkcs8', format: 'pem' })
};
const subscription = (expiry = now + 5 * day) => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
  lineItems: [{
    productId: 'jigeum_album',
    expiryTime: new Date(expiry).toISOString(),
    offerDetails: { basePlanId: 'monthly' },
    autoRenewingPlan: { autoRenewEnabled: true }
  }]
});
const tokenResponse = () => new Response(JSON.stringify({ access_token: 'LOCAL_TEST_ONLY', expires_in: 3600 }));
const configured = options => createVerifier({ serviceAccount, privateKey: ec.privateKey, clock: () => now, ...options });
const claims = envelope => JSON.parse(Buffer.from(envelope.payload, 'base64url'));
const googleMock = play => async (url, options) =>
  url.includes('oauth2.googleapis.com') ? tokenResponse() : play(url, options);

// These boundaries would fail if token length/control-character validation or
// UUID version/variant checks were relaxed by a later production change.
test('purchase token exact size boundaries and control characters fail closed', () => {
  assert.equal(validRequest({ ...input, purchaseToken: 'x'.repeat(8) }), true);
  assert.equal(validRequest({ ...input, purchaseToken: 'x'.repeat(4096) }), true);
  for (const purchaseToken of ['x'.repeat(7), 'x'.repeat(4097), 'xxxx\0xxxx', 'xxxx\txxxx', 'xxxx\u007fxxxx', 'xxxx xxxx']) {
    assert.equal(validRequest({ ...input, purchaseToken }), false);
  }
});

test('installation UUID rejects invalid version and variant boundaries', () => {
  for (const installationId of [
    '94be8be6-04c9-0b85-9994-69113bf442d0',
    '94be8be6-04c9-9b85-9994-69113bf442d0',
    '94be8be6-04c9-4b85-7994-69113bf442d0',
    '94be8be6-04c9-4b85-c994-69113bf442d0',
    `${input.installationId} `
  ]) {
    assert.equal(validRequest({ ...input, installationId }), false);
  }
});

test('exact subscription expiry is denied while one millisecond remaining is allowed', () => {
  assert.equal(selectEntitlement(subscription(now), now), null);
  assert.equal(selectEntitlement(subscription(now + 1), now).subscriptionExpiryMs, now + 1);
});

test('multiple subscription items select the latest eligible expiry', () => {
  const response = subscription(now + day);
  response.lineItems.push(
    { ...response.lineItems[0], expiryTime: new Date(now + 3 * day).toISOString() },
    { ...response.lineItems[0], productId: 'wrong', expiryTime: new Date(now + 9 * day).toISOString() }
  );
  assert.equal(selectEntitlement(response, now).subscriptionExpiryMs, now + 3 * day);
});

test('invalid subscription dates and unknown states deny access', () => {
  for (const subscriptionState of [null, undefined, 'SUBSCRIPTION_STATE_UNSPECIFIED', 'ACTIVE']) {
    assert.equal(selectEntitlement({ ...subscription(), subscriptionState }, now), null);
  }
  for (const expiryTime of ['', 'garbage', '2026-09-29T00:00:00Z']) {
    const response = subscription();
    response.lineItems[0].expiryTime = expiryTime;
    assert.equal(selectEntitlement(response, now), null);
  }
});

test('long subscription grants have a 24-hour cap and reject payload tampering', () => {
  const envelope = signEntitlement({
    installationId: input.installationId,
    basePlanId: 'monthly',
    subscriptionExpiryMs: now + 90 * day
  }, ec.privateKey, now);
  assert.equal(claims(envelope).expiresAtMs, now + day);
  const payload = Buffer.from(envelope.payload, 'base64url');
  const signature = Buffer.from(envelope.signature, 'base64url');
  assert.equal(verifySignature('sha256', payload, ec.publicKey, signature), true);
  payload[0] ^= 1;
  assert.equal(verifySignature('sha256', payload, ec.publicKey, signature), false);
});

test('shared purchase lookup returns a separately bound envelope to each installation', async () => {
  let lookups = 0;
  const verifier = configured({ fetchImpl: googleMock(async () => {
    lookups++;
    return new Response(JSON.stringify(subscription()));
  }) });
  const [first, second] = await Promise.all([
    verifier(input),
    verifier({ ...input, installationId: otherInstallationId })
  ]);
  assert.equal(lookups, 1);
  assert.equal(claims(first).installationId, input.installationId);
  assert.equal(claims(second).installationId, otherInstallationId);
});

test('malformed and oversized Google JSON reject without poisoning the next lookup', async () => {
  let response;
  const verifier = configured({ fetchImpl: googleMock(async () => response()) });
  for (const body of ['{', 'x'.repeat(131_073)]) {
    response = () => new Response(body);
    await assert.rejects(verifier(input));
  }
  response = () => new Response(JSON.stringify(subscription()));
  assert.ok(await verifier(input));
});

test('streamed Google JSON enforces the cumulative response size cap', async () => {
  const body = new ReadableStream({ start(controller) {
    controller.enqueue(new Uint8Array(65_536));
    controller.enqueue(new Uint8Array(65_537));
    controller.close();
  } });
  const verifier = configured({ fetchImpl: googleMock(async () => new Response(body)) });
  await assert.rejects(verifier(input), /too large/);
});

test('invalid OAuth payloads reject and allow a subsequent recovery', async () => {
  for (const data of [
    '{',
    'x'.repeat(16_385),
    JSON.stringify({ expires_in: 3600 }),
    JSON.stringify({ access_token: 'LOCAL', expires_in: 0 }),
    JSON.stringify({ access_token: 'LOCAL', expires_in: 'bad' })
  ]) {
    let broken = true;
    const verifier = configured({ fetchImpl: async url => {
      if (url.includes('oauth2.googleapis.com')) return broken ? new Response(data) : tokenResponse();
      return new Response(JSON.stringify(subscription()));
    } });
    await assert.rejects(verifier(input));
    broken = false;
    assert.ok(await verifier(input));
  }
});

test('Play 401 invalidates the OAuth cache before the next verification', async () => {
  let oauth = 0;
  let denied = true;
  const verifier = configured({ fetchImpl: async url => {
    if (url.includes('oauth2.googleapis.com')) { oauth++; return tokenResponse(); }
    return denied ? new Response('{}', { status: 401 }) : new Response(JSON.stringify(subscription()));
  } });
  await assert.rejects(verifier(input), /unavailable/);
  denied = false;
  assert.ok(await verifier(input));
  assert.equal(oauth, 2);
});

test('OAuth service failure does not poison the next independent request', async () => {
  let failed = true;
  let oauth = 0;
  const verifier = configured({ fetchImpl: async url => {
    if (url.includes('oauth2.googleapis.com')) {
      oauth++;
      return failed ? new Response('{}', { status: 503 }) : tokenResponse();
    }
    return new Response(JSON.stringify(subscription()));
  } });
  await assert.rejects(verifier(input), /authentication unavailable/);
  failed = false;
  assert.ok(await verifier(input));
  assert.equal(oauth, 2);
});

test('32 distinct in-flight lookups bound admission and release capacity after completion', async () => {
  let release;
  let entered = 0;
  const gate = new Promise(resolve => { release = resolve; });
  const verifier = configured({ fetchImpl: googleMock(async () => {
    entered++;
    await gate;
    return new Response(JSON.stringify(subscription()));
  }) });
  const pending = Array.from({ length: 32 }, (_, index) =>
    verifier({ ...input, purchaseToken: `local-token-${index}` })
  );
  const overflow = verifier({ ...input, purchaseToken: 'extra-local-token' });
  // Release before awaiting the assertion: a broken admission guard must fail
  // the assertion rather than leave an unexpectedly admitted request hanging.
  release();
  await assert.rejects(overflow, /busy/);
  assert.equal((await Promise.all(pending)).filter(Boolean).length, 32);
  assert.equal(entered, 32);
  assert.ok(await verifier({ ...input, purchaseToken: 'extra-local-token' }));
});

test('canceling one waiter leaves another installation sharing its lookup usable', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const verifier = configured({ fetchImpl: googleMock(async () => {
    await gate;
    return new Response(JSON.stringify(subscription()));
  }) });
  const controller = new AbortController();
  const canceled = verifier(input, { signal: controller.signal });
  const rejected = assert.rejects(canceled, /abort/i);
  const survivor = verifier({ ...input, installationId: otherInstallationId });
  controller.abort();
  release();
  await rejected;
  assert.equal(claims(await survivor).installationId, otherInstallationId);
});

async function withServer(options, run) {
  const server = createVerificationServer({ maxRequestsPerMinute: 1000, ...options });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
const post = (url, body = input, headers = {}) => fetch(`${url}/verify`, {
  method: 'POST',
  headers: { 'content-type': 'application/json', ...headers },
  body: typeof body === 'string' ? body : JSON.stringify(body),
  signal: AbortSignal.timeout(3000)
});

test('unsupported HTTP routes and methods never invoke purchase verification', async () => {
  let calls = 0;
  await withServer({ verify: async () => { calls++; return null; } }, async url => {
    for (const [path, method] of [
      ['/verify', 'GET'], ['/verify/', 'POST'], ['/verify?q=x', 'POST'],
      ['/healthz', 'POST'], ['/healthz/', 'GET']
    ]) {
      assert.equal((await fetch(url + path, { method, signal: AbortSignal.timeout(3000) })).status, 404);
    }
    assert.equal(calls, 0);
  });
});

test('HTTP accepts JSON charset and keeps denial responses token-free and non-cacheable', async () => {
  await withServer({ verify: async () => null }, async url => {
    const response = await post(url, input, { 'content-type': 'Application/JSON; charset=utf-8' });
    assert.equal(response.status, 403);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    assert.equal(response.headers.get('x-content-type-options'), 'nosniff');
    assert.equal((await response.text()).includes(input.purchaseToken), false);
  });
});

test('HTTP accepts exactly 16 KiB and rejects the next byte before verification', async () => {
  let calls = 0;
  await withServer({ verify: async () => { calls++; return null; } }, async url => {
    const base = JSON.stringify({ ...input, padding: '' });
    const body = JSON.stringify({ ...input, padding: 'x'.repeat(16_384 - Buffer.byteLength(base)) });
    assert.equal(Buffer.byteLength(body), 16_384);
    assert.equal((await post(url, body)).status, 403);
    assert.equal((await post(url, `${body} `)).status, 413);
    assert.equal(calls, 1);
  });
});

test('HTTP quota boundary leaves health live and recovers at exactly one minute', async () => {
  let clock = 1000;
  await withServer({ clock: () => clock, maxRequestsPerMinute: 1, verify: async () => null }, async url => {
    assert.equal((await post(url)).status, 403);
    clock = 60_999;
    assert.equal((await post(url)).status, 429);
    assert.equal((await fetch(`${url}/healthz`, { signal: AbortSignal.timeout(3000) })).status, 200);
    clock = 61_000;
    assert.equal((await post(url)).status, 403);
  });
});
