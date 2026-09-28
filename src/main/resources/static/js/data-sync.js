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
        const reportActions = host.querySelector('.report-page-actions');
        const target = reportActions || document.querySelector('.top-header .header-right');
        if (reportActions) trigger.className = 'issue-add-button';
        if (!existingTrigger) {
            const triggerTarget = target && (typeof target.appendChild === 'function' || typeof target.prepend === 'function') ? target : host;
            if (reportActions) {
                reportActions.classList.add('report-actions-with-sync');
                const addButton = reportActions.querySelector('button:nth-child(2)');
                if (addButton) addButton.insertAdjacentElement('afterend', trigger);
                else reportActions.appendChild(trigger);
            } else if (typeof triggerTarget.appendChild === 'function') triggerTarget.appendChild(trigger); else triggerTarget.prepend(trigger);
        }
        if (typeof document.body.appendChild === 'function') document.body.appendChild(dialog);
        const panel = dialog.querySelector('.data-sync-panel');
        if (trigger.classList.contains('master-header-sync') && !datasetSelection) {
            panel.querySelector('.data-sync-copy').textContent = 'Runs the datasets saved in Cloud Sync Configuration. SMTP, email scheduler, connection settings and license data are not copied.';
        }
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
    function masterPageDatasets(pathname, search) {
        if (pathname === '/pms-configuration') return 'users';
        if (['/sync-configuration', '/email-configuration', '/smtp-configuration'].includes(pathname)) return '';
        if (pathname !== '/settings') return null;
        const pages = {
            'kpi-plant-name': 'plant-master:PLANT\nplant-master:DEPARTMENT\nplant-master:PROCESS_AREA\nplant-master:DESIGNATION',
            'master-designation': 'plant-master:DESIGNATION',
            'master-abnormality': 'abnormality-master:ABT_TAG_TYPE\nabnormality-master:ABNORMALITY_DEFECT_TYPE',
            'master-gemba-walk': 'walk-master:GEMBA_CATEGORY\nwalk-master:LIFE_SAVER_RULE',
            'master-gemba-kaizen': 'kaizen-master:CLASSIFICATION_OF_KAIZEN',
            'master-process': 'process-master:ZM_OBSERVATION\nprocess-master:PM_OBSERVATION\nprocess-master:OM_OBSERVATION\nprocess-master:QM_OBSERVATION'
        };
        return pages[new URLSearchParams(search).get('config')] ?? null;
    }
    function mountMasterHeader(datasetSelection) {
        const header = document.querySelector('.top-header .header-right');
        if (!header || header.querySelector('.master-header-sync')) return;
        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'data-sync-trigger master-header-sync';
        trigger.setAttribute('data-preserve-header-right', 'true');
        trigger.innerHTML = '<i class="fa-solid fa-arrows-rotate" aria-hidden="true"></i> Sync';
        header.insertBefore(trigger, header.querySelector('.pms-profile'));
        mount(header, datasetSelection, trigger);
    }
    function init() {
        const masterDatasets = masterPageDatasets(window.location.pathname, window.location.search);
        if (masterDatasets !== null) mountMasterHeader(masterDatasets);
        const pageDatasets = {
            '/gemba-kaizen-config': 'gemba-kaizen',
            '/abnormality-reporting-config': 'abnormality',
            '/gemba-walk-config': 'gemba-walk',
            '/process-confirmation-config': 'process-confirmation'
        };
        const pageDataset = pageDatasets[window.location.pathname];
        if (pageDataset) {
            const content = document.querySelector('.content-area');
            if (content) mount(content, pageDataset);
        }
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
}());
