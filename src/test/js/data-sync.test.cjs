const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/data-sync.js'), 'utf8');

test('sync dialog uses cloud sync only', () => {
    assert.match(script, /\/api\/cloud-sync\/status/);
    assert.match(script, /\/api\/cloud-sync\/run/);
    assert.match(script, /Sync now/);
    assert.match(script, /Sync settings are not configured/);
    assert.doesNotMatch(script, /Export CSV/);
    assert.doesNotMatch(script, /Import CSV/);
    assert.doesNotMatch(script, /Import template/);
});

test('sync dialog is mounted on supported configuration surfaces', () => {
    for (const page of ['/gemba-kaizen-config', '/abnormality-reporting-config', '/gemba-walk-config', '/process-confirmation-config', '/pms-configuration']) {
        assert.match(script, new RegExp(page.replaceAll('-', '\\-')));
    }
    assert.match(script, /master-plant-card/);
    assert.match(script, /master-process-card/);
});
