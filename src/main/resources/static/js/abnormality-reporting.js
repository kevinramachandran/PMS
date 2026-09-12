$(function () {
    'use strict';

    const API = '/api/abnormality-reporting-config';
    const CHART_COLORS = ['#047857', '#2563eb', '#d97706', '#dc2626', '#7c3aed', '#0891b2', '#65a30d', '#be185d'];
    let records = [];
    let masterPlants = [];
    let masterDepartments = [];
    let masterAreas = [];
    let departmentLookup = new Map();
    let areaLookup = new Map();
    let searchTerm = '';
    let statusChart = null;
    let departmentChart = null;
    let detailsVisible = $('#toggleDetailsBtn').length === 0;

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (ch) {
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

    function isClosed(record) {
        return lower(record && record.tagStatus) === 'closed';
    }

    function todayIso() {
        const now = new Date();
        return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
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

    function filterAnchorParts() {
        const selected = $('#anchorDateFilter').val() || todayIso();
        return {
            date: selected,
            month: selected.slice(0, 7),
            year: selected.slice(0, 4)
        };
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
        const direct = normalize(record.plant || record.plantName || record.plantNameSuffix);
        if (direct) {
            return direct;
        }
        const area = areaForRecord(record) || areaLookup.get(lower(recordArea(record)));
        if (area && itemParentPlant(area)) {
            return itemParentPlant(area);
        }
        const department = departmentLookup.get(lower(recordDepartment(record)));
        return itemParentPlant(department);
    }

    function recordDepartment(record) {
        const direct = normalize(record.department);
        if (direct) {
            return direct;
        }
        const area = areaForRecord(record) || areaLookup.get(lower(recordArea(record)));
        return itemParentDepartment(area);
    }

    function recordArea(record) {
        return normalize(record.areaMachine);
    }

    function inPeriod(record) {
        const dateRaised = normalize(record && record.dateRaised);
        const period = $('#periodFilter').val() || 'ALL';
        const parts = filterAnchorParts();
        if (period === 'ALL') {
            return true;
        }
        if (period === 'YTD') {
            return dateRaised.slice(0, 4) === parts.year;
        }
        if (period === 'MTD') {
            return dateRaised.slice(0, 7) === parts.month;
        }
        return dateRaised === parts.date;
    }

    function matchesSelect(value, selected) {
        return !selected || selected === 'all' || lower(value) === lower(selected);
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
            if (!term) {
                return true;
            }
            return [
                record.typeOfTag,
                record.priority,
                record.tagRaisedBy,
                record.dateRaised,
                record.shift,
                record.department,
                record.areaMachine,
                record.component,
                record.description,
                record.proposedAction,
                record.pictureImage,
                record.abnormalityDefectType,
                record.assignTo,
                record.dateClosed,
                record.tagStatus
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
        $('#abnormalityReportingSyncStatus').text(text);
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
        $('#abnormalityFilterSummary').text(bits.join(' | ') + ' | ' + total + ' records');
    }

    function updateCounts(rows) {
        const closed = rows.filter(isClosed).length;
        const reported = rows.length;
        const open = reported - closed;
        const closedPercent = reported ? Math.round((closed / reported) * 100) : 0;
        $('#reportedCount').text(reported);
        $('#openCount').text(open);
        $('#closedCount').text(closed);
        $('#closedPercent').text(closedPercent + '%');
        $('.ar-count-card').removeClass('active')
            .filter(function() {
                return !$(this).hasClass('ar-percent-card')
                    && String($(this).data('status-filter') || 'all') === ($('#statusFilter').val() || 'all');
            }).addClass('active');
        updateFilterSummary(reported);
    }

    function destroyChart(instance) {
        if (instance) {
            instance.destroy();
        }
    }

    function renderStatusChart(rows) {
        const closed = rows.filter(isClosed).length;
        const open = rows.length - closed;
        const context = document.getElementById('abnormalityStatusChart') || document.getElementById('abnormalityReportingChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        destroyChart(statusChart);
        statusChart = new Chart(context, {
            type: 'bar',
            data: {
                labels: ['Total', 'Open', 'Closed'],
                datasets: [{
                    label: 'Abnormalities',
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
            const current = groups.get(key) || { label: key, total: 0, open: 0, closed: 0 };
            current.total += 1;
            if (isClosed(record)) {
                current.closed += 1;
            } else {
                current.open += 1;
            }
            groups.set(key, current);
        });
        return Array.from(groups.values()).sort(function(a, b) {
            return b.total - a.total || a.label.localeCompare(b.label);
        });
    }

    function renderDepartmentChart(rows) {
        const groups = groupCounts(rows, recordDepartment);
        const context = document.getElementById('abnormalityDepartmentChart');
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

    function summaryTable(groups) {
        if (!groups.length) {
            return '<div class="ar-summary-empty">No data for selected filters.</div>';
        }
        return '<table><thead><tr><th>Name</th><th>Total</th><th>Open</th><th>Closed</th><th>% Closed</th></tr></thead><tbody>' +
            groups.map(function(group) {
                const closedPercent = group.total ? Math.round((group.closed / group.total) * 100) : 0;
                return '<tr><td>' + escapeHtml(group.label) + '</td><td>' + group.total + '</td><td>' + group.open + '</td><td>' + group.closed + '</td><td>' + closedPercent + '%</td></tr>';
            }).join('') +
            '</tbody></table>';
    }

    function renderSummaryTables(rows) {
        $('#departmentSummaryTable').html(summaryTable(groupCounts(rows, recordDepartment)));
        $('#areaSummaryTable').html(summaryTable(groupCounts(rows, recordArea)));
    }

    function renderRows(rows) {
        const $body = $('#abnormalityReportingTableBody');
        if (!detailsVisible) {
            $body.empty();
            return;
        }
        if (!rows.length) {
            $body.html('<tr><td colspan="18" class="empty-row">No abnormality reports found.</td></tr>');
            return;
        }
        $body.html(rows.map(function(record, index) {
            return '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td>' + escapeHtml(record.typeOfTag) + '</td>' +
                '<td>' + escapeHtml(record.priority) + '</td>' +
                '<td>' + escapeHtml(record.tagRaisedBy) + '</td>' +
                '<td>' + escapeHtml(displayDate(record.dateRaised)) + '</td>' +
                '<td>' + escapeHtml(record.shift) + '</td>' +
                '<td>' + escapeHtml(record.department) + '</td>' +
                '<td>' + escapeHtml(record.areaMachine) + '</td>' +
                '<td>' + escapeHtml(record.component) + '</td>' +
                '<td>' + escapeHtml(record.description) + '</td>' +
                '<td>' + escapeHtml(record.proposedAction) + '</td>' +
                '<td>' + attachmentIcon('abnormality-reporting', record.pictureImage, record.pictureImage) + '</td>' +
                '<td>' + escapeHtml(record.abnormalityDefectType) + '</td>' +
                '<td>' + escapeHtml(record.assignTo) + '</td>' +
                '<td>' + escapeHtml(displayDate(record.dateClosed)) + '</td>' +
                '<td>' + escapeHtml(record.tagStatus) + '</td>' +
                '<td class="assignment-history-cell" data-record-id="' + escapeHtml(record.id) + '">Loading...</td>' +
                '<td><button type="button" class="ar-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open abnormality record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
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
        departmentLookup = new Map();
        areaLookup = new Map();
        masterDepartments.forEach(function(item) {
            const name = lower(item && item.name);
            if (name) {
                departmentLookup.set(name, item);
            }
        });
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
            masterAreas = Array.isArray(options.areaItems) ? options.areaItems : (options.areaMachines || []).map(function(name) { return { name: name }; });
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

    $('.ar-count-card').on('click', function() {
        $('#statusFilter').val($(this).data('status-filter') || 'all');
        applyView(true);
    });

    $('#toggleDetailsBtn').on('click', function() {
        detailsVisible = !detailsVisible;
        $('.ar-detail-table-wrap').prop('hidden', !detailsVisible);
        $('#toggleDetailsBtn span').text(detailsVisible ? 'Hide Details' : 'Show Details');
        renderRows(filteredRecords({}));
    });

    $('#abnormalityReportingSearch').on('input', function() {
        searchTerm = $(this).val() || '';
        $(this).closest('.issue-search').toggleClass('has-value', searchTerm.trim().length > 0);
        if (!detailsVisible) {
            detailsVisible = true;
            $('.ar-detail-table-wrap').prop('hidden', false);
            $('#toggleDetailsBtn span').text('Hide Details');
        }
        applyView(true);
    });

    $('#abnormalityReportingSearchClear').on('click', function() {
        $('#abnormalityReportingSearch').val('').trigger('input').trigger('focus');
    });

    $('#abnormalityReportingTableBody').on('click', '.ar-open-btn', function() {
        const id = $(this).data('id');
        window.location.href = '/abnormality-reporting-config?id=' + encodeURIComponent(id);
    });

    $('#anchorDateFilter').val(todayIso());
    loadMasterData();
    loadRecords();
    setInterval(loadRecords, 30000);
});
