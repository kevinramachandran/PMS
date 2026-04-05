// Settings Page JavaScript

$(document).ready(function() {
    let currentForm = 'priorities';
    let currentType = '';
    const requestedConfig = (new URLSearchParams(window.location.search).get('config') || '').trim().toLowerCase();

    const metricFields = [
        'productionProductivityFtdActual', 'productionProductivityFtdTarget',
        'logisticsProductivityFtdActual', 'logisticsProductivityFtdTarget',
        'kpiSensoryScoreFtdActual', 'kpiSensoryScoreFtdTarget',
        'kpiConsumerComplaintUnitsMhlFtdActual', 'kpiConsumerComplaintUnitsMhlFtdTarget',
        'kpiCustomerComplaintUnitsMhlFtdActual', 'kpiCustomerComplaintUnitsMhlFtdTarget',
        'processConfirmationBpFtdActual', 'processConfirmationBpFtdTarget',
        'processConfirmationPackMtdActual', 'processConfirmationPackMtdTarget',
        'kpiOeeFtdActual', 'kpiOeeFtdTarget',
        'kpiBeerLossFtdActual', 'kpiBeerLossFtdTarget',
        'kpiWurHlHlFtdActual', 'kpiWurHlHlFtdTarget',
        'kpiElectricityKwhHlFtdActual', 'kpiElectricityKwhHlFtdTarget',
        'kpiEnergyKwhHlFtdActual', 'kpiEnergyKwhHlFtdTarget',
        'kpiRgbRatioFtdActual', 'kpiRgbRatioFtdTarget'
    ];

    const metricSections = {
        people: [
            'productionProductivityFtdActual', 'productionProductivityFtdTarget',
            'logisticsProductivityFtdActual', 'logisticsProductivityFtdTarget'
        ],
        quality: [
            'kpiSensoryScoreFtdActual', 'kpiSensoryScoreFtdTarget',
            'kpiConsumerComplaintUnitsMhlFtdActual', 'kpiConsumerComplaintUnitsMhlFtdTarget',
            'kpiCustomerComplaintUnitsMhlFtdActual', 'kpiCustomerComplaintUnitsMhlFtdTarget'
        ],
        service: [
            'processConfirmationBpFtdActual', 'processConfirmationBpFtdTarget',
            'processConfirmationPackMtdActual', 'processConfirmationPackMtdTarget',
            'kpiOeeFtdActual', 'kpiOeeFtdTarget',
            'kpiBeerLossFtdActual', 'kpiBeerLossFtdTarget',
            'kpiWurHlHlFtdActual', 'kpiWurHlHlFtdTarget'
        ],
        cost: [
            'kpiElectricityKwhHlFtdActual', 'kpiElectricityKwhHlFtdTarget',
            'kpiEnergyKwhHlFtdActual', 'kpiEnergyKwhHlFtdTarget',
            'kpiRgbRatioFtdActual', 'kpiRgbRatioFtdTarget'
        ]
    };

    $('input[type="date"]').removeAttr('max');

    initializeMetricsDateField();
    initializeIssueBoardConfigDateField();
    initializeGembaScheduleDateField();
    initializeAtConfigDateField();
    initializeLgtConfigDateField();
    initializeTrainingScheduleDateField();
    initializeMeetingAgendaDateField();
    initializeProcessConfirmationDateField();
    initializeProcessConfirmationStatusEditors();

    // ==================== CONFIG ITEM CLICK HANDLERS ====================
    $('.config-item').on('click', function(e) {
        e.preventDefault();
        const config = $(this).data('config');
        const type = $(this).data('type');

        $('.config-item').removeClass('active');
        $(this).addClass('active');
        showForm(config, type);
    });

    // ==================== FORM SWITCHING ====================
    function showForm(config, type) {
        $('.form-section').removeClass('active');
        $('.form-message').removeClass('show');

        currentForm = config;
        currentType = type;

        if (config === 'priorities') {
            $('#form-priorities').addClass('active');
            loadPrioritiesData();
        } else if (config === 'weekly-priorities') {
            $('#form-weekly-priorities').addClass('active');
            loadWeeklyPrioritiesData();
        } else if (config === 'daily-performance') {
            $('#form-daily-performance').addClass('active');
            loadDailyPerformanceData();
        } else if (config === 'daily-section') {
            $('#form-daily-section').addClass('active');
            $('#dailySectionTitle').text(getSectionTitle(type) + ' - Daily');
            loadDailyData(type);
        } else if (config === 'metrics-data') {
            $('#form-metrics-data').addClass('active');
            loadMetricsDataByDate($('#metricsDateInput').val());
        } else if (config === 'issue-board') {
            $('#form-issue-board').addClass('active');
            loadIssueBoardByDate($('#issueBoardConfigDate').val());
        } else if (config === 'gemba-schedule') {
            $('#form-gemba-schedule').addClass('active');
            loadGembaScheduleByDate($('#gembaScheduleDate').val());
        } else if (config === 'abnormality-tracker') {
            $('#form-abnormality-tracker').addClass('active');
            loadAtConfigByDate($('#atConfigDate').val());
        } else if (config === 'leadership-gemba-tracker') {
            $('#form-leadership-gemba-tracker').addClass('active');
            loadLgtConfigByDate($('#lgtConfigDate').val());
        } else if (config === 'training-schedule') {
            $('#form-training-schedule').addClass('active');
            loadTrainingScheduleByDate($('#trainingConfigDate').val());
        } else if (config === 'meeting-agenda') {
            $('#form-meeting-agenda').addClass('active');
            loadMeetingAgendaByDate($('#meetingAgendaConfigDate').val());
        } else if (config === 'process-confirmation') {
            $('#form-process-confirmation').addClass('active');
            loadProcessConfirmationByDate($('#pcConfigDate').val());
        } else if (config === 'hs-cross') {
            $('#form-hs-cross').addClass('active');
            window.dispatchEvent(new Event('hs-cross-open'));
        } else if (config === 'lsr-tracking') {
            $('#form-lsr-tracking').addClass('active');
            window.dispatchEvent(new Event('lsr-tracking-open'));
        }
    }

    // ==================== GET SECTION TITLE ====================
    function getSectionTitle(type) {
        const titles = {
            'PEOPLE': 'People',
            'QUALITY': 'Quality',
            'SERVICE': 'Service',
            'COST': 'Cost'
        };
        return titles[type] || type;
    }

    function getLocalDateString() {
        const today = new Date();
        return today.getFullYear() + '-' +
               String(today.getMonth() + 1).padStart(2, '0') + '-' +
               String(today.getDate()).padStart(2, '0');
    }

    // ==================== PRIORITIES FORM ====================
    function loadPrioritiesData() {
        const today = getLocalDateString();

        $.ajax({
            url: `/api/priorities/type/TOP_3/date/${today}`,
            type: 'GET',
            success: function(data) {
                if (data && data.length > 0) {
                    const item = data[0];
                    $('#priority1').val(item.priority1 || '');
                    $('#priority2').val(item.priority2 || '');
                    $('#priority3').val(item.priority3 || '');
                } else {
                    $('#priority1').val('');
                    $('#priority2').val('');
                    $('#priority3').val('');
                }
            },
            error: function() {
                $('#priority1').val('');
                $('#priority2').val('');
                $('#priority3').val('');
            }
        });
    }

    $('#prioritiesForm').on('submit', function(e) {
        e.preventDefault();

        const today = new Date();
        const dateStr = today.getFullYear() + '-' + 
                      String(today.getMonth() + 1).padStart(2, '0') + '-' + 
                      String(today.getDate()).padStart(2, '0');

        const data = {
            priority1: $('#priority1').val(),
            priority2: $('#priority2').val(),
            priority3: $('#priority3').val(),
            type: 'TOP_3',
            date: dateStr
        };

        // Replace for today (overwrite mode)
        $.ajax({
            url: '/api/priorities/replace/type/TOP_3/date/' + dateStr,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(data),
            success: function(response) {
                console.log('Priorities saved:', response);
                showMessage('prioritiesMessage', 'TOP 3 Priorities saved successfully!', 'success');
                setTimeout(function() {
                    updateKPIDashboard();
                }, 500);
            },
            error: function(xhr) {
                console.error('Error saving priorities:', xhr);
                showMessage('prioritiesMessage', 'Error saving priorities. Please try again.', 'error');
            }
        });
    });

    // ==================== WEEKLY PRIORITIES FORM ====================
    function loadWeeklyPrioritiesData() {
        const today = getLocalDateString();

        $.ajax({
            url: `/api/priorities/type/WEEKLY/date/${today}`,
            type: 'GET',
            success: function(data) {
                if (data && data.length > 0) {
                    const item = data[0];
                    $('#weekly1').val(item.priority1 || '');
                    $('#weekly2').val(item.priority2 || '');
                    $('#weekly3').val(item.priority3 || '');
                } else {
                    $('#weekly1').val('');
                    $('#weekly2').val('');
                    $('#weekly3').val('');
                }
            },
            error: function() {
                $('#weekly1').val('');
                $('#weekly2').val('');
                $('#weekly3').val('');
            }
        });
    }

    $('#weeklyPrioritiesForm').on('submit', function(e) {
        e.preventDefault();

        const today = new Date();
        const dateStr = today.getFullYear() + '-' + 
                      String(today.getMonth() + 1).padStart(2, '0') + '-' + 
                      String(today.getDate()).padStart(2, '0');

        const data = {
            priority1: $('#weekly1').val(),
            priority2: $('#weekly2').val(),
            priority3: $('#weekly3').val(),
            type: 'WEEKLY',
            date: dateStr
        };

        // Replace for today (overwrite mode)
        $.ajax({
            url: '/api/priorities/replace/type/WEEKLY/date/' + dateStr,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(data),
            success: function(response) {
                console.log('Weekly priorities saved:', response);
                showMessage('weeklyPrioritiesMessage', 'Weekly Priorities saved successfully!', 'success');
                setTimeout(function() {
                    updateKPIDashboard();
                }, 500);
            },
            error: function(xhr) {
                console.error('Error saving weekly priorities:', xhr);
                showMessage('weeklyPrioritiesMessage', 'Error saving weekly priorities. Please try again.', 'error');
            }
        });
    });

    // ==================== DAILY PERFORMANCE FORM ====================
    function loadDailyPerformanceData() {
        $.ajax({
            url: '/api/daily-performance/today',
            type: 'GET',
            success: function(data) {
                if (!data) {
                    resetDailyPerformanceForm();
                    return;
                }

                $('#monthTargetInput').val(data.month_target ?? '');
                $('#actualMtdInput').val(data.actual_mtd ?? '');
                $('#dailyTargetInput').val(data.daily_target ?? '');
                $('#yesterdayInput').val(data.yesterday ?? '');
            },
            error: function(xhr) {
                if (xhr.status === 204 || xhr.status === 404) {
                    resetDailyPerformanceForm();
                    return;
                }
                showMessage('dailyPerformanceMessage', 'Unable to load Daily Performance data.', 'error');
            }
        });
    }

    function resetDailyPerformanceForm() {
        $('#monthTargetInput').val('');
        $('#actualMtdInput').val('');
        $('#dailyTargetInput').val('');
        $('#yesterdayInput').val('');
    }

    $('#dailyPerformanceForm').on('submit', function(e) {
        e.preventDefault();

        const payload = {
            month_target: parseNumericField('#monthTargetInput'),
            actual_mtd: parseNumericField('#actualMtdInput'),
            daily_target: parseNumericField('#dailyTargetInput'),
            yesterday: parseNumericField('#yesterdayInput')
        };

        if (payload.month_target === null || payload.actual_mtd === null ||
            payload.daily_target === null || payload.yesterday === null) {
            showMessage('dailyPerformanceMessage', 'All Daily Performance fields are required and must be numeric.', 'error');
            return;
        }

        $.ajax({
            url: '/api/daily-performance',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function() {
                showMessage('dailyPerformanceMessage', 'Daily Performance saved successfully!', 'success');
                updateKPIDashboard();
            },
            error: function() {
                showMessage('dailyPerformanceMessage', 'Error saving Daily Performance. Please try again.', 'error');
            }
        });
    });

    function parseNumericField(selector) {
        const rawValue = $(selector).val();
        if (rawValue === null || rawValue === undefined || rawValue === '') {
            return null;
        }

        const parsed = Number(rawValue);
        return Number.isFinite(parsed) ? parsed : null;
    }

    // ==================== DAILY SECTIONS FORM ====================
    function loadDailyData(type) {
        const today = new Date().toISOString().split('T')[0];

        // Fetch data from API
        $.ajax({
            url: `/api/daily-data/type/${type}/date/${today}`,
            type: 'GET',
            success: function(data) {
                populateDailyTable(data);
            },
            error: function() {
                // Show empty state when nothing is found
                populateDailyTable([]);
            }
        });
    }

    function populateDailyTable(data) {
        const tbody = $('#dailyDataTableBody');
        tbody.empty();

        if (data.length === 0) {
            tbody.html('<tr class="placeholder-row"><td colspan="4" style="text-align: center; padding: 20px; color: #9ca3af;">No data available. Click "Add New Row" to create entries.</td></tr>');
        } else {
            data.forEach(item => {
                const row = createTableRow(item);
                tbody.append(row);
            });
        }

        // Re-enable delete buttons
        bindDeleteButtons();
    }

    function createTableRow(item = null) {
        const id = item ? item.id : '';
        const toDo = item ? item.toDo : '';
        const fpr = item ? item.fpr : '';
        const status = item ? item.status : 'Open';

        return `
            <tr data-id="${id}">
                <td><input type="text" class="todo-input" value="${toDo}" placeholder="Enter TO DO"></td>
                <td><input type="text" class="fpr-input" value="${fpr}" placeholder="Enter FPR"></td>
                <td>
                    <select class="status-select">
                        <option value="Open" ${status === 'Open' ? 'selected' : ''}>Open</option>
                        <option value="Closed" ${status === 'Closed' ? 'selected' : ''}>Closed</option>
                    </select>
                </td>
                <td><button type="button" class="btn-delete" data-id="${id}">Delete</button></td>
            </tr>
        `;
    }

    // Add new row button
    $('#addRowBtn').on('click', function() {
        $('#dailyDataTableBody .placeholder-row').remove();
        const row = createTableRow();
        $('#dailyDataTableBody').append(row);
        bindDeleteButtons();
    });

    // Bind delete buttons
    function bindDeleteButtons() {
        $('.btn-delete').off('click').on('click', function() {
            const id = $(this).data('id');
            if (id) {
                // Delete from API
                $.ajax({
                    url: `/api/daily-data/${id}`,
                    type: 'DELETE',
                    success: function() {
                        $(this).closest('tr').remove();
                        showMessage('dailySectionMessage', 'Entry deleted successfully!', 'success');
                    }.bind(this)
                });
            } else {
                $(this).closest('tr').remove();
            }
        });
    }

    // Save daily data
    $('#saveDailyBtn').on('click', function() {
        if (!currentType) {
            showMessage('dailySectionMessage', 'Please select a daily section first.', 'error');
            return;
        }

        const rows = $('#dailyDataTableBody tr').filter(function() {
            return $(this).find('.todo-input').length > 0;
        });
        let validationFailed = false;

        if (rows.length === 0) {
            showMessage('dailySectionMessage', 'Please add at least one entry before saving.', 'error');
            return;
        }

        const entries = [];
        rows.each(function() {
            const existingId = $(this).attr('data-id') || null;
            const toDo = $(this).find('.todo-input').val().trim();
            const fpr = $(this).find('.fpr-input').val().trim();
            const status = $(this).find('.status-select').val();

            if (!toDo || !fpr) {
                validationFailed = true;
                return false;
            }

            const today = new Date();
            const dateStr = today.getFullYear() + '-' + 
                          String(today.getMonth() + 1).padStart(2, '0') + '-' + 
                          String(today.getDate()).padStart(2, '0');

            entries.push({
                id: existingId,
                toDo: toDo,
                fpr: fpr,
                status: status,
                type: currentType,
                date: dateStr
            });
        });

        if (validationFailed) {
            showMessage('dailySectionMessage', 'All fields are required for each entry.', 'error');
            return;
        }

        if (entries.length === 0) {
            showMessage('dailySectionMessage', 'No valid entries to save.', 'error');
            return;
        }

        const saveDate = entries[0].date;
        const payload = entries.map(({ toDo, fpr, status, type, date }) => ({ toDo, fpr, status, type, date }));

        console.log('Replacing daily entries for type: ' + currentType + ', date: ' + saveDate);
        console.log('Payload:', payload);

        $.ajax({
            url: '/api/daily-data/replace/type/' + currentType + '/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(response) {
                const count = Array.isArray(response) ? response.length : entries.length;
                showMessage('dailySectionMessage', 'Saved ' + count + ' entries successfully!', 'success');
                setTimeout(function() {
                    loadDailyData(currentType);
                    updateKPIDashboard();
                }, 500);
            },
            error: function(xhr, status, error) {
                console.error('Error replacing daily entries:', error);
                console.error('Response status:', xhr.status);
                console.error('Response text:', xhr.responseText);
                showMessage('dailySectionMessage', 'Error saving: ' + (xhr.responseJSON?.message || xhr.statusText || error), 'error');
            }
        });
    });

    // ==================== METRICS DATA FORM ====================
    $('#metricsDateInput').on('change', function() {
        const selectedDate = $(this).val();
        if (!selectedDate) {
            return;
        }

        loadMetricsDataByDate(selectedDate);
    });

    $('.metrics-tab').on('click', function() {
        const tab = $(this).data('tab');
        $('.metrics-tab').removeClass('active');
        $(this).addClass('active');
        $('.metrics-tab-panel').removeClass('active');
        $('#metrics-panel-' + tab).addClass('active');
    });

    $('#metricsDataForm').on('submit', function(e) {
        e.preventDefault();
    });

    $('.metrics-save-btn').on('click', function() {
        const section = $(this).data('section');
        const selectedDate = $('#metricsDateInput').val();
        const $btn = $(this);

        if (!selectedDate) {
            showMessage('metricsDataMessage', 'Please select a metrics date.', 'error');
            showMetricsToast('Please select a metrics date.', 'error');
            return;
        }

        const buildResult = buildSectionPayload(section);
        if (!buildResult.ok) {
            showMessage('metricsDataMessage', buildResult.message, 'error');
            showMetricsToast(buildResult.message, 'error');
            return;
        }

        const payload = buildResult.payload;
        if (Object.keys(payload).length === 0) {
            showMessage('metricsDataMessage', 'Enter at least one value in this tab before saving.', 'error');
            showMetricsToast('No values to save in this tab.', 'error');
            return;
        }

        setMetricsSaveLoading($btn, true);

        $.ajax({
            url: '/api/production-metrics/' + selectedDate,
            type: 'PATCH',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function() {
                const successMessage = section.charAt(0).toUpperCase() + section.slice(1) + ' metrics saved successfully!';
                showMessage('metricsDataMessage', successMessage, 'success');
                showMetricsToast(successMessage, 'success');
                updateKPIDashboard();
                loadMetricsDataByDate(selectedDate);
            },
            error: function(xhr) {
                const message = xhr.responseText || 'Error saving metrics data. Please try again.';
                showMessage('metricsDataMessage', message, 'error');
                showMetricsToast(message, 'error');
            },
            complete: function() {
                setMetricsSaveLoading($btn, false);
            }
        });
    });

    function initializeMetricsDateField() {
        const today = getTodayDateString();
        const dateInput = $('#metricsDateInput');
        dateInput.removeAttr('max');
        dateInput.val(today);
    }

    function loadMetricsDataByDate(dateStr) {
        if (!dateStr) {
            resetMetricsForm();
            return;
        }

        $.ajax({
            url: '/api/production-metrics?date=' + dateStr,
            type: 'GET',
            success: function(data) {
                if (!data || Object.keys(data).length === 0) {
                    resetMetricsForm();
                    return;
                }

                fillMetricsForm(data);
            },
            error: function(xhr) {
                if (xhr.status === 404 || xhr.status === 204) {
                    resetMetricsForm();
                    return;
                }
                showMessage('metricsDataMessage', 'Unable to load metrics for the selected date.', 'error');
            }
        });
    }

    function fillMetricsForm(data) {
        metricFields.forEach(function(field) {
            $('#' + field).val(data[field] ?? '');
        });
    }

    function resetMetricsForm() {
        metricFields.forEach(function(field) {
            $('#' + field).val('');
        });
    }

    function buildSectionPayload(section) {
        const fields = metricSections[section] || [];
        const payload = {};

        for (const field of fields) {
            const element = $('#' + field);
            const rawValue = element.val();

            if (rawValue === null || rawValue === undefined || rawValue === '') {
                continue;
            }

            const parsed = Number(rawValue);
            if (!Number.isFinite(parsed)) {
                return {
                    ok: false,
                    message: 'Please enter valid numeric values only.'
                };
            }

            if (parsed < 0) {
                return {
                    ok: false,
                    message: 'Negative values are not allowed in metrics data.'
                };
            }

            payload[field] = parsed;
        }

        return {
            ok: true,
            payload: payload
        };
    }

    function setMetricsSaveLoading($btn, isLoading) {
        if (isLoading) {
            $btn.prop('disabled', true);
            $btn.data('original-html', $btn.html());
            $btn.html('<i class="fas fa-spinner fa-spin"></i> Saving...');
            return;
        }

        $btn.prop('disabled', false);
        const originalHtml = $btn.data('original-html');
        if (originalHtml) {
            $btn.html(originalHtml);
        }
    }

    function showMetricsToast(message, type) {
        const $toast = $('#metricsToast');
        if ($toast.length === 0) {
            return;
        }

        $toast.removeClass('success error show').addClass(type).text(message);
        $toast.addClass('show');

        setTimeout(function() {
            $toast.removeClass('show');
        }, 2600);
    }

    function getTodayDateString() {
        const today = new Date();
        return today.getFullYear() + '-' +
            String(today.getMonth() + 1).padStart(2, '0') + '-' +
            String(today.getDate()).padStart(2, '0');
    }

    // ==================== ISSUE BOARD CONFIGURATION ====================
    function initializeIssueBoardConfigDateField() {
        const today = getTodayDateString();
        const dateInput = $('#issueBoardConfigDate');
        dateInput.removeAttr('max');
        dateInput.val(today);

        applyIssueBoardDateLimits();

        $('#issueBoardLastReviewDate, #issueBoardNextReviewDate').off('change').on('change', function() {
            const selected = $(this).val();
            if (!selected) {
                return;
            }
        });
    }

    function showIssueBoardPopup(message) {
        const $popup = $('#issueBoardPopup');
        if ($popup.length === 0 || !message) {
            return;
        }

        $popup.text(message).addClass('show');
        window.clearTimeout(window.__issueBoardPopupTimer);
        window.__issueBoardPopupTimer = window.setTimeout(function() {
            $popup.removeClass('show');
        }, 2600);
    }

    function applyIssueBoardDateLimits() {
        $('#issueBoardLastReviewDate').removeAttr('max');
        $('#issueBoardNextReviewDate').removeAttr('max');
        $('#ibFormIssueDate, #ibFormCompletedDate, #ibFormTargetDate').removeAttr('max');
        $('#issueBoardConfigTableBody .ib-issue-date, #issueBoardConfigTableBody .ib-completed-date, #issueBoardConfigTableBody .ib-target-date').removeAttr('max');
    }

    function normalizeIssueStatus(status) {
        if (status === null || status === undefined || status === '') {
            return '0%';
        }

        const normalized = String(status).trim().toLowerCase();
        if (normalized.endsWith('%')) {
            const parsed = Number(normalized.replace('%', ''));
            if (Number.isFinite(parsed)) {
                return Math.min(100, Math.max(0, Math.round(parsed))) + '%';
            }
        }

        if (normalized === 'done' || normalized === 'closed') {
            return '100%';
        }

        if (normalized === 'in-progress' || normalized === 'in progress') {
            return '50%';
        }

        return '0%';
    }

    function calculateDueDaysFromTarget(targetDate) {
        if (!targetDate) {
            return '';
        }

        const target = new Date(targetDate + 'T00:00:00');
        if (Number.isNaN(target.getTime())) {
            return '';
        }

        const today = new Date();
        const todayDate = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        const msPerDay = 24 * 60 * 60 * 1000;
        return Math.round((target.getTime() - todayDate.getTime()) / msPerDay);
    }

    function updateRowDueDays($row) {
        const targetDate = $row.find('.ib-target-date').val();
        const dueDays = calculateDueDaysFromTarget(targetDate);
        $row.find('.ib-due-days').val(dueDays);
    }

    function updateCompletedDateRequirement($row) {
        const status = $row.find('.ib-status').val();
        const $completedDate = $row.find('.ib-completed-date');
        const isCompleted = status === '100%';

        $completedDate.prop('required', isCompleted);
        $completedDate.toggleClass('required', isCompleted && !$completedDate.val());
    }

    function bindIssueBoardRowEvents() {
        $('#issueBoardConfigTableBody .ib-target-date').off('change').on('change', function() {
            const $row = $(this).closest('tr');
            updateRowDueDays($row);
        });

        $('#issueBoardConfigTableBody .ib-issue-date, #issueBoardConfigTableBody .ib-completed-date').off('change').on('change', function() {
            updateCompletedDateRequirement($(this).closest('tr'));
        });

        $('#issueBoardConfigTableBody .ib-status').off('change').on('change', function() {
            updateCompletedDateRequirement($(this).closest('tr'));
        });

        $('#issueBoardConfigTableBody tr').each(function() {
            updateRowDueDays($(this));
            updateCompletedDateRequirement($(this));
        });
    }

    function loadIssueBoardByDate(dateStr) {
        if (!dateStr) {
            populateIssueBoardConfigTable([]);
            return;
        }

        $.ajax({
            url: '/api/issue-board/date/' + dateStr,
            type: 'GET',
            success: function(data) {
                populateIssueBoardConfigTable(Array.isArray(data) ? data : []);
            },
            error: function() {
                populateIssueBoardConfigTable([]);
            }
        });
    }

    function populateIssueBoardConfigTable(items) {
        const tbody = $('#issueBoardConfigTableBody');
        tbody.empty();
        unlockIssueBoardRows();
        applyIssueBoardDateLimits();

        if (!items || items.length === 0) {
            $('#issueBoardLastReviewDate').val('');
            $('#issueBoardNextReviewDate').val('');
            tbody.html('<tr class="placeholder-row"><td colspan="12" style="text-align:center; padding: 18px; color:#9ca3af;">No issue board data for selected date. Click "Add New Row".</td></tr>');
            return;
        }

        $('#issueBoardLastReviewDate').val(items[0].lastReviewDate || '');
        $('#issueBoardNextReviewDate').val(items[0].nextReviewDate || '');

        items.forEach(function(item) {
            tbody.append(createIssueBoardConfigRow(item));
        });

        bindIssueBoardDeleteButtons();
        bindIssueBoardRowEvents();
        applyIssueBoardDateLimits();
    }

    function getIssueFormRowData() {
        return {
            problem: $('#ibFormProblem').val().trim(),
            ownerName: $('#ibFormOwner').val().trim(),
            issueDate: $('#ibFormIssueDate').val() || '',
            rootCause: $('#ibFormRootCause').val().trim(),
            actions: $('#ibFormActions').val().trim(),
            responsible: $('#ibFormResponsible').val().trim(),
            targetDate: $('#ibFormTargetDate').val() || '',
            status: normalizeIssueStatus($('#ibFormStatus').val() || '0%'),
            completedDate: $('#ibFormCompletedDate').val() || '',
            remarks: $('#ibFormRemarks').val().trim()
        };
    }

    function populateIssueFormFromRow($row) {
        $('#ibFormProblem').val($row.find('.ib-problem').val() || '');
        $('#ibFormOwner').val($row.find('.ib-owner').val() || '');
        $('#ibFormIssueDate').val($row.find('.ib-issue-date').val() || '');
        $('#ibFormRootCause').val($row.find('.ib-root-cause').val() || '');
        $('#ibFormActions').val($row.find('.ib-actions').val() || '');
        $('#ibFormResponsible').val($row.find('.ib-responsible').val() || '');
        $('#ibFormTargetDate').val($row.find('.ib-target-date').val() || '');
        $('#ibFormStatus').val(normalizeIssueStatus($row.find('.ib-status').val() || '0%'));
        $('#ibFormCompletedDate').val($row.find('.ib-completed-date').val() || '');
        $('#ibFormRemarks').val($row.find('.ib-remarks').val() || '');
    }

    function resetIssueFormInputs() {
        $('#ibFormProblem, #ibFormOwner, #ibFormRootCause, #ibFormActions, #ibFormResponsible, #ibFormRemarks').val('');
        $('#ibFormIssueDate, #ibFormTargetDate, #ibFormCompletedDate').val('');
        $('#ibFormStatus').val('0%');
    }

    function createIssueBoardConfigRow(item) {
        const safeItem = item || {};
        const status = normalizeIssueStatus(safeItem.status);
        const completedDate = safeItem.completedDate || '';

        return '' +
            '<tr data-id="' + escapeAttributeValue(safeItem.id || '') + '">' +
            '<td><input type="text" class="ib-problem" value="' + escapeAttributeValue(safeItem.problem || '') + '" placeholder="Problem"></td>' +
            '<td><input type="text" class="ib-owner" value="' + escapeAttributeValue(safeItem.ownerName || '') + '" placeholder="Name"></td>' +
            '<td><input type="date" class="ib-issue-date" value="' + escapeAttributeValue(safeItem.issueDate || '') + '"></td>' +
            '<td><input type="text" class="ib-root-cause" value="' + escapeAttributeValue(safeItem.rootCause || '') + '" placeholder="Root Cause"></td>' +
            '<td><input type="text" class="ib-actions" value="' + escapeAttributeValue(safeItem.actions || '') + '" placeholder="Actions"></td>' +
            '<td><input type="text" class="ib-responsible" value="' + escapeAttributeValue(safeItem.responsible || '') + '" placeholder="Responsible"></td>' +
            '<td><input type="date" class="ib-target-date" value="' + escapeAttributeValue(safeItem.targetDate || '') + '"></td>' +
            '<td><input type="number" class="ib-due-days" value="" step="1" readonly></td>' +
            '<td>' +
            '<select class="ib-status">' +
            '<option value="0%" ' + (status === '0%' ? 'selected' : '') + '>0%</option>' +
            '<option value="25%" ' + (status === '25%' ? 'selected' : '') + '>25%</option>' +
            '<option value="50%" ' + (status === '50%' ? 'selected' : '') + '>50%</option>' +
            '<option value="75%" ' + (status === '75%' ? 'selected' : '') + '>75%</option>' +
            '<option value="100%" ' + (status === '100%' ? 'selected' : '') + '>100%</option>' +
            '</select>' +
            '</td>' +
            '<td><input type="date" class="ib-completed-date" value="' + escapeAttributeValue(completedDate) + '"></td>' +
            '<td><input type="text" class="ib-remarks" value="' + escapeAttributeValue(safeItem.remarks || '') + '" placeholder="Remarks"></td>' +
            '<td><button type="button" class="btn btn-secondary issue-edit">Edit</button> <button type="button" class="btn-delete issue-delete">Delete</button></td>' +
            '</tr>';
    }

    $('#addIssueBoardRowBtn').on('click', function() {
        if ($('#issueBoardConfigTableBody').attr('data-locked') === '1') {
            showIssueBoardPopup('Edit is locked after save. Use Cancel to unlock.');
            return;
        }

        const formData = getIssueFormRowData();
        if (!formData.problem || !formData.actions || !formData.responsible) {
            showIssueBoardPopup('Problem, Actions, and Responsible are required before adding a row.');
            return;
        }

        if (formData.status === '100%' && !formData.completedDate) {
            showIssueBoardPopup('Completed date is required when status is 100%.');
            return;
        }

        $('#issueBoardConfigTableBody .placeholder-row').remove();
        $('#issueBoardConfigTableBody').append(createIssueBoardConfigRow(formData));
        bindIssueBoardDeleteButtons();
        bindIssueBoardRowEvents();
        applyIssueBoardDateLimits();
        resetIssueFormInputs();
    });

    function bindIssueBoardDeleteButtons() {
        $('.issue-delete').off('click').on('click', function() {
            $(this).closest('tr').remove();
            if ($('#issueBoardConfigTableBody tr').length === 0) {
                $('#issueBoardConfigTableBody').html('<tr class="placeholder-row"><td colspan="12" style="text-align:center; padding: 18px; color:#9ca3af;">No issue board data for selected date. Click "Add New Row".</td></tr>');
            }
        });

        $('.issue-edit').off('click').on('click', function() {
            const $row = $(this).closest('tr');
            populateIssueFormFromRow($row);
            $row.remove();
            if ($('#issueBoardConfigTableBody tr').length === 0) {
                $('#issueBoardConfigTableBody').html('<tr class="placeholder-row"><td colspan="12" style="text-align:center; padding: 18px; color:#9ca3af;">No issue board data for selected date. Click "Add New Row".</td></tr>');
            }
            showIssueBoardPopup('Row loaded into form for editing. Update and click Add Row.');
        });
    }

    function lockIssueBoardRowsForStatusUpdates() {
        const tbody = $('#issueBoardConfigTableBody');
        tbody.attr('data-locked', '1');

        tbody.find('tr').each(function() {
            $(this).find('input, select').each(function() {
                const isAllowed = $(this).hasClass('ib-status') || $(this).hasClass('ib-completed-date');
                $(this).prop('disabled', !isAllowed);
            });
            $(this).find('.issue-delete').prop('disabled', true);
            $(this).find('.issue-edit').prop('disabled', true);
        });

        $('#addIssueBoardRowBtn').prop('disabled', true);
    }

    function unlockIssueBoardRows() {
        const tbody = $('#issueBoardConfigTableBody');
        tbody.attr('data-locked', '0');
        tbody.find('input, select, .issue-delete, .issue-edit').prop('disabled', false);
        $('#addIssueBoardRowBtn').prop('disabled', false);
    }

    $('#cancelIssueBoardBtn').on('click', function() {
        const saveDate = $('#issueBoardConfigDate').val() || getTodayDateString();
        unlockIssueBoardRows();
        loadIssueBoardByDate(saveDate);
        showIssueBoardPopup('Changes discarded.');
    });

    $('#saveIssueBoardBtn').on('click', function() {
        const saveDate = $('#issueBoardConfigDate').val();
        const lastReviewDate = $('#issueBoardLastReviewDate').val() || null;
        const nextReviewDate = $('#issueBoardNextReviewDate').val() || null;

        if (!saveDate) {
            showIssueBoardPopup('Issue date is not available. Reload the page and try again.');
            return;
        }

        const rows = $('#issueBoardConfigTableBody tr').filter(function() {
            return $(this).find('.ib-problem').length > 0;
        });

        if (rows.length === 0) {
            showIssueBoardPopup('Please add at least one issue row before saving.');
            return;
        }

        const payload = [];
        let invalid = false;

        rows.each(function(index) {
            const problem = $(this).find('.ib-problem').val().trim();
            const actions = $(this).find('.ib-actions').val().trim();
            const responsible = $(this).find('.ib-responsible').val().trim();
            const dueParsed = calculateDueDaysFromTarget($(this).find('.ib-target-date').val());
            const statusValue = $(this).find('.ib-status').val();
            const completedDate = $(this).find('.ib-completed-date').val() || null;

            if (!problem || !actions || !responsible) {
                invalid = true;
                return false;
            }

            if (dueParsed !== '' && !Number.isFinite(dueParsed)) {
                invalid = true;
                return false;
            }

            if (statusValue === '100%' && !completedDate) {
                invalid = true;
                $(this).find('.ib-completed-date').addClass('required');
                showIssueBoardPopup('Completed date is required when status is 100%.');
                return false;
            }

            payload.push({
                rowOrder: index + 1,
                problem: problem,
                ownerName: $(this).find('.ib-owner').val().trim(),
                issueDate: $(this).find('.ib-issue-date').val().trim(),
                rootCause: $(this).find('.ib-root-cause').val().trim(),
                actions: actions,
                responsible: responsible,
                targetDate: $(this).find('.ib-target-date').val() || null,
                dueDays: dueParsed === '' ? null : dueParsed,
                status: statusValue,
                remarks: $(this).find('.ib-remarks').val().trim(),
                lastReviewDate: lastReviewDate,
                nextReviewDate: nextReviewDate,
                boardDate: saveDate
            });
        });

        if (invalid) {
            showIssueBoardPopup('Problem, Actions, and Responsible are required for each row.');
            return;
        }

        $.ajax({
            url: '/api/issue-board/replace/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(response) {
                const count = Array.isArray(response) ? response.length : payload.length;
                showMessage('issueBoardMessage', 'Saved ' + count + ' issue rows successfully!', 'success');
                showIssueBoardPopup('Issue rows saved. Only Status and Completed Date remain editable.');
                localStorage.setItem('issue-board-update', Date.now());
                updateKPIDashboard();
                lockIssueBoardRowsForStatusUpdates();
            },
            error: function() {
                showIssueBoardPopup('Error saving Issue Board data. Please try again.');
            }
        });
    });

    // ==================== GEMBA SCHEDULE CONFIGURATION ====================
    function initializeGembaScheduleDateField() {
        const today = getTodayDateString();
        const dateInput = $('#gembaScheduleDate');
        dateInput.removeAttr('max');
        dateInput.val(today);
        updateGembaMonthLabel(today);

        dateInput.on('change', function() {
            const selectedDate = $(this).val();
            if (!selectedDate) {
                updateGembaMonthLabel('');
                return;
            }

            updateGembaMonthLabel(selectedDate);
            loadGembaScheduleByDate(selectedDate);
        });
    }

    function formatMonthLabelFromDate(dateStr) {
        if (!dateStr) {
            return '';
        }

        const dateObj = new Date(dateStr + 'T00:00:00');
        if (Number.isNaN(dateObj.getTime())) {
            return '';
        }

        const month = dateObj.toLocaleString('en-GB', { month: 'short' });
        return month + "'" + dateObj.getFullYear();
    }

    function updateGembaMonthLabel(dateStr) {
        $('#gembaMonthLabelInput').val(formatMonthLabelFromDate(dateStr));
    }

    function loadGembaScheduleByDate(dateStr) {
        if (!dateStr) {
            populateGembaScheduleTable([]);
            return;
        }

        $.ajax({
            url: '/api/gemba-schedule/date/' + dateStr,
            type: 'GET',
            success: function(data) {
                populateGembaScheduleTable(Array.isArray(data) ? data : []);
            },
            error: function() {
                populateGembaScheduleTable([]);
            }
        });
    }

    function populateGembaScheduleTable(items) {
        const tbody = $('#gembaConfigTableBody');
        tbody.empty();

        if (!items || items.length === 0) {
            updateGembaMonthLabel($('#gembaScheduleDate').val());
            tbody.html('<tr class="placeholder-row"><td colspan="7" style="text-align:center; padding: 18px; color:#9ca3af;">No schedule for selected date. Click "Add New Row".</td></tr>');
            return;
        }

        updateGembaMonthLabel($('#gembaScheduleDate').val());

        items.forEach(function(item) {
            tbody.append(createGembaConfigRow(item));
        });

        bindGembaDeleteButtons();
    }

    function getGembaFormRowData() {
        return {
            functionType: $('#gsFormFunctionType').val() || 'Packaging',
            associateName: $('#gsFormAssociateName').val().trim(),
            week1: $('#gsFormWeek1').val().trim(),
            week2: $('#gsFormWeek2').val().trim(),
            week3: $('#gsFormWeek3').val().trim(),
            week4: $('#gsFormWeek4').val().trim()
        };
    }

    function populateGembaFormFromRow($row) {
        $('#gsFormFunctionType').val($row.find('.gs-function-type').val() || 'Packaging');
        $('#gsFormAssociateName').val($row.find('.gs-associate-name').val() || '');
        $('#gsFormWeek1').val($row.find('.gs-week1').val() || '');
        $('#gsFormWeek2').val($row.find('.gs-week2').val() || '');
        $('#gsFormWeek3').val($row.find('.gs-week3').val() || '');
        $('#gsFormWeek4').val($row.find('.gs-week4').val() || '');
    }

    function resetGembaFormInputs() {
        $('#gsFormFunctionType').val('Packaging');
        $('#gsFormAssociateName, #gsFormWeek1, #gsFormWeek2, #gsFormWeek3, #gsFormWeek4').val('');
    }

    function createGembaConfigRow(item) {
        const safeItem = item || {};
        const functionType = safeItem.functionType || 'Packaging';
        const associateName = safeItem.associateName || safeItem.functionName || '';

        return '' +
            '<tr>' +
            '<td>' +
            '<select class="gs-function-type">' +
            '<option value="Packaging" ' + (functionType === 'Packaging' ? 'selected' : '') + '>Packaging</option>' +
            '<option value="Quality" ' + (functionType === 'Quality' ? 'selected' : '') + '>Quality</option>' +
            '<option value="HR & Admin" ' + (functionType === 'HR & Admin' ? 'selected' : '') + '>HR & Admin</option>' +
            '<option value="Utility & Maintenance" ' + (functionType === 'Utility & Maintenance' ? 'selected' : '') + '>Utility & Maintenance</option>' +
            '<option value="Customer Supply" ' + (functionType === 'Customer Supply' ? 'selected' : '') + '>Customer Supply</option>' +
            '<option value="B&P" ' + (functionType === 'B&P' ? 'selected' : '') + '>B&P</option>' +
            '<option value="Finance" ' + (functionType === 'Finance' ? 'selected' : '') + '>Finance</option>' +
            '<option value="Health & Safety" ' + (functionType === 'Health & Safety' ? 'selected' : '') + '>Health & Safety</option>' +
            '<option value="Carlsberg Excellence" ' + (functionType === 'Carlsberg Excellence' ? 'selected' : '') + '>Carlsberg Excellence</option>' +
            '</select>' +
            '</td>' +
            '<td><input type="text" class="gs-associate-name" value="' + escapeAttributeValue(associateName) + '" placeholder="Associate Name"></td>' +
            '<td><input type="text" class="gs-week1" value="' + escapeAttributeValue(safeItem.week1 || '') + '" placeholder="Week #1"></td>' +
            '<td><input type="text" class="gs-week2" value="' + escapeAttributeValue(safeItem.week2 || '') + '" placeholder="Week #2"></td>' +
            '<td><input type="text" class="gs-week3" value="' + escapeAttributeValue(safeItem.week3 || '') + '" placeholder="Week #3"></td>' +
            '<td><input type="text" class="gs-week4" value="' + escapeAttributeValue(safeItem.week4 || '') + '" placeholder="Week #4"></td>' +
            '<td><button type="button" class="btn btn-secondary gemba-edit">Edit</button> <button type="button" class="btn-delete gemba-delete">Delete</button></td>' +
            '</tr>';
    }

    $('#addGembaRowBtn').on('click', function() {
        const formData = getGembaFormRowData();
        if (!formData.associateName) {
            showMessage('gembaScheduleMessage', 'Associate name is required before adding a row.', 'error');
            return;
        }

        $('#gembaConfigTableBody .placeholder-row').remove();
        $('#gembaConfigTableBody').append(createGembaConfigRow(formData));
        bindGembaDeleteButtons();
        resetGembaFormInputs();
    });

    function bindGembaDeleteButtons() {
        $('.gemba-delete').off('click').on('click', function() {
            $(this).closest('tr').remove();
            if ($('#gembaConfigTableBody tr').length === 0) {
                $('#gembaConfigTableBody').html('<tr class="placeholder-row"><td colspan="7" style="text-align:center; padding: 18px; color:#9ca3af;">No schedule for selected date. Click "Add New Row".</td></tr>');
            }
        });

        $('.gemba-edit').off('click').on('click', function() {
            const $row = $(this).closest('tr');
            populateGembaFormFromRow($row);
            $row.remove();
            if ($('#gembaConfigTableBody tr').length === 0) {
                $('#gembaConfigTableBody').html('<tr class="placeholder-row"><td colspan="7" style="text-align:center; padding: 18px; color:#9ca3af;">No schedule for selected date. Click "Add New Row".</td></tr>');
            }
            showMessage('gembaScheduleMessage', 'Row loaded into form for editing. Update and click Add Row.', 'info');
        });
    }

    $('#saveGembaScheduleBtn').on('click', function() {
        const saveDate = $('#gembaScheduleDate').val();
        const monthLabel = formatMonthLabelFromDate(saveDate);

        if (!saveDate) {
            showMessage('gembaScheduleMessage', 'Please select a configuration date.', 'error');
            return;
        }

        const rows = $('#gembaConfigTableBody tr').filter(function() {
            return $(this).find('.gs-associate-name').length > 0;
        });

        if (rows.length === 0) {
            showMessage('gembaScheduleMessage', 'Please add at least one row before saving.', 'error');
            return;
        }

        let invalid = false;
        const payload = [];

        rows.each(function(index) {
            const functionType = $(this).find('.gs-function-type').val();
            const associateName = $(this).find('.gs-associate-name').val().trim();
            if (!associateName) {
                invalid = true;
                return false;
            }

            payload.push({
                rowOrder: index + 1,
                rowType: 'PERSON',
                functionType: functionType,
                associateName: associateName,
                functionName: associateName,
                functionColor: null,
                week1: $(this).find('.gs-week1').val().trim(),
                week2: $(this).find('.gs-week2').val().trim(),
                week3: $(this).find('.gs-week3').val().trim(),
                week4: $(this).find('.gs-week4').val().trim(),
                scheduleMonthLabel: monthLabel,
                scheduleDate: saveDate
            });
        });

        if (invalid) {
            showMessage('gembaScheduleMessage', 'Associate Name is required for each row.', 'error');
            return;
        }

        $.ajax({
            url: '/api/gemba-schedule/replace/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(response) {
                const count = Array.isArray(response) ? response.length : payload.length;
                showMessage('gembaScheduleMessage', 'Saved ' + count + ' rows successfully!', 'success');
                localStorage.setItem('gemba-schedule-update', Date.now());
                updateKPIDashboard();
                loadGembaScheduleByDate(saveDate);
            },
            error: function() {
                showMessage('gembaScheduleMessage', 'Error saving Gemba schedule. Please try again.', 'error');
            }
        });
    });

    function escapeAttributeValue(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    // ==================== ABNORMALITY TRACKER CONFIG ====================

    const AT_DEPARTMENTS = [
        'H&S and CE', 'Admin', 'B&P', 'Packaging', 'Quality', 'U&M', 'CS', 'Finance'
    ];

    function initializeAtConfigDateField() {
        const today = getTodayDateString();
        const $dateInput = $('#atConfigDate');
        $dateInput.removeAttr('max');
        $dateInput.val(today);
        updateAtPeriodLabel(today);

        $dateInput.on('change', function () {
            const selected = $(this).val();
            if (!selected) { updateAtPeriodLabel(''); return; }
            updateAtPeriodLabel(selected);
            loadAtConfigByDate(selected);
        });
    }

    function updateAtPeriodLabel(dateStr) {
        $('#atPeriodLabelInput').val(formatMonthLabelFromDate(dateStr));
    }

    function loadAtConfigByDate(dateStr) {
        if (!dateStr) { populateAtConfigTable([]); return; }
        const periodLabel = formatMonthLabelFromDate(dateStr);
        if (!periodLabel) { populateAtConfigTable([]); return; }

        $.ajax({
            url: '/api/abnormality-tracker/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function (data) {
                populateAtConfigTable(Array.isArray(data) ? data : []);
            },
            error: function () {
                populateAtConfigTable([]);
            }
        });
    }

    function populateAtConfigTable(items) {
        const tbody = $('#atConfigTableBody');
        tbody.empty();

        if (!items || items.length === 0) {
            tbody.html('<tr class="placeholder-row"><td colspan="5" style="text-align:center;padding:18px;color:#9ca3af;">No data for selected period. Click "Add Row".</td></tr>');
            return;
        }

        items.forEach(function (item) {
            tbody.append(createAtConfigRow(item));
        });

        bindAtDeleteButtons();
    }

    function createAtConfigRow(item) {
        const safeItem = item || {};
        const dept = safeItem.department || 'H&S and CE';
        const yellow = safeItem.yellowTags != null ? safeItem.yellowTags : '';
        const red = safeItem.redTags != null ? safeItem.redTags : '';
        const closure = safeItem.closurePercent != null ? safeItem.closurePercent : '';

        let deptOptions = '';
        AT_DEPARTMENTS.forEach(function (d) {
            deptOptions += '<option value="' + escapeAttributeValue(d) + '"' + (dept === d ? ' selected' : '') + '>' + escapeAttributeValue(d) + '</option>';
        });

        return '<tr>' +
            '<td><select class="at-department">' + deptOptions + '</select></td>' +
            '<td><input type="number" class="at-yellow" min="0" step="1" value="' + escapeAttributeValue(String(yellow)) + '" placeholder="0"></td>' +
            '<td><input type="number" class="at-red" min="0" step="1" value="' + escapeAttributeValue(String(red)) + '" placeholder="0"></td>' +
            '<td><input type="number" class="at-closure" min="0" max="100" step="0.1" value="' + escapeAttributeValue(String(closure)) + '" placeholder="0.0"></td>' +
            '<td><button type="button" class="btn-delete at-delete">Delete</button></td>' +
            '</tr>';
    }

    $('#addAtRowBtn').on('click', function () {
        $('#atConfigTableBody .placeholder-row').remove();
        $('#atConfigTableBody').append(createAtConfigRow(null));
        bindAtDeleteButtons();
    });

    function bindAtDeleteButtons() {
        $('.at-delete').off('click').on('click', function () {
            $(this).closest('tr').remove();
            if ($('#atConfigTableBody tr').length === 0) {
                $('#atConfigTableBody').html('<tr class="placeholder-row"><td colspan="5" style="text-align:center;padding:18px;color:#9ca3af;">No data for selected period. Click "Add Row".</td></tr>');
            }
        });
    }

    $('#saveAtConfigBtn').on('click', function () {
        const saveDate = $('#atConfigDate').val();
        if (!saveDate) {
            showMessage('atConfigMessage', 'Please select a configuration date.', 'error');
            return;
        }

        const periodLabel = formatMonthLabelFromDate(saveDate);

        const rows = $('#atConfigTableBody tr').filter(function () {
            return $(this).find('.at-department').length > 0;
        });

        if (rows.length === 0) {
            showMessage('atConfigMessage', 'Please add at least one row before saving.', 'error');
            return;
        }

        let invalid = false;
        const payload = [];

        rows.each(function (i) {
            const dept = $(this).find('.at-department').val().trim();
            const yellow = parseInt($(this).find('.at-yellow').val(), 10) || 0;
            const red = parseInt($(this).find('.at-red').val(), 10) || 0;
            const closure = parseFloat($(this).find('.at-closure').val()) || 0;

            if (!dept) { invalid = true; return false; }

            payload.push({
                rowOrder: i + 1,
                department: dept,
                yellowTags: yellow,
                redTags: red,
                closurePercent: closure,
                periodLabel: periodLabel
            });
        });

        if (invalid) {
            showMessage('atConfigMessage', 'Department name is required for each row.', 'error');
            return;
        }

        $.ajax({
            url: '/api/abnormality-tracker/replace/period/' + encodeURIComponent(periodLabel),
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function (response) {
                const count = Array.isArray(response) ? response.length : payload.length;
                showMessage('atConfigMessage', 'Saved ' + count + ' rows successfully!', 'success');
                localStorage.setItem('abnormality_tracker_updated', Date.now());
            },
            error: function () {
                showMessage('atConfigMessage', 'Error saving data. Please try again.', 'error');
            }
        });
    });

    // ==================== LEADERSHIP GEMBA TRACKER CONFIG ====================

    function formatShortPeriodFromDate(dateStr) {
        if (!dateStr) {
            return '';
        }
        const dateObj = new Date(dateStr + 'T00:00:00');
        if (Number.isNaN(dateObj.getTime())) {
            return '';
        }
        const month = dateObj.toLocaleString('en-GB', { month: 'short' });
        const year = String(dateObj.getFullYear()).slice(-2);
        return month + "'" + year;
    }

    function initializeLgtConfigDateField() {
        const today = getTodayDateString();
        const dateInput = $('#lgtConfigDate');
        dateInput.removeAttr('max');
        dateInput.val(today);
        updateLgtPeriodLabel(today);

        dateInput.on('change', function() {
            const selectedDate = $(this).val();
            if (!selectedDate) {
                updateLgtPeriodLabel('');
                return;
            }

            updateLgtPeriodLabel(selectedDate);
            loadLgtConfigByDate(selectedDate);
        });
    }

    function updateLgtPeriodLabel(dateStr) {
        $('#lgtPeriodLabelInput').val(formatShortPeriodFromDate(dateStr));
    }

    function loadLgtConfigByDate(dateStr) {
        if (!dateStr) {
            populateLgtConfigTable([]);
            return;
        }

        const periodLabel = formatShortPeriodFromDate(dateStr);
        if (!periodLabel) {
            populateLgtConfigTable([]);
            return;
        }

        $.ajax({
            url: '/api/leadership-gemba-tracker/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function(data) {
                populateLgtConfigTable(Array.isArray(data) ? data : []);
            },
            error: function() {
                populateLgtConfigTable([]);
            }
        });
    }

    function createLgtConfigRow(item) {
        const safe = item || {};
        function num(v) {
            return (v === null || v === undefined) ? '' : String(v);
        }

        return '' +
            '<tr>' +
            '<td><input type="text" class="lgt-manager-name" value="' + escapeAttributeValue(safe.managerName || '') + '" placeholder="Manager name"></td>' +
            '<td><input type="text" class="lgt-department" value="' + escapeAttributeValue(safe.department || '') + '" placeholder="Department"></td>' +
            '<td><input type="text" class="lgt-area" value="' + escapeAttributeValue(safe.areaOfCoverage || '') + '" placeholder="Area of coverage"></td>' +
            '<td><input type="number" class="lgt-target-ytd" min="0" step="1" value="' + escapeAttributeValue(num(safe.targetYtd)) + '"></td>' +
            '<td><input type="number" class="lgt-target-mtd" min="0" step="1" value="' + escapeAttributeValue(num(safe.targetMtd)) + '"></td>' +
            '<td><input type="number" class="lgt-week1-target" min="0" step="1" value="' + escapeAttributeValue(num(safe.week1Target)) + '"></td>' +
            '<td><input type="number" class="lgt-week1-actual" min="0" step="1" value="' + escapeAttributeValue(num(safe.week1Actual)) + '"></td>' +
            '<td><input type="number" class="lgt-week2-target" min="0" step="1" value="' + escapeAttributeValue(num(safe.week2Target)) + '"></td>' +
            '<td><input type="number" class="lgt-week2-actual" min="0" step="1" value="' + escapeAttributeValue(num(safe.week2Actual)) + '"></td>' +
            '<td><input type="number" class="lgt-week3-target" min="0" step="1" value="' + escapeAttributeValue(num(safe.week3Target)) + '"></td>' +
            '<td><input type="number" class="lgt-week3-actual" min="0" step="1" value="' + escapeAttributeValue(num(safe.week3Actual)) + '"></td>' +
            '<td><input type="number" class="lgt-week4-target" min="0" step="1" value="' + escapeAttributeValue(num(safe.week4Target)) + '"></td>' +
            '<td><input type="number" class="lgt-week4-actual" min="0" step="1" value="' + escapeAttributeValue(num(safe.week4Actual)) + '"></td>' +
            '<td><input type="number" class="lgt-compliance" min="0" max="100" step="0.1" value="' + escapeAttributeValue(num(safe.compliancePercent)) + '"></td>' +
            '<td><input type="checkbox" class="lgt-week1-closed" ' + (safe.week1Closed ? 'checked' : '') + '></td>' +
            '<td><input type="checkbox" class="lgt-week2-closed" ' + (safe.week2Closed ? 'checked' : '') + '></td>' +
            '<td><input type="checkbox" class="lgt-week3-closed" ' + (safe.week3Closed ? 'checked' : '') + '></td>' +
            '<td><input type="checkbox" class="lgt-week4-closed" ' + (safe.week4Closed ? 'checked' : '') + '></td>' +
            '<td><button type="button" class="btn-delete lgt-delete">Delete</button></td>' +
            '</tr>';
    }

    function populateLgtConfigTable(items) {
        const tbody = $('#lgtConfigTableBody');
        tbody.empty();

        if (!items || items.length === 0) {
            tbody.html('<tr class="placeholder-row"><td colspan="19" style="text-align:center;padding:18px;color:#9ca3af;">No tracker rows for selected date. Click "Add New Row".</td></tr>');
            return;
        }

        items.forEach(function(item) {
            tbody.append(createLgtConfigRow(item));
        });

        bindLgtDeleteButtons();
    }

    function bindLgtDeleteButtons() {
        $('.lgt-delete').off('click').on('click', function() {
            $(this).closest('tr').remove();
            if ($('#lgtConfigTableBody tr').length === 0) {
                $('#lgtConfigTableBody').html('<tr class="placeholder-row"><td colspan="19" style="text-align:center;padding:18px;color:#9ca3af;">No tracker rows for selected date. Click "Add New Row".</td></tr>');
            }
        });
    }

    $('#addLgtRowBtn').on('click', function() {
        $('#lgtConfigTableBody .placeholder-row').remove();
        $('#lgtConfigTableBody').append(createLgtConfigRow());
        bindLgtDeleteButtons();
    });

    $('#saveLgtConfigBtn').on('click', function() {
        const saveDate = $('#lgtConfigDate').val();
        if (!saveDate) {
            showMessage('lgtConfigMessage', 'Please select a configuration date.', 'error');
            return;
        }

        const rows = $('#lgtConfigTableBody tr').filter(function() {
            return $(this).find('.lgt-manager-name').length > 0;
        });

        if (rows.length === 0) {
            showMessage('lgtConfigMessage', 'Please add at least one row before saving.', 'error');
            return;
        }

        let invalid = false;
        const payload = [];

        rows.each(function(i) {
            const managerName = $(this).find('.lgt-manager-name').val().trim();
            if (!managerName) {
                invalid = true;
                return false;
            }

            payload.push({
                rowOrder: i + 1,
                managerName: managerName,
                department: $(this).find('.lgt-department').val().trim(),
                areaOfCoverage: $(this).find('.lgt-area').val().trim(),
                targetYtd: parseInt($(this).find('.lgt-target-ytd').val(), 10) || 0,
                targetMtd: parseInt($(this).find('.lgt-target-mtd').val(), 10) || 0,
                week1Target: parseInt($(this).find('.lgt-week1-target').val(), 10) || 0,
                week1Actual: parseInt($(this).find('.lgt-week1-actual').val(), 10) || 0,
                week2Target: parseInt($(this).find('.lgt-week2-target').val(), 10) || 0,
                week2Actual: parseInt($(this).find('.lgt-week2-actual').val(), 10) || 0,
                week3Target: parseInt($(this).find('.lgt-week3-target').val(), 10) || 0,
                week3Actual: parseInt($(this).find('.lgt-week3-actual').val(), 10) || 0,
                week4Target: parseInt($(this).find('.lgt-week4-target').val(), 10) || 0,
                week4Actual: parseInt($(this).find('.lgt-week4-actual').val(), 10) || 0,
                compliancePercent: parseFloat($(this).find('.lgt-compliance').val()) || 0,
                week1Closed: $(this).find('.lgt-week1-closed').is(':checked'),
                week2Closed: $(this).find('.lgt-week2-closed').is(':checked'),
                week3Closed: $(this).find('.lgt-week3-closed').is(':checked'),
                week4Closed: $(this).find('.lgt-week4-closed').is(':checked')
            });
        });

        if (invalid) {
            showMessage('lgtConfigMessage', 'Manager Name is required for each row.', 'error');
            return;
        }

        $.ajax({
            url: '/api/leadership-gemba-tracker/replace/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(response) {
                const count = Array.isArray(response) ? response.length : payload.length;
                showMessage('lgtConfigMessage', 'Saved ' + count + ' rows successfully!', 'success');
                localStorage.setItem('leadership-gemba-tracker-update', Date.now());
                loadLgtConfigByDate(saveDate);
            },
            error: function() {
                showMessage('lgtConfigMessage', 'Error saving leadership gemba tracker. Please try again.', 'error');
            }
        });
    });

    // ==================== TRAINING SCHEDULE CONFIG ====================

    function initializeTrainingScheduleDateField() {
        const today = getTodayDateString();
        const dateInput = $('#trainingConfigDate');
        dateInput.removeAttr('max');
        dateInput.val(today);
        updateTrainingPeriodLabel(today);

        dateInput.on('change', function() {
            const selectedDate = $(this).val();
            if (!selectedDate) {
                updateTrainingPeriodLabel('');
                return;
            }

            updateTrainingPeriodLabel(selectedDate);
            loadTrainingScheduleByDate(selectedDate);
        });
    }

    function updateTrainingPeriodLabel(dateStr) {
        $('#trainingPeriodLabelInput').val(formatShortPeriodFromDate(dateStr));
    }

    function createTrainingScheduleRow(item) {
        const safe = item || {};
        return '' +
            '<tr>' +
            '<td><input type="text" class="ts-training-name" value="' + escapeAttributeValue(safe.trainingName || '') + '" placeholder="Training name"></td>' +
            '<td><input type="text" class="ts-target-audience" value="' + escapeAttributeValue(safe.targetAudience || '') + '" placeholder="Target audience"></td>' +
            '<td><input type="text" class="ts-trainer" value="' + escapeAttributeValue(safe.trainer || '') + '" placeholder="Trainer"></td>' +
            '<td><input type="date" class="ts-training-date" value="' + escapeAttributeValue(safe.trainingDate || '') + '"></td>' +
            '<td><input type="text" class="ts-time-slot" value="' + escapeAttributeValue(safe.timeSlot || '') + '" placeholder="Time"></td>' +
            '<td><input type="number" class="ts-duration-hours" min="0" step="0.5" value="' + escapeAttributeValue(safe.durationHours == null ? '' : String(safe.durationHours)) + '"></td>' +
            '<td><input type="text" class="ts-venue" value="' + escapeAttributeValue(safe.venue || '') + '" placeholder="Venue"></td>' +
            '<td><input type="text" class="ts-fpr" value="' + escapeAttributeValue(safe.fpr || '') + '" placeholder="FPR"></td>' +
            '<td>' +
            '<select class="ts-status">' +
            '<option value="Daily Good" ' + ((safe.status || '') === 'Daily Good' ? 'selected' : '') + '>Daily Good</option>' +
            '<option value="Daily Bad" ' + ((safe.status || '') === 'Daily Bad' ? 'selected' : '') + '>Daily Bad</option>' +
            '</select>' +
            '</td>' +
            '<td><button type="button" class="btn-delete ts-delete">Delete</button></td>' +
            '</tr>';
    }

    function bindTrainingScheduleDeleteButtons() {
        $('.ts-delete').off('click').on('click', function() {
            $(this).closest('tr').remove();
            if ($('#trainingScheduleConfigTableBody tr').length === 0) {
                $('#trainingScheduleConfigTableBody').html('<tr class="placeholder-row"><td colspan="10" style="text-align:center;padding:18px;color:#9ca3af;">No training rows for selected date. Click "Add New Row".</td></tr>');
            }
        });
    }

    function populateTrainingScheduleTable(items) {
        const tbody = $('#trainingScheduleConfigTableBody');
        tbody.empty();

        if (!items || items.length === 0) {
            tbody.html('<tr class="placeholder-row"><td colspan="10" style="text-align:center;padding:18px;color:#9ca3af;">No training rows for selected date. Click "Add New Row".</td></tr>');
            return;
        }

        const first = items[0];
        $('#trainingKpiTitle').val(first.kpiTitle || 'Training Compliance');
        $('#trainingTargetPercent').val(first.targetPercent != null ? first.targetPercent : 100);
        $('#trainingResponsible').val(first.responsible || 'HR Mgr.');

        items.forEach(function(item) {
            tbody.append(createTrainingScheduleRow(item));
        });

        bindTrainingScheduleDeleteButtons();
    }

    function loadTrainingScheduleByDate(dateStr) {
        if (!dateStr) {
            populateTrainingScheduleTable([]);
            return;
        }

        const periodLabel = formatShortPeriodFromDate(dateStr);
        if (!periodLabel) {
            populateTrainingScheduleTable([]);
            return;
        }

        $.ajax({
            url: '/api/training-schedule/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function(data) {
                populateTrainingScheduleTable(Array.isArray(data) ? data : []);
            },
            error: function() {
                populateTrainingScheduleTable([]);
            }
        });
    }

    $('#addTrainingScheduleRowBtn').on('click', function() {
        $('#trainingScheduleConfigTableBody .placeholder-row').remove();
        $('#trainingScheduleConfigTableBody').append(createTrainingScheduleRow());
        bindTrainingScheduleDeleteButtons();
    });

    $('#saveTrainingScheduleBtn').on('click', function() {
        const saveDate = $('#trainingConfigDate').val();
        if (!saveDate) {
            showMessage('trainingScheduleMessage', 'Please select a configuration date.', 'error');
            return;
        }

        const rows = $('#trainingScheduleConfigTableBody tr').filter(function() {
            return $(this).find('.ts-training-name').length > 0;
        });

        if (rows.length === 0) {
            showMessage('trainingScheduleMessage', 'Please add at least one row before saving.', 'error');
            return;
        }

        const kpiTitle = $('#trainingKpiTitle').val().trim() || 'Training Compliance';
        const targetPercent = parseInt($('#trainingTargetPercent').val(), 10) || 100;
        const responsible = $('#trainingResponsible').val().trim() || 'HR Mgr.';

        let invalid = false;
        const payload = [];

        rows.each(function(i) {
            const trainingName = $(this).find('.ts-training-name').val().trim();
            if (!trainingName) {
                invalid = true;
                return false;
            }

            payload.push({
                rowOrder: i + 1,
                kpiTitle: kpiTitle,
                targetPercent: targetPercent,
                responsible: responsible,
                trainingName: trainingName,
                targetAudience: $(this).find('.ts-target-audience').val().trim(),
                trainer: $(this).find('.ts-trainer').val().trim(),
                trainingDate: $(this).find('.ts-training-date').val() || null,
                timeSlot: $(this).find('.ts-time-slot').val().trim(),
                durationHours: parseFloat($(this).find('.ts-duration-hours').val()) || 0,
                venue: $(this).find('.ts-venue').val().trim(),
                fpr: $(this).find('.ts-fpr').val().trim(),
                status: $(this).find('.ts-status').val().trim()
            });
        });

        if (invalid) {
            showMessage('trainingScheduleMessage', 'Training Name is required for each row.', 'error');
            return;
        }

        $.ajax({
            url: '/api/training-schedule/replace/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(response) {
                const count = Array.isArray(response) ? response.length : payload.length;
                showMessage('trainingScheduleMessage', 'Saved ' + count + ' rows successfully!', 'success');
                localStorage.setItem('training-schedule-update', Date.now());
                loadTrainingScheduleByDate(saveDate);
            },
            error: function() {
                showMessage('trainingScheduleMessage', 'Error saving training schedule. Please try again.', 'error');
            }
        });
    });

    // ==================== PMS AGENDA CONFIG ====================

    function initializeMeetingAgendaDateField() {
        const today = getTodayDateString();
        const dateInput = $('#meetingAgendaConfigDate');
        dateInput.removeAttr('max');
        dateInput.val(today);
        updateMeetingAgendaPeriodLabel(today);

        dateInput.on('change', function() {
            const selectedDate = $(this).val();
            if (!selectedDate) {
                updateMeetingAgendaPeriodLabel('');
                return;
            }

            updateMeetingAgendaPeriodLabel(selectedDate);
            loadMeetingAgendaByDate(selectedDate);
        });
    }

    function updateMeetingAgendaPeriodLabel(dateStr) {
        $('#meetingAgendaPeriodLabelInput').val(formatShortPeriodFromDate(dateStr));
    }

    function setMeetingAgendaDefaults() {
        $('#maHeaderTitle').val('');
        $('#maFrequency').val('');
        $('#maMeetingTime').val('');
        $('#maMeetingPlace').val('');
        $('#maPurposeDaily').val('');
        $('#maPurposeWeekly').val('');

        $('#maParticipants').val('');
        $('#maRolesResponsibilities').val('');
        $('#maInputsDaily').val('');
        $('#maInputsFriday').val('');
        $('#maOutputsDaily').val('');
        $('#maOutputsFriday').val('');
        $('#maGroundRules').val('');

        $('#maAgenda1Title').val('');
        $('#maAgenda1Points').val('');
        $('#maAgenda1Time').val('');
        $('#maAgenda2Title').val('');
        $('#maAgenda2Points').val('');
        $('#maAgenda2Time').val('');
        $('#maAgenda3Title').val('');
        $('#maAgenda3Points').val('');
        $('#maAgenda3Time').val('');
        $('#maAgenda4Title').val('');
        $('#maAgenda4Points').val('');
        $('#maAgenda4Time').val('');
        $('#maAgenda5Title').val('');
        $('#maAgenda5Points').val('');
        $('#maAgenda5Time').val('');
    }

    function loadMeetingAgendaByDate(dateStr) {
        if (!dateStr) {
            setMeetingAgendaDefaults();
            return;
        }

        const periodLabel = formatShortPeriodFromDate(dateStr);
        if (!periodLabel) {
            setMeetingAgendaDefaults();
            return;
        }

        $.ajax({
            url: '/api/meeting-agenda/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function(data) {
                if (!data) {
                    setMeetingAgendaDefaults();
                    return;
                }

                $('#maHeaderTitle').val(data.headerTitle || 'Brewery PMS Meeting Agenda (PMS level 4)');
                $('#maFrequency').val(data.frequency || 'Daily');
                $('#maMeetingTime').val(data.meetingTime || 'Mon-Fri 10:00 AM');
                $('#maMeetingPlace').val(data.meetingPlace || 'PMS Room');
                $('#maPurposeDaily').val(data.purposeDaily || '');
                $('#maPurposeWeekly').val(data.purposeWeekly || '');
                $('#maParticipants').val(data.participants || '');
                $('#maRolesResponsibilities').val(data.rolesResponsibilities || '');
                $('#maInputsDaily').val(data.inputsDaily || '');
                $('#maInputsFriday').val(data.inputsFriday || '');
                $('#maOutputsDaily').val(data.outputsDaily || '');
                $('#maOutputsFriday').val(data.outputsFriday || '');
                $('#maGroundRules').val(data.groundRules || '');

                $('#maAgenda1Title').val(data.agenda1Title || '');
                $('#maAgenda1Points').val(data.agenda1Points || '');
                $('#maAgenda1Time').val(data.agenda1Time || '');
                $('#maAgenda2Title').val(data.agenda2Title || '');
                $('#maAgenda2Points').val(data.agenda2Points || '');
                $('#maAgenda2Time').val(data.agenda2Time || '');
                $('#maAgenda3Title').val(data.agenda3Title || '');
                $('#maAgenda3Points').val(data.agenda3Points || '');
                $('#maAgenda3Time').val(data.agenda3Time || '');
                $('#maAgenda4Title').val(data.agenda4Title || '');
                $('#maAgenda4Points').val(data.agenda4Points || '');
                $('#maAgenda4Time').val(data.agenda4Time || '');
                $('#maAgenda5Title').val(data.agenda5Title || '');
                $('#maAgenda5Points').val(data.agenda5Points || '');
                $('#maAgenda5Time').val(data.agenda5Time || '');
            },
            error: function() {
                setMeetingAgendaDefaults();
            }
        });
    }

    $('#saveMeetingAgendaBtn').on('click', function() {
        const saveDate = $('#meetingAgendaConfigDate').val();
        if (!saveDate) {
            showMessage('meetingAgendaMessage', 'Please select a configuration date.', 'error');
            return;
        }

        const payload = {
            headerTitle: $('#maHeaderTitle').val().trim(),
            frequency: $('#maFrequency').val().trim(),
            meetingTime: $('#maMeetingTime').val().trim(),
            meetingPlace: $('#maMeetingPlace').val().trim(),
            purposeDaily: $('#maPurposeDaily').val().trim(),
            purposeWeekly: $('#maPurposeWeekly').val().trim(),
            participants: $('#maParticipants').val().trim(),
            rolesResponsibilities: $('#maRolesResponsibilities').val().trim(),
            inputsDaily: $('#maInputsDaily').val().trim(),
            inputsFriday: $('#maInputsFriday').val().trim(),
            outputsDaily: $('#maOutputsDaily').val().trim(),
            outputsFriday: $('#maOutputsFriday').val().trim(),
            groundRules: $('#maGroundRules').val().trim(),
            agenda1Title: $('#maAgenda1Title').val().trim(),
            agenda1Points: $('#maAgenda1Points').val().trim(),
            agenda1Time: $('#maAgenda1Time').val().trim(),
            agenda2Title: $('#maAgenda2Title').val().trim(),
            agenda2Points: $('#maAgenda2Points').val().trim(),
            agenda2Time: $('#maAgenda2Time').val().trim(),
            agenda3Title: $('#maAgenda3Title').val().trim(),
            agenda3Points: $('#maAgenda3Points').val().trim(),
            agenda3Time: $('#maAgenda3Time').val().trim(),
            agenda4Title: $('#maAgenda4Title').val().trim(),
            agenda4Points: $('#maAgenda4Points').val().trim(),
            agenda4Time: $('#maAgenda4Time').val().trim(),
            agenda5Title: $('#maAgenda5Title').val().trim(),
            agenda5Points: $('#maAgenda5Points').val().trim(),
            agenda5Time: $('#maAgenda5Time').val().trim()
        };

        $.ajax({
            url: '/api/meeting-agenda/replace/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function() {
                showMessage('meetingAgendaMessage', 'PMS Agenda saved successfully!', 'success');
                localStorage.setItem('meeting-agenda-update', Date.now());
                loadMeetingAgendaByDate(saveDate);
            },
            error: function(xhr, status, error) {
                console.error('Save error:', xhr.responseText, status, error);
                const errorMsg = xhr.responseJSON?.message || 'Error saving PMS Agenda. Please try again.';
                showMessage('meetingAgendaMessage', errorMsg, 'error');
            }
        });
    });

    // ==================== PMS PROCESS CONFIRMATION CONFIG ====================

    function getProcessConfirmationDefaultQuestions() {
        return [
            'Are all inputs on the boards up to date before starting?',
            'Was everyone on time and respected the rules?',
            'Did the meeting start, finish on time and follow the agenda?',
            'Are upcoming goals clear and actions to mitigate potential risks captured?',
            'Is there an action for every underperforming KPI or negative trend?',
            'Are PDCA and Root Cause fields used correctly?',
            'Are overdue actions kept to minimum and escalation used correctly?',
            'Was problem solving inside the meeting avoided and Gemba walk used instead?',
            'Was meeting atmosphere productive and effective?',
            'Are the top priorities until next meeting clear?'
        ];
    }

    function getPcStatusFieldIds() {
        return [
            'pcQ1Statuses', 'pcQ2Statuses', 'pcQ3Statuses', 'pcQ4Statuses', 'pcQ5Statuses',
            'pcQ6Statuses', 'pcQ7Statuses', 'pcQ8Statuses', 'pcQ9Statuses', 'pcQ10Statuses',
            'pcTotalStatuses'
        ];
    }

    function normalizePcToken(value) {
        const token = String(value || '').trim().toUpperCase();
        if (token === 'G' || token === 'GOOD' || token === 'DAILY GOOD' || token === '1') {
            return 'G';
        }
        if (token === 'B' || token === 'BAD' || token === 'DAILY BAD' || token === '0') {
            return 'B';
        }
        return 'N';
    }

    function applyPcSelectColor($select) {
        $select.removeClass('pc-status-good pc-status-bad pc-status-na');
        const value = $select.val();
        if (value === 'G') {
            $select.addClass('pc-status-good');
            return;
        }
        if (value === 'B') {
            $select.addClass('pc-status-bad');
            return;
        }
        $select.addClass('pc-status-na');
    }

    function parsePcStatuses(raw) {
        const tokens = String(raw || '')
            .replace(/\r?\n/g, ',')
            .replace(/\|/g, ',')
            .split(',')
            .map(function(x) { return normalizePcToken(x); })
            .filter(function(x) { return x.length > 0; });

        const result = [];
        for (let i = 0; i < 31; i++) {
            result.push(tokens[i] || 'N');
        }
        return result;
    }

    function serializePcStatuses(fieldId) {
        const $grid = $('.pc-status-grid[data-target="' + fieldId + '"]');
        if (!$grid.length) {
            return '';
        }
        const values = [];
        $grid.find('select.pc-status-select').each(function() {
            values.push(normalizePcToken($(this).val()));
        });
        const serialized = values.join(',');
        $('#' + fieldId).val(serialized);
        return serialized;
    }

    function setPcStatuses(fieldId, raw) {
        const values = parsePcStatuses(raw);
        const $grid = $('.pc-status-grid[data-target="' + fieldId + '"]');
        if (!$grid.length) {
            $('#' + fieldId).val(values.join(','));
            return;
        }

        const $selects = $grid.find('select.pc-status-select');
        $selects.each(function(index) {
            const value = values[index] || 'N';
            $(this).val(value);
            applyPcSelectColor($(this));
        });

        $('#' + fieldId).val(values.join(','));
    }

    function initializeProcessConfirmationStatusEditors() {
        $('.pc-status-grid').each(function() {
            const $grid = $(this);
            if ($grid.children().length > 0) {
                return;
            }

            let html = '';
            for (let day = 1; day <= 31; day++) {
                html += '' +
                    '<div class="pc-status-cell">' +
                    '<span class="pc-status-day">D' + day + '</span>' +
                    '<select class="pc-status-select pc-status-na" data-day="' + day + '">' +
                    '<option value="N">N/A</option>' +
                    '<option value="G">Good</option>' +
                    '<option value="B">Bad</option>' +
                    '</select>' +
                    '</div>';
            }

            $grid.html(html);
        });

        $('.pc-status-select').off('change').on('change', function() {
            applyPcSelectColor($(this));
            const fieldId = $(this).closest('.pc-status-grid').data('target');
            serializePcStatuses(fieldId);
        });

        getPcStatusFieldIds().forEach(function(fieldId) {
            setPcStatuses(fieldId, $('#' + fieldId).val());
        });
    }

    function initializeProcessConfirmationDateField() {
        const today = getTodayDateString();
        const dateInput = $('#pcConfigDate');
        dateInput.removeAttr('max');
        dateInput.val(today);
        updateProcessConfirmationPeriodLabel(today);

        dateInput.on('change', function() {
            const selectedDate = $(this).val();
            if (!selectedDate) {
                updateProcessConfirmationPeriodLabel('');
                return;
            }

            updateProcessConfirmationPeriodLabel(selectedDate);
            loadProcessConfirmationByDate(selectedDate);
        });
    }

    function updateProcessConfirmationPeriodLabel(dateStr) {
        const period = formatShortPeriodFromDate(dateStr);
        $('#pcPeriodLabelInput').val(period);
        if (!$('#pcMonthLabel').val().trim() || dateStr) {
            $('#pcMonthLabel').val(period);
        }
    }

    function setProcessConfirmationDefaults() {
        const defaults = getProcessConfirmationDefaultQuestions();

        $('#pcKpiTitle').val('Meeting Process Confirmation');
        $('#pcTargetLabel').val('> 80%');
        $('#pcResponsible').val('CarlEx Mgr.');
        $('#pcMonthLabel').val($('#pcPeriodLabelInput').val() || '');

        $('#pcJanScore, #pcFebScore, #pcMarScore, #pcAprScore, #pcMayScore, #pcJunScore, #pcJulScore, #pcAugScore, #pcSepScore, #pcOctScore, #pcNovScore, #pcDecScore, #pcYtdScore').val('');

        $('#pcQuestion1').val(defaults[0]);
        $('#pcQuestion2').val(defaults[1]);
        $('#pcQuestion3').val(defaults[2]);
        $('#pcQuestion4').val(defaults[3]);
        $('#pcQuestion5').val(defaults[4]);
        $('#pcQuestion6').val(defaults[5]);
        $('#pcQuestion7').val(defaults[6]);
        $('#pcQuestion8').val(defaults[7]);
        $('#pcQuestion9').val(defaults[8]);
        $('#pcQuestion10').val(defaults[9]);

        $('#pcQ1Statuses, #pcQ2Statuses, #pcQ3Statuses, #pcQ4Statuses, #pcQ5Statuses, #pcQ6Statuses, #pcQ7Statuses, #pcQ8Statuses, #pcQ9Statuses, #pcQ10Statuses, #pcTotalStatuses').val('');

        getPcStatusFieldIds().forEach(function(fieldId) {
            setPcStatuses(fieldId, '');
        });
    }

    function toIntOrNull(v) {
        if (v === null || v === undefined || String(v).trim() === '') {
            return null;
        }
        const parsed = Number(v);
        return Number.isFinite(parsed) ? Math.round(parsed) : null;
    }

    function loadProcessConfirmationByDate(dateStr) {
        if (!dateStr) {
            setProcessConfirmationDefaults();
            return;
        }

        const periodLabel = formatShortPeriodFromDate(dateStr);
        if (!periodLabel) {
            setProcessConfirmationDefaults();
            return;
        }

        $.ajax({
            url: '/api/process-confirmation/period/' + encodeURIComponent(periodLabel),
            type: 'GET',
            success: function(data) {
                if (!data) {
                    setProcessConfirmationDefaults();
                    return;
                }

                const defaults = getProcessConfirmationDefaultQuestions();

                $('#pcKpiTitle').val(data.kpiTitle || 'Meeting Process Confirmation');
                $('#pcTargetLabel').val(data.targetLabel || '> 80%');
                $('#pcResponsible').val(data.responsible || 'CarlEx Mgr.');
                $('#pcMonthLabel').val(data.monthLabel || periodLabel);

                $('#pcJanScore').val(data.janScore ?? '');
                $('#pcFebScore').val(data.febScore ?? '');
                $('#pcMarScore').val(data.marScore ?? '');
                $('#pcAprScore').val(data.aprScore ?? '');
                $('#pcMayScore').val(data.mayScore ?? '');
                $('#pcJunScore').val(data.junScore ?? '');
                $('#pcJulScore').val(data.julScore ?? '');
                $('#pcAugScore').val(data.augScore ?? '');
                $('#pcSepScore').val(data.sepScore ?? '');
                $('#pcOctScore').val(data.octScore ?? '');
                $('#pcNovScore').val(data.novScore ?? '');
                $('#pcDecScore').val(data.decScore ?? '');
                $('#pcYtdScore').val(data.ytdScore ?? '');

                $('#pcQuestion1').val(data.question1 || defaults[0]);
                $('#pcQuestion2').val(data.question2 || defaults[1]);
                $('#pcQuestion3').val(data.question3 || defaults[2]);
                $('#pcQuestion4').val(data.question4 || defaults[3]);
                $('#pcQuestion5').val(data.question5 || defaults[4]);
                $('#pcQuestion6').val(data.question6 || defaults[5]);
                $('#pcQuestion7').val(data.question7 || defaults[6]);
                $('#pcQuestion8').val(data.question8 || defaults[7]);
                $('#pcQuestion9').val(data.question9 || defaults[8]);
                $('#pcQuestion10').val(data.question10 || defaults[9]);

                setPcStatuses('pcQ1Statuses', data.q1Statuses || '');
                setPcStatuses('pcQ2Statuses', data.q2Statuses || '');
                setPcStatuses('pcQ3Statuses', data.q3Statuses || '');
                setPcStatuses('pcQ4Statuses', data.q4Statuses || '');
                setPcStatuses('pcQ5Statuses', data.q5Statuses || '');
                setPcStatuses('pcQ6Statuses', data.q6Statuses || '');
                setPcStatuses('pcQ7Statuses', data.q7Statuses || '');
                setPcStatuses('pcQ8Statuses', data.q8Statuses || '');
                setPcStatuses('pcQ9Statuses', data.q9Statuses || '');
                setPcStatuses('pcQ10Statuses', data.q10Statuses || '');
                setPcStatuses('pcTotalStatuses', data.totalStatuses || '');
            },
            error: function() {
                setProcessConfirmationDefaults();
            }
        });
    }

    $('#saveProcessConfirmationBtn').on('click', function() {
        const saveDate = $('#pcConfigDate').val();
        if (!saveDate) {
            showMessage('processConfirmationMessage', 'Please select a configuration date.', 'error');
            return;
        }

        const payload = {
            kpiTitle: $('#pcKpiTitle').val().trim(),
            targetLabel: $('#pcTargetLabel').val().trim(),
            responsible: $('#pcResponsible').val().trim(),
            monthLabel: $('#pcMonthLabel').val().trim(),
            janScore: toIntOrNull($('#pcJanScore').val()),
            febScore: toIntOrNull($('#pcFebScore').val()),
            marScore: toIntOrNull($('#pcMarScore').val()),
            aprScore: toIntOrNull($('#pcAprScore').val()),
            mayScore: toIntOrNull($('#pcMayScore').val()),
            junScore: toIntOrNull($('#pcJunScore').val()),
            julScore: toIntOrNull($('#pcJulScore').val()),
            augScore: toIntOrNull($('#pcAugScore').val()),
            sepScore: toIntOrNull($('#pcSepScore').val()),
            octScore: toIntOrNull($('#pcOctScore').val()),
            novScore: toIntOrNull($('#pcNovScore').val()),
            decScore: toIntOrNull($('#pcDecScore').val()),
            ytdScore: toIntOrNull($('#pcYtdScore').val()),
            question1: $('#pcQuestion1').val().trim(),
            question2: $('#pcQuestion2').val().trim(),
            question3: $('#pcQuestion3').val().trim(),
            question4: $('#pcQuestion4').val().trim(),
            question5: $('#pcQuestion5').val().trim(),
            question6: $('#pcQuestion6').val().trim(),
            question7: $('#pcQuestion7').val().trim(),
            question8: $('#pcQuestion8').val().trim(),
            question9: $('#pcQuestion9').val().trim(),
            question10: $('#pcQuestion10').val().trim(),
            q1Statuses: serializePcStatuses('pcQ1Statuses'),
            q2Statuses: serializePcStatuses('pcQ2Statuses'),
            q3Statuses: serializePcStatuses('pcQ3Statuses'),
            q4Statuses: serializePcStatuses('pcQ4Statuses'),
            q5Statuses: serializePcStatuses('pcQ5Statuses'),
            q6Statuses: serializePcStatuses('pcQ6Statuses'),
            q7Statuses: serializePcStatuses('pcQ7Statuses'),
            q8Statuses: serializePcStatuses('pcQ8Statuses'),
            q9Statuses: serializePcStatuses('pcQ9Statuses'),
            q10Statuses: serializePcStatuses('pcQ10Statuses'),
            totalStatuses: serializePcStatuses('pcTotalStatuses')
        };

        $.ajax({
            url: '/api/process-confirmation/replace/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function() {
                showMessage('processConfirmationMessage', 'PMS Process Confirmation saved successfully!', 'success');
                localStorage.setItem('process-confirmation-update', Date.now());
                loadProcessConfirmationByDate(saveDate);
            },
            error: function() {
                showMessage('processConfirmationMessage', 'Error saving PMS Process Confirmation. Please try again.', 'error');
            }
        });
    });

    // ==================== UTILITY FUNCTIONS ====================
    function showMessage(elementId, message, type) {
        const $msg = $('#' + elementId);
        $msg.removeClass('success error').addClass(type);
        $msg.text(message);
        $msg.addClass('show');

        // Auto-hide after 4 seconds
        setTimeout(function() {
            $msg.removeClass('show');
        }, 4000);
    }

    function updateKPIDashboard() {
        // Trigger update in KPI Dashboard if it's open in another tab/window
        // This can be done via localStorage or web sockets
        localStorage.setItem('kpi-dashboard-update', Date.now());
        localStorage.setItem('issue-board-update', Date.now());
        localStorage.setItem('gemba-schedule-update', Date.now());
        localStorage.setItem('leadership-gemba-tracker-update', Date.now());
        localStorage.setItem('training-schedule-update', Date.now());
        localStorage.setItem('meeting-agenda-update', Date.now());
        localStorage.setItem('process-confirmation-update', Date.now());
        console.log('Settings updated. KPI Dashboard will refresh if open.');
    }

    // ==================== NAVIGATION TOGGLE ====================
    $('.nav-parent-toggle').on('click', function(e) {
        e.preventDefault();
        $(this).addClass('expanded');
        $(this).next('.nav-children').addClass('show').show();
    });

    $('.nav-parent-toggle').addClass('expanded');
    $('.nav-children').addClass('show').show();

    const $activeChild = $('.nav-child.active');
    if ($activeChild.length) {
        const $parent = $activeChild.closest('.nav-parent');
        $parent.find('.nav-parent-toggle').addClass('expanded');
        $parent.find('.nav-children').addClass('show').show();
    }

    function initializeSettingsView() {
        if (requestedConfig === 'issue-board') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const issueConfigItem = $('.config-item[data-config="issue-board"]').first();
            if (issueConfigItem.length) {
                issueConfigItem.addClass('active');
            }
            showForm('issue-board', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="issue-board-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'gemba-schedule') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const gembaConfigItem = $('.config-item[data-config="gemba-schedule"]').first();
            if (gembaConfigItem.length) {
                gembaConfigItem.addClass('active');
            }
            showForm('gemba-schedule', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="gemba-schedule-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'abnormality-tracker') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const atConfigItem = $('.config-item[data-config="abnormality-tracker"]').first();
            if (atConfigItem.length) {
                atConfigItem.addClass('active');
            }
            showForm('abnormality-tracker', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="abnormality-tracker-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'leadership-gemba-tracker') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const lgtConfigItem = $('.config-item[data-config="leadership-gemba-tracker"]').first();
            if (lgtConfigItem.length) {
                lgtConfigItem.addClass('active');
            }
            showForm('leadership-gemba-tracker', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="leadership-gemba-tracker-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'training-schedule') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const trainingConfigItem = $('.config-item[data-config="training-schedule"]').first();
            if (trainingConfigItem.length) {
                trainingConfigItem.addClass('active');
            }
            showForm('training-schedule', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="training-schedule-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'meeting-agenda') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const meetingAgendaConfigItem = $('.config-item[data-config="meeting-agenda"]').first();
            if (meetingAgendaConfigItem.length) {
                meetingAgendaConfigItem.addClass('active');
            }
            showForm('meeting-agenda', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="meeting-agenda-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'process-confirmation') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const processConfirmationConfigItem = $('.config-item[data-config="process-confirmation"]').first();
            if (processConfirmationConfigItem.length) {
                processConfirmationConfigItem.addClass('active');
            }
            showForm('process-confirmation', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="process-confirmation-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'hs-cross') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const hsCrossConfigItem = $('.config-item[data-config="hs-cross"]').first();
            if (hsCrossConfigItem.length) {
                hsCrossConfigItem.addClass('active');
            }
            showForm('hs-cross', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="hs-cross-config"]').addClass('active');
            return;
        }

        if (requestedConfig === 'lsr-tracking') {
            $('.settings-container').addClass('issue-board-full-page');
            $('.config-item').removeClass('active');
            const lsrConfigItem = $('.config-item[data-config="lsr-tracking"]').first();
            if (lsrConfigItem.length) {
                lsrConfigItem.addClass('active');
            }
            showForm('lsr-tracking', '');

            $('.nav-child').removeClass('active');
            $('.nav-child[data-nav="lsr-tracking-config"]').addClass('active');
            return;
        }

        $('.settings-container').removeClass('issue-board-full-page');
        loadPrioritiesData();
        $('.nav-child').removeClass('active');
    }

    // Initialize
    initializeSettingsView();
});
