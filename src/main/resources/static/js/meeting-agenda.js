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

    function populateFilters(periods) {
        const monthFilter = $('#agendaMonthFilter');
        const yearFilter = $('#agendaYearFilter');
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

                const month = Number($('#agendaMonthFilter').val());
                const year = Number($('#agendaYearFilter').val());
                loadByPeriod(findPeriodLabel(month, year) || availablePeriods[0]);
            },
            error: function() {
                availablePeriods = [];
                populateFilters([]);
                renderAgenda(null, '');
                updateSyncStatus('Sync failed');
            }
        });
    }

    $('#agendaMonthFilter, #agendaYearFilter').on('change', function() {
        const month = Number($('#agendaMonthFilter').val());
        const year = Number($('#agendaYearFilter').val());
        loadByPeriod(findPeriodLabel(month, year));
    });

    window.addEventListener('storage', function(e) {
        if (e.key === 'meeting-agenda-update') {
            loadFiltersAndData();
        }
    });

    loadFiltersAndData();
    setInterval(loadFiltersAndData, 30000);
});
