(function () {
    function getMonthValue() {
        const now = new Date();
        return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
    }

    function formatTodayDate() {
        const now = new Date();
        const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
        const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 
                           'July', 'August', 'September', 'October', 'November', 'December'];
        const day = dayNames[now.getDay()];
        const month = monthNames[now.getMonth()];
        const date = now.getDate();
        const year = now.getFullYear();
        return day + ', ' + month + ' ' + date + ', ' + year;
    }

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
            const existingName = (header.querySelector('.profile-name') && header.querySelector('.profile-name').textContent.trim())
                || (header.querySelector('.user-avatar') && header.querySelector('.user-avatar').textContent.trim())
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
                '<span>' + existingName + '</span>' +
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
            dateDisplay.textContent = formatTodayDate();
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
        fixFooterBranding();
        standardizeTableAlignment(document);
        bindTableAlignmentObserver();
    });
})();
