/**
 * Shared PDF report generator for Carlsberg PMS pages.
 * Requires jsPDF + jsPDF-AutoTable to be loaded on the page before this script.
 */
(function (window) {
    'use strict';

    var CARLSBERG_LOGO = '/images/carlsbergWhiteLogo.png'; //src\main\resources\static\images\carlsbergWhiteLogo.png
    var HEADER_COLOR   = [0, 61, 36];   // Carlsberg dark green
    var ALT_ROW_COLOR  = [240, 247, 240];

    /**
     * Load an image src as a PNG data URL via Canvas.
     * @param {string} src
     * @param {function} callback  receives {dataUrl, width, height} or null on failure
     */
    function loadImageAsDataUrl(src, callback) {
        var img = new Image();
        img.crossOrigin = 'anonymous';
        img.onload = function () {
            try {
                var canvas = document.createElement('canvas');
                canvas.width  = img.naturalWidth;
                canvas.height = img.naturalHeight;
                canvas.getContext('2d').drawImage(img, 0, 0);
                callback({
                    dataUrl: canvas.toDataURL('image/png'),
                    width: img.naturalWidth,
                    height: img.naturalHeight
                });
            } catch (e) {
                callback(null);
            }
        };
        img.onerror = function () { callback(null); };
        img.src = src;
    }

    function fitImageInBox(width, height, maxWidth, maxHeight) {
        if (!width || !height) {
            return { width: maxWidth, height: maxHeight };
        }

        var scale = Math.min(maxWidth / width, maxHeight / height);
        return {
            width: width * scale,
            height: height * scale
        };
    }

    /**
     * Draw the Carlsberg branded report header.
     * Returns the Y position after the header (ready for the table).
     */
    function drawHeader(doc, logoAsset, title, filterLabel) {
        var pageW = doc.internal.pageSize.getWidth();
        var leftMargin = 10;
        var rightMargin = 10;
        var logoBounds = { maxWidth: 38, maxHeight: 15 };
        var y = 10;
        var logoSize = logoAsset && logoAsset.dataUrl
            ? fitImageInBox(logoAsset.width, logoAsset.height, logoBounds.maxWidth, logoBounds.maxHeight)
            : null;
        var headerContentHeight = Math.max(logoSize ? logoSize.height : 0, 12);

        // Logo (top-left)
        if (logoSize) {
            var logoY = y + ((headerContentHeight - logoSize.height) / 2);
            doc.addImage(logoAsset.dataUrl, 'PNG', leftMargin, logoY, logoSize.width, logoSize.height);
        }

        // Report title (centre)
        doc.setFont('helvetica', 'bold');
        doc.setFontSize(16);
        doc.setTextColor(0, 61, 36);
        doc.text(title, pageW / 2, y + (headerContentHeight / 2) + 1.5, { align: 'center' });
        doc.setTextColor(0, 0, 0);

        y += headerContentHeight + 5;

        // Filter / period sub-line
        if (filterLabel) {
            doc.setFont('helvetica', 'normal');
            doc.setFontSize(10);
            doc.setTextColor(80, 80, 80);
            doc.text(filterLabel, pageW / 2, y, { align: 'center' });
            doc.setTextColor(0, 0, 0);
            y += 7;
        }

        // Generated date (right-aligned)
        doc.setFontSize(8);
        doc.setTextColor(140, 140, 140);
        doc.text(
            'Generated: ' + new Date().toLocaleString('en-GB'),
            pageW - rightMargin, y, { align: 'right' }
        );
        doc.setTextColor(0, 0, 0);
        y += 6;

        // Thin separator line
        doc.setDrawColor(0, 61, 36);
        doc.setLineWidth(0.5);
        doc.line(leftMargin, y, pageW - rightMargin, y);
        y += 4;

        return y;
    }

    /**
     * Main API: generate a table-based PDF report.
     *
     * config = {
     *   title:         string        – report heading
     *   filterLabel:   string|null   – e.g. "Month: Apr 2026"
     *   orientation:   'landscape'|'portrait'
     *   columns:       string[]      – header row
     *   rows:          string[][]    – data rows
     *   filename:      string        – output filename (e.g. 'issue-board.pdf')
     *   extraImages:   [{dataUrl, x, y, w, h, caption}]  (optional, added after table)
     * }
     */
    function generateTableReport(config) {
        loadImageAsDataUrl(CARLSBERG_LOGO, function (logoAsset) {
            var jsPDF = window.jspdf ? window.jspdf.jsPDF : window.jsPDF;
            var doc = new jsPDF({ orientation: config.orientation || 'landscape', unit: 'mm', format: 'a4' });

            var startY = drawHeader(doc, logoAsset, config.title, config.filterLabel || null);

            doc.autoTable({
                startY: startY,
                head: [config.columns],
                body: config.rows,
                styles:           { fontSize: 8, cellPadding: 2, overflow: 'linebreak' },
                headStyles:       { fillColor: HEADER_COLOR, textColor: 255, fontStyle: 'bold', fontSize: 9 },
                alternateRowStyles: { fillColor: ALT_ROW_COLOR },
                margin:           { left: 10, right: 10 },
                tableLineColor:   [200, 215, 200],
                tableLineWidth:   0.2
            });

            // Optional chart images appended after the table
            if (config.extraImages && config.extraImages.length > 0) {
                var pageW = doc.internal.pageSize.getWidth();
                var pageH = doc.internal.pageSize.getHeight();
                var tableEndY = doc.lastAutoTable ? doc.lastAutoTable.finalY : startY;
                var imgY = tableEndY + 8;

                config.extraImages.forEach(function (img) {
                    var w = img.w || 120;
                    var h = img.h || 60;
                    // New page if not enough vertical space
                    if (imgY + h + 10 > pageH) {
                        doc.addPage();
                        imgY = 15;
                    }
                    var x = img.x !== undefined ? img.x : (pageW - w) / 2;
                    if (img.caption) {
                        doc.setFont('helvetica', 'bold');
                        doc.setFontSize(10);
                        doc.setTextColor(0, 61, 36);
                        doc.text(img.caption, x, imgY);
                        doc.setTextColor(0, 0, 0);
                        imgY += 5;
                    }
                    if (img.dataUrl) {
                        doc.addImage(img.dataUrl, 'PNG', x, imgY, w, h);
                    }
                    imgY += h + 8;
                });
            }

            doc.save(config.filename || 'report.pdf');
        });
    }

    /**
     * Helper: read a DOM table (#tableId) into columns[] + rows[][]
     * Strips HTML tags, trims whitespace.
     */
    function readDomTable(tableId) {
        var table   = document.getElementById(tableId);
        var columns = [];
        var rows    = [];
        if (!table) return { columns: columns, rows: rows };

        var headRow = table.querySelector('thead tr');
        if (headRow) {
            [].forEach.call(headRow.querySelectorAll('th'), function (th) {
                columns.push(th.textContent.trim());
            });
        }

        var bodyRows = table.querySelectorAll('tbody tr');
        [].forEach.call(bodyRows, function (tr) {
            var cells = tr.querySelectorAll('td');
            // Skip colspan "empty" rows (single td with colspan = all columns)
            if (cells.length === 1 && cells[0].getAttribute('colspan')) return;
            var row = [];
            [].forEach.call(cells, function (td) {
                row.push(td.textContent.trim());
            });
            rows.push(row);
        });

        return { columns: columns, rows: rows };
    }

    /**
     * Helper: capture a <canvas> element as a PNG data URL.
     */
    function captureCanvas(canvasId) {
        var canvas = document.getElementById(canvasId);
        if (!canvas) return null;
        try { return canvas.toDataURL('image/png'); } catch (e) { return null; }
    }

    // Public API
    window.PmsReport = {
        generate:        generateTableReport,
        readDomTable:    readDomTable,
        captureCanvas:   captureCanvas
    };

})(window);
