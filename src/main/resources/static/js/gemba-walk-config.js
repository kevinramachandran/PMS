$(function() {
    'use strict';

    const API = '/api/gemba-walk-config';
    const ATTACHMENT_API = '/api/attachments/gemba-walk/upload';
    const params = new URLSearchParams(window.location.search);
    let gembaCategories = [];
    let lifeSaverRules = [];
    let processAreas = [];
    let records = [];

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function(ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function optionHtml(value) {
        const safe = escapeHtml(value || '');
        return '<option value="' + safe + '">' + safe + '</option>';
    }

    function userOptionHtml(user) {
        const username = escapeHtml(user.username || '');
        const label = escapeHtml(user.label || user.username || '');
        return '<option value="' + username + '">' + label + '</option>';
    }

    function todayDate() {
        const now = new Date();
        return String(now.getFullYear()) + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
    }

    function currentTime() {
        const now = new Date();
        return String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
    }

    function setMessage(message, type) {
        $('#gembaWalkConfigMessage')
            .removeClass('show success error')
            .addClass(type || 'success')
            .text(message || '')
            .toggleClass('show', !!message);
    }

    function populateResponsibility(users, selected) {
        const html = ['<option value=""></option>'].concat((users || []).map(userOptionHtml)).join('');
        $('#responsibility').html(html).val(selected || '');
    }

    function populateSelect(selector, values, selected) {
        $(selector).html(['<option value=""></option>'].concat((values || []).map(optionHtml)).join('')).val(selected || '');
    }

    function renderObservation(index, observation) {
        const item = observation || {};
        const title = index === 0 ? 'Observation No. 1' : 'Observation No. ' + (index + 1);
        const categoryLabel = index === 0 ? 'Category of Observation' : 'Gemba Category';
        const statusLabel = index === 0 ? 'Observation Status' : 'Status';
        return '' +
            '<section class="gw-observation" data-index="' + index + '">' +
            '<h3 class="gw-observation-title">' + title + '</h3>' +
            '<div class="gw-observation-grid">' +
            '<input type="hidden" class="gw-picture-stored" value="' + escapeHtml(item.pictureImage) + '">' +
            '<div class="gw-form-group gw-wide">' +
            '<label>Observation Description</label>' +
            '<textarea class="gw-observation-description" rows="3">' + escapeHtml(item.observationDescription) + '</textarea>' +
            '</div>' +
            '<div class="gw-form-group">' +
            '<label>Picture/Image</label>' +
            '<input type="file" class="gw-picture-image" accept="image/*">' +
            '</div>' +
            '<div class="gw-form-group">' +
            '<label>' + categoryLabel + '</label>' +
            '<select class="gw-gemba-category"><option value=""></option>' + gembaCategories.map(optionHtml).join('') + '</select>' +
            '</div>' +
            '<div class="gw-form-group">' +
            '<label>Applicable Life Saver Rule (LSR)</label>' +
            '<select class="gw-life-saver-rule"><option value=""></option>' + lifeSaverRules.map(optionHtml).join('') + '</select>' +
            '</div>' +
            '<div class="gw-form-group">' +
            '<label>' + statusLabel + '</label>' +
            '<select class="gw-status"><option value="Open">Open</option><option value="Closed">Closed</option></select>' +
            '</div>' +
            '</div>' +
            '</section>';
    }

    function setObservationValues($section, observation) {
        const item = observation || {};
        $section.find('.gw-observation-description').val(item.observationDescription || '');
        $section.find('.gw-picture-stored').val(item.pictureImage || '');
        $section.find('.gw-gemba-category').val(item.gembaCategory || '');
        $section.find('.gw-life-saver-rule').val(item.lifeSaverRule || '');
        $section.find('.gw-status').val(item.status || 'Open');
    }

    function addObservation(observation) {
        const index = $('#gembaWalkObservations .gw-observation').length;
        $('#gembaWalkObservations').append(renderObservation(index, observation));
        setObservationValues($('#gembaWalkObservations .gw-observation').last(), observation);
    }

    function payload() {
        return {
            scheduleItemId: $('#scheduleItemId').val() || null,
            startTime: $('#startTime').val(),
            completionTime: $('#completionTime').val(),
            email: $('#email').val(),
            managerName: $('#managerName').val(),
            dateOfLeadershipSafetyWalkConducted: $('#dateConducted').val() || null,
            managementSafetyWalkWeek: $('#managementSafetyWalkWeek').val(),
            locationOfMswConducted: $('#locationOfMswConducted').val(),
            responsibility: $('#responsibility').val(),
            finalComments: $('#finalComments').val(),
            observations: $('#gembaWalkObservations .gw-observation').map(function() {
                const $section = $(this);
                return {
                    observationDescription: $section.find('.gw-observation-description').val(),
                    pictureImage: $section.find('.gw-picture-stored').val(),
                    gembaCategory: $section.find('.gw-gemba-category').val(),
                    lifeSaverRule: $section.find('.gw-life-saver-rule').val(),
                    status: $section.find('.gw-status').val()
                };
            }).get()
        };
    }

    function setRecord(record) {
        const item = record || {};
        $('#gembaWalkRecordId').val(item.id || '');
        $('#serialNumber').val(item.id || '');
        $('#scheduleItemId').val(item.scheduleItemId || params.get('scheduleId') || '');
        $('#startTime').val(item.startTime || currentTime());
        $('#completionTime').val(item.completionTime || currentTime());
        $('#email').val(item.email || '');
        $('#managerName').val(item.managerName || '');
        $('#dateConducted').val(item.dateOfLeadershipSafetyWalkConducted || todayDate());
        $('#managementSafetyWalkWeek').val(item.managementSafetyWalkWeek || params.get('week') || '');
        $('#locationOfMswConducted').val(item.locationOfMswConducted || params.get('location') || '');
        $('#responsibility').val(item.responsibility || $('#responsibility').val() || '');
        $('#finalComments').val(item.finalComments || '');
        $('#gembaWalkObservations').empty();
        const observations = item.observations && item.observations.length ? item.observations : [{}];
        observations.forEach(addObservation);
    }

    function resetRecord() {
        $('#gembaWalkRecordId').val('');
        $('#serialNumber').val('');
        $('#scheduleItemId').val(params.get('scheduleId') || '');
        $('#startTime').val(currentTime());
        $('#completionTime').val(currentTime());
        $('#email').val('');
        $('#managerName').val('');
        $('#dateConducted').val(todayDate());
        $('#managementSafetyWalkWeek').val(params.get('week') || '');
        $('#locationOfMswConducted').val(params.get('location') || '');
        $('#responsibility').val('');
        $('#finalComments').val('');
        $('#gembaWalkObservations').empty();
        setMessage('', 'success');
        loadOptions();
    }

    function renderTable() {
        const rows = records.map(function(record, index) {
            const observations = Array.isArray(record.observations) ? record.observations : [];
            return '' +
                '<tr>' +
                '<td class="gw-row-number">' + (index + 1) + '</td>' +
                '<td>' + escapeHtml(record.id) + '</td>' +
                '<td>' + escapeHtml(record.startTime) + '</td>' +
                '<td>' + escapeHtml(record.completionTime) + '</td>' +
                '<td>' + escapeHtml(record.managerName) + '</td>' +
                '<td>' + escapeHtml(record.email) + '</td>' +
                '<td>' + escapeHtml(record.dateOfLeadershipSafetyWalkConducted) + '</td>' +
                '<td>' + escapeHtml(record.managementSafetyWalkWeek) + '</td>' +
                '<td>' + escapeHtml(record.locationOfMswConducted) + '</td>' +
                '<td>' + escapeHtml(record.responsibility) + '</td>' +
                '<td><span class="gw-status-pill">' + observations.length + '</span></td>' +
                '<td>' + escapeHtml(record.finalComments) + '</td>' +
                '<td><button type="button" class="gw-table-action gw-edit-record" data-id="' + escapeHtml(record.id) + '" title="Edit" aria-label="Edit Gemba Walk"><i class="fas fa-pen"></i></button></td>' +
                '</tr>';
        }).join('');
        $('#gembaWalkConfigRecordsBody').html(rows || '<tr><td colspan="13" class="gw-empty-cell">No records found.</td></tr>');
    }

    function loadRecords() {
        $.ajax({
            url: API + '/records',
            type: 'GET',
            success: function(data) {
                records = data && Array.isArray(data.records) ? data.records : [];
                renderTable();
            },
            error: function() {
                records = [];
                $('#gembaWalkConfigRecordsBody').html('<tr><td colspan="13" class="gw-empty-cell">Unable to load records.</td></tr>');
            }
        });
    }

    function openDrawer(record) {
        $('#gembaWalkDrawerTitle').text(record && record.id ? 'Edit Gemba Walk' : 'Add Gemba Walk');
        $('#gembaWalkSubmitBtn span').text(record && record.id ? 'Update Walk' : 'Save Walk');
        $('.gemba-walk-config-page').addClass('gw-drawer-open');
        $('#gembaWalkConfigForm').attr('aria-hidden', 'false');
        $('#gembaWalkConfigDrawerBackdrop').attr('aria-hidden', 'false');
    }

    function closeDrawer() {
        $('.gemba-walk-config-page').removeClass('gw-drawer-open');
        $('#gembaWalkConfigForm').attr('aria-hidden', 'true');
        $('#gembaWalkConfigDrawerBackdrop').attr('aria-hidden', 'true');
    }

    function loadRecordIntoDrawer(id) {
        $.ajax({
            url: API + '/records/' + encodeURIComponent(id),
            type: 'GET',
            success: function(data) {
                if (data && data.record) {
                    setRecord(data.record);
                    openDrawer(data.record);
                }
            },
            error: function() {
                setMessage('Unable to load selected Gemba Walk.', 'error');
            }
        });
    }

    function loadOptions() {
        return $.ajax({
            url: API + '/options',
            type: 'GET',
            data: { location: $('#locationOfMswConducted').val() || params.get('location') || '' },
            success: function(data) {
                const options = data && data.options ? data.options : {};
                gembaCategories = options.gembaCategories || [];
                lifeSaverRules = options.lifeSaverRules || [];
                processAreas = options.processAreas || [];
                populateSelect('#locationOfMswConducted', processAreas, $('#locationOfMswConducted').val() || params.get('location') || '');
                const currentUser = options.currentUser || {};
                if (!$('#email').val()) {
                    $('#email').val(currentUser.email || '');
                }
                if (!$('#managerName').val()) {
                    $('#managerName').val(currentUser.label || currentUser.username || '');
                }
                populateResponsibility(options.responsibilityUsers || [], $('#responsibility').val() || options.defaultResponsibility || '');
                const existing = $('#gembaWalkObservations .gw-observation').map(function() {
                    const $section = $(this);
                    return {
                        observationDescription: $section.find('.gw-observation-description').val(),
                        pictureImage: $section.find('.gw-picture-stored').val(),
                        gembaCategory: $section.find('.gw-gemba-category').val(),
                        lifeSaverRule: $section.find('.gw-life-saver-rule').val(),
                        status: $section.find('.gw-status').val()
                    };
                }).get();
                $('#gembaWalkObservations').empty();
                (existing.length ? existing : [{}]).forEach(addObservation);
            },
            error: function() {
                setMessage('Unable to load Gemba Walk data.', 'error');
            }
        });
    }

    function saveRecord() {
        const id = $('#gembaWalkRecordId').val();
        $.ajax({
            url: API + '/records' + (id ? '/' + encodeURIComponent(id) : ''),
            type: id ? 'PUT' : 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload()),
            success: function(data) {
                if (data && data.status === 'success' && data.record) {
                    setRecord(data.record);
                    setMessage('Submitted.', 'success');
                    loadRecords();
                    closeDrawer();
                } else {
                    setMessage('Unable to submit.', 'error');
                }
            },
            error: function(xhr) {
                setMessage(xhr.responseJSON?.message || 'Unable to submit.', 'error');
            }
        });
    }

    $('#hamburger').on('click', function() {
        if (window.innerWidth <= 768) {
            $('#sidebar').toggleClass('active');
            $('#sidebarOverlay').toggleClass('active');
        } else {
            $('#sidebar').toggleClass('collapsed');
            $('.main-content').toggleClass('expanded');
            localStorage.setItem('sidebarCollapsed', $('#sidebar').hasClass('collapsed'));
        }
    });

    $('#sidebarOverlay').on('click', function() {
        $('#sidebar').removeClass('active');
        $('#sidebarOverlay').removeClass('active');
    });

    if (localStorage.getItem('sidebarCollapsed') === 'true' && window.innerWidth > 768) {
        $('#sidebar').addClass('collapsed');
        $('.main-content').addClass('expanded');
    }

    $('#locationOfMswConducted').on('change input', function() {
        loadOptions();
    });

    $('#addObservationBtn').on('click', function() {
        addObservation({});
    });

    $('#gembaWalkConfigForm').on('submit', function(event) {
        event.preventDefault();
        saveRecord();
    });

    $('#gembaWalkAddRecordBtn').on('click', function() {
        resetRecord();
        openDrawer(null);
    });

    $('#gembaWalkConfigRecordsBody').on('click', '.gw-edit-record', function() {
        loadRecordIntoDrawer($(this).data('id'));
    });

    $('#gembaWalkConfigDrawerClose, #gembaWalkConfigCancelBtn').on('click', function() {
        closeDrawer();
    });

    $('#gembaWalkObservations').on('change', '.gw-picture-image', function() {
        const file = this.files && this.files[0];
        const $section = $(this).closest('.gw-observation');
        if (!file) {
            return;
        }
        const formData = new FormData();
        formData.append('file', file);
        if ($section.find('.gw-picture-stored').val()) {
            formData.append('replace', $section.find('.gw-picture-stored').val());
        }
        $.ajax({
            url: ATTACHMENT_API,
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(data) {
                $section.find('.gw-picture-stored').val(data.storedName || '');
            },
            error: function(xhr) {
                setMessage(xhr.responseJSON?.error || 'Image upload failed.', 'error');
            }
        });
    });

    $('#scheduleItemId').val(params.get('scheduleId') || '');
    $('#managementSafetyWalkWeek').val(params.get('week') || '');
    $('#locationOfMswConducted').val(params.get('location') || '');
    $('#startTime').val(currentTime());
    $('#completionTime').val(currentTime());
    $('#dateConducted').val(todayDate());

    loadRecords();
    loadOptions().always(function() {
        const id = params.get('id');
        if (!id) {
            return;
        }
        loadRecordIntoDrawer(id);
    });
});
