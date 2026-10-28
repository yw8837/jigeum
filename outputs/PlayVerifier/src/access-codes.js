import { createHash, createPrivateKey, sign, timingSafeEqual } from 'node:crypto';
import { PACKAGE_NAME, PRODUCT_ID } from './verifier.js';

export const LAUNCH_OFFER_ID = 'launch-2m-half';
export const CODE_CAMPAIGN = 'launch-20261027';
const DAY_MS = 86_400_000;
const sha256 = value => createHash('sha256').update(value).digest('hex');

export function validAccessCodeRequest(input) {
  return !!input && typeof input === 'object' && !Array.isArray(input) &&
    Object.keys(input).length === 3 && input.packageName === PACKAGE_NAME &&
    typeof input.installationId === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(input.installationId) &&
    typeof input.code === 'string' && /^[A-Za-z0-9_-]{32,128}$/.test(input.code);
}

export function validateCodeManifest(manifest) {
  if (!manifest || manifest.schemaVersion !== 1 || manifest.campaignId !== CODE_CAMPAIGN ||
      manifest.offerId !== LAUNCH_OFFER_ID || !/^[0-9a-f]{64}$/.test(manifest.familyCodeSha256 ?? '') ||
      !Array.isArray(manifest.discountCodeSha256) || manifest.discountCodeSha256.length !== 100 ||
      manifest.discountCodeSha256.some(hash => !/^[0-9a-f]{64}$/.test(hash)) ||
      new Set(manifest.discountCodeSha256).size !== 100 ||
      manifest.discountCodeSha256.includes(manifest.familyCodeSha256)) {
    throw new Error('Invalid access code manifest');
  }
  return manifest;
}

/** Codes are not purchases. Only invitation grants unlock full access; offer grants never do. */
export function createAccessCodeVerifier({ privateKey, manifest, reserveDiscount, clock = Date.now }) {
  if (!manifest) return async () => null;
  validateCodeManifest(manifest);
  if (typeof reserveDiscount !== 'function') throw new Error('Durable code reservation is required');
  const key = typeof privateKey === 'string' || Buffer.isBuffer(privateKey) ? createPrivateKey(privateKey) : privateKey;
  if (key?.asymmetricKeyType !== 'ec' || key?.asymmetricKeyDetails?.namedCurve !== 'prime256v1') {
    throw new Error('Entitlement signing key must be EC P-256');
  }
  const familyHash = Buffer.from(manifest.familyCodeSha256, 'hex');
  const discountHashes = new Set(manifest.discountCodeSha256);
  return async (input, { signal } = {}) => {
    if (!validAccessCodeRequest(input)) return null;
    signal?.throwIfAborted();
    const codeHash = sha256(input.code);
    const invitation = timingSafeEqual(Buffer.from(codeHash, 'hex'), familyHash);
    if (!invitation) {
      if (!discountHashes.has(codeHash)) return null;
      const installationHash = sha256(`${CODE_CAMPAIGN}:${input.installationId.toLowerCase()}`);
      if (!await reserveDiscount({ codeHash, installationHash, campaignId: CODE_CAMPAIGN, signal })) return null;
    }
    signal?.throwIfAborted();
    const issuedAtMs = clock();
    const expiresAtMs = issuedAtMs + DAY_MS;
    const claims = {
      version: 1, grantSource: invitation ? 'invitation' : 'launch-offer',
      packageName: PACKAGE_NAME, productId: PRODUCT_ID,
      basePlanId: invitation ? 'invitation' : 'monthly',
      installationId: input.installationId, codeHash,
      issuedAtMs, expiresAtMs, subscriptionExpiryMs: expiresAtMs
    };
    if (invitation) claims.accessId = 'friends-permanent';
    else Object.assign(claims, {
      campaignId: CODE_CAMPAIGN, offerId: LAUNCH_OFFER_ID,
      currency: 'KRW', introductoryPriceMicros: 2_450_000_000,
      introductoryCycles: 2, billingPeriod: 'P1M', renewalPriceMicros: 4_900_000_000
    });
    const bytes = Buffer.from(JSON.stringify(claims));
    return { payload: bytes.toString('base64url'), signature: sign('sha256', bytes, key).toString('base64url') };
  };
}
