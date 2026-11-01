import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync } from 'node:crypto';
import { createAccessCodeVerifier, validateCodeManifest } from '../src/access-codes.js';
import { createCampaignReservations, createGcsJsonStore, readCampaignSlots, controlName, slotName } from '../src/campaign-reservations.js';

const hash = value => createHash('sha256').update(value).digest('hex');
const campaignId = 'launch-20261027';
const control = { version: 1, campaignId, limit: 100, acceptingNew: true };
const slot = i => ({ version: 1, campaignId, installationHash: hash(String(i)), reservedAtMs: Date.now() });
function memoryStore() {
  const objects = new Map([[controlName, structuredClone(control)]]);
  return { objects,
    async get(name, { signal } = {}) { signal?.throwIfAborted(); await new Promise(resolve => setImmediate(resolve));
      return objects.has(name) ? { value: structuredClone(objects.get(name)), generation: '1' } : null; },
    async create(name, value, { signal } = {}) { signal?.throwIfAborted();
      await new Promise(resolve => setImmediate(resolve));
      if (objects.has(name)) return false; objects.set(name, structuredClone(value)); return true; }
  };
}
const reservation = i => ({ installationHash: hash(String(i)), campaignId });

test('100 immutable slots cap concurrent shared registrations across instances and restart', async () => {
  const store = memoryStore();
  const servers = Array.from({ length: 4 }, () => createCampaignReservations({ store }));
  const results = await Promise.all(Array.from({ length: 125 }, (_, i) => servers[i % 4](reservation(i))));
  assert.equal(results.filter(Boolean).length, 100);
  assert.equal((await readCampaignSlots(store)).filter(Boolean).length, 100);
  const restarted = createCampaignReservations({ store });
  for (let i = 0; i < 125; i++) assert.equal(await restarted(reservation(i)), results[i]);
});

test('same installation concurrent retries consume one slot, including mixed competing registrations', async () => {
  const store = memoryStore();
  const requests = Array.from({ length: 60 }, (_, i) => createCampaignReservations({ store })(reservation(i % 10)));
  assert.ok((await Promise.all(requests)).every(Boolean));
  const slots = (await readCampaignSlots(store)).filter(Boolean);
  assert.equal(slots.length, 10);
  assert.equal(new Set(slots.map(x => x.installationHash)).size, 10);
});

test('closing refuses new registrations but preserves existing users; reopening never resets usage', async () => {
  const store = memoryStore(); store.objects.set(slotName(0), slot('migrated'));
  const reserve = createCampaignReservations({ store });
  assert.ok(await reserve(reservation(1)));
  store.objects.set(controlName, { ...control, acceptingNew: false });
  assert.equal(await reserve(reservation(2)), false);
  assert.ok(await reserve(reservation(1)));
  assert.ok(await reserve(reservation('migrated')));
  store.objects.set(controlName, control);
  assert.ok(await reserve(reservation(2)));
  assert.equal((await readCampaignSlots(store)).filter(Boolean).length, 3);
});

test('missing/corrupt control or slots and storage outages fail closed', async () => {
  for (const bad of [undefined, {}, { ...control, limit: 200 }, { ...control, acceptingNew: 'yes' }]) {
    const store = memoryStore(); store.objects.set(controlName, bad);
    await assert.rejects(createCampaignReservations({ store })(reservation(1)));
    assert.equal(store.objects.size, 1);
  }
  const store = memoryStore(); store.objects.set(slotName(0), {});
  await assert.rejects(createCampaignReservations({ store })(reservation(1)));
  const controller = new AbortController(); controller.abort();
  await assert.rejects(createCampaignReservations({ store: memoryStore() })({ ...reservation(1), signal: controller.signal }));
  await assert.rejects(createCampaignReservations({ store: { get: async () => { throw Error('offline'); } } })(reservation(1)));
});

test('v2 short invitation and shared discount retain signed purpose and share quota with legacy codes', async () => {
  const family = 'ABCDEF2345'; const shared = 'TESTHALF';
  const oldFamily = 'FAMILY-old-synthetic-code-only-00000';
  const legacy = Array.from({ length: 100 }, (_, i) => `LAUNCH-old-synthetic-code-${String(i).padStart(8, '0')}`);
  const manifest = { schemaVersion: 2, campaignId, offerId: 'launch-2m-half', registrationLimit: 100,
    familyCodeSha256: hash(oldFamily), discountCodeSha256: legacy.map(hash),
    shortFamilyCodeSha256: hash(family), sharedDiscountCodeSha256: hash(shared) };
  const { privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const store = memoryStore(); const bindings = new Map();
  const options = { privateKey, manifest, reserveCampaign: createCampaignReservations({ store }),
    reserveDiscount: async ({ codeHash, installationHash }) => {
      if (!bindings.has(codeHash)) bindings.set(codeHash, installationHash);
      return bindings.get(codeHash) === installationHash;
    } };
  assert.throws(() => createAccessCodeVerifier({ ...options, reserveCampaign: undefined }));
  assert.throws(() => validateCodeManifest({ ...manifest, sharedDiscountCodeSha256: hash(family) }));
  const check = createAccessCodeVerifier(options);
  const req = (code, i = 1) => ({ packageName: 'com.moa.babylanguage', installationId: `10000000-0000-4000-8000-${String(i).padStart(12, '0')}`, code });
  const claim = result => JSON.parse(Buffer.from(result.payload, 'base64url'));
  assert.equal(claim(await check(req(family))).grantSource, 'invitation');
  assert.equal(claim(await check(req(oldFamily))).grantSource, 'invitation');
  assert.equal(claim(await check(req(shared))).grantSource, 'launch-offer');
  assert.equal(claim(await check(req(shared))).codeHash, hash(shared));
  assert.ok(await check(req(legacy[0]))); // Same installation, still one slot.
  assert.equal(await check(req(legacy[0], 2)), null);
  assert.equal((await readCampaignSlots(store)).filter(Boolean).length, 1);
  for (let i = 2; i <= 100; i++) assert.ok(await check(req(shared, i)));
  assert.equal(await check(req(shared, 101)), null);
  assert.equal(await check(req(legacy[1], 101)), null);
  assert.ok(await check(req(family, 101))); // Invitation never consumes discount quota.
  assert.equal(await check(req('UNKNOWN8')), null);
});

test('GCS adapter uses generation zero / CAS and generation-pinned reads, with no list or delete', async () => {
  const calls = [];
  const fetchImpl = async (url, options) => {
    calls.push([new URL(url), options]);
    assert.equal(options.headers.authorization, 'Bearer test-only');
    assert.equal(options.redirect, 'error');
    if (options.method === 'POST') return new Response('', { status: calls.length === 4 ? 412 : 200 });
    return Response.json(url.includes('fields=') ? { generation: '19' } : control);
  };
  const store = createGcsJsonStore({ bucket: 'test-bucket', accessToken: async () => 'test-only', fetchImpl });
  assert.deepEqual((await store.get(controlName, { metadata: true })).value, control);
  assert.equal(calls[1][0].searchParams.get('generation'), '19');
  assert.ok(await store.create(slotName(0), slot(1)));
  assert.equal(calls[2][0].searchParams.get('ifGenerationMatch'), '0');
  assert.equal(await store.put(controlName, control, '19'), false);
  assert.equal(calls[3][0].searchParams.get('ifGenerationMatch'), '19');
  await assert.rejects(store.put(controlName, control, undefined));
  await assert.rejects(store.get('../unrelated'));
});
