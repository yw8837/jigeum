import test from 'node:test';
import assert from 'node:assert/strict';
import { request as httpRequest } from 'node:http';
import { setTimeout as pause } from 'node:timers/promises';
import { createVerificationServer } from '../src/server.js';

const input = { packageName: 'com.moa.babylanguage', productId: 'jigeum_album', purchaseToken: 'valid-token', installationId: '94be8be6-04c9-4b85-9994-69113bf442d0' };
async function withServer(options, run) {
  const server = createVerificationServer({ maxRequestsPerMinute: 1000, ...options });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
const post = (url, body = input, headers = {}) => fetch(`${url}/verify`, {
  method: 'POST', headers: { 'content-type': 'application/json', ...headers },
  body: typeof body === 'string' ? body : JSON.stringify(body), signal: AbortSignal.timeout(3000)
});

test('HTTP happy path, rejection and upstream failure remain distinct and token-free', async () => {
  let mode = 'success';
  await withServer({ verify: async () => {
    if (mode === 'reject') return null;
    if (mode === 'error') throw new Error(`secret ${input.purchaseToken}`);
    return { payload: 'signed', signature: 'signature' };
  } }, async url => {
    const normal = await post(url);
    assert.equal(normal.status, 200);
    assert.equal(normal.headers.get('cache-control'), 'no-store');
    assert.equal((await normal.json()).entitlement.payload, 'signed');
    mode = 'reject'; assert.equal((await post(url)).status, 403);
    mode = 'error'; const failed = await post(url);
    assert.equal(failed.status, 503);
    assert.equal((await failed.text()).includes(input.purchaseToken), false);
    assert.equal((await fetch(`${url}/healthz`)).status, 200);
    assert.equal((await fetch(`${url}/other`)).status, 404);
  });
});

test('HTTP malformed, unusual schema and oversized input do not reach Play', async () => {
  let calls = 0;
  await withServer({ verify: async () => { calls++; return null; } }, async url => {
    for (const body of ['{', 'null', '[]', '{}', { ...input, installationId: '../secret' }, { ...input, purchaseToken: 'invalid\ninput' }, { ...input, productId: 'other' }]) {
      assert.equal((await post(url, body)).status, 400);
    }
    assert.equal((await post(url, input, { 'content-type': 'text/plain' })).status, 415);
    assert.equal((await post(url, input, { 'content-type': 'application/jsonx' })).status, 415);
    assert.equal((await post(url, 'x'.repeat(16_385))).status, 413);
    assert.equal(calls, 0);
  });
});

test('HTTP 60-request burst caps concurrent verification, health stays responsive, then recovers', async () => {
  let active = 0, peak = 0, entered = 0;
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await withServer({ maxConcurrent: 4, verify: async () => {
    active++; entered++; peak = Math.max(peak, active);
    await gate; active--; return { payload: 'ok', signature: 'ok' };
  } }, async url => {
    const requests = Array.from({ length: 60 }, () => post(url));
    const deadline = Date.now() + 2000;
    while (entered < 4 && Date.now() < deadline) await pause(5);
    assert.equal(entered, 4);
    assert.equal((await fetch(`${url}/healthz`)).status, 200);
    await pause(75);
    release();
    const results = await Promise.all(requests);
    assert.equal(peak, 4);
    assert.equal(results.filter(result => result.status === 200).length, 4);
    assert.equal(results.filter(result => result.status === 503).length, 56);
    assert.equal((await post(url)).status, 200);
  });
});

test('HTTP rate limit ignores spoofed forwarding header and expires cleanly', async () => {
  let now = 0;
  await withServer({ maxRequestsPerMinute: 2, clock: () => now, verify: async () => null }, async url => {
    assert.equal((await post(url)).status, 403);
    assert.equal((await post(url)).status, 403);
    const capped = await post(url, input, { 'x-forwarded-for': '1.2.3.4' });
    assert.equal(capped.status, 429);
    assert.equal(capped.headers.get('retry-after'), '60');
    now = 60_000;
    assert.equal((await post(url)).status, 403);
  });
});

test('HTTP verification deadline aborts bounded work and releases its slot', async () => {
  let aborted = false, hanging = true;
  await withServer({ maxConcurrent: 1, verificationTimeoutMs: 50, verify: async (_, { signal }) => {
    if (!hanging) return null;
    return new Promise((resolve, reject) => signal.addEventListener('abort', () => { aborted = true; reject(new Error('aborted')); }, { once: true }));
  } }, async url => {
    assert.equal((await post(url)).status, 503);
    assert.equal(aborted, true);
    hanging = false;
    assert.equal((await post(url)).status, 403);
  });
});

test('HTTP slow/chunked bodies are bounded and do not hold capacity indefinitely', async () => {
  let calls = 0;
  await withServer({ bodyTimeoutMs: 60, maxConcurrent: 1, verify: async () => { calls++; return null; } }, async url => {
    const raw = (chunk, finish) => new Promise((resolve, reject) => {
      const req = httpRequest(`${url}/verify`, { method: 'POST', headers: { 'content-type': 'application/json', 'transfer-encoding': 'chunked' } }, res => {
        res.resume(); res.once('end', () => { req.destroy(); resolve(res.statusCode); });
      });
      req.setTimeout(2000, () => req.destroy(new Error('test timeout')));
      req.on('error', reject);
      req.write(chunk);
      if (finish) req.end();
    });
    assert.equal(await raw('{', false), 408);
    assert.equal(await raw('x'.repeat(20_000), true), 413);
    assert.equal(calls, 0);
    assert.equal((await post(url)).status, 403);
  });
});
