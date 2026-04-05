(function () {
    function getMonthValue() {
        const now = new Date();
        return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
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
        return path === '/settings' || path === '/pms-configuration';
    }

    function enhanceHeader(header) {
        const headerLeft = header.querySelector('.header-left');
        const allowMonthPicker = !isConfigPage();
        const headerCenter = header.querySelector('.header-center');
        const headerRight = header.querySelector('.header-right') || (function () {
            const div = document.createElement('div');
            div.className = 'header-right';
            return div;
        })();

        if (allowMonthPicker) {
            const center = headerCenter || (function () {
                const div = document.createElement('div');
                div.className = 'header-center';
                return div;
            })();
            if (!header.contains(center)) {
                header.insertBefore(center, headerRight);
            }
        } else if (headerCenter && header.contains(headerCenter)) {
            headerCenter.remove();
        }
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

        if (allowMonthPicker) {
            const center = header.querySelector('.header-center');
            if (center && !center.querySelector('.pms-month-picker')) {
                const monthInput = document.createElement('input');
                monthInput.type = 'month';
                monthInput.className = 'pms-month-picker';
                monthInput.value = getMonthValue();
                center.innerHTML = '';
                center.appendChild(monthInput);
            }
        }

        if (!headerRight.querySelector('.pms-profile')) {
            const existingName = (header.querySelector('.profile-name') && header.querySelector('.profile-name').textContent.trim())
                || (header.querySelector('.user-avatar') && header.querySelector('.user-avatar').textContent.trim())
                || 'User';
            const logoutHref = (header.querySelector('a[href="/logout"]') && header.querySelector('a[href="/logout"]').getAttribute('href')) || '/logout';

            header.querySelectorAll('.user-info-header, .logout-btn, .user-avatar').forEach(function (el) {
                el.remove();
            });

            const profile = document.createElement('div');
            profile.className = 'pms-profile';
            profile.innerHTML = '' +
                '<button type="button" class="pms-profile-btn">' +
                '<i class="fas fa-user-circle"></i>' +
                '<span>' + existingName + '</span>' +
                '<i class="fas fa-chevron-down"></i>' +
                '</button>' +
                '<div class="pms-profile-menu">' +
                '<a href="/pms-configuration">Profile</a>' +
                '<a href="' + logoutHref + '">Logout</a>' +
                '</div>';
            headerRight.innerHTML = '';
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
    });
})();
