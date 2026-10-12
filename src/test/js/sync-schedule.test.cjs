const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('saved schedules load, switch modes and submit only active timing fields; manual Sync stays available', async () => {
    const elements = new Map();
    function element(id) {
        if (!elements.has(id)) elements.set(id, {
            value: '', disabled: false, checked: false, listeners: {}, options: [],
            appendChild(option) { this.options.push(option); },
            addEventListener(event, handler) { this.listeners[event] = handler; }
        });
        return elements.get(id);
    }
    const labels = Object.entries({ DAILY: 'scheduleTime', INTERVAL: 'intervalMinutes', TIMES: 'scheduleTimes' })
        .map(([mode, id]) => ({ dataset: { scheduleMode: mode }, querySelector: () => element(id) }));
    labels.push({ dataset: { scheduleMode: 'INTERVAL' }, querySelector: () => element('intervalStartTime') });
    const calls = [];
    const config = {
        cloudUrl: 'https://pms.example.com', cloudUsername: 'sync',
        downloadFolder: 'download', processingFolder: 'processing', completedFolder: 'completed', failedFolder: 'failed',
        delaySeconds: 60, scheduleMode: 'TIMES', scheduleTime: '02:00',
        scheduleTimes: '08:00,12:00,16:00', intervalMinutes: 120, intervalStartTime: '08:30', enabled: true, datasets: 'users'
    };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/sync-configuration.js'), 'utf8'), {
        document: { getElementById: element, querySelectorAll: () => labels, createElement: () => ({}) },
        fetch: async (url, options) => {
            calls.push({ url, options });
            return { ok: true, json: async () => url.endsWith('/config') && !options ? config : { status: 'IDLE', message: 'OK' } };
        },
        setInterval: () => 1, clearInterval: () => {}
    });
    await new Promise(setImmediate);
    assert.equal(element('scheduleTimes').value, config.scheduleTimes);
    assert.equal(element('scheduleTimes').disabled, false);
    assert.equal(element('scheduleTime').disabled, true);
    assert.equal(element('intervalMinutes').disabled, true);
    assert.equal(element('intervalStartTime').value, '08:30');
    assert.equal(element('intervalStartTime').disabled, true);
    assert.equal(labels[2].hidden, false);

    element('scheduleMode').value = 'INTERVAL';
    element('scheduleMode').listeners.change();
    element('intervalMinutes').value = '30';
    await element('syncConfigForm').listeners.submit({ preventDefault() {} });
    let saved = JSON.parse(calls.find(call => call.options?.method === 'PUT').options.body);
    assert.equal(saved.scheduleMode, 'INTERVAL');
    assert.equal(saved.intervalMinutes, '30');
    assert.equal(saved.intervalStartTime, '08:30');
    assert.equal(element('intervalStartTime').disabled, false);
    assert.equal(saved.enabled, true);
    assert.ok(!('scheduleTimes' in saved));
    assert.ok(!('scheduleTime' in saved));
    assert.equal(labels[1].hidden, false);

    element('scheduleMode').value = 'TIMES';
    element('scheduleMode').listeners.change();
    element('scheduleTimes').value = '09:00,17:00';
    await element('syncConfigForm').listeners.submit({ preventDefault() {} });
    saved = JSON.parse(calls.filter(call => call.options?.method === 'PUT').at(-1).options.body);
    assert.equal(saved.scheduleTimes, '09:00,17:00');
    assert.ok(!('intervalMinutes' in saved));
    assert.ok(!('intervalStartTime' in saved));

    element('enabled').checked = false;
    await element('syncNow').listeners.click();
    assert.ok(calls.some(call => call.url === '/api/cloud-sync/run' && call.options.method === 'POST'));
});
