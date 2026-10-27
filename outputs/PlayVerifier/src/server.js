import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { createHmac, randomBytes } from 'node:crypto';
import { createVerifier, validRequest } from './verifier.js';
import { createMetadataTokenProvider } from './credentials.js';
import { createReviewVerifier, validReviewRequest } from './review.js';
import { readRuntimeConfig } from './runtime-config.js';
import { createPublicPageHandler } from './public-pages.js';
import { createAccessCodeVerifier, validAccessCodeRequest } from './access-codes.js';
import { createGcsCodeReservations } from './code-redemptions.js';

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
  review = async () => null,
  accessCode = async () => null,
  publicRouteHandler,
  maxConcurrent = 32,
  maxRequestsPerMinute = 30,
  maxGlobalRequestsPerMinute = 1000,
  rateLimitMode = 'socket',
  maxAddresses = 10_000,
  bodyTimeoutMs = 5_000,
  verificationTimeoutMs = 16_000,
  clock = Date.now
}) {
  if (!['socket', 'installation'].includes(rateLimitMode)) throw new Error('Invalid rate limit mode');
  const buckets = new Map();
  const rateSalt = randomBytes(32);
  let globalBucket = { start: clock(), count: 0 };
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
    if (publicRouteHandler?.(request, response) === true) return;
    // The public Google frontend returned its own HTML 404 for /healthz in live
    // deployment. Use a service-specific route externally; keep the local alias.
    if (request.method === 'GET' && ['/jigeum-health', '/healthz'].includes(request.url)) return reply(response, 200, { ok: true });
    if (request.method !== 'POST' || !['/verify', '/review', '/access-code'].includes(request.url)) return reply(response, 404, { error: 'not_found' });
    const now = clock();
    if (now - globalBucket.start >= 60_000) globalBucket = { start: now, count: 0 };
    if (++globalBucket.count > maxGlobalRequestsPerMinute) {
      return reply(response, 429, { error: 'rate_limited' }, { 'retry-after': '60' });
    }
    // A Cloud Run proxy socket is not a client. Deployment uses bounded,
    // salted installation keys; these are not an authenticated client identity.
    // Forwarding headers are never trusted by either mode.
    if (rateLimitMode === 'socket' && rateLimited(request.socket.remoteAddress ?? 'unknown')) {
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
      const isReview = request.url === '/review';
      const isAccessCode = request.url === '/access-code';
      if (!(isAccessCode ? validAccessCodeRequest(input) : isReview ? validReviewRequest(input) : validRequest(input))) return reply(response, 400, { error: 'invalid_request' });
      if (rateLimitMode === 'installation' && rateLimited(createHmac('sha256', rateSalt).update(input.installationId.toLowerCase()).digest('hex'))) {
        return reply(response, 429, { error: 'rate_limited' }, { 'retry-after': '60' });
      }
      const entitlement = await Promise.race([
        (isAccessCode ? accessCode : isReview ? review : verify)(input, { signal: controller.signal }),
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
  const config = readRuntimeConfig();
  const privateKey = readFileSync(config.signingKeyPath, 'utf8');
  const credentials = config.credentialsMode === 'workload' ? {
    accessTokenProvider: createMetadataTokenProvider({ serviceAccountEmail: config.runtimeServiceAccountEmail, timeoutMs: config.upstreamTimeoutMs })
  } : { serviceAccount: JSON.parse(readFileSync(config.serviceAccountPath, 'utf8')) };
  const server = createVerificationServer({
    ...config.server,
    verify: createVerifier({ ...credentials, privateKey, upstreamTimeoutMs: config.upstreamTimeoutMs }),
    review: createReviewVerifier({ privateKey, reviewCodeSha256: config.reviewCodeSha256, reviewAccessId: config.reviewAccessId }),
    accessCode: createAccessCodeVerifier({ privateKey,
      manifest: config.accessCodeManifestPath ? JSON.parse(readFileSync(config.accessCodeManifestPath, 'utf8')) : null,
      reserveDiscount: config.codeRedemptionBucket ? createGcsCodeReservations({ bucket: config.codeRedemptionBucket }) : undefined
    }),
    publicRouteHandler: createPublicPageHandler()
  });
  server.listen(config.port, config.host);
  for (const signal of ['SIGTERM', 'SIGINT']) {
    process.once(signal, () => {
      server.close(() => process.exit(0));
      setTimeout(() => { server.closeAllConnections(); process.exit(0); }, 20_000).unref();
    });
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) start();
