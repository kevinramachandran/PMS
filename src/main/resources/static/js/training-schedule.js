$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

    let availablePeriods = [];
    let currentPeriodLabel = '';
    const canEditCurrentPage = String(document.body && document.body.dataset.canEditCurrentPage || '').toLowerCase() === 'true';

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

    function safeAttr(value) {
        return safeText(value).replace(/'/g, '&#39;');
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

    function monthKeyFromPeriod(label) {
        const parsed = periodToMonthYear(label);
        if (!parsed) return '';
        return parsed.year + '-' + String(parsed.month).padStart(2, '0');
    }

    function periodLabelFromMonthKey(key) {
        const dt = new Date(key + '-01T00:00:00');
        if (Number.isNaN(dt.getTime())) return '';
        const month = dt.toLocaleString('en-GB', { month: 'short' });
        const year = String(dt.getFullYear()).slice(-2);
        return month + "'" + year;
    }

    function dateFromPeriodLabel(label) {
        const key = monthKeyFromPeriod(label);
        return key ? key + '-01' : '';
    }

    function formatDate(d) {
        if (!d) return '-';
        const dt = new Date(d + 'T00:00:00');
        if (Number.isNaN(dt.getTime())) return d;
        return dt.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    }

    function statusClass(status) {
        const value = String(status || '').toLowerCase();
        return (value.includes('completed') && !value.includes('not')) || value.includes('good') ? 'ts-status-good' : 'ts-status-bad';
    }

    function statusMarkup(status) {
        const raw = String(status || '').trim();
        const text = raw.toLowerCase() === 'daily good' ? 'Completed' : (raw.toLowerCase() === 'daily bad' ? 'Not Completed' : raw);
        const cls = statusClass(text);
        return '<span class="ts-status-pill ' + cls + '">' +
            '<span class="ts-status-dot"></span>' +
            safeText(text) +
            '</span>';
    }

    function renderRows(rows, periodLabel) {
        const tbody = $('#trainingScheduleBody');
        tbody.empty();
        const colCount = canEditCurrentPage ? 11 : 10;

        if (!rows || rows.length === 0) {
            tbody.html('<tr><td colspan="' + colCount + '" class="ts-empty">No training schedule configured for selected month.</td></tr>');
            return;
        }

        rows.forEach(function(r, idx) {
            const editDate = r.configDate || dateFromPeriodLabel(periodLabel);
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
                (canEditCurrentPage ? '<td><button type="button" class="schedule-edit-link" data-date="' + safeAttr(editDate) + '" title="Edit training" aria-label="Edit training"><i class="fas fa-pen-to-square"></i></button></td>' : '') +
                '</tr>'
            );
        });
    }

    let currentTrainingData = [];
    let trainingSortKey = null;
    let trainingSortAsc = true;

    function initializeTrainingSorting() {
        $('table:has(#trainingScheduleBody) thead .sortable').on('click', function() {
            const key = $(this).data('sort-key');
            if (trainingSortKey === key) {
                trainingSortAsc = !trainingSortAsc;
            } else {
                trainingSortKey = key;
                trainingSortAsc = true;
            }

            updateTrainingSort();
            updateTrainingSortIndicators();
        });
    }

    function updateTrainingSortIndicators() {
        $('table:has(#trainingScheduleBody) thead th').each(function() {
            $(this).removeClass('sort-asc sort-desc');
            const key = $(this).data('sort-key');
            if (key === trainingSortKey) {
                $(this).addClass(trainingSortAsc ? 'sort-asc' : 'sort-desc');
            }
        });
    }

    function updateTrainingSort() {
        if (!trainingSortKey || !currentTrainingData.length) return;

        const sorted = [...currentTrainingData].sort(function(a, b) {
            let aVal = a[trainingSortKey];
            let bVal = b[trainingSortKey];

            if (aVal === null || aVal === undefined) aVal = '';
            if (bVal === null || bVal === undefined) bVal = '';

            if (typeof aVal === 'string') {
                aVal = aVal.toLowerCase();
                bVal = bVal.toLowerCase();
                const cmp = aVal.localeCompare(bVal);
                return trainingSortAsc ? cmp : -cmp;
            }

            const numA = Number(aVal);
            const numB = Number(bVal);
            if (!Number.isNaN(numA) && !Number.isNaN(numB)) {
                return trainingSortAsc ? (numA - numB) : (numB - numA);
            }

            const cmp = String(aVal).localeCompare(String(bVal));
            return trainingSortAsc ? cmp : -cmp;
        });

        renderRows(sorted, currentPeriodLabel);
    }


    function findPeriodLabel(month, year) {
        return availablePeriods.find(function(label) {
            const p = periodToMonthYear(label);
            return p && p.month === month && p.year === year;
        }) || null;
    }

    function findPeriodLabelByMonthKey(key) {
        return availablePeriods.find(function(label) {
            return monthKeyFromPeriod(label) === key;
        }) || null;
    }

    function populateFilters(periods, preferredMonthKey) {
        const monthFilter = $('#trainingMonthFilter');

        if (!periods || periods.length === 0) {
            monthFilter.val('');
            monthFilter.prop('disabled', true);
            monthFilter.removeAttr('min max');
            return '';
        }

        const parsedPeriods = periods.map(function(label) {
            return { label: label, parsed: periodToMonthYear(label) };
        }).filter(function(item) {
            return item.parsed !== null;
        });

        if (parsedPeriods.length === 0) {
            monthFilter.val('');
            monthFilter.prop('disabled', true);
            monthFilter.removeAttr('min max');
            return '';
        }

        const availableMonthKeys = parsedPeriods
            .map(function(item) { return monthKeyFromPeriod(item.label); })
            .filter(function(key, index, arr) { return key && arr.indexOf(key) === index; })
            .sort()
            .reverse();

        const selectedMonthKey = preferredMonthKey || availableMonthKeys[0];
        monthFilter.prop('disabled', false);
        monthFilter.removeAttr('min max');
        monthFilter.val(selectedMonthKey);
        return selectedMonthKey;
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
                currentTrainingData = Array.isArray(data) ? data : [];
                currentPeriodLabel = periodLabel;
                renderRows(currentTrainingData, periodLabel);
                initializeTrainingSorting();
                updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                renderRows([], periodLabel);
                updateSyncStatus('Sync failed');
            }
        });
    }

    function loadByMonthKey(monthKey) {
        if (!monthKey) {
            renderRows([], '');
            updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            return;
        }

        const periodLabel = findPeriodLabelByMonthKey(monthKey);
        if (!periodLabel) {
            currentTrainingData = [];
            currentPeriodLabel = periodLabelFromMonthKey(monthKey);
            renderRows([], currentPeriodLabel);
            updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            return;
        }

        loadByPeriod(periodLabel);
    }

    function loadFiltersAndData(preferredMonthKey) {
        updateSyncStatus('Syncing...');

        $.ajax({
            url: '/api/training-schedule/periods',
            type: 'GET',
            success: function(data) {
                availablePeriods = Array.isArray(data) ? data : [];
                const selectedMonthKey = populateFilters(availablePeriods, preferredMonthKey);

                if (availablePeriods.length === 0) {
                    renderRows([], '');
                    updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
                    return;
                }

                loadByMonthKey(selectedMonthKey);
            },
            error: function() {
                availablePeriods = [];
                populateFilters([], '');
                renderRows([], '');
                updateSyncStatus('Sync failed');
            }
        });
    }

    $('#trainingMonthFilter').on('change', function() {
        loadByMonthKey($(this).val());
    });

    $('#trainingScheduleBody').on('click', '.schedule-edit-link', function() {
        const editDate = $(this).data('date') || dateFromPeriodLabel(currentPeriodLabel);
        if (editDate) {
            sessionStorage.setItem('training-schedule-open-date', editDate);
        }
        window.location.href = '/settings?config=training-schedule';
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'training-schedule-update') {
            loadFiltersAndData($('#trainingMonthFilter').val() || '');
        }
    });

    loadFiltersAndData();
    setInterval(function() {
        loadFiltersAndData($('#trainingMonthFilter').val() || '');
    }, 30000);
});

// ── PDF Export ──────────────────────────────────────────────────────────────
function exportTrainingPdf() {
    var btn = document.getElementById('trainingPdfBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Generating...'; }

    var tableData   = PmsReport.readDomTable('trainingScheduleTable');
    if (tableData.columns.length && String(tableData.columns[tableData.columns.length - 1]).toLowerCase() === 'action') {
        tableData.columns = tableData.columns.slice(0, -1);
        tableData.rows = tableData.rows.map(function(row) { return row.slice(0, -1); });
    }
    var monthInput  = document.getElementById('trainingMonthFilter');
    var monthText   = monthInput && monthInput.value ? formatTrainingMonthLabel(monthInput.value) : '-';
    var filterLabel = 'Month: ' + monthText;

    var today = new Date();
    var dd = String(today.getDate()).padStart(2, '0');
    var mm = String(today.getMonth() + 1).padStart(2, '0');
    var yyyy = today.getFullYear();

    PmsReport.generate({
        title:       'Training Compliance Plan',
        filterLabel: filterLabel,
        orientation: 'landscape',
        columns:     tableData.columns,
        rows:        tableData.rows,
        filename:    'Training-Schedule_' + (monthInput && monthInput.value ? monthInput.value : yyyy + '-' + mm) + '.pdf'
    });

    setTimeout(function() {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-file-pdf"></i> Export PDF'; }
    }, 2000);
}

function formatTrainingMonthLabel(key) {
    var dateObj = new Date(key + '-01T00:00:00');
    if (Number.isNaN(dateObj.getTime())) {
        return key || '-';
    }
    return dateObj.toLocaleString('en-GB', { month: 'short', year: 'numeric' });
}
