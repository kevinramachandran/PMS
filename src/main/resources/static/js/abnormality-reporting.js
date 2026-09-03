$(function () {
    'use strict';

    const API = '/api/abnormality-reporting-config';
    let records = [];
    let searchTerm = '';
    let chart = null;

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function isClosed(record) {
        return String(record && record.tagStatus || '').trim().toLowerCase() === 'closed';
    }

    function todayParts() {
        const now = new Date();
        const year = String(now.getFullYear());
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        return {
            date: year + '-' + month + '-' + day,
            month: year + '-' + month,
            year: year
        };
    }

    function inPeriod(record) {
        const dateRaised = String(record && record.dateRaised || '');
        const parts = todayParts();
        const period = $('#periodFilter').val() || 'ALL';
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

    function filteredRecords(ignoreStatus) {
        const status = ignoreStatus ? 'all' : ($('#statusFilter').val() || 'all');
        const term = searchTerm.trim().toLowerCase();
        return records.filter(function(record) {
            if (!inPeriod(record)) {
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
                record.abnormalityTagNumber,
                record.tagRaisedBy,
                record.dateRaised,
                record.shift,
                record.abnormalityRelatedTo,
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

    function setSyncStatus(text) {
        $('#abnormalityReportingSyncStatus').text(text);
    }

    function updateCounts() {
        const periodRows = filteredRecords(true);
        const closed = periodRows.filter(isClosed).length;
        const reported = periodRows.length;
        $('#reportedCount').text(reported);
        $('#openCount').text(reported - closed);
        $('#closedCount').text(closed);
        $('.ar-count-card').removeClass('active')
            .filter('[data-status-filter="' + ($('#statusFilter').val() || 'all') + '"]').addClass('active');
    }

    function renderChart() {
        const periodRows = filteredRecords(true);
        const closed = periodRows.filter(isClosed).length;
        const reported = periodRows.length;
        const open = reported - closed;
        const context = document.getElementById('abnormalityReportingChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        if (chart) {
            chart.destroy();
        }
        chart = new Chart(context, {
            type: 'bar',
            data: {
                labels: ['Reported', 'Open', 'Closed'],
                datasets: [{
                    data: [reported, open, closed],
                    backgroundColor: ['#047434', '#f59e0b', '#16a34a'],
                    borderRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: { precision: 0 }
                    }
                }
            }
        });
    }

    function renderRows() {
        const rows = filteredRecords(false);
        const $body = $('#abnormalityReportingTableBody');
        if (!rows.length) {
            $body.html('<tr><td colspan="19" class="empty-row">No abnormality reports found.</td></tr>');
            return;
        }
        $body.html(rows.map(function(record, index) {
            return '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td>' + escapeHtml(record.typeOfTag) + '</td>' +
                '<td>' + escapeHtml(record.priority) + '</td>' +
                '<td>' + escapeHtml(record.abnormalityTagNumber) + '</td>' +
                '<td>' + escapeHtml(record.tagRaisedBy) + '</td>' +
                '<td>' + escapeHtml(record.dateRaised) + '</td>' +
                '<td>' + escapeHtml(record.shift) + '</td>' +
                '<td>' + escapeHtml(record.abnormalityRelatedTo) + '</td>' +
                '<td>' + escapeHtml(record.department) + '</td>' +
                '<td>' + escapeHtml(record.areaMachine) + '</td>' +
                '<td>' + escapeHtml(record.component) + '</td>' +
                '<td>' + escapeHtml(record.description) + '</td>' +
                '<td>' + escapeHtml(record.proposedAction) + '</td>' +
                '<td>' + escapeHtml(record.pictureImage) + '</td>' +
                '<td>' + escapeHtml(record.abnormalityDefectType) + '</td>' +
                '<td>' + escapeHtml(record.assignTo) + '</td>' +
                '<td>' + escapeHtml(record.dateClosed) + '</td>' +
                '<td>' + escapeHtml(record.tagStatus) + '</td>' +
                '<td><button type="button" class="ar-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open abnormality record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
            '</tr>';
        }).join(''));
    }

    function applyView() {
        updateCounts();
        renderChart();
        renderRows();
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

    $('#periodFilter, #statusFilter').on('change', applyView);

    $('.ar-count-card').on('click', function() {
        $('#statusFilter').val($(this).data('status-filter') || 'all');
        applyView();
    });

    $('#abnormalityReportingSearch').on('input', function() {
        searchTerm = $(this).val() || '';
        $(this).closest('.issue-search').toggleClass('has-value', searchTerm.trim().length > 0);
        applyView();
    });

    $('#abnormalityReportingSearchClear').on('click', function() {
        $('#abnormalityReportingSearch').val('').trigger('input').trigger('focus');
    });

    $('#abnormalityReportingTableBody').on('click', '.ar-open-btn', function() {
        const id = $(this).data('id');
        window.location.href = '/abnormality-reporting-config?id=' + encodeURIComponent(id);
    });

    loadRecords();
    setInterval(loadRecords, 30000);
});
