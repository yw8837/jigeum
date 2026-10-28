function integer(env, name, fallback, low, high) {
  const value = Number(env[name] ?? fallback);
  if (!Number.isInteger(value) || value < low || value > high) throw new Error(`Invalid ${name}`);
  return value;
}

export function readRuntimeConfig(env = process.env) {
  const rateLimitMode = env.RATE_LIMIT_MODE ?? 'socket';
  if (!['socket', 'installation'].includes(rateLimitMode)) throw new Error('Invalid RATE_LIMIT_MODE');
  const credentialsMode = env.PLAY_CREDENTIALS_MODE ?? 'service-account';
  if (!['service-account', 'workload'].includes(credentialsMode)) throw new Error('Invalid PLAY_CREDENTIALS_MODE');
  if (credentialsMode === 'workload' && env.PLAY_SERVICE_ACCOUNT_JSON_PATH) throw new Error('Exported account keys are not allowed in workload mode');
  if (!env.PLAY_ENTITLEMENT_PRIVATE_KEY_PATH) throw new Error('Signing key path is required');
  if (!!env.ACCESS_CODE_MANIFEST_PATH !== !!env.CODE_REDEMPTION_BUCKET) throw new Error('Access code manifest and dedicated bucket must be configured together');
  return {
    credentialsMode,
    runtimeServiceAccountEmail: env.PLAY_RUNTIME_SERVICE_ACCOUNT_EMAIL,
    serviceAccountPath: env.PLAY_SERVICE_ACCOUNT_JSON_PATH,
    signingKeyPath: env.PLAY_ENTITLEMENT_PRIVATE_KEY_PATH,
    reviewCodeSha256: env.PLAY_REVIEW_CODE_SHA256,
    reviewAccessId: env.PLAY_REVIEW_ACCESS_ID,
    accessCodeManifestPath: env.ACCESS_CODE_MANIFEST_PATH,
    codeRedemptionBucket: env.CODE_REDEMPTION_BUCKET,
    host: env.HOST ?? '127.0.0.1',
    port: integer(env, 'PORT', 8080, 1, 65535),
    upstreamTimeoutMs: integer(env, 'UPSTREAM_TIMEOUT_MS', 7000, 100, 10_000),
    server: {
      rateLimitMode,
      maxRequestsPerMinute: integer(env, 'ORIGIN_REQUESTS_PER_MINUTE', 30, 1, 100_000),
      maxGlobalRequestsPerMinute: integer(env, 'GLOBAL_REQUESTS_PER_MINUTE', 1000, 1, 100_000),
      maxConcurrent: integer(env, 'MAX_CONCURRENT_VERIFICATIONS', 32, 1, 32),
      maxAddresses: integer(env, 'MAX_RATE_KEYS', 10_000, 1, 10_000),
      bodyTimeoutMs: integer(env, 'BODY_TIMEOUT_MS', 5000, 100, 5000),
      verificationTimeoutMs: integer(env, 'VERIFICATION_TIMEOUT_MS', 16_000, 100, 25_000)
    }
  };
}
