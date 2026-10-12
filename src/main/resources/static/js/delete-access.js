(function () {
    'use strict';
    const selectors = [
        '[data-requires-delete]', '.issue-delete', '.btn-delete', '.metric-delete-btn',
        '.kpi-rename-delete-btn', '.master-plant-delete-btn', '.master-abnormality-delete-btn',
        '.master-gemba-walk-delete-btn', '.master-gemba-kaizen-delete-btn', '.master-process-delete-btn',
        '.gw-delete-record', '.gk-delete-record', '.ar-delete-record', '.carlex-delete',
        '.pms-action-btn.delete', '[data-mobile-user-action="delete"]', '[data-mobile-detail-action="delete"]',
        '.pms-confirm-delete', '#confirmDeleteBtn', '#confirmMasterPlantDelete',
        '.remove-performance-field', '.info-button-remove', '.file-remove-btn',
        '.carlex-remove-observation', '.kpi-rename-remove-draft-btn'
    ].join(',');
    const canDelete = () => document.body && document.body.dataset.canDeleteRecords === 'true';
    function isDeleteAction(element) {
        return element.matches(selectors)
            || /^delete\b/i.test((element.textContent || '').trim())
            || /^delete\b/i.test(element.getAttribute('aria-label') || element.getAttribute('title') || '');
    }
    function apply() {
        if (canDelete()) return;
        document.querySelectorAll('button, a, input[type="button"], input[type="submit"], [role="button"]').forEach(element => {
            if (!isDeleteAction(element)) return;
            if (element.dataset.deleteHidden !== 'true') element.dataset.deleteHidden = 'true';
            if ('disabled' in element && !element.disabled) element.disabled = true;
        });
    }
    document.addEventListener('click', function (event) {
        if (canDelete()) return;
        const action = event.target.closest('button, a, input, [role="button"]');
        if (action && isDeleteAction(action)) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);
    function initialize() {
        apply();
        new MutationObserver(apply).observe(document.body, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['disabled', 'class', 'data-can-delete-records']
        });
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
    else initialize();
})();
