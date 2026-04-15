$(function () {
    'use strict';

    const TARGET_CLOSURE = 80;
    let tagCountChartInstance = null;
    let closureChartInstance = null;

    // -------------------------------------------------------
    // Sidebar toggle: desktop collapse + mobile overlay drawer
    // -------------------------------------------------------
    const $sidebar = $('#sidebar');
    const $mainContent = $('.main-content');
    const $overlay = $('#sidebarOverlay');
    const MOBILE_BREAKPOINT = 900;

    function isMobileViewport() {
        return window.innerWidth <= MOBILE_BREAKPOINT;
    }

    function setDesktopSidebarState(collapsed) {
        if (collapsed) {
            $sidebar.addClass('collapsed');
            $mainContent.addClass('expanded');
        } else {
            $sidebar.removeClass('collapsed');
            $mainContent.removeClass('expanded');
        }
    }

    function applySavedSidebarState() {
        const saved = localStorage.getItem('sidebarCollapsed') === 'true';
        if (!isMobileViewport()) {
            setDesktopSidebarState(saved);
            $sidebar.removeClass('open');
            $overlay.removeClass('active');
        } else {
            $sidebar.removeClass('collapsed');
            $mainContent.removeClass('expanded');
        }
    }

    $('#sidebarToggle').on('click', function () {
        if (isMobileViewport()) {
            $sidebar.toggleClass('open');
            $overlay.toggleClass('active');
            return;
        }

        const collapsed = !$sidebar.hasClass('collapsed');
        setDesktopSidebarState(collapsed);
        localStorage.setItem('sidebarCollapsed', collapsed ? 'true' : 'false');
    });

    $overlay.on('click', function () {
        $sidebar.removeClass('open');
        $overlay.removeClass('active');
    });

    $(window).on('resize', function () {
        applySavedSidebarState();
    });

    // Expand parent nav if child is active
    const $activeChild = $('.nav-child.active');
    if ($activeChild.length) {
        const $parent = $activeChild.closest('.nav-parent');
        $parent.find('.nav-parent-toggle').addClass('expanded');
        $parent.find('.nav-children').addClass('show').show();
    }

    $('.nav-parent-toggle').on('click', function (e) {
        e.preventDefault();
        const $parent = $(this).closest('.nav-parent');
        $(this).addClass('expanded');
        $parent.find('.nav-children').addClass('show').show();
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

    // -------------------------------------------------------
    // Load period filter then render charts
    // -------------------------------------------------------
    function loadPeriodsAndRender() {
        $.ajax({
            url: '/api/abnormality-tracker/periods',
            type: 'GET',
            success: function (periods) {
                const $select = $('#atPeriodFilter');
                $select.empty();

                if (!periods || periods.length === 0) {
                    $select.append('<option value="">No data configured</option>');
                    renderEmpty();
                    return;
                }

                periods.forEach(function (p) {
                    $select.append('<option value="' + escAttr(p) + '">' + escHtml(p) + '</option>');
                });

                // Load latest (first in list)
                loadDataForPeriod(periods[0]);
            },
            error: function () {
                renderEmpty();
            }
        });
    }

    $('#atPeriodFilter').on('change', function () {
        const selected = $(this).val();
        if (selected) {
            loadDataForPeriod(selected);
        }
    });

    function loadDataForPeriod(periodLabel) {
        $.ajax({
            url: '/api/abnormality-tracker/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function (data) {
                if (!data || data.length === 0) {
                    renderEmpty();
                    return;
                }
                renderDashboard(data);
                updateSyncStatus();
            },
            error: function () {
                renderEmpty();
            }
        });
    }

    // -------------------------------------------------------
    // Render charts + table
    // -------------------------------------------------------
    function renderDashboard(entries) {
        const labels = entries.map(function (e) { return e.department || ''; });
        const yellowData = entries.map(function (e) { return e.yellowTags || 0; });
        const redData = entries.map(function (e) { return e.redTags || 0; });
        const closureData = entries.map(function (e) { return e.closurePercent || 0; });

        renderTagCountChart(labels, yellowData, redData);
        renderClosureChart(labels, closureData);
        renderSummaryTable(entries);
    }

    function renderTagCountChart(labels, yellowData, redData) {
        if (tagCountChartInstance) {
            tagCountChartInstance.destroy();
        }

        const ctx = document.getElementById('tagCountChart').getContext('2d');
        tagCountChartInstance = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Yellow',
                        data: yellowData,
                        backgroundColor: '#FFD700',
                        borderColor: '#e6c200',
                        borderWidth: 1,
                        borderRadius: 3
                    },
                    {
                        label: 'Red',
                        data: redData,
                        backgroundColor: '#e53e3e',
                        borderColor: '#c53030',
                        borderWidth: 1,
                        borderRadius: 3
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                return ctx.dataset.label + ': ' + ctx.parsed.y;
                            }
                        }
                    },
                    datalabels: false
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { font: { size: 12 }, color: '#374151' }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: '#f3f4f6' },
                        ticks: {
                            stepSize: 10,
                            font: { size: 12 },
                            color: '#374151'
                        }
                    }
                }
            }
        });
    }

    function renderClosureChart(labels, closureData) {
        if (closureChartInstance) {
            closureChartInstance.destroy();
        }

        // Color each bar: green if >= target, red if < target
        const barColors = closureData.map(function (v) {
            return v >= TARGET_CLOSURE ? '#22c55e' : '#e53e3e';
        });
        const borderColors = closureData.map(function (v) {
            return v >= TARGET_CLOSURE ? '#15803d' : '#b91c1c';
        });

        const ctx = document.getElementById('closureChart').getContext('2d');
        closureChartInstance = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Closure %',
                        data: closureData,
                        backgroundColor: barColors,
                        borderColor: borderColors,
                        borderWidth: 1,
                        borderRadius: 3
                    },
                    {
                        // Target line rendered as a transparent bar with a line annotation via dataset
                        label: 'Target (80%)',
                        data: labels.map(function () { return TARGET_CLOSURE; }),
                        type: 'line',
                        borderColor: '#3b82f6',
                        borderWidth: 2.5,
                        pointRadius: 0,
                        fill: false,
                        tension: 0,
                        backgroundColor: 'transparent',
                        order: 0
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                if (ctx.dataset.type === 'line') {
                                    return 'Target: ' + TARGET_CLOSURE + '%';
                                }
                                return 'Closure: ' + ctx.parsed.y + '%';
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { font: { size: 12 }, color: '#374151' }
                    },
                    y: {
                        beginAtZero: true,
                        max: 120,
                        grid: { color: '#f3f4f6' },
                        ticks: {
                            stepSize: 20,
                            font: { size: 12 },
                            color: '#374151',
                            callback: function (val) { return val + '%'; }
                        }
                    }
                }
            }
        });
    }

    function renderSummaryTable(entries) {
        const tbody = $('#atSummaryBody');
        tbody.empty();

        if (!entries || entries.length === 0) {
            tbody.html('<tr><td colspan="6" class="at-empty">No data available.</td></tr>');
            return;
        }

        entries.forEach(function (e) {
            const yellowTags = e.yellowTags || 0;
            const redTags = e.redTags || 0;
            const total = yellowTags + redTags;
            const closure = (e.closurePercent !== null && e.closurePercent !== undefined)
                ? parseFloat(e.closurePercent).toFixed(1) + '%'
                : '-';
            const isGreen = (e.closurePercent || 0) >= TARGET_CLOSURE;
            const statusHtml = isGreen
                ? '<span class="at-status-green">On Target</span>'
                : '<span class="at-status-red">Below Target</span>';

            tbody.append(
                '<tr>' +
                '<td>' + escHtml(e.department || '-') + '</td>' +
                '<td style="text-align:center;">' + yellowTags + '</td>' +
                '<td style="text-align:center;">' + redTags + '</td>' +
                '<td style="text-align:center;font-weight:700;">' + total + '</td>' +
                '<td style="text-align:center;">' + closure + '</td>' +
                '<td>' + statusHtml + '</td>' +
                '</tr>'
            );
        });
    }

    function renderEmpty() {
        if (tagCountChartInstance) { tagCountChartInstance.destroy(); tagCountChartInstance = null; }
        if (closureChartInstance) { closureChartInstance.destroy(); closureChartInstance = null; }

        const emptyMsg = '<tr><td colspan="6" class="at-empty">No data available. Configure in Abnormality Tracker Config.</td></tr>';
        $('#atSummaryBody').html(emptyMsg);

        ['tagCountChart', 'closureChart'].forEach(function (id) {
            const canvas = document.getElementById(id);
            if (canvas) {
                const ctx = canvas.getContext('2d');
                ctx.clearRect(0, 0, canvas.width, canvas.height);
                ctx.save();
                ctx.textAlign = 'center';
                ctx.fillStyle = '#9ca3af';
                ctx.font = '14px sans-serif';
                ctx.fillText('No data configured', canvas.width / 2, canvas.height / 2);
                ctx.restore();
            }
        });
    }

    function updateSyncStatus() {
        const now = new Date();
        const hh = String(now.getHours()).padStart(2, '0');
        const mm = String(now.getMinutes()).padStart(2, '0');
        const ss = String(now.getSeconds()).padStart(2, '0');
        $('#atSyncStatus').text('Last synced: ' + hh + ':' + mm + ':' + ss);
    }

    // -------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------
    function escHtml(str) {
        if (!str && str !== 0) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function escAttr(str) {
        return escHtml(str);
    }

    // -------------------------------------------------------
    // Auto-refresh every 60s
    // -------------------------------------------------------
    setInterval(function () {
        const period = $('#atPeriodFilter').val();
        if (period) {
            loadDataForPeriod(period);
        }
    }, 60000);

    // -------------------------------------------------------
    // Cross-tab refresh when settings saved
    // -------------------------------------------------------
    window.addEventListener('storage', function (e) {
        if (e.key === 'abnormality_tracker_updated') {
            const period = $('#atPeriodFilter').val();
            if (period) {
                loadDataForPeriod(period);
            } else {
                loadPeriodsAndRender();
            }
        }
    });

    // Init
    applySavedSidebarState();
    loadPeriodsAndRender();
});

// ── PDF Export ──────────────────────────────────────────────────────────────
function exportAbnormalityPdf() {
    var btn = document.getElementById('atPdfBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Generating...'; }

    var tableData    = PmsReport.readDomTable('atSummaryTable');
    var periodFilter = document.getElementById('atPeriodFilter');
    var periodLabel  = periodFilter && periodFilter.options[periodFilter.selectedIndex]
        ? periodFilter.options[periodFilter.selectedIndex].text.trim()
        : '-';
    var filterLabel = 'Period: ' + periodLabel;

    var today = new Date();
    var dd   = String(today.getDate()).padStart(2, '0');
    var mm   = String(today.getMonth() + 1).padStart(2, '0');
    var yyyy = today.getFullYear();

    // Capture chart canvases as PNG images to embed after the table
    var tagChartDataUrl     = PmsReport.captureCanvas('tagCountChart');
    var closureChartDataUrl = PmsReport.captureCanvas('closureChart');

    var extraImages = [];
    var pageW = 210; // A4 portrait width in mm
    var chartW = 85;
    var chartH = 50;
    var gutter = 10;
    var totalChartW = chartW * 2 + gutter;
    var startX = (pageW - totalChartW) / 2;

    if (tagChartDataUrl) {
        extraImages.push({ dataUrl: tagChartDataUrl,     caption: 'No of Abnormality Tags',    w: chartW, h: chartH, x: startX });
    }
    if (closureChartDataUrl) {
        extraImages.push({ dataUrl: closureChartDataUrl, caption: 'Abnormality Tag Closure %', w: chartW, h: chartH, x: startX + chartW + gutter });
    }

    PmsReport.generate({
        title:       'Abnormality Tracker',
        filterLabel: filterLabel,
        orientation: 'portrait',
        columns:     tableData.columns,
        rows:        tableData.rows,
        extraImages: extraImages,
        filename:    'Abnormality-Tracker_' + yyyy + '-' + mm + '-' + dd + '.pdf'
    });

    setTimeout(function() {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-file-pdf"></i> Export PDF'; }
    }, 2000);
}
