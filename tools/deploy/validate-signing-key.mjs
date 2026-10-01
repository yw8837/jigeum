import { readFileSync } from 'node:fs';
import { createPrivateKey } from 'node:crypto';

// Validation only. Never generate, export, log or copy an operational key.
try {
  const key = createPrivateKey(readFileSync(process.argv[2]));
  if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails?.namedCurve !== 'prime256v1') process.exitCode = 1;
} catch { process.exitCode = 1; }
