$(function() {
    'use strict';

    const API = '/api/gemba-kaizen-config';
    const ATTACHMENT_API = '/api/attachments/gemba-kaizen/upload';
    const params = new URLSearchParams(window.location.search);
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

    function resetRecord() {
        $('#gembaKaizenRecordId').val('');
        $('#name').val('');
        $('#lastModifiedTime').val(currentTime());
        $('#gembaKaizenProviderName').val('');
        $('#employeeIdHoNumber').val('');
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

    function renderTable() {
        const rows = records.map(function(record, index) {
            return '' +
                '<tr>' +
                '<td class="gk-row-number">' + (index + 1) + '</td>' +
                '<td>' + escapeHtml(record.id) + '</td>' +
                '<td>' + escapeHtml(record.name) + '</td>' +
                '<td>' + escapeHtml(record.lastModifiedTime) + '</td>' +
                '<td>' + escapeHtml(record.gembaKaizenProviderName) + '</td>' +
                '<td>' + escapeHtml(record.employeeIdHoNumber) + '</td>' +
                '<td>' + escapeHtml(record.department) + '</td>' +
                '<td>' + escapeHtml(record.classificationOfKaizen) + '</td>' +
                '<td>' + escapeHtml(record.gembaKaizenLocation) + '</td>' +
                '<td>' + escapeHtml(record.gembaKaizenGenerationDate) + '</td>' +
                '<td>' + escapeHtml(record.kaizenIdea) + '</td>' +
                '<td>' + escapeHtml(record.pictureImage) + '</td>' +
                '<td>' + escapeHtml(record.benefitsOfKaizen) + '</td>' +
                '<td><span class="gk-status-pill">' + escapeHtml(record.isKaizenImplemented || 'No') + '</span></td>' +
                '<td><button type="button" class="gk-table-action gk-edit-record" data-id="' + escapeHtml(record.id) + '" title="Edit" aria-label="Edit Gemba Kaizen"><i class="fas fa-pen"></i></button></td>' +
                '</tr>';
        }).join('');
        $('#gembaKaizenConfigRecordsBody').html(rows || '<tr><td colspan="15" class="gk-empty-cell">No records found.</td></tr>');
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
                $('#gembaKaizenConfigRecordsBody').html('<tr><td colspan="15" class="gk-empty-cell">Unable to load records.</td></tr>');
            }
        });
    }

    function openDrawer(record) {
        $('#gembaKaizenDrawerTitle').text(record && record.id ? 'Edit Gemba Kaizen' : 'Add Gemba Kaizen');
        $('#gembaKaizenSubmitBtn span').text(record && record.id ? 'Update Kaizen' : 'Save Kaizen');
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
            success: function(data) {
                const options = data && data.options ? data.options : {};
                const currentUser = options.currentUser || {};
                if (!$('#name').val()) {
                    $('#name').val(currentUser.name || '');
                }
                if (!$('#gembaKaizenProviderName').val()) {
                    $('#gembaKaizenProviderName').val(currentUser.name || '');
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
