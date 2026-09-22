(function () {
    'use strict';
    const fields = ['cloudUrl', 'cloudUsername', 'downloadFolder', 'processingFolder', 'completedFolder', 'failedFolder', 'delaySeconds', 'scheduleTime', 'datasets'];
    const form = document.getElementById('syncConfigForm');
    const message = document.getElementById('formMessage');
    const status = document.getElementById('syncStatus');
    const statusMessage = document.getElementById('syncMessage');
    const dot = document.getElementById('syncStatusDot');
    const progress = document.getElementById('syncProgress');
    const times = document.getElementById('syncTimes');
    function value(id) { return document.getElementById(id).value; }
    function setStatus(data) {
        const kind = String(data.status || 'IDLE').toLowerCase();
        status.textContent = kind.toUpperCase(); statusMessage.textContent = data.message ? ' - ' + data.message : '';
        dot.className = 'sync-status-dot ' + kind; progress.className = 'sync-progress-bar ' + kind;
        times.textContent = data.lastRunAt ? 'Last run: ' + data.lastRunAt + (data.lastSuccessAt ? ' | Last success: ' + data.lastSuccessAt : '') : '';
    }
    async function request(url, options) { const response = await fetch(url, options); const data = await response.json(); if (!response.ok) throw new Error(data.message || 'Request failed'); return data; }
    async function load() {
        try { const config = await request('/api/cloud-sync/config'); fields.forEach(id => { document.getElementById(id).value = id === 'datasets' && config.effectiveDatasets ? config.effectiveDatasets.join('\n') : config[id] ?? ''; }); document.getElementById('enabled').checked = !!config.enabled; setStatus(await request('/api/cloud-sync/status')); }
        catch (error) { message.textContent = error.message; }
    }
    form.addEventListener('submit', async event => { event.preventDefault(); message.textContent = 'Saving...'; const payload = {}; fields.forEach(id => payload[id] = value(id)); payload.enabled = document.getElementById('enabled').checked; payload.cloudPassword = value('cloudPassword'); try { message.textContent = (await request('/api/cloud-sync/config', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })).message; document.getElementById('cloudPassword').value = ''; } catch (error) { message.textContent = error.message; } });
    document.getElementById('syncNow').addEventListener('click', async () => { message.textContent = 'Starting sync...'; try { await request('/api/cloud-sync/run', { method: 'POST' }); message.textContent = 'Sync started'; setStatus(await request('/api/cloud-sync/status')); const timer = setInterval(async () => { const data = await request('/api/cloud-sync/status'); setStatus(data); if (data.status !== 'RUNNING') clearInterval(timer); }, 1500); } catch (error) { message.textContent = error.message; } });
    load();
}());
