$(function() {
    'use strict';

    const API = '/api/gemba-kaizen-config';
    const ATTACHMENT_API = '/api/attachments/gemba-kaizen/upload';
    const params = new URLSearchParams(window.location.search);
    let records = [];
    let currentUserIdentity = {};
    let areaItems = [];
    let saveInFlight = false;
    const EDIT_ALLOWED_FIELDS = '#pictureImage, #isKaizenImplemented, #assignedTo, #assignmentRemark';

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function(ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function optionHtml(value) {
        const safe = escapeHtml(value || '');
        return '<option value="' + safe + '">' + safe + '</option>';
    }

    function todayDate() {
        const now = new Date();
        return String(now.getFullYear()) + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
    }

    function currentTime() {
        const now = new Date();
        return String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
    }

    function displayDate(value) {
        const text = String(value || '').trim();
        const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
        return iso ? iso[3] + '/' + iso[2] + '/' + iso[1] : text;
    }

    function setMessage(message, type) {
        $('#gembaKaizenConfigMessage')
            .removeClass('show success error')
            .addClass(type || 'success')
            .text(message || '')
            .toggleClass('show', !!message);
    }

    function setSaveLoading(loading) {
        const $btn = $('#gembaKaizenSubmitBtn');
        if (loading) {
            if (!$btn.data('original-html')) {
                $btn.data('original-html', $btn.html());
            }
            $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i><span>Saving...</span>');
            $('#gembaKaizenConfigCancelBtn, #gembaKaizenConfigDrawerClose').prop('disabled', true);
            return;
        }
        $btn.prop('disabled', false).html($btn.data('original-html') || '<i class="fas fa-save"></i><span>Save Kaizen</span>');
        $('#gembaKaizenConfigCancelBtn, #gembaKaizenConfigDrawerClose').prop('disabled', false);
        setEditLock(isEditMode());
    }

    function populateSelect(selector, values, selected) {
        $(selector).html(['<option value=""></option>'].concat((values || []).map(optionHtml)).join('')).val(selected || '');
    }

    function setEditLock(isEdit) {
        const $fields = $('#gembaKaizenConfigForm')
            .find('input:not([type="hidden"]), select, textarea')
            .not(EDIT_ALLOWED_FIELDS);
        $fields.prop('disabled', !!isEdit);
        $('#gembaKaizenConfigForm').toggleClass('gk-edit-locked', !!isEdit);
    }

    function payload() {
        return {
            lastModifiedTime: $('#lastModifiedTime').val(),
            department: $('#department').val(),
            classificationOfKaizen: $('#classificationOfKaizen').val(),
            gembaKaizenLocation: $('#gembaKaizenLocation').val(),
            gembaKaizenGenerationDate: $('#gembaKaizenGenerationDate').val() || null,
            kaizenIdea: $('#kaizenIdea').val(),
            pictureImage: $('#pictureImageStored').val(),
            benefitsOfKaizen: $('#benefitsOfKaizen').val(),
            isKaizenImplemented: $('#isKaizenImplemented').val(),
            assignedTo: $('#assignedTo').val(),
            assignmentRemark: $('#assignmentRemark').val()
        };
    }

    function setRecord(record) {
        const item = record || {};
        $('#gembaKaizenRecordId').val(item.id || '');
        $('#lastModifiedTime').val(item.lastModifiedTime || currentTime());
        $('#department').val(item.department || '');
        $('#classificationOfKaizen').val(item.classificationOfKaizen || '');
        $('#gembaKaizenLocation').val(item.gembaKaizenLocation || '');
        $('#gembaKaizenGenerationDate').val(item.gembaKaizenGenerationDate || todayDate());
        $('#kaizenIdea').val(item.kaizenIdea || '');
        $('#pictureImage').val('');
        $('#pictureImageStored').val(item.pictureImage || '');
        $('#benefitsOfKaizen').val(item.benefitsOfKaizen || '');
        $('#isKaizenImplemented').val(item.isKaizenImplemented || 'No');
        $('#assignedTo').val(item.assignedTo || '');
        $('#assignmentRemark').val('');
        deriveDepartmentFromLocation(false);
    }

    function resetRecord() {
        $('#gembaKaizenRecordId').val('');
        $('#lastModifiedTime').val(currentTime());
        $('#department').val('');
        $('#classificationOfKaizen').val('');
        $('#gembaKaizenLocation').val('');
        $('#gembaKaizenGenerationDate').val(todayDate());
        $('#kaizenIdea').val('');
        $('#pictureImage').val('');
        $('#pictureImageStored').val('');
        $('#benefitsOfKaizen').val('');
        $('#isKaizenImplemented').val('No');
        setMessage('', 'success');
        loadOptions();
    }

    function deriveDepartmentFromLocation(force) {
        const location = $('#gembaKaizenLocation').val();
        const area = areaItems.find(function(item) {
            return String(item.name || '').trim().toLowerCase() === String(location || '').trim().toLowerCase();
        });
        const derivedDepartment = area && area.parentDepartment ? area.parentDepartment : '';
        if (derivedDepartment && (force || !$('#department').val())) {
            $('#department').val(derivedDepartment);
            $('#department').prop('disabled', true);
            return derivedDepartment;
        }
        $('#department').prop('disabled', false);
        return $('#department').val();
    }

    function renderTable() {
        const rows = records.map(function(record, index) {
            return '' +
                '<tr>' +
                '<td class="gk-row-number">' + (index + 1) + '</td>' +
                '<td>' + escapeHtml(record.id) + '</td>' +
                '<td>' + escapeHtml(record.lastModifiedTime) + '</td>' +
                '<td>' + escapeHtml(record.department) + '</td>' +
                '<td>' + escapeHtml(record.classificationOfKaizen) + '</td>' +
                '<td>' + escapeHtml(record.gembaKaizenLocation) + '</td>' +
                '<td>' + escapeHtml(displayDate(record.gembaKaizenGenerationDate)) + '</td>' +
                '<td>' + escapeHtml(record.kaizenIdea) + '</td>' +
                '<td>' + attachmentIcon('gemba-kaizen', record.pictureImage, record.pictureImage) + '</td>' +
                '<td>' + escapeHtml(record.benefitsOfKaizen) + '</td>' +
                '<td><span class="gk-status-pill">' + escapeHtml(record.isKaizenImplemented || 'No') + '</span></td>' +
                '<td>' + escapeHtml(record.assignedTo) + '</td>' +
                '<td class="assignment-history-cell" data-record-id="' + escapeHtml(record.id) + '">Loading...</td>' +
                '<td><button type="button" class="gk-table-action gk-edit-record" data-id="' + escapeHtml(record.id) + '" title="Edit" aria-label="Edit Gemba Kaizen"><i class="fas fa-pen"></i></button></td>' +
                '</tr>';
        }).join('');
        $('#gembaKaizenConfigRecordsBody').html(rows || '<tr><td colspan="14" class="gk-empty-cell">No records found.</td></tr>');
        records.forEach(function(record) { $.getJSON(API + '/records/' + record.id + '/history', function(entries) { $('.assignment-history-cell[data-record-id="' + record.id + '"]').html(formatAssignmentHistory(entries)); }); });
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
                $('#gembaKaizenConfigRecordsBody').html('<tr><td colspan="14" class="gk-empty-cell">Unable to load records.</td></tr>');
            }
        });
    }

    function openDrawer(record) {
        const isEdit = !!(record && record.id);
        $('#gembaKaizenDrawerTitle').text(isEdit ? 'Edit Gemba Kaizen' : 'Add Gemba Kaizen');
        $('#gembaKaizenSubmitBtn span').text(isEdit ? 'Update Kaizen' : 'Save Kaizen');
        setEditLock(isEdit);
        $('.gemba-kaizen-config-page').addClass('gk-drawer-open');
        $('#gembaKaizenConfigForm').attr('aria-hidden', 'false');
        $('#gembaKaizenConfigDrawerBackdrop').attr('aria-hidden', 'false');
    }

    function closeDrawer() {
        $('.gemba-kaizen-config-page').removeClass('gk-drawer-open');
        $('#gembaKaizenConfigForm').attr('aria-hidden', 'true');
        $('#gembaKaizenConfigDrawerBackdrop').attr('aria-hidden', 'true');
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
                setMessage('Unable to load selected Gemba Kaizen.', 'error');
            }
        });
    }

    function loadOptions() {
        return $.ajax({
            url: API + '/options',
            type: 'GET',
            data: {
                location: $('#gembaKaizenLocation').val() || '',
                recordId: $('#gembaKaizenRecordId').val() || ''
            },
            success: function(data) {
                const options = data && data.options ? data.options : {};
                currentUserIdentity = options.currentUser || {};
                areaItems = options.areaItems || [];
                populateSelect('#department', options.departments || [], $('#department').val());
                populateSelect('#classificationOfKaizen', options.classifications || [], $('#classificationOfKaizen').val());
                populateSelect('#gembaKaizenLocation', options.processAreas || [], $('#gembaKaizenLocation').val());
                deriveDepartmentFromLocation(false);
                const assignmentUsers = options.assignmentUsers || [];
                populateSelect('#assignedTo', assignmentUsers.map(function(user) { return user.username; }), $('#assignedTo').val());
                if (!$('#assignedTo').val() && options.defaultAssignedTo) {
                    $('#assignedTo').val(options.defaultAssignedTo);
                }
            },
            error: function() {
                setMessage('Unable to load Gemba Kaizen data.', 'error');
            }
        });
    }

    function saveRecord() {
        if (saveInFlight) {
            return;
        }
        const id = $('#gembaKaizenRecordId').val();
        saveInFlight = true;
        setSaveLoading(true);
        $.ajax({
            url: API + '/records' + (id ? '/' + encodeURIComponent(id) : ''),
            type: id ? 'PUT' : 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload()),
            success: function(data) {
                if (data && data.status === 'success' && data.record) {
                    setRecord(data.record);
                    setMessage(id ? 'Gemba Kaizen updated successfully.' : 'Gemba Kaizen saved successfully.', 'success');
                    loadRecords();
                    closeDrawer();
                } else {
                    setMessage('Unable to submit.', 'error');
                }
            },
            error: function(xhr) {
                setMessage(xhr.responseJSON?.message || 'Unable to submit.', 'error');
            },
            complete: function() {
                saveInFlight = false;
                setSaveLoading(false);
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

    $('#pictureImage').on('change', function() {
        const file = this.files && this.files[0];
        if (!file) {
            return;
        }
        const formData = new FormData();
        formData.append('file', file);
        if ($('#pictureImageStored').val()) {
            formData.append('replace', $('#pictureImageStored').val());
        }
        $.ajax({
            url: ATTACHMENT_API,
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            success: function(data) {
                $('#pictureImageStored').val(data.storedName || '');
            },
            error: function(xhr) {
                setMessage(xhr.responseJSON?.error || 'Image upload failed.', 'error');
            }
        });
    });

    $('#gembaKaizenLocation').on('change', function() {
        deriveDepartmentFromLocation(true);
        loadOptions();
    });

    $('#gembaKaizenConfigForm').on('submit', function(event) {
        event.preventDefault();
        saveRecord();
    });

    $('#gembaKaizenAddRecordBtn').on('click', function() {
        resetRecord();
        openDrawer(null);
    });

    $('#gembaKaizenConfigRecordsBody').on('click', '.gk-edit-record', function() {
        loadRecordIntoDrawer($(this).data('id'));
    });

    $('#gembaKaizenConfigDrawerClose, #gembaKaizenConfigCancelBtn').on('click', function() {
        closeDrawer();
    });

    $('#lastModifiedTime').val(currentTime());
    $('#gembaKaizenGenerationDate').val(todayDate());
    $('#isKaizenImplemented').val('No');

    loadRecords();
    loadOptions().always(function() {
        const id = params.get('id');
        if (!id) {
            return;
        }
        loadRecordIntoDrawer(id);
    });
});
