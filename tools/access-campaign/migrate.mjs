import { readFile } from 'node:fs/promises';
import { createGcsJsonStore, readCampaignSlots, controlName, slotName } from '../../outputs/PlayVerifier/src/campaign-reservations.js';
import { validateCodeManifest, CODE_CAMPAIGN } from '../../outputs/PlayVerifier/src/access-codes.js';
import { operatorTokenProvider } from './dashboard.mjs';

const [manifestPath, mode = 'inspect'] = process.argv.slice(2);
if (!['inspect', 'seed-closed', 'open'].includes(mode)) throw new Error('Unknown migration mode');
const manifest = validateCodeManifest(JSON.parse(await readFile(manifestPath, 'utf8')));
const token = operatorTokenProvider(process.env.AGATTOK_GCLOUD_PATH);
const bucket = 'jigeum-play-20261001-code-redemptions';
const store = createGcsJsonStore({ bucket, accessToken: token });
const headers = { authorization: `Bearer ${await token()}` };
const listing = await fetch(`https://storage.googleapis.com/storage/v1/b/${bucket}/o?prefix=${CODE_CAMPAIGN}/&fields=items(name),nextPageToken`, { headers, redirect: 'error', signal: AbortSignal.timeout(10000) });
if (!listing.ok) throw new Error('Legacy listing unavailable');
const data = await listing.json();
if (data.nextPageToken) throw new Error('Unexpected legacy listing size');
const acceptedHashes = new Set(manifest.discountCodeSha256);
const installations = new Map();
for (const { name } of data.items ?? []) {
  const codeHash = name.match(/^launch-20261027\/([a-f0-9]{64})\.json$/)?.[1];
  if (!codeHash || !acceptedHashes.has(codeHash)) continue; // Previous isolated test manifests are not campaign users.
  const response = await fetch(`https://storage.googleapis.com/storage/v1/b/${bucket}/o/${encodeURIComponent(name)}?alt=media`, { headers, redirect: 'error', signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error('Legacy reservation unavailable');
  const record = await response.json();
  if (record.version !== 1 || record.campaignId !== CODE_CAMPAIGN || record.codeHash !== codeHash ||
      !/^[a-f0-9]{64}$/.test(record.installationHash) || !Number.isSafeInteger(record.reservedAtMs) || record.reservedAtMs <= 0)
    throw new Error('Invalid legacy reservation');
  installations.set(record.installationHash, record.reservedAtMs);
}
if (mode === 'seed-closed') {
  await store.create(controlName, { version: 1, campaignId: CODE_CAMPAIGN, limit: 100, acceptingNew: false, updatedAtMs: Date.now() });
  const control = await store.get(controlName);
  if (control.value.acceptingNew !== false) throw new Error('Migrate only while new registrations are closed');
  for (const [installationHash, reservedAtMs] of installations) {
    const slots = await readCampaignSlots(store);
    if (slots.some(slot => slot?.installationHash === installationHash)) continue;
    let inserted = false;
    for (let i = 0; i < 100; i++) {
      if (slots[i]) continue;
      if (await store.create(slotName(i), { version: 1, campaignId: CODE_CAMPAIGN, installationHash, reservedAtMs })) { inserted = true; break; }
      const winner = await store.get(slotName(i));
      if (winner?.value?.installationHash === installationHash) { inserted = true; break; }
    }
    if (!inserted) throw new Error('Migration capacity exhausted');
  }
}
const slots = await readCampaignSlots(store);
const missing = [...installations.keys()].filter(hash => !slots.some(slot => slot?.installationHash === hash)).length;
if (mode === 'open') {
  if (missing) throw new Error('Legacy reservations missing from the shared quota');
  const control = await store.get(controlName, { metadata: true });
  if (!control || control.value.limit !== 100) throw new Error('Missing campaign control');
  if (!await store.put(controlName, { ...control.value, acceptingNew: true, updatedAtMs: Date.now() }, control.generation))
    throw new Error('Concurrent control change; inspect before retry');
}
console.log(JSON.stringify({ mode, legacyCampaignInstallations: installations.size,
  registered: slots.filter(Boolean).length, missingLegacyInstallations: missing,
  acceptingNew: (await store.get(controlName))?.value?.acceptingNew ?? null, purchasesVerified: false }));
