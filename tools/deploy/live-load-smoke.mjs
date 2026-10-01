import { randomUUID } from 'node:crypto';

// Bounded, operator-authorized live smoke: no real purchase/credential or baby data.
const origin = new URL(process.argv[2] ?? '');
if (origin.protocol !== 'https:' || origin.pathname !== '/' || origin.username || origin.password || origin.search || origin.hash) {
  throw new Error('Provide one HTTPS origin');
}
const timings = [];
async function review(installationId) {
  const started = performance.now();
  const response = await fetch(new URL('/review', origin), { method: 'POST',
    headers: { 'content-type': 'application/json' }, redirect: 'error', signal: AbortSignal.timeout(35_000),
    body: JSON.stringify({ packageName: 'com.moa.babylanguage', installationId,
      reviewCode: 'load-smoke-only-INVALID-AAAAAAAAAAAAAAA' }) });
  await response.arrayBuffer();
  timings.push(performance.now() - started);
  return response.status;
}
const checks = [];
const concurrent = await Promise.all(Array.from({ length: 16 }, () => review(randomUUID())));
checks.push({ name: '16-distinct-installations-same-client', pass: concurrent.every(status => status === 403),
  expectedDenied: concurrent.filter(status => status === 403).length });
const installation = randomUUID();
const singleOrigin = [];
for (let i = 0; i < 31; i++) singleOrigin.push(await review(installation));
checks.push({ name: 'one-installation-30-per-minute-cap', pass: singleOrigin.slice(0, 30).every(status => status === 403) && singleOrigin[30] === 429,
  expectedDenied: singleOrigin.filter(status => status === 403).length, limited: singleOrigin.filter(status => status === 429).length });
checks.push({ name: 'another-installation-remains-usable', pass: await review(randomUUID()) === 403 });
const health = await fetch(new URL('/jigeum-health', origin), { redirect: 'error', signal: AbortSignal.timeout(35_000) });
checks.push({ name: 'health-after-burst', pass: health.status === 200 && (await health.json()).ok === true });
const sorted = timings.sort((a, b) => a - b);
console.log(JSON.stringify({ origin: origin.origin, checks, reviewRequests: timings.length,
  latencyMs: { median: Math.round(sorted[Math.ceil(sorted.length * .5) - 1]), p95: Math.round(sorted[Math.ceil(sorted.length * .95) - 1]),
    max: Math.round(sorted.at(-1)) },
  scope: 'warm invalid-credential requests; bounded rate/isolation smoke, not capacity or cold-start benchmark',
  realPurchasesTested: false, realCredentialsUsed: false }, null, 2));
if (checks.some(check => !check.pass)) process.exitCode = 1;
