(function () {
    const bulkFields = [
        { key: 'productionProductivityFtdActual', label: 'Prod FTD Actual' },
        { key: 'productionProductivityFtdTarget', label: 'Prod FTD Target' },
        { key: 'logisticsProductivityFtdActual', label: 'Log FTD Actual' },
        { key: 'logisticsProductivityFtdTarget', label: 'Log FTD Target' },
        { key: 'kpiOeeFtdActual', label: 'OEE Actual' },
        { key: 'kpiOeeFtdTarget', label: 'OEE Target' },
        { key: 'kpiSensoryScoreFtdActual', label: 'Sensory Actual' },
        { key: 'kpiSensoryScoreFtdTarget', label: 'Sensory Target' },
        { key: 'kpiWurHlHlFtdActual', label: 'WUR Actual' },
        { key: 'kpiWurHlHlFtdTarget', label: 'WUR Target' },
        { key: 'kpiElectricityKwhHlFtdActual', label: 'Electricity Actual' },
        { key: 'kpiElectricityKwhHlFtdTarget', label: 'Electricity Target' },
        { key: 'kpiEnergyKwhHlFtdActual', label: 'Energy Actual' },
        { key: 'kpiEnergyKwhHlFtdTarget', label: 'Energy Target' },
        { key: 'kpiRgbRatioFtdActual', label: 'RGB Actual' },
        { key: 'kpiRgbRatioFtdTarget', label: 'RGB Target' },
        { key: 'kpiBeerLossFtdActual', label: 'Beer Loss Actual' },
        { key: 'kpiBeerLossFtdTarget', label: 'Beer Loss Target' },
        { key: 'kpiConsumerComplaintUnitsMhlFtdActual', label: 'Consumer Complaint Actual' },
        { key: 'kpiConsumerComplaintUnitsMhlFtdTarget', label: 'Consumer Complaint Target' },
        { key: 'kpiCustomerComplaintUnitsMhlFtdActual', label: 'Customer Complaint Actual' },
        { key: 'kpiCustomerComplaintUnitsMhlFtdTarget', label: 'Customer Complaint Target' }
    ];

    let currentMonthData = [];
    let editedRows = {};
    let hasInvalidInputs = false;

    document.addEventListener('DOMContentLoaded', function () {
        const bulkToggleBtn = document.getElementById('toggleBulkEditBtn');
        if (!bulkToggleBtn) {
            return;
        }

        const singleEntryView = document.getElementById('singleEntryView');
        const bulkEditView = document.getElementById('bulkEditView');
        const monthSelect = document.getElementById('bulkMonth');
        const yearSelect = document.getElementById('bulkYear');
        const loadBtn = document.getElementById('bulkLoadBtn');
        const saveBtn = document.getElementById('bulkSaveBtn');
        const cancelBtn = document.getElementById('bulkCancelBtn');

        initializeMonthYearSelectors(monthSelect, yearSelect);

        bulkToggleBtn.addEventListener('click', function () {
            const inBulkMode = bulkEditView.style.display !== 'none';
            if (inBulkMode) {
                bulkEditView.style.display = 'none';
                singleEntryView.style.display = 'block';
                bulkToggleBtn.innerHTML = '<i class="fas fa-table"></i><span>Bulk Edit</span>';
            } else {
                singleEntryView.style.display = 'none';
                bulkEditView.style.display = 'block';
                bulkToggleBtn.innerHTML = '<i class="fas fa-pen"></i><span>Single Entry</span>';
                loadMonthData();
            }
        });

        loadBtn.addEventListener('click', loadMonthData);
        saveBtn.addEventListener('click', saveBulkChanges);
        cancelBtn.addEventListener('click', function () {
            if (Object.keys(editedRows).length > 0) {
                showBulkMessage('bulkAlert', 'Reverted unsaved bulk changes.', 'success');
            }
            renderBulkTable(currentMonthData);
        });

        function loadMonthData() {
            const month = Number(monthSelect.value);
            const year = Number(yearSelect.value);
            fetch('/api/production-metrics/month?month=' + month + '&year=' + year)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('Failed to load data for selected month');
                    }
                    return response.json();
                })
                .then(function (rows) {
                    currentMonthData = Array.isArray(rows) ? rows : [];
                    renderBulkTable(currentMonthData);
                    showBulkMessage('bulkAlert', 'Loaded ' + currentMonthData.length + ' records.', 'success');
                })
                .catch(function () {
                    currentMonthData = [];
                    renderBulkTable([]);
                    showBulkMessage('bulkAlert', 'Failed to load monthly data.', 'error');
                });
        }

        function saveBulkChanges() {
            if (hasInvalidInputs) {
                showBulkMessage('bulkAlert', 'Fix invalid values before saving. Only non-negative numbers are allowed.', 'error');
                return;
            }

            const payload = Object.keys(editedRows).map(function (id) {
                const numericId = Number(id);
                return Object.assign({ id: numericId }, editedRows[id]);
            });

            if (payload.length === 0) {
                showBulkMessage('bulkAlert', 'No changes to save.', 'success');
                return;
            }

            fetch('/api/production-metrics/bulk-update', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('Bulk save failed');
                    }
                    return response.json();
                })
                .then(function (updated) {
                    const byId = {};
                    (updated || []).forEach(function (row) {
                        byId[row.id] = row;
                    });

                    currentMonthData = currentMonthData.map(function (row) {
                        return byId[row.id] ? byId[row.id] : row;
                    });

                    renderBulkTable(currentMonthData);
                    showBulkMessage('bulkAlert', 'Saved ' + payload.length + ' modified records successfully.', 'success');
                })
                .catch(function () {
                    showBulkMessage('bulkAlert', 'Failed to save bulk changes.', 'error');
                });
        }
    });

    function initializeMonthYearSelectors(monthSelect, yearSelect) {
        if (!monthSelect || !yearSelect) {
            return;
        }

        const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        monthNames.forEach(function (name, idx) {
            const option = document.createElement('option');
            option.value = String(idx + 1);
            option.textContent = name;
            monthSelect.appendChild(option);
        });

        const currentYear = new Date().getFullYear();
        for (let year = currentYear - 3; year <= currentYear + 1; year++) {
            const option = document.createElement('option');
            option.value = String(year);
            option.textContent = String(year);
            yearSelect.appendChild(option);
        }

        monthSelect.value = String(new Date().getMonth() + 1);
        yearSelect.value = String(currentYear);
    }

    function renderBulkTable(rows) {
        const tbody = document.getElementById('bulkEditTableBody');
        if (!tbody) {
            return;
        }

        editedRows = {};
        hasInvalidInputs = false;

        if (!Array.isArray(rows) || rows.length === 0) {
            tbody.innerHTML = '<tr><td colspan="23" style="text-align:center; padding:14px; color:#6b7280;">No data found for selected month.</td></tr>';
            return;
        }

        const html = rows.map(function (row) {
            const cells = bulkFields.map(function (field) {
                const val = row[field.key];
                const normalized = (val === null || val === undefined) ? '' : String(val);
                return '<td data-id="' + row.id + '" data-field="' + field.key + '">' +
                    '<input type="number" step="0.01" min="0" value="' + escapeAttr(normalized) + '" data-id="' + row.id + '" data-field="' + field.key + '">' +
                    '</td>';
            }).join('');

            return '<tr data-id="' + row.id + '">' +
                '<td>' + formatDate(row.date) + '</td>' +
                cells +
                '</tr>';
        }).join('');

        tbody.innerHTML = html;

        tbody.querySelectorAll('input[type="number"]').forEach(function (input) {
            input.addEventListener('input', onCellInput);
        });
    }

    function onCellInput(event) {
        const input = event.target;
        const rowId = Number(input.dataset.id);
        const field = input.dataset.field;
        const td = input.closest('td');

        if (!td) {
            return;
        }

        const baseRow = currentMonthData.find(function (r) { return r.id === rowId; });
        const originalValue = baseRow ? baseRow[field] : null;
        const raw = input.value.trim();

        td.classList.remove('dirty-cell');
        td.classList.remove('invalid-cell');

        if (raw === '') {
            markInvalid(td);
            upsertEditedValue(rowId, field, null);
            recalculateInvalidState();
            return;
        }

        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed < 0) {
            markInvalid(td);
            upsertEditedValue(rowId, field, null);
            recalculateInvalidState();
            return;
        }

        const originalNumeric = originalValue === null || originalValue === undefined ? null : Number(originalValue);
        if (originalNumeric !== null && Math.abs(originalNumeric - parsed) < 0.000001) {
            removeEditedValue(rowId, field);
        } else {
            td.classList.add('dirty-cell');
            upsertEditedValue(rowId, field, parsed);
        }

        recalculateInvalidState();
    }

    function upsertEditedValue(rowId, field, value) {
        if (!editedRows[rowId]) {
            editedRows[rowId] = {};
        }
        editedRows[rowId][field] = value;
    }

    function removeEditedValue(rowId, field) {
        if (!editedRows[rowId]) {
            return;
        }

        delete editedRows[rowId][field];
        if (Object.keys(editedRows[rowId]).length === 0) {
            delete editedRows[rowId];
        }
    }

    function markInvalid(td) {
        td.classList.add('dirty-cell');
        td.classList.add('invalid-cell');
    }

    function recalculateInvalidState() {
        const table = document.getElementById('bulkEditTableBody');
        hasInvalidInputs = !!(table && table.querySelector('td.invalid-cell'));
    }

    function formatDate(dateValue) {
        if (!dateValue) {
            return '-';
        }

        const parsed = new Date(dateValue);
        if (Number.isNaN(parsed.getTime())) {
            return '-';
        }

        const day = String(parsed.getDate()).padStart(2, '0');
        const month = String(parsed.getMonth() + 1).padStart(2, '0');
        const year = parsed.getFullYear();
        return day + '-' + month + '-' + year;
    }

    function escapeAttr(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function showBulkMessage(elementId, message, type) {
        const el = document.getElementById(elementId);
        if (!el) {
            return;
        }

        el.className = 'alert';
        el.classList.add(type === 'error' ? 'alert-error' : 'alert-success');
        el.classList.add('show');
        el.innerHTML = '<i class="fas ' + (type === 'error' ? 'fa-exclamation-circle' : 'fa-check-circle') + '"></i><div>' + message + '</div>';
    }
})();
