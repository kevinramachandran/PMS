$(document).ready(function() {
    // Toggle nav children open/close
    $('.nav-parent-toggle').on('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        var $toggle = $(this);
        var $children = $toggle.next('.nav-children');

        if ($toggle.hasClass('expanded')) {
            $toggle.removeClass('expanded');
            $children.removeClass('show').slideUp(200);
        } else {
            $toggle.addClass('expanded');
            $children.addClass('show').slideDown(200);
        }
    });

    // Keep nav collapsed by default and only open the active branch
    $('.nav-parent-toggle').removeClass('expanded');
    $('.nav-children').removeClass('show').hide();

    function highlightCurrentNavigation() {
        const currentPath = window.location.pathname;
        const currentUrl = currentPath + window.location.search;

        $('.nav-item').removeClass('active');

        const $matchedChild = $('.nav-child').filter(function() {
            const href = $(this).attr('href');
            return href && (href === currentUrl || href === currentPath);
        }).first();

        if ($matchedChild.length) {
            $matchedChild.addClass('active');
            return;
        }

        const $matchedTop = $('.nav-item').not('.nav-child, .nav-parent-toggle').filter(function() {
            const href = $(this).attr('href');
            return href && href === currentPath;
        }).first();

        if ($matchedTop.length) {
            $matchedTop.addClass('active');
        }
    }

    highlightCurrentNavigation();

    const $activeChild = $('.nav-child.active');
    if ($activeChild.length) {
        const $parentChildren = $activeChild.closest('.nav-children');
        const $parentToggle = $parentChildren.prev('.nav-parent-toggle');
        $parentToggle.addClass('expanded');
        $parentChildren.addClass('show').show();
    }

    // Sidebar Toggle
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

    hamburger.on('click', function() {
        if (window.innerWidth > 768) {
            localStorage.setItem('sidebarCollapsed', sidebar.hasClass('collapsed'));
        }
    });

    bindDashboardModalEvents();
    initializeOverviewCards();

    $(document).on('click', '.chart-box canvas, .chart-card canvas', function() {
        if (this.id) {
            window.openChart(this.id);
        }
    });

    initializeKpiMonthFilter();
    setInterval(function() {
        loadProductionCharts(selectedKpiMonth, selectedKpiYear);
    }, 300000);
});

const chartThemes = {
    peopleProductivityChart: {
        primary: '#2E7D32',
        secondary: '#66BB6A',
        targetA: '#90A4AE',
        targetB: '#B0BEC5'
    },
    qualitySensoryChart: {
        primary: '#1565C0',
        targetA: '#64B5F6'
    },
    qualityComplaintChart: {
        primary: '#EF6C00',
        secondary: '#FB8C00'
    },
    serviceOeeChart: {
        primary: '#6A1B9A',
        targetA: '#BA68C8'
    },
    serviceBeerLossChart: {
        primary: '#C62828',
        targetA: '#EF9A9A'
    },
    serviceWurChart: {
        primary: '#00838F',
        targetA: '#4DD0E1'
    },
    costElectricityChart: {
        primary: '#F9A825',
        targetA: '#FFD54F'
    },
    costEnergyChart: {
        primary: '#283593',
        targetA: '#9FA8DA'
    },
    costRgbChart: {
        primary: '#4E342E',
        targetA: '#A1887F'
    }
};

const chartInstances = window.chartInstances || {};
window.chartInstances = chartInstances;
let expandedChartInstance = null;
let dashboardToastTimer = null;
let lastToastSignature = '';
let lastToastAt = 0;

function cloneChartPayload(payload) {
    return JSON.parse(JSON.stringify(payload || {}));
}

function resolveChartTitle(chartId) {
    const canvas = document.getElementById(chartId);
    if (!canvas) {
        return 'Expanded KPI Chart';
    }

    const panel = canvas.closest('.chart-box, .chart-card');
    if (!panel) {
        return 'Expanded KPI Chart';
    }

    const titleEl = panel.querySelector('.chart-title-text');
    const titleText = titleEl ? titleEl.textContent.trim() : '';
    return titleText || 'Expanded KPI Chart';
}

function showToast(message, signature) {
    const toast = document.getElementById('dashboardToast');
    if (!toast || !message) {
        return;
    }

    const resolvedSignature = signature || message;
    const now = Date.now();
    if (resolvedSignature === lastToastSignature && now - lastToastAt < 4000) {
        return;
    }

    lastToastSignature = resolvedSignature;
    lastToastAt = now;

    toast.textContent = message;
    toast.classList.add('show');

    if (dashboardToastTimer) {
        window.clearTimeout(dashboardToastTimer);
    }

    dashboardToastTimer = window.setTimeout(function() {
        toast.classList.remove('show');
    }, 2800);
}

window.showToast = showToast;

function closeChartModal() {
    const modal = document.getElementById('chartModal');
    if (!modal) {
        return;
    }

    if (expandedChartInstance) {
        expandedChartInstance.destroy();
        expandedChartInstance = null;
    }

    const expandedCanvas = document.getElementById('expandedChart');
    const customContent = document.getElementById('expandedCustomContent');
    if (expandedCanvas) {
        expandedCanvas.style.display = 'block';
    }
    if (customContent) {
        customContent.innerHTML = '';
        customContent.style.display = 'none';
    }

    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
}

window.closeChartModal = closeChartModal;

function bindDashboardModalEvents() {
    if (window.__dashboardModalBound) {
        return;
    }

    const modal = document.getElementById('chartModal');
    if (!modal) {
        return;
    }

    modal.addEventListener('click', function(event) {
        if (event.target === modal) {
            closeChartModal();
        }
    });

    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape' && modal.classList.contains('is-open')) {
            closeChartModal();
        }
    });

    window.__dashboardModalBound = true;
}

window.openChart = function(chartId) {
    if (!chartId || !window.chartInstances || !window.chartInstances[chartId]) {
        showToast('No data available for selected period', 'chart-not-ready');
        return;
    }

    const source = window.chartInstances[chartId];
    const chartType = source.config && source.config.type ? source.config.type : 'bar';
    const chartData = cloneChartPayload(source.data);

    if (!chartData || !Array.isArray(chartData.datasets) || chartData.datasets.length === 0) {
        showToast('No data available for selected period', 'chart-empty');
        return;
    }

    const modal = document.getElementById('chartModal');
    const expandedCanvas = document.getElementById('expandedChart');
    const customContent = document.getElementById('expandedCustomContent');
    const title = document.getElementById('expandedChartTitle');
    if (!modal || !expandedCanvas) {
        return;
    }

    if (customContent) {
        customContent.innerHTML = '';
        customContent.style.display = 'none';
    }
    expandedCanvas.style.display = 'block';

    const expandedOptions = cloneChartPayload(source.options);
    expandedOptions.responsive = true;
    expandedOptions.maintainAspectRatio = false;
    expandedOptions.animation = false;
    expandedOptions.plugins = expandedOptions.plugins || {};
    expandedOptions.plugins.legend = expandedOptions.plugins.legend || { display: true, position: 'top' };
    expandedOptions.plugins.tooltip = expandedOptions.plugins.tooltip || { enabled: true };

    if (expandedChartInstance) {
        expandedChartInstance.destroy();
    }

    if (title) {
        title.textContent = resolveChartTitle(chartId);
    }

    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');

    expandedChartInstance = new Chart(expandedCanvas.getContext('2d'), {
        type: chartType,
        data: chartData,
        options: expandedOptions
    });
};

window.openChartPopup = window.openChart;

function parseFirstNumber(text, fallbackValue) {
    const input = text === null || text === undefined ? '' : String(text);
    const matched = input.match(/-?\d+(\.\d+)?/);
    if (!matched) return fallbackValue;
    const parsed = Number(matched[0]);
    return Number.isFinite(parsed) ? parsed : fallbackValue;
}

function setMiniBoxState(boxEl, value, threshold) {
    if (!boxEl) return;
    boxEl.classList.remove('green', 'yellow', 'red');
    if (value >= threshold.good) {
        boxEl.classList.add('green');
        return;
    }
    if (value >= threshold.warn) {
        boxEl.classList.add('yellow');
        return;
    }
    boxEl.classList.add('red');
}

// Update LSR Overview Card Display
function refreshLsrOverview() {
    const lsr1 = parseFirstNumber((document.getElementById('lsr1Score') || {}).textContent, 100);
    const lsr23 = parseFirstNumber((document.getElementById('lsr23Score') || {}).textContent, 100);
    const lsr4 = parseFirstNumber((document.getElementById('lsr4Score') || {}).textContent, 100);
    const lsr5 = parseFirstNumber((document.getElementById('lsr5Score') || {}).textContent, 100);
    const lsrContractor = parseFirstNumber((document.getElementById('lsrContractorScore') || {}).textContent, 100);
    
    const valueMap = {
        lsr1ScoreDisplay: lsr1 + '%',
        lsr23ScoreDisplay: lsr23 + '%',
        lsr4ScoreDisplay: lsr4 + '%',
        lsr5ScoreDisplay: lsr5 + '%',
        lsrContractorScoreDisplay: lsrContractor + '%'
    };
    
    Object.keys(valueMap).forEach(function (id) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = String(valueMap[id]);
        }
    });
    
    // Update month/year
    const now = new Date();
    document.getElementById('lsrOverviewMonth').textContent = now.toLocaleString('default', { month: 'short' });
    document.getElementById('lsrOverviewYear').textContent = String(now.getFullYear()).slice(-2);
}

// Update H&S Overview Card Display
function refreshHsOverview() {
    const accidentsCells = document.querySelectorAll('#accidentsCrossGrid .hs-cross-cell[data-has-incident="true"]');
    const nearMissCells = document.querySelectorAll('#nearMissCrossGrid .hs-cross-cell[data-has-incident="true"]');
    const safetyConcernCells = document.querySelectorAll('#safetyConcernCrossGrid .hs-cross-cell[data-has-incident="true"]');
    
    const accidents = accidentsCells ? accidentsCells.length : 0;
    const nearMiss = nearMissCells ? nearMissCells.length : 0;
    const safetyConcern = safetyConcernCells ? safetyConcernCells.length : 0;
    
    document.getElementById('hsAccidentCount').textContent = String(accidents);
    document.getElementById('hsNearMissCount').textContent = String(nearMiss);
    document.getElementById('hsSafetyConcernCount').textContent = String(safetyConcern);
    
    // Update month/year
    const now = new Date();
    document.getElementById('hsOverviewMonth').textContent = now.toLocaleString('default', { month: 'short' });
    document.getElementById('hsOverviewYear').textContent = String(now.getFullYear()).slice(-2);
}

// Initialize overview cards with live updates
function initializeOverviewCards() {
    refreshLsrOverview();
    refreshHsOverview();
    window.setInterval(function () {
        refreshLsrOverview();
        refreshHsOverview();
    }, 2000);
}

// Open LSR Overview in modal
window.openLsrOverview = function () {
    const modal = document.getElementById('chartModal');
    const expandedCanvas = document.getElementById('expandedChart');
    const customContent = document.getElementById('expandedCustomContent');
    const title = document.getElementById('expandedChartTitle');

    if (!modal || !customContent || !expandedCanvas) {
        return;
    }

    if (expandedChartInstance) {
        expandedChartInstance.destroy();
        expandedChartInstance = null;
    }

    const lsrSection = document.querySelector('.lsr-tracking-section');
    customContent.innerHTML = '';
    if (lsrSection) {
        customContent.appendChild(lsrSection.cloneNode(true));
    }

    if (title) {
        title.textContent = 'Life Saving Rules - Full View';
    }

    expandedCanvas.style.display = 'none';
    customContent.style.display = 'block';
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
};

// Open H&S Overview in modal
window.openHsOverview = function () {
    const modal = document.getElementById('chartModal');
    const expandedCanvas = document.getElementById('expandedChart');
    const customContent = document.getElementById('expandedCustomContent');
    const title = document.getElementById('expandedChartTitle');

    if (!modal || !customContent || !expandedCanvas) {
        return;
    }

    if (expandedChartInstance) {
        expandedChartInstance.destroy();
        expandedChartInstance = null;
    }

    const hsSection = document.querySelector('.hs-cross-section');
    customContent.innerHTML = '';
    if (hsSection) {
        customContent.appendChild(hsSection.cloneNode(true));
    }

    if (title) {
        title.textContent = 'H&S Incidents - Full View';
    }

    expandedCanvas.style.display = 'none';
    customContent.style.display = 'block';
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
};

let selectedKpiMonth = new Date().getMonth() + 1;
let selectedKpiYear = new Date().getFullYear();

const kpiTableConfig = [
    {
        name: 'OEE',
        unit: '%',
        actualField: 'kpiOeeFtdActual',
        targetField: 'kpiOeeFtdTarget',
        decimals: 1,
        higherIsBetter: true
    },
    {
        name: 'Internal Sensory Score',
        unit: 'HL/HI',
        actualField: 'kpiSensoryScoreFtdActual',
        targetField: 'kpiSensoryScoreFtdTarget',
        decimals: 1,
        higherIsBetter: true
    },
    {
        name: 'WUR',
        unit: 'HL/HI',
        actualField: 'kpiWurHlHlFtdActual',
        targetField: 'kpiWurHlHlFtdTarget',
        decimals: 2,
        higherIsBetter: false
    },
    {
        name: 'Electricity',
        unit: 'Kwh/HI',
        actualField: 'kpiElectricityKwhHlFtdActual',
        targetField: 'kpiElectricityKwhHlFtdTarget',
        decimals: 1,
        higherIsBetter: false
    },
    {
        name: 'Energy',
        unit: 'Kwh/HI',
        actualField: 'kpiEnergyKwhHlFtdActual',
        targetField: 'kpiEnergyKwhHlFtdTarget',
        decimals: 2,
        higherIsBetter: false
    },
    {
        name: 'No. of Brews & Volume',
        unit: 'Nos & HL',
        actualField: null,
        targetField: null,
        decimals: 0,
        higherIsBetter: true
    },
    {
        name: 'Dispatch',
        unit: 'No. of Cases & HL',
        actualField: null,
        targetField: null,
        decimals: 0,
        higherIsBetter: true
    },
    {
        name: 'Production Productivity',
        unit: 'HL/FTE',
        actualField: 'productionProductivityFtdActual',
        targetField: 'productionProductivityFtdTarget',
        decimals: 0,
        higherIsBetter: true
    },
    {
        name: 'Logistics Productivity',
        unit: 'HL/FTE',
        actualField: 'logisticsProductivityFtdActual',
        targetField: 'logisticsProductivityFtdTarget',
        decimals: 0,
        higherIsBetter: true
    }
];

function loadProductionCharts(month, year) {
    const safeMonth = Number.isInteger(month) ? month : (new Date().getMonth() + 1);
    const safeYear = Number.isInteger(year) ? year : new Date().getFullYear();

    updateSyncStatus('Syncing...');
    loadDailyPerformanceSummary(safeMonth, safeYear);
    updateKpiDeckMonthLabel(safeMonth, safeYear);

    $.ajax({
        url: '/api/production-metrics/month?month=' + safeMonth + '&year=' + safeYear,
        type: 'GET',
        success: function(metrics) {
            const safeMetrics = Array.isArray(metrics) ? metrics : [];
            renderAllCharts(safeMetrics);
            renderDailyPerformanceTable(safeMetrics);
            updateSyncStatus('Last synced: ' + formatTime(new Date()));
        },
        error: function() {
            renderNoDataForAllCharts('API unavailable');
            renderDailyPerformanceTable([]);
            showToast('Unable to load KPI dashboard data', 'kpi-load-error');
            updateSyncStatus('Sync failed');
        }
    });
}

function formatTime(date) {
    return [
        String(date.getHours()).padStart(2, '0'),
        String(date.getMinutes()).padStart(2, '0'),
        String(date.getSeconds()).padStart(2, '0')
    ].join(':');
}

function initializeKpiMonthFilter() {
    const $filter = $('#kpiMonthFilter');
    if (!$filter.length) {
        loadProductionCharts(selectedKpiMonth, selectedKpiYear);
        return;
    }

    const options = buildKpiMonthOptions(18);
    $filter.empty();
    options.forEach(function(item) {
        $filter.append('<option value="' + item.value + '">' + item.label + '</option>');
    });

    const currentValue = selectedKpiYear + '-' + String(selectedKpiMonth).padStart(2, '0');
    $filter.val(currentValue);

    $filter.on('change', function() {
        const parts = ($(this).val() || '').split('-');
        if (parts.length !== 2) {
            return;
        }

        selectedKpiYear = Number(parts[0]);
        selectedKpiMonth = Number(parts[1]);
        loadProductionCharts(selectedKpiMonth, selectedKpiYear);
    });

    loadProductionCharts(selectedKpiMonth, selectedKpiYear);
}

function buildKpiMonthOptions(monthCount) {
    const options = [];
    const now = new Date();

    for (let i = 0; i < monthCount; i++) {
        const dt = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const year = dt.getFullYear();
        const month = dt.getMonth() + 1;
        const value = year + '-' + String(month).padStart(2, '0');
        const label = dt.toLocaleString('en-GB', { month: 'short', year: 'numeric' });
        options.push({ value: value, label: label });
    }

    return options;
}

function updateKpiDeckMonthLabel(month, year) {
    const dateElement = document.getElementById('pmsDeckDate');
    if (!dateElement) return;

    const dt = new Date(year, month - 1, 1);
    if (Number.isNaN(dt.getTime())) {
        dateElement.textContent = '-';
        return;
    }

    dateElement.textContent = dt.toLocaleString('en-GB', { month: 'short', year: 'numeric' });
}

function updateSyncStatus(text) {
    const el = document.getElementById('chartSyncStatus');
    if (!el) return;
    el.textContent = text;
}

function renderAllCharts(metrics) {
    const labels = metrics.map(m => {
        if (!m.date) return '';
        const dt = new Date(m.date);
        return Number.isNaN(dt.getTime()) ? '' : String(dt.getDate());
    });

    if (metrics.length === 0) {
        showToast('No data available for selected period', 'kpi-empty-period');
        renderNoDataForAllCharts('No Data Available');
        return;
    }

    // 1) PEOPLE - Productivity (Production + Logistics)
    renderKpiMixedChart('peopleProductivityChart', labels, metrics, {
        actualSeries: [
            { label: 'Production Actual', key: 'productionProductivityFtdActual' },
            { label: 'Logistics Actual', key: 'logisticsProductivityFtdActual' }
        ],
        targetSeries: [
            { label: 'Production Target', key: 'productionProductivityFtdTarget' },
            { label: 'Logistics Target', key: 'logisticsProductivityFtdTarget' }
        ]
    });

    // 2) QUALITY - Sensory
    renderKpiMixedChart('qualitySensoryChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiSensoryScoreFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiSensoryScoreFtdTarget' }
        ]
    });

    // 3) QUALITY - Complaint (Customer & Consumer)
    renderKpiMixedChart('qualityComplaintChart', labels, metrics, {
        actualSeries: [
            { label: 'Consumer Actual', key: 'kpiConsumerComplaintUnitsMhlFtdActual' },
            { label: 'Customer Actual', key: 'kpiCustomerComplaintUnitsMhlFtdActual' }
        ],
        targetSeries: [
            { label: 'Consumer Target', key: 'kpiConsumerComplaintUnitsMhlFtdTarget' },
            { label: 'Customer Target', key: 'kpiCustomerComplaintUnitsMhlFtdTarget' }
        ]
    });

    // 4) SERVICE - OEE
    renderKpiMixedChart('serviceOeeChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiOeeFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiOeeFtdTarget' }
        ]
    });

    // 5) SERVICE - Beer Loss
    renderKpiMixedChart('serviceBeerLossChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiBeerLossFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiBeerLossFtdTarget' }
        ]
    });

    // 6) SERVICE - WUR
    renderKpiMixedChart('serviceWurChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiWurHlHlFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiWurHlHlFtdTarget' }
        ]
    });

    // 7) COST - Electricity
    renderKpiMixedChart('costElectricityChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiElectricityKwhHlFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiElectricityKwhHlFtdTarget' }
        ]
    });

    // 8) COST - Energy
    renderKpiMixedChart('costEnergyChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiEnergyKwhHlFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiEnergyKwhHlFtdTarget' }
        ]
    });

    // 9) COST - RGB Ratio
    renderKpiMixedChart('costRgbChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiRgbRatioFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiRgbRatioFtdTarget' }
        ]
    });
}

const kpiChartPalettes = {
    peopleProductivityChart: {
        bars: ['#2563EB', '#38BDF8', '#1D4ED8'],
        lines: ['#0F172A', '#F59E0B', '#334155']
    },
    qualitySensoryChart: {
        bars: ['#A855F7', '#C084FC', '#7E22CE'],
        lines: ['#6D28D9', '#1F2937', '#EC4899']
    },
    qualityComplaintChart: {
        bars: ['#F97316', '#FDBA74', '#EA580C'],
        lines: ['#7C2D12', '#B45309', '#1F2937']
    },
    serviceOeeChart: {
        bars: ['#14B8A6', '#5EEAD4', '#0F766E'],
        lines: ['#115E59', '#1F2937', '#0EA5E9']
    },
    serviceBeerLossChart: {
        bars: ['#EF4444', '#FCA5A5', '#DC2626'],
        lines: ['#7F1D1D', '#1F2937', '#B91C1C']
    },
    serviceWurChart: {
        bars: ['#84CC16', '#BEF264', '#65A30D'],
        lines: ['#365314', '#1F2937', '#4D7C0F']
    },
    costElectricityChart: {
        bars: ['#EAB308', '#FDE047', '#CA8A04'],
        lines: ['#713F12', '#1F2937', '#B45309']
    },
    costEnergyChart: {
        bars: ['#0EA5E9', '#7DD3FC', '#0284C7'],
        lines: ['#0C4A6E', '#1F2937', '#0369A1']
    },
    costRgbChart: {
        bars: ['#8B5CF6', '#C4B5FD', '#7C3AED'],
        lines: ['#4C1D95', '#1F2937', '#6D28D9']
    }
};

function getKpiPalette(canvasId) {
    return kpiChartPalettes[canvasId] || {
        bars: ['#16A34A', '#22C55E', '#15803D'],
        lines: ['#064E1F', '#F59E0B', '#1F2937']
    };
}

function renderKpiMixedChart(canvasId, labels, metrics, seriesConfig) {
    const actualSeries = seriesConfig.actualSeries || [];
    const targetSeries = seriesConfig.targetSeries || [];
    const palette = getKpiPalette(canvasId);

    const datasets = [];

    actualSeries.forEach(function(series, index) {
        datasets.push(buildKpiDataset(series.label, metrics, series.key, 'actual', index, palette));
    });

    targetSeries.forEach(function(series, index) {
        datasets.push(buildKpiDataset(series.label, metrics, series.key, 'target', index, palette));
    });

    renderChart(canvasId, {
        type: 'bar',
        labels: labels,
        datasets: datasets
    });
}

function buildKpiDataset(label, metrics, key, seriesType, index, palette) {
    const data = metrics.map(m => {
        const value = m[key];
        return value === null || value === undefined ? null : Number(value);
    });

    const hasAnyValue = data.some(v => v !== null);
    if (!hasAnyValue) {
        return null;
    }

    if (seriesType === 'target') {
        const lineColor = palette.lines[index % palette.lines.length];
        return {
            label,
            type: 'line',
            data,
            borderColor: lineColor,
            backgroundColor: 'transparent',
            tension: 0.4,
            borderWidth: 2,
            pointRadius: 3,
            pointHoverRadius: 4,
            fill: false
        };
    }

    const barColor = palette.bars[index % palette.bars.length];
    return {
        label,
        type: 'bar',
        data,
        backgroundColor: barColor,
        borderRadius: 4,
        maxBarThickness: 20,
        categoryPercentage: 0.62,
        barPercentage: 0.86
    };
}

function renderDailyPerformanceTable(metrics) {
    const tbody = document.querySelector('.main-kpi-table tbody');
    if (!tbody) return;

    if (!Array.isArray(metrics) || metrics.length === 0) {
        updateDailyPerformanceAsOf(null, true);
        tbody.innerHTML = '<tr><td colspan="10" style="text-align:center; padding: 14px; color:#6b7280;">No Data Available</td></tr>';
        return;
    }

    const todayRecord = metrics[metrics.length - 1] || null;
    const yesterdayRecord = metrics.length > 1 ? metrics[metrics.length - 2] : null;

    updateDailyPerformanceAsOf(todayRecord ? todayRecord.date : null, true);

    const rows = kpiTableConfig.map(function(kpi, index) {
        const todayTarget = readNumber(todayRecord, kpi.targetField);
        const todayActual = readNumber(todayRecord, kpi.actualField);
        const yesterdayActual = readNumber(yesterdayRecord, kpi.actualField);
        const mtdValue = calcAverage(metrics, kpi.actualField);
        const ytdValue = mtdValue;

        const yesterdayClass = getPerformanceClass(yesterdayActual, todayTarget, kpi.higherIsBetter);
        const todayClass = getPerformanceClass(todayActual, todayTarget, kpi.higherIsBetter);
        const actualFtdClass = getPerformanceClass(todayActual, todayTarget, kpi.higherIsBetter);
        const actualMtdClass = getPerformanceClass(mtdValue, todayTarget, kpi.higherIsBetter);

        return '' +
            '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td class="text-left">' + escapeHtmlText(kpi.name) + '</td>' +
                '<td>' + escapeHtmlText(kpi.unit) + '</td>' +
                '<td' + classAttr(yesterdayClass) + '>' + formatMetric(yesterdayActual, kpi.decimals) + '</td>' +
                '<td' + classAttr(todayClass) + '>' + formatMetric(todayActual, kpi.decimals) + '</td>' +
                '<td' + classAttr(actualMtdClass) + '>' + formatMetric(mtdValue, kpi.decimals) + '</td>' +
                '<td>' + formatMetric(ytdValue, kpi.decimals) + '</td>' +
                '<td' + classAttr(actualFtdClass) + '>' + formatMetric(todayActual, kpi.decimals) + '</td>' +
                '<td' + classAttr(actualMtdClass) + '>' + formatMetric(mtdValue, kpi.decimals) + '</td>' +
                '<td>' + formatMetric(ytdValue, kpi.decimals) + '</td>' +
            '</tr>';
    });

    tbody.innerHTML = rows.join('');
}

function loadDailyPerformanceSummary(month, year) {
    const safeMonth = Number.isInteger(month) ? month : (new Date().getMonth() + 1);
    const safeYear = Number.isInteger(year) ? year : new Date().getFullYear();

    $.ajax({
        url: '/api/daily-performance/month?month=' + safeMonth + '&year=' + safeYear,
        type: 'GET',
        success: function(data) {
            renderDailyPerformanceSummary(data || null);
        },
        error: function(xhr) {
            if (xhr.status === 204 || xhr.status === 404) {
                renderDailyPerformanceSummary(null);
                return;
            }
            renderDailyPerformanceSummary(null);
        }
    });
}

function renderDailyPerformanceSummary(performanceData) {
    const summaryTable = document.querySelector('.daily-perf-table');
    if (!summaryTable) return;

    if (performanceData && performanceData.date) {
        updateDailyPerformanceAsOf(performanceData.date, true);
    }

    const valueCells = summaryTable.querySelectorAll('td');
    if (!valueCells || valueCells.length < 4) return;

    const monthTarget = readNumber(performanceData, 'month_target');
    const actualMtd = readNumber(performanceData, 'actual_mtd');
    const dailyTarget = readNumber(performanceData, 'daily_target');
    const yesterdayActual = readNumber(performanceData, 'yesterday');

    setSummaryCell(valueCells[0], monthTarget, 0, '');
    setSummaryCell(valueCells[1], actualMtd, 0, getPerformanceClass(actualMtd, monthTarget, true));
    setSummaryCell(valueCells[2], dailyTarget, 0, '');
    setSummaryCell(valueCells[3], yesterdayActual, 0, getPerformanceClass(yesterdayActual, dailyTarget, true));
}

function updateDailyPerformanceAsOf(dateValue, forceUpdate) {
    const asOf = document.getElementById('dailyPerfAsOf');
    if (!asOf) return;

    if (!dateValue) {
        if (forceUpdate) {
            asOf.textContent = 'As of: -';
        }
        return;
    }

    const parsed = new Date(dateValue);
    if (Number.isNaN(parsed.getTime())) {
        if (forceUpdate) {
            asOf.textContent = 'As of: -';
        }
        return;
    }

    const day = String(parsed.getDate()).padStart(2, '0');
    const month = String(parsed.getMonth() + 1).padStart(2, '0');
    const year = parsed.getFullYear();
    asOf.textContent = 'As of: ' + day + '-' + month + '-' + year;
}

function readNumber(record, fieldName) {
    if (!record || !fieldName) return null;
    const value = record[fieldName];
    if (value === null || value === undefined || value === '') return null;
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
}

function calcAverage(metrics, fieldName) {
    if (!fieldName || !Array.isArray(metrics) || metrics.length === 0) return null;

    const values = metrics
        .map(function(item) { return readNumber(item, fieldName); })
        .filter(function(v) { return v !== null; });

    if (values.length === 0) return null;

    const total = values.reduce(function(sum, value) { return sum + value; }, 0);
    return total / values.length;
}

function setSummaryCell(cell, value, decimals, className) {
    if (!cell) return;
    cell.textContent = formatMetric(value, decimals);
    cell.classList.remove('perf-value-good', 'perf-value-poor', 'val-good', 'val-poor');

    if (className === 'val-good') {
        cell.classList.add('perf-value-good');
    } else if (className === 'val-poor') {
        cell.classList.add('perf-value-poor');
    }
}

function formatMetric(value, decimals) {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return '-';
    }
    return Number(value).toFixed(decimals);
}

function getPerformanceClass(value, target, higherIsBetter) {
    if (value === null || target === null) return '';
    if (value === target) return '';

    if (higherIsBetter) {
        return value > target ? 'val-good' : 'val-poor';
    }
    return value < target ? 'val-good' : 'val-poor';
}

function classAttr(className) {
    return className ? ' class="' + className + '"' : '';
}

function escapeHtmlText(text) {
    if (text === null || text === undefined) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function renderChart(canvasId, config) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    const filteredDatasets = (config.datasets || []).filter(Boolean);
    if (filteredDatasets.length === 0) {
        renderNoData(canvasId, 'No Data Available');
        if (chartInstances[canvasId]) {
            chartInstances[canvasId].destroy();
            delete chartInstances[canvasId];
        }
        return;
    }

    clearNoData(canvasId);

    if (chartInstances[canvasId]) {
        chartInstances[canvasId].destroy();
    }

    chartInstances[canvasId] = new Chart(canvas.getContext('2d'), {
        type: config.type,
        data: {
            labels: config.labels,
            datasets: filteredDatasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 700,
                easing: 'easeOutCubic'
            },
            interaction: {
                mode: 'index',
                intersect: false
            },
            plugins: {
                legend: {
                    display: true,
                    position: 'top',
                    labels: { boxWidth: 10, font: { size: 9 } }
                },
                tooltip: {
                    enabled: true
                }
            },
            scales: {
                x: {
                    grid: { color: '#E5E7EB' },
                    ticks: { autoSkip: true, maxTicksLimit: 12 }
                },
                y: {
                    beginAtZero: true,
                    grid: { color: '#E5E7EB' }
                }
            }
        }
    });

    canvas.title = 'Click to expand chart';
}

function renderNoDataForAllCharts(message) {
    const ids = [
        'peopleProductivityChart',
        'qualitySensoryChart',
        'qualityComplaintChart',
        'serviceOeeChart',
        'serviceBeerLossChart',
        'serviceWurChart',
        'costElectricityChart',
        'costEnergyChart',
        'costRgbChart'
    ];

    ids.forEach(id => {
        renderNoData(id, message);
        if (chartInstances[id]) {
            chartInstances[id].destroy();
            delete chartInstances[id];
        }
    });
}

function renderNoData(canvasId, message) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    const parent = canvas.parentElement;
    if (!parent) return;

    let emptyEl = parent.querySelector('.chart-no-data');
    if (!emptyEl) {
        emptyEl = document.createElement('div');
        emptyEl.className = 'chart-no-data';
        emptyEl.style.cssText = 'height: 170px; display: flex; align-items: center; justify-content: center; color: #6b7280; font-size: 12px;';
        parent.appendChild(emptyEl);
    }

    emptyEl.textContent = message;
    canvas.style.display = 'none';
}

function clearNoData(canvasId) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    const parent = canvas.parentElement;
    if (!parent) return;

    const emptyEl = parent.querySelector('.chart-no-data');
    if (emptyEl) {
        emptyEl.remove();
    }

    canvas.style.display = 'block';
}
