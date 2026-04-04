$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

    $('.nav-parent-toggle').on('click', function(e) {
        e.preventDefault();
        $(this).addClass('expanded');
        $(this).next('.nav-children').addClass('show').show();
    });

    $('.nav-parent-toggle').addClass('expanded');
    $('.nav-children').addClass('show').show();

    const currentPath = window.location.pathname;
    const currentUrl = currentPath + window.location.search;
    const $matchedChild = $('.nav-child').filter(function() {
        const href = $(this).attr('href');
        return href && (href === currentUrl || href === currentPath);
    }).first();
    if ($matchedChild.length) {
        $('.nav-child').removeClass('active');
        $matchedChild.addClass('active');
    }

    hamburger.on('click', function() {
        if (window.innerWidth <= 768) {
            sidebar.toggleClass('active');
            sidebarOverlay.toggleClass('active');
        } else {
            sidebar.toggleClass('collapsed');
            mainContent.toggleClass('expanded');
            localStorage.setItem('sidebarCollapsed', sidebar.hasClass('collapsed'));
        }
    });

    sidebarOverlay.on('click', function() {
        sidebar.removeClass('active');
        sidebarOverlay.removeClass('active');
    });

    if (localStorage.getItem('sidebarCollapsed') === 'true' && window.innerWidth > 768) {
        sidebar.addClass('collapsed');
        mainContent.addClass('expanded');
    }

    function updateSyncStatus(label) {
        $('#issueBoardSyncStatus').text(label);
    }

    function formatDisplayDate(rawDate) {
        if (!rawDate) {
            return '-';
        }

        const dateObj = new Date(rawDate + 'T00:00:00');
        if (Number.isNaN(dateObj.getTime())) {
            return rawDate;
        }

        return dateObj.toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric'
        });
    }

    function safeText(value) {
        if (value === null || value === undefined) {
            return '';
        }

        const div = document.createElement('div');
        div.textContent = String(value);
        return div.innerHTML;
    }

    function clampPercent(value) {
        const parsed = Number(value);
        if (!Number.isFinite(parsed)) {
            return 0;
        }
        return Math.min(100, Math.max(0, Math.round(parsed)));
    }

    function statusToPercent(status) {
        if (status === null || status === undefined || status === '') {
            return 0;
        }

        const normalized = String(status).trim().toLowerCase();
        if (normalized.endsWith('%')) {
            return clampPercent(normalized.replace('%', ''));
        }

        if (normalized === 'done' || normalized === 'closed') {
            return 100;
        }

        if (normalized === 'in-progress' || normalized === 'in progress') {
            return 50;
        }

        if (normalized === 'open') {
            return 0;
        }

        return clampPercent(normalized);
    }

    function renderStatusCircle(status) {
        const progress = statusToPercent(status);
        return '' +
            '<div class="status-cell">' +
            '<div class="progress-circle" data-value="' + progress + '" style="--progress: ' + progress + ';" title="Status: ' + progress + '%">' +
            '<span class="progress-circle-label">' + progress + '%</span>' +
            '</div>' +
            '</div>';
    }

    function dueClass(dueDays) {
        if (dueDays === null || dueDays === undefined || dueDays === '') {
            return '';
        }

        const parsed = Number(dueDays);
        if (!Number.isFinite(parsed)) {
            return '';
        }

        return parsed < 0 ? 'due-overdue' : (parsed === 0 ? 'due-today' : 'due-upcoming');
    }

    function normalizeDueDays(row) {
        if (row.dueDays !== null && row.dueDays !== undefined && row.dueDays !== '') {
            return row.dueDays;
        }

        if (!row.targetDate) {
            return '';
        }

        const target = new Date(row.targetDate + 'T00:00:00');
        const today = new Date();
        const todayUtc = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        if (Number.isNaN(target.getTime())) {
            return '';
        }

        const msPerDay = 24 * 60 * 60 * 1000;
        return Math.round((target.getTime() - todayUtc.getTime()) / msPerDay);
    }

    function renderRows(rows) {
        const tbody = $('#issueBoardBody');
        tbody.empty();

        if (!rows || rows.length === 0) {
            tbody.append('<tr><td colspan="11" class="empty-row">No issue board data configured. Open Issue Board Configuration.</td></tr>');
            $('#lastReviewDate').text('-');
            $('#nextReviewDate').text('-');
            return;
        }

        const first = rows[0];
        $('#lastReviewDate').text(formatDisplayDate(first.lastReviewDate));
        $('#nextReviewDate').text(formatDisplayDate(first.nextReviewDate));

        rows.forEach(function(row, index) {
            const dueDays = normalizeDueDays(row);
            const tr = '' +
                '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td>' + safeText(row.problem) + '</td>' +
                '<td>' + safeText(row.ownerName) + '</td>' +
                '<td>' + safeText(row.issueDate) + '</td>' +
                '<td>' + safeText(row.rootCause) + '</td>' +
                '<td>' + safeText(row.actions) + '</td>' +
                '<td>' + safeText(row.responsible) + '</td>' +
                '<td>' + safeText(formatDisplayDate(row.targetDate)) + '</td>' +
                '<td class="' + dueClass(dueDays) + '">' + safeText(dueDays) + '</td>' +
                '<td>' + renderStatusCircle(row.status) + '</td>' +
                '<td>' + safeText(row.remarks) + '</td>' +
                '</tr>';
            tbody.append(tr);
        });
    }

    function loadIssueBoardData() {
        updateSyncStatus('Syncing...');

        $.ajax({
            url: '/api/issue-board/latest',
            type: 'GET',
            success: function(data) {
                renderRows(Array.isArray(data) ? data : []);
                updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                renderRows([]);
                updateSyncStatus('Sync failed');
            }
        });
    }

    window.addEventListener('storage', function(e) {
        if (e.key === 'issue-board-update') {
            loadIssueBoardData();
        }
    });

    loadIssueBoardData();
    setInterval(loadIssueBoardData, 30000);
});
