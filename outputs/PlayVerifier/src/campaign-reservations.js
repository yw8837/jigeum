import { CODE_CAMPAIGN } from './access-codes.js';

export const CAMPAIGN_LIMIT = 100;
export const controlName = `campaigns/${CODE_CAMPAIGN}/control.json`;
export const slotName = index => `campaigns/${CODE_CAMPAIGN}/slots/${String(index + 1).padStart(3, '0')}.json`;
const hashPattern = /^[0-9a-f]{64}$/;

export function validateControl(value) {
  if (value?.version !== 1 || value.campaignId !== CODE_CAMPAIGN || value.limit !== CAMPAIGN_LIMIT ||
      typeof value.acceptingNew !== 'boolean') throw new Error('Invalid campaign control');
  return value;
}

export function validateSlot(value) {
  if (value?.version !== 1 || value.campaignId !== CODE_CAMPAIGN || !hashPattern.test(value.installationHash ?? '') ||
      !Number.isSafeInteger(value.reservedAtMs) || value.reservedAtMs <= 0) throw new Error('Invalid campaign slot');
  return value;
}

// Bounded parallel reads; no listing or overwrite permission is needed by the app server.
export async function readCampaignSlots(store, signal) {
  const slots = Array(CAMPAIGN_LIMIT); let next = 0;
  await Promise.all(Array.from({ length: 8 }, async () => {
    while (next < CAMPAIGN_LIMIT) {
      const index = next++;
      signal?.throwIfAborted();
      const object = await store.get(slotName(index), { signal });
      slots[index] = object ? validateSlot(object.value) : null;
    }
  }));
  return slots;
}

/** Immutable, ordered slots cap registration at 100 across instances/restarts/retries. */
export function createCampaignReservations({ store, clock = Date.now }) {
  return async ({ installationHash, campaignId, signal }) => {
    if (campaignId !== CODE_CAMPAIGN || !hashPattern.test(installationHash ?? '')) throw new Error('Invalid campaign reservation');
    const slots = await readCampaignSlots(store, signal);
    // Closing new registration never revokes a previously registered installation.
    if (slots.some(slot => slot?.installationHash === installationHash)) return true;
    for (let index = 0; index < CAMPAIGN_LIMIT; index++) {
      if (slots[index]) continue;
      const control = await store.get(controlName, { signal });
      if (!validateControl(control?.value).acceptingNew) return false;
      const value = { version: 1, campaignId, installationHash, reservedAtMs: clock() };
      if (await store.create(slotName(index), value, { signal })) return true;
      // A stale snapshot cannot skip the winning slot. This also deduplicates concurrent requests
      // from the same installation, even when they arrive at different server instances.
      const winner = await store.get(slotName(index), { signal });
      if (validateSlot(winner?.value).installationHash === installationHash) return true;
    }
    return false;
  };
}

/** JSON-only GCS adapter. Tokens stay in memory; no credentials, raw codes or device IDs are logged. */
export function createGcsJsonStore({ bucket, accessToken, fetchImpl = fetch, timeoutMs = 5000, clock = Date.now }) {
  if (!/^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/.test(bucket ?? '')) throw new Error('Invalid campaign bucket');
  let credential; let tokenFlight;
  async function token() {
    if (accessToken) return accessToken();
    if (credential?.until > clock() + 60_000) return credential.token;
    if (!tokenFlight) tokenFlight = (async () => {
      const response = await fetchImpl('http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token', {
        headers: { 'Metadata-Flavor': 'Google' }, redirect: 'error', signal: AbortSignal.timeout(timeoutMs)
      });
      if (!response.ok) throw new Error('Campaign identity unavailable');
      const data = await limitedJson(response);
      if (typeof data.access_token !== 'string' || !data.access_token || /[\r\n]/.test(data.access_token) ||
          !Number.isFinite(data.expires_in) || data.expires_in <= 0) throw new Error('Invalid campaign identity');
      credential = { token: data.access_token, until: clock() + Math.min(data.expires_in, 3600) * 1000 };
      return credential.token;
    })();
    try { return await tokenFlight; } finally { tokenFlight = null; }
  }
  async function request(url, options = {}) {
    const auth = await token();
    const signal = options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(timeoutMs)]) : AbortSignal.timeout(timeoutMs);
    return fetchImpl(url, { ...options, headers: { authorization: `Bearer ${auth}`, 'content-type': 'application/json' }, redirect: 'error', signal });
  }
  function objectUrl(name) {
    if (!/^campaigns\/launch-20261027\/(control\.json|slots\/\d{3}\.json)$/.test(name)) throw new Error('Invalid campaign object');
    return `https://storage.googleapis.com/storage/v1/b/${bucket}/o/${encodeURIComponent(name)}`;
  }
  return {
    async get(name, { signal, metadata = false } = {}) {
      const url = objectUrl(name);
      let generation;
      if (metadata) {
        const info = await request(url + '?fields=generation', { signal });
        if (info.status === 404) { await info.body?.cancel(); return null; }
        if (!info.ok) { await info.body?.cancel(); throw new Error('Campaign metadata unavailable'); }
        generation = (await limitedJson(info)).generation;
        if (!/^[1-9][0-9]*$/.test(generation ?? '')) throw new Error('Invalid object generation');
      }
      const response = await request(url + '?alt=media' + (generation ? `&generation=${generation}` : ''), { signal });
      if (response.status === 404 && !generation) { await response.body?.cancel(); return null; }
      if (!response.ok) { await response.body?.cancel(); throw new Error('Campaign lookup unavailable'); }
      return { value: await limitedJson(response), generation };
    },
    async create(name, value, { signal } = {}) {
      return this.put(name, value, '0', { signal });
    },
    async put(name, value, generation, { signal } = {}) {
      objectUrl(name);
      if (!/^(0|[1-9][0-9]*)$/.test(String(generation))) throw new Error('Generation precondition required');
      const response = await request(`https://storage.googleapis.com/upload/storage/v1/b/${bucket}/o?uploadType=media&name=${encodeURIComponent(name)}&ifGenerationMatch=${generation}`, {
        method: 'POST', body: JSON.stringify(value), signal
      });
      await response.body?.cancel();
      if (response.status === 412) return false;
      if (!response.ok) throw new Error('Campaign write unavailable');
      return true;
    }
  };
}

async function limitedJson(response) {
  const reader = response.body?.getReader();
  if (!reader) throw new Error('Missing storage response');
  const chunks = []; let length = 0;
  try {
    while (true) {
      const next = await reader.read(); if (next.done) break;
      length += next.value.length;
      if (length > 16_384) throw new Error('Storage response too large');
      chunks.push(next.value);
    }
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } finally { await reader.cancel().catch(() => {}); }
}
