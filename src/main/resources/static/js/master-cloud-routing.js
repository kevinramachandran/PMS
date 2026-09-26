(function () {
    'use strict';

    const actions = [
        '.master-plant-add-btn', '#openMasterPlantAddModal', '#saveMasterPlantAdd', '.master-plant-update-btn',
        '.master-gemba-walk-add-btn', '.master-gemba-walk-edit-btn', '.master-gemba-walk-save-btn', '.master-gemba-walk-delete-btn',
        '.master-gemba-kaizen-add-btn', '.master-gemba-kaizen-edit-btn', '.master-gemba-kaizen-save-btn', '.master-gemba-kaizen-delete-btn',
        '.master-abnormality-add-btn', '.master-abnormality-edit-btn', '.master-abnormality-save-btn', '.master-abnormality-delete-btn',
        '.master-process-add-btn', '.master-process-edit-btn', '.master-process-save-btn', '.master-process-delete-btn'
    ].join(',');
    const masterNewTabActions = [
        '.master-plant-add-btn', '#openMasterPlantAddModal', '#saveMasterPlantAdd', '.master-plant-update-btn',
        '.master-plant-edit-btn', '.master-plant-save-btn',
        '.master-gemba-walk-add-btn', '.master-gemba-walk-edit-btn', '.master-gemba-walk-save-btn',
        '.master-gemba-kaizen-add-btn', '.master-gemba-kaizen-edit-btn', '.master-gemba-kaizen-save-btn',
        '.master-abnormality-add-btn', '.master-abnormality-edit-btn', '.master-abnormality-save-btn',
        '.master-process-add-btn', '.master-process-edit-btn', '.master-process-save-btn'
    ].join(',');
    const userManagementActions = '#openAddUserDrawer, #addUserBtn, #saveEditBtn, .pms-action-btn.edit';
    const reportingPages = [
        { body: 'gemba-walk-config-page', config: 'master-gemba-walk', route: '/gemba-walk-config', form: 'gembaWalkConfigForm', actions: '#gembaWalkAddRecordBtn, #gembaWalkUpdatePageBtn, #gembaWalkSubmitBtn, .gw-edit-record, .gw-delete-record' },
        { body: 'gemba-kaizen-config-page', config: 'master-gemba-kaizen', route: '/gemba-kaizen-config', form: 'gembaKaizenConfigForm', actions: '#gembaKaizenAddRecordBtn, #gembaKaizenUpdatePageBtn, #gembaKaizenSubmitBtn, .gk-edit-record, .gk-delete-record' },
        { body: 'carlex-config-page', config: 'master-process', route: '/process-confirmation-config', form: 'carlexForm', actions: '#carlexAddBtn, #carlexUpdatePageBtn, #carlexSaveBtn, .carlex-view, .carlex-edit, .carlex-delete' },
        { body: 'abnormality-reporting-config-page', config: 'master-abnormality', route: '/abnormality-reporting-config', form: 'abnormalityReportingForm', actions: '#addAbnormalityReportingBtn, #abnormalityUpdatePageBtn, #saveAbnormalityReportingBtn, .ar-edit-record, .ar-delete-record' }
    ];

    function currentReportingPage() {
        return reportingPages.find(page => document.body.classList.contains(page.body));
    }

    function cloudConfigFor(button) {
        const card = button.closest('.master-plant-card, .master-gemba-walk-card, .master-gemba-kaizen-card, .master-abnormality-card, .master-process-card');
        if (!card) return '';
        if (card.classList.contains('master-plant-card')) {
            return card.getAttribute('data-category') === 'DESIGNATION' ? 'master-designation' : 'kpi-plant-name';
        }
        if (card.classList.contains('master-gemba-walk-card')) return 'master-gemba-walk';
        if (card.classList.contains('master-gemba-kaizen-card')) return 'master-gemba-kaizen';
        if (card.classList.contains('master-abnormality-card')) return 'master-abnormality';
        if (card.classList.contains('master-process-card')) return 'master-process';
        return '';
    }

    async function openCloudPage(config, route, openInNewTab) {
        const newWindow = openInNewTab ? window.open('about:blank', '_blank') : null;
        if (newWindow) newWindow.opener = null;
        try {
            const response = await fetch('/api/master-cloud-target?config=' + encodeURIComponent(config), { credentials: 'same-origin' });
            const data = await response.json();
            if (!response.ok) throw new Error(data.message || 'Unable to load Cloud PMS configuration.');
            const rawUrl = String(data.cloudUrl || '').trim();
            if (!rawUrl) throw new Error('Set and save the Cloud PMS URL in Cloud Sync Configuration first.');
            const base = new URL(rawUrl);
            const targetRoute = route || '/settings';
            base.pathname = base.pathname.replace(/\/+$/, '') + targetRoute;
            base.search = targetRoute === '/settings' ? new URLSearchParams({ config: config }).toString() : '';
            base.hash = '';
            if (newWindow) newWindow.location = base.toString();
            else window.location.assign(base.toString());
        } catch (error) {
            if (newWindow) newWindow.close();
            window.alert(error.message || 'Unable to open the configured Cloud PMS page.');
        }
    }

    document.addEventListener('click', function (event) {
        if (document.body.classList.contains('user-management-page') && event.target.closest(userManagementActions)) {
            event.preventDefault();
            event.stopImmediatePropagation();
            openCloudPage('user-management', '/pms-configuration', true);
            return;
        }
        const reporting = currentReportingPage();
        if (reporting && event.target.closest(reporting.actions)) {
            event.preventDefault();
            event.stopImmediatePropagation();
            const deleteActions = {
                'gemba-walk-config-page': '.gw-delete-record',
                'gemba-kaizen-config-page': '.gk-delete-record',
                'carlex-config-page': '.carlex-delete',
                'abnormality-reporting-config-page': '.ar-delete-record'
            }[reporting.body];
            const newTab = !event.target.closest(deleteActions);
            openCloudPage(reporting.config, reporting.route, newTab);
            return;
        }
        const button = event.target.closest(actions);
        if (!button) return;
        const config = cloudConfigFor(button);
        if (!config) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        openCloudPage(config, null, button.matches(masterNewTabActions));
    }, true);

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter') return;
        const reporting = currentReportingPage();
        if (reporting && event.target.closest('#' + reporting.form) && event.target.matches('input, select')) {
            event.preventDefault();
            event.stopImmediatePropagation();
            openCloudPage(reporting.config, reporting.route, true);
            return;
        }
        const card = event.target.closest('.master-plant-card, .master-gemba-walk-card, .master-gemba-kaizen-card, .master-abnormality-card, .master-process-card');
        if (!card || !event.target.matches('input')) return;
        const config = cloudConfigFor(card);
        if (!config) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        openCloudPage(config);
    }, true);

    document.addEventListener('submit', function (event) {
        const reporting = currentReportingPage();
        if (!reporting || event.target.id !== reporting.form) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        openCloudPage(reporting.config, reporting.route, true);
    }, true);
}());
