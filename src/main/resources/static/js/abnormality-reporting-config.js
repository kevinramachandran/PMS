$(function () {
    'use strict';

    const API = '/api/abnormality-reporting-config';
    const ATTACHMENT_API = '/api/attachments/abnormality-reporting/upload';
    const username = String($('body').data('username') || '').trim();
    let allUsers = [];

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function escapeAttr(value) {
        return escapeHtml(value);
    }

    function setMessage(message, type) {
        $('#abnormalityReportingMessage')
            .removeClass('show success error warning')
            .addClass(type || 'success')
            .text(message || '')
            .toggleClass('show', !!message);
    }

    function optionHtml(value) {
        const safe = escapeHtml(value || '');
        return '<option value="' + safe + '">' + safe + '</option>';
    }

    function populateSelect(selector, values) {
        const html = ['<option value=""></option>'].concat((values || []).map(optionHtml)).join('');
        $(selector).html(html);
    }

    function userLabel(user) {
        const name = user.name || user.username || '';
        const designation = user.designation || '';
        return designation ? name + ' - ' + designation : name;
    }

    function populateAssignTo(users) {
        allUsers = users || allUsers;
        const html = ['<option value=""></option>'].concat((users || []).map(function(user) {
            return '<option value="' + escapeAttr(user.username || '') + '">' + escapeHtml(userLabel(user)) + '</option>';
        })).join('');
        $('#assignTo').html(html);
    }

    function populateRaisedByUsers(users) {
        const html = (users || []).map(function(user) {
            return '<option value="' + escapeAttr(user.username || '') + '">' + escapeHtml(userLabel(user)) + '</option>';
        }).join('');
        $('#reportingUsersList').html(html);
    }

    function loadOptions() {
        return $.ajax({
            url: API + '/options',
            type: 'GET',
            success: function(data) {
                const options = data && data.options ? data.options : {};
                populateSelect('#typeOfTag', options.typeOfTags || []);
                populateSelect('#department', options.departments || []);
                populateSelect('#areaMachine', options.areaMachines || []);
                populateSelect('#abnormalityDefectType', options.abnormalityDefectTypes || []);
                populateAssignTo(options.assignableUsers || []);
                populateRaisedByUsers(options.assignableUsers || []);
                if (username && !$('#tagRaisedBy').val()) {
                    $('#tagRaisedBy').val(username);
                }
            },
            error: function() {
                setMessage('Unable to load configuration data.', 'error');
            }
        });
    }

    function loadDepartmentOptions(department) {
        $.ajax({
            url: API + '/department-options',
            type: 'GET',
            data: { department: department || '' },
            success: function(data) {
                const options = data && data.options ? data.options : {};
                populateAssignTo(options.assignableUsers || []);
            }
        });
    }

    function payload() {
        return {
            typeOfTag: $('#typeOfTag').val(),
            priority: $('#priority').val(),
            abnormalityTagNumber: $('#abnormalityTagNumber').val(),
            tagRaisedBy: $('#tagRaisedBy').val(),
            dateRaised: $('#dateRaised').val() || null,
            shift: $('#shift').val(),
            abnormalityRelatedTo: $('#abnormalityRelatedTo').val(),
            department: $('#department').val(),
            areaMachine: $('#areaMachine').val(),
            component: $('#component').val(),
            description: $('#description').val(),
            proposedAction: $('#proposedAction').val(),
            pictureImage: $('#pictureImageStored').val(),
            abnormalityDefectType: $('#abnormalityDefectType').val(),
            assignTo: $('#assignTo').val(),
            dateClosed: $('#dateClosed').val() || null,
            tagStatus: $('#tagStatus').val()
        };
    }

    function setForm(record) {
        const item = record || {};
        $('#abnormalityReportingId').val(item.id || '');
        $('#typeOfTag').val(item.typeOfTag || '');
        $('#priority').val(item.priority || '');
        $('#abnormalityTagNumber').val(item.abnormalityTagNumber || '');
        $('#tagRaisedBy').val(item.tagRaisedBy || username || '');
        $('#dateRaised').val(item.dateRaised || '');
        $('#shift').val(item.shift || '');
        $('#abnormalityRelatedTo').val(item.abnormalityRelatedTo || '');
        $('#department').val(item.department || '');
        $('#areaMachine').val(item.areaMachine || '');
        $('#component').val(item.component || '');
        $('#description').val(item.description || '');
        $('#proposedAction').val(item.proposedAction || '');
        $('#pictureImage').val('');
        $('#pictureImageStored').val(item.pictureImage || '');
        $('#abnormalityDefectType').val(item.abnormalityDefectType || '');
        loadDepartmentOptions(item.department || '');
        setTimeout(function() {
            $('#assignTo').val(item.assignTo || '');
        }, 150);
        $('#dateClosed').val(item.dateClosed || '');
        $('#tagStatus').val(item.tagStatus || '');
    }

    function resetForm() {
        setForm({});
    }

    function loadRecordById(id) {
        if (!id) {
            return;
        }
        $.ajax({
            url: API + '/records/' + encodeURIComponent(id),
            type: 'GET',
            success: function(data) {
                if (data && data.record) {
                    setForm(data.record);
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }
            },
            error: function() {
                setMessage('Unable to load selected record.', 'error');
            }
        });
    }

    function saveRecord() {
        const id = $('#abnormalityReportingId').val();
        $.ajax({
            url: API + '/records' + (id ? '/' + encodeURIComponent(id) : ''),
            type: id ? 'PUT' : 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload()),
            success: function(data) {
                if (data && data.status === 'success') {
                    setMessage('Saved.', 'success');
                    resetForm();
                } else {
                    setMessage((data && data.message) || 'Unable to save.', 'error');
                }
            },
            error: function(xhr) {
                setMessage(xhr.responseJSON?.message || 'Unable to save.', 'error');
            }
        });
    }

    $('#abnormalityReportingForm').on('submit', function(event) {
        event.preventDefault();
        saveRecord();
    });

    $('#department').on('change', function() {
        loadDepartmentOptions($(this).val());
    });

    $('#tagStatus').on('change input', function() {
        const closed = String($(this).val() || '').trim().toLowerCase() === 'closed';
        if (closed && !$('#dateClosed').val()) {
            $('#dateClosed').val(new Date().toISOString().slice(0, 10));
        }
        if (!closed) {
            $('#dateClosed').val('');
        }
    });

    $('#pictureImage').on('change', function() {
        const file = this.files && this.files[0];
        if (!file) return;
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
                setMessage('Image attached.', 'success');
            },
            error: function(xhr) {
                setMessage(xhr.responseJSON?.error || 'Image upload failed.', 'error');
            }
        });
    });

    const selectedRecordId = new URLSearchParams(window.location.search).get('id');
    const optionsRequest = loadOptions();
    optionsRequest.always(function() {
        loadRecordById(selectedRecordId);
    });
});
