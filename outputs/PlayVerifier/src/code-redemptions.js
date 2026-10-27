/** Private, immutable reservations. Only code/device hashes and time are stored, never media or tokens. */
export function createGcsCodeReservations({ bucket, fetchImpl = fetch, clock = Date.now, timeoutMs = 5000 }) {
  if (!/^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/.test(bucket ?? '')) throw new Error('Invalid redemption bucket');
  let credential;
  let tokenFlight;
  async function accessToken() {
    if (credential && credential.until > clock() + 60_000) return credential.token;
    if (!tokenFlight) tokenFlight = (async () => {
      const response = await fetchImpl('http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token', {
        headers: { 'Metadata-Flavor': 'Google' }, redirect: 'error', signal: AbortSignal.timeout(timeoutMs)
      });
      if (!response.ok) throw new Error('Reservation identity unavailable');
      const body = await limitedJson(response);
      if (typeof body.access_token !== 'string' || !body.access_token || /[\r\n]/.test(body.access_token) ||
          !Number.isFinite(body.expires_in) || body.expires_in <= 0) throw new Error('Invalid reservation identity');
      credential = { token: body.access_token, until: clock() + Math.min(body.expires_in, 3600) * 1000 };
      return credential.token;
    })();
    try { return await tokenFlight; } finally { tokenFlight = null; }
  }
  async function limitedJson(response) {
    const reader = response.body?.getReader();
    if (!reader) throw new Error('Missing storage response');
    const chunks = []; let length = 0;
    try {
      while (true) {
        const next = await reader.read();
        if (next.done) break;
        length += next.value.length;
        if (length > 16_384) throw new Error('Storage response too large');
        chunks.push(next.value);
      }
      return JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } finally { await reader.cancel().catch(() => {}); }
  }
  return async ({ codeHash, installationHash, campaignId, signal }) => {
    if (!/^[0-9a-f]{64}$/.test(codeHash ?? '') || !/^[0-9a-f]{64}$/.test(installationHash ?? '') ||
        campaignId !== 'launch-20261027') throw new Error('Invalid reservation');
    signal?.throwIfAborted();
    const token = await accessToken();
    const name = `${campaignId}/${codeHash}.json`;
    const body = { version: 1, campaignId, codeHash, installationHash, reservedAtMs: clock() };
    const response = await fetchImpl(`https://storage.googleapis.com/upload/storage/v1/b/${bucket}/o?uploadType=media&name=${encodeURIComponent(name)}&ifGenerationMatch=0`, {
      method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      body: JSON.stringify(body), redirect: 'error',
      signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(timeoutMs)]) : AbortSignal.timeout(timeoutMs)
    });
    await response.body?.cancel().catch(() => {});
    if (response.ok) return true;
    if (response.status !== 412) throw new Error('Code reservation unavailable');
    const existing = await fetchImpl(`https://storage.googleapis.com/storage/v1/b/${bucket}/o/${encodeURIComponent(name)}?alt=media`, {
      headers: { authorization: `Bearer ${token}` }, redirect: 'error',
      signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(timeoutMs)]) : AbortSignal.timeout(timeoutMs)
    });
    if (!existing.ok) throw new Error('Code reservation lookup unavailable');
    const record = await limitedJson(existing);
    return record.version === 1 && record.campaignId === campaignId && record.codeHash === codeHash &&
      record.installationHash === installationHash;
  };
}
