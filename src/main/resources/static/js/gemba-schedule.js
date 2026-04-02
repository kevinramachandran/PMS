$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

    $('.nav-parent-toggle').on('click', function(e) {
        e.preventDefault();
        $(this).toggleClass('expanded');
        $(this).next('.nav-children').slideToggle(200).toggleClass('show');
    });

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

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value || '';
        return div.innerHTML;
    }

    function rowClass(row) {
        const idx = Number(row.__groupIndex || 0);
        return idx % 2 === 0 ? 'gemba-row-blue' : 'gemba-row-green';
    }

    function groupByFunctionType(rows) {
        const orderedTypes = [
            'Packaging',
            'Quality',
            'HR & Admin',
            'Utility & Maintenance',
            'Customer Supply',
            'B&P',
            'Finance',
            'Health & Safety',
            'Carlsberg Excellence'
        ];

        const map = new Map();
        rows.forEach(function(row) {
            const type = (row.functionType || '').trim() || 'Other';
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

        if (!rows || rows.length === 0) {
            tbody.append('<tr><td colspan="5" class="gemba-empty">No schedule configured for selected month.</td></tr>');
            $('#gembaMonthLabel').text(selectedMonthKey ? monthLabelFromKey(selectedMonthKey) : '-');
            return;
        }

        $('#gembaMonthLabel').text(monthLabelFromKey(selectedMonthKey));

        const grouped = groupByFunctionType(rows);

        grouped.forEach(function(group) {
            tbody.append(
                '<tr class="gemba-row-section">' +
                '<td colspan="5">' + escapeHtml(group.type) + '</td>' +
                '</tr>'
            );

            group.rows.forEach(function(row, index) {
                row.__groupIndex = index;
                const associate = row.associateName || row.functionName || '';
                tbody.append(
                    '<tr class="' + rowClass(row) + '">' +
                    '<td>' + escapeHtml(associate) + '</td>' +
                    '<td>' + escapeHtml(row.week1) + '</td>' +
                    '<td>' + escapeHtml(row.week2) + '</td>' +
                    '<td>' + escapeHtml(row.week3) + '</td>' +
                    '<td>' + escapeHtml(row.week4) + '</td>' +
                    '</tr>'
                );
            });
        });
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
        const monthSelect = $('#gembaMonthFilter');
        monthSelect.empty();

        const uniqueMonths = [];
        dates.forEach(function(dateStr) {
            const key = monthKey(dateStr);
            if (!uniqueMonths.includes(key)) {
                uniqueMonths.push(key);
            }
        });

        if (uniqueMonths.length === 0) {
            monthSelect.append('<option value="">No month</option>');
            return;
        }

        uniqueMonths.forEach(function(key) {
            monthSelect.append('<option value="' + key + '">' + monthLabelFromKey(key) + '</option>');
        });

        monthSelect.val(selectedMonthKey || uniqueMonths[0]);
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
        $.ajax({
            url: '/api/gemba-schedule/date/' + latestDate,
            type: 'GET',
            success: function(data) {
                renderRowsForMonth(Array.isArray(data) ? data : [], selectedMonthKey);
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

    $('#gembaMonthFilter').on('change', function() {
        const selectedMonth = $(this).val();
        loadScheduleForMonth(selectedMonth);
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'gemba-schedule-update') {
            loadFiltersAndSchedule($('#gembaMonthFilter').val() || '');
        }
    });

    loadFiltersAndSchedule('');
    setInterval(function() {
        loadFiltersAndSchedule($('#gembaMonthFilter').val() || '');
    }, 30000);
});
