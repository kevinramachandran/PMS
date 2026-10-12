(function () {
    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (ch) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]; });
    }

    function ensureModal() {
        if ($('#attachmentPreviewModal').length) return;
        $('body').append('<div id="attachmentPreviewModal" class="attachment-preview-modal" aria-hidden="true"><div class="attachment-preview-dialog" role="dialog" aria-modal="true" aria-label="Attachment preview"><button type="button" class="attachment-preview-close" aria-label="Close attachment preview"><i class="fas fa-times"></i></button><img id="attachmentPreviewImage" alt="Attachment preview"><div id="attachmentPreviewName"></div></div></div>');
    }

    function open(module, file, name) {
        if (!file) return;
        ensureModal();
        $('#attachmentPreviewImage').attr('src', '/api/attachments/' + encodeURIComponent(module) + '/file/' + encodeURIComponent(file));
        $('#attachmentPreviewName').text(name || file);
        $('#attachmentPreviewModal').attr('aria-hidden', 'false').addClass('is-open');
    }

    let currentAttachmentId = 0;
    function ensureId(element, prefix) {
        if (element && !element.id) element.id = prefix + '-' + (++currentAttachmentId);
        return element ? element.id : '';
    }

    function renderCurrentAttachment(container, module, storedInput, fileInput, label) {
        if (!container || !storedInput) return;
        container.classList.add('current-attachment-field');
        const storedId = ensureId(storedInput, 'current-attachment-stored');
        const fileId = ensureId(fileInput, 'current-attachment-file');
        const labelId = ensureId(label, 'current-attachment-label');
        const file = storedInput.value || '';
        container.dataset.module = module || '';
        container.dataset.storedInput = storedId;
        container.dataset.fileInput = fileId;
        container.dataset.label = labelId;
        if (label) label.textContent = file ? 'Replace' : 'Choose';
        container.innerHTML = file
            ? '<span class="current-attachment-label">Image attached:</span>' +
                '<button type="button" class="attachment-preview-trigger current-attachment-preview" data-module="' + escapeHtml(module) + '" data-file="' + escapeHtml(file) + '" data-name="' + escapeHtml(file) + '" title="View attached image" aria-label="View attached image"><img src="/api/attachments/' + encodeURIComponent(module) + '/file/' + encodeURIComponent(file) + '" alt="Current attached image"></button>' +
                '<span class="current-attachment-name">' + escapeHtml(file) + '</span>' +
                '<button type="button" class="current-attachment-remove" aria-label="Remove attached image"><i class="fas fa-times"></i><span>Remove image</span></button>'
            : '<span class="current-attachment-empty">No image attached</span>';
    }

    window.PmsCurrentAttachment = { render: renderCurrentAttachment };

    $(document).on('click', '.current-attachment-remove', function () {
        const container = this.closest('.current-attachment-field');
        if (!container) return;
        const storedInput = document.getElementById(container.dataset.storedInput);
        const fileInput = document.getElementById(container.dataset.fileInput);
        const label = document.getElementById(container.dataset.label);
        if (storedInput) storedInput.value = '';
        if (fileInput) fileInput.value = '';
        renderCurrentAttachment(container, container.dataset.module, storedInput, fileInput, label);
    });

    $(document).on('click', '.attachment-preview-trigger', function () {
        open($(this).data('module'), $(this).data('file'), $(this).data('name'));
    });
    $(document).on('click', '.attachment-preview-close, #attachmentPreviewModal', function (event) {
        if (event.target === this || $(event.target).closest('.attachment-preview-close').length) {
            $('#attachmentPreviewModal').attr('aria-hidden', 'true').removeClass('is-open');
            $('#attachmentPreviewImage').removeAttr('src');
        }
    });
    $(document).on('keydown', function (event) {
        if (event.key === 'Escape') $('#attachmentPreviewModal').attr('aria-hidden', 'true').removeClass('is-open');
    });

    window.attachmentIcon = function (module, file, label) {
        if (!file) return '<span class="attachment-empty">-</span>';
        return '<button type="button" class="attachment-preview-trigger" data-module="' + escapeHtml(module) + '" data-file="' + escapeHtml(file) + '" data-name="' + escapeHtml(label || file) + '" title="View attachment" aria-label="View attachment"><i class="fas fa-paperclip"></i></button>';
    };
}());
