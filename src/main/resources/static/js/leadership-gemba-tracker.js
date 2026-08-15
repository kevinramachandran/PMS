$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

    let availablePeriods = [];

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
        $('#lgtSyncStatus').text(text);
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

    function safeText(v) {
        const div = document.createElement('div');
        div.textContent = (v === null || v === undefined) ? '' : String(v);
        return div.innerHTML;
    }

    function boolCircle(value) {
        return '<span class="lgt-circle ' + (value ? 'done' : '') + '"></span>';
    }

    function renderRows(rows, periodLabel) {
        const tbody = $('#lgtBody');
        tbody.empty();
        $('#lgtPeriodLabel').text(periodLabel || '-');

        if (!rows || rows.length === 0) {
            tbody.append('<tr><td colspan="19" class="lgt-empty">No data configured for selected period.</td></tr>');
            return;
        }

        rows.forEach(function(r, idx) {
            tbody.append(
                '<tr>' +
                '<td>' + (idx + 1) + '</td>' +
                '<td>' + safeText(r.managerName) + '</td>' +
                '<td>' + safeText(r.department) + '</td>' +
                '<td>' + safeText(r.areaOfCoverage) + '</td>' +
                '<td>' + safeText(r.targetYtd) + '</td>' +
                '<td>' + safeText(r.targetMtd) + '</td>' +
                '<td>' + safeText(r.week1Target) + '</td>' +
                '<td>' + safeText(r.week1Actual) + '</td>' +
                '<td>' + safeText(r.week2Target) + '</td>' +
                '<td>' + safeText(r.week2Actual) + '</td>' +
                '<td>' + safeText(r.week3Target) + '</td>' +
                '<td>' + safeText(r.week3Actual) + '</td>' +
                '<td>' + safeText(r.week4Target) + '</td>' +
                '<td>' + safeText(r.week4Actual) + '</td>' +
                '<td class="lgt-compliance">' + safeText(r.compliancePercent) + '</td>' +
                '<td>' + boolCircle(r.week1Closed) + '</td>' +
                '<td>' + boolCircle(r.week2Closed) + '</td>' +
                '<td>' + boolCircle(r.week3Closed) + '</td>' +
                '<td>' + boolCircle(r.week4Closed) + '</td>' +
                '</tr>'
            );
        });
    }

    function populateFilters(periods) {
        const monthFilter = $('#lgtMonthFilter');

        if (!periods || periods.length === 0) {
            monthFilter.val('');
            monthFilter.removeAttr('min max');
            return;
        }

        const periodPairs = periods
            .map(function(p) { return { label: p, parsed: periodToMonthYear(p) }; })
            .filter(function(p) { return p.parsed !== null; });

        if (periodPairs.length === 0) {
            monthFilter.val('');
            monthFilter.removeAttr('min max');
            return;
        }

        const values = periodPairs.map(function(item) {
            return monthInputValue(item.parsed.month, item.parsed.year);
        }).sort();
        const current = monthInputValue(new Date().getMonth() + 1, new Date().getFullYear());
        const selected = values.includes(current) ? current : values[values.length - 1];
        monthFilter.attr('min', values[0]);
        monthFilter.attr('max', values[values.length - 1]);
        monthFilter.val(selected);
    }

    function findPeriodLabel(month, year) {
        return availablePeriods.find(function(label) {
            const p = periodToMonthYear(label);
            return p && p.month === month && p.year === year;
        }) || null;
    }

    function monthInputValue(month, year) {
        return year + '-' + String(month).padStart(2, '0');
    }

    function monthInputToPeriodLabel(value) {
        const parts = String(value || '').split('-');
        if (parts.length !== 2) return null;
        return findPeriodLabel(Number(parts[1]), Number(parts[0]));
    }

    function loadByPeriod(periodLabel) {
        if (!periodLabel) {
            renderRows([], '');
            updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            return;
        }

        updateSyncStatus('Syncing...');
        $.ajax({
            url: '/api/leadership-gemba-tracker/period/' + encodeURIComponent(periodLabel),
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
            url: '/api/leadership-gemba-tracker/periods',
            type: 'GET',
            success: function(data) {
                availablePeriods = Array.isArray(data) ? data : [];
                populateFilters(availablePeriods);

                if (availablePeriods.length === 0) {
                    renderRows([], '');
                    updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
                    return;
                }

                loadByPeriod(monthInputToPeriodLabel($('#lgtMonthFilter').val()) || availablePeriods[0]);
            },
            error: function() {
                availablePeriods = [];
                populateFilters([]);
                renderRows([], '');
                updateSyncStatus('Sync failed');
            }
        });
    }

    $('#lgtMonthFilter').on('change', function() {
        loadByPeriod(monthInputToPeriodLabel($(this).val()));
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'leadership-gemba-tracker-update') {
            loadFiltersAndData();
        }
    });

    loadFiltersAndData();
    setInterval(loadFiltersAndData, 30000);
});
