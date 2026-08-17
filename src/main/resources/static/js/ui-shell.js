(function () {
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

    const originalAlert = window.alert;
    window.alert = function (message) {
        showToast(message || 'Notification');
        if (typeof originalAlert === 'function' && window.__pmsAllowNativeAlert === true) {
            originalAlert(message);
        }
    };

    function isConfigPage() {
        const path = (window.location.pathname || '').toLowerCase().replace(/\/+$/, '');
        return path === '/settings' || path === '/pms-configuration' || path === '/email-configuration';
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
        const canManageEmailConfiguration = Boolean(document.querySelector('.sidebar-nav a[href="/email-configuration"]'));

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

    document.addEventListener('click', function (event) {
        document.querySelectorAll('.pms-profile.open').forEach(function (profile) {
            if (!profile.contains(event.target)) {
                profile.classList.remove('open');
            }
        });
    });

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.top-header').forEach(enhanceHeader);
        normalizeSidebarLabels();
        setupSidebarDropdowns();
        keepActiveNavigationVisible();
        loadPmsDeckNavDates();
        window.setTimeout(keepActiveNavigationVisible, 150);
        fixFooterBranding();
        standardizeTableAlignment(document);
        bindTableAlignmentObserver();
    });
})();
