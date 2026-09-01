(function () {
    'use strict';

    function text(value, fallback) {
        if (value === null || value === undefined || value === '') {
            return fallback || '-';
        }
        return String(value);
    }

    function safeHtml(value) {
        const div = document.createElement('div');
        div.textContent = text(value, '');
        return div.innerHTML;
    }

    function normalizeLookup(value) {
        return String(value || '').trim().toLowerCase();
    }

    function currentUserLookups() {
        const body = document.body || {};
        return [
            body.dataset ? body.dataset.currentUsername : '',
            body.dataset ? body.dataset.currentEmail : ''
        ].map(normalizeLookup).filter(Boolean);
    }

    function valueMatchesCurrentUser(value, lookups) {
        const normalized = normalizeLookup(value);
        if (!normalized || !lookups.length) {
            return false;
        }
        return lookups.some(function (lookup) {
            return normalized === lookup || normalized.includes(lookup) || lookup.includes(normalized);
        });
    }

    function rowMatchesCurrentUser(row, fields, lookups) {
        if (!row || !lookups.length) {
            return false;
        }
        return fields.some(function (field) {
            return valueMatchesCurrentUser(row[field], lookups);
        });
    }

    function userScopedRows(rows, fields, lookups) {
        const scoped = rows.filter(function (row) {
            return rowMatchesCurrentUser(row, fields, lookups);
        });
        return scoped.length ? scoped : rows;
    }

    function fetchJson(url) {
        return fetch(url, { headers: { 'Accept': 'application/json' } })
            .then(function (response) {
                if (!response.ok || response.status === 204) {
                    return null;
                }
                return response.json();
            })
            .catch(function () {
                return null;
            });
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = text(value);
        }
    }

    function formatDate(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(String(value).substring(0, 10) + 'T00:00:00');
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }
        return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    }

    function statusPercent(status) {
        if (status === null || status === undefined || status === '') {
            return 0;
        }
        const normalized = String(status).trim().toLowerCase();
        if (normalized.endsWith('%')) {
            return clampPercent(normalized.slice(0, -1));
        }
        if (normalized === 'done' || normalized === 'closed' || normalized === 'completed') {
            return 100;
        }
        if (normalized === 'in-progress' || normalized === 'in progress') {
            return 50;
        }
        return clampPercent(normalized);
    }

    function clampPercent(value) {
        const parsed = Number(value);
        if (!Number.isFinite(parsed)) {
            return 0;
        }
        return Math.max(0, Math.min(100, Math.round(parsed)));
    }

    function effectiveTargetDate(row) {
        return row && (row.targetDateExtension2 || row.targetDateExtension1 || row.targetDate || '');
    }

    function dueDays(row) {
        if (row && row.dueDays !== null && row.dueDays !== undefined && row.dueDays !== '') {
            return Number(row.dueDays);
        }
        const targetDate = effectiveTargetDate(row);
        const target = targetDate ? new Date(targetDate + 'T00:00:00') : null;
        if (!target || Number.isNaN(target.getTime())) {
            return null;
        }
        const today = new Date();
        const todayDate = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        return Math.round((target.getTime() - todayDate.getTime()) / 86400000);
    }

    function sortByDateDesc(rows, fields) {
        return rows.slice().sort(function (a, b) {
            const aValue = firstDateValue(a, fields);
            const bValue = firstDateValue(b, fields);
            return String(bValue || '').localeCompare(String(aValue || ''));
        });
    }

    function firstDateValue(row, fields) {
        for (let i = 0; i < fields.length; i++) {
            if (row && row[fields[i]]) {
                return row[fields[i]];
            }
        }
        return '';
    }

    function renderList(id, rows, mapper, emptyMessage) {
        const container = document.getElementById(id);
        if (!container) {
            return;
        }
        if (!rows || rows.length === 0) {
            container.innerHTML = '<div class="user-empty-row">' + safeHtml(emptyMessage || 'No records found.') + '</div>';
            return;
        }
        container.innerHTML = rows.map(mapper).join('');
    }

    function renderIssueSummary(rows, lookups) {
        rows = userScopedRows(rows, ['responsible', 'ownerName', 'assignTo', 'assignedTo', 'createdBy', 'raisedBy'], lookups);
        const openRows = rows.filter(function (row) {
            return statusPercent(row.status) < 100;
        });
        setText('userOpenIssues', openRows.length);

        const sorted = openRows
            .map(function (row) {
                return Object.assign({}, row, { _dueDays: dueDays(row) });
            })
            .sort(function (a, b) {
                const av = a._dueDays === null ? 999999 : a._dueDays;
                const bv = b._dueDays === null ? 999999 : b._dueDays;
                return av - bv;
            })
            .slice(0, 5);

        renderList('userDueIssues', sorted, function (row) {
            const days = row._dueDays;
            const dueClass = days !== null && days < 0 ? 'is-overdue' : (days === 0 ? 'is-today' : '');
            const dueText = days === null ? 'No target date' : (days < 0 ? Math.abs(days) + ' days overdue' : (days === 0 ? 'Due today' : 'Due in ' + days + ' days'));
            return '' +
                '<a class="user-list-row" href="/issue-board">' +
                '<span class="user-row-main">' + safeHtml(row.problem || row.actions || 'Issue') + '</span>' +
                '<span class="user-row-meta">' + safeHtml(row.responsible || row.ownerName || '-') + '</span>' +
                '<span class="user-row-pill ' + dueClass + '">' + safeHtml(dueText) + '</span>' +
                '</a>';
        }, 'No open issue actions.');
    }

    function renderKpiSnapshot(rows) {
        const latest = sortByDateDesc(rows, ['date', 'createdAt', 'updatedAt'])[0] || null;
        setText('userKpiDate', latest ? 'Data date: ' + formatDate(latest.date) : 'No KPI data found');

        const metricMap = [
            ['Production Productivity', 'productionProductivityFtdActual', 'productionProductivityFtdTarget'],
            ['OEE', 'kpiOeeFtdActual', 'kpiOeeFtdTarget'],
            ['Beer Loss', 'kpiBeerLossFtdActual', 'kpiBeerLossFtdTarget'],
            ['Energy', 'kpiEnergyKwhHlFtdActual', 'kpiEnergyKwhHlFtdTarget'],
            ['Dispatch', 'dispatchFtdActual', 'dispatchFtdTarget']
        ];

        const rowsHtml = latest ? metricMap.map(function (metric) {
            const actual = latest[metric[1]];
            const target = latest[metric[2]];
            return '' +
                '<div class="user-kpi-row">' +
                '<span>' + safeHtml(metric[0]) + '</span>' +
                '<strong>' + safeHtml(text(actual)) + '</strong>' +
                '<em>Target ' + safeHtml(text(target)) + '</em>' +
                '</div>';
        }) : [];

        renderList('userKpiList', rowsHtml, function (rowHtml) { return rowHtml; }, 'No KPI data configured yet.');
    }

    function renderTrainingSummary(rows, lookups) {
        rows = userScopedRows(rows, ['trainer', 'fpr', 'ownerName', 'responsible', 'assignTo', 'assignedTo'], lookups);
        const sorted = sortByDateDesc(rows, ['trainingDate', 'updatedAt']).slice(0, 5);
        renderList('userTrainingList', sorted, function (row) {
            return '' +
                '<a class="user-list-row" href="/training-schedule">' +
                '<span class="user-row-main">' + safeHtml(row.trainingName || 'Training') + '</span>' +
                '<span class="user-row-meta">' + safeHtml(row.trainer || row.fpr || '-') + '</span>' +
                '<span class="user-row-pill">' + safeHtml(formatDate(row.trainingDate)) + '</span>' +
                '</a>';
        }, 'No training scheduled.');
    }

    function renderGembaSummary(rows, lookups) {
        rows = userScopedRows(rows, ['responsibility', 'managerName', 'email', 'createdBy', 'assignedTo', 'assignTo'], lookups);
        setText('userGembaFindings', rows.length);
        const sorted = sortByDateDesc(rows, ['gembaDate', 'walkDate', 'createdAt', 'updatedAt']).slice(0, 5);
        renderList('userGembaList', sorted, function (row) {
            return '' +
                '<a class="user-list-row" href="/gemba-reporting">' +
                '<span class="user-row-main">' + safeHtml(row.finding || row.observation || row.gembaWalkObservation || row.location || 'Gemba finding') + '</span>' +
                '<span class="user-row-meta">' + safeHtml(row.responsible || row.ownerName || row.createdBy || '-') + '</span>' +
                '<span class="user-row-pill">' + safeHtml(formatDate(firstDateValue(row, ['gembaDate', 'walkDate', 'createdAt', 'updatedAt']))) + '</span>' +
                '</a>';
        }, 'No Gemba findings recorded.');
    }

    function renderAbnormalitySummary(rows, lookups) {
        rows = userScopedRows(rows, ['assignTo', 'assignedTo', 'tagRaisedBy', 'raisedBy', 'createdBy', 'email'], lookups);
        const openRows = rows.filter(function (row) {
            const status = String(row.tagStatus || row.status || row.closureStatus || row.actionStatus || '').toLowerCase();
            return !status || !(status.includes('closed') || status.includes('complete'));
        });
        setText('userOpenAbnormalities', openRows.length);
        const sorted = sortByDateDesc(rows, ['reportedDate', 'date', 'createdAt', 'updatedAt']).slice(0, 5);
        renderList('userAbnormalityList', sorted, function (row) {
            return '' +
                '<a class="user-list-row" href="/abnormality-reporting">' +
                '<span class="user-row-main">' + safeHtml(row.abnormality || row.description || row.problem || row.department || 'Abnormality report') + '</span>' +
                '<span class="user-row-meta">' + safeHtml(row.responsible || row.ownerName || row.department || '-') + '</span>' +
                '<span class="user-row-pill">' + safeHtml(formatDate(firstDateValue(row, ['reportedDate', 'date', 'createdAt', 'updatedAt']))) + '</span>' +
                '</a>';
        }, 'No abnormality reports found.');
    }

    function renderKaizenSummary(rows, lookups) {
        rows = userScopedRows(rows, ['name', 'gembaKaizenProviderName', 'employeeIdHoNumber', 'assignedTo', 'assignTo'], lookups);
        const openRows = rows.filter(function (row) {
            return String(row.isKaizenImplemented || '').trim().toLowerCase() !== 'yes';
        });
        setText('userOpenKaizen', openRows.length);
    }

    function extractRecords(payload) {
        if (Array.isArray(payload)) {
            return payload;
        }
        if (payload && Array.isArray(payload.records)) {
            return payload.records;
        }
        return [];
    }

    function refresh() {
        const lookups = currentUserLookups();
        setText('userDashboardSyncStatus', 'Syncing...');
        Promise.all([
            fetchJson('/api/issue-board/latest'),
            fetchJson('/api/production-metrics/current-month'),
            fetchJson('/api/training-schedule/latest'),
            fetchJson('/api/gemba-walk-config/records'),
            fetchJson('/api/abnormality-reporting-config/records'),
            fetchJson('/api/gemba-kaizen-config/records')
        ]).then(function (results) {
            renderIssueSummary(extractRecords(results[0]), lookups);
            renderKpiSnapshot(extractRecords(results[1]));
            renderTrainingSummary(extractRecords(results[2]), lookups);
            renderGembaSummary(extractRecords(results[3]), lookups);
            renderAbnormalitySummary(extractRecords(results[4]), lookups);
            renderKaizenSummary(extractRecords(results[5]), lookups);
            setText('userDashboardSyncStatus', 'Last synced: ' + new Date().toLocaleTimeString('en-GB'));
        }).catch(function () {
            setText('userDashboardSyncStatus', 'Sync failed');
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        refresh();
        window.setInterval(refresh, 30000);
    });
})();
