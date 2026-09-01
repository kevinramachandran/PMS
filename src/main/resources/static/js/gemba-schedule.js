$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

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

    let availableDates = [];
    let currentGembaEditDate = '';
    let departmentOrder = [];
    const canEditCurrentPage = String(document.body && document.body.dataset.canEditCurrentPage || '').toLowerCase() === 'true';

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value || '';
        return div.innerHTML;
    }

    function escapeAttr(value) {
        return escapeHtml(value).replace(/'/g, '&#39;');
    }

    function rowClass(row) {
        const idx = Number(row.__groupIndex || 0);
        return idx % 2 === 0 ? 'gemba-row-blue' : 'gemba-row-green';
    }

    function groupByFunctionType(rows) {
        const orderedTypes = departmentOrder;

        const map = new Map();
        rows.forEach(function(row) {
            const type = (row.functionType || '').trim();
            if (!map.has(type)) {
                map.set(type, []);
            }
            map.get(type).push(row);
        });

        const ordered = [];
        orderedTypes.forEach(function(type) {
            if (map.has(type)) {
                ordered.push({ type: type, rows: map.get(type) });
                map.delete(type);
            }
        });

        map.forEach(function(value, key) {
            ordered.push({ type: key, rows: value });
        });

        return ordered;
    }

    function renderRowsForMonth(rows, selectedMonthKey) {
        const tbody = $('#gembaScheduleBody');
        tbody.empty();
        const colCount = canEditCurrentPage ? 6 : 5;

        if (!rows || rows.length === 0) {
            tbody.append('<tr><td colspan="' + colCount + '" class="gemba-empty">No schedule configured for selected month.</td></tr>');
            $('#gembaMonthLabel').text(selectedMonthKey ? monthLabelFromKey(selectedMonthKey) : '-');
            return;
        }

        $('#gembaMonthLabel').text(monthLabelFromKey(selectedMonthKey));

        const grouped = groupByFunctionType(rows);

        grouped.forEach(function(group) {
            tbody.append(
                '<tr class="gemba-row-section">' +
                '<td colspan="' + colCount + '">' + escapeHtml(group.type || '-') + '</td>' +
                '</tr>'
            );

            group.rows.forEach(function(row, index) {
                row.__groupIndex = index;
                const associate = row.associateName || row.functionName || '';
                const editDate = row.scheduleDate || currentGembaEditDate || '';
                const scheduleId = row.id || '';
                tbody.append(
                    '<tr class="' + rowClass(row) + '">' +
                    '<td>' + escapeHtml(associate) + '</td>' +
                    '<td class="gemba-selectable-cell" data-id="' + escapeAttr(scheduleId) + '" data-location="' + escapeAttr(row.week1 || '') + '" data-week="' + escapeAttr(row.week1 || 'Week 1') + '">' + escapeHtml(row.week1) + '</td>' +
                    '<td class="gemba-selectable-cell" data-id="' + escapeAttr(scheduleId) + '" data-location="' + escapeAttr(row.week2 || '') + '" data-week="' + escapeAttr(row.week2 || 'Week 2') + '">' + escapeHtml(row.week2) + '</td>' +
                    '<td class="gemba-selectable-cell" data-id="' + escapeAttr(scheduleId) + '" data-location="' + escapeAttr(row.week3 || '') + '" data-week="' + escapeAttr(row.week3 || 'Week 3') + '">' + escapeHtml(row.week3) + '</td>' +
                    '<td class="gemba-selectable-cell" data-id="' + escapeAttr(scheduleId) + '" data-location="' + escapeAttr(row.week4 || '') + '" data-week="' + escapeAttr(row.week4 || 'Week 4') + '">' + escapeHtml(row.week4) + '</td>' +
                    (canEditCurrentPage ? '<td><button type="button" class="schedule-edit-link" data-date="' + escapeAttr(editDate) + '" title="Edit schedule" aria-label="Edit schedule"><i class="fas fa-pen-to-square"></i></button></td>' : '') +
                    '</tr>'
                );
            });
        });
    }

    let currentGembaData = [];
    let gembaFunctionOrder = null;

    function initializeGembaSorting() {
        $('table:has(#gembaScheduleBody) thead .sortable').on('click', function() {
            const key = $(this).data('sort-key');
            if (key === 'functionType') {
                if (!gembaFunctionOrder) {
                    gembaFunctionOrder = 'asc';
                } else {
                    gembaFunctionOrder = gembaFunctionOrder === 'asc' ? 'desc' : null;
                }
                updateGembaSort();
                updateGembaSortIndicators();
            }
        });
    }

    function updateGembaSortIndicators() {
        $('table:has(#gembaScheduleBody) thead th').each(function() {
            $(this).removeClass('sort-asc sort-desc');
            const key = $(this).data('sort-key');
            if (key === 'functionType' && gembaFunctionOrder) {
                $(this).addClass(gembaFunctionOrder === 'asc' ? 'sort-asc' : 'sort-desc');
            }
        });
    }

    function updateGembaSort() {
        if (!gembaFunctionOrder || !currentGembaData.length) {
            renderRowsForMonth(currentGembaData, selectedMonthKey);
            return;
        }

        const sorted = [...currentGembaData].sort(function(a, b) {
            const aType = (a.functionType || '').toLowerCase();
            const bType = (b.functionType || '').toLowerCase();
            const cmp = aType.localeCompare(bType);
            return gembaFunctionOrder === 'asc' ? cmp : -cmp;
        });

        renderRowsForMonth(sorted, selectedMonthKey);
    }


    function updateSyncStatus(text) {
        $('#gembaSyncStatus').text(text);
    }

    function formatDateLabel(dateStr) {
        const dateObj = new Date(dateStr + 'T00:00:00');
        if (Number.isNaN(dateObj.getTime())) {
            return dateStr;
        }
        return dateObj.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    }

    function monthKey(dateStr) {
        return dateStr.slice(0, 7);
    }

    function monthLabelFromKey(key) {
        const dateObj = new Date(key + '-01T00:00:00');
        if (Number.isNaN(dateObj.getTime())) {
            return key;
        }
        const month = dateObj.toLocaleString('en-GB', { month: 'short' });
        return month + "'" + dateObj.getFullYear();
    }

    function populateMonthFilter(dates, selectedMonthKey) {
        const monthInput = $('#gembaMonthFilter');

        const uniqueMonths = [];
        dates.forEach(function(dateStr) {
            const key = monthKey(dateStr);
            if (!uniqueMonths.includes(key)) {
                uniqueMonths.push(key);
            }
        });
        uniqueMonths.sort().reverse();

        if (uniqueMonths.length === 0) {
            monthInput.val('');
            monthInput.prop('disabled', true);
            monthInput.removeAttr('min max');
            return;
        }

        monthInput.prop('disabled', false);
        monthInput.removeAttr('min max');
        monthInput.val(selectedMonthKey || uniqueMonths[0]);
    }

    function loadScheduleForMonth(selectedMonthKey) {
        if (!selectedMonthKey) {
            renderRowsForMonth([], '');
            return;
        }

        const datesInMonth = availableDates.filter(function(dateStr) {
            return monthKey(dateStr) === selectedMonthKey;
        });

        if (datesInMonth.length === 0) {
            renderRowsForMonth([], selectedMonthKey);
            updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            return;
        }

        updateSyncStatus('Syncing...');

        const latestDate = datesInMonth[0];
        currentGembaEditDate = latestDate;
        $.ajax({
            url: '/api/gemba-schedule/date/' + latestDate,
            type: 'GET',
            success: function(data) {
                currentGembaData = Array.isArray(data) ? data : [];
                renderRowsForMonth(currentGembaData, selectedMonthKey);
                initializeGembaSorting();
                updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                renderRowsForMonth([], selectedMonthKey);
                updateSyncStatus('Sync failed');
            }
        });
    }

    function loadFiltersAndSchedule(preferredMonthKey) {
        updateSyncStatus('Syncing...');
        $.ajax({
            url: '/api/gemba-schedule/dates',
            type: 'GET',
            success: function(data) {
                availableDates = Array.isArray(data) ? data : [];

                if (availableDates.length === 0) {
                    populateMonthFilter([], '');
                    renderRowsForMonth([], '');
                    updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
                    return;
                }

                const defaultMonth = monthKey(availableDates[0]);
                const initialMonth = preferredMonthKey && availableDates.some(function(d) { return monthKey(d) === preferredMonthKey; })
                    ? preferredMonthKey
                    : defaultMonth;

                populateMonthFilter(availableDates, initialMonth);
                loadScheduleForMonth(initialMonth);
            },
            error: function() {
                availableDates = [];
                renderRowsForMonth([], '');
                updateSyncStatus('Sync failed');
            }
        });
    }

    function loadDepartmentOrder(callback) {
        $.ajax({
            url: '/api/dashboard-config/master-data/DEPARTMENT',
            type: 'GET',
            success: function(data) {
                const items = data && data.status === 'success' && Array.isArray(data.items) ? data.items : [];
                departmentOrder = items.map(function(item) {
                    return String(item.name || '').trim();
                }).filter(function(name) {
                    return name.length > 0;
                });
            },
            complete: function() {
                if (typeof callback === 'function') {
                    callback();
                }
            }
        });
    }

    $('#gembaMonthFilter').on('change', function() {
        const selectedMonth = $(this).val();
        loadScheduleForMonth(selectedMonth);
    });

    $('#gembaScheduleBody').on('click', '.schedule-edit-link', function() {
        const editDate = $(this).data('date') || currentGembaEditDate;
        if (editDate) {
            sessionStorage.setItem('gemba-schedule-open-date', editDate);
        }
        window.location.href = '/settings?config=gemba-schedule';
    });

    $('#gembaScheduleBody').on('click', '.gemba-selectable-cell', function() {
        const scheduleId = $(this).data('id') || '';
        const week = $(this).data('week') || '';
        const location = $(this).data('location') || '';
        window.location.href = '/gemba-walk-config?scheduleId=' + encodeURIComponent(scheduleId)
            + '&week=' + encodeURIComponent(week)
            + '&location=' + encodeURIComponent(location);
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'gemba-schedule-update') {
            loadFiltersAndSchedule($('#gembaMonthFilter').val() || '');
        }
    });

    loadDepartmentOrder(function() {
        loadFiltersAndSchedule('');
    });
    setInterval(function() {
        loadFiltersAndSchedule($('#gembaMonthFilter').val() || '');
    }, 30000);
});

// ── PDF Export ──────────────────────────────────────────────────────────────
function exportGembaPdf() {
    var btn = document.getElementById('gembaPdfBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Generating...'; }

    var tableData   = PmsReport.readDomTable('gembaScheduleTable');
    if (tableData.columns.length && String(tableData.columns[tableData.columns.length - 1]).toLowerCase() === 'action') {
        tableData.columns = tableData.columns.slice(0, -1);
        tableData.rows = tableData.rows.map(function(row) { return row.slice(0, -1); });
    }
    var monthFilter = document.getElementById('gembaMonthFilter');
    var monthLabel  = monthFilter && monthFilter.value ? formatGembaMonthLabel(monthFilter.value) : '-';
    var filterLabel = 'Month: ' + monthLabel;

    var today = new Date();
    var dd = String(today.getDate()).padStart(2, '0');
    var mm = String(today.getMonth() + 1).padStart(2, '0');
    var yyyy = today.getFullYear();

    PmsReport.generate({
        title:       'Gemba Walk Tracker',
        filterLabel: filterLabel,
        orientation: 'portrait',
        columns:     tableData.columns,
        rows:        tableData.rows,
        filename:    'Gemba-Walk-Schedule_' + (monthFilter && monthFilter.value ? monthFilter.value : yyyy + '-' + mm) + '.pdf'
    });

    setTimeout(function() {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-file-pdf"></i> Export PDF'; }
    }, 2000);
}

function formatGembaMonthLabel(key) {
    var dateObj = new Date(key + '-01T00:00:00');
    if (Number.isNaN(dateObj.getTime())) {
        return key || '-';
    }
    var month = dateObj.toLocaleString('en-GB', { month: 'short' });
    return month + "'" + dateObj.getFullYear();
}
