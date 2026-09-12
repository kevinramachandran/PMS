$(function() {
    'use strict';

    const API = '/api/carlex-process-confirmation';
    const CHART_COLORS = ['#047857', '#2563eb', '#d97706', '#dc2626', '#7c3aed', '#0891b2', '#65a30d', '#be185d'];
    const IMAGE_FIELDS = ['zm1ObservationImage', 'zm2ObservationImage', 'pm1ObservationImage', 'pm2ObservationImage', 'qm1ObservationImage', 'qm2ObservationImage'];
    let records = [];
    let masterPlants = [];
    let masterDepartments = [];
    let masterAreas = [];
    let areaLookup = new Map();
    let searchTerm = '';
    let statusChart = null;
    let departmentChart = null;
    let detailsVisible = $('#toggleDetailsBtn').length === 0 || $('#pcRecordsBody').length > 0;

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function(ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function normalize(value) {
        return String(value || '').trim();
    }

    function lower(value) {
        return normalize(value).toLowerCase();
    }

    function matchesText(left, right) {
        const expected = lower(right);
        return expected && lower(left) === expected;
    }

    function todayIso() {
        const now = new Date();
        return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
    }

    function filterAnchorParts() {
        const selected = $('#anchorDateFilter').val() || todayIso();
        return { date: selected, month: selected.slice(0, 7), year: selected.slice(0, 4) };
    }

    function recordDate(record) {
        return normalize(record && record.dateOfGwProcessConfirmationConducted);
    }

    function displayDate(value) {
        const text = normalize(value);
        const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
        return iso ? iso[3] + '/' + iso[2] + '/' + iso[1] : text;
    }

    function itemName(item) {
        return normalize(item && item.name);
    }

    function itemParentPlant(item) {
        return normalize(item && (item.parentPlant || item.plant || item.plantName));
    }

    function itemParentDepartment(item) {
        return normalize(item && (item.parentDepartment || item.department || item.departmentName));
    }

    function recordArea(record) {
        return normalize(record && (record.areaOfGwProcessConfirmationConducted || record.area || record.areaName));
    }

    function areaForRecord(record) {
        const areaName = recordArea(record);
        if (!areaName) {
            return null;
        }
        const candidates = masterAreas.filter(function(item) {
            return matchesText(itemName(item), areaName);
        });
        if (!candidates.length) {
            return null;
        }
        const directDepartment = normalize(record && record.department);
        return candidates.find(function(item) {
            return matchesText(itemParentDepartment(item), directDepartment);
        }) || candidates[0];
    }

    function recordPlant(record) {
        const direct = normalize(record && (record.plant || record.plantName));
        if (direct) {
            return direct;
        }
        const area = areaForRecord(record) || areaLookup.get(lower(recordArea(record)));
        return itemParentPlant(area);
    }

    function recordDepartment(record) {
        const direct = normalize(record && record.department);
        if (direct) {
            return direct;
        }
        const area = areaForRecord(record) || areaLookup.get(lower(recordArea(record)));
        return itemParentDepartment(area);
    }

    function safeParse(value) {
        if (!value) {
            return [];
        }
        try {
            const parsed = JSON.parse(value);
            return Array.isArray(parsed) ? parsed : [];
        } catch (err) {
            return [];
        }
    }

    function legacyObservation(record, prefix, index) {
        const base = prefix + index;
        const description = normalize(record && record[base + 'Description']);
        const actions = normalize(record && record[base + 'CounterMeasureActions']);
        const status = normalize(record && record[base + 'Status']);
        const image = normalize(record && record[base + 'ObservationImage']);
        if (!description && !actions && !status && !image) {
            return null;
        }
        return { description: description, counterMeasureActions: actions, status: status, observationImage: image };
    }

    function observations(record) {
        const dynamicRows = ['zmObservationsJson', 'pmObservationsJson', 'qmObservationsJson']
            .flatMap(function(field) { return safeParse(record && record[field]); });
        if (dynamicRows.length) {
            return dynamicRows;
        }
        return [
            legacyObservation(record, 'zm', 1),
            legacyObservation(record, 'zm', 2),
            legacyObservation(record, 'pm', 1),
            legacyObservation(record, 'pm', 2),
            legacyObservation(record, 'qm', 1),
            legacyObservation(record, 'qm', 2)
        ].filter(Boolean);
    }

    function isObservationClosed(observation) {
        const status = lower(observation && observation.status);
        return status === 'a' || status === 'closed';
    }

    function isClosed(record) {
        const items = observations(record);
        return items.length > 0 && items.every(isObservationClosed);
    }

    function statusLabel(record) {
        return isClosed(record) ? 'Closed' : 'Open';
    }

    function daysOpen(record) {
        if (isClosed(record)) {
            return 0;
        }
        const dateValue = recordDate(record);
        if (!dateValue) {
            return 0;
        }
        const raised = new Date(dateValue + 'T00:00:00');
        const anchor = new Date(($('#anchorDateFilter').val() || todayIso()) + 'T00:00:00');
        if (Number.isNaN(raised.getTime()) || Number.isNaN(anchor.getTime())) {
            return 0;
        }
        return Math.max(0, Math.floor((anchor.getTime() - raised.getTime()) / 86400000));
    }

    function inPeriod(record) {
        const dateValue = recordDate(record);
        const period = $('#periodFilter').val() || 'ALL';
        const parts = filterAnchorParts();
        if (period === 'ALL') {
            return true;
        }
        if (!dateValue) {
            return false;
        }
        if (period === 'YTD') {
            return dateValue.slice(0, 4) === parts.year;
        }
        if (period === 'MTD') {
            return dateValue.slice(0, 7) === parts.month;
        }
        return dateValue === parts.date;
    }

    function matchesSelect(value, selected) {
        return !selected || selected === 'all' || lower(value) === lower(selected);
    }

    function observationsSummary(record) {
        return observations(record).map(function(observation, index) {
            return 'Observation ' + (index + 1) + ': ' + [
                observation.description,
                observation.counterMeasureActions,
                observation.status,
                observation.observationImage ? 'Picture: ' + observation.observationImage : ''
            ].filter(Boolean).join(' | ');
        }).join(' / ');
    }

    function filteredRecords() {
        const status = $('#statusFilter').val() || 'all';
        const plant = $('#plantFilter').val() || 'all';
        const department = $('#departmentFilter').val() || 'all';
        const area = $('#areaFilter').val() || 'all';
        const term = searchTerm.trim().toLowerCase();

        return records.filter(function(record) {
            if (!inPeriod(record)
                    || !matchesSelect(recordPlant(record), plant)
                    || !matchesSelect(recordDepartment(record), department)
                    || !matchesSelect(recordArea(record), area)) {
                return false;
            }
            if (status === 'closed' && !isClosed(record)) {
                return false;
            }
            if (status === 'open' && isClosed(record)) {
                return false;
            }
            if (term) {
                return [
                    record.id,
                    recordDepartment(record),
                    recordArea(record),
                    record.name,
                    record.email,
                    recordDate(record),
                    record.gwPcWeek,
                    record.assignedTo,
                    record.areaResponsibility,
                    observationsSummary(record),
                    statusLabel(record)
                ].join(' ').toLowerCase().includes(term);
            }
            return true;
        });
    }

    function uniqueValues(accessor, sourceRows) {
        const seen = new Set();
        (sourceRows || records).forEach(function(record) {
            const value = accessor(record);
            if (value) {
                seen.add(value);
            }
        });
        return Array.from(seen).sort(function(a, b) { return a.localeCompare(b); });
    }

    function uniqueList(values) {
        return (values || []).filter(Boolean).filter(function(value, index, list) {
            return list.findIndex(function(candidate) { return lower(candidate) === lower(value); }) === index;
        }).sort(function(a, b) { return a.localeCompare(b); });
    }

    function optionHtml(value) {
        return '<option value="' + escapeHtml(value) + '">' + escapeHtml(value) + '</option>';
    }

    function populateFilter(selector, values, current, allLabel) {
        const selected = current || $(selector).val() || 'all';
        const html = '<option value="all">' + escapeHtml(allLabel || 'All') + '</option>' + values.map(optionHtml).join('');
        const matchingValue = values.find(function(value) { return lower(value) === lower(selected); });
        $(selector).html(html).val(matchingValue || 'all');
    }

    function updateFilterOptions() {
        const periodRows = records.filter(inPeriod);
        const plantValues = uniqueList(uniqueValues(recordPlant, periodRows).concat(masterPlants.map(itemName)));
        populateFilter('#plantFilter', plantValues, $('#plantFilter').val(), 'All');

        const plant = $('#plantFilter').val();
        const plantRows = periodRows.filter(function(record) {
            return matchesSelect(recordPlant(record), plant);
        });
        const departmentMasterValues = masterDepartments
            .filter(function(item) { return matchesSelect(itemParentPlant(item), plant); })
            .map(itemName);
        populateFilter('#departmentFilter', uniqueList(uniqueValues(recordDepartment, plantRows).concat(departmentMasterValues)), $('#departmentFilter').val(), 'All');

        const department = $('#departmentFilter').val();
        const departmentRows = plantRows.filter(function(record) {
            return matchesSelect(recordDepartment(record), department);
        });
        const areaMasterValues = masterAreas
            .filter(function(item) { return matchesSelect(itemParentPlant(item), plant) && matchesSelect(itemParentDepartment(item), department); })
            .map(itemName);
        populateFilter('#areaFilter', uniqueList(uniqueValues(recordArea, departmentRows).concat(areaMasterValues)), $('#areaFilter').val(), 'All');
    }

    function setSyncStatus(text) {
        $('#pcReportingSyncStatus').text(text);
    }

    function updateFilterSummary(total) {
        const bits = [];
        const period = $('#periodFilter').val() || 'ALL';
        const anchor = $('#anchorDateFilter').val() || todayIso();
        bits.push(period === 'ALL' ? 'All periods' : period + ' from ' + anchor);
        ['plantFilter', 'departmentFilter', 'areaFilter', 'statusFilter'].forEach(function(id) {
            const value = $('#' + id).val();
            if (value && value !== 'all') {
                bits.push($('#' + id).prev('label').text() + ': ' + value);
            }
        });
        $('#pcFilterSummary').text(bits.join(' | ') + ' | ' + total + ' records');
    }

    function updateCounts(rows) {
        const closed = rows.filter(isClosed).length;
        const total = rows.length;
        const open = total - closed;
        $('#reportedCount').text(total);
        $('#openCount').text(open);
        $('#closedCount').text(closed);
        $('#closedPercent').text((total ? Math.round((closed / total) * 100) : 0) + '%');
        $('.gw-count-card').removeClass('active')
            .filter(function() {
                return !$(this).hasClass('gw-percent-card')
                    && String($(this).data('status-filter') || 'all') === ($('#statusFilter').val() || 'all');
            }).addClass('active');
        updateFilterSummary(total);
    }

    function destroyChart(instance) {
        if (instance) {
            instance.destroy();
        }
    }

    function renderStatusChart(rows) {
        const closed = rows.filter(isClosed).length;
        const open = rows.length - closed;
        const context = document.getElementById('pcStatusChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        destroyChart(statusChart);
        statusChart = new Chart(context, {
            type: 'bar',
            data: {
                labels: ['Total', 'Open', 'Closed'],
                datasets: [{ label: 'Process Confirmations', data: [rows.length, open, closed], backgroundColor: ['#0f766e', '#d97706', '#16a34a'], borderRadius: 6 }]
            },
            options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
        });
    }

    function groupCounts(rows, accessor) {
        const groups = new Map();
        rows.forEach(function(record) {
            const key = accessor(record) || 'Unassigned';
            const current = groups.get(key) || { label: key, total: 0, open: 0, closed: 0, daysOpenTotal: 0 };
            current.total += 1;
            if (isClosed(record)) {
                current.closed += 1;
            } else {
                current.open += 1;
                current.daysOpenTotal += daysOpen(record);
            }
            groups.set(key, current);
        });
        return Array.from(groups.values()).sort(function(a, b) {
            return b.total - a.total || a.label.localeCompare(b.label);
        });
    }

    function renderDepartmentChart(rows) {
        const groups = groupCounts(rows, recordDepartment);
        const context = document.getElementById('pcDepartmentChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        destroyChart(departmentChart);
        departmentChart = new Chart(context, {
            type: 'pie',
            data: {
                labels: groups.map(function(group) { return group.label; }),
                datasets: [{ data: groups.map(function(group) { return group.total; }), backgroundColor: groups.map(function(_, index) { return CHART_COLORS[index % CHART_COLORS.length]; }), borderColor: '#ffffff', borderWidth: 2 }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'right',
                        labels: {
                            boxWidth: 12,
                            font: { size: 11 },
                            generateLabels: function(chart) {
                                const values = chart.data.datasets[0].data || [];
                                const total = values.reduce(function(sum, value) { return sum + Number(value || 0); }, 0) || 1;
                                return (chart.data.labels || []).map(function(label, index) {
                                    const value = Number(values[index] || 0);
                                    return { text: label + ': ' + value + ' (' + Math.round((value / total) * 100) + '%)', fillStyle: chart.data.datasets[0].backgroundColor[index], strokeStyle: '#ffffff', lineWidth: 2, hidden: false, index: index };
                                });
                            }
                        }
                    }
                }
            }
        });
    }

    function summaryTable(groups, firstColumnLabel) {
        if (!groups.length) {
            return '<div class="gw-summary-empty">No data for selected filters.</div>';
        }
        return '<table><thead><tr><th>' + escapeHtml(firstColumnLabel) + '</th><th>Open</th><th>Closed</th><th>% Closed</th><th>Average Days Open</th></tr></thead><tbody>' +
            groups.map(function(group) {
                const closedPercent = group.total ? Math.round((group.closed / group.total) * 100) : 0;
                const averageDaysOpen = group.open ? Math.round(group.daysOpenTotal / group.open) : 0;
                return '<tr><td>' + escapeHtml(group.label) + '</td><td>' + group.open + '</td><td>' + group.closed + '</td><td>' + closedPercent + '%</td><td>' + averageDaysOpen + '</td></tr>';
            }).join('') +
            '</tbody></table>';
    }

    function renderSummaryTables(rows) {
        $('#departmentSummaryTable').html(summaryTable(groupCounts(rows, recordDepartment), 'Department'));
        $('#areaSummaryTable').html(summaryTable(groupCounts(rows, recordArea), 'Area'));
    }

    function renderRows(rows) {
        const legacyRecordsTable = $('#pcRecordsBody').length > 0;
        const $body = legacyRecordsTable ? $('#pcRecordsBody') : $('#pcReportingTableBody');
        if (!detailsVisible) {
            $body.empty();
            return;
        }
        if (!rows.length) {
            $body.html('<tr><td colspan="' + (legacyRecordsTable ? 13 : 14) + '" class="empty-row">No CarlEX Process Confirmation records found.</td></tr>');
            return;
        }
        if (legacyRecordsTable) {
            $body.html(rows.map(function(record, index) {
                return '<tr>' +
                    '<td>' + (index + 1) + '</td>' +
                    '<td>' + escapeHtml(record.id) + '</td>' +
                    '<td>' + escapeHtml(displayDate(recordDate(record))) + '</td>' +
                    '<td>' + escapeHtml(record.name) + '</td>' +
                    '<td>' + escapeHtml(recordArea(record)) + '</td>' +
                    '<td>' + escapeHtml(record.areaResponsibility) + '</td>' +
                    '<td>' + escapeHtml(record.assignedTo) + '</td>' +
                    '<td>' + escapeHtml(record.processConfirmationDoneBy) + '</td>' +
                    '<td>' + escapeHtml(record.startTime) + '</td>' +
                    '<td>' + escapeHtml(record.completionTime) + '</td>' +
                    '<td>' + IMAGE_FIELDS.map(function(field) { return attachmentIcon('process-confirmation', record[field], record[field]); }).join(' ') + '</td>' +
                    '<td class="assignment-history-cell" data-record-id="' + escapeHtml(record.id) + '">Loading...</td>' +
                    '<td><button type="button" class="gw-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open Process Confirmation record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
                '</tr>';
            }).join(''));
            $('.assignment-history-cell').each(function() {
                const cell = $(this);
                $.getJSON(API + '/records/' + cell.data('record-id') + '/history', function(entries) {
                    cell.html(formatAssignmentHistory(entries));
                });
            });
            return;
        }
        $body.html(rows.map(function(record, index) {
            return '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td>' + escapeHtml(recordDepartment(record)) + '</td>' +
                '<td>' + escapeHtml(recordArea(record)) + '</td>' +
                '<td>' + escapeHtml(record.name) + '</td>' +
                '<td>' + escapeHtml(record.email) + '</td>' +
                '<td>' + escapeHtml(displayDate(recordDate(record))) + '</td>' +
                '<td>' + escapeHtml(record.gwPcWeek) + '</td>' +
                '<td><span class="gw-status-pill">' + observations(record).length + '</span></td>' +
                '<td>' + escapeHtml(statusLabel(record)) + '</td>' +
                '<td>' + escapeHtml(record.assignedTo) + '</td>' +
                '<td>' + escapeHtml(record.areaResponsibility) + '</td>' +
                '<td>' + IMAGE_FIELDS.map(function(field) { return attachmentIcon('process-confirmation', record[field], record[field]); }).join(' ') + '</td>' +
                '<td class="assignment-history-cell" data-record-id="' + escapeHtml(record.id) + '">Loading...</td>' +
                '<td><button type="button" class="gw-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open Process Confirmation record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
            '</tr>';
        }).join(''));
        $('.assignment-history-cell').each(function() {
            const cell = $(this);
            $.getJSON(API + '/records/' + cell.data('record-id') + '/history', function(entries) {
                cell.html(formatAssignmentHistory(entries));
            });
        });
    }

    function applyView(skipFilterRefresh) {
        if (!skipFilterRefresh) {
            updateFilterOptions();
        }
        const rows = filteredRecords();
        updateCounts(rows);
        renderStatusChart(rows);
        renderDepartmentChart(rows);
        renderSummaryTables(rows);
        renderRows(rows);
    }

    function rebuildMasterLookups() {
        areaLookup = new Map();
        masterAreas.forEach(function(item) {
            const name = lower(item && item.name);
            if (name) {
                areaLookup.set(name, item);
            }
        });
    }

    function loadMasterData() {
        $.getJSON(API + '/options', function(data) {
            const options = data && data.options ? data.options : {};
            masterPlants = Array.isArray(options.plantItems) ? options.plantItems : (options.plants || []).map(function(name) { return { name: name }; });
            masterDepartments = Array.isArray(options.departmentItems) ? options.departmentItems : (options.departments || []).map(function(name) { return { name: name }; });
            masterAreas = Array.isArray(options.areaItems) ? options.areaItems : (options.areas || []).map(function(name) { return { name: name }; });
            rebuildMasterLookups();
            applyView();
        });
    }

    function loadRecords() {
        setSyncStatus('Syncing...');
        $.ajax({
            url: API + '/records',
            type: 'GET',
            success: function(data) {
                records = data && Array.isArray(data.records) ? data.records : [];
                applyView();
                setSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                records = [];
                applyView();
                setSyncStatus('Sync failed');
            }
        });
    }

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

    $('#periodFilter, #anchorDateFilter').on('change', function() {
        applyView();
    });

    $('#plantFilter, #departmentFilter').on('change', function() {
        applyView();
    });

    $('#areaFilter, #statusFilter').on('change', function() {
        applyView(true);
    });

    $('.gw-count-card').on('click', function() {
        $('#statusFilter').val($(this).data('status-filter') || 'all');
        applyView(true);
    });

    $('#toggleDetailsBtn').on('click', function() {
        detailsVisible = !detailsVisible;
        $('.gw-detail-table-wrap').prop('hidden', !detailsVisible);
        $('#toggleDetailsBtn span').text(detailsVisible ? 'Hide Details' : 'Show Details');
        renderRows(filteredRecords());
    });

    $('#pcReportingSearch').on('input', function() {
        searchTerm = $(this).val() || '';
        $(this).closest('.issue-search').toggleClass('has-value', searchTerm.trim().length > 0);
        if (!detailsVisible) {
            detailsVisible = true;
            $('.gw-detail-table-wrap').prop('hidden', false);
            $('#toggleDetailsBtn span').text('Hide Details');
        }
        applyView(true);
    });

    $('#pcReportingSearchClear').on('click', function() {
        $('#pcReportingSearch').val('').trigger('input').trigger('focus');
    });

    $('#pcReportingTableBody, #pcRecordsBody').on('click', '.gw-open-btn', function() {
        window.location.href = '/process-confirmation-config?edit=' + encodeURIComponent($(this).data('id'));
    });

    $('#anchorDateFilter').val(todayIso());
    loadMasterData();
    loadRecords();
    setInterval(loadRecords, 30000);
});
