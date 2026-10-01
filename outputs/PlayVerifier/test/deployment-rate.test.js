import test from 'node:test';
import assert from 'node:assert/strict';
import { createVerificationServer } from '../src/server.js';

const input = { packageName: 'com.moa.babylanguage', productId: 'jigeum_album', purchaseToken: 'valid-test-token', installationId: '94be8be6-04c9-4b85-9994-69113bf442d0' };
const other = { ...input, installationId: 'd4efbfe1-7a41-485a-bda9-7e674bc24850' };
async function withServer(options, run) {
  const server = createVerificationServer({ rateLimitMode: 'installation', verify: async () => null, ...options });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
const post = (url, body = input, headers = {}) => fetch(`${url}/verify`, { method: 'POST',
  headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });

test('shared proxy socket does not collapse different installations into one quota', async () => {
  await withServer({ maxRequestsPerMinute: 1 }, async url => {
    assert.equal((await post(url)).status, 403);
    assert.equal((await post(url, other)).status, 403);
    assert.equal((await post(url, input, { 'x-forwarded-for': '203.0.113.77' })).status, 429);
    assert.equal((await post(url, { ...input, installationId: input.installationId.toUpperCase() })).status, 429);
  });
});

test('rotating client identifiers still meets global hard admission limit and recovers', async () => {
  let now = 0;
  await withServer({ clock: () => now, maxGlobalRequestsPerMinute: 2, maxRequestsPerMinute: 1 }, async url => {
    assert.equal((await post(url)).status, 403);
    assert.equal((await post(url, other)).status, 403);
    const third = { ...input, installationId: 'd4efbfe1-7a41-485a-bda9-7e674bc24851' };
    assert.equal((await post(url, third)).status, 429);
    assert.equal((await fetch(`${url}/healthz`)).status, 200);
    now = 60_000;
    assert.equal((await post(url, third)).status, 403);
  });
});

test('installation table saturation does not reset quotas or retain stale slots', async () => {
  let now = 0;
  await withServer({ clock: () => now, maxAddresses: 1, maxRequestsPerMinute: 1 }, async url => {
    assert.equal((await post(url)).status, 403);
    assert.equal((await post(url, other)).status, 429);
    assert.equal((await post(url)).status, 429);
    now = 60_000;
    assert.equal((await post(url, other)).status, 403);
  });
});
