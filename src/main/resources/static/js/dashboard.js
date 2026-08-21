$(document).ready(function() {
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
            return href && (href === currentUrl || href === currentPath);
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
        $parentChildren.addClass('show');
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
    initializeModalExportControls();
    initializeKpiTableExport();
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.export-dropdown-wrap')) { closeExportDropdowns(); }
    });
    initializeKpiTvFit();
    initializeDailyTopRowSync();
    renderSystemChartContainers();

    $(document).on('click', '.chart-box canvas, .chart-card canvas', function() {
        if (this.id) {
            window.openChart(this.id);
        }
    });

    loadKpiMetricDefinitions().always(function() {
        initializeKpiMonthFilter();
    });
    setInterval(function() {
        loadProductionCharts(selectedKpiMonth, selectedKpiYear, selectedKpiDate);
    }, 300000);

    window.addEventListener('storage', function(event) {
        if (event.key === 'kpi-dashboard-update') {
            loadKpiMetricDefinitions().always(function() {
                loadProductionCharts(selectedKpiMonth, selectedKpiYear, selectedKpiDate);
            });
        }
    });
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
    shell.style.overflow = '';
}

function applyKpiTvFit() {
    const shell = document.getElementById('dashboardMainFitShell');
    const stage = document.getElementById('dashboardMainStage');

    if (!shell || !stage) {
        return;
    }

    // Keep natural mobile/tablet scrolling, but fit the full dashboard on normal desktop screens.
    if (!isKpiTvLayout() || window.innerWidth <= 1024 || window.innerHeight <= 560) {
        resetKpiTvFit(shell, stage);
        return;
    }

    stage.style.width = '';
    stage.style.transform = 'scale(1)';
    shell.style.height = 'auto';

    const availableWidth = shell.clientWidth;
    const shellTop = shell.getBoundingClientRect().top;
    const naturalWidth = Math.ceil(stage.scrollWidth);
    const naturalHeight = Math.ceil(stage.offsetHeight);

    if (!availableWidth || !naturalWidth || !naturalHeight) {
        resetKpiTvFit(shell, stage);
        return;
    }

    const scale = Math.min(availableWidth / naturalWidth, 1);

    stage.style.width = naturalWidth + 'px';
    stage.style.transform = 'scale(' + scale + ')';
    shell.style.height = Math.ceil(naturalHeight * scale) + 'px';
    shell.style.overflow = 'hidden';
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

function refreshKpiDashboardLayout() {
    window.requestAnimationFrame(function() {
        syncDailyTopRowHeight();
        scheduleKpiTvFit();
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

    const dashboardRight = document.querySelector('.dashboard-right');
    if (dashboardRight && 'MutationObserver' in window) {
        const observer = new MutationObserver(refreshKpiDashboardLayout);
        observer.observe(dashboardRight, {
            attributes: true,
            childList: true,
            subtree: true,
            attributeFilter: ['class', 'hidden']
        });
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
    noOfBrewsChart: {
        primary: '#0F766E',
        targetA: '#5EEAD4'
    },
    dispatchChart: {
        primary: '#166534',
        targetA: '#86EFAC'
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
    document.body.classList.remove('drawer-open');
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

function slugifyFileName(input) {
    if (!input) {
        return 'kpi-dashboard-export';
    }
    return String(input)
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '') || 'kpi-dashboard-export';
}

function downloadDataUrl(dataUrl, filename) {
    const anchor = document.createElement('a');
    anchor.href = dataUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
}

function downloadTextFile(content, filename, mimeType) {
    const blob = new Blob([content], { type: mimeType || 'text/plain;charset=utf-8;' });
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(objectUrl);
}

function tableToCsv(tableEl) {
    if (!tableEl) {
        return '';
    }

    const rows = [];
    const trList = tableEl.querySelectorAll('tr');
    trList.forEach(function(tr) {
        const cells = tr.querySelectorAll('th, td');
        const values = [];
        cells.forEach(function(cell) {
            const raw = (cell.textContent || '').replace(/\s+/g, ' ').trim();
            const escaped = '"' + raw.replace(/"/g, '""') + '"';
            values.push(escaped);
        });
        if (values.length > 0) {
            rows.push(values.join(','));
        }
    });

    return rows.join('\n');
}

function exportModalAsPng() {
    closeExportDropdowns();
    const modal = document.getElementById('chartModal');
    if (!modal || !modal.classList.contains('is-open')) {
        showToast('Open a popup before exporting.', 'modal-export-not-open');
        return;
    }
    const expandedCanvas = document.getElementById('expandedChart');
    if (!expandedCanvas || expandedCanvas.style.display === 'none') {
        showToast('PNG export is only available for chart popups.', 'modal-export-png-unavail');
        return;
    }
    const title = getSafeModalTitle();
    const dataUrl = expandedCanvas.toDataURL('image/png');
    downloadDataUrl(dataUrl, slugifyFileName(title) + '.png');
    showToast('Chart exported as PNG.', 'modal-export-chart');
}

function exportModalAsCsv() {
    closeExportDropdowns();
    const modal = document.getElementById('chartModal');
    if (!modal || !modal.classList.contains('is-open')) {
        showToast('Open a popup before exporting.', 'modal-export-not-open');
        return;
    }
    const customContent = document.getElementById('expandedCustomContent');
    const expandedCanvas = document.getElementById('expandedChart');
    const title = getSafeModalTitle();
    const safeBaseName = slugifyFileName(title);

    // Canvas chart: extract labels + datasets as CSV
    if (expandedCanvas && expandedCanvas.style.display !== 'none') {
        const chartKey = expandedCanvas.dataset.chartId;
        const instance = chartKey && window.chartInstances && window.chartInstances[chartKey];
        if (instance && instance.data) {
            const data = instance.data;
            const labels = (data.labels || []).map(String);
            const header = ['Label'].concat((data.datasets || []).map(function(ds) { return ds.label || ''; }));
            const rows = labels.map(function(lbl, i) {
                const vals = (data.datasets || []).map(function(ds) {
                    const v = ds.data[i];
                    return v === undefined ? '' : String(v);
                });
                return [lbl].concat(vals);
            });
            const csv = [header].concat(rows).map(function(r) {
                return r.map(function(c) { return '"' + c.replace(/"/g, '""') + '"'; }).join(',');
            }).join('\n');
            downloadTextFile(csv, safeBaseName + '.csv', 'text/csv;charset=utf-8;');
            showToast('Chart data exported as CSV.', 'modal-export-chart-csv');
            return;
        }
        showToast('No chart data available for CSV export.', 'modal-export-chart-csv-empty');
        return;
    }

    if (customContent && customContent.style.display !== 'none') {
        const table = customContent.querySelector('table');
        if (table) {
            const csv = tableToCsv(table);
            if (!csv) {
                showToast('No table data available to export.', 'modal-export-empty-table');
                return;
            }
            downloadTextFile(csv, safeBaseName + '.csv', 'text/csv;charset=utf-8;');
            showToast('Table exported as CSV.', 'modal-export-table');
            return;
        }
    }
    showToast('No data available for CSV export in this popup.', 'modal-export-csv-unavail');
}

function getSafeModalTitle() {
    const el = document.getElementById('expandedChartTitle');
    if (!el) { return 'KPI Export'; }
    const text = (el.textContent || '').trim();
    return (text && text.toLowerCase() !== 'null') ? text : 'KPI Export';
}

function getJsPdfCtor() {
    if (window.jspdf && window.jspdf.jsPDF) {
        return window.jspdf.jsPDF;
    }
    if (window.jsPDF) {
        return window.jsPDF;
    }
    return null;
}

function createPdfDoc(title) {
    const JsPdfCtor = getJsPdfCtor();
    if (!JsPdfCtor) {
        return null;
    }

    const doc = new JsPdfCtor({ orientation: 'landscape', unit: 'mm', format: 'a4' });
    doc.setFontSize(14);
    doc.text(String(title || 'KPI Export'), 10, 12);
    return doc;
}

function saveCanvasPdf(canvasEl, title, fileNameBase) {
    if (!canvasEl) {
        return false;
    }

    const doc = createPdfDoc(title);
    if (!doc) {
        return false;
    }

    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();
    const contentX = 10;
    const contentY = 18;
    const contentW = pageWidth - (contentX * 2);
    const contentH = pageHeight - contentY - 10;

    const imgData = canvasEl.toDataURL('image/png');
    const sourceW = canvasEl.width || 1;
    const sourceH = canvasEl.height || 1;
    const aspect = sourceW / sourceH;

    let drawW = contentW;
    let drawH = drawW / aspect;
    if (drawH > contentH) {
        drawH = contentH;
        drawW = drawH * aspect;
    }

    const offsetX = contentX + ((contentW - drawW) / 2);
    doc.addImage(imgData, 'PNG', offsetX, contentY, drawW, drawH, undefined, 'FAST');
    doc.save(slugifyFileName(fileNameBase || title || 'kpi-export') + '.pdf');
    return true;
}

function saveTablePdf(tableEl, title, fileNameBase) {
    if (!tableEl) {
        return false;
    }

    const doc = createPdfDoc(title);
    if (!doc) {
        return false;
    }

    if (typeof doc.autoTable === 'function') {
        doc.autoTable({
            html: tableEl,
            startY: 18,
            theme: 'grid',
            styles: { fontSize: 8, cellPadding: 1.6 },
            headStyles: { fillColor: [0, 61, 36], textColor: [255, 255, 255] },
            margin: { left: 8, right: 8 }
        });
    } else {
        const rows = [];
        tableEl.querySelectorAll('tr').forEach(function(tr) {
            const cols = [];
            tr.querySelectorAll('th, td').forEach(function(cell) {
                cols.push((cell.textContent || '').replace(/\s+/g, ' ').trim());
            });
            if (cols.length > 0) {
                rows.push(cols.join(' | '));
            }
        });
        doc.setFontSize(9);
        doc.text(rows.join('\n'), 10, 20);
    }

    doc.save(slugifyFileName(fileNameBase || title || 'kpi-export') + '.pdf');
    return true;
}

function exportModalAsPdf() {
    closeExportDropdowns();
    const modal = document.getElementById('chartModal');
    if (!modal || !modal.classList.contains('is-open')) {
        showToast('Open a popup before exporting.', 'modal-export-not-open');
        return;
    }
    const title = getSafeModalTitle();
    const expandedCanvas = document.getElementById('expandedChart');
    const customContent = document.getElementById('expandedCustomContent');

    if (!getJsPdfCtor()) {
        showToast('PDF library is not loaded. Refresh and try again.', 'modal-export-pdf-lib-missing');
        return;
    }

    if (expandedCanvas && expandedCanvas.style.display !== 'none') {
        if (!saveCanvasPdf(expandedCanvas, title, title)) {
            showToast('Unable to export chart PDF.', 'modal-export-pdf-chart-failed');
            return;
        }
        showToast('Chart exported as PDF.', 'modal-export-pdf-chart');
        return;
    }

    if (customContent && customContent.style.display !== 'none') {
        const contentTable = customContent.querySelector('table');
        if (contentTable) {
            if (!saveTablePdf(contentTable, title, title)) {
                showToast('Unable to export table PDF.', 'modal-export-pdf-table-failed');
                return;
            }
            showToast('Table exported as PDF.', 'modal-export-pdf-table');
            return;
        }
    }

    showToast('Nothing to export in this popup.', 'modal-export-pdf-empty');
}

function closeExportDropdowns() {
    document.querySelectorAll('.export-dropdown-menu.is-open').forEach(function(m) {
        m.classList.remove('is-open');
    });
}

function initializeModalExportControls() {
    const modalHeader = document.querySelector('#chartModal .chart-modal-header');
    if (!modalHeader || modalHeader.querySelector('#chartModalExportWrap')) {
        return;
    }

    const wrap = document.createElement('div');
    wrap.id = 'chartModalExportWrap';
    wrap.className = 'export-dropdown-wrap';
    wrap.innerHTML =
        '<button type="button" class="chart-modal-export export-dropdown-toggle" aria-haspopup="true" aria-expanded="false">'
        + '<i class="fas fa-file-export"></i> Export <i class="fas fa-caret-down export-caret"></i>'
        + '</button>'
        + '<ul class="export-dropdown-menu" role="menu">'
        + '<li><button type="button" class="export-menu-item" data-action="png"><i class="fas fa-image"></i> PNG (image)</button></li>'
        + '<li><button type="button" class="export-menu-item" data-action="csv"><i class="fas fa-file-csv"></i> CSV (data)</button></li>'
        + '<li><button type="button" class="export-menu-item" data-action="pdf"><i class="fas fa-file-pdf"></i> PDF</button></li>'
        + '</ul>';

    wrap.querySelector('.export-dropdown-toggle').addEventListener('click', function(e) {
        e.stopPropagation();
        const menu = wrap.querySelector('.export-dropdown-menu');
        const isOpen = menu.classList.contains('is-open');
        closeExportDropdowns();
        if (!isOpen) {
            menu.classList.add('is-open');
            this.setAttribute('aria-expanded', 'true');
        } else {
            this.setAttribute('aria-expanded', 'false');
        }
    });

    wrap.querySelectorAll('.export-menu-item').forEach(function(btn) {
        btn.addEventListener('click', function() {
            const action = this.dataset.action;
            wrap.querySelector('.export-dropdown-toggle').setAttribute('aria-expanded', 'false');
            if (action === 'png') { exportModalAsPng(); }
            else if (action === 'csv') { exportModalAsCsv(); }
            else if (action === 'pdf') { exportModalAsPdf(); }
        });
    });

    const backBtn = modalHeader.querySelector('.chart-modal-back');
    if (backBtn && backBtn.parentNode) {
        backBtn.parentNode.insertBefore(wrap, backBtn.nextSibling);
    } else {
        modalHeader.appendChild(wrap);
    }
}

function exportMainKpiTable() {
    const table = document.querySelector('.main-kpi-table');
    if (!table) {
        showToast('KPI table not found.', 'main-table-missing');
        return;
    }

    const csv = tableToCsv(table);
    if (!csv) {
        showToast('No KPI table data to export.', 'main-table-empty');
        return;
    }

    const now = new Date();
    const stamp = now.getFullYear()
        + '-' + String(now.getMonth() + 1).padStart(2, '0')
        + '-' + String(now.getDate()).padStart(2, '0');
    downloadTextFile(csv, 'kpi-table-' + stamp + '.csv', 'text/csv;charset=utf-8;');
    showToast('KPI table exported as CSV.', 'main-table-exported');
}

function pdfMainKpiTable() {
    const table = document.querySelector('.main-kpi-table');
    if (!table) {
        showToast('KPI table not found.', 'main-table-missing-pdf');
        return;
    }

    if (!getJsPdfCtor()) {
        showToast('PDF library is not loaded. Refresh and try again.', 'kpi-table-pdf-lib-missing');
        return;
    }

    const now = new Date();
    const stamp = now.getFullYear()
        + '-' + String(now.getMonth() + 1).padStart(2, '0')
        + '-' + String(now.getDate()).padStart(2, '0');

    if (!saveTablePdf(table, 'KPI Table', 'kpi-table-' + stamp)) {
        showToast('Unable to export KPI table PDF.', 'kpi-table-pdf-failed');
        return;
    }

    showToast('KPI table exported as PDF.', 'kpi-table-pdf');
}

function initializeKpiTableExport() {
    const table = document.querySelector('.main-kpi-table');
    if (!table) {
        return;
    }

    const container = table.closest('.table-container');
    if (!container || container.parentNode.querySelector('#kpiTableExportWrap')) {
        return;
    }

    const actions = document.createElement('div');
    actions.className = 'kpi-table-export-actions';

    const wrap = document.createElement('div');
    wrap.id = 'kpiTableExportWrap';
    wrap.className = 'export-dropdown-wrap';
    wrap.innerHTML =
        '<button type="button" class="kpi-table-export-btn export-dropdown-toggle" aria-haspopup="true" aria-expanded="false">'
        + '<i class="fas fa-file-export"></i> Export Table <i class="fas fa-caret-down export-caret"></i>'
        + '</button>'
        + '<ul class="export-dropdown-menu kpi-table-export-menu" role="menu">'
        + '<li><button type="button" class="export-menu-item" data-action="csv"><i class="fas fa-file-csv"></i> CSV (data)</button></li>'
        + '<li><button type="button" class="export-menu-item" data-action="pdf"><i class="fas fa-file-pdf"></i> PDF</button></li>'
        + '</ul>';

    wrap.querySelector('.export-dropdown-toggle').addEventListener('click', function(e) {
        e.stopPropagation();
        const menu = wrap.querySelector('.export-dropdown-menu');
        const isOpen = menu.classList.contains('is-open');
        closeExportDropdowns();
        if (!isOpen) {
            menu.classList.add('is-open');
            this.setAttribute('aria-expanded', 'true');
        } else {
            this.setAttribute('aria-expanded', 'false');
        }
    });

    wrap.querySelectorAll('.export-menu-item').forEach(function(btn) {
        btn.addEventListener('click', function() {
            closeExportDropdowns();
            if (this.dataset.action === 'csv') {
                exportMainKpiTable();
            } else if (this.dataset.action === 'pdf') {
                pdfMainKpiTable();
            }
        });
    });

    actions.appendChild(wrap);
    container.parentNode.insertBefore(actions, container);
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
    expandedOptions.plugins.legend = { 
        display: true, 
        position: 'top',
        labels: { font: { size: 11 } }
    };
    expandedOptions.plugins.tooltip = expandedOptions.plugins.tooltip || { enabled: true };
    
    // Also improve axis font size in expanded view
    if (expandedOptions.scales) {
        if (expandedOptions.scales.x && expandedOptions.scales.x.ticks) {
            expandedOptions.scales.x.ticks.font = { size: 11 };
        }
        if (expandedOptions.scales.y && expandedOptions.scales.y.ticks) {
            expandedOptions.scales.y.ticks.font = { size: 11 };
        }
    }

    if (expandedChartInstance) {
        expandedChartInstance.destroy();
    }

    if (title) {
        title.textContent = resolveChartTitle(chartId);
    }

    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('drawer-open');
    expandedCanvas.dataset.chartId = chartId;

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
    const fullYear = Number(year);
    const monthName = monthShortName(month);

    if (monthEl) {
        monthEl.textContent = monthName;
    }
    if (yearEl) {
        yearEl.textContent = Number.isFinite(fullYear) ? String(fullYear).slice(-2) : '--';
    }

    const syncText = 'Synced period: ' + monthName + ' ' + (Number.isFinite(fullYear) ? fullYear : '----');
    if (monthElId === 'hsOverviewMonth') {
        setElementText('hsOverviewSyncText', syncText);
    } else if (monthElId === 'lsrOverviewMonth') {
        setElementText('lsrOverviewSyncText', syncText);
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
    document.body.classList.add('drawer-open');
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
    document.body.classList.add('drawer-open');
};

let selectedKpiMonth = new Date().getMonth() + 1;
let selectedKpiYear = new Date().getFullYear();
let selectedKpiDate = toLocalDateKey(new Date());
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

function applyInfoPortalButtons(config) {
    const containers = document.querySelectorAll('.kpi-footer, .info-portal-buttons');
    if (!containers.length || !config) {
        return;
    }

    function normalizeInfoPortalHref(url) {
        const trimmed = String(url || '').trim();
        if (!trimmed) {
            return '';
        }
        if (/^(https?:\/\/|mailto:|tel:|\/|#)/i.test(trimmed)) {
            return trimmed;
        }
        return 'https://' + trimmed;
    }

    const buttons = Array.isArray(config.buttons) ? config.buttons : [
        { id: 1, label: config.kpiButton1Label || config.button1Label, type: config.kpiButton1Type || config.button1Type, url: config.kpiButton1Url || config.button1Url },
        { id: 2, label: config.kpiButton2Label || config.button2Label, type: config.kpiButton2Type || config.button2Type, url: config.kpiButton2Url || config.button2Url }
    ];

    containers.forEach(function(container) {
        container.innerHTML = '';
        buttons.forEach(function(button) {
            const label = String((button && button.label) || '').trim();
            const type = String((button && button.type) || 'link').trim();
            const href = type === 'file'
                ? '/api/dashboard-config/info-portal/file/' + button.id
                : normalizeInfoPortalHref(button && button.url);
            if (!label || !href) {
                return;
            }
            const anchor = document.createElement('a');
            anchor.className = container.classList.contains('kpi-footer') ? 'kpi-footer-btn' : 'info-portal-btn';
            anchor.href = href;
            anchor.target = '_blank';
            anchor.textContent = label;
            container.appendChild(anchor);
        });
    });
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
            applyInfoPortalButtons(config);
            kpiDashboardMetaLoaded = true;
        })
        .catch(function() {
            setElementText('pmsDeckTitle', '-');
            renderLsrFocusRules([]);
            applyInfoPortalButtons({});
        });
}

const customChartContainerIds = {
    PEOPLE: 'peopleCustomCharts',
    QUALITY: 'qualityCustomCharts',
    SERVICE: 'serviceCustomCharts',
    COST: 'costCustomCharts'
};

const systemChartContainerIds = {
    PEOPLE: 'peopleSystemCharts',
    QUALITY: 'qualitySystemCharts',
    SERVICE: 'serviceSystemCharts',
    COST: 'costSystemCharts'
};

let customKpiDefinitions = [];
let customMetricDefinitionsRequest = null;
let systemMetricDefinitions = [];
let systemMetricDefinitionsRequest = null;

const fixedKpiTableConfig = [
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
        name: 'Consumer Complaint',
        unit: 'Units/MHL',
        actualField: 'kpiConsumerComplaintUnitsMhlFtdActual',
        targetField: 'kpiConsumerComplaintUnitsMhlFtdTarget',
        mtdField: 'kpiConsumerComplaintUnitsMhlMtdActual',
        targetMtdField: 'kpiConsumerComplaintUnitsMhlMtdTarget',
        ytdField: 'kpiConsumerComplaintUnitsMhlYtdActual',
        targetYtdField: 'kpiConsumerComplaintUnitsMhlYtdTarget',
        decimals: 0,
        higherIsBetter: false
    },
    {
        name: 'Customer Complaint',
        unit: 'Units/MHL',
        actualField: 'kpiCustomerComplaintUnitsMhlFtdActual',
        targetField: 'kpiCustomerComplaintUnitsMhlFtdTarget',
        mtdField: 'kpiCustomerComplaintUnitsMhlMtdActual',
        targetMtdField: 'kpiCustomerComplaintUnitsMhlMtdTarget',
        ytdField: 'kpiCustomerComplaintUnitsMhlYtdActual',
        targetYtdField: 'kpiCustomerComplaintUnitsMhlYtdTarget',
        decimals: 0,
        higherIsBetter: false
    },
    {
        name: 'Beer Loss',
        unit: 'HL',
        actualField: 'kpiBeerLossFtdActual',
        targetField: 'kpiBeerLossFtdTarget',
        mtdField: 'kpiBeerLossMtdActual',
        targetMtdField: 'kpiBeerLossMtdTarget',
        ytdField: 'kpiBeerLossYtdActual',
        targetYtdField: 'kpiBeerLossYtdTarget',
        decimals: 2,
        higherIsBetter: false
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
        name: 'RGB Ratio',
        unit: '-',
        actualField: 'kpiRgbRatioFtdActual',
        targetField: 'kpiRgbRatioFtdTarget',
        mtdField: 'kpiRgbRatioMtdActual',
        targetMtdField: 'kpiRgbRatioMtdTarget',
        ytdField: 'kpiRgbRatioYtdActual',
        targetYtdField: 'kpiRgbRatioYtdTarget',
        decimals: 0,
        higherIsBetter: true
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
        name: 'Process Confirmation - BP',
        unit: '%',
        actualField: 'processConfirmationBpFtdActual',
        targetField: 'processConfirmationBpFtdTarget',
        mtdField: 'processConfirmationBpMtdActual',
        targetMtdField: 'processConfirmationBpMtdTarget',
        ytdField: 'processConfirmationBpYtdActual',
        targetYtdField: 'processConfirmationBpYtdTarget',
        decimals: 0,
        higherIsBetter: true
    },
    {
        name: 'Process Confirmation - Pack',
        unit: '%',
        actualField: 'processConfirmationPackFtdActual',
        targetField: 'processConfirmationPackFtdTarget',
        mtdField: 'processConfirmationPackMtdActual',
        targetMtdField: 'processConfirmationPackMtdTarget',
        ytdField: 'processConfirmationPackYtdActual',
        targetYtdField: 'processConfirmationPackYtdTarget',
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

const systemKpiChartDefinitions = [
    { section: 'PEOPLE', chartId: 'productionProductivityChart', title: 'Production Productivity', actualField: 'productionProductivityFtdActual', targetField: 'productionProductivityFtdTarget', targetMtdField: 'productionProductivityMtdTarget' },
    { section: 'PEOPLE', chartId: 'logisticsProductivityChart', title: 'Logistics Productivity', actualField: 'logisticsProductivityFtdActual', targetField: 'logisticsProductivityFtdTarget', targetMtdField: 'logisticsProductivityMtdTarget' },
    { section: 'QUALITY', chartId: 'qualitySensoryChart', title: 'Internal Sensory Score', actualField: 'kpiSensoryScoreFtdActual', targetField: 'kpiSensoryScoreFtdTarget', targetMtdField: 'kpiSensoryScoreMtdTarget' },
    { section: 'QUALITY', chartId: 'consumerComplaintChart', title: 'Consumer Complaint', actualField: 'kpiConsumerComplaintUnitsMhlFtdActual', targetField: 'kpiConsumerComplaintUnitsMhlFtdTarget', targetMtdField: 'kpiConsumerComplaintUnitsMhlMtdTarget' },
    { section: 'QUALITY', chartId: 'customerComplaintChart', title: 'Customer Complaint', actualField: 'kpiCustomerComplaintUnitsMhlFtdActual', targetField: 'kpiCustomerComplaintUnitsMhlFtdTarget', targetMtdField: 'kpiCustomerComplaintUnitsMhlMtdTarget' },
    { section: 'SERVICE', chartId: 'noOfBrewsChart', title: 'No. of Brews & Volume', actualField: 'noOfBrewsFtdActual', targetField: 'noOfBrewsFtdTarget', targetMtdField: 'noOfBrewsMtdTarget' },
    { section: 'SERVICE', chartId: 'dispatchChart', title: 'Dispatch', actualField: 'dispatchFtdActual', targetField: 'dispatchFtdTarget', targetMtdField: 'dispatchMtdTarget' },
    { section: 'SERVICE', chartId: 'processConfirmationBpChart', title: 'Process Confirmation - BP', actualField: 'processConfirmationBpFtdActual', targetField: 'processConfirmationBpFtdTarget', targetMtdField: 'processConfirmationBpMtdTarget' },
    { section: 'SERVICE', chartId: 'processConfirmationPackChart', title: 'Process Confirmation - Pack', actualField: 'processConfirmationPackFtdActual', targetField: 'processConfirmationPackFtdTarget', targetMtdField: 'processConfirmationPackMtdTarget' },
    { section: 'SERVICE', chartId: 'serviceOeeChart', title: 'OEE', actualField: 'kpiOeeFtdActual', targetField: 'kpiOeeFtdTarget', targetMtdField: 'kpiOeeMtdTarget' },
    { section: 'SERVICE', chartId: 'serviceBeerLossChart', title: 'Beer Loss', actualField: 'kpiBeerLossFtdActual', targetField: 'kpiBeerLossFtdTarget', targetMtdField: 'kpiBeerLossMtdTarget' },
    { section: 'SERVICE', chartId: 'serviceWurChart', title: 'WUR', actualField: 'kpiWurHlHlFtdActual', targetField: 'kpiWurHlHlFtdTarget', targetMtdField: 'kpiWurHlHlMtdTarget' },
    { section: 'COST', chartId: 'costElectricityChart', title: 'Electricity', actualField: 'kpiElectricityKwhHlFtdActual', targetField: 'kpiElectricityKwhHlFtdTarget', targetMtdField: 'kpiElectricityKwhHlMtdTarget' },
    { section: 'COST', chartId: 'costEnergyChart', title: 'Energy', actualField: 'kpiEnergyKwhHlFtdActual', targetField: 'kpiEnergyKwhHlFtdTarget', targetMtdField: 'kpiEnergyKwhHlMtdTarget' },
    { section: 'COST', chartId: 'costRgbChart', title: 'RGB Ratio', actualField: 'kpiRgbRatioFtdActual', targetField: 'kpiRgbRatioFtdTarget', targetMtdField: 'kpiRgbRatioMtdTarget' }
];

const systemMetricKeyByActualField = {
    productionProductivityFtdActual: 'productionProductivity',
    logisticsProductivityFtdActual: 'logisticsProductivity',
    kpiSensoryScoreFtdActual: 'kpiSensoryScore',
    kpiConsumerComplaintUnitsMhlFtdActual: 'kpiConsumerComplaintUnitsMhl',
    kpiCustomerComplaintUnitsMhlFtdActual: 'kpiCustomerComplaintUnitsMhl',
    noOfBrewsFtdActual: 'noOfBrews',
    dispatchFtdActual: 'dispatch',
    processConfirmationBpFtdActual: 'processConfirmationBp',
    processConfirmationPackFtdActual: 'processConfirmationPack',
    kpiOeeFtdActual: 'kpiOee',
    kpiBeerLossFtdActual: 'kpiBeerLoss',
    kpiWurHlHlFtdActual: 'kpiWurHlHl',
    kpiElectricityKwhHlFtdActual: 'kpiElectricityKwhHl',
    kpiEnergyKwhHlFtdActual: 'kpiEnergyKwhHl',
    kpiRgbRatioFtdActual: 'kpiRgbRatio'
};

function normalizeDashboardSection(section) {
    return String(section || '').trim().toUpperCase();
}

function sortDashboardCustomDefinitions(definitions) {
    return (definitions || []).slice().sort(function(a, b) {
        const sectionCompare = normalizeDashboardSection(a.section).localeCompare(normalizeDashboardSection(b.section));
        if (sectionCompare !== 0) {
            return sectionCompare;
        }

        const orderA = Number.isFinite(Number(a.displayOrder)) ? Number(a.displayOrder) : 0;
        const orderB = Number.isFinite(Number(b.displayOrder)) ? Number(b.displayOrder) : 0;
        if (orderA !== orderB) {
            return orderA - orderB;
        }

        return Number(a.id || 0) - Number(b.id || 0);
    });
}

function getDashboardSystemDefinition(metricKey) {
    return systemMetricDefinitions.find(function(definition) {
        return String(definition.metricKey || '').toLowerCase() === String(metricKey || '').toLowerCase();
    }) || null;
}

function applySystemMetricDefinitionLabels() {
    fixedKpiTableConfig.forEach(function(kpi) {
        const metricKey = systemMetricKeyByActualField[kpi.actualField];
        const savedDefinition = getDashboardSystemDefinition(metricKey);
        if (!savedDefinition) {
            return;
        }
        if (savedDefinition.label) {
            kpi.name = savedDefinition.label;
        }
        if (savedDefinition.unit) {
            kpi.unit = savedDefinition.unit;
        }
        if (Number.isFinite(Number(savedDefinition.decimals))) {
            kpi.decimals = Number(savedDefinition.decimals);
        }
    });

    systemKpiChartDefinitions.forEach(function(chartDefinition) {
        const metricKey = systemMetricKeyByActualField[chartDefinition.actualField];
        const savedDefinition = getDashboardSystemDefinition(metricKey);
        if (savedDefinition && savedDefinition.label) {
            chartDefinition.title = savedDefinition.label;
        }
    });
}

function buildCustomMetricFieldRef(definitionId, valueKey) {
    return 'custom:' + definitionId + ':' + valueKey;
}

function getCustomChartCanvasId(definitionId) {
    return 'customMetricChart' + definitionId;
}

function normalizeKpiVisibilityFlag(value) {
    return value === undefined || value === null || value === true || String(value).toLowerCase() === 'true';
}

function getKpiTableVisibilityKey(rowKey) {
    return 'kpiTableVisibility_' + rowKey;
}

function isKpiTableVisible(rowKey) {
    return !rowKey || localStorage.getItem(getKpiTableVisibilityKey(rowKey)) !== 'hidden';
}

function getDefaultKpiTableRowKey(kpi) {
    const chartDefinition = systemKpiChartDefinitions.find(function(definition) {
        return definition.actualField === kpi.actualField;
    });
    const section = chartDefinition ? normalizeDashboardSection(chartDefinition.section).toLowerCase() : 'default';
    const rowId = chartDefinition && chartDefinition.chartId ? chartDefinition.chartId : kpi.name;
    return 'default_' + section + '_' + String(rowId || '').replace(/[^a-zA-Z0-9_-]/g, '_');
}

function buildCustomKpiTableConfig() {
    return customKpiDefinitions.filter(function(definition) {
        return normalizeKpiVisibilityFlag(definition.tableVisible) && isKpiTableVisible('custom_' + definition.id);
    }).map(function(definition) {
        const decimals = Number.isFinite(Number(definition.decimals)) ? Number(definition.decimals) : 2;
        return {
            name: definition.label || 'Custom Metric',
            unit: definition.unit || '-',
            actualField: buildCustomMetricFieldRef(definition.id, 'ftdActual'),
            targetField: buildCustomMetricFieldRef(definition.id, 'ftdTarget'),
            mtdField: buildCustomMetricFieldRef(definition.id, 'mtdActual'),
            targetMtdField: buildCustomMetricFieldRef(definition.id, 'mtdTarget'),
            ytdField: buildCustomMetricFieldRef(definition.id, 'ytdActual'),
            targetYtdField: buildCustomMetricFieldRef(definition.id, 'ytdTarget'),
            decimals: decimals,
            higherIsBetter: true
        };
    });
}

function getCombinedKpiTableConfig() {
    return fixedKpiTableConfig.filter(function(kpi) {
        return isKpiTableVisible(getDefaultKpiTableRowKey(kpi));
    }).concat(buildCustomKpiTableConfig());
}

function getCustomChartPalette(section) {
    switch (normalizeDashboardSection(section)) {
        case 'PEOPLE':
            return { bars: ['#1B5E20', '#43A047', '#2E7D32'], lines: ['#0a2e0a', '#66BB6A', '#1B5E20'] };
        case 'QUALITY':
            return { bars: ['#2E7D32', '#66BB6A', '#1B5E20'], lines: ['#1B5E20', '#43A047', '#2E7D32'] };
        case 'SERVICE':
            return { bars: ['#00695C', '#26A69A', '#004D40'], lines: ['#003D30', '#2E7D32', '#00695C'] };
        case 'COST':
            return { bars: ['#558B2F', '#AED581', '#33691E'], lines: ['#1B5E20', '#43A047', '#33691E'] };
        default:
            return { bars: ['#16A34A', '#22C55E', '#15803D'], lines: ['#064E1F', '#F59E0B', '#1F2937'] };
    }
}

function buildKpiChartBoxHtml(canvasId, title, cssClass, dataAttribute) {
    return '' +
        '<div class="chart-box ' + (cssClass || '') + '"' + (dataAttribute || '') + '>' +
            '<div class="chart-title">' +
                '<span class="chart-title-text">' + escapeHtmlText(title || 'KPI Metric') + '</span>' +
                '<span class="expand-icon" title="Expand" onclick="openChart(\'' + canvasId + '\')"><i class="fas fa-expand-alt"></i></span>' +
            '</div>' +
            '<div class="chart-container"><canvas id="' + canvasId + '"></canvas></div>' +
        '</div>';
}

function renderSystemChartContainers() {
    Object.keys(systemChartContainerIds).forEach(function(sectionKey) {
        const container = document.getElementById(systemChartContainerIds[sectionKey]);
        if (!container) {
            return;
        }

        container.innerHTML = systemKpiChartDefinitions
            .filter(function(definition) {
                return normalizeDashboardSection(definition.section) === sectionKey;
            })
            .map(function(definition) {
                return buildKpiChartBoxHtml(definition.chartId, definition.title, 'system-kpi-chart-box', ' data-chart-id="' + definition.chartId + '"');
            })
            .join('');
    });
}

function renderCustomChartContainers() {
    Object.keys(customChartContainerIds).forEach(function(sectionKey) {
        const container = document.getElementById(customChartContainerIds[sectionKey]);
        if (!container) {
            return;
        }

        const chartsMarkup = customKpiDefinitions
            .filter(function(definition) {
                return normalizeDashboardSection(definition.section) === sectionKey;
            })
            .map(function(definition) {
                const canvasId = getCustomChartCanvasId(definition.id);
                return buildKpiChartBoxHtml(canvasId, definition.label || 'Custom Metric', 'custom-kpi-chart-box', ' data-definition-id="' + definition.id + '"');
            })
            .join('');

        container.innerHTML = chartsMarkup;
    });
}

function loadCustomMetricDefinitions() {
    if (customMetricDefinitionsRequest) {
        return customMetricDefinitionsRequest;
    }

    customMetricDefinitionsRequest = $.ajax({
        url: '/api/metrics/custom-definitions',
        type: 'GET'
    }).done(function(data) {
        customKpiDefinitions = sortDashboardCustomDefinitions(Array.isArray(data) ? data : []);
        renderCustomChartContainers();
    }).fail(function() {
        customKpiDefinitions = [];
        renderCustomChartContainers();
    }).always(function() {
        customMetricDefinitionsRequest = null;
    });

    return customMetricDefinitionsRequest;
}

function loadSystemMetricDefinitions() {
    if (systemMetricDefinitionsRequest) {
        return systemMetricDefinitionsRequest;
    }

    systemMetricDefinitionsRequest = $.ajax({
        url: '/api/metrics/system-definitions',
        type: 'GET'
    }).done(function(data) {
        systemMetricDefinitions = Array.isArray(data) ? data : [];
        applySystemMetricDefinitionLabels();
        renderSystemChartContainers();
    }).fail(function() {
        systemMetricDefinitions = [];
        renderSystemChartContainers();
    }).always(function() {
        systemMetricDefinitionsRequest = null;
    });

    return systemMetricDefinitionsRequest;
}

function loadKpiMetricDefinitions() {
    return $.when(loadSystemMetricDefinitions(), loadCustomMetricDefinitions());
}

function loadProductionCharts(month, year, dateValue) {
    const safeMonth = Number.isInteger(month) ? month : (new Date().getMonth() + 1);
    const safeYear = Number.isInteger(year) ? year : new Date().getFullYear();
    const resolvedDate = dateValue || toLocalDateKey(new Date(safeYear, safeMonth - 1, 1));

    loadKpiDashboardMeta();
    updateSyncStatus('Syncing...');
    loadDailyPerformanceSummary(safeMonth, safeYear);
    updateKpiDeckMonthLabel(safeMonth, safeYear);

    $.ajax({
        url: '/api/production-metrics/month?month=' + safeMonth + '&year=' + safeYear,
        type: 'GET',
        success: function(metrics) {
            const safeMetrics = Array.isArray(metrics) ? metrics : [];

            const chartMetrics = safeMetrics.filter(function(m) {
                if (!m.date) return false;
                const dt = new Date(m.date);
                return (dt.getMonth() + 1) === safeMonth && dt.getFullYear() === safeYear;
            });

            renderAllCharts(chartMetrics);
            renderDailyPerformanceTable(safeMetrics, safeMonth, safeYear, resolvedDate);
            updateSyncStatus('Last synced: ' + formatTime(new Date()));
        },
        error: function() {
            renderNoDataForAllCharts('API unavailable');
            renderDailyPerformanceTable([], safeMonth, safeYear, resolvedDate);
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

function updateKpiHeaderDateDisplay(dateValue) {
    if (typeof window.setPmsHeaderDate === 'function') {
        window.setPmsHeaderDate(dateValue);
    } else {
        window.__pmsHeaderDateOverride = dateValue || '';
    }
}

function initializeKpiMonthFilter() {
    const $filter = $('#kpiMonthFilter');
    if (!$filter.length) {
        updateKpiHeaderDateDisplay(selectedKpiDate);
        loadProductionCharts(selectedKpiMonth, selectedKpiYear, selectedKpiDate);
        return;
    }

    $filter.val(selectedKpiDate);
    updateKpiHeaderDateDisplay(selectedKpiDate);

    $filter.on('change', function() {
        const selectedDateValue = $(this).val();
        if (!selectedDateValue) {
            return;
        }

        const selectedDate = new Date(selectedDateValue + 'T00:00:00');
        if (Number.isNaN(selectedDate.getTime())) {
            return;
        }

        selectedKpiDate = selectedDateValue;
        selectedKpiYear = selectedDate.getFullYear();
        selectedKpiMonth = selectedDate.getMonth() + 1;
        updateKpiHeaderDateDisplay(selectedKpiDate);
        loadProductionCharts(selectedKpiMonth, selectedKpiYear, selectedKpiDate);
    });

    loadProductionCharts(selectedKpiMonth, selectedKpiYear, selectedKpiDate);
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

    renderSystemMetricCharts(labels, metrics);
    renderCustomMetricCharts(labels, metrics);
    refreshKpiDashboardLayout();
}

function renderSystemMetricCharts(labels, metrics) {
    systemKpiChartDefinitions.forEach(function(definition) {
        renderKpiMixedChart(definition.chartId, labels, metrics, {
            actualSeries: [
                { label: 'Actual', key: definition.actualField }
            ],
            targetSeries: [
                {
                    labelPrefix: '',
                    ftdKey: definition.targetField,
                    mtdKey: definition.targetMtdField
                }
            ]
        });
    });
}

function renderCustomMetricCharts(labels, metrics) {
    customKpiDefinitions.forEach(function(definition) {
        const canvasId = getCustomChartCanvasId(definition.id);
        if (!normalizeKpiVisibilityFlag(definition.graphVisible)) {
            setKpiChartCardVisible(canvasId, false);
            return;
        }

        renderKpiMixedChart(canvasId, labels, metrics, {
            actualSeries: [
                { label: 'Actual', key: buildCustomMetricFieldRef(definition.id, 'ftdActual') }
            ],
            targetSeries: [
                {
                    labelPrefix: '',
                    ftdKey: buildCustomMetricFieldRef(definition.id, 'ftdTarget'),
                    mtdKey: buildCustomMetricFieldRef(definition.id, 'mtdTarget')
                }
            ]
        });
    });
}

const kpiChartPalettes = {
    peopleProductivityChart: {
        bars: ['#1B5E20', '#43A047', '#2E7D32'],
        lines: ['#0a2e0a', '#66BB6A', '#1B5E20']
    },
    productionProductivityChart: {
        bars: ['#1B5E20', '#43A047', '#2E7D32'],
        lines: ['#0a2e0a', '#66BB6A', '#1B5E20']
    },
    logisticsProductivityChart: {
        bars: ['#2E7D32', '#66BB6A', '#1B5E20'],
        lines: ['#1B5E20', '#43A047', '#2E7D32']
    },
    qualitySensoryChart: {
        bars: ['#2E7D32', '#4CAF50', '#1B5E20'],
        lines: ['#1B5E20', '#388E3C', '#2E7D32']
    },
    serviceProcessConfirmationChart: {
        bars: ['#388E3C', '#66BB6A', '#2E7D32'],
        lines: ['#1B5E20', '#43A047', '#2E7D32']
    },
    processConfirmationBpChart: {
        bars: ['#388E3C', '#66BB6A', '#2E7D32'],
        lines: ['#1B5E20', '#43A047', '#2E7D32']
    },
    processConfirmationPackChart: {
        bars: ['#43A047', '#81C784', '#2E7D32'],
        lines: ['#2E7D32', '#66BB6A', '#1B5E20']
    },
    qualityComplaintChart: {
        bars: ['#43A047', '#81C784', '#388E3C'],
        lines: ['#1B5E20', '#4CAF50', '#2E7D32']
    },
    consumerComplaintChart: {
        bars: ['#43A047', '#81C784', '#388E3C'],
        lines: ['#1B5E20', '#4CAF50', '#2E7D32']
    },
    customerComplaintChart: {
        bars: ['#558B2F', '#AED581', '#33691E'],
        lines: ['#2E7D32', '#7CB342', '#1B5E20']
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
    noOfBrewsChart: {
        bars: ['#0F766E', '#2DD4BF', '#115E59'],
        lines: ['#134E4A', '#14B8A6', '#0F766E']
    },
    dispatchChart: {
        bars: ['#166534', '#4ADE80', '#15803D'],
        lines: ['#14532D', '#22C55E', '#166534']
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
    if (String(canvasId || '').indexOf('customMetricChart') === 0) {
        const definitionId = Number(String(canvasId).replace('customMetricChart', ''));
        const definition = customKpiDefinitions.find(function(item) {
            return Number(item.id) === definitionId;
        });
        return getCustomChartPalette(definition && definition.section);
    }

    return kpiChartPalettes[canvasId] || {
        bars: ['#16A34A', '#22C55E', '#15803D'],
        lines: ['#064E1F', '#F59E0B', '#1F2937']
    };
}

function getKpiChartVisibilityKey(chartId) {
    return 'kpiChartVisibility_' + chartId;
}

function isKpiMetricGraphVisible(chartId) {
    return !chartId || localStorage.getItem(getKpiChartVisibilityKey(chartId)) !== 'hidden';
}

function setKpiChartCardVisible(canvasId, visible) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        return;
    }

    const card = canvas.closest('.chart-box');
    if (card) {
        card.classList.toggle('kpi-chart-hidden', !visible);
        card.hidden = !visible;
    }

    if (!visible && chartInstances[canvasId]) {
        chartInstances[canvasId].destroy();
        delete chartInstances[canvasId];
    }

    refreshKpiDashboardLayout();
}

function renderKpiMixedChart(canvasId, labels, metrics, seriesConfig) {
    const actualSeries = (seriesConfig.actualSeries || []).filter(function(series) {
        return isKpiMetricGraphVisible(series.metricChartId || canvasId);
    });
    const targetSeries = (seriesConfig.targetSeries || []).filter(function(series) {
        return isKpiMetricGraphVisible(series.metricChartId || canvasId);
    });
    const hasVisibleSeries = actualSeries.length > 0 || targetSeries.length > 0;
    setKpiChartCardVisible(canvasId, hasVisibleSeries);
    if (!hasVisibleSeries) {
        return;
    }

    const palette = getKpiPalette(canvasId);

    const datasets = [];

    actualSeries.forEach(function(series, index) {
        datasets.push(buildKpiDataset(series.label, metrics, series.key, 'actual', index, palette));
    });

    targetSeries.forEach(function(series, index) {
        const ftdData = readMetricSeries(metrics, series.ftdKey);
        const mtdData = readMetricSeries(metrics, series.mtdKey);
        const prefix = (series.labelPrefix || '').trim();
        const ftdLabel = prefix ? (prefix + ' FTD Target') : 'FTD Target';
        const mtdLabel = prefix ? (prefix + ' MTD Target') : 'MTD Target';
        const metricChartId = series.metricChartId || canvasId;

        const ftdDataset = buildKpiTargetDataset(ftdLabel, ftdData, palette.lines[index % palette.lines.length], mtdData, metricChartId);
        if (ftdDataset) {
            datasets.push(ftdDataset);
        }

        const mtdDataset = buildKpiTargetDataset(mtdLabel, mtdData, palette.lines[(index + 1) % palette.lines.length], null, metricChartId);
        if (mtdDataset) {
            datasets.push(mtdDataset);
        }
    });

    renderChart(canvasId, {
        type: 'bar',
        labels: labels,
        datasets: datasets
    });
}

function buildKpiDataset(label, metrics, key, seriesType, index, palette) {
    const data = readMetricSeries(metrics, key);

    const hasAnyValue = data.some(v => v !== null);
    if (!hasAnyValue) {
        return null;
    }

    const barColor = palette.bars[index % palette.bars.length];
    return {
        label,
        type: 'bar',
        data,
        backgroundColor: barColor,
        borderRadius: 2,
        maxBarThickness: 6,
        categoryPercentage: 0.8,
        barPercentage: 0.9
    };
}

function readMetricSeries(metrics, key) {
    if (!key) {
        return [];
    }

    return (metrics || []).map(function(metric) {
        return readMetricValue(metric, key);
    });
}

function readMetricValue(record, fieldName) {
    const value = readMetricRawValue(record, fieldName);
    if (value === null || value === undefined || value === '') {
        return null;
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
}

function readMetricRawValue(record, fieldName) {
    if (!record || !fieldName) {
        return null;
    }

    if (String(fieldName).indexOf('custom:') === 0) {
        const parts = String(fieldName).split(':');
        if (parts.length !== 3) {
            return null;
        }

        const definitionId = Number(parts[1]);
        const valueKey = parts[2];
        const customMetrics = Array.isArray(record.customMetrics) ? record.customMetrics : [];
        const match = customMetrics.find(function(item) {
            return Number(item.definitionId) === definitionId;
        });
        if (!match) {
            return null;
        }

        return match[valueKey];
    }

    return record[fieldName];
}

function findCrossingIndex(primaryData, compareData) {
    if (!Array.isArray(primaryData) || !Array.isArray(compareData)) {
        return -1;
    }

    const length = Math.min(primaryData.length, compareData.length);
    for (let i = 0; i < length; i++) {
        const primary = primaryData[i];
        const compare = compareData[i];
        if (primary === null || compare === null) {
            continue;
        }
        if (primary > compare) {
            return i;
        }
    }

    return -1;
}

function buildKpiTargetDataset(label, data, baseColor, compareWithFtdData, canvasId) {
    if (!Array.isArray(data) || data.length === 0 || !data.some(v => v !== null)) {
        return null;
    }

    const alertColor = (canvasId && localStorage.getItem('kpiCrossAlertColor_' + canvasId)) || '#DC2626';
    const crossingIndex = findCrossingIndex(data, compareWithFtdData);
    const colorByPoint = data.map(function(_, index) {
        if (crossingIndex !== -1 && index >= crossingIndex) {
            return alertColor;
        }
        return baseColor;
    });

    return {
        label: label,
        type: 'line',
        data: data,
        borderColor: colorByPoint,
        pointBackgroundColor: colorByPoint,
        pointBorderColor: colorByPoint,
        backgroundColor: 'transparent',
        tension: 0.35,
        borderWidth: 2,
        pointRadius: 3,
        pointHoverRadius: 4,
        fill: false,
        spanGaps: true,
        segment: {
            borderColor: function(ctx) {
                if (crossingIndex !== -1 && ctx.p0DataIndex >= crossingIndex) {
                    return alertColor;
                }
                return baseColor;
            }
        }
    };
}

function renderDailyPerformanceTable(metrics, selectedMonth, selectedYear, selectedDateValue) {
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

    let today = new Date(selectedYear, selectedMonth, 0);
    if (selectedDateValue) {
        const parsedSelectedDate = new Date(selectedDateValue + 'T00:00:00');
        if (!Number.isNaN(parsedSelectedDate.getTime()) && parsedSelectedDate.getFullYear() === selectedYear && (parsedSelectedDate.getMonth() + 1) === selectedMonth) {
            today = parsedSelectedDate;
        }
    }

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

    const todayTargetRecord = metricsByTargetDate.get(currentDateKey) || null;
    const yesterdayTargetRecord = metricsByTargetDate.get(yesterdayDateKey) || null;
    const yesterdayActualRecord = metricsByActualDate.get(yesterdayDateKey) || null;

    updateDailyPerformanceAsOf(todayTargetRecord ? todayTargetRecord.targetDate || todayTargetRecord.date : null, true);
    updateYesterdayDataDate(yesterdayActualRecord ? yesterdayActualRecord.date : null);
    updateDateLabel('todayTargetDate', todayTargetRecord ? (todayTargetRecord.targetDate || todayTargetRecord.date) : null);
    updateDateLabel('ftdYesterdayDate', yesterdayTargetRecord ? yesterdayTargetRecord.date : null);
    updateDateLabel('ftdTodayDate', todayTargetRecord ? todayTargetRecord.date : null);

    const rows = getCombinedKpiTableConfig().map(function(kpi, index) {
        const todayTarget = readNumber(todayTargetRecord, kpi.targetField);
        const yesterdayTarget = readNumber(yesterdayTargetRecord, kpi.targetField);
        const yesterdayActual = readNumber(yesterdayActualRecord, kpi.actualField);
        const targetMtdValue = readNumber(todayTargetRecord, kpi.targetMtdField || kpi.mtdField);
        const targetYtdValue = readNumber(todayTargetRecord, kpi.targetYtdField || kpi.ytdField);
        const actualMtdValue = readNumber(yesterdayActualRecord, kpi.mtdField);
        const actualYtdValue = readNumber(yesterdayActualRecord, kpi.ytdField);
        const todayTargetRaw = readMetricRawValue(todayTargetRecord, kpi.targetField);
        const yesterdayTargetRaw = readMetricRawValue(yesterdayTargetRecord, kpi.targetField);
        const yesterdayActualRaw = readMetricRawValue(yesterdayActualRecord, kpi.actualField);
        const targetMtdRaw = readMetricRawValue(todayTargetRecord, kpi.targetMtdField || kpi.mtdField);
        const targetYtdRaw = readMetricRawValue(todayTargetRecord, kpi.targetYtdField || kpi.ytdField);
        const actualMtdRaw = readMetricRawValue(yesterdayActualRecord, kpi.mtdField);
        const actualYtdRaw = readMetricRawValue(yesterdayActualRecord, kpi.ytdField);

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
                '<td' + classAttr(yesterdayTargetClass) + '>' + formatMetricRawValue(yesterdayTargetRaw, kpi.decimals) + '</td>' +
                '<td' + classAttr(todayTargetClass) + '>' + formatMetricRawValue(todayTargetRaw, kpi.decimals) + '</td>' +
                '<td' + classAttr(targetMtdClass) + '>' + formatMetricRawValue(targetMtdRaw, kpi.decimals) + '</td>' +
                '<td>' + formatMetricRawValue(targetYtdRaw, kpi.decimals) + '</td>' +
                '<td>' + formatMetricRawValue(yesterdayActualRaw, kpi.decimals) + '</td>' +
                '<td>' + formatMetricRawValue(actualMtdRaw, kpi.decimals) + '</td>' +
                '<td>' + formatMetricRawValue(actualYtdRaw, kpi.decimals) + '</td>' +
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

function formatDisplayDate(dateValue) {
    if (!dateValue) {
        return '';
    }

    const parsed = new Date(dateValue);
    if (Number.isNaN(parsed.getTime())) {
        return '';
    }

    return parsed.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
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

    asOf.textContent = 'As of: ' + formatDisplayDate(parsed);
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

    dateLabel.textContent = '[' + formatDisplayDate(parsed) + ']';
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
    el.textContent = '[' + formatDisplayDate(parsed) + ']';
}

function readNumber(record, fieldName) {
    return readMetricValue(record, fieldName);
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

function formatMetricRawValue(value, decimals) {
    if (value === null || value === undefined || value === '') {
        return '-';
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric.toFixed(decimals) : escapeHtmlText(value);
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
                    display: false,
                    position: 'top',
                    labels: { boxWidth: 10, font: { size: 8 } }
                },
                tooltip: {
                    enabled: true
                }
            },
            scales: {
                x: {
                    grid: { color: '#E5E7EB' },
                    ticks: { 
                        autoSkip: true, 
                        maxTicksLimit: 12,
                        font: { size: 8 }
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: { color: '#E5E7EB' },
                    ticks: {
                        font: { size: 8 }
                    }
                }
            }
        }
    });

    canvas.title = 'Click to expand chart';
}

function renderNoDataForAllCharts(message) {
    const ids = systemKpiChartDefinitions.map(function(definition) {
        return {
            id: definition.chartId,
            visible: isKpiMetricGraphVisible(definition.chartId)
        };
    }).concat(customKpiDefinitions.map(function(definition) {
        return {
            id: getCustomChartCanvasId(definition.id),
            visible: normalizeKpiVisibilityFlag(definition.graphVisible)
        };
    }));

    ids.forEach(function(item) {
        setKpiChartCardVisible(item.id, item.visible);
        if (item.visible) {
            renderNoData(item.id, message);
        }
        if (chartInstances[item.id]) {
            chartInstances[item.id].destroy();
            delete chartInstances[item.id];
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
