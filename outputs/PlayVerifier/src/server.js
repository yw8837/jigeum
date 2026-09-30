import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { createVerifier, validRequest } from './verifier.js';

const MAX_BODY_BYTES = 16_384;

function reply(response, status, body, extraHeaders = {}) {
  if (response.destroyed || response.writableEnded) return;
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    ...extraHeaders
  });
  response.end(JSON.stringify(body));
}

/** Stateless, bounded HTTP adapter. No request body, identifier or token is logged. */
export function createVerificationServer({
  verify,
  maxConcurrent = 32,
  maxRequestsPerMinute = 30,
  maxAddresses = 10_000,
  bodyTimeoutMs = 5_000,
  verificationTimeoutMs = 16_000,
  clock = Date.now
}) {
  const buckets = new Map();
  let active = 0;
  function rateLimited(ip) {
    const now = clock();
    let current = buckets.get(ip);
    if (!current || now - current.start >= 60_000) {
      if (!current && buckets.size >= maxAddresses) {
        for (const [address, bucket] of buckets) {
          if (now - bucket.start >= 60_000) buckets.delete(address);
        }
        // Saturating the address table cannot reset every visitor's quota.
        if (buckets.size >= maxAddresses) return true;
      }
      current = { start: now, count: 0 };
      buckets.set(ip, current);
    }
    return ++current.count > maxRequestsPerMinute;
  }

  const server = createServer(async (request, response) => {
    if (request.method === 'GET' && request.url === '/healthz') return reply(response, 200, { ok: true });
    if (request.method !== 'POST' || request.url !== '/verify') return reply(response, 404, { error: 'not_found' });
    // X-Forwarded-For is deliberately not trusted. The deployment's edge must
    // enforce its own per-client quota before forwarding to this private origin.
    if (rateLimited(request.socket.remoteAddress ?? 'unknown')) {
      return reply(response, 429, { error: 'rate_limited' }, { 'retry-after': '60' });
    }
    if (active >= maxConcurrent) return reply(response, 503, { error: 'busy' }, { 'retry-after': '5' });
    if (!/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] ?? '')) {
      return reply(response, 415, { error: 'content_type' });
    }
    if (Number(request.headers['content-length']) > MAX_BODY_BYTES) {
      return reply(response, 413, { error: 'too_large' }, { connection: 'close' });
    }
    active++;
    let timer;
    const controller = new AbortController();
    const abort = () => controller.abort();
    response.on('close', abort);
    try {
      const body = await new Promise((resolve, reject) => {
        let size = 0;
        const chunks = [];
        const fail = (status, error) => reject(Object.assign(new Error(error), { status }));
        timer = setTimeout(() => fail(408, 'body_timeout'), bodyTimeoutMs);
        request.on('data', chunk => {
          size += chunk.length;
          if (size > MAX_BODY_BYTES) {
            fail(413, 'too_large');
            request.pause();
          } else chunks.push(chunk);
        });
        request.once('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
        request.once('error', () => fail(400, 'request_error'));
        request.once('aborted', () => fail(400, 'request_aborted'));
      });
      clearTimeout(timer);
      let input;
      try { input = JSON.parse(body); } catch { return reply(response, 400, { error: 'invalid_json' }); }
      if (!validRequest(input)) return reply(response, 400, { error: 'invalid_request' });
      const entitlement = await Promise.race([
        verify(input, { signal: controller.signal }),
        new Promise((_, reject) => {
          timer = setTimeout(() => {
            controller.abort();
            reject(new Error('verification_timeout'));
          }, verificationTimeoutMs);
        })
      ]);
      return entitlement ? reply(response, 200, { entitlement }) : reply(response, 403, { error: 'not_entitled' });
    } catch (error) {
      const status = error.status ?? 503;
      return reply(response, status, { error: status === 503 ? 'verification_unavailable' : error.message },
        status === 413 || status === 408 ? { connection: 'close' } : {});
    } finally {
      clearTimeout(timer);
      controller.abort();
      response.off('close', abort);
      active--;
    }
  });
  server.maxConnections = 256;
  server.maxRequestsPerSocket = 100;
  server.requestTimeout = bodyTimeoutMs + verificationTimeoutMs + 1_000;
  server.headersTimeout = Math.min(5_000, server.requestTimeout);
  server.keepAliveTimeout = 5_000;
  return server;
}

function start() {
  const serviceAccount = JSON.parse(readFileSync(process.env.PLAY_SERVICE_ACCOUNT_JSON_PATH, 'utf8'));
  const privateKey = readFileSync(process.env.PLAY_ENTITLEMENT_PRIVATE_KEY_PATH, 'utf8');
  const quota = Number(process.env.ORIGIN_REQUESTS_PER_MINUTE || 30);
  if (!Number.isInteger(quota) || quota < 1 || quota > 100_000) throw new Error('Invalid origin rate limit');
  const server = createVerificationServer({ verify: createVerifier({ serviceAccount, privateKey }), maxRequestsPerMinute: quota });
  const port = Number(process.env.PORT || 8080);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be 1..65535');
  server.listen(port, process.env.HOST || '127.0.0.1');
  for (const signal of ['SIGTERM', 'SIGINT']) {
    process.once(signal, () => {
      server.close(() => process.exit(0));
      setTimeout(() => { server.closeAllConnections(); process.exit(0); }, 20_000).unref();
    });
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) start();
