// Settings Page JavaScript

$(document).ready(function() {
    let currentForm = 'priorities';
    let currentType = '';
    const pageState = document.body ? document.body.dataset : {};
    const canEditCurrentPage = String(pageState.canEditCurrentPage || '').toLowerCase().trim() === 'true';
    const urlParams = new URLSearchParams(window.location.search);
    const requestedConfig = (urlParams.get('config') || '').toLowerCase().trim();
    const serverActivePage = (pageState.activePage || '').toLowerCase().trim();
    const serverActiveType = (pageState.activeType || '').toUpperCase().trim();
    const serverActiveTitle = (pageState.activeTitle || '').trim();
    
    console.log('=== SETTINGS PAGE INITIALIZATION ===');
    console.log('URL:', window.location.href);
    console.log('URL Search:', window.location.search);
    console.log('Config Parameter:', urlParams.get('config'));
    console.log('Requested Config (processed):', requestedConfig);
    console.log('Server Active Page:', serverActivePage);
    console.log('Server Active Type:', serverActiveType);
    console.log('Server Active Title:', serverActiveTitle);
    
    let issueBoardAssignableUsers = [];
    let issueBoardAssignableLookup = new Map();

    const fixedMetricSections = {
        people: [
            'productionProductivityFtdActual', 'productionProductivityFtdTarget', 'productionProductivityMtdActual', 'productionProductivityMtdTarget', 'productionProductivityYtdActual', 'productionProductivityYtdTarget',
            'logisticsProductivityFtdActual', 'logisticsProductivityFtdTarget', 'logisticsProductivityMtdActual', 'logisticsProductivityMtdTarget', 'logisticsProductivityYtdActual', 'logisticsProductivityYtdTarget'
        ],
        quality: [
            'kpiSensoryScoreFtdActual', 'kpiSensoryScoreFtdTarget', 'kpiSensoryScoreMtdActual', 'kpiSensoryScoreMtdTarget', 'kpiSensoryScoreYtdActual', 'kpiSensoryScoreYtdTarget',
            'kpiConsumerComplaintUnitsMhlFtdActual', 'kpiConsumerComplaintUnitsMhlFtdTarget', 'kpiConsumerComplaintUnitsMhlMtdActual', 'kpiConsumerComplaintUnitsMhlMtdTarget', 'kpiConsumerComplaintUnitsMhlYtdActual', 'kpiConsumerComplaintUnitsMhlYtdTarget',
            'kpiCustomerComplaintUnitsMhlFtdActual', 'kpiCustomerComplaintUnitsMhlFtdTarget', 'kpiCustomerComplaintUnitsMhlMtdActual', 'kpiCustomerComplaintUnitsMhlMtdTarget', 'kpiCustomerComplaintUnitsMhlYtdActual', 'kpiCustomerComplaintUnitsMhlYtdTarget'
        ],
        service: [
            'noOfBrewsFtdActual', 'noOfBrewsFtdTarget', 'noOfBrewsMtdActual', 'noOfBrewsMtdTarget', 'noOfBrewsYtdActual', 'noOfBrewsYtdTarget',
            'dispatchFtdActual', 'dispatchFtdTarget', 'dispatchMtdActual', 'dispatchMtdTarget', 'dispatchYtdActual', 'dispatchYtdTarget',
            'processConfirmationBpFtdActual', 'processConfirmationBpFtdTarget', 'processConfirmationBpMtdActual', 'processConfirmationBpMtdTarget', 'processConfirmationBpYtdActual', 'processConfirmationBpYtdTarget',
            'processConfirmationPackFtdActual', 'processConfirmationPackFtdTarget', 'processConfirmationPackMtdActual', 'processConfirmationPackMtdTarget', 'processConfirmationPackYtdActual', 'processConfirmationPackYtdTarget',
            'kpiOeeFtdActual', 'kpiOeeFtdTarget', 'kpiOeeMtdActual', 'kpiOeeMtdTarget', 'kpiOeeYtdActual', 'kpiOeeYtdTarget',
            'kpiBeerLossFtdActual', 'kpiBeerLossFtdTarget', 'kpiBeerLossMtdActual', 'kpiBeerLossMtdTarget', 'kpiBeerLossYtdActual', 'kpiBeerLossYtdTarget',
            'kpiWurHlHlFtdActual', 'kpiWurHlHlFtdTarget', 'kpiWurHlHlMtdActual', 'kpiWurHlHlMtdTarget', 'kpiWurHlHlYtdActual', 'kpiWurHlHlYtdTarget'
        ],
        cost: [
            'kpiElectricityKwhHlFtdActual', 'kpiElectricityKwhHlFtdTarget', 'kpiElectricityKwhHlMtdActual', 'kpiElectricityKwhHlMtdTarget', 'kpiElectricityKwhHlYtdActual', 'kpiElectricityKwhHlYtdTarget',
            'kpiEnergyKwhHlFtdActual', 'kpiEnergyKwhHlFtdTarget', 'kpiEnergyKwhHlMtdActual', 'kpiEnergyKwhHlMtdTarget', 'kpiEnergyKwhHlYtdActual', 'kpiEnergyKwhHlYtdTarget',
            'kpiRgbRatioFtdActual', 'kpiRgbRatioFtdTarget', 'kpiRgbRatioMtdActual', 'kpiRgbRatioMtdTarget', 'kpiRgbRatioYtdActual', 'kpiRgbRatioYtdTarget'
        ]
    };
    const metricSectionOrder = ['people', 'quality', 'service', 'cost'];
    let customMetricDefinitions = [];
    let metricSections = {};
    let metricFields = [];
    const metricFieldGroups = {};
    const optionalMetricFields = new Set([
        'noOfBrewsFtdActual', 'noOfBrewsFtdTarget', 'noOfBrewsMtdActual', 'noOfBrewsYtdActual',
        'dispatchFtdActual', 'dispatchFtdTarget', 'dispatchMtdActual', 'dispatchYtdActual'
    ]);

    function normalizeMetricSection(section) {
        return String(section || '').trim().toLowerCase();
    }

    function buildCustomMetricFieldIds(definition) {
        const definitionId = Number(definition && definition.id);
        if (!Number.isFinite(definitionId)) {
            return [];
        }

        return [
            'customMetric' + definitionId + 'FtdActual',
            'customMetric' + definitionId + 'FtdTarget',
            'customMetric' + definitionId + 'MtdActual',
            'customMetric' + definitionId + 'MtdTarget',
            'customMetric' + definitionId + 'YtdActual',
            'customMetric' + definitionId + 'YtdTarget'
        ];
    }

    function rebuildMetricRegistry() {
        metricFields = [];
        Object.keys(metricFieldGroups).forEach(function(sectionKey) {
            delete metricFieldGroups[sectionKey];
        });

        metricSectionOrder.forEach(function(section) {
            const fixedFields = fixedMetricSections[section] || [];
            const customFields = customMetricDefinitions
                .filter(function(definition) {
                    return normalizeMetricSection(definition.section) === section;
                })
                .flatMap(buildCustomMetricFieldIds);
            const sectionFields = fixedFields.concat(customFields);

            metricSections[section] = sectionFields;
            metricFieldGroups[section] = {
                actual: sectionFields.filter(function(field) {
                    return field.endsWith('Actual');
                }),
                target: sectionFields.filter(function(field) {
                    return field.endsWith('Target');
                })
            };
            metricFields = metricFields.concat(sectionFields);
        });
    }

    rebuildMetricRegistry();

    $('input[type="date"]').removeAttr('max');

    ensureMetricsDateControls();
    initializeMetricsDateField();
    initializeMetricsFieldGrouping();
    initializeIssueBoardConfigDateField();
    loadIssueBoardResponsibleUsers();
    initializeGembaScheduleDateField();
    initializeAtConfigDateField();
    initializeLgtConfigDateField();
    initializeTrainingScheduleDateField();
    initializeMeetingAgendaDateField();
    initializeProcessConfirmationDateField();
    initializeProcessConfirmationStatusEditors();

    // ==================== DIRECT NAV CONFIGS ====================
    const directNavConfigs = ['issue-board', 'gemba-schedule', 'abnormality-tracker', 'leadership-gemba-tracker', 
                             'training-schedule', 'meeting-agenda', 'process-confirmation', 'hs-cross', 
                             'lsr-tracking', 'kpi-footer-buttons', 'license', 'metrics-data', 'kpi-cross-color'];
    const supportedConfigs = ['priorities', 'weekly-priorities', 'daily-performance', 'daily-section'].concat(directNavConfigs);
    const readOnlyActionSelectors = [
        '.form-actions button',
        '#addRowBtn',
        '.issue-delete',
        '.issue-add-row-btn',
        '.file-upload-trigger',
        '#saveKpiCrossColor',
        '.metrics-add-custom-btn',
        '.metrics-custom-save-btn',
        '.metrics-custom-cancel-btn'
    ].join(', ');

    function currentPageTitle() {
        return serverActiveTitle || 'this page';
    }

    function getActiveFormSection() {
        return $('.form-section.active').first();
    }

    function ensureReadonlyBanner($section) {
        if (!$section || !$section.length || canEditCurrentPage || $section.children('.permission-readonly-banner').length) {
            return;
        }

        const title = currentPageTitle();
        const bannerHtml = '<div class="permission-readonly-banner"><i class="fas fa-lock"></i><div><strong>View only access</strong><span>You can review ' + title + ', but editing controls are locked for this account.</span></div></div>';
        $section.prepend(bannerHtml);
    }

    function applyReadonlyStateToActiveSection() {
        if (canEditCurrentPage) {
            return;
        }

        const $section = getActiveFormSection();
        if (!$section.length) {
            return;
        }

        $section.addClass('permission-readonly-mode');
        ensureReadonlyBanner($section);

        $section.find('input:not([type="hidden"]), textarea, select').each(function() {
            $(this)
                .prop('disabled', true)
                .attr('title', 'Edit access required');
        });

        $section.find(readOnlyActionSelectors).each(function() {
            const $element = $(this);
            if ($element.is('button, input, select, textarea')) {
                $element.prop('disabled', true);
            } else {
                $element.addClass('permission-readonly-trigger').attr('aria-disabled', 'true');
            }
            $element.attr('title', 'Edit access required');
        });
    }

    function installReadonlyObserver() {
        if (canEditCurrentPage) {
            return;
        }

        const settingsMain = document.querySelector('.settings-main');
        if (!settingsMain) {
            return;
        }

        const observer = new MutationObserver(function() {
            applyReadonlyStateToActiveSection();
        });

        observer.observe(settingsMain, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class', 'disabled', 'style']
        });
    }
    
    console.log('Direct Nav Configs:', directNavConfigs);
    console.log('Is requested config in direct nav?', directNavConfigs.includes(requestedConfig), '(' + requestedConfig + ')');

    // ==================== FORM SWITCHING ====================
    function showForm(config, type) {
        console.log('>>> showForm() called with config:', config, ', type:', type);
        
        $('.form-section').removeClass('active');
        $('.form-message').removeClass('show');

        currentForm = config;
        currentType = type;

        if (config === 'priorities') {
            console.log('   -> Setting priorities form ACTIVE');
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
            console.log('   -> Setting metrics-data form ACTIVE');
            $('#form-metrics-data').addClass('active');
            mountMetricsDateControls($('.metrics-tab.active').first().data('tab') || metricSectionOrder[0]);
            const selectedMetricsDate = $('#metricsDateInput').val();
            if (selectedMetricsDate) {
                loadMetricsDataByDate(selectedMetricsDate);
            } else {
                resetMetricsForm();
                showMessage('metricsDataMessage', 'Select a valid past date to load Production Metrics.', 'error');
            }
        } else if (config === 'issue-board') {
            console.log('   -> Setting issue-board form ACTIVE');
            $('#form-issue-board').addClass('active');
            loadIssueBoardByDate($('#issueBoardConfigDate').val());
        } else if (config === 'gemba-schedule') {
            console.log('   -> Setting gemba-schedule form ACTIVE');
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
        } else if (config === 'kpi-footer-buttons') {
            $('#form-kpi-footer-buttons').addClass('active');
            loadKpiFooterButtonsConfig();
        } else if (config === 'kpi-cross-color') {
            $('#form-kpi-cross-color').addClass('active');
            loadKpiCrossColorConfig();
        } else if (config === 'license') {
            $('#form-license').addClass('active');
            loadLicenseConfig();
        } else {
            console.warn('Unknown config:', config);
        }
        
        // Debug: log which form is active
        setTimeout(function() {
            const activeForm = $('.form-section.active');
            console.log('Active form element:', activeForm.attr('id'), 'visible:', activeForm.is(':visible'));
        }, 100);

        setTimeout(function() {
            applyReadonlyStateToActiveSection();
        }, 0);
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

    function getYesterdayDateString() {
        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);
        return yesterday.getFullYear() + '-' +
            String(yesterday.getMonth() + 1).padStart(2, '0') + '-' +
            String(yesterday.getDate()).padStart(2, '0');
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
    $(document).on('change', '#metricsDateInput', function() {
        const selectedDate = $(this).val();
        if (!selectedDate) {
            updateMetricsTargetDate('');
            updateMetricsSelectedDateLabel('', '');
            return;
        }

        const validation = validateMetricsDate(selectedDate);
        if (!validation.ok) {
            resetMetricsForm();
            showMessage('metricsDataMessage', validation.message, 'error');
            showMetricsToast(validation.message, 'error');
            $(this).val('');
            updateMetricsTargetDate('');
            updateMetricsSelectedDateLabel('', '');
            return;
        }

        const targetDate = calculateMetricsTargetDate(selectedDate);
        updateMetricsTargetDate(targetDate);
        updateMetricsSelectedDateLabel(selectedDate, targetDate);
        loadMetricsDataByDate(selectedDate);
    });

    $('.metrics-tab').on('click', function() {
        const tab = $(this).data('tab');
        $('.metrics-tab').removeClass('active');
        $(this).addClass('active');
        $('.metrics-tab-panel').removeClass('active');
        $('#metrics-panel-' + tab).addClass('active');
        mountMetricsDateControls(tab);
    });

    $('#metricsDataForm').on('submit', function(e) {
        e.preventDefault();
    });

    $('#downloadMetricsCsvTemplateBtn').on('click', function() {
        window.location.href = '/api/production-metrics/template/csv';
    });

    $('#uploadMetricsCsvBtn').on('click', function() {
        $('#metricsCsvFileInput').click();
    });

    $('#metricsCsvFileInput').on('change', function() {
        const file = this.files && this.files[0] ? this.files[0] : null;
        if (!file) {
            return;
        }

        const fileName = (file.name || '').toLowerCase();
        if (!fileName.endsWith('.csv')) {
            showMessage('metricsDataMessage', 'Please select a valid CSV file.', 'error');
            showMetricsToast('Please select a valid CSV file.', 'error');
            $(this).val('');
            return;
        }

        const $uploadBtn = $('#uploadMetricsCsvBtn');
        const originalHtml = $uploadBtn.html();
        $uploadBtn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> Uploading...');

        const formData = new FormData();
        formData.append('file', file);

        $.ajax({
            url: '/api/production-metrics/import/csv',
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(responseText) {
                const msg = responseText || 'CSV imported successfully.';
                showMessage('metricsDataMessage', msg, 'success');
                showMetricsToast('CSV import completed.', 'success');

                const selectedDate = $('#metricsDateInput').val();
                if (selectedDate) {
                    loadMetricsDataByDate(selectedDate);
                }
                updateKPIDashboard();
            },
            error: function(xhr) {
                const msg = xhr.responseText || 'Failed to import CSV file.';
                showMessage('metricsDataMessage', msg, 'error');
                showMetricsToast(msg, 'error');
            },
            complete: function() {
                $uploadBtn.prop('disabled', false).html(originalHtml);
                $('#metricsCsvFileInput').val('');
            }
        });
    });

    $('.metrics-save-btn').on('click', function() {
        const section = $(this).data('section') || 'people';
        const $btn = $(this);
        mountMetricsDateControls(section);

        const actualDate = $('#metricsDateInput').val();
        const targetDate = calculateMetricsTargetDate(actualDate);
        updateMetricsTargetDate(targetDate);

        if (!actualDate || !targetDate) {
            showMessage('metricsDataMessage', 'Please select a valid past metrics date.', 'error');
            showMetricsToast('Please select a valid past metrics date.', 'error');
            return;
        }

        const buildResult = buildMetricsSectionPayload(actualDate, targetDate, section);
        if (!buildResult.ok) {
            showMessage('metricsDataMessage', buildResult.message, 'error');
            showMetricsToast(buildResult.message, 'error');
            return;
        }

        setMetricsSaveLoading($btn, true);

        $.ajax({
            url: '/api/metrics',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(buildResult.payload),
            success: function() {
                const sectionLabel = section.charAt(0).toUpperCase() + section.slice(1);
                showMessage('metricsDataMessage', sectionLabel + ' metrics saved successfully.', 'success');
                updateKPIDashboard();
                loadMetricsDataByDate(actualDate);
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
        const dateInput = $('#metricsDateInput');
        const yesterday = getYesterdayDateString();
        dateInput.attr('max', yesterday);
        if (!dateInput.val()) {
            dateInput.val(yesterday);
        }
        const targetDate = calculateMetricsTargetDate(dateInput.val());
        updateMetricsTargetDate(targetDate);
        updateMetricsSelectedDateLabel(dateInput.val(), targetDate);
    }

    function loadMetricsDataByDate(dateStr) {
        const validation = validateMetricsDate(dateStr);
        if (!validation.ok) {
            resetMetricsForm();
            showMessage('metricsDataMessage', validation.message, 'error');
            return;
        }

        const targetDate = calculateMetricsTargetDate(dateStr);
        updateMetricsTargetDate(targetDate);
        updateMetricsSelectedDateLabel(dateStr, targetDate);
        resetMetricsForm();

        $.ajax({
            url: '/api/metrics?date=' + dateStr,
            type: 'GET',
            success: function(data) {
                if (!data || Object.keys(data).length === 0) {
                    showMessage('metricsDataMessage', 'No metrics record found for selected date. Enter values for all categories.', 'success');
                    return;
                }

                fillMetricsForm(data);
                showMessage('metricsDataMessage', 'Metrics loaded for selected date.', 'success');
            },
            error: function(xhr) {
                if (xhr.status === 404 || xhr.status === 204) {
                    showMessage('metricsDataMessage', 'No metrics record found for selected date. Enter values for all categories.', 'success');
                    return;
                }
                showMessage('metricsDataMessage', 'Unable to load metrics for the selected date.', 'error');
            }
        });
    }

    function fillMetricsForm(data) {
        metricSectionOrder.forEach(function(section) {
            const actualSection = (data.actual && data.actual[section]) || {};
            const targetSection = (data.target && data.target[section]) || {};
            (metricFieldGroups[section].actual || []).forEach(function(field) {
                $('#' + field).val(actualSection[field] ?? '');
            });
            (metricFieldGroups[section].target || []).forEach(function(field) {
                $('#' + field).val(targetSection[field] ?? '');
            });
        });
    }

    function resetMetricsForm() {
        metricFields.forEach(function(field) {
            $('#' + field).val('');
        });
    }

    function buildMetricsSectionPayload(actualDate, targetDate, section) {
        const validation = validateMetricsDate(actualDate);
        if (!validation.ok) {
            return { ok: false, message: validation.message };
        }

        const groups = metricFieldGroups[section];
        if (!groups) {
            return { ok: false, message: 'Unknown section: ' + section };
        }

        const sectionLabel = section.charAt(0).toUpperCase() + section.slice(1);
        const actualSectionData = {};
        const targetSectionData = {};

        const actualBuild = buildMetricsSectionValues(groups.actual || [], actualSectionData, sectionLabel);
        if (!actualBuild.ok) return actualBuild;

        const targetBuild = buildMetricsSectionValues(groups.target || [], targetSectionData, sectionLabel);
        if (!targetBuild.ok) return targetBuild;

        const payload = {
            actualDate: actualDate,
            targetDate: targetDate,
            actual: { date: actualDate, entryType: 'ACTUAL' },
            target: { date: targetDate, entryType: 'TARGET' }
        };
        payload.actual[section] = actualSectionData;
        payload.target[section] = targetSectionData;

        return { ok: true, payload: payload };
    }

    function buildMetricsPayload(actualDate, targetDate) {
        const validation = validateMetricsDate(actualDate);
        if (!validation.ok) {
            return {
                ok: false,
                message: validation.message
            };
        }

        const payload = {
            actualDate: actualDate,
            targetDate: targetDate,
            actual: {
                date: actualDate,
                entryType: 'ACTUAL'
            },
            target: {
                date: targetDate,
                entryType: 'TARGET'
            }
        };

        for (const section of metricSectionOrder) {
            const sectionLabel = section.charAt(0).toUpperCase() + section.slice(1);
            payload.actual[section] = {};
            payload.target[section] = {};

            const actualBuild = buildMetricsSectionValues(metricFieldGroups[section].actual || [], payload.actual[section], sectionLabel);
            if (!actualBuild.ok) {
                return actualBuild;
            }

            const targetBuild = buildMetricsSectionValues(metricFieldGroups[section].target || [], payload.target[section], sectionLabel);
            if (!targetBuild.ok) {
                return targetBuild;
            }
        }

        return {
            ok: true,
            payload: payload
        };
    }

    function buildMetricsSectionValues(fields, targetSection, sectionLabel) {
        for (const field of fields) {
            const rawValue = $('#' + field).val();
            if (rawValue === null || rawValue === undefined || rawValue === '') {
                if (optionalMetricFields.has(field)) {
                    continue;
                }
                return {
                    ok: false,
                    message: 'Complete all fields for: ' + sectionLabel + '.'
                };
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

            targetSection[field] = parsed;
        }

        return {
            ok: true
        };
    }

    function validateMetricsDate(dateStr) {
        if (!dateStr) {
            return {
                ok: false,
                message: 'Please select a metrics date.'
            };
        }

        const today = getTodayDateString();
        if (dateStr === today) {
            return {
                ok: false,
                message: 'Today\'s data entry is not allowed. Please select a previous date.'
            };
        }

        if (dateStr > today) {
            return {
                ok: false,
                message: 'Future dates are not allowed for production metrics.'
            };
        }

        return {
            ok: true,
            message: ''
        };
    }

    function updateMetricsSelectedDateLabel(actualDate, targetDate) {
        const text = actualDate
            ? 'Actual date: ' + actualDate + ' | Target date: ' + (targetDate || '-')
            : 'Actual date: - | Target date: -';
        $('#metricsSelectedDateLabel').text(text);
    }

    function updateMetricsTargetDate(targetDate) {
        $('#metricsTargetDateInput').val(targetDate || '');
    }

    function calculateMetricsTargetDate(actualDate) {
        if (!actualDate) {
            return '';
        }

        const actual = new Date(actualDate + 'T00:00:00');
        if (Number.isNaN(actual.getTime())) {
            return '';
        }

        actual.setDate(actual.getDate() + 1);
        return formatLocalDateValue(actual);
    }

    function formatLocalDateValue(date) {
        if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
            return '';
        }

        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    }

    function escapeHtml(text) {
        if (text === null || text === undefined) {
            return '';
        }

        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function initializeMetricsFieldGrouping() {
        ensureMetricsTargetInputs();

        $('.metrics-tab-panel .metrics-grid').each(function() {
            const $grid = $(this);
            const $groups = $grid.children('.form-group');
            if ($groups.length === 0) {
                return;
            }

            const $actualWrap = $('<div class="metrics-entry-group metrics-entry-group-actual"></div>');
            $actualWrap.append('<div class="metrics-entry-group-title">Actual</div>');
            $actualWrap.append('<div class="metrics-date-slot metrics-date-slot-actual"></div>');
            $actualWrap.append('<div class="metrics-entry-grid metrics-entry-grid-actual"></div>');

            const $targetWrap = $('<div class="metrics-entry-group metrics-entry-group-target"></div>');
            $targetWrap.append('<div class="metrics-entry-group-title">Target</div>');
            $targetWrap.append('<div class="metrics-date-slot metrics-date-slot-target"></div>');
            $targetWrap.append('<div class="metrics-entry-grid metrics-entry-grid-target"></div>');

            $groups.each(function() {
                const $field = $(this);
                const inputId = $field.find('input').attr('id') || '';
                if (inputId.endsWith('Target')) {
                    $targetWrap.find('.metrics-entry-grid-target').append($field);
                    return;
                }
                $actualWrap.find('.metrics-entry-grid-actual').append($field);
            });

            const $container = $('<div class="metrics-entry-groups"></div>');
            $container.append($actualWrap).append($targetWrap);
            $grid.replaceWith($container);
        });

        mountMetricsDateControls($('.metrics-tab.active').first().data('tab') || metricSectionOrder[0]);
    }

    function ensureMetricsDateControls() {
        if ($('#metricsDateInput').length === 0) {
            const $actualDateRow = $('<div class="metrics-date-row"></div>');
            const $actualGroup = $('<div class="form-group metrics-date-group"></div>');
            $actualGroup.append('<label for="metricsDateInput">Actual Date</label>');
            $actualGroup.append('<input type="date" id="metricsDateInput" name="metricsDateInput">');
            $actualDateRow.append($actualGroup);
            $('#metricsDataForm').append($actualDateRow);
        }

        if ($('#metricsTargetDateInput').length === 0) {
            const $targetDateRow = $('<div class="metrics-date-row"></div>');
            const $targetGroup = $('<div class="form-group metrics-date-group"></div>');
            $targetGroup.append('<label for="metricsTargetDateInput">Target Date</label>');
            $targetGroup.append('<input type="date" id="metricsTargetDateInput" name="metricsTargetDateInput" readonly>');
            $targetDateRow.append($targetGroup);
            $('#metricsDataForm').append($targetDateRow);
        }
    }

    function mountMetricsDateControls(section) {
        ensureMetricsDateControls();

        const normalizedSection = normalizeMetricSection(section) || metricSectionOrder[0];
        const $panel = $('#metrics-panel-' + normalizedSection);
        if ($panel.length === 0) {
            return;
        }

        const $actualSlot = $panel.find('.metrics-entry-group-actual .metrics-date-slot-actual').first();
        const $targetSlot = $panel.find('.metrics-entry-group-target .metrics-date-slot-target').first();
        const $actualRow = $('#metricsDateInput').closest('.metrics-date-row');
        const $targetRow = $('#metricsTargetDateInput').closest('.metrics-date-row');

        if ($actualSlot.length && $actualRow.length) {
            $actualSlot.append($actualRow);
        }

        if ($targetSlot.length && $targetRow.length) {
            $targetSlot.append($targetRow);
        }
    }

    function ensureMetricsTargetInputs() {
        metricSectionOrder.forEach(function(section) {
            const fields = metricSections[section] || [];
            fields.forEach(function(field) {
                if (!field.endsWith('Target')) {
                    return;
                }
                if ($('#' + field).length) {
                    return;
                }

                const actualFieldId = field.replace(/Target$/, 'Actual');
                const $actualGroup = $('#' + actualFieldId).closest('.form-group');
                if ($actualGroup.length === 0) {
                    return;
                }

                const actualLabel = $actualGroup.find('label').text().trim();
                const targetLabel = actualLabel.replace(/\bActual\b/, 'Target');
                const nameAttr = field.charAt(0).toLowerCase() + field.slice(1);
                const inputStep = $actualGroup.find('input').attr('step') || '0.01';
                const inputMin = $actualGroup.find('input').attr('min') || '0';

                const $newGroup = $('<div class="form-group"></div>');
                $newGroup.append('<label for="' + field + '">' + escapeHtml(targetLabel) + '</label>');
                $newGroup.append('<input type="number" id="' + field + '" name="' + nameAttr + '" min="' + inputMin + '" step="' + inputStep + '">');
                $actualGroup.after($newGroup);
            });
        });
    }

    function getMetricSectionTitle(section) {
        return section.charAt(0).toUpperCase() + section.slice(1);
    }

    function getCustomMetricStep(definition) {
        const decimals = Number(definition && definition.decimals);
        if (!Number.isFinite(decimals) || decimals <= 0) {
            return '1';
        }
        return (1 / Math.pow(10, decimals)).toFixed(decimals);
    }

    function getCustomMetricFieldLabel(definition, suffix) {
        const label = (definition && definition.label ? String(definition.label).trim() : 'Custom Metric');
        const unit = definition && definition.unit ? String(definition.unit).trim() : '';
        const suffixLabel = suffix
            .replace('Ftd', 'FTD ')
            .replace('Mtd', 'MTD ')
            .replace('Ytd', 'YTD ')
            .replace('Actual', 'Actual')
            .replace('Target', 'Target');
        return unit && unit !== '-'
            ? label + ' (' + unit + ') - ' + suffixLabel
            : label + ' - ' + suffixLabel;
    }

    function buildCustomMetricInputMarkup(definition, suffix) {
        const definitionId = Number(definition && definition.id);
        const fieldId = 'customMetric' + definitionId + suffix;
        const step = getCustomMetricStep(definition);
        return '' +
            '<div class="form-group metrics-custom-field" data-definition-id="' + definitionId + '">' +
                '<label for="' + fieldId + '">' + escapeHtml(getCustomMetricFieldLabel(definition, suffix)) + '</label>' +
                '<input type="number" id="' + fieldId + '" name="' + fieldId + '" min="0" step="' + step + '">' +
            '</div>';
    }

    function ensureCustomMetricToolbar(section) {
        const $group = $('#metrics-panel-' + section + ' .metrics-type-group');
        if ($group.length === 0) {
            return;
        }

        if ($group.children('.metrics-custom-toolbar').length === 0) {
            const toolbarHtml = '' +
                '<div class="metrics-custom-toolbar" data-section="' + section + '">' +
                    '<div class="metrics-custom-toolbar-copy">Add custom KPI rows for ' + escapeHtml(getMetricSectionTitle(section)) + ' and capture them with the same FTD, MTD, and YTD pattern.</div>' +
                    '<button type="button" class="btn btn-secondary metrics-add-custom-btn" data-section="' + section + '"><i class="fas fa-plus"></i> Add Custom Metric</button>' +
                '</div>';
            $group.children('h3').first().after(toolbarHtml);
        }

        if ($group.children('.metrics-custom-manager').length === 0) {
            const managerHtml = '' +
                '<div class="metrics-custom-manager" data-section="' + section + '">' +
                    '<div class="metrics-custom-form">' +
                        '<div class="form-group">' +
                            '<label>Label</label>' +
                            '<input type="text" class="metrics-custom-label" maxlength="160" placeholder="e.g. Absenteeism">' +
                        '</div>' +
                        '<div class="form-group">' +
                            '<label>Unit</label>' +
                            '<input type="text" class="metrics-custom-unit" maxlength="64" placeholder="Optional">' +
                        '</div>' +
                        '<div class="form-group">' +
                            '<label>Decimals</label>' +
                            '<select class="metrics-custom-decimals">' +
                                '<option value="0">0</option>' +
                                '<option value="1">1</option>' +
                                '<option value="2" selected>2</option>' +
                                '<option value="3">3</option>' +
                                '<option value="4">4</option>' +
                            '</select>' +
                        '</div>' +
                        '<div class="form-actions metrics-custom-actions">' +
                            '<button type="button" class="btn btn-primary metrics-custom-save-btn" data-section="' + section + '"><i class="fas fa-save"></i> Save Custom Metric</button>' +
                            '<button type="button" class="btn btn-secondary metrics-custom-cancel-btn" data-section="' + section + '">Cancel</button>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            $group.children('.metrics-custom-toolbar').after(managerHtml);
        }
    }

    function renderCustomMetricEditors() {
        metricSectionOrder.forEach(function(section) {
            ensureCustomMetricToolbar(section);

            const $panel = $('#metrics-panel-' + section);
            const $actualGrid = $panel.find('.metrics-entry-grid-actual').first();
            const $targetGrid = $panel.find('.metrics-entry-grid-target').first();
            if ($actualGrid.length === 0 || $targetGrid.length === 0) {
                return;
            }

            $actualGrid.find('.metrics-custom-field').remove();
            $targetGrid.find('.metrics-custom-field').remove();

            customMetricDefinitions
                .filter(function(definition) {
                    return normalizeMetricSection(definition.section) === section;
                })
                .forEach(function(definition) {
                    $actualGrid.append(buildCustomMetricInputMarkup(definition, 'FtdActual'));
                    $actualGrid.append(buildCustomMetricInputMarkup(definition, 'MtdActual'));
                    $actualGrid.append(buildCustomMetricInputMarkup(definition, 'YtdActual'));
                    $targetGrid.append(buildCustomMetricInputMarkup(definition, 'FtdTarget'));
                    $targetGrid.append(buildCustomMetricInputMarkup(definition, 'MtdTarget'));
                    $targetGrid.append(buildCustomMetricInputMarkup(definition, 'YtdTarget'));
                });
        });

        bindCustomMetricDefinitionEvents();
        applyReadonlyStateToActiveSection();
    }

    function resetCustomMetricManager(section) {
        const $manager = $('.metrics-custom-manager[data-section="' + section + '"]');
        $manager.removeClass('show');
        $manager.find('.metrics-custom-label').val('');
        $manager.find('.metrics-custom-unit').val('');
        $manager.find('.metrics-custom-decimals').val('2');
    }

    function bindCustomMetricDefinitionEvents() {
        $('.metrics-add-custom-btn').off('click').on('click', function() {
            const section = $(this).data('section');
            $('.metrics-custom-manager').removeClass('show');
            const $manager = $('.metrics-custom-manager[data-section="' + section + '"]');
            $manager.addClass('show');
            $manager.find('.metrics-custom-label').trigger('focus');
        });

        $('.metrics-custom-cancel-btn').off('click').on('click', function() {
            resetCustomMetricManager($(this).data('section'));
        });

        $('.metrics-custom-save-btn').off('click').on('click', function() {
            const section = $(this).data('section');
            const $manager = $('.metrics-custom-manager[data-section="' + section + '"]');
            const label = ($manager.find('.metrics-custom-label').val() || '').trim();
            const unit = ($manager.find('.metrics-custom-unit').val() || '').trim();
            const decimals = Number($manager.find('.metrics-custom-decimals').val() || '2');
            const $btn = $(this);

            if (!label) {
                showMessage('metricsDataMessage', 'Custom metric label is required.', 'error');
                showMetricsToast('Custom metric label is required.', 'error');
                return;
            }

            $btn.prop('disabled', true).data('original-html', $btn.html()).html('<i class="fas fa-spinner fa-spin"></i> Saving...');

            $.ajax({
                url: '/api/metrics/custom-definitions',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({
                    section: section.toUpperCase(),
                    label: label,
                    unit: unit,
                    decimals: decimals
                }),
                success: function() {
                    const selectedDate = $('#metricsDateInput').val();
                    loadCustomMetricDefinitions().done(function() {
                        resetCustomMetricManager(section);
                        $('.metrics-tab').removeClass('active');
                        $('.metrics-tab[data-tab="' + section + '"]').addClass('active');
                        $('.metrics-tab-panel').removeClass('active');
                        $('#metrics-panel-' + section).addClass('active');
                        if (selectedDate) {
                            loadMetricsDataByDate(selectedDate);
                        }
                        showMessage('metricsDataMessage', 'Custom metric created successfully.', 'success');
                        showMetricsToast('Custom metric created.', 'success');
                    });
                },
                error: function(xhr) {
                    const message = xhr.responseText || 'Unable to create custom metric.';
                    showMessage('metricsDataMessage', message, 'error');
                    showMetricsToast(message, 'error');
                },
                complete: function() {
                    $btn.prop('disabled', false).html($btn.data('original-html'));
                }
            });
        });
    }

    function sortCustomMetricDefinitions(definitions) {
        return (definitions || []).slice().sort(function(a, b) {
            const sectionCompare = normalizeMetricSection(a.section).localeCompare(normalizeMetricSection(b.section));
            if (sectionCompare !== 0) {
                return sectionCompare;
            }
            const orderA = Number.isFinite(Number(a.displayOrder)) ? Number(a.displayOrder) : 0;
            const orderB = Number.isFinite(Number(b.displayOrder)) ? Number(b.displayOrder) : 0;
            if (orderA !== orderB) {
                return orderA - orderB;
            }
            return Number(a.id || 0) - Number(b.id || 0);
        });
    }

    function loadCustomMetricDefinitions() {
        return $.ajax({
            url: '/api/metrics/custom-definitions',
            type: 'GET'
        }).done(function(data) {
            customMetricDefinitions = sortCustomMetricDefinitions(Array.isArray(data) ? data : []);
            rebuildMetricRegistry();
            renderCustomMetricEditors();
        }).fail(function() {
            customMetricDefinitions = [];
            rebuildMetricRegistry();
            renderCustomMetricEditors();
        });
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

    function loadIssueBoardResponsibleUsers() {
        $.ajax({
            url: '/api/issue-board/assignable-users',
            type: 'GET',
            success: function(response) {
                const users = Array.isArray(response && response.users) ? response.users : [];
                issueBoardAssignableUsers = users;
                issueBoardAssignableLookup = new Map();

                users.forEach(function(user) {
                    const username = (user.username || '').trim();
                    const email = (user.email || '').trim();
                    if (username) {
                        issueBoardAssignableLookup.set(username.toLowerCase(), user);
                    }
                    if (email) {
                        issueBoardAssignableLookup.set(email.toLowerCase(), user);
                    }
                });

                renderIssueBoardResponsibleUsers();
                $('#issueBoardConfigTableBody .ib-responsible').each(function() {
                    normalizeIssueBoardResponsibleField($(this));
                });
                setIssueBoardSaveState();
            },
            error: function() {
                issueBoardAssignableUsers = [];
                issueBoardAssignableLookup = new Map();
                renderIssueBoardResponsibleUsers();
            }
        });
    }

    function renderIssueBoardResponsibleUsers() {
        const $list = $('#issueBoardResponsibleUsersList');
        if ($list.length === 0) {
            return;
        }

        $list.empty();
        issueBoardAssignableUsers.forEach(function(user) {
            const username = (user.username || '').trim();
            const email = (user.email || '').trim();
            if (!username) {
                return;
            }

            $('<option>')
                .attr('value', username)
                .attr('label', email ? username + ' (' + email + ')' : username)
                .appendTo($list);
        });
    }

    function findIssueBoardResponsibleUser(value) {
        const normalized = (value || '').trim().toLowerCase();
        if (!normalized) {
            return null;
        }
        return issueBoardAssignableLookup.get(normalized) || null;
    }

    function normalizeIssueBoardResponsibleField($field) {
        const match = findIssueBoardResponsibleUser($field.val());
        if (match && match.username) {
            $field.val(match.username);
        }
        return match;
    }

    function isIssueBoardResponsibleValidValue(value) {
        if (!value || !value.trim()) {
            return false;
        }
        if (issueBoardAssignableUsers.length === 0) {
            return true;
        }
        return !!findIssueBoardResponsibleUser(value);
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

    function showIssueBoardToast(message, type) {
        const $toast = $('#issueBoardToast');
        if ($toast.length === 0 || !message) {
            return;
        }

        $toast.removeClass('success error show').addClass(type || 'success').text(message);
        $toast.addClass('show');

        window.clearTimeout(window.__issueBoardToastTimer);
        window.__issueBoardToastTimer = window.setTimeout(function() {
            $toast.removeClass('show');
        }, 2800);
    }

    function applyIssueBoardDateLimits() {
        $('#issueBoardLastReviewDate').removeAttr('max');
        $('#issueBoardNextReviewDate').removeAttr('max');
        $('#issueBoardConfigTableBody .ib-issue-date, #issueBoardConfigTableBody .ib-completed-date, #issueBoardConfigTableBody .ib-target-date').removeAttr('max');
    }

    function statusToPercent(status) {
        const normalized = normalizeIssueStatus(status);
        return Math.max(0, Math.min(100, Number(normalized.replace('%', '')) || 0));
    }

    function updateStatusProgressUI($row) {
        const percent = statusToPercent($row.find('.ib-status').val());
        const $bar = $row.find('.ib-progress-bar');
        const $label = $row.find('.ib-progress-label');
        $bar.css('width', percent + '%');
        $bar.removeClass('low medium high');
        if (percent < 30) {
            $bar.addClass('low');
        } else if (percent < 70) {
            $bar.addClass('medium');
        } else {
            $bar.addClass('high');
        }
        $label.text(percent + '%');
    }

    function clearIssueFieldError($field) {
        $field.removeClass('ib-invalid');
    }

    function validateIssueRow($row) {
        const requiredFields = [
            $row.find('.ib-problem'),
            $row.find('.ib-actions'),
            $row.find('.ib-responsible')
        ];

        let rowValid = true;
        requiredFields.forEach(function($field) {
            if (!$field.val().trim()) {
                $field.addClass('ib-invalid');
                rowValid = false;
            }
        });

        const $responsible = $row.find('.ib-responsible');
        normalizeIssueBoardResponsibleField($responsible);
        if (!isIssueBoardResponsibleValidValue($responsible.val())) {
            $responsible.addClass('ib-invalid');
            rowValid = false;
        }

        const status = $row.find('.ib-status').val();
        const $completed = $row.find('.ib-completed-date');
        if (status === '100%' && !$completed.val()) {
            $completed.addClass('ib-invalid');
            rowValid = false;
        }

        return rowValid;
    }

    function hasAtLeastOneIssueRow() {
        return $('#issueBoardConfigTableBody tr').filter(function() {
            return $(this).find('.ib-problem').length > 0;
        }).length > 0;
    }

    function validateIssueBoardRows(showErrors) {
        const rows = $('#issueBoardConfigTableBody tr').filter(function() {
            return $(this).find('.ib-problem').length > 0;
        });

        if (rows.length === 0) {
            return false;
        }

        let valid = true;
        rows.each(function() {
            const $row = $(this);
            if (showErrors) {
                if (!validateIssueRow($row)) {
                    valid = false;
                }
            } else {
                const responsibleValue = $row.find('.ib-responsible').val().trim();
                const requiredOk = $row.find('.ib-problem').val().trim()
                    && $row.find('.ib-actions').val().trim()
                    && responsibleValue
                    && isIssueBoardResponsibleValidValue(responsibleValue);
                const status = $row.find('.ib-status').val();
                const completedOk = status !== '100%' || !!$row.find('.ib-completed-date').val();
                if (!(requiredOk && completedOk)) {
                    valid = false;
                }
            }
        });

        return valid;
    }

    function setIssueBoardSaveState() {
        const hasRows = hasAtLeastOneIssueRow();
        const isValid = validateIssueBoardRows(false);
        $('#saveIssueBoardBtn').prop('disabled', !(hasRows && isValid));
    }

    function setIssueBoardSaveLoading(isLoading) {
        const $saveBtn = $('#saveIssueBoardBtn');
        if (isLoading) {
            $saveBtn.prop('disabled', true);
            $saveBtn.data('original-html', $saveBtn.html());
            $saveBtn.html('<i class="fas fa-spinner fa-spin"></i> Saving...');
            $('#cancelIssueBoardBtn, #addIssueBoardRowTableBtn').prop('disabled', true);
            return;
        }

        const original = $saveBtn.data('original-html');
        if (original) {
            $saveBtn.html(original);
        }
        $('#cancelIssueBoardBtn, #addIssueBoardRowTableBtn').prop('disabled', false);
        setIssueBoardSaveState();
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

        $('#issueBoardConfigTableBody .ib-responsible').off('change.responsible blur.responsible').on('change.responsible blur.responsible', function() {
            const $field = $(this);
            normalizeIssueBoardResponsibleField($field);
            clearIssueFieldError($field);
            if ($field.val().trim() && !isIssueBoardResponsibleValidValue($field.val())) {
                $field.addClass('ib-invalid');
            }
            setIssueBoardSaveState();
        });

        $('#issueBoardConfigTableBody .ib-issue-date, #issueBoardConfigTableBody .ib-completed-date').off('change').on('change', function() {
            updateCompletedDateRequirement($(this).closest('tr'));
            clearIssueFieldError($(this));
            setIssueBoardSaveState();
        });

        $('#issueBoardConfigTableBody .ib-status').off('change').on('change', function() {
            const $row = $(this).closest('tr');
            updateCompletedDateRequirement($row);
            updateStatusProgressUI($row);
            setIssueBoardSaveState();
        });

        $('#issueBoardConfigTableBody input, #issueBoardConfigTableBody select').off('input.issue change.issue').on('input.issue change.issue', function() {
            clearIssueFieldError($(this));
            setIssueBoardSaveState();
        });

        $('#issueBoardConfigTableBody tr').each(function() {
            const $row = $(this);
            updateRowDueDays($row);
            updateCompletedDateRequirement($row);
            updateStatusProgressUI($row);
        });

        setIssueBoardSaveState();
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
            tbody.html('<tr class="placeholder-row"><td colspan="13" style="text-align:center; padding: 18px; color:#9ca3af;">No issue board data for selected date. Click "Add New Row".</td></tr>');
            setIssueBoardSaveState();
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
        setIssueBoardSaveState();
    }

    function createIssueBoardConfigRow(item) {
        const safeItem = item || {};
        const status = normalizeIssueStatus(safeItem.status);
        const completedDate = safeItem.completedDate || '';

        return '' +
            '<tr data-id="' + escapeAttributeValue(safeItem.id || '') + '">' +
            '<td class="ib-text-cell"><input type="text" class="ib-problem" value="' + escapeAttributeValue(safeItem.problem || '') + '" placeholder="Describe issue"></td>' +
            '<td class="ib-text-cell"><input type="text" class="ib-priority" value="' + escapeAttributeValue(safeItem.priority || '') + '" placeholder="Priority"></td>' +
            '<td class="ib-text-cell"><input type="text" class="ib-owner" value="' + escapeAttributeValue(safeItem.ownerName || '') + '" placeholder="Owner name"></td>' +
            '<td><input type="date" class="ib-issue-date" value="' + escapeAttributeValue(safeItem.issueDate || '') + '" placeholder="Issue date"></td>' +
            '<td class="ib-text-cell"><input type="text" class="ib-root-cause" value="' + escapeAttributeValue(safeItem.rootCause || '') + '" placeholder="Root cause"></td>' +
            '<td class="ib-text-cell"><input type="text" class="ib-actions" value="' + escapeAttributeValue(safeItem.actions || '') + '" placeholder="Action plan"></td>' +
            '<td class="ib-text-cell"><input type="text" class="ib-responsible" list="issueBoardResponsibleUsersList" autocomplete="off" value="' + escapeAttributeValue(safeItem.responsible || '') + '" placeholder="Select responsible user"></td>' +
            '<td><input type="date" class="ib-target-date" value="' + escapeAttributeValue(safeItem.targetDate || '') + '" placeholder="Target date"></td>' +
            '<td><input type="number" class="ib-due-days" value="" step="1" readonly placeholder="0"></td>' +
            '<td class="ib-status-cell">' +
            '<div class="ib-status-wrap">' +
            '<div class="ib-progress-row">' +
            '<div class="ib-progress" aria-hidden="true"><div class="ib-progress-bar"></div></div>' +
            '<span class="ib-progress-label">0%</span>' +
            '</div>' +
            '<select class="ib-status" aria-label="Issue progress status">' +
            '<option value="0%" ' + (status === '0%' ? 'selected' : '') + '>0%</option>' +
            '<option value="25%" ' + (status === '25%' ? 'selected' : '') + '>25%</option>' +
            '<option value="50%" ' + (status === '50%' ? 'selected' : '') + '>50%</option>' +
            '<option value="75%" ' + (status === '75%' ? 'selected' : '') + '>75%</option>' +
            '<option value="100%" ' + (status === '100%' ? 'selected' : '') + '>100%</option>' +
            '</select>' +
            '</div>' +
            '</td>' +
            '<td class="ib-completed-cell"><input type="date" class="ib-completed-date" value="' + escapeAttributeValue(completedDate) + '" placeholder="Completed date"></td>' +
            '<td class="ib-text-cell"><input type="text" class="ib-remarks" value="' + escapeAttributeValue(safeItem.remarks || '') + '" placeholder="Remarks"></td>' +
            '<td class="ib-action-cell"><button type="button" class="issue-delete" title="Delete row" aria-label="Delete row"><i class="fas fa-trash-alt"></i></button></td>' +
            '</tr>';
    }

    function addIssueBoardInlineRow() {
        if ($('#issueBoardConfigTableBody').attr('data-locked') === '1') {
            showIssueBoardPopup('Edit is locked after save. Use Cancel to unlock.');
            return;
        }

        $('#issueBoardConfigTableBody .placeholder-row').remove();
        $('#issueBoardConfigTableBody').append(createIssueBoardConfigRow({
            status: '0%'
        }));
        bindIssueBoardDeleteButtons();
        bindIssueBoardRowEvents();
        applyIssueBoardDateLimits();
        setIssueBoardSaveState();
    }

    $('#addIssueBoardRowTableBtn').on('click', function() {
        addIssueBoardInlineRow();
    });

    function bindIssueBoardDeleteButtons() {
        $('.issue-delete').off('click').on('click', function() {
            const confirmed = window.confirm('Delete this issue row? This change will be lost if you do not save.');
            if (!confirmed) {
                return;
            }
            $(this).closest('tr').remove();
            if ($('#issueBoardConfigTableBody tr').length === 0) {
                $('#issueBoardConfigTableBody').html('<tr class="placeholder-row"><td colspan="13" style="text-align:center; padding: 18px; color:#9ca3af;">No issue board data for selected date. Click "Add New Row".</td></tr>');
            }
            setIssueBoardSaveState();
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
        });

        $('#addIssueBoardRowTableBtn').prop('disabled', true);
    }

    function unlockIssueBoardRows() {
        const tbody = $('#issueBoardConfigTableBody');
        tbody.attr('data-locked', '0');
        tbody.find('input, select, .issue-delete').prop('disabled', false);
        $('#addIssueBoardRowTableBtn').prop('disabled', false);
        setIssueBoardSaveState();
    }

    $('#cancelIssueBoardBtn').on('click', function() {
        const saveDate = $('#issueBoardConfigDate').val() || getTodayDateString();
        unlockIssueBoardRows();
        loadIssueBoardByDate(saveDate);
        showIssueBoardPopup('Changes discarded.');
        showIssueBoardToast('Issue Board changes were canceled.', 'error');
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

        if (!validateIssueBoardRows(true)) {
            showIssueBoardPopup('Problem, Actions, Responsible, and completion date for 100% are required.');
            setIssueBoardSaveState();
            return;
        }

        const payload = [];
        let invalid = false;

        rows.each(function(index) {
            const problem = $(this).find('.ib-problem').val().trim();
            const actions = $(this).find('.ib-actions').val().trim();
            const $responsibleField = $(this).find('.ib-responsible');
            normalizeIssueBoardResponsibleField($responsibleField);
            const responsible = $responsibleField.val().trim();
            const dueParsed = calculateDueDaysFromTarget($(this).find('.ib-target-date').val());
            const statusValue = $(this).find('.ib-status').val();
            const completedDate = $(this).find('.ib-completed-date').val() || null;

            if (!problem || !actions || !responsible) {
                invalid = true;
                $(this).find('.ib-problem, .ib-actions, .ib-responsible').each(function() {
                    if (!$(this).val().trim()) {
                        $(this).addClass('ib-invalid');
                    }
                });
                return false;
            }

            if (!isIssueBoardResponsibleValidValue(responsible)) {
                invalid = true;
                $responsibleField.addClass('ib-invalid');
                showIssueBoardPopup('Responsible must be selected from User Management suggestions.');
                return false;
            }

            if (dueParsed !== '' && !Number.isFinite(dueParsed)) {
                invalid = true;
                return false;
            }

            if (statusValue === '100%' && !completedDate) {
                invalid = true;
                $(this).find('.ib-completed-date').addClass('required ib-invalid');
                showIssueBoardPopup('Completed date is required when status is 100%.');
                return false;
            }

            payload.push({
                rowOrder: index + 1,
                problem: problem,
                priority: $(this).find('.ib-priority').val().trim(),
                ownerName: $(this).find('.ib-owner').val().trim(),
                issueDate: $(this).find('.ib-issue-date').val().trim(),
                rootCause: $(this).find('.ib-root-cause').val().trim(),
                actions: actions,
                responsible: responsible,
                targetDate: $(this).find('.ib-target-date').val() || null,
                dueDays: dueParsed === '' ? null : dueParsed,
                status: statusValue,
                completedDate: completedDate,
                remarks: $(this).find('.ib-remarks').val().trim(),
                lastReviewDate: lastReviewDate,
                nextReviewDate: nextReviewDate,
                boardDate: saveDate
            });
        });

        if (invalid) {
            showIssueBoardPopup('Problem, Actions, and a valid Responsible user are required for each row.');
            setIssueBoardSaveState();
            return;
        }

        setIssueBoardSaveLoading(true);

        $.ajax({
            url: '/api/issue-board/replace/date/' + saveDate,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function(response) {
                const count = Array.isArray(response) ? response.length : payload.length;
                showMessage('issueBoardMessage', 'Saved ' + count + ' issue rows successfully!', 'success');
                showIssueBoardPopup('Issue rows saved. Only Status and Completed Date remain editable.');
                showIssueBoardToast('Issue Board saved successfully.', 'success');
                localStorage.setItem('issue-board-update', Date.now());
                updateKPIDashboard();
                lockIssueBoardRowsForStatusUpdates();
            },
            error: function() {
                showIssueBoardPopup('Error saving Issue Board data. Please try again.');
                showIssueBoardToast('Save failed. Please retry.', 'error');
            },
            complete: function() {
                setIssueBoardSaveLoading(false);
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

    // ==================== KPI FOOTER BUTTONS ====================

    function toggleBtnType(num, type) {
        if (type === 'file') {
            $('#btn' + num + 'UrlGroup').hide();
            $('#btn' + num + 'FileGroup').show();
        } else {
            $('#btn' + num + 'UrlGroup').show();
            $('#btn' + num + 'FileGroup').hide();
        }
    }

    function showCurrentFile(num, fileName) {
        $('#btn' + num + 'CurrentFileName').text(fileName);
        $('#btn' + num + 'CurrentFileDisplay').show();
        $('#btn' + num + 'UploadTrigger').hide();
    }

    function clearCurrentFile(num) {
        $('#btn' + num + 'CurrentFileName').text('');
        $('#btn' + num + 'CurrentFileDisplay').hide();
        $('#btn' + num + 'UploadTrigger').show();
        $('#btn' + num + 'FileInput').val('');
    }

    function uploadButtonFile(num, file) {
        var formData = new FormData();
        formData.append('file', file);

        $('#btn' + num + 'UploadStatus').show();
        $('#btn' + num + 'StatusText').text('Uploading...');
        $('#btn' + num + 'UploadTrigger').hide();
        $('#btn' + num + 'CurrentFileDisplay').hide();

        $.ajax({
            url: '/api/dashboard-config/kpi-footer-buttons/upload/' + num,
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(data) {
                $('#btn' + num + 'UploadStatus').hide();
                showCurrentFile(num, data.originalName);
                showMessage('kpiButtonsMessage', 'File uploaded for Button ' + num + '. Click Save Buttons to confirm.', 'success');
            },
            error: function(xhr) {
                $('#btn' + num + 'UploadStatus').hide();
                $('#btn' + num + 'UploadTrigger').show();
                var msg = (xhr.responseJSON && xhr.responseJSON.error) ? xhr.responseJSON.error : 'Upload failed';
                showMessage('kpiButtonsMessage', 'Upload error: ' + msg, 'error');
            }
        });
    }

    // ==================== KPI CROSS COLOR ====================
    const KPI_CROSS_CHARTS = [
        'peopleProductivityChart',
        'qualitySensoryChart',
        'qualityProcessConfirmationChart',
        'qualityComplaintChart',
        'serviceOeeChart',
        'serviceBeerLossChart',
        'serviceWurChart',
        'costElectricityChart',
        'costEnergyChart',
        'costRgbChart'
    ];

    function loadKpiCrossColorConfig() {
        KPI_CROSS_CHARTS.forEach(function(chartId) {
            const saved = localStorage.getItem('kpiCrossAlertColor_' + chartId) || '#DC2626';
            $('input[name="kpiCross_' + chartId + '"]').each(function() {
                $(this).prop('checked', $(this).val() === saved);
            });
        });
    }

    $('#saveKpiCrossColor').on('click', function() {
        KPI_CROSS_CHARTS.forEach(function(chartId) {
            const selected = $('input[name="kpiCross_' + chartId + '"]:checked').val();
            if (selected) {
                localStorage.setItem('kpiCrossAlertColor_' + chartId, selected);
            }
        });
        $('#kpiCrossColorMessage').removeClass('error').addClass('success').text('Colors saved! Changes will reflect on next chart load.').show();
        setTimeout(function() { $('#kpiCrossColorMessage').fadeOut(); }, 3000);
    });

    // ==================== LICENSE CONFIG ====================
    function loadLicenseConfig() {
        $.ajax({
            url: '/api/license/current',
            type: 'GET',
            success: function(response) {
                if (!response || response.status !== 'success') {
                    showMessage('licenseMessage', 'Unable to load license.', 'error');
                    return;
                }

                const license = response.license;
                if (!license) {
                    $('#licenseStatusText').text('Status: NOT CONFIGURED');
                    $('#licenseMetaText').text('No active license. Paste a license token below to activate.');
                    return;
                }

                const managedCount = Number(license.managedUsersCount || 0);
                const userCount    = Number(license.userCount || 0);
                $('#licenseStatusText').text('Status: ' + (license.status || '-'));
                $('#licenseMetaText').text(
                    'Vendor: ' + (license.vendorName || '-') +
                    ' | Users: ' + managedCount + '/' + userCount +
                    ' | Valid: ' + (license.dateFrom || '-') + ' → ' + (license.dateTo || '-')
                );

                // Pre-fill token field with current saved token for easy update reference
                if (license.licenseToken) {
                    $('#licenseTokenInput').val(license.licenseToken);
                    populateDecodedPanel(license);
                    $('#licenseDecodedPanel').show();
                }

                if (license.status === 'EXPIRED') {
                    showMessage('licenseMessage', 'License is expired. Normal users cannot sign in until renewed.', 'error');
                } else if (license.status === 'ACTIVE') {
                    showMessage('licenseMessage', 'License is active.', 'success');
                }
            },
            error: function(xhr) {
                const msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : 'Unable to load license configuration.';
                showMessage('licenseMessage', msg, 'error');
            }
        });
    }

    function populateDecodedPanel(fields) {
        $('#decVendorName').val(fields.vendorName || '');
        $('#decUserCount').val(fields.userCount || '');
        $('#decDateFrom').val(fields.dateFrom || '');
        $('#decDateTo').val(fields.dateTo || '');
        $('#decLicenseText').val(fields.licenseText || '');
    }

    // Decode button — calls /api/license/decode to show embedded fields
    $('#decodeLicenseBtn').on('click', function() {
        const token = ($('#licenseTokenInput').val() || '').trim();
        if (!token) {
            showMessage('licenseMessage', 'Please paste a license token first.', 'error');
            return;
        }

        const $btn = $(this);
        $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> Decoding...');

        $.ajax({
            url: '/api/license/decode',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ licenseToken: token }),
            success: function(response) {
                if (!response || response.status !== 'success') {
                    showMessage('licenseMessage', (response && response.message) ? response.message : 'Invalid token.', 'error');
                    $('#licenseDecodedPanel').hide();
                    return;
                }
                populateDecodedPanel(response.fields || {});
                $('#licenseDecodedPanel').show();
                showMessage('licenseMessage', 'Token decoded successfully. Review the fields below and click Save License.', 'success');
            },
            error: function(xhr) {
                const msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : 'Invalid or tampered token.';
                showMessage('licenseMessage', msg, 'error');
                $('#licenseDecodedPanel').hide();
            },
            complete: function() {
                $btn.prop('disabled', false).html('<i class="fas fa-unlock-alt"></i> Decode Token');
            }
        });
    });

    // Save button — sends token to /api/license/save
    $('#licenseForm').on('submit', function(e) {
        e.preventDefault();

        const token = ($('#licenseTokenInput').val() || '').trim();
        if (!token) {
            showMessage('licenseMessage', 'License token is required.', 'error');
            return;
        }

        const $btn = $('#saveLicenseBtn');
        $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> Saving...');

        $.ajax({
            url: '/api/license/save',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ licenseToken: token }),
            success: function(response) {
                if (!response || response.status !== 'success') {
                    showMessage('licenseMessage', (response && response.message) ? response.message : 'License save failed.', 'error');
                    return;
                }
                showMessage('licenseMessage', 'License saved and activated successfully.', 'success');
                loadLicenseConfig();
            },
            error: function(xhr) {
                const msg = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : 'Error saving license.';
                showMessage('licenseMessage', msg, 'error');
            },
            complete: function() {
                $btn.prop('disabled', false).html('<i class="fas fa-save"></i> Save License');
            }
        });
    });

    function loadKpiFooterButtonsConfig() {
        $.ajax({
            url: '/api/dashboard-config/kpi-footer-buttons',
            type: 'GET',
            success: function(data) {
                $('#kpiButton1Name').val(data.button1Label || '');
                $('#kpiButton1Url').val(data.button1Url || '');
                $('#kpiButton2Name').val(data.button2Label || '');
                $('#kpiButton2Url').val(data.button2Url || '');

                var type1 = data.button1Type || 'link';
                var type2 = data.button2Type || 'link';

                $('input[name="btn1ActionType"][value="' + type1 + '"]').prop('checked', true);
                $('input[name="btn2ActionType"][value="' + type2 + '"]').prop('checked', true);
                toggleBtnType(1, type1);
                toggleBtnType(2, type2);

                clearCurrentFile(1);
                clearCurrentFile(2);
                if (type1 === 'file' && data.button1FileName) {
                    showCurrentFile(1, data.button1FileName);
                }
                if (type2 === 'file' && data.button2FileName) {
                    showCurrentFile(2, data.button2FileName);
                }
            },
            error: function() {
                showMessage('kpiButtonsMessage', 'Unable to load footer button configuration.', 'error');
            }
        });
    }

    // Radio toggles
    $('input[name="btn1ActionType"]').on('change', function() {
        toggleBtnType(1, $(this).val());
    });
    $('input[name="btn2ActionType"]').on('change', function() {
        toggleBtnType(2, $(this).val());
    });

    // File upload triggers
    $('#btn1UploadTrigger').on('click', function() { $('#btn1FileInput').click(); });
    $('#btn2UploadTrigger').on('click', function() { $('#btn2FileInput').click(); });

    $('#btn1FileInput').on('change', function() {
        if (this.files[0]) uploadButtonFile(1, this.files[0]);
    });
    $('#btn2FileInput').on('change', function() {
        if (this.files[0]) uploadButtonFile(2, this.files[0]);
    });

    // Remove file - switch back to URL mode
    $('#btn1RemoveFile').on('click', function() {
        clearCurrentFile(1);
        $('input[name="btn1ActionType"][value="link"]').prop('checked', true);
        toggleBtnType(1, 'link');
    });
    $('#btn2RemoveFile').on('click', function() {
        clearCurrentFile(2);
        $('input[name="btn2ActionType"][value="link"]').prop('checked', true);
        toggleBtnType(2, 'link');
    });

    $('#cancelKpiButtonsBtn').on('click', function() {
        loadKpiFooterButtonsConfig();
        showMessage('kpiButtonsMessage', 'Changes discarded.', 'success');
    });

    $('#saveKpiButtonsBtn').on('click', function() {
        var type1 = $('input[name="btn1ActionType"]:checked').val() || 'link';
        var type2 = $('input[name="btn2ActionType"]:checked').val() || 'link';
        var label1 = ($('#kpiButton1Name').val() || '').trim();
        var label2 = ($('#kpiButton2Name').val() || '').trim();
        var url1   = ($('#kpiButton1Url').val() || '').trim();
        var url2   = ($('#kpiButton2Url').val() || '').trim();

        if (!label1) {
            showMessage('kpiButtonsMessage', 'Button 1 label is required.', 'error'); return;
        }
        if (type1 === 'link' && !url1) {
            showMessage('kpiButtonsMessage', 'Button 1 URL is required when action type is URL.', 'error'); return;
        }
        if (type1 === 'file' && !$('#btn1CurrentFileName').text().trim()) {
            showMessage('kpiButtonsMessage', 'Button 1 requires an uploaded file when action type is File.', 'error'); return;
        }
        if (!label2) {
            showMessage('kpiButtonsMessage', 'Button 2 label is required.', 'error'); return;
        }
        if (type2 === 'link' && !url2) {
            showMessage('kpiButtonsMessage', 'Button 2 URL is required when action type is URL.', 'error'); return;
        }
        if (type2 === 'file' && !$('#btn2CurrentFileName').text().trim()) {
            showMessage('kpiButtonsMessage', 'Button 2 requires an uploaded file when action type is File.', 'error'); return;
        }

        const payload = {
            button1Label: label1,
            button1Url:   type1 === 'link' ? url1 : '',
            button1Type:  type1,
            button2Label: label2,
            button2Url:   type2 === 'link' ? url2 : '',
            button2Type:  type2
        };

        const $btn = $('#saveKpiButtonsBtn');
        $btn.prop('disabled', true);
        $btn.data('original-html', $btn.html());
        $btn.html('<i class="fas fa-spinner fa-spin"></i> Saving...');

        $.ajax({
            url: '/api/dashboard-config/kpi-footer-buttons',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            success: function() {
                showMessage('kpiButtonsMessage', 'KPI footer buttons saved successfully!', 'success');
                updateKPIDashboard();
                loadKpiFooterButtonsConfig();
            },
            error: function() {
                showMessage('kpiButtonsMessage', 'Error saving KPI footer buttons. Please try again.', 'error');
            },
            complete: function() {
                $btn.prop('disabled', false);
                $btn.html($btn.data('original-html'));
            }
        });
    });

    // ==================== UTILITY FUNCTIONS ====================
    function showMessage(elementId, message, type) {
        const $msg = $('#' + elementId);
        if (!message) {
            $msg.removeClass('show success error').text('');
            return;
        }
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

    function initializeSettingsView() {
        const initialConfig = serverActivePage || requestedConfig;
        const initialType = serverActiveType || '';

        console.log('====== INITIAL SETTINGS VIEW ======');
        console.log('Initial config:', initialConfig);
        console.log('Initial type:', initialType);
        console.log('URL:', window.location.href);

        if (supportedConfigs.includes(initialConfig)) {
            showForm(initialConfig, initialType);
            return;
        }

        console.log('Unknown initial config. Falling back to priorities.');
        showForm('priorities', '');
    }

    installReadonlyObserver();
    loadCustomMetricDefinitions().always(function() {
        initializeSettingsView();
    });
    });
