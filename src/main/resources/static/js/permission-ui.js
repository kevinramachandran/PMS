(function () {
    'use strict';
    const editSelectors = [
    "#addRowBtn",
    "#addGembaRowBtn",
    "#addLgtRowBtn",
    "#addAtRowBtn",
    "#addTrainingScheduleRowBtn",
    "#saveTrainingScheduleBtn",
    ".issue-delete",
    ".issue-edit",
    ".issue-add-row-btn",
    "#issueConfigDrawerSave",
    ".file-upload-trigger",
    "#saveKpiCrossColor",
    "#saveKpiPlantNameBtn",
    "#resetKpiPlantNameBtn",
    "#openMasterPlantAddModal",
    "#saveMasterPlantAdd",
    ".master-plant-add-btn",
    ".master-plant-edit-btn",
    ".master-plant-save-btn",
    ".master-plant-delete-btn",
    ".master-abnormality-add-btn",
    ".master-abnormality-edit-btn",
    ".master-abnormality-save-btn",
    ".master-abnormality-delete-btn",
    ".master-gemba-walk-add-btn",
    ".master-gemba-walk-edit-btn",
    ".master-gemba-walk-save-btn",
    ".master-gemba-walk-delete-btn",
    ".master-gemba-kaizen-add-btn",
    ".master-gemba-kaizen-edit-btn",
    ".master-gemba-kaizen-save-btn",
    ".master-gemba-kaizen-delete-btn",
    ".master-process-add-btn",
    ".master-process-edit-btn",
    ".master-process-save-btn",
    ".master-process-delete-btn",
    "#addKpiRenameMetricBtn",
    "#saveKpiRenameAlertColorsBtn",
    ".kpi-rename-visibility-btn",
    ".kpi-rename-edit-btn",
    ".kpi-rename-delete-btn",
    ".kpi-rename-remove-draft-btn",
    ".metrics-custom-save-btn",
    "[data-requires-edit=\"true\"]",
    "button[type=\"submit\"]",
    "input[type=\"submit\"]",
    ".btn-delete",
    ".remove-performance-field",
    ".metric-edit-btn",
    ".metric-delete-btn",
    ".metric-save-inline",
    ".info-button-remove",
    ".file-remove-btn",
    ".master-plant-update-btn",
    ".gw-edit-record",
    ".gw-delete-record",
    ".gk-edit-record",
    ".gk-delete-record",
    ".ar-edit-record",
    ".ar-delete-record",
    ".carlex-edit",
    ".carlex-delete",
    ".carlex-add-observation",
    ".carlex-remove-observation"
].join(',');
    function canEdit(element) {
        if (element && element.closest('.metric-actions, .metric-inline-editor, .metrics-custom-manager')
                && document.body.dataset.canManageMetrics === 'false') return false;
        const scope = element && element.closest('[data-edit-authorized]');
        if (scope) return scope.dataset.editAuthorized === 'true';
        return document.body.dataset.canEditCurrentPage === 'true';
    }
    function isReadControl(element) {
        return element.matches('[type="search"], [type="date"], [type="month"], [data-permission-read-control]')
            || /search|filter|export|month|year/i.test(element.id || '');
    }
    function apply() {
        if (!document.body || !document.body.hasAttribute('data-can-edit-current-page')) return;
        document.querySelectorAll(editSelectors).forEach(element => {
            if (canEdit(element)) return;
            if (element.dataset.permissionHidden !== 'true') element.dataset.permissionHidden = 'true';
            if ('disabled' in element && !element.disabled) element.disabled = true;
        });
        document.querySelectorAll('form input:not([type="hidden"]), form textarea, form select, .form-section input:not([type="hidden"]), .form-section textarea, .form-section select').forEach(element => {
            if (!canEdit(element) && !isReadControl(element) && !element.disabled) element.disabled = true;
        });
    }
    document.addEventListener('click', event => {
        const action = event.target.closest(editSelectors);
        if (action && !canEdit(action)) { event.preventDefault(); event.stopImmediatePropagation(); }
    }, true);
    document.addEventListener('submit', event => {
        if (!canEdit(event.target)) { event.preventDefault(); event.stopImmediatePropagation(); }
    }, true);
    function initialize() {
        apply();
        const observer = new MutationObserver(apply);
        observer.observe(document.body, {childList: true, subtree: true, attributes: true, attributeFilter: ['disabled', 'data-edit-authorized']});
    }
    window.PmsPermissions = { canEdit, apply, isReadControl };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
    else initialize();
})();
