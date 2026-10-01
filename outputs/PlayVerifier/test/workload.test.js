import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { createMetadataTokenProvider } from '../src/credentials.js';
import { createVerifier } from '../src/verifier.js';
import { readRuntimeConfig } from '../src/runtime-config.js';

const now = Date.parse('2026-10-01T00:00:00Z');
const serviceAccountEmail = 'jigeum-play-runtime@jigeum-play-20261001.iam.gserviceaccount.com';
const bootstrap = body => new Response(JSON.stringify(body ?? { access_token: 'metadata-test' }), { headers: { 'Metadata-Flavor': 'Google' } });
const scoped = () => new Response(JSON.stringify({ accessToken: 'publisher-test', expireTime: new Date(now + 3_600_000).toISOString() }));

test('workload provider uses fixed metadata, scopes Publisher token and never exports keys', async () => {
  const calls = [];
  const provider = createMetadataTokenProvider({ serviceAccountEmail, clock: () => now, fetchImpl: async (url, options) => {
    calls.push({ url, options });
    if (url.startsWith('http://metadata.google.internal/')) {
      assert.equal(options.headers['Metadata-Flavor'], 'Google');
      assert.equal(options.redirect, 'error');
      return bootstrap();
    }
    assert.equal(options.headers.authorization, 'Bearer metadata-test');
    assert.deepEqual(JSON.parse(options.body), { scope: ['https://www.googleapis.com/auth/androidpublisher'], lifetime: '3600s' });
    assert.equal(options.redirect, 'error');
    return scoped();
  } });
  assert.deepEqual(await provider(), { access_token: 'publisher-test', expires_in: 3600 });
  assert.equal(calls.length, 2);
  assert.match(calls[1].url, /^https:\/\/iamcredentials\.googleapis\.com\/v1\/projects\/-\/serviceAccounts\/jigeum-play-runtime%40jigeum-play-20261001\.iam\.gserviceaccount\.com:generateAccessToken$/);
  assert.equal(JSON.stringify(calls).includes('private_key'), false);
});

test('metadata and scoped credential failures are closed and bounded', async () => {
  assert.throws(() => createMetadataTokenProvider({ serviceAccountEmail: 'https://attacker.invalid' }), /service account/);
  const responses = [
    () => new Response('{}', { status: 403 }),
    () => new Response('{}'),
    () => bootstrap({}),
    () => new Response('x'.repeat(16_385), { headers: { 'Metadata-Flavor': 'Google' } }),
    () => bootstrap({ access_token: 'bad\r\nheader' })
  ];
  for (const response of responses) {
    const provider = createMetadataTokenProvider({ serviceAccountEmail, clock: () => now, fetchImpl: async () => response() });
    await assert.rejects(provider());
  }
  for (const response of [() => new Response('{}', { status: 403 }), () => new Response('{}'),
    () => new Response(JSON.stringify({ accessToken: 't', expireTime: new Date(now).toISOString() }))]) {
    const provider = createMetadataTokenProvider({ serviceAccountEmail, clock: () => now,
      fetchImpl: async url => url.startsWith('http:') ? bootstrap() : response() });
    await assert.rejects(provider(), /credentials/);
  }
});

test('workload credentials are single-flight, cache safely and refresh after Play401', async () => {
  let credentials = 0, play = 0, denied = false;
  const ec = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const verifier = createVerifier({ privateKey: ec.privateKey, clock: () => now,
    accessTokenProvider: async () => { credentials++; return { access_token: 'publisher-test', expires_in: 3600 }; },
    fetchImpl: async (url, options) => {
      assert.equal(url.startsWith('https://androidpublisher.googleapis.com/'), true);
      assert.equal(options.headers.authorization, 'Bearer publisher-test');
      play++;
      return denied ? new Response('{}', { status: 401 }) : new Response(JSON.stringify({
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
        lineItems: [{ productId: 'jigeum_album', expiryTime: new Date(now + 3_600_000).toISOString(),
          offerDetails: { basePlanId: 'monthly' }, autoRenewingPlan: {} }]
      }));
    } });
  const input = { packageName: 'com.moa.babylanguage', productId: 'jigeum_album', purchaseToken: 'valid-test-token', installationId: '94be8be6-04c9-4b85-9994-69113bf442d0' };
  assert.equal((await Promise.all(Array.from({ length: 20 }, () => verifier(input)))).every(Boolean), true);
  assert.equal(credentials, 1); assert.equal(play, 1);
  denied = true;
  await assert.rejects(verifier(input));
  denied = false;
  assert.ok(await verifier(input));
  assert.equal(credentials, 2); assert.equal(play, 3);
});

test('malformed workload token expiry cannot poison the credential cache', async () => {
  let valid = false, credentials = 0;
  const ec = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const verifier = createVerifier({ privateKey: ec.privateKey,
    accessTokenProvider: async () => { credentials++; return { access_token: 'publisher-test', expires_in: valid ? 3600 : 0 }; },
    fetchImpl: async () => new Response('{}', { status: 404 }) });
  const input = { packageName: 'com.moa.babylanguage', productId: 'jigeum_album', purchaseToken: 'valid-test-token', installationId: '94be8be6-04c9-4b85-9994-69113bf442d0' };
  await assert.rejects(verifier(input), /expiry/);
  valid = true;
  assert.equal(await verifier(input), null);
  assert.equal(credentials, 2);
});

test('runtime config rejects exported workload keys and unsafe envelope changes', () => {
  const env = { PLAY_ENTITLEMENT_PRIVATE_KEY_PATH: '/secrets/entitlement.pem', PLAY_CREDENTIALS_MODE: 'workload',
    PLAY_RUNTIME_SERVICE_ACCOUNT_EMAIL: serviceAccountEmail, RATE_LIMIT_MODE: 'installation', MAX_CONCURRENT_VERIFICATIONS: '24',
    GLOBAL_REQUESTS_PER_MINUTE: '300', BODY_TIMEOUT_MS: '3000', VERIFICATION_TIMEOUT_MS: '23000' };
  assert.equal(readRuntimeConfig(env).server.maxConcurrent, 24);
  for (const override of [{ PLAY_SERVICE_ACCOUNT_JSON_PATH: 'account.json' }, { MAX_CONCURRENT_VERIFICATIONS: '33' },
    { RATE_LIMIT_MODE: 'forwarded-for' }, { PLAY_CREDENTIALS_MODE: 'unknown' }, { GLOBAL_REQUESTS_PER_MINUTE: 'NaN' }, { PORT: '0' }]) {
    assert.throws(() => readRuntimeConfig({ ...env, ...override }));
  }
});
