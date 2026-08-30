(function () {
    const IMAGE_TYPES = ['image/png', 'image/jpeg'];

    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (ch) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
        });
    }

    function build(container) {
        const module = container.dataset.module || '';
        const inputName = container.dataset.inputName || 'attachment';
        const originalInputName = container.dataset.originalInputName || inputName + 'OriginalName';
        const existingStored = container.dataset.storedName || '';
        const existingOriginal = container.dataset.originalName || '';
        const readonly = container.dataset.readonly === 'true';

        container.classList.add('pms-attachment');
        container.innerHTML = '' +
            '<input type="hidden" class="pms-attachment-stored" name="' + escapeHtml(inputName) + '" value="' + escapeHtml(existingStored) + '">' +
            '<input type="hidden" class="pms-attachment-original" name="' + escapeHtml(originalInputName) + '" value="' + escapeHtml(existingOriginal) + '">' +
            '<div class="pms-attachment-preview">' +
                '<img class="pms-attachment-img" alt="Attachment preview">' +
                '<div class="pms-attachment-empty"><i class="fas fa-image"></i><span>No image attached</span></div>' +
            '</div>' +
            '<div class="pms-attachment-meta">' +
                '<span class="pms-attachment-name"></span>' +
                '<span class="pms-attachment-status"></span>' +
            '</div>' +
            '<div class="pms-attachment-actions">' +
                '<button type="button" class="pms-attachment-btn pms-attachment-upload"><i class="fas fa-upload"></i><span>Upload</span></button>' +
                '<button type="button" class="pms-attachment-btn pms-attachment-camera"><i class="fas fa-camera"></i><span>Camera</span></button>' +
                '<button type="button" class="pms-attachment-btn pms-attachment-remove"><i class="fas fa-times"></i><span>Remove</span></button>' +
            '</div>' +
            '<input type="file" class="pms-attachment-file" accept="image/png,image/jpeg" hidden>' +
            '<input type="file" class="pms-attachment-capture" accept="image/png,image/jpeg" capture="environment" hidden>';

        const storedEl = container.querySelector('.pms-attachment-stored');
        const originalEl = container.querySelector('.pms-attachment-original');
        const imgEl = container.querySelector('.pms-attachment-img');
        const emptyEl = container.querySelector('.pms-attachment-empty');
        const nameEl = container.querySelector('.pms-attachment-name');
        const statusEl = container.querySelector('.pms-attachment-status');
        const uploadBtn = container.querySelector('.pms-attachment-upload');
        const cameraBtn = container.querySelector('.pms-attachment-camera');
        const removeBtn = container.querySelector('.pms-attachment-remove');
        const fileInput = container.querySelector('.pms-attachment-file');
        const captureInput = container.querySelector('.pms-attachment-capture');

        function fileUrl(storedName) {
            return '/api/attachments/' + encodeURIComponent(module) + '/file/' + encodeURIComponent(storedName);
        }

        function setStatus(message, type) {
            statusEl.textContent = message || '';
            statusEl.className = 'pms-attachment-status ' + (type || '');
        }

        function render() {
            const stored = storedEl.value || '';
            const original = originalEl.value || '';
            if (stored) {
                imgEl.src = fileUrl(stored);
                imgEl.style.display = 'block';
                emptyEl.style.display = 'none';
                nameEl.textContent = original || stored;
                removeBtn.style.display = readonly ? 'none' : 'inline-flex';
            } else {
                imgEl.removeAttribute('src');
                imgEl.style.display = 'none';
                emptyEl.style.display = 'flex';
                nameEl.textContent = '';
                removeBtn.style.display = 'none';
            }
            uploadBtn.style.display = readonly ? 'none' : 'inline-flex';
            cameraBtn.style.display = readonly ? 'none' : 'inline-flex';
        }

        function validate(file) {
            if (!file) return 'Please select an image.';
            if (!IMAGE_TYPES.includes(file.type)) return 'Only PNG and JPG images are allowed.';
            return '';
        }

        function upload(file) {
            const error = validate(file);
            if (error) {
                setStatus(error, 'error');
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            if (storedEl.value) {
                formData.append('replace', storedEl.value);
            }
            setStatus('Uploading...', 'loading');
            fetch('/api/attachments/' + encodeURIComponent(module) + '/upload', {
                method: 'POST',
                body: formData
            }).then(function (response) {
                if (response.status === 401) {
                    window.location.href = '/pms-login';
                    return Promise.reject(new Error('Unauthorized'));
                }
                return response.json();
            }).then(function (data) {
                if (data.error) {
                    setStatus(data.error, 'error');
                    return;
                }
                storedEl.value = data.storedName || '';
                originalEl.value = data.originalName || file.name || '';
                setStatus('Image attached.', 'success');
                render();
                container.dispatchEvent(new CustomEvent('pms-attachment-change', { detail: data, bubbles: true }));
            }).catch(function () {
                setStatus('Image upload failed.', 'error');
            }).finally(function () {
                fileInput.value = '';
                captureInput.value = '';
            });
        }

        uploadBtn.addEventListener('click', function () { fileInput.click(); });
        cameraBtn.addEventListener('click', function () { captureInput.click(); });
        removeBtn.addEventListener('click', function () {
            storedEl.value = '';
            originalEl.value = '';
            setStatus('Image removed.', 'success');
            render();
            container.dispatchEvent(new CustomEvent('pms-attachment-change', { detail: { removed: true }, bubbles: true }));
        });
        fileInput.addEventListener('change', function () { upload(fileInput.files && fileInput.files[0]); });
        captureInput.addEventListener('change', function () { upload(captureInput.files && captureInput.files[0]); });

        render();
    }

    window.PmsAttachment = {
        init: function (root) {
            (root || document).querySelectorAll('[data-pms-attachment]:not(.pms-attachment)').forEach(build);
        }
    };

    document.addEventListener('DOMContentLoaded', function () {
        window.PmsAttachment.init(document);
    });
})();
