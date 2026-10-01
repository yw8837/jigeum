import { createSign, createPrivateKey, createHash, sign } from 'node:crypto';

export const PACKAGE_NAME = 'com.moa.babylanguage';
export const PRODUCT_ID = 'jigeum_album';
export const BASE_PLANS = new Set(['monthly', 'three-month-prepaid']);
const MAX_ENTITLEMENT_MS = 24 * 60 * 60 * 1000;

function base64url(bytes) { return Buffer.from(bytes).toString('base64url'); }

async function readJsonLimited(response, maxBytes) {
  const reader = response.body?.getReader();
  if (!reader) throw new Error('Google response missing');
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > maxBytes) throw new Error('Google response too large');
      chunks.push(value);
    }
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } finally { await reader.cancel().catch(() => {}); }
}

export function selectEntitlement(subscription, now = Date.now()) {
  // subscriptionsv2.get is scoped to PACKAGE_NAME by its request path. Its response
  // does not repeat packageName, so never require a nonexistent response field.
  if (!subscription || !Array.isArray(subscription.lineItems)) return null;
  if (!['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED']
    .includes(subscription.subscriptionState)) return null;
  const eligible = subscription.lineItems.filter(item => {
    if (!item || typeof item !== 'object') return false;
    const expiry = Date.parse(item.expiryTime ?? '');
    const basePlan = item.offerDetails?.basePlanId;
    const kindMatches = basePlan === 'monthly' ? !!item.autoRenewingPlan :
      basePlan === 'three-month-prepaid' ? !!item.prepaidPlan : false;
    return item.productId === PRODUCT_ID && BASE_PLANS.has(basePlan) && kindMatches && Number.isSafeInteger(expiry) && expiry > now;
  });
  if (!eligible.length) return null;
  const item = eligible.sort((a, b) => Date.parse(b.expiryTime) - Date.parse(a.expiryTime))[0];
  return { basePlanId: item.offerDetails.basePlanId, subscriptionExpiryMs: Date.parse(item.expiryTime) };
}

export function signEntitlement({ installationId, basePlanId, subscriptionExpiryMs, acknowledgedTokenHash }, privateKey, now = Date.now()) {
  if (!BASE_PLANS.has(basePlanId) || !Number.isSafeInteger(subscriptionExpiryMs) || subscriptionExpiryMs <= now) {
    throw new Error('Invalid entitlement claims');
  }
  const claims = {
    version: 1,
    packageName: PACKAGE_NAME,
    productId: PRODUCT_ID,
    basePlanId,
    installationId,
    issuedAtMs: now,
    expiresAtMs: Math.min(now + MAX_ENTITLEMENT_MS, subscriptionExpiryMs),
    subscriptionExpiryMs
  };
  if (acknowledgedTokenHash !== undefined) {
    if (!/^[0-9a-f]{64}$/.test(acknowledgedTokenHash)) throw new Error('Invalid acknowledgment claim');
    claims.acknowledgedTokenHash = acknowledgedTokenHash;
  }
  const payload = base64url(JSON.stringify(claims));
  const signature = base64url(sign('sha256', Buffer.from(payload, 'base64url'), privateKey));
  return { payload, signature };
}

function serviceAccountAssertion(serviceAccount, now) {
  const header = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = base64url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token',
    iat: Math.floor(now / 1000),
    exp: Math.floor(now / 1000) + 3600
  }));
  const input = `${header}.${claims}`;
  const signer = createSign('RSA-SHA256');
  signer.update(input);
  return `${input}.${base64url(signer.sign(serviceAccount.private_key))}`;
}

export function validRequest(input) {
  return !!input && typeof input === 'object' && !Array.isArray(input) &&
    input.packageName === PACKAGE_NAME && input.productId === PRODUCT_ID &&
    typeof input.purchaseToken === 'string' && input.purchaseToken.length >= 8 && input.purchaseToken.length <= 4096 &&
    !/[\s\u0000-\u001f\u007f]/.test(input.purchaseToken) &&
    typeof input.installationId === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(input.installationId);
}

export function createVerifier({ serviceAccount, accessTokenProvider, privateKey, fetchImpl = fetch, clock = Date.now, upstreamTimeoutMs = 7000 }) {
  if (((!serviceAccount?.client_email || !serviceAccount?.private_key) && typeof accessTokenProvider !== 'function') || !privateKey) {
    throw new Error('Service account or workload credentials and entitlement signing key are required');
  }
  if (serviceAccount && accessTokenProvider) {
    throw new Error('Select exactly one Google credential provider');
  }
  const signingKey = typeof privateKey === 'string' || Buffer.isBuffer(privateKey) ? createPrivateKey(privateKey) : privateKey;
  if (signingKey.asymmetricKeyType !== 'ec' || signingKey.asymmetricKeyDetails?.namedCurve !== 'prime256v1') {
    throw new Error('Entitlement signing key must be EC P-256');
  }
  let accessToken = null;
  let accessTokenExpiresAt = 0;
  let tokenRequest = null;
  const lookups = new Map();
  async function googleAccessToken() {
    const now = clock();
    if (accessToken && now < accessTokenExpiresAt - 60_000) return accessToken;
    if (tokenRequest) return tokenRequest;
    tokenRequest = requestAccessToken(now);
    try { return await tokenRequest; } finally { tokenRequest = null; }
  }
  async function requestAccessToken(now) {
    let body;
    if (accessTokenProvider) {
      body = await accessTokenProvider();
    } else {
      const response = await fetchImpl('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
          assertion: serviceAccountAssertion(serviceAccount, now)
        }),
        signal: AbortSignal.timeout(upstreamTimeoutMs)
      });
      if (!response.ok) throw new Error('Google authentication unavailable');
      body = await readJsonLimited(response, 16_384);
    }
    if (typeof body?.access_token !== 'string' || !body.access_token || /[\r\n]/.test(body.access_token)) throw new Error('Google authentication invalid');
    const seconds = Number(body.expires_in);
    if (!Number.isFinite(seconds) || seconds <= 0) throw new Error('Google authentication expiry invalid');
    accessToken = body.access_token;
    accessTokenExpiresAt = now + Math.min(seconds, 3600) * 1000;
    return accessToken;
  }

  async function lookup(purchaseToken) {
    const token = await googleAccessToken();
    const path = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
    const response = await fetchImpl(path, {
      headers: { authorization: `Bearer ${token}`, accept: 'application/json' },
      signal: AbortSignal.timeout(upstreamTimeoutMs)
    });
    if ([400, 404, 410].includes(response.status)) return null;
    if (!response.ok) {
      if (response.status === 401) { accessToken = null; accessTokenExpiresAt = 0; }
      throw new Error('Google verification unavailable');
    }
    return readJsonLimited(response, 131_072);
  }

  async function lookupAndAcknowledge(purchaseToken) {
    const subscription = await lookup(purchaseToken);
    // Never write for pending, expired, wrong-product, or otherwise ineligible purchases.
    if (!selectEntitlement(subscription, clock())) return subscription;
    if (subscription.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') return subscription;
    if (subscription.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_PENDING') {
      throw new Error('Google acknowledgment state unavailable');
    }
    const token = await googleAccessToken();
    const path = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/subscriptions/${PRODUCT_ID}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
    const response = await fetchImpl(path, {
      method: 'POST',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      // No account, installation identifier, or baby data is attached to the purchase.
      body: '{}',
      signal: AbortSignal.timeout(upstreamTimeoutMs)
    });
    // This API returns an empty success body; do not parse or retain error bodies.
    await response.body?.cancel().catch(() => {});
    if (!response.ok) {
      if (response.status === 401) { accessToken = null; accessTokenExpiresAt = 0; }
      // A racing client acknowledgement can produce an error. A later verification
      // must read Play again; never turn a guessed "already acknowledged" error into a grant.
      throw new Error('Google acknowledgment unavailable');
    }
    return subscription;
  }

  return async function verify(input, { signal } = {}) {
    if (!validRequest(input)) return null;
    signal?.throwIfAborted();
    const { purchaseToken, installationId } = input;
    let pending = lookups.get(purchaseToken);
    if (!pending) {
      if (lookups.size >= 32) throw new Error('Google verification busy');
      pending = lookupAndAcknowledge(purchaseToken);
      lookups.set(purchaseToken, pending);
    }
    let subscription;
    try { subscription = await pending; } finally {
      if (lookups.get(purchaseToken) === pending) lookups.delete(purchaseToken);
    }
    signal?.throwIfAborted();
    const now = clock();
    const eligible = selectEntitlement(subscription, now);
    if (!eligible) return null;
    const acknowledgedTokenHash = createHash('sha256').update(purchaseToken).digest('hex');
    return signEntitlement({ ...eligible, installationId, acknowledgedTokenHash }, signingKey, now);
  };
}
