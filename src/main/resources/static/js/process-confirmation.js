$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

    let availablePeriods = [];

    const defaultQuestions = [
        'Are all inputs on the boards up to date before starting?',
        'Was everyone on time and respected the rules?',
        'Did the meeting start, finish on time and follow the agenda?',
        'Are upcoming goals clear and actions to mitigate potential risks captured?',
        'Is there an action for every underperforming KPI or negative trend?',
        'Are PDCA and Root Cause fields used correctly?',
        'Are overdue actions kept to minimum and escalation used correctly?',
        'Was problem solving inside the meeting avoided and Gemba walk used instead?',
        'Was meeting atmosphere productive and effective?',
        'Are the top priorities until next meeting clear?'
    ];

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

    function updateSyncStatus(text) {
        $('#pcSyncStatus').text(text);
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

    function normalizeStatuses(raw) {
        const out = [];
        if (!raw) {
            for (let i = 0; i < 31; i++) out.push('N');
            return out;
        }

        const tokens = String(raw)
            .replace(/\|/g, ',')
            .replace(/\n/g, ',')
            .split(',')
            .map(function(x) { return x.trim().toUpperCase(); })
            .filter(function(x) { return x.length > 0; });

        for (let i = 0; i < 31; i++) {
            const value = tokens[i] || 'N';
            if (value === 'G' || value === 'GOOD' || value === 'DAILY GOOD' || value === '1') {
                out.push('G');
            } else if (value === 'B' || value === 'BAD' || value === 'DAILY BAD' || value === '0') {
                out.push('B');
            } else {
                out.push('N');
            }
        }

        return out;
    }

    function statusCellClass(status) {
        if (status === 'G') return 'good';
        if (status === 'B') return 'bad';
        return 'na';
    }

    function renderDayHeader() {
        const row = $('#pcDayHeader');
        row.empty();
        row.append('<th class="pc-question-col"></th>');
        for (let day = 1; day <= 31; day++) {
            row.append('<th class="pc-day-col">' + day + '</th>');
        }
    }

    function renderScoreRow(data) {
        const values = [
            data?.janScore, data?.febScore, data?.marScore, data?.aprScore, data?.mayScore, data?.junScore,
            data?.julScore, data?.augScore, data?.sepScore, data?.octScore, data?.novScore, data?.decScore, data?.ytdScore
        ];

        const row = $('#pcScoreRow');
        row.empty();
        values.forEach(function(v) {
            const text = v == null ? '-' : (String(v) + '%');
            row.append('<td>' + text + '</td>');
        });
    }

    function renderRows(data) {
        const body = $('#pcBody');
        body.empty();

        const questions = [
            data?.question1 || defaultQuestions[0],
            data?.question2 || defaultQuestions[1],
            data?.question3 || defaultQuestions[2],
            data?.question4 || defaultQuestions[3],
            data?.question5 || defaultQuestions[4],
            data?.question6 || defaultQuestions[5],
            data?.question7 || defaultQuestions[6],
            data?.question8 || defaultQuestions[7],
            data?.question9 || defaultQuestions[8],
            data?.question10 || defaultQuestions[9]
        ];

        const statusesByRow = [
            normalizeStatuses(data?.q1Statuses),
            normalizeStatuses(data?.q2Statuses),
            normalizeStatuses(data?.q3Statuses),
            normalizeStatuses(data?.q4Statuses),
            normalizeStatuses(data?.q5Statuses),
            normalizeStatuses(data?.q6Statuses),
            normalizeStatuses(data?.q7Statuses),
            normalizeStatuses(data?.q8Statuses),
            normalizeStatuses(data?.q9Statuses),
            normalizeStatuses(data?.q10Statuses)
        ];

        for (let i = 0; i < questions.length; i++) {
            const tr = $('<tr></tr>');
            tr.append('<td class="pc-question">' + (i + 1) + '. ' + escapeHtml(questions[i]) + '</td>');

            statusesByRow[i].forEach(function(status) {
                tr.append('<td><span class="pc-status ' + statusCellClass(status) + '"></span></td>');
            });

            body.append(tr);
        }

        const totalStatuses = normalizeStatuses(data?.totalStatuses);
        const totalTr = $('<tr class="pc-total-row"></tr>');
        totalTr.append('<td class="pc-question">Total Score &gt; 80%</td>');
        totalStatuses.forEach(function(status) {
            totalTr.append('<td><span class="pc-status ' + statusCellClass(status) + '"></span></td>');
        });
        body.append(totalTr);
    }

    function render(data) {
        $('#pcKpi').text(data?.kpiTitle || 'Meeting Process Confirmation');
        $('#pcTarget').text(data?.targetLabel || '> 80%');
        $('#pcResponsible').text(data?.responsible || 'CarlEx Mgr.');
        $('#pcMonth').text(data?.monthLabel || '-');

        renderScoreRow(data || {});
        renderRows(data || null);
    }

    function escapeHtml(v) {
        const div = document.createElement('div');
        div.textContent = v == null ? '' : String(v);
        return div.innerHTML;
    }

    function findPeriodLabel(month, year) {
        return availablePeriods.find(function(label) {
            const p = periodToMonthYear(label);
            return p && p.month === month && p.year === year;
        }) || null;
    }

    function populateFilters(periods) {
        const monthFilter = $('#pcMonthFilter');
        const yearFilter = $('#pcYearFilter');
        monthFilter.empty();
        yearFilter.empty();

        if (!periods || periods.length === 0) {
            monthFilter.append('<option value="">No month</option>');
            yearFilter.append('<option value="">No year</option>');
            return;
        }

        const parsed = periods.map(function(label) {
            return { label: label, parsed: periodToMonthYear(label) };
        }).filter(function(item) { return item.parsed !== null; });

        const months = [];
        const years = [];
        parsed.forEach(function(item) {
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

        if (parsed.length > 0) {
            monthFilter.val(String(parsed[0].parsed.month));
            yearFilter.val(String(parsed[0].parsed.year));
        }
    }

    function loadByPeriod(periodLabel) {
        if (!periodLabel) {
            render(null);
            return;
        }

        updateSyncStatus('Syncing...');
        $.ajax({
            url: '/api/process-confirmation/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function(data) {
                render(data || null);
                updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                render(null);
                updateSyncStatus('Sync failed');
            }
        });
    }

    function loadFiltersAndData() {
        updateSyncStatus('Syncing...');
        $.ajax({
            url: '/api/process-confirmation/periods',
            type: 'GET',
            success: function(data) {
                availablePeriods = Array.isArray(data) ? data : [];
                populateFilters(availablePeriods);

                if (availablePeriods.length === 0) {
                    render(null);
                    updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
                    return;
                }

                const month = Number($('#pcMonthFilter').val());
                const year = Number($('#pcYearFilter').val());
                loadByPeriod(findPeriodLabel(month, year) || availablePeriods[0]);
            },
            error: function() {
                availablePeriods = [];
                populateFilters([]);
                render(null);
                updateSyncStatus('Sync failed');
            }
        });
    }

    $('#pcMonthFilter, #pcYearFilter').on('change', function() {
        const month = Number($('#pcMonthFilter').val());
        const year = Number($('#pcYearFilter').val());
        loadByPeriod(findPeriodLabel(month, year));
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'process-confirmation-update') {
            loadFiltersAndData();
        }
    });

    renderDayHeader();
    render(null);
    loadFiltersAndData();
    setInterval(loadFiltersAndData, 30000);
});
