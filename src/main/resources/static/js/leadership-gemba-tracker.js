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
        const yearFilter = $('#lgtYearFilter');

        monthFilter.empty();
        yearFilter.empty();

        if (!periods || periods.length === 0) {
            monthFilter.append('<option value="">No month</option>');
            yearFilter.append('<option value="">No year</option>');
            return;
        }

        const periodPairs = periods
            .map(function(p) { return { label: p, parsed: periodToMonthYear(p) }; })
            .filter(function(p) { return p.parsed !== null; });

        const months = [];
        const years = [];

        periodPairs.forEach(function(p) {
            const m = p.parsed.month;
            const y = p.parsed.year;
            if (!months.includes(m)) months.push(m);
            if (!years.includes(y)) years.push(y);
        });

        const monthNames = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        years.sort(function(a, b) { return b - a; }).forEach(function(y) {
            yearFilter.append('<option value="' + y + '">' + y + '</option>');
        });

        function refreshMonths(selectedYear, preferredMonth) {
            monthFilter.empty();
            const monthsForYear = periodPairs
                .filter(function(item) { return item.parsed.year === selectedYear; })
                .map(function(item) { return item.parsed.month; })
                .filter(function(month, index, arr) { return arr.indexOf(month) === index; })
                .sort(function(a, b) { return a - b; });

            monthsForYear.forEach(function(m) {
                monthFilter.append('<option value="' + m + '">' + monthNames[m] + '</option>');
            });

            const fallbackMonth = monthsForYear.length ? monthsForYear[monthsForYear.length - 1] : '';
            const resolvedMonth = monthsForYear.includes(Number(preferredMonth)) ? Number(preferredMonth) : fallbackMonth;
            monthFilter.val(resolvedMonth ? String(resolvedMonth) : '');
        }

        const currentYear = new Date().getFullYear();
        const preferredYear = years.includes(currentYear) ? currentYear : years[0];
        yearFilter.val(String(preferredYear));

        const latest = periodPairs.find(function(item) { return item.parsed.year === preferredYear; }) || periodPairs[0];
        if (latest) {
            refreshMonths(preferredYear, latest.parsed.month);
        }

        yearFilter.off('change.lgtFilterSync').on('change.lgtFilterSync', function() {
            refreshMonths(Number($(this).val()), monthFilter.val());
        });
    }

    function findPeriodLabel(month, year) {
        return availablePeriods.find(function(label) {
            const p = periodToMonthYear(label);
            return p && p.month === month && p.year === year;
        }) || null;
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

                const month = Number($('#lgtMonthFilter').val());
                const year = Number($('#lgtYearFilter').val());
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

    $('#lgtMonthFilter, #lgtYearFilter').on('change', function() {
        const month = Number($('#lgtMonthFilter').val());
        const year = Number($('#lgtYearFilter').val());
        loadByPeriod(findPeriodLabel(month, year));
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'leadership-gemba-tracker-update') {
            loadFiltersAndData();
        }
    });

    loadFiltersAndData();
    setInterval(loadFiltersAndData, 30000);
});
