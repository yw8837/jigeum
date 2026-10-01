import { readFileSync } from 'node:fs';
import { randomUUID, verify as verifySignature } from 'node:crypto';

// Run only after the operator has approved the public service deployment.
// No purchase token, review code, UUID, response payload or private key is printed.
const origin = new URL(process.argv[2] ?? '');
if (origin.protocol !== 'https:' || origin.pathname !== '/' || origin.username || origin.password || origin.search || origin.hash) {
  throw new Error('Provide an HTTPS origin without path/query/credentials');
}
const checks = [];
async function request(path, options = {}) {
  return fetch(new URL(path, origin), { ...options, redirect: 'error', signal: AbortSignal.timeout(35_000) });
}
async function check(name, run) {
  try { await run(); checks.push({ name, pass: true }); }
  catch { checks.push({ name, pass: false }); }
}
async function requireStatus(path, status, options = {}) {
  const response = await request(path, options);
  if (response.status !== status) throw new Error('Unexpected HTTP status');
  return response;
}
await check('process-health', async () => {
  const response = await requireStatus('/jigeum-health', 200);
  if ((await response.json()).ok !== true) throw new Error('Invalid health response');
});
for (const path of ['/privacy', '/terms', '/support']) {
  await check(`public${path}`, async () => {
    const response = await requireStatus(path, 200);
    if (!response.headers.get('content-type')?.startsWith('text/html') || !response.headers.get('content-security-policy')) throw new Error('Invalid policy headers');
    if (!(await response.text()).includes('bsfire1237@gmail.com')) throw new Error('Missing support contact');
  });
}
await check('unknown-route', () => requireStatus('/not-a-real-route', 404));
await check('malformed-purchase-request', () => requireStatus('/verify', 400, {
  method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}'
}));
await check('unapproved-review-credential', () => requireStatus('/review', 403, {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ packageName: 'com.moa.babylanguage', installationId: randomUUID(), reviewCode: 'smoke-only-INVALID-AAAAAAAAAAAAAAA' })
}));
await check('invalid-purchase-token-rejected', () => requireStatus('/verify', 403, {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ packageName: 'com.moa.babylanguage', productId: 'jigeum_album',
    installationId: randomUUID(), purchaseToken: 'smoke-INVALID-NOT-A-PURCHASE-TOKEN' })
}));

// Optional valid review round-trip; files must be outside the repository.
// argv3 is credential text file, argv4 is public SPKI PEM file (never private key).
if (process.argv[3] || process.argv[4]) {
  if (!process.argv[3] || !process.argv[4]) throw new Error('Both review credential file and public key file are required');
  await check('signed-review-round-trip', async () => {
    const reviewCode = readFileSync(process.argv[3], 'utf8').trim();
    if (!/^[A-Za-z0-9_-]{32,128}$/.test(reviewCode)) throw new Error('Invalid credential file');
    const installationId = randomUUID();
    const response = await requireStatus('/review', 200, { method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ packageName: 'com.moa.babylanguage', installationId, reviewCode }) });
    const envelope = (await response.json()).entitlement;
    const bytes = Buffer.from(envelope.payload, 'base64url');
    if (!verifySignature('sha256', bytes, readFileSync(process.argv[4], 'utf8'), Buffer.from(envelope.signature, 'base64url'))) throw new Error('Invalid signature');
    const claims = JSON.parse(bytes);
    const now = Date.now();
    if (claims.version !== 1 || claims.grantSource !== 'play-review' || claims.basePlanId !== 'play-review' ||
        claims.packageName !== 'com.moa.babylanguage' || claims.productId !== 'jigeum_album' || claims.installationId !== installationId ||
        claims.issuedAtMs > now + 60_000 || claims.expiresAtMs <= now || claims.expiresAtMs > claims.issuedAtMs + 86_400_000) {
      throw new Error('Invalid review claims');
    }
  });
}
console.log(JSON.stringify({ origin: origin.origin, checks, livePlayPurchaseTested: false }, null, 2));
if (checks.some(check => !check.pass)) process.exitCode = 1;
