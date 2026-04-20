(function () {
    const PASSWORD_MASK = '********';
    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const canEditCurrentPage = String(document.body && document.body.dataset ? document.body.dataset.canEditCurrentPage : '').toLowerCase() === 'true';

    const fields = {
        host: document.getElementById('smtpHost'),
        port: document.getElementById('smtpPort'),
        username: document.getElementById('smtpUsername'),
        password: document.getElementById('smtpPassword'),
        encryption: document.getElementById('smtpEncryption'),
        fromEmail: document.getElementById('fromEmail'),
        fromName: document.getElementById('fromName'),
        replyTo: document.getElementById('replyTo'),
        enabled: document.getElementById('emailEnabled')
    };

    const errors = {
        host: document.getElementById('smtpHostError'),
        port: document.getElementById('smtpPortError'),
        username: document.getElementById('smtpUsernameError'),
        password: document.getElementById('smtpPasswordError'),
        encryption: document.getElementById('smtpEncryptionError'),
        fromEmail: document.getElementById('fromEmailError'),
        fromName: document.getElementById('fromNameError'),
        replyTo: document.getElementById('replyToError')
    };

    const testBtn = document.getElementById('testConnectionBtn');
    const saveBtn = document.getElementById('saveEmailConfigBtn');
    const togglePasswordBtn = document.getElementById('togglePasswordBtn');
    const passwordMeta = document.getElementById('passwordMeta');
    const messageEl = document.getElementById('emailConfigMessage');

    let hasStoredPassword = false;
    let usingMaskedPassword = false;

    function ensureReadonlyBanner() {
        const container = document.querySelector('.email-config-page');
        if (!container || canEditCurrentPage || container.querySelector('.permission-readonly-banner')) {
            return;
        }

        const banner = document.createElement('div');
        banner.className = 'permission-readonly-banner';
        banner.innerHTML = '<i class="fas fa-lock"></i><div><strong>View only access</strong><span>You can review the current email configuration, but editing and connection testing are disabled for this account.</span></div>';
        container.insertBefore(banner, container.firstChild);
    }

    function applyReadonlyMode() {
        const container = document.querySelector('.email-config-page');
        if (!container || canEditCurrentPage) {
            return;
        }

        container.classList.add('permission-readonly-mode');
        ensureReadonlyBanner();

        Object.keys(fields).forEach(function (key) {
            const field = fields[key];
            if (!field) {
                return;
            }
            field.disabled = true;
            field.setAttribute('title', 'Edit access required');
        });

        if (togglePasswordBtn) {
            togglePasswordBtn.disabled = true;
            togglePasswordBtn.setAttribute('title', 'Edit access required');
        }
        if (testBtn) {
            testBtn.disabled = true;
            testBtn.setAttribute('title', 'Edit access required');
        }
        if (saveBtn) {
            saveBtn.disabled = true;
            saveBtn.setAttribute('title', 'Edit access required');
        }
    }

    function showMessage(message, type) {
        if (!messageEl) return;
        if (!message) {
            messageEl.className = 'form-message';
            messageEl.textContent = '';
            return;
        }
        messageEl.textContent = message;
        messageEl.className = 'form-message show ' + type;
    }

    function setFieldError(key, message) {
        const errorEl = errors[key];
        const fieldEl = fields[key];
        if (errorEl) {
            errorEl.textContent = message || '';
        }
        if (fieldEl) {
            fieldEl.classList.toggle('field-invalid', !!message);
        }
    }

    function clearErrors() {
        Object.keys(errors).forEach(function (key) {
            setFieldError(key, '');
        });
    }

    function trimValue(input) {
        return input && typeof input.value === 'string' ? input.value.trim() : '';
    }

    function isValidEmail(value) {
        return EMAIL_REGEX.test(String(value || '').trim());
    }

    function getNormalizedEncryption() {
        return trimValue(fields.encryption).toUpperCase() || 'NONE';
    }

    function getPasswordPayload() {
        const passwordValue = fields.password.value || '';
        if (usingMaskedPassword && passwordValue === PASSWORD_MASK) {
            return PASSWORD_MASK;
        }
        return passwordValue.trim();
    }

    function getPayload() {
        return {
            host: trimValue(fields.host),
            port: fields.port.value ? Number(fields.port.value) : null,
            username: trimValue(fields.username),
            password: getPasswordPayload(),
            encryption: getNormalizedEncryption(),
            fromEmail: trimValue(fields.fromEmail),
            fromName: trimValue(fields.fromName),
            replyTo: trimValue(fields.replyTo),
            enabled: !!fields.enabled.checked
        };
    }

    function validatePayload(includeSenderFields, showInlineErrors) {
        if (showInlineErrors) {
            clearErrors();
        }
        const payload = getPayload();
        let valid = true;

        function applyError(key, message) {
            if (showInlineErrors) {
                setFieldError(key, message);
            }
        }

        if (!payload.host) {
            applyError('host', 'SMTP Host is required.');
            valid = false;
        }

        if (!payload.port && payload.port !== 0) {
            applyError('port', 'SMTP Port is required.');
            valid = false;
        } else if (payload.port < 1 || payload.port > 65535) {
            applyError('port', 'Port must be between 1 and 65535.');
            valid = false;
        }

        if (!payload.username) {
            applyError('username', 'Username / Email is required.');
            valid = false;
        } else if (!isValidEmail(payload.username)) {
            applyError('username', 'Enter a valid email address.');
            valid = false;
        }

        if (!payload.password) {
            applyError('password', 'Password is required.');
            valid = false;
        }

        if (!['NONE', 'SSL', 'TLS'].includes(payload.encryption)) {
            applyError('encryption', 'Choose a valid encryption type.');
            valid = false;
        } else if (!isEncryptionCompatible(payload.port, payload.encryption)) {
            applyError('encryption', 'Use SSL for 465 and TLS for 587.');
            applyError('port', 'Selected port does not match encryption.');
            valid = false;
        }

        if (includeSenderFields) {
            if (!payload.fromEmail) {
                applyError('fromEmail', 'From Email Address is required.');
                valid = false;
            } else if (!isValidEmail(payload.fromEmail)) {
                applyError('fromEmail', 'Enter a valid email address.');
                valid = false;
            }

            if (!payload.fromName) {
                applyError('fromName', 'From Name is required.');
                valid = false;
            }

            if (payload.replyTo && !isValidEmail(payload.replyTo)) {
                applyError('replyTo', 'Reply-To Email must be valid.');
                valid = false;
            }
        }

        return { valid: valid, payload: payload };
    }

    function isEncryptionCompatible(port, encryption) {
        if (!port || !encryption) {
            return false;
        }
        if (port === 465) {
            return encryption === 'SSL';
        }
        if (port === 587) {
            return encryption === 'TLS';
        }
        return true;
    }

    function setButtonLoading(button, loadingText, isLoading, defaultHtml) {
        if (!button) return;
        if (isLoading) {
            button.dataset.originalHtml = button.innerHTML;
            button.disabled = true;
            button.innerHTML = loadingText;
            return;
        }
        button.disabled = !canEditCurrentPage;
        button.innerHTML = defaultHtml || button.dataset.originalHtml || button.innerHTML;
    }

    function refreshPasswordState() {
        if (usingMaskedPassword) {
            passwordMeta.textContent = 'Saved password is masked. Enter a new password only if you want to replace it.';
        } else if (hasStoredPassword) {
            passwordMeta.textContent = 'Changing this field will overwrite the saved SMTP password.';
        } else {
            passwordMeta.textContent = '';
        }
    }

    function applyLoadedConfiguration(data) {
        fields.host.value = data.host || '';
        fields.port.value = data.port || 587;
        fields.username.value = data.username || '';
        fields.encryption.value = (data.encryption || 'TLS').toUpperCase();
        fields.fromEmail.value = data.fromEmail || '';
        fields.fromName.value = data.fromName || '';
        fields.replyTo.value = data.replyTo || '';
        fields.enabled.checked = !!data.enabled;

        hasStoredPassword = !!data.passwordConfigured;
        usingMaskedPassword = hasStoredPassword;
        fields.password.value = hasStoredPassword ? PASSWORD_MASK : '';
        refreshPasswordState();
        updateTestButtonState();
        applyReadonlyMode();
    }

    function loadConfiguration() {
        showMessage('Loading email configuration...', 'info');
        fetch('/api/email-config')
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Unable to load email configuration.');
                }
                return response.json();
            })
            .then(function (data) {
                applyLoadedConfiguration(data || {});
                showMessage('', '');
            })
            .catch(function (error) {
                showMessage(error.message || 'Unable to load email configuration.', 'error');
            });
    }

    function saveConfiguration() {
        if (!canEditCurrentPage) {
            applyReadonlyMode();
            return;
        }

        const result = validatePayload(true, true);
        if (!result.valid) {
            showMessage('Please correct the highlighted fields.', 'error');
            return;
        }

        setButtonLoading(saveBtn, '<i class="fas fa-spinner fa-spin"></i> Saving...', true);
        fetch('/api/email-config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(result.payload)
        })
            .then(function (response) {
                return response.json().then(function (data) {
                    return { ok: response.ok, data: data };
                });
            })
            .then(function (resultData) {
                if (!resultData.ok) {
                    throw new Error(resultData.data && resultData.data.message ? resultData.data.message : 'Unable to save configuration.');
                }
                hasStoredPassword = true;
                usingMaskedPassword = true;
                fields.password.type = 'password';
                fields.password.value = PASSWORD_MASK;
                togglePasswordBtn.innerHTML = '<i class="fas fa-eye"></i>';
                refreshPasswordState();
                showMessage(resultData.data.message || 'Configuration saved successfully', 'success');
                updateTestButtonState();
            })
            .catch(function (error) {
                showMessage(error.message || 'Unable to save configuration.', 'error');
            })
            .finally(function () {
                setButtonLoading(saveBtn, '', false, '<i class="fas fa-save"></i> Save Configuration');
            });
    }

    function testConnection() {
        if (!canEditCurrentPage) {
            applyReadonlyMode();
            return;
        }

        const result = validatePayload(false, true);
        if (!result.valid) {
            showMessage('Please complete the required SMTP fields before testing.', 'error');
            return;
        }

        setButtonLoading(testBtn, '<i class="fas fa-spinner fa-spin"></i> Testing...', true);
        fetch('/api/email-config/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(result.payload)
        })
            .then(function (response) {
                return response.json().then(function (data) {
                    return { ok: response.ok, data: data };
                });
            })
            .then(function (resultData) {
                const data = resultData.data || {};
                showMessage(data.message || 'Test completed.', resultData.ok ? 'success' : 'error');
            })
            .catch(function () {
                showMessage('Unable to test SMTP connection. Please try again.', 'error');
            })
            .finally(function () {
                setButtonLoading(testBtn, '', false, '<i class="fas fa-plug"></i> Test Connection');
                updateTestButtonState();
            });
    }

    function updateTestButtonState() {
        if (!canEditCurrentPage) {
            testBtn.disabled = true;
            return;
        }

        const result = validatePayload(false, false);
        testBtn.disabled = !result.valid;
    }

    function handlePasswordInput() {
        const value = fields.password.value || '';
        if (usingMaskedPassword && value !== PASSWORD_MASK) {
            usingMaskedPassword = false;
        }
        refreshPasswordState();
        updateTestButtonState();
    }

    if (togglePasswordBtn) {
        togglePasswordBtn.addEventListener('click', function () {
            if (!canEditCurrentPage) {
                applyReadonlyMode();
                return;
            }
            const nextType = fields.password.type === 'password' ? 'text' : 'password';
            fields.password.type = nextType;
            togglePasswordBtn.innerHTML = nextType === 'password'
                ? '<i class="fas fa-eye"></i>'
                : '<i class="fas fa-eye-slash"></i>';
        });
    }

    [fields.host, fields.port, fields.username, fields.password, fields.encryption, fields.fromEmail, fields.fromName, fields.replyTo]
        .forEach(function (field) {
            if (!field) return;
            field.addEventListener('input', function () {
                if (field === fields.password) {
                    handlePasswordInput();
                } else {
                    updateTestButtonState();
                }
            });
            field.addEventListener('blur', function () {
                validatePayload(true, true);
            });
        });

    if (saveBtn) {
        saveBtn.addEventListener('click', saveConfiguration);
    }

    if (testBtn) {
        testBtn.addEventListener('click', testConnection);
    }

    applyReadonlyMode();
    loadConfiguration();
})();
