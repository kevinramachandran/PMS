$(function() {
    'use strict';

    const API = '/api/gemba-walk-config';
    const CHART_COLORS = ['#047857', '#2563eb', '#d97706', '#dc2626', '#7c3aed', '#0891b2', '#65a30d', '#be185d'];
    let records = [];
    let masterPlants = [];
    let masterDepartments = [];
    let masterAreas = [];
    let areaLookup = new Map();
    let searchTerm = '';
    let statusChart = null;
    let departmentChart = null;
    let detailsVisible = true;

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
        return {
            date: selected,
            month: selected.slice(0, 7),
            year: selected.slice(0, 4)
        };
    }

    function recordDate(record) {
        return normalize(record && record.dateOfLeadershipSafetyWalkConducted);
    }

    function displayDate(value) {
        const text = normalize(value);
        const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
        if (iso) {
            return iso[3] + '/' + iso[2] + '/' + iso[1];
        }
        const dashed = text.match(/^(\d{2})-(\d{2})-(\d{4})$/);
        if (dashed) {
            return dashed[1] + '/' + dashed[2] + '/' + dashed[3];
        }
        return text;
    }

    function conductedBy(record) {
        return normalize(record && (record.managerName || record.createdBy));
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

    function areaCandidates(record) {
        const areaName = recordArea(record);
        if (!areaName) {
            return [];
        }
        return masterAreas.filter(function(item) {
            return matchesText(itemName(item), areaName);
        });
    }

    function areaForRecord(record) {
        const candidates = areaCandidates(record);
        if (!candidates.length) {
            return null;
        }
        const directPlant = normalize(record && (record.plant || record.plantName || record.plantNameSuffix));
        if (directPlant) {
            const plantMatch = candidates.find(function(item) {
                return matchesText(itemParentPlant(item), directPlant);
            });
            if (plantMatch) {
                return plantMatch;
            }
        }
        return candidates[0];
    }

    function recordPlant(record) {
        const direct = normalize(record.plant || record.plantName || record.plantNameSuffix);
        if (direct) {
            return direct;
        }
        const area = areaForRecord(record) || areaLookup.get(lower(recordArea(record)));
        return itemParentPlant(area);
    }

    function recordDepartment(record) {
        const direct = normalize(record.department || record.departmentName);
        if (direct) {
            return direct;
        }
        const area = areaForRecord(record) || areaLookup.get(lower(recordArea(record)));
        return itemParentDepartment(area);
    }

    function recordArea(record) {
        return normalize(record.locationOfMswConducted || record.area || record.areaName);
    }

    function observations(record) {
        return record && Array.isArray(record.observations) ? record.observations : [];
    }

    function isObservationClosed(observation) {
        return lower(observation && observation.status) === 'closed';
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
        if (Number.isNaN(raised.getTime())) {
            return 0;
        }
        const anchor = new Date(($('#anchorDateFilter').val() || todayIso()) + 'T00:00:00');
        const diff = anchor.getTime() - raised.getTime();
        return Math.max(0, Math.floor(diff / 86400000));
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
                observation.observationDescription,
                observation.gembaCategory,
                observation.lifeSaverRule,
                observation.status,
                observation.pictureImage ? 'Picture: ' + observation.pictureImage : ''
            ].filter(Boolean).join(' | ');
        }).join(' / ');
    }

    function observationsSummaryWithAttachments(record) {
        return observations(record).map(function(observation, index) {
            return escapeHtml('Observation ' + (index + 1) + ': ' + [
                observation.observationDescription,
                observation.gembaCategory,
                observation.lifeSaverRule,
                observation.status
            ].filter(Boolean).join(' | ')) + ' ' + attachmentIcon('gemba-walk', observation.pictureImage, observation.pictureImage);
        }).join(' / ');
    }

    function filteredRecords(options) {
        const ignoreStatus = options && options.ignoreStatus;
        const ignoreSearch = options && options.ignoreSearch;
        const status = ignoreStatus ? 'all' : ($('#statusFilter').val() || 'all');
        const plant = $('#plantFilter').val() || 'all';
        const department = $('#departmentFilter').val() || 'all';
        const area = $('#areaFilter').val() || 'all';
        const term = ignoreSearch ? '' : searchTerm.trim().toLowerCase();

        return records.filter(function(record) {
            if (!inPeriod(record)) {
                return false;
            }
            if (!matchesSelect(recordPlant(record), plant)) {
                return false;
            }
            if (!matchesSelect(recordDepartment(record), department)) {
                return false;
            }
            if (!matchesSelect(recordArea(record), area)) {
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
                    record.startTime,
                    record.completionTime,
                    recordDepartment(record),
                    record.email,
                    conductedBy(record),
                    recordDate(record),
                    record.managementSafetyWalkWeek,
                    recordArea(record),
                    record.responsibility,
                    observationsSummary(record),
                    record.finalComments,
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
        $(selector).html(html);
        $(selector).val(matchingValue || 'all');
    }

    function updateFilterOptions() {
        const periodRows = records.filter(inPeriod);
        const plantValues = uniqueList(uniqueValues(recordPlant, periodRows).concat(masterPlants.map(function(item) {
            return normalize(item.name);
        })));
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
        $('#gembaReportingSyncStatus').text(text);
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
        $('#gembaFilterSummary').text(bits.join(' | ') + ' | ' + total + ' records');
    }

    function updateCounts(rows) {
        const closed = rows.filter(isClosed).length;
        const total = rows.length;
        const open = total - closed;
        const closedPercent = total ? Math.round((closed / total) * 100) : 0;
        $('#reportedCount').text(total);
        $('#openCount').text(open);
        $('#closedCount').text(closed);
        $('#closedPercent').text(closedPercent + '%');
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
        const context = document.getElementById('gembaStatusChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        destroyChart(statusChart);
        statusChart = new Chart(context, {
            type: 'bar',
            data: {
                labels: ['Total', 'Open', 'Closed'],
                datasets: [{
                    label: 'Gemba Walks',
                    data: [rows.length, open, closed],
                    backgroundColor: ['#0f766e', '#d97706', '#16a34a'],
                    borderRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
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
        const context = document.getElementById('gembaDepartmentChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        destroyChart(departmentChart);
        departmentChart = new Chart(context, {
            type: 'pie',
            data: {
                labels: groups.map(function(group) { return group.label; }),
                datasets: [{
                    data: groups.map(function(group) { return group.total; }),
                    backgroundColor: groups.map(function(_, index) { return CHART_COLORS[index % CHART_COLORS.length]; }),
                    borderColor: '#ffffff',
                    borderWidth: 2
                }]
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
                                    const percent = Math.round((value / total) * 100);
                                    return {
                                        text: label + ': ' + value + ' (' + percent + '%)',
                                        fillStyle: chart.data.datasets[0].backgroundColor[index],
                                        strokeStyle: '#ffffff',
                                        lineWidth: 2,
                                        hidden: false,
                                        index: index
                                    };
                                });
                            }
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const total = rows.length || 1;
                                const value = Number(context.parsed || 0);
                                const percent = Math.round((value / total) * 100);
                                return context.label + ': ' + value + ' (' + percent + '%)';
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
        const $body = $('#gembaReportingTableBody');
        if (!detailsVisible) {
            $body.empty();
            return;
        }
        if (!rows.length) {
            $body.html('<tr><td colspan="13" class="empty-row">No Gemba Walk records found.</td></tr>');
            return;
        }
        $body.html(rows.map(function(record, index) {
            return '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td>' + escapeHtml(recordDepartment(record)) + '</td>' +
                '<td>' + escapeHtml(recordArea(record)) + '</td>' +
                '<td>' + escapeHtml(conductedBy(record)) + '</td>' +
                '<td>' + escapeHtml(record.email) + '</td>' +
                '<td>' + escapeHtml(displayDate(recordDate(record))) + '</td>' +
                '<td>' + escapeHtml(record.managementSafetyWalkWeek) + '</td>' +
                '<td><span class="gw-status-pill">' + observations(record).length + '</span></td>' +
                '<td>' + escapeHtml(statusLabel(record)) + '</td>' +
                '<td>' + escapeHtml(record.responsibility) + '</td>' +
                '<td>' + escapeHtml(record.finalComments) + '</td>' +
                '<td class="assignment-history-cell" data-record-id="' + escapeHtml(record.id) + '">Loading...</td>' +
                '<td><button type="button" class="gw-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open Gemba Walk record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
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
        const rows = filteredRecords({});
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
            masterAreas = Array.isArray(options.areaItems) ? options.areaItems : (options.processAreas || []).map(function(name) { return { name: name }; });
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
        renderRows(filteredRecords({}));
    });

    $('#gembaReportingSearch').on('input', function() {
        searchTerm = $(this).val() || '';
        $(this).closest('.issue-search').toggleClass('has-value', searchTerm.trim().length > 0);
        if (!detailsVisible) {
            detailsVisible = true;
            $('.gw-detail-table-wrap').prop('hidden', false);
            $('#toggleDetailsBtn span').text('Hide Details');
        }
        applyView(true);
    });

    $('#gembaReportingSearchClear').on('click', function() {
        $('#gembaReportingSearch').val('').trigger('input').trigger('focus');
    });

    $('#gembaReportingTableBody').on('click', '.gw-open-btn', function() {
        window.location.href = '/gemba-walk-config?id=' + encodeURIComponent($(this).data('id'));
    });

    $('#anchorDateFilter').val(todayIso());
    loadMasterData();
    loadRecords();
    setInterval(loadRecords, 30000);
});
