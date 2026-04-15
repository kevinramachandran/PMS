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

    const isTvDashboard = document.body.classList.contains('kpi-tv-layout');

    if (isTvDashboard && window.innerWidth > 1024 && localStorage.getItem('sidebarCollapsed') !== 'false') {
        sidebar.addClass('collapsed');
        mainContent.addClass('expanded');
        localStorage.setItem('sidebarCollapsed', 'true');
    } else if (localStorage.getItem('sidebarCollapsed') === 'true' && window.innerWidth > 768) {
        sidebar.addClass('collapsed');
        mainContent.addClass('expanded');
    }

    hamburger.on('click', function() {
        if (window.innerWidth > 768) {
            localStorage.setItem('sidebarCollapsed', sidebar.hasClass('collapsed'));
            window.setTimeout(scheduleKpiTvFit, 320);
        }
    });

    bindDashboardModalEvents();
    initializeOverviewCards();
    initializeKpiTvFit();
    initializeDailyTopRowSync();

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

let kpiTvFitFrame = 0;
let kpiTvFitResizeObserver = null;

function isKpiTvLayout() {
    return document.body && document.body.classList.contains('kpi-tv-layout');
}

function resetKpiTvFit(shell, stage) {
    if (!shell || !stage) {
        return;
    }

    stage.style.width = '';
    stage.style.transform = '';
    shell.style.height = '';
}

function applyKpiTvFit() {
    const shell = document.getElementById('dashboardMainFitShell');
    const stage = document.getElementById('dashboardMainStage');

    if (!shell || !stage) {
        return;
    }

    // Keep fixed-fit mode only on wide screens; allow natural overflow + scroll on laptop-sized windows.
    if (!isKpiTvLayout() || window.innerWidth <= 1440 || window.innerHeight <= 680) {
        resetKpiTvFit(shell, stage);
        return;
    }

    stage.style.width = '';
    stage.style.transform = 'scale(1)';
    shell.style.height = 'auto';

    const availableWidth = shell.clientWidth;
    const shellTop = shell.getBoundingClientRect().top;
    const availableHeight = Math.max(window.innerHeight - shellTop - 12, 320);
    const naturalWidth = Math.ceil(stage.scrollWidth);
    const naturalHeight = Math.ceil(stage.offsetHeight);

    if (!availableWidth || !naturalWidth || !naturalHeight) {
        resetKpiTvFit(shell, stage);
        return;
    }

    const scale = Math.min(availableWidth / naturalWidth, availableHeight / naturalHeight, 1);

    stage.style.width = naturalWidth + 'px';
    stage.style.transform = 'scale(' + scale + ')';
    shell.style.height = Math.ceil(naturalHeight * scale) + 'px';
}

function syncDailyTopRowHeight() {
    const row = document.querySelector('.dashboard-right');
    const boxes = Array.from(document.querySelectorAll('.dashboard-right .daily-top-box'));

    if (!row || boxes.length === 0) {
        return;
    }

    boxes.forEach(function(box) {
        box.style.minHeight = '';
    });

    const tallest = boxes.reduce(function(maxValue, box) {
        return Math.max(maxValue, Math.ceil(box.scrollHeight));
    }, 0);

    const resolved = Math.max(tallest, isKpiTvLayout() ? 118 : 146);
    row.style.setProperty('--daily-row-box-height', resolved + 'px');

    boxes.forEach(function(box) {
        box.style.minHeight = resolved + 'px';
    });
}

function initializeDailyTopRowSync() {
    syncDailyTopRowHeight();

    window.addEventListener('resize', function() {
        window.requestAnimationFrame(syncDailyTopRowHeight);
    });

    const targets = document.querySelectorAll(
        '#peopleDailyBody, #qualityDailyBody, #serviceDailyBody, #costDailyBody'
    );

    if ('MutationObserver' in window) {
        const observer = new MutationObserver(function() {
            window.requestAnimationFrame(syncDailyTopRowHeight);
        });

        targets.forEach(function(target) {
            observer.observe(target, { childList: true, subtree: true, characterData: true });
        });
    }

    window.setInterval(syncDailyTopRowHeight, 2000);
}

function scheduleKpiTvFit() {
    if (!isKpiTvLayout()) {
        return;
    }

    if (kpiTvFitFrame) {
        window.cancelAnimationFrame(kpiTvFitFrame);
    }

    kpiTvFitFrame = window.requestAnimationFrame(function() {
        kpiTvFitFrame = 0;
        applyKpiTvFit();
    });
}

function initializeKpiTvFit() {
    const stage = document.getElementById('dashboardMainStage');

    if (!isKpiTvLayout() || !stage || window.__kpiTvFitBound) {
        scheduleKpiTvFit();
        return;
    }

    window.addEventListener('resize', scheduleKpiTvFit);
    window.addEventListener('load', scheduleKpiTvFit);

    if ('ResizeObserver' in window) {
        kpiTvFitResizeObserver = new ResizeObserver(function() {
            scheduleKpiTvFit();
        });
        kpiTvFitResizeObserver.observe(stage);
    }

    window.__kpiTvFitBound = true;

    scheduleKpiTvFit();
    window.setTimeout(scheduleKpiTvFit, 250);
    window.setTimeout(scheduleKpiTvFit, 1000);
}

const chartThemes = {
    peopleProductivityChart: {
        primary: '#1B5E20',
        secondary: '#43A047',
        targetA: '#90A4AE',
        targetB: '#B0BEC5'
    },
    qualitySensoryChart: {
        primary: '#2E7D32',
        targetA: '#66BB6A'
    },
    qualityComplaintChart: {
        primary: '#388E3C',
        secondary: '#81C784'
    },
    serviceOeeChart: {
        primary: '#004D40',
        targetA: '#26A69A'
    },
    serviceBeerLossChart: {
        primary: '#33691E',
        targetA: '#8BC34A'
    },
    serviceWurChart: {
        primary: '#00695C',
        targetA: '#4DB6AC'
    },
    costElectricityChart: {
        primary: '#558B2F',
        targetA: '#AED581'
    },
    costEnergyChart: {
        primary: '#1B5E20',
        targetA: '#A5D6A7'
    },
    costRgbChart: {
        primary: '#2E7D32',
        targetA: '#C8E6C9'
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

function monthShortName(monthNumber) {
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const idx = Number(monthNumber) - 1;
    return monthNames[idx] || '--';
}

function applyOverviewMonthYear(monthElId, yearElId, year, month) {
    const monthEl = document.getElementById(monthElId);
    const yearEl = document.getElementById(yearElId);
    if (monthEl) {
        monthEl.textContent = monthShortName(month);
    }
    if (yearEl) {
        const yy = Number(year);
        yearEl.textContent = Number.isFinite(yy) ? String(yy).slice(-2) : '--';
    }
}

window.updateHsOverviewFromApi = function(hsData, maxDays, year, month) {
    const byDay = {};
    (hsData || []).forEach(function(item) {
        if (item && Number.isFinite(Number(item.day))) {
            byDay[Number(item.day)] = item;
        }
    });

    let accidents = 0;
    let nearMiss = 0;
    let safetyConcern = 0;

    for (let day = 1; day <= maxDays; day++) {
        const entry = byDay[day] || {};
        if (entry.accidentStatus === 'WITH_LOST' || entry.accidentStatus === 'WITHOUT_LOST') {
            accidents++;
        }
        if (entry.nearMissStatus === 'OCCURRED') {
            nearMiss++;
        }
        if (entry.safetyConcernStatus === 'OCCURRED') {
            safetyConcern++;
        }
    }

    setElementText('hsAccidentCount', accidents);
    setElementText('hsNearMissCount', nearMiss);
    setElementText('hsSafetyConcernCount', safetyConcern);
    applyOverviewMonthYear('hsOverviewMonth', 'hsOverviewYear', year, month);
};

window.updateLsrOverviewFromApi = function(lsrData, maxDays, year, month) {
    const byDay = {};
    (lsrData || []).forEach(function(item) {
        if (item && Number.isFinite(Number(item.day))) {
            byDay[Number(item.day)] = item;
        }
    });

    const fields = [
        { key: 'lsr1Status', outputId: 'lsr1ScoreDisplay' },
        { key: 'lsr23Status', outputId: 'lsr23ScoreDisplay' },
        { key: 'lsr4Status', outputId: 'lsr4ScoreDisplay' },
        { key: 'lsr5Status', outputId: 'lsr5ScoreDisplay' },
        { key: 'contractorStatus', outputId: 'lsrContractorScoreDisplay' }
    ];

    fields.forEach(function(field) {
        let safe = 0;
        let considered = 0;
        for (let day = 1; day <= maxDays; day++) {
            const value = (byDay[day] || {})[field.key];
            if (value === 'SAFE' || value === 'UNSAFE') {
                considered++;
                if (value === 'SAFE') {
                    safe++;
                }
            }
        }
        const text = considered > 0 ? Math.round((safe / considered) * 100) + '%' : '-';
        setElementText(field.outputId, text);
    });

    applyOverviewMonthYear('lsrOverviewMonth', 'lsrOverviewYear', year, month);
};

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
    const lsr1 = parseFirstNumber((document.getElementById('lsr1Score') || {}).textContent, null);
    const lsr23 = parseFirstNumber((document.getElementById('lsr23Score') || {}).textContent, null);
    const lsr4 = parseFirstNumber((document.getElementById('lsr4Score') || {}).textContent, null);
    const lsr5 = parseFirstNumber((document.getElementById('lsr5Score') || {}).textContent, null);
    const lsrContractor = parseFirstNumber((document.getElementById('lsrContractorScore') || {}).textContent, null);
    
    const valueMap = {
        lsr1ScoreDisplay: lsr1 === null ? '-' : lsr1 + '%',
        lsr23ScoreDisplay: lsr23 === null ? '-' : lsr23 + '%',
        lsr4ScoreDisplay: lsr4 === null ? '-' : lsr4 + '%',
        lsr5ScoreDisplay: lsr5 === null ? '-' : lsr5 + '%',
        lsrContractorScoreDisplay: lsrContractor === null ? '-' : lsrContractor + '%'
    };
    
    Object.keys(valueMap).forEach(function (id) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = String(valueMap[id]);
        }
    });
    
}

// Update H&S Overview Card Display
function refreshHsOverview() {
    const accidentsCells = document.querySelectorAll('#accidentsCrossGrid .hs-cell-red, #accidentsCrossGrid .hs-cell-diag');
    const nearMissCells = document.querySelectorAll('#nearMissCrossGrid .hs-cell-red');
    const safetyConcernCells = document.querySelectorAll('#safetyConcernCrossGrid .hs-cell-red');
    
    const accidents = accidentsCells ? accidentsCells.length : 0;
    const nearMiss = nearMissCells ? nearMissCells.length : 0;
    const safetyConcern = safetyConcernCells ? safetyConcernCells.length : 0;
    
    document.getElementById('hsAccidentCount').textContent = String(accidents);
    document.getElementById('hsNearMissCount').textContent = String(nearMiss);
    document.getElementById('hsSafetyConcernCount').textContent = String(safetyConcern);
    
}

// Initialize overview cards with live updates
function initializeOverviewCards() {
    refreshLsrOverview();
    refreshHsOverview();
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
let kpiDashboardMetaLoaded = false;

function setElementText(id, value) {
    const el = document.getElementById(id);
    if (!el) {
        return;
    }
    el.textContent = value === null || value === undefined || value === '' ? '-' : String(value);
}

function renderLsrFocusRules(rulesMatrix) {
    const foot = document.getElementById('lsrFocusFoot');
    if (!foot) {
        return;
    }

    const rows = Array.isArray(rulesMatrix) ? rulesMatrix : [];
    const html = ['<tr><td class="lsr-focus-header" colspan="30">Primary Focus Behaviours</td></tr>'];

    if (rows.length === 0) {
        html.push('<tr class="lsr-focus-row"><td class="lsr-focus-cell lsr-focus-yellow" colspan="30">No focus behaviours configured.</td></tr>');
        foot.innerHTML = html.join('');
        return;
    }

    rows.forEach(function(ruleRow, rowIndex) {
        const cssClass = rowIndex % 2 === 0 ? 'lsr-focus-yellow' : 'lsr-focus-blue';
        const cells = Array.isArray(ruleRow) ? ruleRow.slice(0, 5) : [];

        while (cells.length < 5) {
            cells.push('-');
        }

        html.push('<tr class="lsr-focus-row">' + cells.map(function(ruleText) {
            return '<td class="lsr-focus-cell ' + cssClass + '" colspan="6">' + escapeHtmlText(ruleText || '-') + '</td>';
        }).join('') + '</tr>');
    });

    foot.innerHTML = html.join('');
}

function applyKpiFooterButtons(config) {
    const btn1 = document.getElementById('kpiFooterBtn1');
    const btn2 = document.getElementById('kpiFooterBtn2');

    if (!btn1 || !btn2 || !config) {
        return;
    }

    const btn1Label = (config.kpiButton1Label || config.button1Label || btn1.textContent || 'Solvex').trim();
    const btn1Type  = (config.kpiButton1Type  || config.button1Type  || 'link').trim();
    const btn1Url   = btn1Type === 'file'
        ? '/api/dashboard-config/kpi-footer-buttons/file/1'
        : (config.kpiButton1Url || config.button1Url || '').trim();

    const btn2Label = (config.kpiButton2Label || config.button2Label || btn2.textContent || 'Carlsbridge').trim();
    const btn2Type  = (config.kpiButton2Type  || config.button2Type  || 'link').trim();
    const btn2Url   = btn2Type === 'file'
        ? '/api/dashboard-config/kpi-footer-buttons/file/2'
        : (config.kpiButton2Url || config.button2Url || '').trim();

    if (btn1Label && btn1Url) {
        btn1.textContent = btn1Label;
        btn1.href = btn1Url;
        btn1.target = '_blank';
        btn1.style.display = 'inline-flex';
    } else {
        btn1.style.display = 'none';
    }

    if (btn2Label && btn2Url) {
        btn2.textContent = btn2Label;
        btn2.href = btn2Url;
        btn2.target = '_blank';
        btn2.style.display = 'inline-flex';
    } else {
        btn2.style.display = 'none';
    }
}

function loadKpiDashboardMeta() {
    if (kpiDashboardMetaLoaded) {
        return;
    }

    fetch('/api/dashboard-config/kpi')
        .then(function(response) {
            if (!response.ok) {
                throw new Error('Dashboard config request failed');
            }
            return response.json();
        })
        .then(function(config) {
            setElementText('pmsDeckTitle', config.deckTitle);
            setElementText('lsrTargetText', config.lsrOverviewTarget);
            setElementText('lsr12Target', config.lsrTarget12);
            setElementText('lsr5Target', config.lsrTarget5);
            renderLsrFocusRules(config.lsrFocusRules);
            applyKpiFooterButtons(config);
            kpiDashboardMetaLoaded = true;
        })
        .catch(function() {
            setElementText('pmsDeckTitle', '-');
            renderLsrFocusRules([]);
            applyKpiFooterButtons({});
        });
}

const kpiTableConfig = [
    {
        name: 'OEE',
        unit: '%',
        actualField: 'kpiOeeFtdActual',
        targetField: 'kpiOeeFtdTarget',
        mtdField: 'kpiOeeMtdActual',
        targetMtdField: 'kpiOeeMtdTarget',
        ytdField: 'kpiOeeYtdActual',
        targetYtdField: 'kpiOeeYtdTarget',
        decimals: 1,
        higherIsBetter: true
    },
    {
        name: 'Internal Sensory Score',
        unit: 'HL/HI',
        actualField: 'kpiSensoryScoreFtdActual',
        targetField: 'kpiSensoryScoreFtdTarget',
        mtdField: 'kpiSensoryScoreMtdActual',
        targetMtdField: 'kpiSensoryScoreMtdTarget',
        ytdField: 'kpiSensoryScoreYtdActual',
        targetYtdField: 'kpiSensoryScoreYtdTarget',
        decimals: 1,
        higherIsBetter: true
    },
    {
        name: 'WUR',
        unit: 'HL/HI',
        actualField: 'kpiWurHlHlFtdActual',
        targetField: 'kpiWurHlHlFtdTarget',
        mtdField: 'kpiWurHlHlMtdActual',
        targetMtdField: 'kpiWurHlHlMtdTarget',
        ytdField: 'kpiWurHlHlYtdActual',
        targetYtdField: 'kpiWurHlHlYtdTarget',
        decimals: 2,
        higherIsBetter: false
    },
    {
        name: 'Electricity',
        unit: 'Kwh/HI',
        actualField: 'kpiElectricityKwhHlFtdActual',
        targetField: 'kpiElectricityKwhHlFtdTarget',
        mtdField: 'kpiElectricityKwhHlMtdActual',
        targetMtdField: 'kpiElectricityKwhHlMtdTarget',
        ytdField: 'kpiElectricityKwhHlYtdActual',
        targetYtdField: 'kpiElectricityKwhHlYtdTarget',
        decimals: 1,
        higherIsBetter: false
    },
    {
        name: 'Energy',
        unit: 'Kwh/HI',
        actualField: 'kpiEnergyKwhHlFtdActual',
        targetField: 'kpiEnergyKwhHlFtdTarget',
        mtdField: 'kpiEnergyKwhHlMtdActual',
        targetMtdField: 'kpiEnergyKwhHlMtdTarget',
        ytdField: 'kpiEnergyKwhHlYtdActual',
        targetYtdField: 'kpiEnergyKwhHlYtdTarget',
        decimals: 2,
        higherIsBetter: false
    },
    {
        name: 'No. of Brews & Volume',
        unit: 'Nos & HL',
        actualField: 'noOfBrewsFtdActual',
        targetField: 'noOfBrewsFtdTarget',
        mtdField: 'noOfBrewsMtdActual',
        targetMtdField: 'noOfBrewsMtdTarget',
        ytdField: 'noOfBrewsYtdActual',
        targetYtdField: 'noOfBrewsYtdTarget',
        decimals: 0,
        higherIsBetter: true
    },
    {
        name: 'Dispatch',
        unit: 'No. of Cases & HL',
        actualField: 'dispatchFtdActual',
        targetField: 'dispatchFtdTarget',
        mtdField: 'dispatchMtdActual',
        targetMtdField: 'dispatchMtdTarget',
        ytdField: 'dispatchYtdActual',
        targetYtdField: 'dispatchYtdTarget',
        decimals: 0,
        higherIsBetter: true
    },
    {
        name: 'Production Productivity',
        unit: 'HL/FTE',
        actualField: 'productionProductivityFtdActual',
        targetField: 'productionProductivityFtdTarget',
        mtdField: 'productionProductivityMtdActual',
        targetMtdField: 'productionProductivityMtdTarget',
        ytdField: 'productionProductivityYtdActual',
        targetYtdField: 'productionProductivityYtdTarget',
        decimals: 0,
        higherIsBetter: true
    },
    {
        name: 'Logistics Productivity',
        unit: 'HL/FTE',
        actualField: 'logisticsProductivityFtdActual',
        targetField: 'logisticsProductivityFtdTarget',
        mtdField: 'logisticsProductivityMtdActual',
        targetMtdField: 'logisticsProductivityMtdTarget',
        ytdField: 'logisticsProductivityYtdActual',
        targetYtdField: 'logisticsProductivityYtdTarget',
        decimals: 0,
        higherIsBetter: true
    }
];

function loadProductionCharts(month, year) {
    const safeMonth = Number.isInteger(month) ? month : (new Date().getMonth() + 1);
    const safeYear = Number.isInteger(year) ? year : new Date().getFullYear();

    loadKpiDashboardMeta();
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

    // 3) QUALITY - Process Confirmation (B&P and Pack)
    renderKpiMixedChart('qualityProcessConfirmationChart', labels, metrics, {
        actualSeries: [
            { label: 'B&P Actual', key: 'processConfirmationBpFtdActual' },
            { label: 'Pack Actual', key: 'processConfirmationPackMtdActual' }
        ],
        targetSeries: [
            { label: 'B&P Target', key: 'processConfirmationBpFtdTarget' },
            { label: 'Pack Target', key: 'processConfirmationPackMtdTarget' }
        ]
    });

    // 4) QUALITY - Complaint (Customer & Consumer)
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

    // 5) SERVICE - OEE
    renderKpiMixedChart('serviceOeeChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiOeeFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiOeeFtdTarget' }
        ]
    });

    // 6) SERVICE - Beer Loss
    renderKpiMixedChart('serviceBeerLossChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiBeerLossFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiBeerLossFtdTarget' }
        ]
    });

    // 7) SERVICE - WUR
    renderKpiMixedChart('serviceWurChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiWurHlHlFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiWurHlHlFtdTarget' }
        ]
    });

    // 8) COST - Electricity
    renderKpiMixedChart('costElectricityChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiElectricityKwhHlFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiElectricityKwhHlFtdTarget' }
        ]
    });

    // 9) COST - Energy
    renderKpiMixedChart('costEnergyChart', labels, metrics, {
        actualSeries: [
            { label: 'Actual', key: 'kpiEnergyKwhHlFtdActual' }
        ],
        targetSeries: [
            { label: 'Target', key: 'kpiEnergyKwhHlFtdTarget' }
        ]
    });

    // 10) COST - RGB Ratio
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
        bars: ['#1B5E20', '#43A047', '#2E7D32'],
        lines: ['#0a2e0a', '#66BB6A', '#1B5E20']
    },
    qualitySensoryChart: {
        bars: ['#2E7D32', '#4CAF50', '#1B5E20'],
        lines: ['#1B5E20', '#388E3C', '#2E7D32']
    },
    qualityProcessConfirmationChart: {
        bars: ['#388E3C', '#66BB6A', '#2E7D32'],
        lines: ['#1B5E20', '#43A047', '#2E7D32']
    },
    qualityComplaintChart: {
        bars: ['#43A047', '#81C784', '#388E3C'],
        lines: ['#1B5E20', '#4CAF50', '#2E7D32']
    },
    serviceOeeChart: {
        bars: ['#00695C', '#26A69A', '#004D40'],
        lines: ['#003D30', '#2E7D32', '#00695C']
    },
    serviceBeerLossChart: {
        bars: ['#2E7D32', '#66BB6A', '#1B5E20'],
        lines: ['#003300', '#388E3C', '#1B5E20']
    },
    serviceWurChart: {
        bars: ['#33691E', '#7CB342', '#558B2F'],
        lines: ['#1B3300', '#2E7D32', '#33691E']
    },
    costElectricityChart: {
        bars: ['#558B2F', '#AED581', '#33691E'],
        lines: ['#1B5E20', '#43A047', '#33691E']
    },
    costEnergyChart: {
        bars: ['#00796B', '#4DB6AC', '#00695C'],
        lines: ['#003D30', '#1B5E20', '#004D40']
    },
    costRgbChart: {
        bars: ['#1B5E20', '#A5D6A7', '#2E7D32'],
        lines: ['#003300', '#388E3C', '#2E7D32']
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
        updateYesterdayDataDate(null);
        updateDateLabel('todayTargetDate', null);
        updateDateLabel('ftdYesterdayDate', null);
        updateDateLabel('ftdTodayDate', null);
        tbody.innerHTML = '<tr><td colspan="10" style="text-align:center; padding: 14px; color:#6b7280;">No Data Available</td></tr>';
        return;
    }

    const today = new Date();
    const currentDateKey = toLocalDateKey(today);
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayDateKey = toLocalDateKey(yesterday);

    const metricsByActualDate = new Map();
    const metricsByTargetDate = new Map();
    metrics.forEach(function(record) {
        if (!record || !record.date) {
            return;
        }
        const actualDateKey = String(record.date).split('T')[0];
        metricsByActualDate.set(actualDateKey, record);
        const targetDateKey = record.targetDate ? String(record.targetDate).split('T')[0] : '';
        if (targetDateKey) {
            metricsByTargetDate.set(targetDateKey, record);
        }
    });

    const lastRecord = metrics[metrics.length - 1] || null;
    const todayTargetRecord = metricsByTargetDate.get(currentDateKey) || lastRecord;
    const yesterdayTargetRecord = metricsByTargetDate.get(yesterdayDateKey) || lastRecord;
    const yesterdayActualRecord = metricsByActualDate.get(yesterdayDateKey) || (metrics.length > 1 ? metrics[metrics.length - 2] : lastRecord);

    updateDailyPerformanceAsOf(todayTargetRecord ? todayTargetRecord.targetDate || todayTargetRecord.date : null, true);
    updateYesterdayDataDate(yesterdayActualRecord ? yesterdayActualRecord.date : (todayTargetRecord ? todayTargetRecord.date : null));
    updateDateLabel('todayTargetDate', todayTargetRecord ? (todayTargetRecord.targetDate || todayTargetRecord.date) : null);
    updateDateLabel('ftdYesterdayDate', yesterdayTargetRecord ? yesterdayTargetRecord.date : null);
    updateDateLabel('ftdTodayDate', todayTargetRecord ? todayTargetRecord.date : null);

    const rows = kpiTableConfig.map(function(kpi, index) {
        const todayTarget = readNumber(todayTargetRecord, kpi.targetField);
        const yesterdayTarget = readNumber(yesterdayTargetRecord, kpi.targetField);
        const yesterdayActual = readNumber(yesterdayActualRecord, kpi.actualField);
        const targetMtdValue = readNumber(todayTargetRecord, kpi.targetMtdField || kpi.mtdField);
        const targetYtdValue = readNumber(todayTargetRecord, kpi.targetYtdField || kpi.ytdField);
        const actualMtdValue = readNumber(yesterdayActualRecord, kpi.mtdField);
        const actualYtdValue = readNumber(yesterdayActualRecord, kpi.ytdField);

        const yesterdayTargetClass = getPerformanceClass(yesterdayTarget, todayTarget, kpi.higherIsBetter);
        const todayTargetClass = getPerformanceClass(todayTarget, yesterdayTarget, kpi.higherIsBetter);
        const targetMtdClass = getPerformanceClass(targetMtdValue, todayTarget, kpi.higherIsBetter);
        const yesterdayActualClass = getPerformanceClass(yesterdayActual, todayTarget, kpi.higherIsBetter);
        const actualMtdClass = getPerformanceClass(actualMtdValue, targetMtdValue, kpi.higherIsBetter);

        return '' +
            '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td class="text-left">' + escapeHtmlText(kpi.name) + '</td>' +
                '<td>' + escapeHtmlText(kpi.unit) + '</td>' +
                '<td' + classAttr(yesterdayTargetClass) + '>' + formatMetric(yesterdayTarget, kpi.decimals) + '</td>' +
                '<td' + classAttr(todayTargetClass) + '>' + formatMetric(todayTarget, kpi.decimals) + '</td>' +
                '<td' + classAttr(targetMtdClass) + '>' + formatMetric(targetMtdValue, kpi.decimals) + '</td>' +
                '<td>' + formatMetric(targetYtdValue, kpi.decimals) + '</td>' +
                '<td' + classAttr(yesterdayActualClass) + '>' + formatMetric(yesterdayActual, kpi.decimals) + '</td>' +
                '<td' + classAttr(actualMtdClass) + '>' + formatMetric(actualMtdValue, kpi.decimals) + '</td>' +
                '<td>' + formatMetric(actualYtdValue, kpi.decimals) + '</td>' +
            '</tr>';
    });

    tbody.innerHTML = rows.join('');
}

function toLocalDateKey(date) {
    if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
        return '';
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return year + '-' + month + '-' + day;
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

function updateYesterdayDataDate(dateValue) {
    const dateLabel = document.getElementById('yesterdayDataDate');
    if (!dateLabel) return;

    if (!dateValue) {
        dateLabel.textContent = '';
        return;
    }

    const parsed = new Date(dateValue);
    if (Number.isNaN(parsed.getTime())) {
        dateLabel.textContent = '';
        return;
    }

    const day = String(parsed.getDate()).padStart(2, '0');
    const month = String(parsed.getMonth() + 1).padStart(2, '0');
    const year = parsed.getFullYear();
    dateLabel.textContent = '[' + day + '-' + month + '-' + year + ']';
}

function updateDateLabel(elementId, dateValue) {
    const el = document.getElementById(elementId);
    if (!el) return;
    if (!dateValue) {
        el.textContent = '';
        return;
    }
    const parsed = new Date(dateValue);
    if (Number.isNaN(parsed.getTime())) {
        el.textContent = '';
        return;
    }
    const day = String(parsed.getDate()).padStart(2, '0');
    const month = String(parsed.getMonth() + 1).padStart(2, '0');
    const year = parsed.getFullYear();
    el.textContent = '[' + day + '-' + month + '-' + year + ']';
}

function readNumber(record, fieldName) {
    if (!record || !fieldName) return null;
    const value = record[fieldName];
    if (value === null || value === undefined || value === '') return null;
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
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
        'qualityProcessConfirmationChart',
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
