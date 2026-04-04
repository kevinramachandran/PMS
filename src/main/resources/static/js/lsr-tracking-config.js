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

    function daysInMonth(year, month) {
        return new Date(year, month, 0).getDate();
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
        const yearSel = document.getElementById('lsrYearSel');
        const monthSel = document.getElementById('lsrMonthSel');
        const saveBtn = document.getElementById('saveLsrBtn');
        const tbody = document.getElementById('lsrTableBody');

        if (yearSel) yearSel.disabled = isLoading;
        if (monthSel) monthSel.disabled = isLoading;
        if (saveBtn) saveBtn.disabled = isLoading;

        if (isLoading && tbody) {
            tbody.innerHTML = `<tr><td colspan="6" class="lsr-loading-cell">Loading data\u2026</td></tr>`;
        }
    }

    function initYearDropdown() {
        const yearSel = document.getElementById('lsrYearSel');
        if (!yearSel) return;

        yearSel.innerHTML = '';
        const startYear = 2020;
        const endYear = new Date().getFullYear() + 1;
        for (let y = startYear; y <= endYear; y++) {
            const opt = document.createElement('option');
            opt.value = y;
            opt.textContent = y;
            yearSel.appendChild(opt);
        }
        yearSel.value = currentYear;
    }

    function buildTable() {
        const tbody = document.getElementById('lsrTableBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        lsrState = {};
        const maxDays = daysInMonth(currentYear, currentMonth);

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
        const yearSel = document.getElementById('lsrYearSel');
        if (!yearSel) return;

        currentYear = parseInt(yearSel.value, 10);
        const monthSel = document.getElementById('lsrMonthSel');
        currentMonth = parseInt(monthSel.value, 10);

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
        initYearDropdown();

        const monthSel = document.getElementById('lsrMonthSel');
        if (monthSel) {
            monthSel.value = String(currentMonth);
        }

        const yearSel = document.getElementById('lsrYearSel');
        if (yearSel) yearSel.addEventListener('change', loadLsrData);
        if (monthSel) monthSel.addEventListener('change', loadLsrData);

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
