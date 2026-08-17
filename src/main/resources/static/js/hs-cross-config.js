/* H&S Cross Daily - Settings Configuration Script */
(function () {
    const ACCIDENT_OPTIONS = [
        { value: 'ZERO', label: 'Zero Accidents' },
        { value: 'WITH_LOST', label: 'Accidents' }
    ];

    const NEAR_MISS_OPTIONS = [
        { value: '', label: 'Not Set' },
        { value: 'NONE', label: 'None' },
        { value: 'OCCURRED', label: 'Occurred' }
    ];

    const SAFETY_OPTIONS = [
        { value: '', label: 'Not Set' },
        { value: 'NONE', label: 'None' },
        { value: 'OCCURRED', label: 'Occurred' }
    ];

    let hsState = {};
    let currentYear = new Date().getFullYear();
    let currentMonth = new Date().getMonth() + 1;
    let maxDays = 31;
    let lastRequestId = 0;

    function daysInMonth(y, m) {
        return new Date(y, m, 0).getDate();
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
        if (parts.length !== 3) {
            return null;
        }
        const year = parseInt(parts[0], 10);
        const month = parseInt(parts[1], 10);
        const day = parseInt(parts[2], 10);
        if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
            return null;
        }
        return { year, month, day };
    }

    function formatRecordDate(year, month, day) {
        return year + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
    }

    function csvCell(value) {
        const text = value == null ? '' : String(value);
        return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
    }

    function downloadCsv(csv, filename) {
        const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    function getMessageElement() {
        return document.getElementById('hsCrossMessage');
    }

    function showMessage(text, type) {
        const msgEl = getMessageElement();
        if (!msgEl) return;
        msgEl.textContent = text;
        msgEl.className = 'form-message show ' + type;
    }

    function getStatusClass(field, value) {
        if (!value) return 'hs-status-notset';
        if (field === 'accidentStatus') {
            return value === 'ZERO' ? 'hs-status-safe' : 'hs-status-issue';
        }
        return value === 'NONE' ? 'hs-status-safe' : 'hs-status-issue';
    }

    function normalizeAccidentStatus(value) {
        if (value === 'WITH_LOST' || value === 'WITHOUT_LOST') {
            return 'WITH_LOST';
        }
        return 'ZERO';
    }

    function applySelectStatusClass(selectEl, field, value) {
        if (!selectEl) return;
        selectEl.classList.remove('hs-status-notset', 'hs-status-safe', 'hs-status-issue');
        if (field === 'accidentStatus') {
            selectEl.classList.add('hs-status-notset');
            return;
        }
        selectEl.classList.add(getStatusClass(field, value));
    }

    function setLoading(isLoading) {
        const saveBtn = document.getElementById('saveHsCrossBtn');
        const downloadBtn = document.getElementById('downloadHsCrossCsvBtn');
        const dateInput = document.getElementById('hsCrossDateInput');

        if (saveBtn) saveBtn.disabled = isLoading;
        if (downloadBtn) downloadBtn.disabled = isLoading;
        if (dateInput) dateInput.disabled = isLoading;

        if (isLoading) {
            showMessage('Loading...', 'info');
            const tbody = document.getElementById('hsCrossTableBody');
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="4" class="hs-loading-cell">Loading...</td></tr>';
            }
        }
    }

    function createStatusSelect(day, field, value, options) {
        const select = document.createElement('select');
        select.className = 'form-select hs-inline-select';
        select.dataset.day = day;
        select.dataset.field = field;

        options.forEach(opt => {
            const optEl = document.createElement('option');
            optEl.value = opt.value;
            optEl.textContent = opt.label;
            select.appendChild(optEl);
        });

        select.value = field === 'accidentStatus' ? normalizeAccidentStatus(value) : (value || '');
        applySelectStatusClass(select, field, select.value);
        select.addEventListener('change', onFieldChange);

        return select;
    }

    function buildTable() {
        const tbody = document.getElementById('hsCrossTableBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        for (let day = 1; day <= maxDays; day++) {
            const entry = hsState[day] || {};
            const row = document.createElement('tr');

            const dayCell = document.createElement('td');
            dayCell.textContent = day;
            dayCell.className = 'hs-day-cell';
            row.appendChild(dayCell);

            const accidentCell = document.createElement('td');
            accidentCell.appendChild(createStatusSelect(day, 'accidentStatus', entry.accidentStatus, ACCIDENT_OPTIONS));
            row.appendChild(accidentCell);

            const nearMissCell = document.createElement('td');
            nearMissCell.appendChild(createStatusSelect(day, 'nearMissStatus', entry.nearMissStatus, NEAR_MISS_OPTIONS));
            row.appendChild(nearMissCell);

            const safetyCell = document.createElement('td');
            safetyCell.appendChild(createStatusSelect(day, 'safetyConcernStatus', entry.safetyConcernStatus, SAFETY_OPTIONS));
            row.appendChild(safetyCell);

            tbody.appendChild(row);
        }
    }

    function onFieldChange(e) {
        const select = e.currentTarget;
        const day = parseInt(select.dataset.day, 10);
        const field = select.dataset.field;
        const value = select.value || null;

        if (!hsState[day]) hsState[day] = {};
        hsState[day][field] = value;
        applySelectStatusClass(select, field, select.value);
    }

    function loadHsCrossData() {
        const dateInput = document.getElementById('hsCrossDateInput');
        if (!dateInput) return;

        const yesterdayValue = formatDateValue(getYesterday());
        dateInput.max = yesterdayValue;
        if (!dateInput.value || dateInput.value > yesterdayValue) {
            dateInput.value = yesterdayValue;
        }

        const selected = parseDateValue(dateInput.value);
        if (!selected) {
            return;
        }

        currentYear = selected.year;
        currentMonth = selected.month;
        maxDays = Math.min(selected.day, daysInMonth(currentYear, currentMonth));
        hsState = {};

        const requestId = ++lastRequestId;
        setLoading(true);

        fetch('/api/hs-daily/' + currentYear + '/' + currentMonth)
            .then(r => r.json())
            .then(data => {
                if (requestId !== lastRequestId) return;
                data.forEach(entry => {
                    hsState[entry.day] = {
                        id: entry.id,
                        accidentStatus: normalizeAccidentStatus(entry.accidentStatus),
                        nearMissStatus: entry.nearMissStatus || null,
                        safetyConcernStatus: entry.safetyConcernStatus || null
                    };
                });
                buildTable();
                showMessage('Data loaded successfully up to selected date.', 'info');
            })
            .catch(() => {
                if (requestId !== lastRequestId) return;
                buildTable();
                showMessage('Unable to fetch data. Showing editable blank table.', 'warning');
            })
            .finally(() => {
                if (requestId === lastRequestId) {
                    setLoading(false);
                }
            });
    }

    function saveHsCrossData() {
        const batch = [];
        for (let day = 1; day <= maxDays; day++) {
            const entry = hsState[day];
            if (entry && (entry.accidentStatus || entry.nearMissStatus || entry.safetyConcernStatus)) {
                batch.push({
                    id: entry.id || null,
                    year: currentYear,
                    month: currentMonth,
                    day: day,
                    accidentStatus: entry.accidentStatus || null,
                    nearMissStatus: entry.nearMissStatus || null,
                    safetyConcernStatus: entry.safetyConcernStatus || null
                });
            }
        }

        if (batch.length === 0) {
            showMessage('No data to save.', 'warning');
            return;
        }

        fetch('/api/hs-daily/batch', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(batch)
        })
        .then(r => {
            if (!r.ok) throw new Error('Save failed');
            return r.json();
        })
        .then(saved => {
            saved.forEach(s => {
                if (!hsState[s.day]) hsState[s.day] = {};
                hsState[s.day].id = s.id;
            });
            showMessage('H&S Cross data saved successfully (' + saved.length + ' days).', 'success');
            localStorage.setItem('hs-cross-update', Date.now().toString());
        })
        .catch(() => {
            showMessage('Error saving data. Please try again.', 'error');
        });
    }

    function downloadHsCrossCsv() {
        const rows = [
            ['date', 'year', 'month', 'day', 'accidentStatus', 'nearMissStatus', 'safetyConcernStatus']
        ];

        for (let day = 1; day <= maxDays; day++) {
            const entry = hsState[day] || {};
            rows.push([
                formatRecordDate(currentYear, currentMonth, day),
                currentYear,
                currentMonth,
                day,
                entry.accidentStatus || '',
                entry.nearMissStatus || '',
                entry.safetyConcernStatus || ''
            ]);
        }

        const csv = rows.map(row => row.map(csvCell).join(',')).join('\r\n') + '\r\n';
        downloadCsv(csv, 'hs-cross-daily-' + currentYear + '-' + String(currentMonth).padStart(2, '0') + '.csv');
        showMessage('H&S Cross CSV downloaded.', 'success');
    }

    function init() {
        const dateInput = document.getElementById('hsCrossDateInput');
        if (dateInput) {
            const yesterdayValue = formatDateValue(getYesterday());
            dateInput.max = yesterdayValue;
            dateInput.value = yesterdayValue;
            dateInput.addEventListener('change', loadHsCrossData);
        }

        const saveBtn = document.getElementById('saveHsCrossBtn');
        if (saveBtn) saveBtn.addEventListener('click', saveHsCrossData);

        const downloadBtn = document.getElementById('downloadHsCrossCsvBtn');
        if (downloadBtn) downloadBtn.addEventListener('click', downloadHsCrossCsv);

        window.addEventListener('hs-cross-open', function () {
            setTimeout(loadHsCrossData, 50);
        });

        const hsCrossItem = document.querySelector('[data-config="hs-cross"]');
        if (hsCrossItem) {
            hsCrossItem.addEventListener('click', function () {
                setTimeout(loadHsCrossData, 100);
            });
        }

        window.addEventListener('load', function () {
            loadHsCrossData();
        });

        const hsSection = document.getElementById('form-hs-cross');
        if (hsSection && hsSection.classList.contains('active')) {
            loadHsCrossData();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

