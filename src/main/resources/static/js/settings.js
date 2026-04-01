// Settings Page JavaScript

$(document).ready(function() {
    let currentForm = 'priorities';
    let currentType = '';

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

    initializeMetricsDateField();

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

    // ==================== PRIORITIES FORM ====================
    function loadPrioritiesData() {
        const today = new Date().toISOString().split('T')[0];

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
        const today = new Date().toISOString().split('T')[0];

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

        if (isFutureDate(selectedDate)) {
            showMessage('metricsDataMessage', 'Future dates are not allowed for metrics data.', 'error');
            $(this).val(getTodayDateString());
            resetMetricsForm();
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

        if (isFutureDate(selectedDate)) {
            showMessage('metricsDataMessage', 'Future dates are not allowed for metrics data.', 'error');
            showMetricsToast('Future dates are not allowed.', 'error');
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
        dateInput.attr('max', today);
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

    function isFutureDate(dateStr) {
        const today = getTodayDateString();
        return dateStr > today;
    }

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
        console.log('Settings updated. KPI Dashboard will refresh if open.');
    }

    // ==================== NAVIGATION TOGGLE ====================
    $('.nav-parent-toggle').on('click', function(e) {
        e.preventDefault();
        $(this).toggleClass('expanded');
        $(this).next('.nav-children').slideToggle(200).toggleClass('show');
    });

    const $activeChild = $('.nav-child.active');
    if ($activeChild.length) {
        const $parent = $activeChild.closest('.nav-parent');
        $parent.find('.nav-parent-toggle').addClass('expanded');
        $parent.find('.nav-children').addClass('show').show();
    }

    // Initialize
    loadPrioritiesData();
});
