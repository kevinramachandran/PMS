$(function() {
    'use strict';

    const API = '/api/gemba-walk-config';
    let records = [];
    let searchTerm = '';
    let chart = null;

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function(ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function isClosed(record) {
        const observations = record && Array.isArray(record.observations) ? record.observations : [];
        return observations.length > 0 && observations.every(function(observation) {
            return String(observation.status || '').trim().toLowerCase() === 'closed';
        });
    }

    function statusLabel(record) {
        return isClosed(record) ? 'Closed' : 'Open';
    }

    function observationsSummary(record) {
        const observations = record && Array.isArray(record.observations) ? record.observations : [];
        return observations.map(function(observation, index) {
            return 'Observation ' + (index + 1) + ': ' + [
                observation.observationDescription,
                observation.gembaCategory,
                observation.lifeSaverRule,
                observation.status,
                observation.pictureImage ? 'Picture: ' + observation.pictureImage : ''
            ].filter(Boolean).join(' | ');
        }).join(' / ');
    }

    function filteredRecords() {
        const status = $('#statusFilter').val() || 'all';
        const term = searchTerm.trim().toLowerCase();
        return records.filter(function(record) {
            if (status === 'closed' && !isClosed(record)) {
                return false;
            }
            if (term) {
                return [
                    record.id,
                    record.startTime,
                    record.completionTime,
                    record.email,
                    record.managerName,
                    record.dateOfLeadershipSafetyWalkConducted,
                    record.managementSafetyWalkWeek,
                    record.locationOfMswConducted,
                    record.responsibility,
                    observationsSummary(record),
                    record.finalComments,
                    statusLabel(record)
                ].join(' ').toLowerCase().includes(term);
            }
            return true;
        });
    }

    function setSyncStatus(text) {
        $('#gembaReportingSyncStatus').text(text);
    }

    function updateCounts() {
        const reported = records.length;
        const closed = records.filter(isClosed).length;
        $('#reportedCount').text(reported);
        $('#closedCount').text(closed);
        $('.gw-count-card').removeClass('active')
            .filter('[data-status-filter="' + ($('#statusFilter').val() || 'all') + '"]').addClass('active');
    }

    function renderChart() {
        const reported = records.length;
        const closed = records.filter(isClosed).length;
        const context = document.getElementById('gembaReportingChart');
        if (!context || typeof Chart === 'undefined') {
            return;
        }
        if (chart) {
            chart.destroy();
        }
        chart = new Chart(context, {
            type: 'bar',
            data: {
                labels: ['Reported', 'Closed'],
                datasets: [{
                    data: [reported, closed],
                    backgroundColor: ['#047434', '#16a34a'],
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
        const rows = filteredRecords();
        const $body = $('#gembaReportingTableBody');
        if (!rows.length) {
            $body.html('<tr><td colspan="13" class="empty-row">No Gemba Walk records found.</td></tr>');
            return;
        }
        $body.html(rows.map(function(record) {
            return '<tr>' +
                '<td>' + escapeHtml(record.id) + '</td>' +
                '<td>' + escapeHtml(record.startTime) + '</td>' +
                '<td>' + escapeHtml(record.completionTime) + '</td>' +
                '<td>' + escapeHtml(record.email) + '</td>' +
                '<td>' + escapeHtml(record.managerName) + '</td>' +
                '<td>' + escapeHtml(record.dateOfLeadershipSafetyWalkConducted) + '</td>' +
                '<td>' + escapeHtml(record.managementSafetyWalkWeek) + '</td>' +
                '<td>' + escapeHtml(record.locationOfMswConducted) + '</td>' +
                '<td>' + escapeHtml(record.responsibility) + '</td>' +
                '<td>' + escapeHtml(observationsSummary(record)) + '</td>' +
                '<td>' + escapeHtml(statusLabel(record)) + '</td>' +
                '<td>' + escapeHtml(record.finalComments) + '</td>' +
                '<td><button type="button" class="gw-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open Gemba Walk record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
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

    $('#hamburger').on('click', function() {
        if (window.innerWidth <= 768) {
            $('#sidebar').toggleClass('active');
            $('#sidebarOverlay').toggleClass('active');
        } else {
            $('#sidebar').toggleClass('collapsed');
            $('.main-content').toggleClass('expanded');
            localStorage.setItem('sidebarCollapsed', $('#sidebar').hasClass('collapsed'));
        }
    });

    $('#sidebarOverlay').on('click', function() {
        $('#sidebar').removeClass('active');
        $('#sidebarOverlay').removeClass('active');
    });

    if (localStorage.getItem('sidebarCollapsed') === 'true' && window.innerWidth > 768) {
        $('#sidebar').addClass('collapsed');
        $('.main-content').addClass('expanded');
    }

    $('#statusFilter').on('change', applyView);

    $('.gw-count-card').on('click', function() {
        $('#statusFilter').val($(this).data('status-filter') || 'all');
        applyView();
    });

    $('#gembaReportingSearch').on('input', function() {
        searchTerm = $(this).val() || '';
        $(this).closest('.issue-search').toggleClass('has-value', searchTerm.trim().length > 0);
        applyView();
    });

    $('#gembaReportingSearchClear').on('click', function() {
        $('#gembaReportingSearch').val('').trigger('input').trigger('focus');
    });

    $('#gembaReportingTableBody').on('click', '.gw-open-btn', function() {
        window.location.href = '/gemba-walk-config?id=' + encodeURIComponent($(this).data('id'));
    });

    loadRecords();
    setInterval(loadRecords, 30000);
});
