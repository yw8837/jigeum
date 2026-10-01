import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

/** Exact allowlist only. Paths never select arbitrary files or interpolate user input. */
export function createPublicPageHandler({
  rootDirectory = fileURLToPath(new URL('../public/', import.meta.url))
} = {}) {
  const pages = new Map();
  for (const name of ['privacy', 'terms', 'support']) {
    const bytes = readFileSync(join(rootDirectory, `${name}.html`));
    if (bytes.length > 262144) throw new Error('Public page exceeds size limit');
    pages.set(`/${name}`, bytes);
  }
  pages.set('/', pages.get('/support'));
  return (request, response) => {
    const page = pages.get(request.url);
    if (!page) return false;
    const allowed = ['GET', 'HEAD'].includes(request.method);
    response.writeHead(allowed ? 200 : 405, {
      'content-type': 'text/html; charset=utf-8',
      'cache-control': 'public, max-age=300',
      'x-content-type-options': 'nosniff',
      'referrer-policy': 'no-referrer',
      'content-security-policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
      ...(allowed ? { 'content-length': page.length } : { allow: 'GET, HEAD' })
    });
    response.end(allowed && request.method === 'GET' ? page : undefined);
    return true;
  };
}
