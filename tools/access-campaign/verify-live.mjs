import { readFile } from 'node:fs/promises';
import { createHash, createPublicKey, randomUUID, verify } from 'node:crypto';

const [privateDirectory, publicKeyPath] = process.argv.slice(2);
const text = await readFile(`${privateDirectory}/codes-private.txt`, 'utf8');
const family = text.match(/무료: ([A-Z2-9-]+)/)?.[1]?.replaceAll('-', '');
const shared = text.match(/출시 할인: ([A-Z0-9]+)/)?.[1];
if (!family || !shared) throw new Error('Private issued code file missing');
const key = createPublicKey({ key: Buffer.from((await readFile(publicKeyPath, 'utf8')).trim(), 'base64'), format: 'der', type: 'spki' });
const url = 'https://jigeum-verifier-th7ce7roma-du.a.run.app/access-code';
const rows = [];
for (const [name, code, expected] of [['invitation-short', family, 200], ['unknown-short', 'INVALID9', 403], ['discount-while-closed', shared, 403]]) {
  const installationId = randomUUID(); const start = Date.now();
  const response = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ packageName: 'com.moa.babylanguage', installationId, code }), signal: AbortSignal.timeout(30000), redirect: 'error' });
  const data = await response.json();
  if (response.status !== expected) throw new Error(`${name}: unexpected HTTP ${response.status}`);
  if (expected === 200) {
    const payload = Buffer.from(data.entitlement.payload, 'base64url');
    const claim = JSON.parse(payload);
    if (!verify('sha256', payload, key, Buffer.from(data.entitlement.signature, 'base64url')) ||
        claim.installationId !== installationId || claim.grantSource !== 'invitation' ||
        claim.codeHash !== createHash('sha256').update(code).digest('hex') || claim.accessId !== 'friends-permanent')
      throw new Error('Live invitation signature or claim mismatch');
  }
  rows.push({ name, status: 'pass', http: response.status, elapsedMs: Date.now() - start });
}
console.log(JSON.stringify({ testedAt: new Date().toISOString(), rows, liveDiscountRegistrationConsumed: false, actualPurchaseTested: false }, null, 2));
