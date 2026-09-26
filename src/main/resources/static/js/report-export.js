(function (window, document) {
    'use strict';

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (character) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character];
        });
    }

    function isoDate(value) {
        if (!value) return '';
        var text = String(value).trim();
        var iso = text.match(/^(\d{4}-\d{2}-\d{2})/);
        if (iso) return iso[1];
        var dmy = text.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
        return dmy ? dmy[3] + '-' + dmy[2] + '-' + dmy[1] : '';
    }

    function cell(value) {
        var text = value == null ? '' : String(value);
        return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
    }

    function currentMonth() {
        var now = new Date();
        return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
    }

    function downloadCsv(columns, rows, filename) {
        var content = [columns.map(function (column) { return cell(column.label); }).join(',')]
            .concat(rows.map(function (row) {
                return columns.map(function (column) { return cell(text(column.value(row))); }).join(',');
            })).join('\r\n') + '\r\n';
        var blob = new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8;' });
        var url = URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    }

    function downloadPdf(config, rows, filename, filterLabel) {
        if (!window.PmsReport || typeof window.PmsReport.generate !== 'function') return false;
        window.PmsReport.generate({
            title: config.title,
            filterLabel: filterLabel,
            orientation: 'landscape',
            columns: config.columns.map(function (column) { return column.label; }),
            rows: rows.map(function (row) {
                return config.columns.map(function (column) { return text(column.value(row)); });
            }),
            styles: { fontSize: 7, cellPadding: 1.5, overflow: 'linebreak', valign: 'top' },
            headStyles: { fontSize: 7.5, cellPadding: 1.5, valign: 'middle' },
            filename: filename
        });
        return true;
    }

    function ensureModal() {
        if (document.getElementById('reportExportModal')) return;
        var modal = document.createElement('div');
        modal.id = 'reportExportModal';
        modal.className = 'report-export-backdrop';
        modal.setAttribute('aria-hidden', 'true');
        modal.innerHTML = '<section class="report-export-dialog" role="dialog" aria-modal="true" aria-labelledby="reportExportTitle">' +
            '<div class="report-export-heading"><div><h2 id="reportExportTitle">Download report data</h2><p>Choose a month or a date range for the exported records.</p></div>' +
            '<button type="button" class="report-export-close" data-export-cancel aria-label="Close">&times;</button></div>' +
            '<div class="report-export-mode"><label><input type="radio" name="reportExportMode" value="month" checked> Month</label>' +
            '<label><input type="radio" name="reportExportMode" value="range"> Date range</label></div>' +
            '<div class="report-export-fields"><label class="report-export-month">Month<input type="month" id="reportExportMonth"></label>' +
            '<label class="report-export-from" hidden>From<input type="date" id="reportExportFrom"></label>' +
            '<label class="report-export-to" hidden>To<input type="date" id="reportExportTo"></label></div>' +
            '<fieldset class="report-export-format"><legend>File format</legend><label><input type="radio" name="reportExportFormat" value="csv" checked> CSV</label>' +
            '<label><input type="radio" name="reportExportFormat" value="pdf"> PDF</label></fieldset>' +
            '<p class="report-export-error" id="reportExportError" aria-live="polite"></p>' +
            '<div class="report-export-footer"><button type="button" class="report-export-cancel" data-export-cancel>Cancel</button>' +
            '<button type="button" class="report-export-download" id="reportExportDownload"><i class="fas fa-download"></i> Download</button></div>' +
            '</section>';
        document.body.appendChild(modal);
        modal.addEventListener('change', function (event) {
            if (event.target.name !== 'reportExportMode') return;
            var isMonth = event.target.value === 'month';
            modal.querySelector('.report-export-month').hidden = !isMonth;
            modal.querySelector('.report-export-from').hidden = isMonth;
            modal.querySelector('.report-export-to').hidden = isMonth;
            modal.querySelector('#reportExportError').textContent = '';
        });
        modal.addEventListener('click', function (event) {
            if (event.target === modal || event.target.closest('[data-export-cancel]')) close();
        });
        modal.querySelector('#reportExportMonth').value = currentMonth();
        modal.querySelector('#reportExportDownload').addEventListener('click', function () {
            var mode = modal.querySelector('input[name="reportExportMode"]:checked').value;
            var month = modal.querySelector('#reportExportMonth').value;
            var from = modal.querySelector('#reportExportFrom').value;
            var to = modal.querySelector('#reportExportTo').value;
            var format = modal.querySelector('input[name="reportExportFormat"]:checked').value;
            var start = mode === 'month' ? month + '-01' : from;
            var end = mode === 'month' ? (month ? new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)).toISOString().slice(0, 10) : '') : to;
            var error = modal.querySelector('#reportExportError');
            if (!start || !end || (mode === 'range' && start > end)) {
                error.textContent = 'Choose a valid month or date range.';
                return;
            }
            var config = modal._exportConfig;
            var filtered = config.records().filter(function (record) {
                var date = isoDate(config.dateValue(record));
                return date && date >= start && date <= end;
            });
            if (!filtered.length) {
                error.textContent = 'No records were found for the selected dates.';
                return;
            }
            var stem = config.filename + '-' + start + (start === end ? '' : '-to-' + end);
            var filterLabel = mode === 'month' ? 'Month: ' + month : (start === end ? 'Date: ' + start : 'Date range: ' + start + ' to ' + end);
            if (format === 'pdf') {
                if (!downloadPdf(config, filtered, stem + '.pdf', filterLabel)) {
                    error.textContent = 'PDF export could not load. Refresh the page and try again.';
                    return;
                }
            } else {
                downloadCsv(config.columns, filtered, stem + '.csv');
            }
            close();
            if (window.PmsFeedback) window.PmsFeedback.show(filtered.length + ' records downloaded as ' + format.toUpperCase() + '.', 'success');
        });
        document.addEventListener('keydown', function (event) { if (event.key === 'Escape') close(); });
    }

    function close() {
        var modal = document.getElementById('reportExportModal');
        if (!modal) return;
        modal.classList.remove('is-open');
        modal.setAttribute('aria-hidden', 'true');
    }

    function open(config) {
        ensureModal();
        var modal = document.getElementById('reportExportModal');
        modal._exportConfig = config;
        modal.querySelector('#reportExportTitle').textContent = 'Download ' + config.title + ' data';
        modal.querySelector('#reportExportMonth').value = currentMonth();
        modal.querySelector('#reportExportFrom').value = '';
        modal.querySelector('#reportExportTo').value = '';
        modal.querySelector('input[name="reportExportMode"][value="month"]').checked = true;
        modal.querySelector('input[name="reportExportFormat"][value="csv"]').checked = true;
        modal.querySelector('.report-export-month').hidden = false;
        modal.querySelector('.report-export-from').hidden = true;
        modal.querySelector('.report-export-to').hidden = true;
        modal.querySelector('#reportExportError').textContent = '';
        modal.classList.add('is-open');
        modal.setAttribute('aria-hidden', 'false');
        modal.querySelector('#reportExportMonth').focus();
    }

    function text(value) {
        if (value == null) return '';
        if (Array.isArray(value)) return value.map(function (item) { return typeof item === 'object' && item ? Object.keys(item).map(function (key) { return item[key]; }).map(text).filter(Boolean).join(' / ') : text(item); }).filter(Boolean).join('; ');
        if (typeof value === 'object') return Object.keys(value).map(function (key) { return value[key]; }).map(text).filter(Boolean).join('; ');
        return String(value);
    }

    window.ReportExport = { open: open, text: text };
})(window, document);
