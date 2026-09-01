$(function() {
    'use strict';

    const API = '/api/gemba-kaizen-config';
    const ATTACHMENT_API = '/api/attachments/gemba-kaizen/upload';
    const params = new URLSearchParams(window.location.search);

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

    function setMessage(message, type) {
        $('#gembaKaizenConfigMessage')
            .removeClass('show success error')
            .addClass(type || 'success')
            .text(message || '')
            .toggleClass('show', !!message);
    }

    function populateSelect(selector, values, selected) {
        $(selector).html(['<option value=""></option>'].concat((values || []).map(optionHtml)).join('')).val(selected || '');
    }

    function payload() {
        return {
            name: $('#name').val(),
            lastModifiedTime: $('#lastModifiedTime').val(),
            gembaKaizenProviderName: $('#gembaKaizenProviderName').val(),
            employeeIdHoNumber: $('#employeeIdHoNumber').val(),
            department: $('#department').val(),
            classificationOfKaizen: $('#classificationOfKaizen').val(),
            gembaKaizenLocation: $('#gembaKaizenLocation').val(),
            gembaKaizenGenerationDate: $('#gembaKaizenGenerationDate').val() || null,
            kaizenIdea: $('#kaizenIdea').val(),
            pictureImage: $('#pictureImageStored').val(),
            benefitsOfKaizen: $('#benefitsOfKaizen').val(),
            isKaizenImplemented: $('#isKaizenImplemented').val()
        };
    }

    function setRecord(record) {
        const item = record || {};
        $('#gembaKaizenRecordId').val(item.id || '');
        $('#name').val(item.name || $('#name').val() || '');
        $('#lastModifiedTime').val(item.lastModifiedTime || currentTime());
        $('#gembaKaizenProviderName').val(item.gembaKaizenProviderName || '');
        $('#employeeIdHoNumber').val(item.employeeIdHoNumber || $('#employeeIdHoNumber').val() || '');
        $('#department').val(item.department || '');
        $('#classificationOfKaizen').val(item.classificationOfKaizen || '');
        $('#gembaKaizenLocation').val(item.gembaKaizenLocation || '');
        $('#gembaKaizenGenerationDate').val(item.gembaKaizenGenerationDate || todayDate());
        $('#kaizenIdea').val(item.kaizenIdea || '');
        $('#pictureImage').val('');
        $('#pictureImageStored').val(item.pictureImage || '');
        $('#benefitsOfKaizen').val(item.benefitsOfKaizen || '');
        $('#isKaizenImplemented').val(item.isKaizenImplemented || 'No');
    }

    function loadOptions() {
        return $.ajax({
            url: API + '/options',
            type: 'GET',
            success: function(data) {
                const options = data && data.options ? data.options : {};
                const currentUser = options.currentUser || {};
                if (!$('#name').val()) {
                    $('#name').val(currentUser.name || '');
                }
                if (!$('#employeeIdHoNumber').val()) {
                    $('#employeeIdHoNumber').val(currentUser.employeeId || '');
                }
                populateSelect('#department', options.departments || [], $('#department').val());
                populateSelect('#classificationOfKaizen', options.classifications || [], $('#classificationOfKaizen').val());
                populateSelect('#gembaKaizenLocation', options.processAreas || [], $('#gembaKaizenLocation').val());
            },
            error: function() {
                setMessage('Unable to load Gemba Kaizen data.', 'error');
            }
        });
    }

    function saveRecord() {
        const id = $('#gembaKaizenRecordId').val();
        $.ajax({
            url: API + '/records' + (id ? '/' + encodeURIComponent(id) : ''),
            type: id ? 'PUT' : 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload()),
            success: function(data) {
                if (data && data.status === 'success' && data.record) {
                    setRecord(data.record);
                    setMessage('Submitted.', 'success');
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

    $('#gembaKaizenConfigForm').on('submit', function(event) {
        event.preventDefault();
        saveRecord();
    });

    $('#lastModifiedTime').val(currentTime());
    $('#gembaKaizenGenerationDate').val(todayDate());
    $('#isKaizenImplemented').val('No');

    loadOptions().always(function() {
        const id = params.get('id');
        if (!id) {
            return;
        }
        $.ajax({
            url: API + '/records/' + encodeURIComponent(id),
            type: 'GET',
            success: function(data) {
                if (data && data.record) {
                    setRecord(data.record);
                }
            },
            error: function() {
                setMessage('Unable to load selected Gemba Kaizen.', 'error');
            }
        });
    });
});
