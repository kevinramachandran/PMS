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
        $('#agendaSyncStatus').text(text);
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

    function toList(str) {
        if (!str) return [];
        return String(str).split(/\r?\n|\|/).map(function(x) { return x.trim(); }).filter(function(x) { return x.length > 0; });
    }

    function renderList(selector, items) {
        const el = $(selector);
        el.empty();
        if (!items || items.length === 0) {
            el.append('<li>-</li>');
            return;
        }
        items.forEach(function(item) {
            el.append('<li>' + escapeHtml(item) + '</li>');
        });
    }

    function escapeHtml(v) {
        const div = document.createElement('div');
        div.textContent = v == null ? '' : String(v);
        return div.innerHTML;
    }

    function renderAgendaRows(data) {
        const rows = [
            { title: data.agenda1Title, points: data.agenda1Points, time: data.agenda1Time, cls: 'ag1' },
            { title: data.agenda2Title, points: data.agenda2Points, time: data.agenda2Time, cls: 'ag2' },
            { title: data.agenda3Title, points: data.agenda3Points, time: data.agenda3Time, cls: 'ag3' },
            { title: data.agenda4Title, points: data.agenda4Points, time: data.agenda4Time, cls: 'ag4' },
            { title: data.agenda5Title, points: data.agenda5Points, time: data.agenda5Time, cls: 'ag5' }
        ];

        const tbody = $('#agAgendaBody');
        tbody.empty();

        rows.forEach(function(r, idx) {
            const points = toList(r.points);
            const pointsHtml = points.map(function(p) { return '<li>' + escapeHtml(p) + '</li>'; }).join('');

            tbody.append(
                '<tr class="' + r.cls + '">' +
                '<td>' +
                '<div class="ag-row-title">' + escapeHtml((idx + 1) + '. ' + (r.title || '-')) + '</div>' +
                '<ul class="ag-row-points">' + (pointsHtml || '<li>-</li>') + '</ul>' +
                '</td>' +
                '<td class="ag-row-time">' + escapeHtml(r.time || '-') + '</td>' +
                '</tr>'
            );
        });
    }

    function renderAgenda(data, periodLabel) {
        if (!data) {
            renderList('#agParticipants', []);
            renderList('#agRoles', []);
            renderList('#agInputsDaily', []);
            renderList('#agInputsFriday', []);
            renderList('#agOutputsDaily', []);
            renderList('#agOutputsFriday', []);
            renderList('#agGroundRules', []);
            $('#agFrequency, #agTime, #agPlace, #agPurposeDaily, #agPurposeWeekly').text('-');
            $('#agendaHeaderTitle').text('Brewery PMS Meeting Agenda (PMS level 4)');
            $('#agAgendaBody').html('<tr><td colspan="2">No agenda configured for selected month.</td></tr>');
            return;
        }

        $('#agendaHeaderTitle').text(data.headerTitle || 'Brewery PMS Meeting Agenda (PMS level 4)');
        $('#agFrequency').text(data.frequency || '-');
        $('#agTime').text(data.meetingTime || '-');
        $('#agPlace').text(data.meetingPlace || '-');
        $('#agPurposeDaily').text(data.purposeDaily || '-');
        $('#agPurposeWeekly').text(data.purposeWeekly || '-');

        renderList('#agParticipants', toList(data.participants));
        renderList('#agRoles', toList(data.rolesResponsibilities));
        renderList('#agInputsDaily', toList(data.inputsDaily));
        renderList('#agInputsFriday', toList(data.inputsFriday));
        renderList('#agOutputsDaily', toList(data.outputsDaily));
        renderList('#agOutputsFriday', toList(data.outputsFriday));
        renderList('#agGroundRules', toList(data.groundRules));

        renderAgendaRows(data);
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
        const monthFilter = $('#agendaMonthFilter');

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
            renderAgenda(null, '');
            return;
        }

        updateSyncStatus('Syncing...');
        $.ajax({
            url: '/api/meeting-agenda/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function(data) {
                renderAgenda(data || null, periodLabel);
                updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                renderAgenda(null, periodLabel);
                updateSyncStatus('Sync failed');
            }
        });
    }

    function loadFiltersAndData() {
        updateSyncStatus('Syncing...');
        $.ajax({
            url: '/api/meeting-agenda/periods',
            type: 'GET',
            success: function(data) {
                availablePeriods = Array.isArray(data) ? data : [];
                populateFilters(availablePeriods);

                if (availablePeriods.length === 0) {
                    renderAgenda(null, '');
                    updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
                    return;
                }

                loadByPeriod(monthInputToPeriodLabel($('#agendaMonthFilter').val()) || availablePeriods[0]);
            },
            error: function() {
                availablePeriods = [];
                populateFilters([]);
                renderAgenda(null, '');
                updateSyncStatus('Sync failed');
            }
        });
    }

    $('#agendaMonthFilter').on('change', function() {
        loadByPeriod(monthInputToPeriodLabel($(this).val()));
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'meeting-agenda-update') {
            loadFiltersAndData();
        }
    });

    loadFiltersAndData();
    setInterval(loadFiltersAndData, 30000);
});
