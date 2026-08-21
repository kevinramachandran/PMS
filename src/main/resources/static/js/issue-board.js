$(document).ready(function() {
    const hamburger = $('#hamburger');
    const sidebar = $('#sidebar');
    const sidebarOverlay = $('#sidebarOverlay');
    const mainContent = $('.main-content');
    const canEditIssueBoard = String(document.body && document.body.dataset.canEditIssueBoard || '').toLowerCase() === 'true';

    hamburger.on('click', function() {
        if (window.innerWidth <= 768) {
            sidebar.toggleClass('active');
            sidebarOverlay.toggleClass('active');
        } else {
            sidebar.toggleClass('collapsed');
            mainContent.toggleClass('expanded');
            localStorage.setItem('sidebarCollapsed', sidebar.hasClass('collapsed'));
        }
    });

    sidebarOverlay.on('click', function() {
        sidebar.removeClass('active');
        sidebarOverlay.removeClass('active');
    });

    if (localStorage.getItem('sidebarCollapsed') === 'true' && window.innerWidth > 768) {
        sidebar.addClass('collapsed');
        mainContent.addClass('expanded');
    }

    function updateSyncStatus(label) {
        $('#issueBoardSyncStatus').text(label);
    }

    function formatDisplayDate(rawDate) {
        if (!rawDate) {
            return '-';
        }

        const dateObj = new Date(rawDate + 'T00:00:00');
        if (Number.isNaN(dateObj.getTime())) {
            return rawDate;
        }

        return dateObj.toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric'
        });
    }

    function safeText(value) {
        if (value === null || value === undefined) {
            return '';
        }

        const div = document.createElement('div');
        div.textContent = String(value);
        return div.innerHTML;
    }

    function clampPercent(value) {
        const parsed = Number(value);
        if (!Number.isFinite(parsed)) {
            return 0;
        }
        return Math.min(100, Math.max(0, Math.round(parsed)));
    }

    function statusToPercent(status) {
        if (status === null || status === undefined || status === '') {
            return 0;
        }

        const normalized = String(status).trim().toLowerCase();
        if (normalized.endsWith('%')) {
            return clampPercent(normalized.replace('%', ''));
        }

        if (normalized === 'done' || normalized === 'closed') {
            return 100;
        }

        if (normalized === 'in-progress' || normalized === 'in progress') {
            return 50;
        }

        if (normalized === 'open') {
            return 0;
        }

        return clampPercent(normalized);
    }

    function percentToPdcaStage(percent) {
        if (percent >= 100) return 'A';
        if (percent >= 75) return 'C';
        if (percent >= 50) return 'D';
        if (percent >= 25) return 'P';
        return '-';
    }

    function renderStatusCircle(status) {
        const progress = statusToPercent(status);
        const stage = percentToPdcaStage(progress);
        const title = stage === '-' ? 'PDCA not started' : 'PDCA: ' + stage + ' (' + progress + '%)';
        return '' +
            '<div class="status-cell">' +
            '<div class="progress-circle" data-value="' + progress + '" style="--progress: ' + progress + ';" title="' + title + '">' +
            '<span class="pdca-quarter pdca-p">P</span>' +
            '<span class="pdca-quarter pdca-d">D</span>' +
            '<span class="pdca-quarter pdca-c">C</span>' +
            '<span class="pdca-quarter pdca-a">A</span>' +
            '</div>' +
            '</div>';
    }

    function renderActionButtons(row) {
        const sourceIndex = currentIssueBoardData.indexOf(row);
        if (!canEditIssueBoard || !row || !row.id) {
            return '';
        }
        return '' +
            '<div class="issue-action-stack">' +
            '<button type="button" class="issue-edit-link" data-index="' + sourceIndex + '" title="Update issue progress">' +
            '<i class="fas fa-pen-to-square"></i><span>Edit</span>' +
            '</button>' +
            '</div>';
    }

    function renderHistoryButton(row) {
        if (!row || !row.id) {
            return '-';
        }
        return '' +
            '<button type="button" class="issue-history-link" data-id="' + safeText(row.id) + '" title="View history" aria-label="View issue history">' +
            '<i class="fas fa-clock-rotate-left"></i>' +
            '</button>';
    }

    function dueClass(dueDays) {
        if (dueDays === null || dueDays === undefined || dueDays === '') {
            return '';
        }

        const parsed = Number(dueDays);
        if (!Number.isFinite(parsed)) {
            return '';
        }

        return parsed < 0 ? 'due-overdue' : (parsed === 0 ? 'due-today' : 'due-upcoming');
    }

    function getEffectiveTargetDate(row) {
        if (!row) {
            return '';
        }

        return row.targetDateExtension2 || row.targetDateExtension1 || row.targetDate || '';
    }

    function normalizeDueDays(row) {
        if (row.dueDays !== null && row.dueDays !== undefined && row.dueDays !== '') {
            return row.dueDays;
        }

        const effectiveTargetDate = getEffectiveTargetDate(row);
        if (!effectiveTargetDate) {
            return '';
        }

        const target = new Date(effectiveTargetDate + 'T00:00:00');
        const today = new Date();
        const todayUtc = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        if (Number.isNaN(target.getTime())) {
            return '';
        }

        const msPerDay = 24 * 60 * 60 * 1000;
        return Math.round((target.getTime() - todayUtc.getTime()) / msPerDay);
    }

    function renderReviewDates(rows) {
        if (!rows || rows.length === 0) {
            $('#issueLastUpdatedDate').text('-');
            $('#lastReviewDate').text('-');
            $('#nextReviewDate').text('-');
            return;
        }

        const first = rows[0];
        $('#issueLastUpdatedDate').text(formatDisplayDate(first.boardDate));
        $('#lastReviewDate').text(formatDisplayDate(first.lastReviewDate));
        $('#nextReviewDate').text(formatDisplayDate(first.nextReviewDate));
    }

    function renderTargetDateCell(row) {
        const slots = [
            { label: 'Target', value: row.targetDate || '', remark: row.targetDateRemark || '' },
            { label: 'Ext. 1', value: row.targetDateExtension1 || '', remark: row.targetDateExtension1Remark || '' },
            { label: 'Ext. 2', value: row.targetDateExtension2 || '', remark: row.targetDateExtension2Remark || '' }
        ];
        const dates = slots.map(function(slot, index) {
            return Object.assign({ originalIndex: index }, slot);
        }).filter(function(slot) {
            return slot.originalIndex === 0 || !!slot.value;
        }).map(function(slot) {
            const hasLaterValue = slots.slice(slot.originalIndex + 1).some(function(laterValue) {
                return !!laterValue.value;
            });
            return {
                label: slot.label,
                value: slot.value,
                remark: slot.remark,
                superseded: !!slot.value && hasLaterValue
            };
        });

        return '<div class="issue-target-date-stack">' + dates.map(function(slot) {
            const cls = slot.superseded ? 'issue-target-date-old' : 'issue-target-date-current';
            const dateText = slot.value ? formatDisplayDate(slot.value) : '-';
            const remarkText = slot.remark ? slot.remark : 'No remarks';
            return '' +
                '<div class="issue-target-date-entry ' + cls + '">' +
                '<div class="issue-target-date-mainline">' +
                '<span class="issue-target-date-label">' + safeText(slot.label) + '</span>' +
                '<span class="issue-target-date-value">' + safeText(dateText) + '</span>' +
                '</div>' +
                '<div class="issue-target-date-remark"><strong>Remarks:</strong> ' + safeText(remarkText) + '</div>' +
                '</div>';
        }).join('') + '</div>';
    }

    function renderRows(rows, emptyMessage) {
        const tbody = $('#issueBoardBody');
        tbody.empty();

        if (!rows || rows.length === 0) {
            tbody.append('<tr><td colspan="' + (canEditIssueBoard ? '13' : '12') + '" class="empty-row">' + safeText(emptyMessage || 'No issue board data configured. Open Issue Board Configuration.') + '</td></tr>');
            return;
        }

        rows.forEach(function(row, index) {
            const dueDays = normalizeDueDays(row);
            const tr = '' +
                '<tr>' +
                '<td>' + (index + 1) + '</td>' +
                '<td>' + safeText(row.problem) + '</td>' +
                '<td>' + safeText(row.priority) + '</td>' +
                '<td>' + safeText(row.ownerName) + '</td>' +
                '<td>' + safeText(formatDisplayDate(row.issueDate)) + '</td>' +
                '<td>' + safeText(row.rootCause) + '</td>' +
                '<td>' + safeText(row.actions) + '</td>' +
                '<td>' + safeText(row.responsible) + '</td>' +
                '<td>' + renderTargetDateCell(row) + '</td>' +
                '<td class="issue-due-days-cell ' + dueClass(dueDays) + '">' + safeText(dueDays) + '</td>' +
                '<td class="issue-icon-cell issue-status-col">' + renderStatusCircle(row.status) + '</td>' +
                '<td class="issue-icon-cell issue-history-col">' + renderHistoryButton(row) + '</td>' +
                (canEditIssueBoard ? '<td class="issue-icon-cell issue-action-col">' + renderActionButtons(row) + '</td>' : '') +
                '</tr>';
            tbody.append(tr);
        });
    }

    let currentIssueBoardData = [];
    let issueBoardSortKey = null;
    let issueBoardSortAsc = true;
    let issueBoardSearchTerm = '';
    let issueBoardAssignableUsers = [];

    function initializeIssueBoardSorting() {
        $('#issueBoardTable thead .sortable').off('click.issueSort').on('click.issueSort', function() {
            const key = $(this).data('sort-key');
            if (issueBoardSortKey === key) {
                issueBoardSortAsc = !issueBoardSortAsc;
            } else {
                issueBoardSortKey = key;
                issueBoardSortAsc = true;
            }

            applyIssueBoardView();
            updateIssueBoardSortIndicators();
        });
    }

    function updateIssueBoardSortIndicators() {
        $('#issueBoardTable thead th').each(function() {
            $(this).removeClass('sort-asc sort-desc');
            const key = $(this).data('sort-key');
            if (key === issueBoardSortKey) {
                $(this).addClass(issueBoardSortAsc ? 'sort-asc' : 'sort-desc');
            }
        });
    }

    function sortIssueRows(rows) {
        if (!issueBoardSortKey || !rows.length) {
            return [...rows].sort(function(a, b) {
                const aVal = a.updatedAt || '';
                const bVal = b.updatedAt || '';
                const cmp = String(bVal).localeCompare(String(aVal));
                return cmp !== 0 ? cmp : (Number(b.id || 0) - Number(a.id || 0));
            });
        }

        return [...rows].sort(function(a, b) {
            let aVal = a[issueBoardSortKey];
            let bVal = b[issueBoardSortKey];

            if (issueBoardSortKey === 'dueDays') {
                aVal = normalizeDueDays(a);
                bVal = normalizeDueDays(b);
            }

            if (aVal === null || aVal === undefined) aVal = '';
            if (bVal === null || bVal === undefined) bVal = '';

            if (typeof aVal === 'string') {
                aVal = aVal.toLowerCase();
                bVal = bVal.toLowerCase();
                const cmp = aVal.localeCompare(bVal);
                return issueBoardSortAsc ? cmp : -cmp;
            }

            const numA = Number(aVal);
            const numB = Number(bVal);
            if (!Number.isNaN(numA) && !Number.isNaN(numB)) {
                return issueBoardSortAsc ? (numA - numB) : (numB - numA);
            }

            const cmp = String(aVal).localeCompare(String(bVal));
            return issueBoardSortAsc ? cmp : -cmp;
        });
    }

    function rowSearchText(row, index) {
        const dueDays = normalizeDueDays(row);
        const effectiveTargetDate = getEffectiveTargetDate(row);
        return [
            index + 1,
            row.problem,
            row.priority,
            row.ownerName,
            row.issueDate,
            formatDisplayDate(row.issueDate),
            row.rootCause,
            row.actions,
            row.responsible,
            row.targetDate,
            row.targetDateRemark,
            row.targetDateExtension1,
            row.targetDateExtension1Remark,
            row.targetDateExtension2,
            row.targetDateExtension2Remark,
            effectiveTargetDate,
            formatDisplayDate(row.targetDate),
            formatDisplayDate(row.targetDateExtension1),
            formatDisplayDate(row.targetDateExtension2),
            formatDisplayDate(effectiveTargetDate),
            dueDays,
            statusToPercent(row.status) + '%',
            row.status,
            row.completedDate
        ].map(function(value) {
            return value === null || value === undefined ? '' : String(value).toLowerCase();
        }).join(' ');
    }

    function filterIssueRows(rows) {
        const term = issueBoardSearchTerm.trim().toLowerCase();
        if (!term) {
            return rows;
        }

        return rows.filter(function(row, index) {
            return rowSearchText(row, index).includes(term);
        });
    }

    function applyIssueBoardView() {
        renderReviewDates(currentIssueBoardData);
        const filtered = filterIssueRows(currentIssueBoardData);
        const sorted = sortIssueRows(filtered);
        const emptyMessage = issueBoardSearchTerm.trim()
            ? 'No matching issues found.'
            : 'No issue board data configured. Open Issue Board Configuration.';

        renderRows(sorted, emptyMessage);
    }

    function initializeIssueSearch() {
        const searchInput = $('#issueBoardSearch');
        const searchWrap = searchInput.closest('.issue-search');
        const clearBtn = $('#issueBoardSearchClear');

        searchInput.off('input.issueSearch').on('input.issueSearch', function() {
            issueBoardSearchTerm = $(this).val() || '';
            searchWrap.toggleClass('has-value', issueBoardSearchTerm.trim().length > 0);
            applyIssueBoardView();
        });

        clearBtn.off('click.issueSearch').on('click.issueSearch', function() {
            searchInput.val('');
            issueBoardSearchTerm = '';
            searchWrap.removeClass('has-value');
            searchInput.trigger('focus');
            applyIssueBoardView();
        });
    }

    function setIssueDrawerOpen(open) {
        $('#issueBoardDrawer').toggleClass('open', open).attr('aria-hidden', open ? 'false' : 'true');
        $('#issueBoardDrawerBackdrop').toggleClass('open', open);
        $('body').toggleClass('drawer-open', open);
    }

    function setHistoryDrawerOpen(open) {
        $('#issueHistoryDrawer').toggleClass('open', open).attr('aria-hidden', open ? 'false' : 'true');
        $('#issueHistoryDrawerBackdrop').toggleClass('open', open);
        $('body').toggleClass('drawer-open', open);
    }

    function getIssueDrawerPayload() {
        const extension1Date = $('#drawerIssueTargetExt1').val() || null;
        const extension2Date = $('#drawerIssueTargetExt2').val() || null;
        return {
            problem: ($('#drawerIssueProblem').val() || '').trim(),
            priority: $('#drawerIssuePriority').val() || '',
            ownerName: ($('#drawerIssueOwner').val() || '').trim(),
            issueDate: $('#drawerIssueDate').val() || null,
            rootCause: ($('#drawerIssueRootCause').val() || '').trim(),
            actions: ($('#drawerIssueActions').val() || '').trim(),
            responsible: ($('#drawerIssueResponsible').val() || '').trim(),
            targetDate: $('#drawerIssueTargetDate').val() || null,
            targetDateRemark: ($('#drawerIssueTargetDateRemark').val() || '').trim() || null,
            targetDateExtension1: extension1Date,
            targetDateExtension1Remark: extension1Date ? (($('#drawerIssueTargetExt1Remark').val() || '').trim() || null) : null,
            targetDateExtension2: extension2Date,
            targetDateExtension2Remark: extension2Date ? (($('#drawerIssueTargetExt2Remark').val() || '').trim() || null) : null,
            status: $('#drawerIssueStatus').val() || '0%',
            completedDate: $('#drawerIssueCompletedDate').val() || null
        };
    }

    function updateIssueDrawerDueDays() {
        const effectiveTarget = $('#drawerIssueTargetExt2').val() || $('#drawerIssueTargetExt1').val() || $('#drawerIssueTargetDate').val();
        $('#drawerIssueDueDays').val(effectiveTarget ? normalizeDueDays({ targetDate: $('#drawerIssueTargetDate').val(), targetDateExtension1: $('#drawerIssueTargetExt1').val(), targetDateExtension2: $('#drawerIssueTargetExt2').val() }) : '');
    }

    function loadIssueBoardAssignableUsers() {
        $.ajax({
            url: '/api/issue-board/assignable-users',
            type: 'GET',
            success: function(response) {
                issueBoardAssignableUsers = response && Array.isArray(response.users) ? response.users : [];
                const options = issueBoardAssignableUsers.map(function(user) {
                    return '<option value="' + safeText(user.username || '') + '">' + safeText(user.label || user.username || '') + '</option>';
                }).join('');
                $('#issueBoardResponsibleList').html(options);
            },
            error: function() {
                issueBoardAssignableUsers = [];
                $('#issueBoardResponsibleList').empty();
            }
        });
    }

    function isIssueResponsibleValid(value) {
        const normalized = (value || '').trim().toLowerCase();
        if (!normalized || issueBoardAssignableUsers.length === 0) {
            return !!normalized;
        }
        return issueBoardAssignableUsers.some(function(user) {
            return String(user.username || '').toLowerCase() === normalized || String(user.label || '').toLowerCase() === normalized;
        });
    }

    function validateIssueDrawerTargetRemarks() {
        const pairs = [
            { date: '#drawerIssueTargetExt1', remark: '#drawerIssueTargetExt1Remark' },
            { date: '#drawerIssueTargetExt2', remark: '#drawerIssueTargetExt2Remark' }
        ];
        let valid = true;
        pairs.forEach(function(pair) {
            const $date = $(pair.date);
            const $remark = $(pair.remark);
            if ($date.prop('disabled')) {
                $remark.removeClass('issue-invalid');
                return;
            }
            const hasDate = !!($date.val() || '').trim();
            const hasRemark = !!($remark.val() || '').trim();
            $remark.toggleClass('issue-invalid', hasDate && !hasRemark);
            if (hasDate && !hasRemark && valid) {
                $remark.trigger('focus');
                valid = false;
            }
        });
        return valid;
    }

    function getDrawerEffectiveTargetDate(row) {
        return row.targetDateExtension2 || row.targetDateExtension1 || row.targetDate || '';
    }

    function applyIssueDrawerTargetLocks() {
        const fields = [
            $('#drawerIssueTargetDate'),
            $('#drawerIssueTargetExt1'),
            $('#drawerIssueTargetExt2')
        ];
        const remarkFields = [
            $('#drawerIssueTargetDateRemark'),
            $('#drawerIssueTargetExt1Remark'),
            $('#drawerIssueTargetExt2Remark')
        ];
        const savedValues = fields.map(function($field) {
            return $field.data('saved-value') || '';
        });

        fields.forEach(function($field, index) {
            const hasSavedValue = !!savedValues[index];
            const hasLaterSavedValue = savedValues.slice(index + 1).some(function(value) {
                return !!value;
            });
            const locked = hasSavedValue;
            $field
                .prop('disabled', locked)
                .toggleClass('issue-date-struck', hasSavedValue && hasLaterSavedValue)
                .attr('title', hasSavedValue ? 'This target date is already saved' : 'Enter the next target date');
            remarkFields[index]
                .prop('disabled', locked)
                .removeClass('issue-date-struck')
                .attr('title', hasSavedValue ? 'Saved target date remarks' : 'Enter remarks for this target date');
        });
    }

    function fillIssueDrawer(row, index) {
        $('#drawerIssueIndex').val(index === null || index === undefined ? '' : index);
        $('#drawerIssueId').val(row.id || '');
        $('#issueBoardDrawerTitle').text('Edit Issue');
        $('#issueBoardDrawerSave').html('<i class="fas fa-check"></i> Update Issue');
        $('#drawerIssueSummary').html(
            '<strong>' + safeText(row.problem || 'Issue') + '</strong>' +
            '<span>' + safeText(row.responsible || '-') + '</span>'
        );
        $('#drawerIssueProblem').val(row.problem || '');
        $('#drawerIssuePriority').val(row.priority || '');
        $('#drawerIssueOwner').val(row.ownerName || '');
        $('#drawerIssueDate').val(row.issueDate || '');
        $('#drawerIssueRootCause').val(row.rootCause || '');
        $('#drawerIssueActions').val(row.actions || '');
        $('#drawerIssueResponsible').val(row.responsible || '');
        $('#drawerIssueTargetDate').val(row.targetDate || '');
        $('#drawerIssueTargetDateRemark').val(row.targetDateRemark || '');
        $('#drawerIssueTargetExt1').val(row.targetDateExtension1 || '');
        $('#drawerIssueTargetExt1Remark').val(row.targetDateExtension1Remark || '');
        $('#drawerIssueTargetExt2').val(row.targetDateExtension2 || '');
        $('#drawerIssueTargetExt2Remark').val(row.targetDateExtension2Remark || '');
        $('#drawerIssueTargetDate').data('saved-value', row.targetDate || '');
        $('#drawerIssueTargetDateRemark').data('saved-value', row.targetDateRemark || '');
        $('#drawerIssueTargetExt1').data('saved-value', row.targetDateExtension1 || '');
        $('#drawerIssueTargetExt1Remark').data('saved-value', row.targetDateExtension1Remark || '');
        $('#drawerIssueTargetExt2').data('saved-value', row.targetDateExtension2 || '');
        $('#drawerIssueTargetExt2Remark').data('saved-value', row.targetDateExtension2Remark || '');
        $('#drawerIssueStatus').val(row.status || '0%');
        $('#drawerIssueCompletedDate').val(row.completedDate || '');
        $('#drawerIssueDueDays').val(normalizeDueDays(row));
        applyIssueDrawerTargetLocks();
        loadIssueDrawerHistory(row.id);
    }

    $('#drawerIssueTargetDate, #drawerIssueTargetExt1, #drawerIssueTargetExt2').on('change input', function() {
        updateIssueDrawerDueDays();
    });
    $('#drawerIssueTargetDateRemark, #drawerIssueTargetExt1Remark, #drawerIssueTargetExt2Remark').on('input', function() {
        $(this).removeClass('issue-invalid issue-date-struck');
    });

    $('#issueBoardBody').on('click', '.issue-edit-link', function() {
        const index = Number($(this).data('index'));
        const row = currentIssueBoardData[index];
        if (!canEditIssueBoard || !row) {
            return;
        }
        fillIssueDrawer(row, index);
        setIssueDrawerOpen(true);
    });

    function formatHistoryDate(value) {
        if (!value) {
            return '-';
        }
        const normalized = String(value).replace(' ', 'T');
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return date.toLocaleString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function formatHistoryValueForDisplay(value) {
        if (!value || value === '') {
            return '-';
        }
        // Check if it looks like a date (YYYY-MM-DD format)
        if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
            const date = new Date(value + 'T00:00:00');
            if (!Number.isNaN(date.getTime())) {
                return date.toLocaleString('en-GB', {
                    day: '2-digit',
                    month: 'short',
                    year: 'numeric'
                });
            }
        }
        return value;
    }

    function buildHistoryDescription(entry) {
        const field = entry.fieldName || 'Field';
        const oldVal = formatHistoryValueForDisplay(entry.oldValue);
        const newVal = formatHistoryValueForDisplay(entry.newValue);
        return 'Changed ' + field + ' from ' + oldVal + ' to ' + newVal + '.';
    }

    function renderHistoryEntries(entries) {
        const list = $('#issueHistoryList');
        renderHistoryTimeline(entries, list);
    }

    function renderHistoryTimeline(entries, list) {
        list.empty();
        if (!entries || entries.length === 0) {
            list.html('<div class="issue-history-empty">No history yet.</div>');
            return;
        }
        entries.forEach(function(entry) {
            const description = buildHistoryDescription(entry);
            list.append(
                '<div class="issue-history-item">' +
                '<div class="issue-history-meta">' +
                '<strong>' + safeText(entry.editedBy || '-') + '</strong>' +
                '<span>' + safeText(formatHistoryDate(entry.editedAt)) + '</span>' +
                '</div>' +
                '<div class="issue-history-description">' +
                '<p>' + safeText(description) + '</p>' +
                '</div>' +
                '</div>'
            );
        });
    }

    function loadIssueDrawerHistory(id) {
        const list = $('#issueDrawerHistoryList');
        if (!id) {
            list.html('<div class="issue-history-empty">History available after save.</div>');
            return;
        }
        list.html('<div class="issue-history-empty">Loading history...</div>');
        $.ajax({
            url: '/api/issue-board/' + id + '/history',
            type: 'GET',
            success: function(data) {
                renderHistoryTimeline(Array.isArray(data) ? data : [], list);
            },
            error: function() {
                list.html('<div class="issue-history-empty">Failed to load history.</div>');
            }
        });
    }

    $('#issueBoardBody').on('click', '.issue-history-link', function() {
        const id = $(this).data('id');
        $('#issueHistoryList').html('<div class="issue-history-empty">Loading...</div>');
        setHistoryDrawerOpen(true);
        $.ajax({
            url: '/api/issue-board/' + id + '/history',
            type: 'GET',
            success: function(data) {
                renderHistoryEntries(Array.isArray(data) ? data : []);
            },
            error: function() {
                $('#issueHistoryList').html('<div class="issue-history-empty">Failed to load history.</div>');
            }
        });
    });

    $('#issueBoardDrawerClose, #issueBoardDrawerCancel, #issueBoardDrawerBackdrop').on('click', function() {
        setIssueDrawerOpen(false);
    });

    $('#issueHistoryDrawerClose, #issueHistoryDrawerBackdrop').on('click', function() {
        setHistoryDrawerOpen(false);
    });

    $('#issueBoardDrawerForm').on('submit', function(event) {
        event.preventDefault();
        if (!canEditIssueBoard) {
            return;
        }
        const row = getIssueDrawerPayload();
        const id = $('#drawerIssueId').val();
        const indexValue = $('#drawerIssueIndex').val();
        if (!id || !getDrawerEffectiveTargetDate(row)) {
            return;
        }
        if (!row.problem || !row.priority || !row.ownerName || !row.issueDate || !row.rootCause || !row.actions || !isIssueResponsibleValid(row.responsible)) {
            $('#drawerIssueProblem').toggleClass('issue-invalid', !row.problem);
            $('#drawerIssuePriority').toggleClass('issue-invalid', !row.priority);
            $('#drawerIssueOwner').toggleClass('issue-invalid', !row.ownerName);
            $('#drawerIssueDate').toggleClass('issue-invalid', !row.issueDate);
            $('#drawerIssueRootCause').toggleClass('issue-invalid', !row.rootCause);
            $('#drawerIssueActions').toggleClass('issue-invalid', !row.actions);
            $('#drawerIssueResponsible').toggleClass('issue-invalid', !isIssueResponsibleValid(row.responsible));
            return;
        }
        if (!validateIssueDrawerTargetRemarks()) {
            alert('Remarks are required only for the extension target date entered.');
            return;
        }
        if (row.status === '100%' && !row.completedDate) {
            $('#drawerIssueCompletedDate').trigger('focus');
            return;
        }

        $('#issueBoardDrawerSave').prop('disabled', true);
        $.ajax({
            url: '/api/issue-board/' + id + '/progress',
            type: 'PATCH',
            contentType: 'application/json',
            data: JSON.stringify(row),
            success: function(saved) {
                const index = Number(indexValue);
                if (Number.isInteger(index) && currentIssueBoardData[index]) {
                    currentIssueBoardData[index] = Object.assign({}, currentIssueBoardData[index], saved || row);
                    window.__issueBoardRows = currentIssueBoardData;
                }
                applyIssueBoardView();
                localStorage.setItem('issue-board-update', Date.now());
                updateSyncStatus('Issue updated');
                setIssueDrawerOpen(false);
            },
            error: function() {
                updateSyncStatus('Save failed');
            },
            complete: function() {
                $('#issueBoardDrawerSave').prop('disabled', false);
            }
        });
    });


    function loadIssueBoardData() {
        updateSyncStatus('Syncing...');

        $.ajax({
            url: '/api/issue-board/latest',
            type: 'GET',
            success: function(data) {
                currentIssueBoardData = Array.isArray(data) ? data : [];
                window.__issueBoardRows = currentIssueBoardData;
                applyIssueBoardView();
                initializeIssueBoardSorting();
                updateSyncStatus('Last synced: ' + new Date().toLocaleTimeString('en-GB'));
            },
            error: function() {
                currentIssueBoardData = [];
                window.__issueBoardRows = [];
                applyIssueBoardView();
                updateSyncStatus('Sync failed');
            }
        });
    }

    window.addEventListener('storage', function(e) {
        if (e.key === 'issue-board-update') {
            loadIssueBoardData();
        }
    });

    initializeIssueSearch();
    $('body').toggleClass('issue-board-readonly', !canEditIssueBoard);
    loadIssueBoardAssignableUsers();
    loadIssueBoardData();
    setInterval(loadIssueBoardData, 30000);
});

// ── PDF Export ──────────────────────────────────────────────────────────────
function exportIssueBoardPdf() {
    var btn = document.getElementById('issueBoardPdfBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Generating...'; }

    var updatedDate = document.getElementById('issueLastUpdatedDate') ? document.getElementById('issueLastUpdatedDate').textContent.trim() : '-';
    var lastDate  = document.getElementById('lastReviewDate') ? document.getElementById('lastReviewDate').textContent.trim() : '-';
    var nextDate  = document.getElementById('nextReviewDate') ? document.getElementById('nextReviewDate').textContent.trim() : '-';
    var filterLabel = 'Last Updated: ' + updatedDate + '   |   Last Reviewed: ' + lastDate + '   |   Next Review: ' + nextDate;

    var rows  = window.__issueBoardRows || [];
    var today = new Date();
    var dd = String(today.getDate()).padStart(2, '0');
    var mm = String(today.getMonth() + 1).padStart(2, '0');
    var yyyy = today.getFullYear();

    buildIssueHistorySummaryMap().always(function(historySummaryById) {
        historySummaryById = historySummaryById || {};
        var columns = ['#', 'Problem', 'Priority', 'Name', 'Date', 'Root Cause', 'Actions', 'Resp.', 'Target date / remarks', 'Due days', 'Status', 'Completed date', 'History Summary'];
        var tableRows = rows.map(function(row, index) {
            var targetDate = getIssueBoardTargetDateRemarks(row);
            var dueDays = getIssueBoardDueDays(row);
            var status = getIssueBoardStatusLabel(row.status);
            var historySummary = row.id == null ? 'History available after save.' : (historySummaryById[row.id] || 'No history recorded.');
            return [
                (index + 1),
                row.problem || '',
                row.priority || '',
                row.ownerName || '',
                row.issueDate || '',
                row.rootCause || '',
                row.actions || '',
                row.responsible || '',
                targetDate,
                dueDays,
                status,
                row.completedDate || '',
                historySummary
            ];
        });

        PmsReport.generate({
            title:       'Issue Board',
            filterLabel: filterLabel,
            orientation: 'landscape',
            columns:     columns,
            rows:        tableRows,
            paragraphs:  [],
            styles:      { fontSize: 6.4, cellPadding: 1.3, overflow: 'linebreak', valign: 'top' },
            headStyles:  { fontSize: 7.1, cellPadding: 1.4, valign: 'middle' },
            columnStyles: {
                0:  { cellWidth: 7, halign: 'center' },
                1:  { cellWidth: 24 },
                2:  { cellWidth: 13 },
                3:  { cellWidth: 17 },
                4:  { cellWidth: 16 },
                5:  { cellWidth: 25 },
                6:  { cellWidth: 30 },
                7:  { cellWidth: 18 },
                8:  { cellWidth: 38 },
                9:  { cellWidth: 10, halign: 'center' },
                10: { cellWidth: 10, halign: 'center' },
                11: { cellWidth: 17 },
                12: { cellWidth: 35 }
            },
            filename:    'Issue-Board_' + yyyy + '-' + mm + '-' + dd + '.pdf'
        });

        setTimeout(function() {
            if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-file-pdf"></i> Export PDF'; }
        }, 1200);
    });
}

function exportIssueBoardCsv() {
    var btn = document.getElementById('issueBoardCsvBtn');
    if (btn) { btn.disabled = true; }

    try {
        var rows = window.__issueBoardRows || [];
        var headers = ['#', 'Problem', 'Priority', 'Name', 'Date', 'Root Cause', 'Actions', 'Resp.', 'Target Date', 'Target Date Remarks', 'Target Date Extension 1', 'Target Date Extension 1 Remarks', 'Target Date Extension 2', 'Target Date Extension 2 Remarks', 'Effective Target Date', 'Due Days', 'Status', 'Completed Date'];
        var csvRows = [headers].concat(rows.map(function(row, index) {
            return [
                index + 1,
                row.problem || '',
                row.priority || '',
                row.ownerName || '',
                row.issueDate || '',
                row.rootCause || '',
                row.actions || '',
                row.responsible || '',
                row.targetDate || '',
                row.targetDateRemark || '',
                row.targetDateExtension1 || '',
                row.targetDateExtension1Remark || '',
                row.targetDateExtension2 || '',
                row.targetDateExtension2Remark || '',
                getIssueBoardEffectiveTargetDate(row),
                getIssueBoardDueDays(row),
                getIssueBoardStatusLabel(row.status),
                row.completedDate || ''
            ];
        }));

        var csv = csvRows.map(function(row) {
            return row.map(issueBoardCsvCell).join(',');
        }).join('\r\n') + '\r\n';
        var today = new Date();
        var filename = 'issue-board-' + today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-' + String(today.getDate()).padStart(2, '0') + '.csv';
        downloadIssueBoardCsv(csv, filename);
    } finally {
        if (btn) { btn.disabled = false; }
    }
}

function issueBoardCsvCell(value) {
    var text = value === null || value === undefined ? '' : String(value);
    return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}

function downloadIssueBoardCsv(csv, filename) {
    var blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

function buildIssueHistorySummaryMap() {
    var rows = window.__issueBoardRows || [];
    var deferred = $.Deferred();
    var summaries = {};
    var pending = 0;

    if (!rows || rows.length === 0) {
        return deferred.resolve(summaries).promise();
    }

    rows.forEach(function(row) {
        if (!row || row.id == null) {
            return;
        }

        pending++;
        $.ajax({
            url: '/api/issue-board/' + row.id + '/history',
            type: 'GET',
            success: function(data) {
                summaries[row.id] = buildIssueHistorySummary(Array.isArray(data) ? data : []);
            },
            error: function() {
                summaries[row.id] = 'History could not be loaded.';
            },
            complete: function() {
                pending--;
                if (pending === 0) {
                    deferred.resolve(summaries);
                }
            }
        });
    });

    if (pending === 0) {
        deferred.resolve(summaries);
    }

    return deferred.promise();
}

function buildIssueHistorySummary(entries) {
    if (!entries || entries.length === 0) {
        return 'No history recorded.';
    }

    return entries.slice(0, 4).map(function(entry) {
        var at = formatIssueHistoryDate(entry.editedAt);
        var editor = entry.editedBy || 'User';
        var fieldName = entry.fieldName || 'Field';
        var oldValue = formatIssueHistorySummaryValue(entry.oldValue);
        var newValue = formatIssueHistorySummaryValue(entry.newValue);
        return (at ? at + ' - ' : '') + editor + ': ' + fieldName + ' changed from ' + oldValue + ' to ' + newValue;
    }).join('\n');
}

function formatIssueHistorySummaryValue(value) {
    if (value === null || value === undefined || value === '') {
        return '-';
    }

    var text = String(value).replace(/\s+/g, ' ').trim();
    if (text.length > 60) {
        text = text.substring(0, 57) + '...';
    }
    return '"' + text + '"';
}

function buildIssueHistoryParagraphs() {
    var rows = window.__issueBoardRows || [];
    var deferred = $.Deferred();
    var paragraphs = [];
    var pending = 0;

    if (!rows || rows.length === 0) {
        return deferred.resolve([]).promise();
    }

    rows.forEach(function(row, index) {
        if (!row || row.id == null) {
            return;
        }
        pending++;
        $.ajax({
            url: '/api/issue-board/' + row.id + '/history',
            type: 'GET',
            success: function(data) {
                var entries = Array.isArray(data) ? data : [];
                if (entries.length === 0) {
                    return;
                }
                paragraphs.push(buildIssueHistoryParagraph(row, index, entries));
            },
            complete: function() {
                pending--;
                if (pending === 0) {
                    paragraphs.sort(function(a, b) { return a.index - b.index; });
                    deferred.resolve(paragraphs.map(function(paragraph) {
                        return {
                            title: paragraph.title,
                            text: paragraph.text
                        };
                    }));
                }
            }
        });
    });

    if (pending === 0) {
        deferred.resolve([]);
    }

    return deferred.promise();
}

function buildIssueHistoryParagraph(row, index, entries) {
    var sentences = entries.map(function(entry) {
        var fieldName = entry.fieldName || 'Field';
        var oldValue = entry.oldValue || '-';
        var newValue = entry.newValue || '-';
        var editedBy = entry.editedBy || '-';
        var at = formatIssueHistoryDate(entry.editedAt);
        return fieldName + ' changed from "' + oldValue + '" to "' + newValue + '" by ' + editedBy + (at ? ' on ' + at : '') + '.';
    });

    return {
        index: index,
        title: 'History - #' + (index + 1) + ' ' + (row.problem || 'Issue'),
        text: sentences.join(' ')
    };
}

function getIssueBoardEffectiveTargetDate(row) {
    if (!row) {
        return '';
    }

    return row.targetDateExtension2 || row.targetDateExtension1 || row.targetDate || '';
}

function getIssueBoardTargetDateRemarks(row) {
    if (!row) {
        return '';
    }

    return [
        { label: 'Target', date: row.targetDate, remark: row.targetDateRemark },
        { label: 'Ext. 1', date: row.targetDateExtension1, remark: row.targetDateExtension1Remark },
        { label: 'Ext. 2', date: row.targetDateExtension2, remark: row.targetDateExtension2Remark }
    ].filter(function(slot) {
        return !!slot.date;
    }).map(function(slot) {
        return slot.label + ': ' + slot.date + (slot.remark ? ' (' + slot.remark + ')' : '');
    }).join('\n');
}

function getIssueBoardDueDays(row) {
    if (row && row.dueDays !== null && row.dueDays !== undefined && row.dueDays !== '') {
        return row.dueDays;
    }

    var targetDate = getIssueBoardEffectiveTargetDate(row);
    if (!targetDate) {
        return '';
    }

    var target = new Date(targetDate + 'T00:00:00');
    if (Number.isNaN(target.getTime())) {
        return '';
    }

    var today = new Date();
    var todayDate = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    var msPerDay = 24 * 60 * 60 * 1000;
    return Math.round((target.getTime() - todayDate.getTime()) / msPerDay);
}

function getIssueBoardStatusLabel(status) {
    var percent = getIssueBoardStatusPercent(status);
    var stage = getIssueBoardPdcaStage(percent);
    return stage;
}

function getIssueBoardStatusPercent(status) {
    if (status === null || status === undefined || status === '') {
        return 0;
    }

    var normalized = String(status).trim().toLowerCase();
    if (normalized.slice(-1) === '%') {
        return clampIssueBoardPercent(normalized.slice(0, -1));
    }
    if (normalized === 'done' || normalized === 'closed') {
        return 100;
    }
    if (normalized === 'in-progress' || normalized === 'in progress') {
        return 50;
    }
    if (normalized === 'open') {
        return 0;
    }

    return clampIssueBoardPercent(normalized);
}

function clampIssueBoardPercent(value) {
    var parsed = Number(value);
    if (!Number.isFinite(parsed)) {
        return 0;
    }
    return Math.min(100, Math.max(0, Math.round(parsed)));
}

function getIssueBoardPdcaStage(percent) {
    if (percent >= 100) return 'A';
    if (percent >= 75) return 'C';
    if (percent >= 50) return 'D';
    if (percent >= 25) return 'P';
    return '-';
}

function formatIssueHistoryDate(value) {
    if (!value) {
        return '';
    }

    var normalized = String(value).replace('T', ' ');
    return normalized.length > 16 ? normalized.slice(0, 16) : normalized;
}
