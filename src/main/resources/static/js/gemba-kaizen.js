$(function() {
    'use strict';

    const API = '/api/gemba-kaizen-config';
    let records = [];
    let searchTerm = '';
    let chart = null;

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function(ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function isClosed(record) {
        return String(record && record.isKaizenImplemented || '').trim().toLowerCase() === 'yes';
    }

    function filteredRecords() {
        const status = $('#statusFilter').val() || 'all';
        const term = searchTerm.trim().toLowerCase();
        return records.filter(function(record) {
            if (status === 'closed' && !isClosed(record)) {
                return false;
            }
            if (!term) {
                return true;
            }
            return [
                record.name,
                record.id,
                record.lastModifiedTime,
                record.gembaKaizenProviderName,
                record.employeeIdHoNumber,
                record.department,
                record.classificationOfKaizen,
                record.gembaKaizenLocation,
                record.gembaKaizenGenerationDate,
                record.kaizenIdea,
                record.pictureImage,
                record.benefitsOfKaizen,
                record.isKaizenImplemented
            ].join(' ').toLowerCase().includes(term);
        });
    }

    function setSyncStatus(text) {
        $('#gembaKaizenSyncStatus').text(text);
    }

    function updateCounts() {
        $('#reportedCount').text(records.length);
        $('#closedCount').text(records.filter(isClosed).length);
        $('.gk-count-card').removeClass('active')
            .filter('[data-status-filter="' + ($('#statusFilter').val() || 'all') + '"]').addClass('active');
    }

    function renderChart() {
        const reported = records.length;
        const closed = records.filter(isClosed).length;
        const context = document.getElementById('gembaKaizenChart');
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
        const $body = $('#gembaKaizenTableBody');
        if (!rows.length) {
            $body.html('<tr><td colspan="14" class="empty-row">No Gemba Kaizen records found.</td></tr>');
            return;
        }
        $body.html(rows.map(function(record) {
            return '<tr>' +
                '<td>' + escapeHtml(record.id) + '</td>' +
                '<td>' + escapeHtml(record.name) + '</td>' +
                '<td>' + escapeHtml(record.lastModifiedTime) + '</td>' +
                '<td>' + escapeHtml(record.gembaKaizenProviderName) + '</td>' +
                '<td>' + escapeHtml(record.employeeIdHoNumber) + '</td>' +
                '<td>' + escapeHtml(record.department) + '</td>' +
                '<td>' + escapeHtml(record.classificationOfKaizen) + '</td>' +
                '<td>' + escapeHtml(record.gembaKaizenLocation) + '</td>' +
                '<td>' + escapeHtml(record.gembaKaizenGenerationDate) + '</td>' +
                '<td>' + escapeHtml(record.kaizenIdea) + '</td>' +
                '<td>' + escapeHtml(record.pictureImage) + '</td>' +
                '<td>' + escapeHtml(record.benefitsOfKaizen) + '</td>' +
                '<td>' + escapeHtml(record.isKaizenImplemented) + '</td>' +
                '<td><button type="button" class="gk-open-btn" data-id="' + escapeHtml(record.id) + '" title="Open record" aria-label="Open Gemba Kaizen record"><i class="fas fa-arrow-up-right-from-square"></i></button></td>' +
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

    $('.gk-count-card').on('click', function() {
        $('#statusFilter').val($(this).data('status-filter') || 'all');
        applyView();
    });

    $('#gembaKaizenSearch').on('input', function() {
        searchTerm = $(this).val() || '';
        $(this).closest('.issue-search').toggleClass('has-value', searchTerm.trim().length > 0);
        applyView();
    });

    $('#gembaKaizenSearchClear').on('click', function() {
        $('#gembaKaizenSearch').val('').trigger('input').trigger('focus');
    });

    $('#gembaKaizenTableBody').on('click', '.gk-open-btn', function() {
        window.location.href = '/gemba-kaizen-config?id=' + encodeURIComponent($(this).data('id'));
    });

    loadRecords();
    setInterval(loadRecords, 30000);
});
