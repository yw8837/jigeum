import { createHash, createPrivateKey, sign, timingSafeEqual } from 'node:crypto';
import { PACKAGE_NAME, PRODUCT_ID } from './verifier.js';

export function validReviewRequest(input) {
  return !!input && typeof input === 'object' && !Array.isArray(input) &&
    Object.keys(input).length === 3 &&
    input.packageName === PACKAGE_NAME &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(input.installationId ?? '') &&
    typeof input.reviewCode === 'string' && /^[A-Za-z0-9_-]{32,128}$/.test(input.reviewCode);
}

/** Dedicated signed review grants; a purchase callback can never issue one. */
export function createReviewVerifier({ privateKey, reviewCodeSha256, reviewAccessId, clock = Date.now }) {
  if (!reviewCodeSha256 && !reviewAccessId) return async () => null;
  if (!/^[0-9a-f]{64}$/.test(reviewCodeSha256 ?? '') || !/^[A-Za-z0-9_-]{8,128}$/.test(reviewAccessId ?? '')) {
    throw new Error('Invalid review credential configuration');
  }
  const signingKey = typeof privateKey === 'string' || Buffer.isBuffer(privateKey) ? createPrivateKey(privateKey) : privateKey;
  if (signingKey?.asymmetricKeyType !== 'ec' || signingKey?.asymmetricKeyDetails?.namedCurve !== 'prime256v1') {
    throw new Error('Entitlement signing key must be EC P-256');
  }
  const expectedHash = Buffer.from(reviewCodeSha256, 'hex');
  return async input => {
    if (!validReviewRequest(input)) return null;
    const hash = createHash('sha256').update(input.reviewCode).digest();
    if (!timingSafeEqual(hash, expectedHash)) return null;
    const issuedAtMs = clock();
    const expiresAtMs = issuedAtMs + 86_400_000;
    const claims = {
      version: 1, grantSource: 'play-review', packageName: PACKAGE_NAME, productId: PRODUCT_ID,
      basePlanId: 'play-review', reviewAccessId, installationId: input.installationId,
      issuedAtMs, expiresAtMs, subscriptionExpiryMs: expiresAtMs
    };
    const payloadBytes = Buffer.from(JSON.stringify(claims));
    return { payload: payloadBytes.toString('base64url'), signature: sign('sha256', payloadBytes, signingKey).toString('base64url') };
  };
}
