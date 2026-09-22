(function () {
    window.PmsFeedback = {
        show: function (message, type) {
            if (!message) return;
            let notice = document.getElementById('pmsActionFeedback');
            if (!notice) {
                notice = document.createElement('div');
                notice.id = 'pmsActionFeedback';
                const text = document.createElement('span');
                text.className = 'pms-action-feedback-text';
                const close = document.createElement('button');
                close.type = 'button'; close.textContent = '\u00d7';
                close.setAttribute('aria-label', 'Dismiss message');
                close.addEventListener('click', function () { notice.hidden = true; });
                notice.appendChild(text); notice.appendChild(close);
                document.body.appendChild(notice);
            }
            const state = type === 'error' ? 'error' : type === 'warning' ? 'warning' : 'success';
            notice.className = 'pms-action-feedback pms-action-feedback-' + state;
            notice.setAttribute('role', state === 'error' ? 'alert' : 'status');
            notice.setAttribute('aria-live', state === 'error' ? 'assertive' : 'polite');
            notice.querySelector('.pms-action-feedback-text').textContent = message;
            notice.hidden = false;
        }
    };
    function getMonthValue() {
        const now = new Date();
        return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
    }

    function formatTodayDate(dateValue) {
        let now = new Date();
        if (dateValue) {
            const parsed = new Date(String(dateValue).substring(0, 10) + 'T00:00:00');
            if (!Number.isNaN(parsed.getTime())) {
                now = parsed;
            }
        }
        const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
        const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 
                           'July', 'August', 'September', 'October', 'November', 'December'];
        const day = dayNames[now.getDay()];
        const month = monthNames[now.getMonth()];
        const date = now.getDate();
        const year = now.getFullYear();
        return day + ', ' + month + ' ' + date + ', ' + year;
    }

    window.setPmsHeaderDate = function (dateValue) {
        window.__pmsHeaderDateOverride = dateValue || '';
        document.querySelectorAll('.pms-today-date').forEach(function (dateDisplay) {
            dateDisplay.textContent = formatTodayDate(window.__pmsHeaderDateOverride);
        });
    };

    function createToastIfNeeded() {
        let toast = document.getElementById('pmsGlobalToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'pmsGlobalToast';
            toast.className = 'pms-toast';
            document.body.appendChild(toast);
        }
        return toast;
    }

    function showToast(message) {
        if (!message) {
            return;
        }
        const toast = createToastIfNeeded();
        toast.textContent = String(message);
        toast.classList.add('show');
        window.clearTimeout(window.__pmsToastTimer);
        window.__pmsToastTimer = window.setTimeout(function () {
            toast.classList.remove('show');
        }, 2600);
    }

    function ensureGlobalTableHeaderStyle() {
        if (document.getElementById('pmsGlobalTableHeaderStyle')) {
            return;
        }
        const style = document.createElement('style');
        style.id = 'pmsGlobalTableHeaderStyle';
        style.textContent = '' +
            'body:not(.login-page) table thead th{' +
            'background:linear-gradient(135deg,#0b6b2e 0%,#087333 100%)!important;' +
            'color:#fff!important;border-color:#075f2c!important;font-weight:800;' +
            'letter-spacing:0;text-transform:uppercase;' +
            '}' +
            'body:not(.login-page) table thead th a,' +
            'body:not(.login-page) table thead th button,' +
            'body:not(.login-page) table thead th span,' +
            'body:not(.login-page) table thead th i{color:inherit!important;}' +
            'body:not(.login-page) table thead th input,' +
            'body:not(.login-page) table thead th select,' +
            'body:not(.login-page) table thead th textarea{color:#111827!important;}' +
            'body:not(.login-page) table thead th.pms-global-sortable,' +
            'body:not(.login-page) table thead th.sortable{cursor:pointer;position:relative;padding-right:26px!important;}' +
            'body:not(.login-page) table thead th.pms-global-sortable:hover,' +
            'body:not(.login-page) table thead th.sortable:hover{background:linear-gradient(135deg,#087333 0%,#065f2a 100%)!important;}' +
            'body:not(.login-page) table thead th.pms-global-sortable:after,' +
            'body:not(.login-page) table thead th.sortable:after{font-family:"Font Awesome 6 Free";font-weight:900;content:"\\f0dc";position:absolute;right:9px;top:50%;transform:translateY(-50%);color:#d8f8e2!important;opacity:.78;font-size:.8em;}' +
            'body:not(.login-page) table thead th.pms-sort-asc:after,' +
            'body:not(.login-page) table thead th.sort-asc:after{content:"\\f0de";color:#fff!important;opacity:1;}' +
            'body:not(.login-page) table thead th.pms-sort-desc:after,' +
            'body:not(.login-page) table thead th.sort-desc:after{content:"\\f0dd";color:#fff!important;opacity:1;}';
        document.head.appendChild(style);
    }

    function isPlainSortableHeader(header) {
        if (!header || header.tagName !== 'TH') {
            return false;
        }
        if (header.classList.contains('sortable') || header.classList.contains('no-sort') || header.dataset.noSort === 'true') {
            return false;
        }
        if (header.colSpan > 1 || header.rowSpan > 1 || header.querySelector('input, select, textarea')) {
            return false;
        }
        const text = normalizeCellText(header).toLowerCase();
        return text && !/^(action|actions|history|assignment history|pictures?|image|open)$/i.test(text);
    }

    function enhanceSortableTables(root) {
        const scope = root || document;
        scope.querySelectorAll('table').forEach(function (table) {
            const headerRow = table.tHead && table.tHead.rows.length ? table.tHead.rows[table.tHead.rows.length - 1] : null;
            const body = table.tBodies && table.tBodies.length ? table.tBodies[0] : null;
            if (!headerRow || !body) {
                return;
            }
            Array.from(headerRow.cells || []).forEach(function (header) {
                if (header.classList.contains('sortable')) {
                    header.setAttribute('aria-sort', header.classList.contains('sort-asc') ? 'ascending' : (header.classList.contains('sort-desc') ? 'descending' : 'none'));
                    return;
                }
                if (!isPlainSortableHeader(header)) {
                    return;
                }
                header.classList.add('pms-global-sortable');
                header.setAttribute('role', 'button');
                header.setAttribute('tabindex', '0');
                header.setAttribute('aria-sort', header.classList.contains('pms-sort-asc') ? 'ascending' : (header.classList.contains('pms-sort-desc') ? 'descending' : 'none'));
                if (!header.getAttribute('title')) {
                    header.setAttribute('title', 'Sort column');
                }
            });
        });
    }

    function cellSortText(cell) {
        if (!cell) {
            return '';
        }
        const field = cell.querySelector('input:not([type="hidden"]), select, textarea');
        if (field) {
            if (field.tagName === 'SELECT') {
                const option = field.options[field.selectedIndex];
                return ((option && option.textContent) || field.value || '').trim();
            }
            return (field.value || '').trim();
        }
        return normalizeCellText(cell);
    }

    function parseSortableDate(value) {
        const text = String(value || '').trim();
        if (!text) {
            return null;
        }
        let match = text.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
        if (match) {
            return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])).getTime();
        }
        match = text.match(/^(\d{1,2})[\/-](\d{1,2})[\/-](\d{4})$/);
        if (match) {
            return new Date(Number(match[3]), Number(match[2]) - 1, Number(match[1])).getTime();
        }
        const parsed = Date.parse(text.replace(/\bSept\b/i, 'Sep'));
        return Number.isNaN(parsed) ? null : parsed;
    }

    function sortableValue(text) {
        const value = String(text || '').replace(/\s+/g, ' ').trim();
        const date = parseSortableDate(value);
        if (date !== null) {
            return { type: 'date', value: date };
        }
        const numeric = Number(value.replace(/[%#,]/g, ''));
        if (value && Number.isFinite(numeric) && /^-?[\d,]+(\.\d+)?%?$/.test(value)) {
            return { type: 'number', value: numeric };
        }
        return { type: 'text', value: value.toLowerCase() };
    }

    function compareSortableValues(left, right) {
        if (left.type === right.type) {
            if (left.value < right.value) return -1;
            if (left.value > right.value) return 1;
            return 0;
        }
        return String(left.value).localeCompare(String(right.value), undefined, { numeric: true, sensitivity: 'base' });
    }

    function sortPlainTable(header) {
        const table = header.closest('table');
        const body = table && table.tBodies && table.tBodies.length ? table.tBodies[0] : null;
        const headerRow = header.parentElement;
        if (!table || !body || !headerRow) {
            return;
        }

        const columnIndex = Array.from(headerRow.cells).indexOf(header);
        if (columnIndex < 0) {
            return;
        }

        const ascending = !header.classList.contains('pms-sort-asc');
        Array.from(headerRow.cells).forEach(function (cell) {
            cell.classList.remove('pms-sort-asc', 'pms-sort-desc');
            if (cell.classList.contains('pms-global-sortable')) {
                cell.setAttribute('aria-sort', 'none');
            }
        });
        header.classList.add(ascending ? 'pms-sort-asc' : 'pms-sort-desc');
        header.setAttribute('aria-sort', ascending ? 'ascending' : 'descending');

        const movableRows = [];
        const pinnedRows = [];
        Array.from(body.rows || []).forEach(function (row, originalIndex) {
            if (row.classList.contains('placeholder-row') || row.classList.contains('loading-row') || row.classList.contains('issue-config-search-empty') || row.cells.length <= columnIndex) {
                pinnedRows.push(row);
                return;
            }
            movableRows.push({
                row: row,
                value: sortableValue(cellSortText(row.cells[columnIndex])),
                originalIndex: originalIndex
            });
        });

        movableRows.sort(function (a, b) {
            const compared = compareSortableValues(a.value, b.value);
            if (compared === 0) {
                return a.originalIndex - b.originalIndex;
            }
            return ascending ? compared : -compared;
        });

        movableRows.forEach(function (item) {
            body.appendChild(item.row);
        });
        pinnedRows.forEach(function (row) {
            body.appendChild(row);
        });
        standardizeTableAlignment(table);
    }

    function bindGlobalTableSorting() {
        if (window.__pmsGlobalTableSortingBound) {
            return;
        }
        document.addEventListener('click', function (event) {
            const header = event.target.closest('th.pms-global-sortable');
            if (!header) {
                return;
            }
            sortPlainTable(header);
        });
        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter' && event.key !== ' ') {
                return;
            }
            const header = event.target.closest('th.pms-global-sortable');
            if (!header) {
                return;
            }
            event.preventDefault();
            sortPlainTable(header);
        });
        window.__pmsGlobalTableSortingBound = true;
    }

    function createConfirmIfNeeded() {
        let modal = document.getElementById('pmsConfirmModal');
        if (modal) {
            return modal;
        }

        modal = document.createElement('div');
        modal.id = 'pmsConfirmModal';
        modal.className = 'pms-confirm-modal';
        modal.setAttribute('aria-hidden', 'true');
        modal.innerHTML = '' +
            '<div class="pms-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="pmsConfirmTitle" aria-describedby="pmsConfirmMessage">' +
            '<div class="pms-confirm-icon" aria-hidden="true"><i class="fas fa-triangle-exclamation"></i></div>' +
            '<div class="pms-confirm-copy">' +
            '<h2 id="pmsConfirmTitle">Delete record?</h2>' +
            '<p id="pmsConfirmMessage">This action cannot be undone.</p>' +
            '</div>' +
            '<div class="pms-confirm-actions">' +
            '<button type="button" class="pms-confirm-cancel">Cancel</button>' +
            '<button type="button" class="pms-confirm-delete"><i class="fas fa-trash-alt"></i><span>Delete</span></button>' +
            '</div>' +
            '</div>';
        document.body.appendChild(modal);
        return modal;
    }

    window.PmsConfirm = {
        open: function (options) {
            const settings = options || {};
            const modal = createConfirmIfNeeded();
            const title = modal.querySelector('#pmsConfirmTitle');
            const message = modal.querySelector('#pmsConfirmMessage');
            const cancelButton = modal.querySelector('.pms-confirm-cancel');
            const deleteButton = modal.querySelector('.pms-confirm-delete');
            const deleteText = deleteButton.querySelector('span');
            const previousFocus = document.activeElement;

            title.textContent = settings.title || 'Delete record?';
            message.textContent = settings.message || 'This action cannot be undone.';
            deleteText.textContent = settings.confirmText || 'Delete';

            modal.classList.add('is-open');
            modal.setAttribute('aria-hidden', 'false');
            document.body.classList.add('pms-confirm-open');

            return new Promise(function (resolve) {
                let settled = false;

                function cleanup(result) {
                    if (settled) {
                        return;
                    }
                    settled = true;
                    modal.classList.remove('is-open');
                    modal.setAttribute('aria-hidden', 'true');
                    document.body.classList.remove('pms-confirm-open');
                    cancelButton.removeEventListener('click', onCancel);
                    deleteButton.removeEventListener('click', onConfirm);
                    modal.removeEventListener('click', onBackdrop);
                    document.removeEventListener('keydown', onKeydown);
                    if (previousFocus && typeof previousFocus.focus === 'function') {
                        previousFocus.focus();
                    }
                    resolve(result);
                }

                function onCancel() {
                    cleanup(false);
                }

                function onConfirm() {
                    cleanup(true);
                }

                function onBackdrop(event) {
                    if (event.target === modal) {
                        cleanup(false);
                    }
                }

                function onKeydown(event) {
                    if (event.key === 'Escape') {
                        cleanup(false);
                    }
                    if (event.key === 'Enter' && document.activeElement === deleteButton) {
                        cleanup(true);
                    }
                }

                cancelButton.addEventListener('click', onCancel);
                deleteButton.addEventListener('click', onConfirm);
                modal.addEventListener('click', onBackdrop);
                document.addEventListener('keydown', onKeydown);

                window.setTimeout(function () {
                    cancelButton.focus();
                }, 0);
            });
        }
    };

    const originalAlert = window.alert;
    window.alert = function (message) {
        showToast(message || 'Notification');
        if (typeof originalAlert === 'function' && window.__pmsAllowNativeAlert === true) {
            originalAlert(message);
        }
    };

    function isConfigPage() {
        const path = (window.location.pathname || '').toLowerCase().replace(/\/+$/, '');
        return path === '/settings'
            || path === '/pms-configuration'
            || path === '/smtp-configuration'
            || path === '/email-configuration'
            || path === '/abnormality-reporting-config'
            || path === '/gemba-walk-config'
            || path === '/gemba-kaizen-config';
    }

    function normalizeCellText(cell) {
        return (cell && cell.textContent ? cell.textContent : '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function hasTextInputLikeContent(cell) {
        return !!(cell && cell.querySelector('textarea, select, input:not([type="checkbox"]):not([type="radio"]), button'));
    }

    function hasVisualOnlyContent(cell) {
        if (!cell) {
            return false;
        }
        const text = normalizeCellText(cell);
        return !text && !!cell.querySelector('i, svg, img, input[type="checkbox"], input[type="radio"]');
    }

    function isNumericLikeText(text) {
        if (!text) {
            return false;
        }
        return /^((top\s*)?\d+([.,:/-]\d+)*(\.\d+)?%?|#\d+|w\d+|sl\s*no|s\.no|\d{1,2}:\d{2}(\s?[ap]m)?)$/i.test(text);
    }

    function isCenterCandidateCell(cell) {
        if (!cell || cell.classList.contains('text-left') || hasTextInputLikeContent(cell)) {
            return false;
        }
        if (cell.colSpan > 1) {
            return true;
        }

        const text = normalizeCellText(cell);
        if (hasVisualOnlyContent(cell)) {
            return true;
        }
        if (cell.querySelector('i, svg, img') && text.length <= 4) {
            return true;
        }
        return isNumericLikeText(text);
    }

    function applyCellAlignment(cell, shouldCenter) {
        if (!cell) {
            return;
        }
        cell.classList.remove('pms-cell-left', 'pms-cell-center');
        cell.classList.add(shouldCenter ? 'pms-cell-center' : 'pms-cell-left');
    }

    function standardizeTableAlignment(root) {
        const scope = root || document;
        scope.querySelectorAll('table').forEach(function (table) {
            const bodyRows = Array.from(table.tBodies || []).flatMap(function (tbody) {
                return Array.from(tbody.rows || []);
            });
            const centerColumns = [];

            bodyRows.forEach(function (row) {
                Array.from(row.cells || []).forEach(function (cell, cellIndex) {
                    if (cell.colSpan !== 1 || hasTextInputLikeContent(cell)) {
                        return;
                    }
                    if (!centerColumns[cellIndex]) {
                        centerColumns[cellIndex] = { samples: 0, centered: 0 };
                    }
                    centerColumns[cellIndex].samples += 1;
                    if (isCenterCandidateCell(cell)) {
                        centerColumns[cellIndex].centered += 1;
                    }
                });
            });

            table.querySelectorAll('thead tr, tbody tr, tfoot tr').forEach(function (row) {
                Array.from(row.cells || []).forEach(function (cell, cellIndex) {
                    const score = centerColumns[cellIndex];
                    const centerByColumn = !!(score && score.samples > 0 && (score.centered / score.samples) >= 0.6);
                    const shouldCenter = centerByColumn || isCenterCandidateCell(cell);
                    applyCellAlignment(cell, shouldCenter);
                });
            });
        });
    }

    function bindTableAlignmentObserver() {
        if (window.__pmsTableAlignmentObserverBound) {
            return;
        }

        let pendingTimer = null;
        const scheduleRefresh = function () {
            window.clearTimeout(pendingTimer);
            pendingTimer = window.setTimeout(function () {
                standardizeTableAlignment(document);
            }, 120);
        };

        const observer = new MutationObserver(function (mutations) {
            const hasTableMutation = mutations.some(function (mutation) {
                if (mutation.target && mutation.target.nodeType === 1) {
                    const element = mutation.target;
                    if (element.tagName === 'TABLE' || element.tagName === 'TR' || element.tagName === 'TD' || element.tagName === 'TH' || element.closest('table')) {
                        return true;
                    }
                }
                return Array.from(mutation.addedNodes || []).some(function (node) {
                    return node.nodeType === 1 && (node.tagName === 'TABLE' || (node.querySelector && node.querySelector('table')));
                });
            });

            if (hasTableMutation) {
                scheduleRefresh();
                window.setTimeout(function () {
                    enhanceSortableTables(document);
                }, 140);
            }
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true,
            characterData: true
        });

        window.__pmsTableAlignmentObserverBound = true;
    }

    function enhanceHeader(header) {
        const headerLeft = header.querySelector('.header-left');
        const headerCenter = header.querySelector('.header-center');
        const headerRight = header.querySelector('.header-right') || (function () {
            const div = document.createElement('div');
            div.className = 'header-right';
            return div;
        })();
        const existingRoleBadge = header.querySelector('.header-role-badge');
        const existingRole = existingRoleBadge ? existingRoleBadge.textContent.trim().toUpperCase().replace(/\s+/g, '_') : '';
        const canAccessPmsDataEntry = Boolean(document.querySelector('.sidebar-nav .nav-parent')) || Boolean(document.querySelector('.sidebar-nav a[href^="/settings?config="]'));
        const canManageUsers = Boolean(document.querySelector('.sidebar-nav a[href="/pms-configuration"]'));
        const canManageEmailConfiguration = Boolean(document.querySelector('.sidebar-nav a[href="/smtp-configuration"], .sidebar-nav a[href="/email-configuration"]'));

        if (!header.contains(headerRight)) {
            header.appendChild(headerRight);
        }

        const title = header.querySelector('h1');
        if (title) {
            title.classList.add('page-title');
        }

        if (headerLeft) {
            headerLeft.querySelectorAll('.header-shell-logo, .header-logo').forEach(function (logoEl) {
                logoEl.remove();
            });
        }

        header.querySelectorAll('.header-icon, .sync-status, .notification-badge, .header-role-badge, .profile-email').forEach(function (el) {
            el.remove();
        });

        if (!headerRight.querySelector('.pms-profile')) {
            const avatarEl = header.querySelector('.user-avatar');
            const existingName = (header.querySelector('.profile-name') && header.querySelector('.profile-name').textContent.trim())
                || (avatarEl && avatarEl.getAttribute('data-username') && avatarEl.getAttribute('data-username').trim())
                || (avatarEl && avatarEl.textContent.trim())
                || 'User';
            const logoutHref = (header.querySelector('a[href="/logout"]') && header.querySelector('a[href="/logout"]').getAttribute('href')) || '/logout';

            header.querySelectorAll('.user-info-header, .logout-btn, .user-avatar').forEach(function (el) {
                el.remove();
            });

            const preservedHeaderActions = Array.from(
                headerRight.querySelectorAll('.pdf-export-btn, [data-preserve-header-right="true"]')
            );

            const profile = document.createElement('div');
            profile.className = 'pms-profile';
            const menuLinks = [
                '<a href="' + logoutHref + '">Logout</a>'
            ];
            profile.innerHTML = '' +
                '<button type="button" class="pms-profile-btn">' +
                '<i class="fas fa-user-circle"></i>' +
                '<span class="pms-profile-name">' + existingName + '</span>' +
                '<i class="fas fa-chevron-down"></i>' +
                '</button>' +
                '<div class="pms-profile-menu">' +
                menuLinks.join('') +
                '</div>';
            headerRight.innerHTML = '';

            preservedHeaderActions.forEach(function (actionEl) {
                headerRight.appendChild(actionEl);
            });
            
            // Add today's date display
            const dateDisplay = document.createElement('div');
            dateDisplay.className = 'pms-today-date';
            dateDisplay.textContent = formatTodayDate(window.__pmsHeaderDateOverride);
            headerRight.appendChild(dateDisplay);
            
            headerRight.appendChild(profile);

            const btn = profile.querySelector('.pms-profile-btn');
            btn.addEventListener('click', function (event) {
                event.stopPropagation();
                profile.classList.toggle('open');
            });
        }
    }

    function normalizeSidebarLabels() {
        document.querySelectorAll('.nav-parent-toggle').forEach(function (toggle) {
            const span = toggle.querySelector('span');
            const currentLabel = span ? span.textContent.trim() : '';
            const currentTooltip = (toggle.getAttribute('data-tooltip') || '').trim();
            if (currentLabel !== 'PMS Data Entry' && currentTooltip !== 'PMS Data Entry') {
                return;
            }
            if (span) {
                span.textContent = 'PMS4 Deck V0 Data';
            }
            toggle.setAttribute('data-tooltip', 'PMS4 Deck V0 Data');
        });

        document.querySelectorAll('.config-group-title').forEach(function (el) {
            if (el.textContent && el.textContent.toLowerCase().indexOf('configuration') !== -1) {
                el.textContent = 'PMS4 Deck V0 Data';
            }
        });
    }

    function setupSidebarDropdowns() {
        const toggles = Array.from(document.querySelectorAll('.nav-parent-toggle'));
        if (!toggles.length) {
            return;
        }

        function childrenFor(toggle) {
            const next = toggle.nextElementSibling;
            return next && next.classList.contains('nav-children') ? next : null;
        }

        function closeBranch(toggle) {
            const children = childrenFor(toggle);
            toggle.classList.remove('expanded');
            toggle.setAttribute('aria-expanded', 'false');
            if (children) {
                children.classList.remove('show');
            }
        }

        function openBranch(toggle) {
            const children = childrenFor(toggle);
            toggle.classList.add('expanded');
            toggle.setAttribute('aria-expanded', 'true');
            if (children) {
                children.classList.add('show');
            }
        }

        toggles.forEach(function (toggle) {
            closeBranch(toggle);
            toggle.setAttribute('role', 'button');
            toggle.setAttribute('aria-expanded', 'false');

            if (toggle.dataset.pmsNavBound === 'true') {
                return;
            }
            toggle.dataset.pmsNavBound = 'true';

            toggle.addEventListener('click', function (event) {
                event.preventDefault();
                event.stopImmediatePropagation();

                if (toggle.classList.contains('expanded')) {
                    closeBranch(toggle);
                } else {
                    openBranch(toggle);
                }
            }, true);
        });

        const currentPath = window.location.pathname;
        const currentUrl = currentPath + window.location.search;
        let activeChild = null;

        document.querySelectorAll('.sidebar-nav .nav-item').forEach(function (item) {
            item.classList.remove('active');
        });

        document.querySelectorAll('.sidebar-nav .nav-child').forEach(function (link) {
            const href = link.getAttribute('href');
            if (!activeChild && href && (href === currentUrl || href === currentPath)) {
                activeChild = link;
            }
        });

        if (activeChild) {
            activeChild.classList.add('active');
            const parentChildren = activeChild.closest('.nav-children');
            const parentToggle = parentChildren ? parentChildren.previousElementSibling : null;
            if (parentToggle && parentToggle.classList.contains('nav-parent-toggle')) {
                openBranch(parentToggle);
            }
            return;
        }

        document.querySelectorAll('.sidebar-nav .nav-item:not(.nav-child):not(.nav-parent-toggle)').forEach(function (link) {
            const href = link.getAttribute('href');
            if (href && (href === currentUrl || href === currentPath)) {
                link.classList.add('active');
            }
        });
    }

    function keepActiveNavigationVisible() {
        const sidebar = document.getElementById('sidebar');
        const activeItem = sidebar ? sidebar.querySelector('.sidebar-nav .nav-item.active') : null;
        if (!sidebar || !activeItem || sidebar.classList.contains('collapsed')) {
            return;
        }

        window.requestAnimationFrame(function () {
            const sidebarRect = sidebar.getBoundingClientRect();
            const itemRect = activeItem.getBoundingClientRect();
            const topPadding = 18;
            const bottomPadding = 24;
            const isAboveView = itemRect.top < sidebarRect.top + topPadding;
            const isBelowView = itemRect.bottom > sidebarRect.bottom - bottomPadding;

            if (!isAboveView && !isBelowView) {
                return;
            }

            const targetScrollTop = sidebar.scrollTop
                + (itemRect.top - sidebarRect.top)
                - ((sidebar.clientHeight - activeItem.offsetHeight) / 2);

            sidebar.scrollTo({
                top: Math.max(0, targetScrollTop),
                behavior: 'auto'
            });
        });
    }

    function formatShortDate(dateValue) {
        if (!dateValue) {
            return '';
        }

        const parsed = new Date(String(dateValue) + 'T00:00:00');
        if (Number.isNaN(parsed.getTime())) {
            return '';
        }

        return parsed.toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric'
        });
    }

    function latestDateFromRows(rows) {
        if (!Array.isArray(rows)) {
            rows = rows ? [rows] : [];
        }

        return rows.reduce(function (latest, row) {
            const dateValue = row && (row.date || row.targetDate || row.updatedAt || row.createdAt);
            if (!dateValue) {
                return latest;
            }

            const parsed = new Date(String(dateValue).substring(0, 10) + 'T00:00:00');
            if (Number.isNaN(parsed.getTime())) {
                return latest;
            }

            if (!latest || parsed.getTime() > latest.getTime()) {
                return parsed;
            }
            return latest;
        }, null);
    }

    function applyPmsDeckNavDate(href, dateValue) {
        document.querySelectorAll('.sidebar-nav .nav-child[href="' + href + '"]').forEach(function (link) {
            const span = link.querySelector('span');
            if (!span) {
                return;
            }

            if (!span.dataset.baseLabel) {
                span.dataset.baseLabel = span.textContent.trim();
            }

            span.classList.add('pms-nav-label-stack');
            span.innerHTML = '' +
                '<span class="pms-nav-main-label">' + span.dataset.baseLabel + '</span>' +
                '<span class="pms-nav-edited-date">' + (dateValue ? 'Last edited: ' + dateValue : 'Last edited: -') + '</span>';
        });
    }

    function loadPmsDeckNavDates() {
        const pmsDeckItems = [
            { href: '/pms/top-priorities', url: '/api/priorities/type/TOP_3' },
            { href: '/pms/weekly-priorities', url: '/api/priorities/type/WEEKLY' },
            { href: '/pms/daily-performance', url: '/api/daily-performance/current-month' },
            { href: '/pms/people-daily', url: '/api/daily-data/type/PEOPLE' },
            { href: '/pms/quality-daily', url: '/api/daily-data/type/QUALITY' },
            { href: '/pms/service-daily', url: '/api/daily-data/type/SERVICE' },
            { href: '/pms/cost-daily', url: '/api/daily-data/type/COST' }
        ];

        pmsDeckItems.forEach(function (item) {
            if (!document.querySelector('.sidebar-nav .nav-child[href="' + item.href + '"]')) {
                return;
            }

            fetch(item.url, { headers: { 'Accept': 'application/json' } })
                .then(function (response) {
                    if (!response.ok || response.status === 204) {
                        return null;
                    }
                    return response.json();
                })
                .then(function (data) {
                    const latest = latestDateFromRows(data);
                    applyPmsDeckNavDate(item.href, latest ? formatShortDate(latest.toISOString().substring(0, 10)) : '');
                })
                .catch(function () {
                    applyPmsDeckNavDate(item.href, '');
                });
        });
    }

    function fixFooterBranding() {
        document.querySelectorAll('.footer').forEach(function (footer) {
            if (!footer.querySelector('img')) {
                const img = document.createElement('img');
                img.src = '/images/solvex-logo.png';
                img.alt = 'SolveX';
                footer.appendChild(img);
            }
        });
    }

    function addProcessConfirmationConfigNavigation() {
        const processLink = document.querySelector('.sidebar-nav a[href="/process-confirmation"]');
        const sidebarNav = document.querySelector('.sidebar-nav');
        if (!processLink || !sidebarNav || sidebarNav.querySelector('a[href="/process-confirmation-config"]')) {
            return;
        }

        const configLink = document.createElement('a');
        configLink.href = '/process-confirmation-config';
        configLink.className = 'nav-item';
        configLink.dataset.tooltip = 'CarlEx P C reporting';
        configLink.innerHTML = '<i class="fas fa-sliders"></i><span>CarlEx P C reporting</span>';

        const insertBefore = sidebarNav.querySelector('a[href="/gemba-kaizen-config"]');
        if (insertBefore) {
            insertBefore.parentNode.insertBefore(configLink, insertBefore);
        } else {
            processLink.insertAdjacentElement('afterend', configLink);
        }
    }

    document.addEventListener('click', function (event) {
        document.querySelectorAll('.pms-profile.open').forEach(function (profile) {
            if (!profile.contains(event.target)) {
                profile.classList.remove('open');
            }
        });
    });

    document.addEventListener('DOMContentLoaded', function () {
        ensureGlobalTableHeaderStyle();
        document.querySelectorAll('.top-header').forEach(enhanceHeader);
        normalizeSidebarLabels();
        addProcessConfirmationConfigNavigation();
        setupSidebarDropdowns();
        keepActiveNavigationVisible();
        loadPmsDeckNavDates();
        window.setTimeout(keepActiveNavigationVisible, 150);
        fixFooterBranding();
        standardizeTableAlignment(document);
        enhanceSortableTables(document);
        bindGlobalTableSorting();
        bindTableAlignmentObserver();
    });
})();
