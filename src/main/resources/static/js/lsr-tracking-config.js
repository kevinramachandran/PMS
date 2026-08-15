/**
 * LSR Tracking Configuration Page
 * Manages daily LSR (Life Saving Rules) compliance status configuration
 */

(function () {
    const LSR_FIELDS = [
        { key: 'lsr1Status', label: 'LSR 1: Traffic Rules' },
        { key: 'lsr23Status', label: 'LSR 2&3: LOTO/Safeguard' },
        { key: 'lsr4Status', label: 'LSR 4: Working at Height' },
        { key: 'lsr5Status', label: 'LSR 5: Confined Space' },
        { key: 'contractorStatus', label: 'Contractor Mgmt' }
    ];

    let lastRequestId = 0;

    const STATUS_OPTIONS = ['Not Set', 'Safe', 'Unsafe'];
    const STATUS_VALUES = [null, 'SAFE', 'UNSAFE'];

    // State management: keyed by day (1-31)
    let lsrState = {};
    let currentYear = new Date().getFullYear();
    let currentMonth = new Date().getMonth() + 1;
    let maxDays = 31;

    function daysInMonth(year, month) {
        return new Date(year, month, 0).getDate();
    }

    function getYesterday() {
        const date = new Date();
        date.setDate(date.getDate() - 1);
        return date;
    }

    function formatDateValue(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    }

    function parseDateValue(value) {
        const parts = String(value || '').split('-');
        if (parts.length !== 3) return null;
        const year = parseInt(parts[0], 10);
        const month = parseInt(parts[1], 10);
        const day = parseInt(parts[2], 10);
        if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
            return null;
        }
        return { year, month, day };
    }

    function getLsrStatusClass(value) {
        if (!value) return 'lsr-status-notset';
        return value === 'SAFE' ? 'lsr-status-safe' : 'lsr-status-unsafe';
    }

    function applyLsrSelectClass(selectEl, value) {
        selectEl.classList.remove('lsr-status-notset', 'lsr-status-safe', 'lsr-status-unsafe');
        selectEl.classList.add(getLsrStatusClass(value));
    }

    function setLoading(isLoading) {
        const dateInput = document.getElementById('lsrDateInput');
        const saveBtn = document.getElementById('saveLsrBtn');
        const tbody = document.getElementById('lsrTableBody');

        if (dateInput) dateInput.disabled = isLoading;
        if (saveBtn) saveBtn.disabled = isLoading;

        if (isLoading && tbody) {
            tbody.innerHTML = `<tr><td colspan="6" class="lsr-loading-cell">Loading data\u2026</td></tr>`;
        }
    }

    function buildTable() {
        const tbody = document.getElementById('lsrTableBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        lsrState = {};

        for (let day = 1; day <= maxDays; day++) {
            lsrState[day] = {};
            LSR_FIELDS.forEach(field => {
                lsrState[day][field.key] = null;
            });

            const tr = document.createElement('tr');
            const dayCell = document.createElement('td');
            dayCell.textContent = day;
            dayCell.className = 'lsr-day-cell';
            tr.appendChild(dayCell);

            LSR_FIELDS.forEach(field => {
                const td = document.createElement('td');
                const select = document.createElement('select');
                select.className = 'lsr-status-select lsr-inline-select lsr-status-notset';
                select.dataset.day = day;
                select.dataset.field = field.key;

                STATUS_OPTIONS.forEach((opt, idx) => {
                    const option = document.createElement('option');
                    option.value = idx === 0 ? '' : STATUS_VALUES[idx];
                    option.textContent = opt;
                    select.appendChild(option);
                });

                select.addEventListener('change', onFieldChange);
                td.appendChild(select);
                tr.appendChild(td);
            });

            tbody.appendChild(tr);
        }
    }

    function onFieldChange(e) {
        const select = e.target;
        const day = parseInt(select.dataset.day, 10);
        const field = select.dataset.field;
        const value = select.value || null;

        if (!lsrState[day]) lsrState[day] = {};
        lsrState[day][field] = value;
        applyLsrSelectClass(select, value);
    }

    function loadLsrData() {
        const dateInput = document.getElementById('lsrDateInput');
        if (!dateInput) return;

        const yesterdayValue = formatDateValue(getYesterday());
        dateInput.max = yesterdayValue;
        if (!dateInput.value || dateInput.value > yesterdayValue) {
            dateInput.value = yesterdayValue;
        }

        const selected = parseDateValue(dateInput.value);
        if (!selected) return;

        currentYear = selected.year;
        currentMonth = selected.month;
        maxDays = Math.min(selected.day, daysInMonth(currentYear, currentMonth));

        setLoading(true);
        const requestId = ++lastRequestId;

        buildTable();

        const url = `/api/lsr-daily/${currentYear}/${currentMonth}`;
        fetch(url)
            .then(r => {
                if (!r.ok) throw new Error('Network response was not ok');
                return r.json();
            })
            .then(data => {
                if (requestId !== lastRequestId) return;
                if (Array.isArray(data)) {
                    data.forEach(record => {
                        const day = record.day;
                        if (!lsrState[day]) lsrState[day] = {};
                        LSR_FIELDS.forEach(field => {
                            const value = record[field.key];
                            lsrState[day][field.key] = value || null;
                        });
                    });
                }
                updateTableUI();
            })
            .catch(err => {
                if (requestId !== lastRequestId) return;
                console.error('Error loading LSR data:', err);
                showMessage('Error loading LSR data', 'error');
                updateTableUI();
            })
            .finally(() => {
                if (requestId === lastRequestId) setLoading(false);
            });
    }

    function updateTableUI() {
        const selects = document.querySelectorAll('.lsr-status-select');
        selects.forEach(select => {
            const day = parseInt(select.dataset.day, 10);
            const field = select.dataset.field;
            const value = lsrState[day] && lsrState[day][field] ? lsrState[day][field] : '';
            select.value = value;
            applyLsrSelectClass(select, value || null);
        });
    }

    function saveLsrData() {
        const changes = [];

        Object.keys(lsrState).forEach(dayStr => {
            const day = parseInt(dayStr, 10);
            const hasAnyValue = LSR_FIELDS.some(field => !!(lsrState[day] && lsrState[day][field.key]));
            if (!hasAnyValue) return;

            const record = {
                year: currentYear,
                month: currentMonth,
                day: day,
                lsr1Status: lsrState[day].lsr1Status || null,
                lsr23Status: lsrState[day].lsr23Status || null,
                lsr4Status: lsrState[day].lsr4Status || null,
                lsr5Status: lsrState[day].lsr5Status || null,
                contractorStatus: lsrState[day].contractorStatus || null
            };
            changes.push(record);
        });

        if (changes.length === 0) {
            showMessage('No changes to save', 'warning');
            return;
        }

        fetch('/api/lsr-daily/batch', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(changes)
        })
            .then(r => {
                if (!r.ok) throw new Error('Failed to save LSR data');
                return r.json();
            })
            .then(() => {
                showMessage('LSR data saved successfully!', 'success');
                localStorage.setItem('lsr-tracking-update', new Date().getTime());
            })
            .catch(err => {
                console.error('Error saving LSR data:', err);
                showMessage('Error saving LSR data', 'error');
            });
    }

    function showMessage(text, type) {
        const msgEl = document.getElementById('lsrMessage');
        if (!msgEl) return;
        msgEl.textContent = text;
        msgEl.className = 'form-message show ' + type;
        setTimeout(() => {
            msgEl.textContent = '';
            msgEl.className = 'form-message';
        }, 4000);
    }

    function init() {
        const dateInput = document.getElementById('lsrDateInput');
        if (dateInput) {
            const yesterdayValue = formatDateValue(getYesterday());
            dateInput.max = yesterdayValue;
            dateInput.value = yesterdayValue;
            dateInput.addEventListener('change', loadLsrData);
        }

        const saveBtn = document.getElementById('saveLsrBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', saveLsrData);
        }

        const lsrItem = document.querySelector('[data-config="lsr-tracking"]');
        if (lsrItem) {
            lsrItem.addEventListener('click', function () {
                setTimeout(loadLsrData, 100);
            });
        }

        window.addEventListener('lsr-tracking-open', function () {
            setTimeout(loadLsrData, 50);
        });

        window.addEventListener('load', loadLsrData);
    }

    document.addEventListener('DOMContentLoaded', init);
})();
