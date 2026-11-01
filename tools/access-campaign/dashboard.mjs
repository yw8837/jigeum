import { createServer } from 'node:http';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { CODE_CAMPAIGN } from '../../outputs/PlayVerifier/src/access-codes.js';
import { createGcsJsonStore, readCampaignSlots, validateControl, controlName } from '../../outputs/PlayVerifier/src/campaign-reservations.js';

export function operatorTokenProvider(gcloudPath) {
  let cached; let flight;
  return async () => {
    if (cached?.expires > Date.now()) return cached.token;
    if (!flight) flight = (async () => {
      // Use the already authenticated operator account. Never expose stdout/stderr to the browser or log it.
      const script = fileURLToPath(new URL('./operator-token.ps1', import.meta.url));
      const { stdout } = await promisify(execFile)('powershell.exe', [
        '-NoProfile', '-NonInteractive', '-File', script, '-GcloudPath', gcloudPath
      ], { windowsHide: true, timeout: 30_000, maxBuffer: 16_384 });
      const token = stdout.trim();
      if (!token || /\s/.test(token)) throw new Error('Operator authentication unavailable');
      cached = { token, expires: Date.now() + 5 * 60_000 };
      return token;
    })();
    try { return await flight; } finally { flight = null; }
  };
}

export function createCampaignDashboard({ store, html, clock = Date.now }) {
  const csrf = randomBytes(32).toString('hex');
  let busy = false;
  return createServer(async (req, res) => {
    const port = res.socket.localPort;
    const origin = `http://127.0.0.1:${port}`;
    const headers = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-" + csrf + "'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'" };
    const reply = (status, data, type = 'application/json; charset=utf-8') => {
      res.writeHead(status, { ...headers, 'Content-Type': type });
      res.end(type.startsWith('application/json') ? JSON.stringify(data) : data);
    };
    if (req.headers.host !== `127.0.0.1:${port}` || !['127.0.0.1', '::ffff:127.0.0.1'].includes(req.socket.remoteAddress))
      return reply(403, { error: 'local_only' });
    if (req.headers.origin && req.headers.origin !== origin) return reply(403, { error: 'same_origin_required' });
    if (req.method === 'GET' && req.url === '/api/health') return reply(200, { app: 'agattok-campaign-local', version: 1 });
    if (req.method === 'GET' && req.url === '/') return reply(200, html.replaceAll('__NONCE__', csrf), 'text/html; charset=utf-8');
    if (req.method === 'GET' && req.url === '/api/state') {
      try {
        const signal = AbortSignal.timeout(30_000);
        const [object, slots] = await Promise.all([store.get(controlName, { signal }), readCampaignSlots(store, signal)]);
        const control = validateControl(object?.value);
        const registered = slots.filter(Boolean).length;
        return reply(200, { acceptingNew: control.acceptingNew, registered, remaining: 100 - registered,
          limit: 100, checkedAt: clock(), purchases: null });
      } catch { return reply(503, { error: '현황을 읽지 못했어요. PC의 운영자 로그인과 인터넷 연결을 확인해 주세요.' }); }
    }
    if (req.method !== 'POST' || req.url !== '/api/control') return reply(404, { error: 'not_found' });
    const supplied = Buffer.from(String(req.headers['x-campaign-csrf'] ?? ''));
    if (req.headers.origin !== origin || supplied.length !== csrf.length || !timingSafeEqual(supplied, Buffer.from(csrf)) ||
        req.headers['content-type'] !== 'application/json') return reply(403, { error: 'same_origin_required' });
    if (busy) return reply(409, { error: '이전 변경을 처리 중이에요. 현황을 새로 확인해 주세요.' });
    busy = true;
    try {
      req.setTimeout(5_000, () => req.destroy());
      let body = '';
      for await (const chunk of req) { body += chunk.toString('utf8'); if (body.length > 256) return reply(413, { error: 'too_large' }); }
      req.setTimeout(0);
      const input = JSON.parse(body);
      if (!input || Object.keys(input).length !== 1 || typeof input.acceptingNew !== 'boolean') return reply(400, { error: 'invalid_control' });
      const signal = AbortSignal.timeout(30_000);
      const object = await store.get(controlName, { metadata: true, signal });
      const control = validateControl(object?.value);
      const next = { ...control, campaignId: CODE_CAMPAIGN, acceptingNew: input.acceptingNew, updatedAtMs: clock() };
      if (!await store.put(controlName, next, object.generation, { signal }))
        return reply(409, { error: '다른 창에서 상태가 바뀌었어요. 새로 확인한 뒤 다시 눌러 주세요.' });
      return reply(200, { acceptingNew: next.acceptingNew });
    } catch { return reply(503, { error: '변경을 확인하지 못했어요. 현황을 새로 확인해 주세요.' }); }
    finally { busy = false; }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const gcloudPath = process.env.AGATTOK_GCLOUD_PATH;
  if (!gcloudPath) throw new Error('Use Start-Dashboard.ps1 with the existing operator gcloud path');
  const store = createGcsJsonStore({ bucket: 'jigeum-play-20261001-code-redemptions', accessToken: operatorTokenProvider(gcloudPath) });
  const html = await readFile(new URL('./dashboard.html', import.meta.url), 'utf8');
  const server = createCampaignDashboard({ store, html });
  server.listen(8765, '127.0.0.1', () => console.log('Agattok local dashboard: http://127.0.0.1:8765/'));
  server.on('error', () => { console.error('Local dashboard could not start. Check port 8765.'); process.exitCode = 1; });
}
