(function () {
    'use strict';
    const mobile = window.matchMedia('(max-width: 1023px)');
    const phone = window.matchMedia('(max-width: 600px)');
    function initialize() {
        const sidebar = document.getElementById('sidebar');
        const toggle = document.querySelector('#hamburger, #sidebarToggle, .sidebar-toggle-btn');
        let savedCollapsed = sidebar ? sidebar.classList.contains('collapsed') : false;
        let wasMobile = false;
        let overlay = document.getElementById('sidebarOverlay');
        let closeButton;
        function closeNavigation(restoreFocus, makeInert = true) {
            document.body.classList.remove('pms-mobile-nav-open');
            if (toggle) toggle.setAttribute('aria-expanded', 'false');
            if (sidebar && mobile.matches && makeInert) sidebar.setAttribute('inert', '');
            if (restoreFocus && toggle) toggle.focus();
        }
        function openNavigation() {
            sidebar.removeAttribute('inert');
            document.body.classList.add('pms-mobile-nav-open');
            toggle.setAttribute('aria-expanded', 'true');
            closeButton.focus();
        }
        if (sidebar && toggle) {
            if (!overlay) {
                overlay = document.createElement('div');
                overlay.id = 'sidebarOverlay'; overlay.className = 'sidebar-overlay';
                document.body.appendChild(overlay);
            }
            closeButton = document.createElement('button');
            closeButton.type = 'button'; closeButton.className = 'pms-mobile-nav-close';
            closeButton.textContent = 'Close menu';
            sidebar.prepend(closeButton);
            closeButton.addEventListener('click', () => closeNavigation(true));
            // Some legacy pages initialize their sidebar in jQuery ready, after this handler.
            // Keep their delayed desktop collapse from hiding mobile navigation labels.
            new MutationObserver(function () {
                if (mobile.matches && sidebar.classList.contains('collapsed')) {
                    savedCollapsed = true;
                    sidebar.classList.remove('collapsed');
                }
            }).observe(sidebar, { attributes: true, attributeFilter: ['class'] });
            toggle.setAttribute('aria-controls', sidebar.id);
            toggle.setAttribute('aria-label', 'Open navigation menu');
            if (toggle.tagName !== 'BUTTON') { toggle.setAttribute('role', 'button'); toggle.tabIndex = 0; }
            document.addEventListener('click', function (event) {
                if (!mobile.matches) return;
                if (toggle.contains(event.target)) {
                    event.preventDefault(); event.stopImmediatePropagation();
                    if (document.body.classList.contains('pms-mobile-nav-open')) closeNavigation(true);
                    else openNavigation();
                } else if (event.target === overlay) {
                    event.preventDefault(); event.stopImmediatePropagation(); closeNavigation(true);
                } else if (event.target.closest('.sidebar a[href]:not([href="#"])')) closeNavigation(false, false);
            }, true);
            document.addEventListener('keydown', function (event) {
                if (!mobile.matches) return;
                if (event.target === toggle && (event.key === 'Enter' || event.key === ' ')) {
                    event.preventDefault(); event.stopImmediatePropagation(); toggle.click();
                }
                if (!document.body.classList.contains('pms-mobile-nav-open')) return;
                if (event.key === 'Escape') { event.preventDefault(); closeNavigation(true); }
                if (event.key === 'Tab') {
                    const items = Array.from(sidebar.querySelectorAll('a[href],button,[tabindex="0"]')).filter(el => el.getClientRects().length && !el.disabled);
                    const first = items[0], last = items[items.length - 1];
                    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
                    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
                }
            }, true);
        }
        // Reuse existing table scrollports; only bare tables receive a wrapper.
        const wrappers = '.daily-data-table,.issue-config-table-wrap,.issue-table-wrap,.ts-table-wrap,.gemba-table-wrap,.lgt-table-wrap,.gw-table-shell,.gk-table-shell,.at-table-wrap,.gw-detail-table-wrap,.gk-detail-table-wrap,.ar-table-wrap,.ar-detail-table-wrap,.pc-table-wrap,.pc-records-wrap,.carlex-table-wrap,.hs-cross-table-wrap,.lsr-table-wrap,.lsr-table-wrapper,.table-wrapper,.compact-table-container,.pms-mobile-table-scroll';
        function simpleTableHeaders(table) {
            const head = table.tHead;
            const bodies = Array.from(table.tBodies || []);
            if (!head || head.rows.length !== 1 || bodies.length !== 1) return null;
            const headers = Array.from(head.rows[0].cells || []);
            if (!headers.length || headers.some(cell => cell.tagName !== 'TH' || cell.colSpan > 1 || cell.rowSpan > 1 || !cell.textContent.trim())) return null;
            const rows = Array.from(bodies[0].rows || []);
            if (!rows.length || rows.some(row => row.cells.length !== headers.length || Array.from(row.cells).some(cell => cell.colSpan > 1 || cell.rowSpan > 1 || cell.querySelector('table')))) return null;
            return headers;
        }
        function clearMobileCardLabels(table) {
            table.classList.remove('pms-mobile-table-cards');
            table.querySelectorAll('td[data-mobile-label]').forEach(cell => cell.removeAttribute('data-mobile-label'));
        }
        function prepareTables() {
            if (!mobile.matches) return;
            document.querySelectorAll('table').forEach(function (table) {
                if (table.closest('.sidebar')) return;
                if (table.classList.contains('pms-users-table')) {
                    clearMobileCardLabels(table);
                    return;
                }
                const headers = phone.matches ? simpleTableHeaders(table) : null;
                if (headers) {
                    table.classList.add('pms-mobile-table-cards');
                    Array.from(table.tBodies[0].rows).forEach(row => Array.from(row.cells).forEach((cell, index) => {
                        cell.setAttribute('data-mobile-label', headers[index].textContent.trim());
                    }));
                } else {
                    clearMobileCardLabels(table);
                }
                if (table.dataset.mobileScrollReady) return;
                let scrollport = table.parentElement;
                if (!scrollport.matches(wrappers)) {
                    scrollport = document.createElement('div');
                    scrollport.dataset.mobileGeneratedWrapper = 'true';
                    table.parentNode.insertBefore(scrollport, table); scrollport.appendChild(table);
                }
                scrollport.classList.add('pms-mobile-table-scroll');
                if (!scrollport.hasAttribute('tabindex')) scrollport.tabIndex = 0;
                if (!scrollport.hasAttribute('aria-label')) scrollport.setAttribute('aria-label', 'Table: scroll horizontally to see all columns');
                table.dataset.mobileScrollReady = 'true';
            });
        }
        function updateMode() {
            if (!mobile.matches) {
                document.querySelectorAll('table').forEach(function(table) {
                    if (table.classList.contains('pms-mobile-table-cards')) clearMobileCardLabels(table);
                });
                document.querySelectorAll('[data-mobile-generated-wrapper]').forEach(function (wrapper) {
                    const table = wrapper.querySelector('table');
                    if (table) { wrapper.parentNode.insertBefore(table, wrapper); delete table.dataset.mobileScrollReady; }
                    wrapper.remove();
                });
            }
            if (sidebar && toggle) {
                if (mobile.matches && !wasMobile) {
                    savedCollapsed = sidebar.classList.contains('collapsed');
                    sidebar.classList.remove('collapsed', 'active', 'open'); closeNavigation(false);
                } else if (!mobile.matches && wasMobile) {
                    closeNavigation(false); sidebar.removeAttribute('inert');
                    sidebar.classList.toggle('collapsed', savedCollapsed);
                    toggle.removeAttribute('aria-expanded');
                }
            }
            wasMobile = mobile.matches;
            prepareTables();
        }
        mobile.addEventListener('change', updateMode);
        phone.addEventListener('change', updateMode);
        let queued = false;
        new MutationObserver(function (mutations) {
            if (!mobile.matches || queued || !mutations.some(m => m.addedNodes.length)) return;
            queued = true; window.requestAnimationFrame(function () { queued = false; prepareTables(); });
        }).observe(document.body, { childList: true, subtree: true });
        updateMode();
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
    else initialize();
})();
