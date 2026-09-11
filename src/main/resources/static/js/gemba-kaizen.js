$(function() {
    'use strict';

    const API = '/api/gemba-kaizen-config';
    const CHART_COLORS = ['#047857', '#2563eb', '#d97706', '#dc2626', '#7c3aed', '#0891b2', '#65a30d', '#be185d'];
    let records = [];
    let masterPlants = [];
    let masterDepartments = [];
    let masterAreas = [];
    let areaLookup = new Map();
    let searchTerm = '';
    let statusChart = null;
    let departmentChart = null;
    let detailsVisible = false;

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
        return normalize(record && record.gembaKaizenGenerationDate);
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
        return normalize(record && (record.gembaKaizenLocation || record.area || record.areaName));
    }

    function areaForRecord(record) {
        const areaName = recordArea(record);
        const candidates = masterAreas.filter(function(item) {
            return matchesText(itemName(item), areaName);
        });
        if (!candidates.length) {
            return null;
        }
        const directDepartment = normalize(record && record.department);
        if (directDepartment) {
            const departmentMatch = candidates.find(function(item) {
                return matchesText(itemParentDepartment(item), directDepartment);
            });
            if (departmentMatch) {
                return departmentMatch;
            }
        }
        return candidates[0];
    }

    function recordPlant(record) {
        const direct = normalize(record && (record.plant || record.plantName || record.plantNameSuffix));
        if (direct) {
            return direct;
        }
        return itemParentPlant(areaForRecord(record) || areaLookup.get(lower(recordArea(record))));
    }

    function recordDepartment(record) {
        const direct = normalize(record && record.department);
        if (direct) {
            return direct;
        }
        return itemParentDepartment(areaForRecord(record) || areaLookup.get(lower(recordArea(record))));
    }

    function isClosed(record) {
        return lower(record && record.isKaizenImplemented) === 'yes';
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

    function filteredRecords(options) {
        const ignoreSearch = options && options.ignoreSearch;
        const status = $('#statusFilter').val() || 'all';
        const plant = $('#plantFilter').val() || 'all';
        const department = $('#departmentFilter').val() || 'all';
        const area = $('#areaFilter').val() || 'all';
        const term = ignoreSearch ? '' : searchTerm.trim().toLowerCase();
        return records.filter(function(record) {
            if (!inPeriod(record) || !matchesSelect(recordPlant(record), plant)
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
            if (!term) {
                return true;
            }
            return [
                record.id, record.lastModifiedTime, recordDepartment(record), record.classificationOfKaizen,
                recordArea(record), recordDate(record), record.kaizenIdea, record.pictureImage,
                record.benefitsOfKaizen, statusLabel(record), record.assignedTo
            ].join(' ').toLowerCase().includes(term);
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
        const matchingValue = values.find(function(value) { return lower(value) === lower(selected); });
        $(selector).html('<option value="all">' + escapeHtml(allLabel || 'All') + '</option>' + values.map(optionHtml).join(''));
        $(selector).val(matchingValue || 'all');
    }

    function updateFilterOptions() {
        const periodRows = records.filter(inPeriod);
        populateFilter('#plantFilter', uniqueList(uniqueValues(recordPlant, periodRows).concat(masterPlants.map(itemName))), $('#plantFilter').val(), 'All');
        const plant = $('#plantFilter').val();
        const plantRows = periodRows.filter(function(record) { return matchesSelect(recordPlant(record), plant); });
        const departmentMasterValues = masterDepartments
            .filter(function(item) { return matchesSelect(itemParentPlant(item), plant); })
            .map(itemName);
        populateFilter('#departmentFilter', uniqueList(uniqueValues(recordDepartment, plantRows).concat(departmentMasterValues)), $('#departmentFilter').val(), 'All');
        const department = $('#departmentFilter').val();
        const departmentRows = plantRows.filter(function(record) { return matchesSelect(recordDepartment(record), department); });
        const areaMasterValues = masterAreas
            .filter(function(item) { return matchesSelect(itemParentPlant(item), plant) && matchesSelect(itemParentDepartment(item), department); })
            .map(itemName);
        populateFilter('#areaFilter', uniqueList(uniqueValues(recordArea, departmentRows).concat(areaMasterValues)), $('#areaFilter').val(), 'All');
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
        $('#gembaKaizenFilterSummary').text(bits.join(' | ') + ' | ' + total + ' records');
    }

    function updateCounts(rows) {
        const closed = rows.filter(isClosed).length;
        const total = rows.length;
        const open = total - closed;
        $('#reportedCount').text(total);
        $('#openCount').text(open);
        $('#closedCount').text(closed);
        $('#closedPercent').text((total ? Math.round((closed / total) * 100) : 0) + '%');
        $('.gk-count-card').removeClass('active')
            .filter(function() {
                return !$(this).hasClass('gk-percent-card')
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
        const context = document.getElementById('gembaKaizenStatusChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        destroyChart(statusChart);
        statusChart = new Chart(context, {
            type: 'bar',
            data: {
                labels: ['Total', 'Open', 'Closed'],
                datasets: [{ label: 'Gemba Kaizens', data: [rows.length, open, closed], backgroundColor: ['#0f766e', '#d97706', '#16a34a'], borderRadius: 6 }]
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
        return Array.from(groups.values()).sort(function(a, b) { return b.total - a.total || a.label.localeCompare(b.label); });
    }

    function renderDepartmentChart(rows) {
        const groups = groupCounts(rows, recordDepartment);
        const context = document.getElementById('gembaKaizenDepartmentChart');
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
            return '<div class="gk-summary-empty">No data for selected filters.</div>';
        }
        return '<table><thead><tr><th>' + escapeHtml(firstColumnLabel) + '</th><th>Open</th><th>Closed</th><th>% Closed</th><th>Average Days Open</th></tr></thead><tbody>' +
            groups.map(function(group) {
                const closedPercent = group.total ? Math.round((group.closed / group.total) * 100) : 0;
                const averageDaysOpen = group.open ? Math.round(group.daysOpenTotal / group.open) : 0;
                return '<tr><td>' + escapeHtml(group.label) + '</td><td>' + group.open + '</td><td>' + group.closed + '</td><td>' + closedPercent + '%</td><td>' + averageDaysOpen + '</td></tr>';
            }).join('') + '</tbody></table>';
    }

    function renderRows(rows) {
        const $body = $('#gembaKaizenTableBody');
        if (!detailsVisible) {
            $body.empty();
            return;
        }
        if (!rows.length) {
            $body.html('<tr><td colspan="13" class="empty-row">No Gemba Kaizen records found.</td></tr>');
            return;
        }
        $body.html(rows.map(function(record) {
            return '<tr>' +
                '<td>' + escapeHtml(record.id) + '</td>' +
                '<td>' + escapeHtml(record.lastModifiedTime) + '</td>' +
                '<td>' + escapeHtml(recordDepartment(record)) + '</td>' +
                '<td>' + escapeHtml(record.classificationOfKaizen) + '</td>' +
                '<td>' + escapeHtml(recordArea(record)) + '</td>' +
                '<td>' + escapeHtml(displayDate(recordDate(record))) + '</td>' +
                '<td>' + escapeHtml(record.kaizenIdea) + '</td>' +
                '<td>' + attachmentIcon('gemba-kaizen', record.pictureImage, record.pictureImage) + '</td>' +
                '<td>' + escapeHtml(record.benefitsOfKaizen) + '</td>' +
                '<td>' + escapeHtml(statusLabel(record)) + '</td>' +
                '<td>' + escapeHtml(record.assignedTo) + '</td>' +
                '<td class="assignment-history-cell" data-record-id="' + escapeHtml(record.id) + '">Loading...</td>' +
                '<td><button type="button" class="gk-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open Gemba Kaizen record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
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
        $('#departmentSummaryTable').html(summaryTable(groupCounts(rows, recordDepartment), 'Department'));
        $('#areaSummaryTable').html(summaryTable(groupCounts(rows, recordArea), 'Area'));
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
        $('#gembaKaizenSyncStatus').text('Syncing...');
        $.ajax({
            url: API + '/records',
            type: 'GET',
            success: function(data) {
                records = data && Array.isArray(data.records) ? data.records : [];
                applyView();
                $('#gembaKaizenSyncStatus').text('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                records = [];
                applyView();
                $('#gembaKaizenSyncStatus').text('Sync failed');
            }
        });
    }

    $('#periodFilter, #anchorDateFilter, #plantFilter, #departmentFilter').on('change', function() {
        applyView();
    });

    $('#areaFilter, #statusFilter').on('change', function() {
        applyView(true);
    });

    $('.gk-count-card').on('click', function() {
        if ($(this).hasClass('gk-percent-card')) {
            return;
        }
        $('#statusFilter').val($(this).data('status-filter') || 'all');
        applyView(true);
    });

    $('#toggleDetailsBtn').on('click', function() {
        detailsVisible = !detailsVisible;
        $('.gk-detail-table-wrap').prop('hidden', !detailsVisible);
        $('#toggleDetailsBtn span').text(detailsVisible ? 'Hide Details' : 'Show Details');
        renderRows(filteredRecords({}));
    });

    $('#gembaKaizenSearch').on('input', function() {
        searchTerm = $(this).val() || '';
        $(this).closest('.issue-search').toggleClass('has-value', searchTerm.trim().length > 0);
        if (!detailsVisible) {
            detailsVisible = true;
            $('.gk-detail-table-wrap').prop('hidden', false);
            $('#toggleDetailsBtn span').text('Hide Details');
        }
        applyView(true);
    });

    $('#gembaKaizenSearchClear').on('click', function() {
        $('#gembaKaizenSearch').val('').trigger('input').trigger('focus');
    });

    $('#gembaKaizenTableBody').on('click', '.gk-open-btn', function() {
        window.location.href = '/gemba-kaizen-config?id=' + encodeURIComponent($(this).data('id'));
    });

    $('#anchorDateFilter').val(todayIso());
    loadMasterData();
    loadRecords();
    setInterval(loadRecords, 30000);
});
