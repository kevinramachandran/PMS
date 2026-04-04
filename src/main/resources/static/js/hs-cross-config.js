/* H&S Cross Daily - Settings Configuration Script */
(function () {
    const ACCIDENT_OPTIONS = [
        { value: '', label: 'Not Set' },
        { value: 'ZERO', label: 'Zero Accident' },
        { value: 'WITH_LOST', label: 'With Lost Days' },
        { value: 'WITHOUT_LOST', label: 'Without Lost Days' }
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

    function applySelectStatusClass(selectEl, field, value) {
        if (!selectEl) return;
        selectEl.classList.remove('hs-status-notset', 'hs-status-safe', 'hs-status-issue');
        selectEl.classList.add(getStatusClass(field, value));
    }

    function setLoading(isLoading) {
        const saveBtn = document.getElementById('saveHsCrossBtn');
        const yearSel = document.getElementById('hsCrossYearSel');
        const monthSel = document.getElementById('hsCrossMonthSel');

        if (saveBtn) saveBtn.disabled = isLoading;
        if (yearSel) yearSel.disabled = isLoading;
        if (monthSel) monthSel.disabled = isLoading;

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

        select.value = value || '';
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
        const yearEl = document.getElementById('hsCrossYearSel');
        const monthEl = document.getElementById('hsCrossMonthSel');
        if (!yearEl || !monthEl) return;

        currentYear = parseInt(yearEl.value, 10);
        currentMonth = parseInt(monthEl.value, 10);
        maxDays = daysInMonth(currentYear, currentMonth);
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
                        accidentStatus: entry.accidentStatus || null,
                        nearMissStatus: entry.nearMissStatus || null,
                        safetyConcernStatus: entry.safetyConcernStatus || null
                    };
                });
                buildTable();
                showMessage('Data loaded successfully for selected month.', 'info');
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

    function initYearSelector() {
        const sel = document.getElementById('hsCrossYearSel');
        if (!sel) return;
        const now = new Date();
        for (let y = now.getFullYear() - 2; y <= now.getFullYear() + 1; y++) {
            const opt = document.createElement('option');
            opt.value = y;
            opt.textContent = y;
            if (y === now.getFullYear()) opt.selected = true;
            sel.appendChild(opt);
        }
    }

    function init() {
        initYearSelector();

        const monthSel = document.getElementById('hsCrossMonthSel');
        if (monthSel) monthSel.value = new Date().getMonth() + 1;

        const yearSel = document.getElementById('hsCrossYearSel');
        if (yearSel) yearSel.addEventListener('change', loadHsCrossData);
        if (monthSel) monthSel.addEventListener('change', loadHsCrossData);

        const saveBtn = document.getElementById('saveHsCrossBtn');
        if (saveBtn) saveBtn.addEventListener('click', saveHsCrossData);

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

