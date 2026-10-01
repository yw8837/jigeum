import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, verify } from 'node:crypto';
import { createReviewVerifier, validReviewRequest } from '../src/review.js';
import { createVerificationServer } from '../src/server.js';

const now = Date.parse('2026-10-01T00:00:00Z');
const ec = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const code = 'test-only-code-AAAAAAAAAAAAAAAAAAAA';
const input = { packageName: 'com.moa.babylanguage', installationId: '94be8be6-04c9-4b85-9994-69113bf442d0', reviewCode: code };
const configured = options => createReviewVerifier({ privateKey: ec.privateKey,
  reviewCodeSha256: createHash('sha256').update(code).digest('hex'), reviewAccessId: 'play-review-test', clock: () => now, ...options });

test('review grant requires configured credential, signed source and installation binding', async () => {
  const envelope = await configured()(input);
  const bytes = Buffer.from(envelope.payload, 'base64url');
  const claims = JSON.parse(bytes);
  assert.equal(claims.grantSource, 'play-review'); assert.equal(claims.basePlanId, 'play-review');
  assert.equal(claims.reviewAccessId, 'play-review-test'); assert.equal(claims.installationId, input.installationId);
  assert.equal(claims.expiresAtMs, now + 86_400_000); assert.equal(claims.subscriptionExpiryMs, claims.expiresAtMs);
  assert.equal(verify('sha256', bytes, ec.publicKey, Buffer.from(envelope.signature, 'base64url')), true);
  assert.equal(JSON.stringify(claims).includes(code), false);
  assert.equal(claims.acknowledgedTokenHash, undefined);
});

test('wrong, missing, unknown and purchase-shaped review requests fail closed', async () => {
  const review = configured();
  for (const invalid of [null, [], {}, { ...input, reviewCode: 'B'.repeat(32) },
    { ...input, packageName: 'wrong' }, { ...input, installationId: 'not-a-uuid' },
    { ...input, purchaseToken: 'valid-token' }, { ...input, reviewCode: 'x'.repeat(129) }]) {
    assert.equal(await review(invalid), null);
  }
  assert.equal(await createReviewVerifier({ privateKey: ec.privateKey })(input), null);
  assert.throws(() => configured({ reviewCodeSha256: 'invalid' }), /configuration/);
  assert.throws(() => configured({ reviewAccessId: 'bad' }), /configuration/);
  assert.equal(validReviewRequest({ ...input, reviewCode: 'x'.repeat(32) }), true);
  assert.equal(validReviewRequest({ ...input, reviewCode: 'x'.repeat(128) }), true);
});

test('HTTP review route cannot invoke purchase verifier or accept extra data', async () => {
  let purchases = 0;
  const server = createVerificationServer({ review: configured(), verify: async () => { purchases++; return null; },
    rateLimitMode: 'installation', maxRequestsPerMinute: 2 });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = body => fetch(`${base}/review`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  try {
    assert.equal((await post({ ...input, purchaseToken: 'valid-token' })).status, 400);
    assert.equal((await post({ ...input, reviewCode: 'B'.repeat(32) })).status, 403);
    const granted = await post(input);
    assert.equal(granted.status, 200);
    assert.equal(JSON.stringify(await granted.json()).includes(code), false);
    assert.equal((await post(input)).status, 429);
    assert.equal(purchases, 0);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});
