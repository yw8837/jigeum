const METADATA_TOKEN_URL = 'http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token';
const PUBLISHER_SCOPE = 'https://www.googleapis.com/auth/androidpublisher';

async function limitedJson(response) {
  const reader = response.body?.getReader();
  if (!reader) throw new Error('Workload credentials unavailable');
  const chunks = [];
  let bytes = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      bytes += value.length;
      if (bytes > 16_384) throw new Error('Workload credentials response too large');
      chunks.push(value);
    }
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } finally { await reader.cancel().catch(() => {}); }
}

/** Cloud Run workload ADC, scoped via IAM Credentials; no exported account key. */
export function createMetadataTokenProvider({ serviceAccountEmail, fetchImpl = fetch, timeoutMs = 5000, clock = Date.now }) {
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$/.test(serviceAccountEmail ?? '')) {
    throw new Error('A runtime service account email is required');
  }
  return async () => {
    const bootstrap = await fetchImpl(METADATA_TOKEN_URL, {
      headers: { 'Metadata-Flavor': 'Google' },
      signal: AbortSignal.timeout(timeoutMs), redirect: 'error'
    });
    if (!bootstrap.ok || bootstrap.headers.get('Metadata-Flavor') !== 'Google') {
      throw new Error('Workload metadata credentials unavailable');
    }
    const metadata = await limitedJson(bootstrap);
    if (typeof metadata.access_token !== 'string' || !metadata.access_token || /[\r\n]/.test(metadata.access_token)) {
      throw new Error('Workload metadata credentials invalid');
    }
    const response = await fetchImpl(
      `https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${encodeURIComponent(serviceAccountEmail)}:generateAccessToken`, {
        method: 'POST',
        headers: { authorization: `Bearer ${metadata.access_token}`, 'content-type': 'application/json' },
        body: JSON.stringify({ scope: [PUBLISHER_SCOPE], lifetime: '3600s' }),
        signal: AbortSignal.timeout(timeoutMs), redirect: 'error'
      });
    if (!response.ok) throw new Error('Scoped workload credentials unavailable');
    const scoped = await limitedJson(response);
    const expiresAt = Date.parse(scoped.expireTime ?? '');
    if (typeof scoped.accessToken !== 'string' || !scoped.accessToken || /[\r\n]/.test(scoped.accessToken) ||
        !Number.isSafeInteger(expiresAt) || expiresAt <= clock()) {
      throw new Error('Scoped workload credentials invalid');
    }
    return { access_token: scoped.accessToken, expires_in: Math.min(3600, (expiresAt - clock()) / 1000) };
  };
}
