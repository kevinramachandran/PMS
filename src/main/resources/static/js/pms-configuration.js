(function () {
    const tableBody = document.getElementById('adminUsersTableBody');
    const addBtn = document.getElementById('addUserBtn');
    const form = document.getElementById('addUserForm');

    const usernameEl = document.getElementById('newUsername');
    const emailEl = document.getElementById('newEmail');
    const passwordEl = document.getElementById('newPassword');
    const roleEl = document.getElementById('newRole');
    const newPermissionsSectionEl = document.getElementById('newPermissionsSection');
    const messageEl = document.getElementById('addUserMessage');
    const tableMessageEl = document.getElementById('usersTableMessage');

    const modalEl = document.getElementById('editUserModal');
    const editUserIdEl = document.getElementById('editUserId');
    const editEmailEl = document.getElementById('editEmail');
    const editRoleEl = document.getElementById('editRole');
    const editPermissionsSectionEl = document.getElementById('editPermissionsSection');
    const editPasswordEl = document.getElementById('editPassword');
    const editMessageEl = document.getElementById('editUserMessage');
    const saveEditBtn = document.getElementById('saveEditBtn');
    const closeEditModalBtn = document.getElementById('closeEditModal');
    const cancelEditBtn = document.getElementById('cancelEditBtn');

    const deleteModalEl = document.getElementById('deleteUserModal');
    const confirmDeleteBtn = document.getElementById('confirmDeleteBtn');
    const closeDeleteModalBtn = document.getElementById('closeDeleteModal');
    const cancelDeleteBtn = document.getElementById('cancelDeleteBtn');

    let pendingDeleteUserId = null;

    function normalizeRole(role) {
        const value = String(role || '').trim().toUpperCase().replace(/\s+/g, '_');
        if (value === 'ADMIN') {
            return 'ADMIN';
        }
        return 'USER';
    }

    function roleLabel(role) {
        switch (normalizeRole(role)) {
            case 'ADMIN':
                return 'Admin';
            default:
                return 'User';
        }
    }

    function showMessage(targetEl, text, type) {
        if (!targetEl) return;
        targetEl.textContent = text;
        targetEl.className = 'form-message show ' + type;
        setTimeout(function () {
            targetEl.className = 'form-message';
            targetEl.textContent = '';
        }, 3500);
    }

    function userIcon(role) {
        const normalized = normalizeRole(role);
        if (normalized === 'ADMIN') return 'fa-user-shield';
        return 'fa-user';
    }

    function roleClass(role) {
        const normalized = normalizeRole(role);
        if (normalized === 'ADMIN') return 'admin';
        return 'l1';
    }

    function readArray(value) {
        if (Array.isArray(value)) {
            return value.map(function (item) { return String(item || '').trim().toUpperCase(); }).filter(Boolean);
        }
        if (!value) {
            return [];
        }
        return String(value)
            .split(',')
            .map(function (item) { return item.trim().toUpperCase(); })
            .filter(Boolean);
    }

    function formatPermissionSummary(user) {
        const view = new Set(readArray(user.viewPermissions));
        const edit = new Set(readArray(user.editPermissions));
        const parts = [];

        if (view.has('SETTINGS') || edit.has('SETTINGS')) {
            parts.push('PMS: ' + (edit.has('SETTINGS') ? 'Edit' : 'View'));
        }

        if (view.has('EMAIL_CONFIGURATION') || edit.has('EMAIL_CONFIGURATION')) {
            parts.push('Email: ' + (edit.has('EMAIL_CONFIGURATION') ? 'Edit' : 'View'));
        }

        return parts.length ? parts.join(' | ') : '-';
    }

    function setPermissionInputs(scope, values, type) {
        const normalized = new Set(readArray(values));
        const inputs = document.querySelectorAll('input[data-scope="' + scope + '"][data-type="' + type + '"]');
        inputs.forEach(function (input) {
            input.checked = normalized.has(String(input.dataset.page || '').toUpperCase());
        });
    }

    function collectPermissions(scope) {
        const viewInputs = document.querySelectorAll('input[data-scope="' + scope + '"][data-type="view"]');
        const editInputs = document.querySelectorAll('input[data-scope="' + scope + '"][data-type="edit"]');
        const viewPermissions = [];
        const editPermissions = [];

        viewInputs.forEach(function (input) {
            if (input.checked) {
                viewPermissions.push(String(input.dataset.page || '').toUpperCase());
            }
        });

        editInputs.forEach(function (input) {
            if (input.checked) {
                const page = String(input.dataset.page || '').toUpperCase();
                editPermissions.push(page);

                const relatedView = document.querySelector('input[data-scope="' + scope + '"][data-type="view"][data-page="' + page + '"]');
                if (relatedView && !relatedView.checked) {
                    relatedView.checked = true;
                    if (!viewPermissions.includes(page)) {
                        viewPermissions.push(page);
                    }
                }
            }
        });

        return {
            viewPermissions: Array.from(new Set(viewPermissions)),
            editPermissions: Array.from(new Set(editPermissions))
        };
    }

    function resetPermissions(scope) {
        setPermissionInputs(scope, [], 'view');
        setPermissionInputs(scope, [], 'edit');
    }

    function togglePermissionSection(roleElement, sectionElement) {
        if (!roleElement || !sectionElement) return;
        const isAdmin = normalizeRole(roleElement.value) === 'ADMIN';
        sectionElement.style.display = isAdmin ? 'none' : 'block';
    }

    function renderUsers(users) {
        if (!tableBody) return;

        if (!Array.isArray(users) || users.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#64748b;padding:14px;">No users found.</td></tr>';
            return;
        }

        tableBody.innerHTML = users.map(function (u) {
            return '' +
                '<tr>' +
                '<td><i class="fas ' + userIcon(u.role) + '" style="margin-right:6px;color:#2563eb;"></i>' + escapeHtml(u.username) + '</td>' +
                '<td>' + escapeHtml(u.email || '-') + '</td>' +
                '<td><span class="pms-role-badge ' + roleClass(u.role) + '">' + escapeHtml(u.roleLabel || roleLabel(u.role)) + '</span></td>' +
                '<td>' + escapeHtml(formatPermissionSummary(u)) + '</td>' +
                '<td><span class="pms-status-badge active">' + escapeHtml(u.status || 'Active') + '</span></td>' +
                '<td class="pms-user-actions">' +
                '<button type="button" class="pms-action-btn edit" data-id="' + u.id + '" data-email="' + escapeHtml(u.email || '') + '" data-role="' + escapeHtml(u.role) + '" data-view-permissions="' + escapeHtml(readArray(u.viewPermissions).join(',')) + '" data-edit-permissions="' + escapeHtml(readArray(u.editPermissions).join(',')) + '"><i class="fas fa-pen"></i> Edit</button>' +
                '<button type="button" class="pms-action-btn delete" data-id="' + u.id + '"><i class="fas fa-trash"></i> Delete</button>' +
                '</td>' +
                '</tr>';
        }).join('');

        bindTableActions();
    }

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function loadUsers() {
        if (!tableBody) return;

        fetch('/api/users')
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.status !== 'success') {
                    tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#b91c1c;padding:14px;">Failed to load users.</td></tr>';
                    return;
                }
                renderUsers(data.users);
            })
            .catch(function () {
                tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#b91c1c;padding:14px;">Error loading users.</td></tr>';
            });
    }

    function addUser() {
        const username = (usernameEl.value || '').trim();
        const email = (emailEl.value || '').trim();
        const password = passwordEl.value || '';
        const role = normalizeRole(roleEl.value || 'USER');
        const permissions = role === 'ADMIN'
            ? { viewPermissions: ['SETTINGS', 'EMAIL_CONFIGURATION'], editPermissions: ['SETTINGS', 'EMAIL_CONFIGURATION'] }
            : collectPermissions('new');

        if (!username || !email || !password) {
            showMessage(messageEl, 'Please fill username, email, and password.', 'warning');
            return;
        }

        addBtn.disabled = true;
        addBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Adding...';

        fetch('/api/users', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: username,
                email: email,
                password: password,
                role: role,
                viewPermissions: permissions.viewPermissions,
                editPermissions: permissions.editPermissions
            })
        })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.status === 'success') {
                    showMessage(messageEl, 'User added successfully.', 'success');
                    showMessage(tableMessageEl, 'User created successfully.', 'success');
                    form.reset();
                    roleEl.value = 'USER';
                    resetPermissions('new');
                    togglePermissionSection(roleEl, newPermissionsSectionEl);
                    loadUsers();
                } else {
                    showMessage(messageEl, data.message || 'Failed to add user.', 'error');
                }
            })
            .catch(function () {
                showMessage(messageEl, 'Server error while adding user.', 'error');
            })
            .finally(function () {
                addBtn.disabled = false;
                addBtn.innerHTML = '<i class="fas fa-plus"></i> Add User';
            });
    }

    function openEditModal(id, email, role, viewPermissions, editPermissions) {
        if (!modalEl) return;
        editUserIdEl.value = id;
        editEmailEl.value = email || '';
        editRoleEl.value = normalizeRole(role || 'USER');
        setPermissionInputs('edit', viewPermissions, 'view');
        setPermissionInputs('edit', editPermissions, 'edit');
        togglePermissionSection(editRoleEl, editPermissionsSectionEl);
        editPasswordEl.value = '';
        editMessageEl.className = 'form-message';
        editMessageEl.textContent = '';
        modalEl.style.display = 'flex';
    }

    function closeEditModal() {
        if (!modalEl) return;
        modalEl.style.display = 'none';
    }

    function openDeleteModal(id) {
        if (!deleteModalEl || !id) return;
        pendingDeleteUserId = id;
        confirmDeleteBtn.disabled = false;
        confirmDeleteBtn.innerHTML = '<i class="fas fa-trash"></i> Delete User';
        deleteModalEl.style.display = 'flex';
    }

    function closeDeleteModal() {
        if (!deleteModalEl) return;
        deleteModalEl.style.display = 'none';
        pendingDeleteUserId = null;
    }

    function saveEdit() {
        const id = editUserIdEl.value;
        const email = (editEmailEl.value || '').trim();
        const role = normalizeRole(editRoleEl.value || 'USER');
        const password = editPasswordEl.value || '';
        const permissions = role === 'ADMIN'
            ? { viewPermissions: ['SETTINGS', 'EMAIL_CONFIGURATION'], editPermissions: ['SETTINGS', 'EMAIL_CONFIGURATION'] }
            : collectPermissions('edit');

        if (!id || !email) {
            showMessage(editMessageEl, 'Email is required.', 'warning');
            return;
        }

        saveEditBtn.disabled = true;
        saveEditBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';

        fetch('/api/users/' + encodeURIComponent(id), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                email: email,
                role: role,
                password: password,
                viewPermissions: permissions.viewPermissions,
                editPermissions: permissions.editPermissions
            })
        })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.status === 'success') {
                    showMessage(tableMessageEl, 'User updated successfully.', 'success');
                    closeEditModal();
                    loadUsers();
                } else {
                    showMessage(editMessageEl, data.message || 'Failed to update user.', 'error');
                }
            })
            .catch(function () {
                showMessage(editMessageEl, 'Server error while updating user.', 'error');
            })
            .finally(function () {
                saveEditBtn.disabled = false;
                saveEditBtn.innerHTML = '<i class="fas fa-save"></i> Save Changes';
            });
    }

    function deleteUser(id) {
        if (!id) return;

        confirmDeleteBtn.disabled = true;
        confirmDeleteBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Deleting...';

        fetch('/api/users/' + encodeURIComponent(id), { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.status === 'success') {
                    showMessage(tableMessageEl, 'User deleted successfully.', 'success');
                    closeDeleteModal();
                    loadUsers();
                } else {
                    showMessage(tableMessageEl, data.message || 'Failed to delete user.', 'error');
                }
            })
            .catch(function () {
                showMessage(tableMessageEl, 'Server error while deleting user.', 'error');
            })
            .finally(function () {
                if (!deleteModalEl || deleteModalEl.style.display === 'none') {
                    return;
                }
                confirmDeleteBtn.disabled = false;
                confirmDeleteBtn.innerHTML = '<i class="fas fa-trash"></i> Delete User';
            });
    }

    function bindTableActions() {
        const editButtons = tableBody.querySelectorAll('.pms-action-btn.edit');
        const deleteButtons = tableBody.querySelectorAll('.pms-action-btn.delete');

        editButtons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                openEditModal(
                    btn.dataset.id,
                    btn.dataset.email,
                    btn.dataset.role,
                    btn.dataset.viewPermissions,
                    btn.dataset.editPermissions
                );
            });
        });

        deleteButtons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                openDeleteModal(btn.dataset.id);
            });
        });
    }

    if (addBtn) {
        addBtn.addEventListener('click', addUser);
    }

    if (saveEditBtn) {
        saveEditBtn.addEventListener('click', saveEdit);
    }

    function bindPermissionRules(scope) {
        const editInputs = document.querySelectorAll('input[data-scope="' + scope + '"][data-type="edit"]');
        const viewInputs = document.querySelectorAll('input[data-scope="' + scope + '"][data-type="view"]');

        editInputs.forEach(function (input) {
            input.addEventListener('change', function () {
                const page = String(input.dataset.page || '').toUpperCase();
                if (!page || !input.checked) return;
                const relatedView = document.querySelector('input[data-scope="' + scope + '"][data-type="view"][data-page="' + page + '"]');
                if (relatedView) {
                    relatedView.checked = true;
                }
            });
        });

        viewInputs.forEach(function (input) {
            input.addEventListener('change', function () {
                const page = String(input.dataset.page || '').toUpperCase();
                if (!page || input.checked) return;
                const relatedEdit = document.querySelector('input[data-scope="' + scope + '"][data-type="edit"][data-page="' + page + '"]');
                if (relatedEdit) {
                    relatedEdit.checked = false;
                }
            });
        });
    }

    bindPermissionRules('new');
    bindPermissionRules('edit');

    if (roleEl) {
        roleEl.addEventListener('change', function () {
            togglePermissionSection(roleEl, newPermissionsSectionEl);
        });
    }

    if (editRoleEl) {
        editRoleEl.addEventListener('change', function () {
            togglePermissionSection(editRoleEl, editPermissionsSectionEl);
        });
    }

    if (closeEditModalBtn) {
        closeEditModalBtn.addEventListener('click', closeEditModal);
    }

    if (cancelEditBtn) {
        cancelEditBtn.addEventListener('click', closeEditModal);
    }

    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener('click', function () {
            if (!pendingDeleteUserId) return;
            deleteUser(pendingDeleteUserId);
        });
    }

    if (closeDeleteModalBtn) {
        closeDeleteModalBtn.addEventListener('click', closeDeleteModal);
    }

    if (cancelDeleteBtn) {
        cancelDeleteBtn.addEventListener('click', closeDeleteModal);
    }

    if (modalEl) {
        modalEl.addEventListener('click', function (e) {
            if (e.target === modalEl) closeEditModal();
        });
    }

    if (deleteModalEl) {
        deleteModalEl.addEventListener('click', function (e) {
            if (e.target === deleteModalEl) closeDeleteModal();
        });
    }

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            closeEditModal();
            closeDeleteModal();
        }
    });

    [usernameEl, emailEl, passwordEl].forEach(function (el) {
        if (!el) return;
        el.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') addUser();
        });
    });

    togglePermissionSection(roleEl, newPermissionsSectionEl);

    loadUsers();
})();
