(function () {
    'use strict';
    function mount(host, datasetSelection, existingTrigger) {
        const trigger = existingTrigger || document.createElement('button');
        if (!existingTrigger) {
            trigger.type = 'button';
            trigger.className = 'data-sync-trigger';
            trigger.innerHTML = '<i class="fa-solid fa-arrows-rotate" aria-hidden="true"></i> Sync';
        }
        trigger.setAttribute('aria-haspopup', 'dialog');
        const dialog = document.createElement('div');
        dialog.className = 'data-sync-dialog';
        dialog.hidden = true;
        dialog.innerHTML = '<div class="data-sync-backdrop"></div><section class="data-sync-panel" role="dialog" aria-modal="true" aria-labelledby="data-sync-title">' +
            '<div class="data-sync-dialog-header"><div><span class="data-sync-eyebrow">Cloud to on-premises</span><h2 id="data-sync-title">Sync data</h2></div>' +
            '<button type="button" class="data-sync-close" aria-label="Close sync"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>' +
            '<p class="data-sync-copy">Imports cloud data using its IDs. Matching local records are replaced, missing IDs are added, and other local records are kept.</p>' +
            '<div class="data-sync-progress-track"><div class="data-sync-progress-bar"></div></div>' +
            '<p class="data-sync-status" role="status" aria-live="polite">Ready to sync.</p>' +
            '<div class="data-sync-actions"><button type="button" class="data-sync-button data-sync-run"><i class="fa-solid fa-arrows-rotate" aria-hidden="true"></i> Sync now</button>' +
            '<button type="button" class="data-sync-button data-sync-ok" hidden>OK</button>' +
            '<button type="button" class="data-sync-button data-sync-cancel">Cancel</button></div></section>';
        const target = host.classList && host.classList.contains('footer-btn-card') ? host.querySelector('.footer-btn-card-header') : document.querySelector('.top-header .header-right');
        if (!existingTrigger) {
            const triggerTarget = target && (typeof target.appendChild === 'function' || typeof target.prepend === 'function') ? target : host;
            if (typeof triggerTarget.appendChild === 'function') triggerTarget.appendChild(trigger); else triggerTarget.prepend(trigger);
        }
        if (typeof document.body.appendChild === 'function') document.body.appendChild(dialog);
        const panel = dialog.querySelector('.data-sync-panel');
        const status = panel.querySelector('.data-sync-status');
        const progress = panel.querySelector('.data-sync-progress-bar');
        const runButton = panel.querySelector('.data-sync-run');
        const okButton = panel.querySelector('.data-sync-ok');
        const closeDialog = () => { dialog.hidden = true; document.body.classList.remove('drawer-open'); };
        const openDialog = () => { dialog.hidden = false; document.body.classList.add('drawer-open'); panel.querySelector('.data-sync-close').focus(); };
        const setStatus = data => {
            const state = String(data.status || 'IDLE').toUpperCase();
            status.textContent = (data.lastRunAt ? 'Last run: ' + data.lastRunAt.replace('T', ' ') + '. ' : '') + (data.message || state);
            status.className = 'data-sync-status data-sync-status-' + state.toLowerCase();
            progress.className = 'data-sync-progress-bar data-sync-progress-' + state.toLowerCase();
            runButton.disabled = state === 'RUNNING';
            okButton.hidden = state !== 'SUCCESS' && state !== 'SUCCESS_WITH_WARNINGS';
        };
        const request = async (url, options) => {
            const response = await fetch(url, options);
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data.message || 'Sync request failed.');
            return data;
        };
        async function refreshStatus() {
            try {
                const data = await request('/api/cloud-sync/status');
                if (data.configured === false) setStatus({ status: 'ERROR', message: 'Sync settings are not configured. Save them in Cloud Sync Configuration first.' });
                else setStatus(data);
                return data;
            } catch (error) { setStatus({ status: 'ERROR', message: error.message }); return null; }
        }
        async function runSync() {
            runButton.disabled = true;
            setStatus({ status: 'RUNNING', message: 'Starting sync...' });
            try {
                const query = datasetSelection ? '?dataset=' + encodeURIComponent(datasetSelection) : '';
                await request('/api/cloud-sync/run' + query, { method: 'POST' });
                const timer = setInterval(async () => { const data = await refreshStatus(); if (!data || data.status !== 'RUNNING') clearInterval(timer); }, 1500);
            } catch (error) { setStatus({ status: 'ERROR', message: error.message }); }
        }
        trigger.addEventListener('click', () => { openDialog(); refreshStatus(); });
        panel.querySelector('.data-sync-close').addEventListener('click', closeDialog);
        panel.querySelector('.data-sync-cancel').addEventListener('click', closeDialog);
        okButton.addEventListener('click', () => { closeDialog(); window.location.reload(); });
        dialog.querySelector('.data-sync-backdrop').addEventListener('click', closeDialog);
        runButton.addEventListener('click', runSync);
        if (document.addEventListener) document.addEventListener('keydown', event => { if (event.key === 'Escape' && !dialog.hidden) closeDialog(); });
    }
    function datasetForCard(card) {
        const category = card.getAttribute('data-category') || '';
        const mappings = {
            'master-plant-card': 'plant-master:',
            'master-gemba-kaizen-card': 'kaizen-master:',
            'master-abnormality-card': 'abnormality-master:',
            'master-gemba-walk-card': 'walk-master:',
            'master-process-card': 'process-master:'
        };
        const prefix = Object.keys(mappings).find(name => card.classList.contains(name));
        return prefix && category ? mappings[prefix] + category : '';
    }
    function init() {
        const pageDatasets = {
            '/gemba-kaizen-config': 'gemba-kaizen',
            '/abnormality-reporting-config': 'abnormality',
            '/gemba-walk-config': 'gemba-walk',
            '/process-confirmation-config': 'process-confirmation',
            '/pms-configuration': 'users'
        };
        const pageDataset = pageDatasets[window.location.pathname];
        if (pageDataset) {
            const content = document.querySelector('.content-area');
            if (content) mount(content, pageDataset);
        }
        if (window.location.pathname === '/settings') {
            document.querySelectorAll('.master-plant-sync-btn').forEach(button =>
                mount(button.parentElement, 'plant-master:PLANT\nplant-master:DEPARTMENT\nplant-master:PROCESS_AREA\nplant-master:DESIGNATION', button));
            ['master-plant-card', 'master-gemba-kaizen-card', 'master-abnormality-card', 'master-gemba-walk-card', 'master-process-card'].forEach(className =>
                document.querySelectorAll('.' + className + '[data-category]').forEach(card => mount(card, datasetForCard(card))));
        }
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
}());
