$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

    let availablePeriods = [];

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

    function updateSyncStatus(text) {
        $('#trainingSyncStatus').text(text);
    }

    function safeText(value) {
        const div = document.createElement('div');
        div.textContent = value === null || value === undefined ? '' : String(value);
        return div.innerHTML;
    }

    function periodToMonthYear(label) {
        const parts = String(label || '').split("'");
        if (parts.length !== 2) return null;
        const monthMap = { Jan: 1, Feb: 2, Mar: 3, Apr: 4, May: 5, Jun: 6, Jul: 7, Aug: 8, Sep: 9, Oct: 10, Nov: 11, Dec: 12 };
        const month = monthMap[parts[0]];
        const yy = Number(parts[1]);
        if (!month || Number.isNaN(yy)) return null;
        return { month: month, year: 2000 + yy };
    }

    function monthLabel(month, year) {
        const dt = new Date(year, month - 1, 1);
        return dt.toLocaleString('en-GB', { month: 'short', year: 'numeric' });
    }

    function formatDate(d) {
        if (!d) return '-';
        const dt = new Date(d + 'T00:00:00');
        if (Number.isNaN(dt.getTime())) return d;
        return dt.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
    }

    function statusClass(status) {
        return String(status || '').toLowerCase().includes('good') ? 'ts-status-good' : 'ts-status-bad';
    }

    function statusMarkup(status) {
        const text = status || '';
        const cls = statusClass(text);
        return '<span class="ts-status-pill ' + cls + '">' +
            '<span class="ts-status-dot"></span>' +
            safeText(text) +
            '</span>';
    }

    function renderRows(rows, periodLabel) {
        const tbody = $('#trainingScheduleBody');
        tbody.empty();

        const parsed = periodToMonthYear(periodLabel || '');
        $('#tsMonth').text(parsed ? monthLabel(parsed.month, parsed.year) : '-');

        if (!rows || rows.length === 0) {
            tbody.html('<tr><td colspan="10" class="ts-empty">No training schedule configured for selected month.</td></tr>');
            return;
        }

        const first = rows[0];
        $('#tsKpi').text(first.kpiTitle || 'Training Compliance');
        $('#tsTarget').text((first.targetPercent != null ? first.targetPercent : 100) + '%');
        $('#tsResponsible').text(first.responsible || 'HR Mgr.');

        rows.forEach(function(r, idx) {
            tbody.append(
                '<tr>' +
                '<td>' + (idx + 1) + '</td>' +
                '<td>' + safeText(r.trainingName) + '</td>' +
                '<td>' + safeText(r.targetAudience) + '</td>' +
                '<td>' + safeText(r.trainer) + '</td>' +
                '<td>' + safeText(formatDate(r.trainingDate)) + '</td>' +
                '<td>' + safeText(r.timeSlot) + '</td>' +
                '<td>' + safeText(r.durationHours) + '</td>' +
                '<td>' + safeText(r.venue) + '</td>' +
                '<td>' + safeText(r.fpr) + '</td>' +
                '<td class="ts-status-cell">' + statusMarkup(r.status) + '</td>' +
                '</tr>'
            );
        });
    }

    function findPeriodLabel(month, year) {
        return availablePeriods.find(function(label) {
            const p = periodToMonthYear(label);
            return p && p.month === month && p.year === year;
        }) || null;
    }

    function populateFilters(periods) {
        const monthFilter = $('#trainingMonthFilter');
        const yearFilter = $('#trainingYearFilter');

        monthFilter.empty();
        yearFilter.empty();

        if (!periods || periods.length === 0) {
            monthFilter.append('<option value="">No month</option>');
            yearFilter.append('<option value="">No year</option>');
            return;
        }

        const parsedPeriods = periods.map(function(label) {
            return { label: label, parsed: periodToMonthYear(label) };
        }).filter(function(item) {
            return item.parsed !== null;
        });

        const months = [];
        const years = [];

        parsedPeriods.forEach(function(item) {
            if (!months.includes(item.parsed.month)) months.push(item.parsed.month);
            if (!years.includes(item.parsed.year)) years.push(item.parsed.year);
        });

        const monthNames = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        months.sort(function(a, b) { return a - b; }).forEach(function(m) {
            monthFilter.append('<option value="' + m + '">' + monthNames[m] + '</option>');
        });

        years.sort(function(a, b) { return b - a; }).forEach(function(y) {
            yearFilter.append('<option value="' + y + '">' + y + '</option>');
        });

        if (parsedPeriods.length > 0) {
            monthFilter.val(String(parsedPeriods[0].parsed.month));
            yearFilter.val(String(parsedPeriods[0].parsed.year));
        }
    }

    function loadByPeriod(periodLabel) {
        if (!periodLabel) {
            renderRows([], '');
            return;
        }

        updateSyncStatus('Syncing...');
        $.ajax({
            url: '/api/training-schedule/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function(data) {
                renderRows(Array.isArray(data) ? data : [], periodLabel);
                updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                renderRows([], periodLabel);
                updateSyncStatus('Sync failed');
            }
        });
    }

    function loadFiltersAndData() {
        updateSyncStatus('Syncing...');

        $.ajax({
            url: '/api/training-schedule/periods',
            type: 'GET',
            success: function(data) {
                availablePeriods = Array.isArray(data) ? data : [];
                populateFilters(availablePeriods);

                if (availablePeriods.length === 0) {
                    renderRows([], '');
                    updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
                    return;
                }

                const month = Number($('#trainingMonthFilter').val());
                const year = Number($('#trainingYearFilter').val());
                loadByPeriod(findPeriodLabel(month, year) || availablePeriods[0]);
            },
            error: function() {
                availablePeriods = [];
                populateFilters([]);
                renderRows([], '');
                updateSyncStatus('Sync failed');
            }
        });
    }

    $('#trainingMonthFilter, #trainingYearFilter').on('change', function() {
        const month = Number($('#trainingMonthFilter').val());
        const year = Number($('#trainingYearFilter').val());
        loadByPeriod(findPeriodLabel(month, year));
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'training-schedule-update') {
            loadFiltersAndData();
        }
    });

    loadFiltersAndData();
    setInterval(loadFiltersAndData, 30000);
});
