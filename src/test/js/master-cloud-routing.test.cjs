const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('src/main/resources/static/js/master-cloud-routing.js', 'utf8');
async function check(enabled, authorized) {
  const listeners = {};
  const popup = { opener: {}, location: '', close() {} };
  let requests = 0;
  const document = { body: { dataset: { cloudManagedEdits: String(enabled) }, classList: { contains: x => x === 'user-management-page' } }, addEventListener: (type, callback) => { listeners[type] = callback; } };
  const window = { location: { origin: 'http://local.test' }, open: () => popup, alert: msg => { throw Error(msg); }, PmsPermissions: { canEdit: () => authorized } };
  vm.runInNewContext(source, { document, window, URL, URLSearchParams, fetch: async () => { requests++; return { ok: true, json: async () => ({ cloudUrl: 'https://cloud.test' }) }; } });
  if (!enabled) { assert.equal(Object.keys(listeners).length, 0); return; }
  let stopped = false;
  const button = { closest: () => button };
  listeners.click({ target: button, preventDefault() {}, stopImmediatePropagation() { stopped = true; } });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(stopped, authorized);
  assert.equal(requests, authorized ? 1 : 0);
  if (authorized) { assert.equal(popup.location, 'https://cloud.test/pms-configuration'); assert.equal(popup.opener, null); }
}
(async () => { await check(false, true); await check(true, false); await check(true, true); })().catch(e => { console.error(e); process.exitCode = 1; });
