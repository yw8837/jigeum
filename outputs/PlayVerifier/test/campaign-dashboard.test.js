import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { once } from 'node:events';
import { createCampaignDashboard } from '../../../tools/access-campaign/dashboard.mjs';
import { controlName } from '../src/campaign-reservations.js';

test('PC dashboard rejects remote origins/hosts and only changes control with local CSRF + generation CAS', async t => {
  let control = { version: 1, campaignId: 'launch-20261027', limit: 100, acceptingNew: true };
  let writes = 0; let conflict = false;
  const store = {
    get: async name => name === controlName ? { value: control, generation: '42' } : null,
    put: async (name, value, generation) => { assert.equal(name, controlName); assert.equal(generation, '42');
      if (conflict) return false; writes++; control = value; return true; }
  };
  const server = createCampaignDashboard({ store, html: '<script nonce="__NONCE__">const csrf="__NONCE__"</script>' });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;
  const home = await fetch(base); const html = await home.text();
  const csrf = html.match(/nonce="([a-f0-9]+)"/)[1];
  assert.equal(home.headers.get('cache-control'), 'no-store');
  assert.match(home.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  const status = await (await fetch(base + '/api/state')).json();
  assert.equal(status.registered, 0); assert.equal(status.purchases, null);
  assert.equal((await fetch(base + '/api/state', { headers: { Origin: 'https://attacker.example' } })).status, 403);
  const wrongHost = await new Promise(resolve => { http.get(base, { headers: { Host: 'attacker.example' } }, r => { r.resume(); resolve(r.statusCode); }); });
  assert.equal(wrongHost, 403);
  const post = headers => fetch(base + '/api/control', { method: 'POST', headers,
    body: JSON.stringify({ acceptingNew: false }) });
  assert.equal((await post({ 'Content-Type': 'application/json', Origin: base })).status, 403);
  assert.equal((await post({ 'Content-Type': 'application/json', Origin: 'https://attacker.example', 'X-Campaign-CSRF': csrf })).status, 403);
  assert.equal(writes, 0);
  const headers = { 'Content-Type': 'application/json', Origin: base, 'X-Campaign-CSRF': csrf };
  assert.equal((await post(headers)).status, 200); assert.equal(writes, 1); assert.equal(control.acceptingNew, false);
  conflict = true;
  assert.equal((await post(headers)).status, 409); assert.equal(writes, 1);
  assert.equal((await fetch(base + '/api/reset', { method: 'POST', headers })).status, 404);
});
