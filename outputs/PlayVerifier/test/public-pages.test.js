import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createPublicPageHandler } from '../src/public-pages.js';

test('public policies work without purchases and protect exact allowlist', async () => {
  const handle = createPublicPageHandler();
  const server = createServer((req, res) => {
    if (!handle(req, res)) { res.writeHead(404); res.end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  try {
    for (const path of ['/', '/privacy', '/terms', '/support']) {
      const response = await fetch(origin + path);
      assert.equal(response.status, 200);
      assert.match(response.headers.get('content-type'), /text\/html; charset=utf-8/);
      assert.match(response.headers.get('content-security-policy'), /default-src 'none'/);
      const body = await response.text();
      assert.match(body, /지금 내 기분이 그래/);
      assert.match(body, /bsfire1237@gmail.com/);
      assert.doesNotMatch(body, /<script|미게시 초안|(?:방침|약관)\s*초안|MVP|게시 전|API 키/);
      const head = await fetch(origin + path, { method: 'HEAD' });
      assert.equal(head.status, 200);
      assert.equal(await head.text(), '');
      assert.equal(head.headers.get('content-length'), response.headers.get('content-length'));
    }
    for (const path of ['/privacy?token=invalid', '/privacy/', '/public/privacy.html', '/%2e%2e/secret', '/%70rivacy']) {
      assert.equal((await fetch(origin + path)).status, 404);
    }
    assert.equal((await fetch(origin + '/privacy', { method: 'POST', body: '{}' })).status, 405);
    assert.throws(() => createPublicPageHandler({ rootDirectory: '/not-a-public-directory' }));
  } finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
});
