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
