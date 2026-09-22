$(function() {
    let assignmentOptionsRequest = 0;
    const API = '/api/carlex-process-confirmation/records';
    const OPTIONS_API = '/api/carlex-process-confirmation/options';
    const GROUPS = {
        zm: { label: 'ZM', category: 'ZM_OBSERVATION', jsonField: 'zmObservationsJson' },
        pm: { label: 'PM', category: 'PM_OBSERVATION', jsonField: 'pmObservationsJson' },
        qm: { label: 'QM', category: 'QM_OBSERVATION', jsonField: 'qmObservationsJson' }
    };
    const LEGACY_FIELDS = {
        zm: [
            ['zm1Description', 'zm1CounterMeasureActions', 'zm1Status', 'zm1ObservationImage'],
            ['zm2Description', 'zm2CounterMeasureActions', 'zm2Status', 'zm2ObservationImage']
        ],
        pm: [
            ['pm1Description', 'pm1CounterMeasureActions', 'pm1Status', 'pm1ObservationImage'],
            ['pm2Description', 'pm2CounterMeasureActions', 'pm2Status', 'pm2ObservationImage']
        ],
        qm: [
            ['qm1Description', 'qm1CounterMeasureActions', 'qm1Status', 'qm1ObservationImage'],
            ['qm2Description', 'qm2CounterMeasureActions', 'qm2Status', 'qm2ObservationImage']
        ]
    };
    const imageFields = [
        'zm1ObservationImage', 'zm2ObservationImage',
        'pm1ObservationImage', 'pm2ObservationImage',
        'qm1ObservationImage', 'qm2ObservationImage'
    ];
    const baseFields = [
        ['department', 'Department', 'select'],
        ['areaOfGwProcessConfirmationConducted', 'Area', 'select'],
        ['startTime', 'Start time', 'time'],
        ['completionTime', 'Completion time', 'time'],
        ['dateOfGwProcessConfirmationConducted', 'Date of the GW Process Confirmation conducted', 'date'],
        ['gwPcWeek', 'GW PC week', 'text'],
        ['assignedTo', 'Assigned To', 'select'],
        ['assignmentRemark', 'Remarks (optional)', 'textarea'],
        ['reassignedTo1', 'Reassign 1', 'select'],
        ['reassignment1Remark', 'Reassign 1 Remarks', 'textarea'],
        ['reassignedTo2', 'Reassign 2', 'select'],
        ['reassignment2Remark', 'Reassign 2 Remarks', 'textarea']
    ];

    let records = [];
    let readOnly = false;
    let currentUserIdentity = {};
    let areaItems = [];
    let departments = [];
    let assignmentUsers = [];
    let saveInFlight = false;
    let pendingImageUploads = 0;
    let observationOptions = { zm: [], pm: [], qm: [] };
    let observationState = { zm: [emptyObservation()], pm: [emptyObservation()], qm: [emptyObservation()] };

    function esc(value) {
        return $('<div>').text(value == null ? '' : value).html();
    }

    function escAttribute(value) {
        return esc(value).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function showFormError(message) {
        $('#carlexMessage').text(message).addClass('show error').trigger('focus');
    }

    function nowTime() {
        const d = new Date();
        return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    }

    function today() {
        return new Date().toISOString().slice(0, 10);
    }

    function formatDate(value) {
        if (!value) return '';
        const parts = String(value).slice(0, 10).split('-');
        if (parts.length === 3) return parts[2] + '/' + parts[1] + '/' + parts[0];
        return value;
    }

    function emptyObservation() {
        return { description: '', counterMeasureActions: '', status: '', observationImage: '' };
    }

    function safeParse(value) {
        if (!value) return [];
        try {
            const parsed = JSON.parse(value);
            return Array.isArray(parsed) ? parsed : [];
        } catch (err) {
            return [];
        }
    }

    function fieldHtml(field) {
        const id = field[0], label = field[1], type = field[2];
        let input = type === 'textarea'
            ? '<textarea id="' + id + '" rows="2"></textarea>'
            : '<input id="' + id + '" type="' + type + '">';
        if (type === 'select') input = '<select id="' + id + '"></select>';
        return '<div class="carlex-form-group' + (type === 'textarea' && id !== 'assignmentRemark' ? ' carlex-wide' : '') + '">' +
            '<label for="' + id + '">' + label + '</label>' + input + '</div>';
    }

    function observationGroupHtml(key) {
        const group = GROUPS[key];
        return '<section class="carlex-observation-panel" data-group="' + key + '">' +
            '<div class="carlex-observation-header">' +
            '<div class="carlex-observation-heading"><i class="fas fa-user" aria-hidden="true"></i><div><h3>' + group.label + ' Observations</h3>' +
            '<p>Enter ' + group.label + ' observations and measurement details</p></div></div>' +
            '<button type="button" class="carlex-add-observation" data-group="' + key + '">' +
            '<i class="fas fa-plus"></i><span>Add ' + group.label + '</span></button>' +
            '</div><div class="carlex-observation-list" id="' + key + 'ObservationList"></div></section>';
    }

    function buildForm() {
        $('#carlexFields').html('<input type="hidden" id="areaResponsibility">' +
            baseFields.filter(function(field) { return !/^reassign/.test(field[0]); }).map(fieldHtml).join('') +
            "<section class=\"assignment-workflow\" aria-labelledby=\"assignmentWorkflowTitle\"><h3 id=\"assignmentWorkflowTitle\">Assignment workflow</h3><p class=\"assignment-workflow-help\">Choose the initial assignee. Reassignment becomes available after saving.</p><div class=\"assignment-stage\"><div><label for=\"reassignedTo1\">Reassignee 1<span class=\"assignment-stage-status\">Locked</span></label><select id=\"reassignedTo1\" disabled></select></div><div><label for=\"reassignment1Remark\">Reassignment 1 remark</label><textarea id=\"reassignment1Remark\" rows=\"2\" disabled placeholder=\"Reason for reassignment\"></textarea></div></div><div class=\"assignment-stage\"><div><label for=\"reassignedTo2\">Reassignee 2<span class=\"assignment-stage-status\">Locked</span></label><select id=\"reassignedTo2\" disabled></select></div><div><label for=\"reassignment2Remark\">Reassignment 2 remark</label><textarea id=\"reassignment2Remark\" rows=\"2\" disabled placeholder=\"Reason for reassignment\"></textarea></div></div></section>" +
            '<div class="carlex-observations-wide">' +
            observationGroupHtml('zm') + observationGroupHtml('pm') + observationGroupHtml('qm') +
            '</div>');
        renderAllObservationGroups();
        loadOptions();
        loadObservationOptions();
    }

    function populateSelect(selector, values, current, placeholder) {
        const selected = String(current || '').trim();
        const list = (values || []).slice();
        if (selected && !list.some(function(value) { return String(value || '').trim().toLowerCase() === selected.toLowerCase(); })) {
            list.push(selected);
        }
        const options = ['<option value="">' + esc(placeholder || '') + '</option>']
            .concat(list.map(function(value) {
                return '<option value="' + esc(value) + '">' + esc(value) + '</option>';
            }));
        $(selector).html(options.join('')).val(selected);
    }

    function populateDepartments() {
        populateSelect('#department', departments, $('#department').val(), 'Select department');
    }

    function filteredAreaItems() {
        const department = $('#department').val();
        if (!department) {
            return areaItems;
        }
        return areaItems.filter(function(item) {
                return String(item.parentDepartment || '').trim().toLowerCase() === department.trim().toLowerCase();
            });
    }

    function filteredAreas() {
        return filteredAreaItems().map(function(item) { return item.name; });
    }

    function populateAreas(current) {
        populateSelect('#areaOfGwProcessConfirmationConducted', filteredAreas(), current || $('#areaOfGwProcessConfirmationConducted').val(), 'Select area');
    }

    function syncDepartmentFromArea() {
        const area = $('#areaOfGwProcessConfirmationConducted').val();
        if (!area) return;
        const areaItem = areaItems.find(function(item) {
            return String(item.name || '').trim().toLowerCase() === area.trim().toLowerCase();
        });
        if (areaItem && areaItem.parentDepartment) {
            $('#department').val(areaItem.parentDepartment);
            populateAreas(area);
            $('#areaOfGwProcessConfirmationConducted').val(area);
        }
    }

    function areaHodLabel(user) {
        if (!user) return '';
        return user.label || user.name || user.username || '';
    }

    function userMatchesDepartment(user, department) {
        return !department || String(user.department || '').trim().toLowerCase() === department.trim().toLowerCase();
    }

    function isHodUser(user) {
        const designation = String(user.designation || '').toUpperCase();
        const role = String(user.role || '').toUpperCase();
        return designation.indexOf('AREA HOD') !== -1
            || designation.indexOf('AREA_HOD') !== -1
            || designation.indexOf('AREA HEAD') !== -1
            || designation.indexOf('HOD') !== -1
            || role === 'AREA_HOD'
            || role === 'HOD';
    }

    function departmentHod() {
        const department = $('#department').val();
        return assignmentUsers.find(function(user) {
            return isHodUser(user) && userMatchesDepartment(user, department);
        });
    }

    function assignedUser() {
        const selected = $('#assignedTo').val();
        return assignmentUsers.find(function(user) {
            return String(user.username || '').trim().toLowerCase() === String(selected || '').trim().toLowerCase();
        });
    }

    function syncAreaResponsibility() {
        const user = assignedUser() || departmentHod();
        $('#areaResponsibility').val(areaHodLabel(user));
    }

    function syncAssignedTo(defaultAssignedTo, forceDefault) {
        const current = $('#assignedTo').val();
        const hod = departmentHod();
        const target = defaultAssignedTo || (hod && hod.username) || '';
        if (!$('#carlexId').val() && (forceDefault || !current) && target) {
            $('#assignedTo').val(target);
        }
        syncAreaResponsibility();
    }

    function loadOptions(callback, forceDefaultAssignee) {
        const request = ++assignmentOptionsRequest;
        const params = {
            department: $('#department').val() || '',
            area: $('#areaOfGwProcessConfirmationConducted').val() || '',
            recordId: $('#carlexId').val() || ''
        };
        $.getJSON(OPTIONS_API, params, function(data) {
            if (request !== assignmentOptionsRequest) return;
            const options = data.options || data || {};
            currentUserIdentity = options.currentUser || {};
            departments = options.departments || [];
            areaItems = options.areaItems || [];
            assignmentUsers = options.assignmentUsers || [];
            const currentAssignedTo = $('#assignedTo').val();
            populateDepartments();
            populateAreas();
            const assignmentHtml = '<option value=""></option>' + assignmentUsers.map(function(user) {
                return '<option value="' + esc(user.username) + '">' + esc(user.label || user.username) + '</option>';
            }).join('');
            AssignmentWorkflow.options('#assignedTo', assignmentHtml);
            AssignmentWorkflow.options('#reassignedTo1', assignmentHtml);
            AssignmentWorkflow.options('#reassignedTo2', assignmentHtml);
            if (currentAssignedTo && assignmentUsers.some(function(user) {
                return String(user.username || '').trim().toLowerCase() === currentAssignedTo.trim().toLowerCase();
            })) {
                $('#assignedTo').val(currentAssignedTo);
            }
            syncAssignedTo(options.defaultAssignedTo, !!forceDefaultAssignee);
            if (typeof callback === 'function') callback();
        });
    }

    function loadObservationOptions() {
        Object.keys(GROUPS).forEach(function(groupKey) {
            $.getJSON('/api/dashboard-config/process-master-data/' + GROUPS[groupKey].category, function(data) {
                const items = data.items || [];
                observationOptions[groupKey] = items.map(function(item) { return item.name; }).filter(Boolean);
                $('#' + groupKey + 'ObservationList .pc-observation-description').each(function() {
                    const selected = $(this).val() || '';
                    $(this).html(observationDescriptionOptions(groupKey, selected)).val(selected);
                });
            });
        });
    }

    function renderAllObservationGroups() {
        Object.keys(GROUPS).forEach(renderObservationGroup);
    }

    function observationDescriptionOptions(groupKey, selected) {
        const names = (observationOptions[groupKey] || []).slice();
        if (selected && !names.includes(selected)) names.push(selected);
        return '<option value="">Select an observation</option>' + names.map(function(name) {
            return '<option value="' + escAttribute(name) + '"' + (name === selected ? ' selected' : '') + '>' + esc(name) + '</option>';
        }).join('');
    }

    function renderObservationGroup(groupKey) {
        const group = GROUPS[groupKey];
        const rows = (observationState[groupKey] || [emptyObservation()]).map(function(item, index) {
            const remove = index === 0 ? '' :
                '<button type="button" class="carlex-remove-observation" data-group="' + groupKey + '" data-index="' + index + '" title="Remove ' + group.label + ' observation"><i class="fas fa-trash"></i></button>';
            const options = observationDescriptionOptions(groupKey, item.description);
            return '<div class="carlex-observation-row" data-group="' + groupKey + '" data-index="' + index + '">' +
                '<div class="carlex-form-group"><label>' + group.label + ' ' + (index + 1) + ' Describe observation ' + (index + 1) + ' - Issues</label><select class="pc-observation-description">' + options + '</select></div>' +
                '<div class="carlex-form-group carlex-wide"><label>' + group.label + ' ' + (index + 1) + ' Counter measure actions</label><textarea class="pc-observation-actions" rows="2" placeholder="Enter counter measure actions…">' + esc(item.counterMeasureActions) + '</textarea></div>' +
                '<div class="carlex-form-group"><label>' + group.label + ' ' + (index + 1) + ' Status</label><select class="pc-observation-status">' +
                '<option value="">Select status</option><option value="P">P</option><option value="D">D</option><option value="C">C</option><option value="A">A</option></select></div>' +
                '<div class="carlex-form-group carlex-observation-image-group"><label>' + group.label + ' ' + (index + 1) + ' Observation Image</label><label class="carlex-image-picker"><i class="fas fa-image" aria-hidden="true"></i><span>' + (item.observationImage ? 'Replace' : 'Choose') + '</span><input class="pc-observation-image" type="file" accept="image/*" capture="environment" aria-label="' + group.label + ' ' + (index + 1) + ' observation image"><input class="pc-observation-image-stored" type="hidden" value="' + escAttribute(item.observationImage) + '"></label></div>' +
                '<div class="carlex-observation-actions">' + remove + '</div>' +
                '</div>';
        }).join('');
        $('#' + groupKey + 'ObservationList').html(rows);
        $('#' + groupKey + 'ObservationList .carlex-observation-row').each(function(index) {
            $(this).find('.pc-observation-status').val((observationState[groupKey][index] || {}).status || '');
        });
    }

    function readObservationsFromDom(groupKey) {
        return $('#' + groupKey + 'ObservationList .carlex-observation-row').map(function() {
            const row = $(this);
            return {
                description: row.find('.pc-observation-description').val() || '',
                counterMeasureActions: row.find('.pc-observation-actions').val() || '',
                status: row.find('.pc-observation-status').val() || '',
                observationImage: row.find('.pc-observation-image-stored').val() || ''
            };
        }).get().filter(function(item, index) {
            return index === 0 || item.description || item.counterMeasureActions || item.status || item.observationImage;
        });
    }

    function isObservationTouched(item) {
        return !!(item && [item.description, item.counterMeasureActions, item.status, item.observationImage]
            .some(function(value) { return String(value || '').trim(); }));
    }

    function validatePayload(data) {
        if (!data.department) return 'Department is required.';
        if (!data.areaOfGwProcessConfirmationConducted) return 'Area is required.';
        let observationCount = 0;
        let statusError = '';
        Object.keys(GROUPS).forEach(function(groupKey) {
            (observationState[groupKey] || []).forEach(function(item, index) {
                if (isObservationTouched(item)) observationCount += 1;
                const status = String(item.status || '').trim().toUpperCase();
                if (!statusError && status && !['P', 'D', 'C', 'A'].includes(status)) {
                    statusError = GROUPS[groupKey].label + ' ' + (index + 1) + ' status must be P, D, C, or A.';
                }
            });
        });
        if (statusError) return statusError;
        if (!observationCount) {
            return 'Enter at least one ZM, PM, or QM observation before saving.';
        }
        return '';
    }

    function syncStateFromDom() {
        Object.keys(GROUPS).forEach(function(groupKey) {
            observationState[groupKey] = readObservationsFromDom(groupKey);
            if (!observationState[groupKey].length) observationState[groupKey] = [emptyObservation()];
        });
    }

    function observationsFromRecord(record, groupKey) {
        const jsonRows = safeParse(record[GROUPS[groupKey].jsonField]).map(function(item) {
            return {
                description: item.description || '',
                counterMeasureActions: item.counterMeasureActions || '',
                status: item.status || '',
                observationImage: item.observationImage || ''
            };
        });
        if (jsonRows.length) return jsonRows;
        const legacyRows = LEGACY_FIELDS[groupKey].map(function(fields) {
            return {
                description: record[fields[0]] || '',
                counterMeasureActions: record[fields[1]] || '',
                status: record[fields[2]] || '',
                observationImage: record[fields[3]] || ''
            };
        }).filter(function(item) {
            return item.description || item.counterMeasureActions || item.status || item.observationImage;
        });
        return legacyRows.length ? legacyRows : [emptyObservation()];
    }

    function setForm(record) {
        assignmentOptionsRequest++;
        AssignmentWorkflow.setRecord(record, '#assignedTo', readOnly);
        baseFields.forEach(function(field) {
            let value = (record || {})[field[0]];
            $('#' + field[0]).val(value == null ? '' : value);
        });
        observationState = {
            zm: observationsFromRecord(record || {}, 'zm'),
            pm: observationsFromRecord(record || {}, 'pm'),
            qm: observationsFromRecord(record || {}, 'qm')
        };
        renderAllObservationGroups();
        $('#areaResponsibility').val((record || {}).areaResponsibility || '');
        $('#department').val((record || {}).department || $('#department').val());
        populateDepartments();
        $('#department').val((record || {}).department || $('#department').val());
        populateAreas((record || {}).areaOfGwProcessConfirmationConducted || '');
        $('#areaOfGwProcessConfirmationConducted').val((record || {}).areaOfGwProcessConfirmationConducted || '');
        loadOptions(function() {
            $('#assignedTo').val((record || {}).assignedTo || $('#assignedTo').val());
            $('#reassignedTo1').val((record || {}).reassignedTo1 || '');
            $('#reassignedTo2').val((record || {}).reassignedTo2 || '');
            syncAreaResponsibility();
        });
    }

    function payload() {
        syncStateFromDom();
        const result = {};
        baseFields.forEach(function(field) {
            const value = $('#' + field[0]).val();
            result[field[0]] = value || null;
        });
        syncAreaResponsibility();
        result.areaResponsibility = $('#areaResponsibility').val() || null;
        Object.keys(GROUPS).forEach(function(groupKey) {
            const observations = observationState[groupKey];
            result[GROUPS[groupKey].jsonField] = JSON.stringify(observations);
            LEGACY_FIELDS[groupKey].forEach(function(fields, index) {
                const item = observations[index] || emptyObservation();
                result[fields[0]] = item.description || null;
                result[fields[1]] = item.counterMeasureActions || null;
                result[fields[2]] = item.status || null;
                result[fields[3]] = item.observationImage || null;
            });
            result['another' + groupKey.toUpperCase().slice(0, 1) + groupKey.slice(1) + 'Observation'] = observations.length > 1;
        });
        return result;
    }

    function render() {
        $('#carlexRecordsBody').html(records.map(function(r, i) {
            return '<tr><td>' + (i + 1) + '</td><td>' + esc(r.id) + '</td><td>' + esc(formatDate(r.dateOfGwProcessConfirmationConducted)) + '</td>' +
                '<td>' + esc(r.name) + '</td><td>' + esc(r.email) + '</td><td>' + esc(r.department) + '</td>' +
                '<td>' + esc(r.areaOfGwProcessConfirmationConducted) + '</td><td>' + esc(r.areaResponsibility) + '</td><td>' + esc(r.assignedTo) + '</td>' +
                '<td>' + esc(r.zm1Description) + '</td><td>' + esc(r.pm1Description) + '</td><td>' + esc(r.qm1Description) + '</td>' +
                '<td>' + esc(r.zm1Status || r.pm1Status || r.qm1Status) + '</td><td>' + imageFields.map(function(field) {
                    return attachmentIcon('process-confirmation', r[field], r[field]);
                }).join(' ') + '</td><td class="assignment-history-cell" data-record-id="' + r.id + '">Loading...</td>' +
                '<td class="carlex-actions"><button class="carlex-view" data-id="' + r.id + '" title="View"><i class="fas fa-eye"></i></button>' +
                '<button class="carlex-edit" data-id="' + r.id + '" title="Edit"><i class="fas fa-pen"></i></button>' +
                '<button class="carlex-delete" data-id="' + r.id + '" title="Delete"><i class="fas fa-trash"></i></button></td></tr>';
        }).join('') || '<tr><td colspan="16">No records found.</td></tr>');
        $('.assignment-history-cell').each(function() {
            const cell = $(this);
            $.getJSON(API + '/' + cell.data('record-id') + '/history', function(entries) {
                cell.html(formatAssignmentHistory(entries));
            });
        });
    }

    function setSaveLoading(loading) {
        const btn = $('#carlexSaveBtn');
        if (loading) {
            if (!btn.data('original-html')) btn.data('original-html', btn.html());
            btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> Saving...');
            $('#carlexCancelBtn,#carlexCloseBtn').prop('disabled', true);
            return;
        }
        btn.prop('disabled', false).html(btn.data('original-html') || '<i class="fas fa-save"></i> Save confirmation');
        $('#carlexCancelBtn,#carlexCloseBtn').prop('disabled', false);
    }

    function confirmDelete(options) {
        if (window.PmsConfirm && typeof window.PmsConfirm.open === 'function') return window.PmsConfirm.open(options);
        return Promise.resolve(window.confirm((options && options.message) || 'Delete this record?'));
    }

    function load() {
        $.getJSON(API, function(data) {
            records = data.records || data || [];
            render();
        }).fail(function() {
            $('#carlexRecordsBody').html('<tr><td colspan="16">Unable to load records.</td></tr>');
        });
    }

    function open(record, viewing) {
        readOnly = !!viewing;
        $('#carlexMessage').removeClass('show error').text('');
        $('#carlexId').val(record ? record.id : '');
        setForm(record || {
            startTime: nowTime(),
            completionTime: nowTime(),
            dateOfGwProcessConfirmationConducted: today()
        });
        $('#carlexDrawerTitle').text(viewing ? 'View CarlEX Process Confirmation' : (record ? 'Edit CarlEX Process Confirmation' : 'Add CarlEX Process Confirmation'));
        $('#carlexSaveBtn').toggle(!readOnly);
        $('#carlexCancelBtn').text(readOnly ? 'Close' : 'Cancel');
        $('#carlexFields input, #carlexFields select, #carlexFields textarea, .carlex-add-observation, .carlex-remove-observation').prop('disabled', readOnly);
        AssignmentWorkflow.refresh();
        $('#carlexBackdrop, #carlexForm').attr('aria-hidden', 'false');
        $('.carlex-config-page').addClass('carlex-drawer-open');
    }

    function close() {
        if (saveInFlight) return;
        if (pendingImageUploads) {
            showFormError('Please wait for the image upload to finish.');
            return;
        }
        $('#carlexBackdrop, #carlexForm').attr('aria-hidden', 'true');
        $('.carlex-config-page').removeClass('carlex-drawer-open');
    }

    $('#carlexForm').on('submit', function(e) {
        e.preventDefault();
        if (readOnly || saveInFlight) return;
        if (pendingImageUploads) {
            showFormError('Please wait for the image upload to finish before saving.');
            return;
        }
        const id = $('#carlexId').val();
        const data = payload();
        const validationMessage = validatePayload(data);
        if (validationMessage) {
            showFormError(validationMessage);
            return;
        }
        saveInFlight = true;
        setSaveLoading(true);
        $.ajax({
            url: API + (id ? '/' + id : ''),
            type: id ? 'PUT' : 'POST',
            contentType: 'application/json',
            data: JSON.stringify(data),
            success: function(data) {
                if (data && data.status === 'error') {
                    showFormError(data.message || 'Unable to save record.');
                    return;
                }
                saveInFlight = false;
                close();
                load();
                if (window.PmsFeedback) window.PmsFeedback.show(id ? 'Process Confirmation updated successfully.' : 'Process Confirmation saved successfully.', 'success');
            },
            error: function(xhr) {
                showFormError(xhr.responseJSON?.message || 'Unable to save record. Please try again.');
            },
            complete: function() {
                saveInFlight = false;
                setSaveLoading(false);
            }
        });
    });

    $('#carlexAddBtn').on('click', function() { open(null, false); });
    $('#carlexCloseBtn, #carlexCancelBtn, #carlexBackdrop').on('click', close);
    $(document).on('change', '#department', function() {
        $('#areaOfGwProcessConfirmationConducted').val('');
        populateAreas('');
        $('#areaResponsibility').val('');
        if (!$('#carlexId').val()) $('#assignedTo').val('');
        loadOptions(null, true);
    });
    $(document).on('change', '#areaOfGwProcessConfirmationConducted', function() {
        syncDepartmentFromArea();
        loadOptions(null, true);
    });
    $(document).on('change', '#assignedTo', function() {
        syncAreaResponsibility();
    });
    $(document).on('click', '.carlex-add-observation', function() {
        syncStateFromDom();
        const group = $(this).data('group');
        observationState[group].push(emptyObservation());
        renderObservationGroup(group);
    });
    $(document).on('click', '.carlex-remove-observation', function() {
        syncStateFromDom();
        const group = $(this).data('group');
        observationState[group].splice(Number($(this).data('index')), 1);
        if (!observationState[group].length) observationState[group] = [emptyObservation()];
        renderObservationGroup(group);
    });
    $(document).on('click', '.carlex-view,.carlex-edit', function() {
        const record = records.find(r => String(r.id) === String($(this).data('id')));
        open(record, $(this).hasClass('carlex-view'));
    });
    $(document).on('click', '.carlex-delete', function() {
        const $button = $(this);
        if ($button.prop('disabled')) return;
        const id = $button.data('id');
        $button.prop('disabled', true);
        Promise.resolve(confirmDelete({
            title: 'Delete process confirmation?',
            message: 'This process confirmation record will be permanently deleted.',
            confirmText: 'Delete Record'
        })).then(function(confirmed) {
            if (!confirmed) return;
            return $.ajax({ url: API + '/' + encodeURIComponent(id), type: 'DELETE' }).then(function(data) {
                if (data && data.status === 'error') throw new Error(data.message || 'Unable to delete this record.');
                load();
                if (window.PmsFeedback) window.PmsFeedback.show('Process Confirmation deleted successfully.', 'success');
            });
        }).catch(function(error) {
            const message = error.responseJSON && error.responseJSON.message || error.message || 'Unable to delete this record.';
            if (window.PmsFeedback) window.PmsFeedback.show(message, 'error');
            else window.alert(message);
        }).finally(function() { $button.prop('disabled', false); });
    });
    $(document).on('change', '.pc-observation-image', function() {
        const input = this;
        const file = input.files && input.files[0];
        if (!file) return;
        const hidden = $(input).siblings('.pc-observation-image-stored');
        pendingImageUploads += 1;
        $(input).siblings('span').text('Uploading…');
        $('.carlex-add-observation, .carlex-remove-observation').prop('disabled', true);
        const formData = new FormData();
        formData.append('file', file);
        if (hidden.val()) formData.append('replace', hidden.val());
        $.ajax({
            url: '/api/attachments/process-confirmation/upload',
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(data) {
                hidden.val(data.storedName || '');
                $(input).siblings('span').text('Replace');
            },
            error: function(xhr) {
                $(input).siblings('span').text(hidden.val() ? 'Replace' : 'Choose');
                showFormError(xhr.responseJSON?.error || 'Image upload failed. Please choose the image again.');
            },
            complete: function() {
                pendingImageUploads -= 1;
                if (!pendingImageUploads) $('.carlex-add-observation, .carlex-remove-observation').prop('disabled', readOnly);
            }
        });
    });
    $('#hamburger').on('click', function() {
        if (innerWidth <= 768) {
            $('#sidebar').toggleClass('active');
            $('#sidebarOverlay').toggleClass('active');
        } else {
            $('#sidebar').toggleClass('collapsed');
            $('.main-content').toggleClass('expanded');
        }
    });

    buildForm();
    load();
    const edit = new URLSearchParams(location.search).get('edit');
    if (edit) {
        $.getJSON(API + '/' + edit, function(data) {
            open(data.record || data, false);
        });
    }
});
