$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');

    let availablePeriods = [];
    let processObservationOptions = [];

    const processObservationCategories = [
        'ZM_OBSERVATION',
        'PM_OBSERVATION',
        'QM_OBSERVATION'
    ];

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

    function getProcessConfirmationDefaultQuestions() {
        return processObservationOptions.map(function(option) {
            return option.name;
        }).slice(0, 10);
    }

    function hasProcessObservationOption(value) {
        const normalized = String(value || '').trim();
        return processObservationOptions.some(function(option) {
            return option.name === normalized;
        });
    }

    function processQuestionValue(value, fallback) {
        const requested = String(value || '').trim();
        const defaultValue = String(fallback || '').trim();
        if (hasProcessObservationOption(requested)) {
            return requested;
        }
        return hasProcessObservationOption(defaultValue) ? defaultValue : '';
    }

    function loadProcessObservationOptions(callback) {
        const requests = processObservationCategories.map(function(category) {
            return $.ajax({
                url: '/api/dashboard-config/process-master-data/' + encodeURIComponent(category),
                type: 'GET'
            }).then(function(data) {
                const items = data && data.status === 'success' && Array.isArray(data.items) ? data.items : [];
                return items.map(function(item) {
                    return { category: category, name: String(item.name || '').trim() };
                }).filter(function(item) {
                    return item.name.length > 0;
                });
            });
        });

        $.when.apply($, requests).done(function() {
            processObservationOptions = Array.prototype.concat.apply([], Array.prototype.slice.call(arguments));
            if (typeof callback === 'function') {
                callback();
            }
        }).fail(function() {
            processObservationOptions = [];
            if (typeof callback === 'function') {
                callback();
            }
        });
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

        const defaults = getProcessConfirmationDefaultQuestions();
        const questions = [
            processQuestionValue(data?.question1, defaults[0]),
            processQuestionValue(data?.question2, defaults[1]),
            processQuestionValue(data?.question3, defaults[2]),
            processQuestionValue(data?.question4, defaults[3]),
            processQuestionValue(data?.question5, defaults[4]),
            processQuestionValue(data?.question6, defaults[5]),
            processQuestionValue(data?.question7, defaults[6]),
            processQuestionValue(data?.question8, defaults[7]),
            processQuestionValue(data?.question9, defaults[8]),
            processQuestionValue(data?.question10, defaults[9])
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

    function monthInputValue(month, year) {
        return year + '-' + String(month).padStart(2, '0');
    }

    function monthInputToPeriodLabel(value) {
        const parts = String(value || '').split('-');
        if (parts.length !== 2) return null;
        return findPeriodLabel(Number(parts[1]), Number(parts[0]));
    }

    function populateFilters(periods) {
        const monthFilter = $('#pcMonthFilter');

        if (!periods || periods.length === 0) {
            monthFilter.val('');
            monthFilter.removeAttr('min max');
            return;
        }

        const parsed = periods.map(function(label) {
            return { label: label, parsed: periodToMonthYear(label) };
        }).filter(function(item) { return item.parsed !== null; });

        if (parsed.length === 0) {
            monthFilter.val('');
            monthFilter.removeAttr('min max');
            return;
        }

        const values = parsed.map(function(item) {
            return monthInputValue(item.parsed.month, item.parsed.year);
        }).sort();
        const current = monthInputValue(new Date().getMonth() + 1, new Date().getFullYear());
        const selected = values.includes(current) ? current : values[values.length - 1];
        monthFilter.attr('min', values[0]);
        monthFilter.attr('max', values[values.length - 1]);
        monthFilter.val(selected);
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

                loadByPeriod(monthInputToPeriodLabel($('#pcMonthFilter').val()) || availablePeriods[0]);
            },
            error: function() {
                availablePeriods = [];
                populateFilters([]);
                render(null);
                updateSyncStatus('Sync failed');
            }
        });
    }

    $('#pcMonthFilter').on('change', function() {
        loadByPeriod(monthInputToPeriodLabel($(this).val()));
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'process-confirmation-update') {
            loadProcessObservationOptions(loadFiltersAndData);
        }
    });

    renderDayHeader();
    loadProcessObservationOptions(function() {
        render(null);
        loadFiltersAndData();
    });
    setInterval(loadFiltersAndData, 30000);
});
