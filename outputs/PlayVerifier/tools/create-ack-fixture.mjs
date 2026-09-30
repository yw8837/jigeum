// Local test fixture only. The generated private key remains in memory and is discarded.
import { createHash, generateKeyPairSync } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { signEntitlement } from '../src/verifier.js';

const now = Date.parse('2026-09-30T00:00:00Z');
const installationId = '94be8be6-04c9-4b85-9994-69113bf442d0';
const purchaseToken = 'LOCAL-QA-owned-token/한글?x=1';
const keys = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const envelope = signEntitlement({ installationId, basePlanId: 'monthly', subscriptionExpiryMs: now + 86400000,
  acknowledgedTokenHash: createHash('sha256').update(purchaseToken).digest('hex') }, keys.privateKey, now);
const fixture = { purpose: 'Synthetic Node-to-Android contract fixture; not a purchase or production key.', now,
  installationId, purchaseToken, publicKey: keys.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'), envelope };
const target = fileURLToPath(new URL('../../MoaAndroid/app/src/androidTest/assets/server-ack-fixture.json', import.meta.url));
writeFileSync(target, JSON.stringify(fixture, null, 2) + '\n');
console.log('Wrote public synthetic contract fixture; no private key was saved.');
