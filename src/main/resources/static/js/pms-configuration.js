(function () {
    const tableBody = document.getElementById('adminUsersTableBody');
    const addBtn = document.getElementById('addUserBtn');
    const form = document.getElementById('addUserForm');

    const usernameEl = document.getElementById('newUsername');
    const emailEl = document.getElementById('newEmail');
    const passwordEl = document.getElementById('newPassword');
    const roleEl = document.getElementById('newRole');
    const messageEl = document.getElementById('addUserMessage');
    const tableMessageEl = document.getElementById('usersTableMessage');

    const modalEl = document.getElementById('editUserModal');
    const editUserIdEl = document.getElementById('editUserId');
    const editEmailEl = document.getElementById('editEmail');
    const editRoleEl = document.getElementById('editRole');
    const editPasswordEl = document.getElementById('editPassword');
    const editMessageEl = document.getElementById('editUserMessage');
    const saveEditBtn = document.getElementById('saveEditBtn');
    const closeEditModalBtn = document.getElementById('closeEditModal');
    const cancelEditBtn = document.getElementById('cancelEditBtn');

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
        return role === 'ADMIN' ? 'fa-user-shield' : 'fa-user';
    }

    function roleClass(role) {
        return role === 'ADMIN' ? 'admin' : 'user';
    }

    function renderUsers(users) {
        if (!tableBody) return;

        if (!Array.isArray(users) || users.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#64748b;padding:14px;">No users found.</td></tr>';
            return;
        }

        tableBody.innerHTML = users.map(function (u) {
            return '' +
                '<tr>' +
                '<td><i class="fas ' + userIcon(u.role) + '" style="margin-right:6px;color:#2563eb;"></i>' + escapeHtml(u.username) + '</td>' +
                '<td>' + escapeHtml(u.email || '-') + '</td>' +
                '<td><span class="pms-role-badge ' + roleClass(u.role) + '">' + escapeHtml(u.role) + '</span></td>' +
                '<td><span class="pms-status-badge active">' + escapeHtml(u.status || 'Active') + '</span></td>' +
                '<td class="pms-user-actions">' +
                '<button type="button" class="pms-action-btn edit" data-id="' + u.id + '" data-email="' + escapeHtml(u.email || '') + '" data-role="' + escapeHtml(u.role) + '"><i class="fas fa-pen"></i> Edit</button>' +
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
                    tableBody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#b91c1c;padding:14px;">Failed to load users.</td></tr>';
                    return;
                }
                renderUsers(data.users);
            })
            .catch(function () {
                tableBody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#b91c1c;padding:14px;">Error loading users.</td></tr>';
            });
    }

    function addUser() {
        const username = (usernameEl.value || '').trim();
        const email = (emailEl.value || '').trim();
        const password = passwordEl.value || '';
        const role = roleEl.value || 'USER';

        if (!username || !email || !password) {
            showMessage(messageEl, 'Please fill username, email, and password.', 'warning');
            return;
        }

        addBtn.disabled = true;
        addBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Adding...';

        fetch('/api/users', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: username, email: email, password: password, role: role })
        })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.status === 'success') {
                    showMessage(messageEl, 'User added successfully.', 'success');
                    showMessage(tableMessageEl, 'User created successfully.', 'success');
                    form.reset();
                    roleEl.value = 'USER';
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

    function openEditModal(id, email, role) {
        if (!modalEl) return;
        editUserIdEl.value = id;
        editEmailEl.value = email || '';
        editRoleEl.value = role || 'USER';
        editPasswordEl.value = '';
        editMessageEl.className = 'form-message';
        editMessageEl.textContent = '';
        modalEl.style.display = 'flex';
    }

    function closeEditModal() {
        if (!modalEl) return;
        modalEl.style.display = 'none';
    }

    function saveEdit() {
        const id = editUserIdEl.value;
        const email = (editEmailEl.value || '').trim();
        const role = editRoleEl.value || 'USER';
        const password = editPasswordEl.value || '';

        if (!id || !email) {
            showMessage(editMessageEl, 'Email is required.', 'warning');
            return;
        }

        saveEditBtn.disabled = true;
        saveEditBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';

        fetch('/api/users/' + encodeURIComponent(id), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: email, role: role, password: password })
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
        const ok = window.confirm('Are you sure?');
        if (!ok) return;

        fetch('/api/users/' + encodeURIComponent(id), { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.status === 'success') {
                    showMessage(tableMessageEl, 'User deleted successfully.', 'success');
                    loadUsers();
                } else {
                    showMessage(tableMessageEl, data.message || 'Failed to delete user.', 'error');
                }
            })
            .catch(function () {
                showMessage(tableMessageEl, 'Server error while deleting user.', 'error');
            });
    }

    function bindTableActions() {
        const editButtons = tableBody.querySelectorAll('.pms-action-btn.edit');
        const deleteButtons = tableBody.querySelectorAll('.pms-action-btn.delete');

        editButtons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                openEditModal(btn.dataset.id, btn.dataset.email, btn.dataset.role);
            });
        });

        deleteButtons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                deleteUser(btn.dataset.id);
            });
        });
    }

    if (addBtn) {
        addBtn.addEventListener('click', addUser);
    }

    if (saveEditBtn) {
        saveEditBtn.addEventListener('click', saveEdit);
    }

    if (closeEditModalBtn) {
        closeEditModalBtn.addEventListener('click', closeEditModal);
    }

    if (cancelEditBtn) {
        cancelEditBtn.addEventListener('click', closeEditModal);
    }

    if (modalEl) {
        modalEl.addEventListener('click', function (e) {
            if (e.target === modalEl) closeEditModal();
        });
    }

    [usernameEl, emailEl, passwordEl].forEach(function (el) {
        if (!el) return;
        el.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') addUser();
        });
    });

    loadUsers();
})();
