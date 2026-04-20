(function () {
    const tableBody = document.getElementById('adminUsersTableBody');
    const addBtn = document.getElementById('addUserBtn');
    const form = document.getElementById('addUserForm');

    const usernameEl = document.getElementById('newUsername');
    const emailEl = document.getElementById('newEmail');
    const passwordEl = document.getElementById('newPassword');
    const roleEl = document.getElementById('newRole');
    const newPermissionsSectionEl = document.getElementById('newPermissionsSection');
    const newPermissionsMatrixEl = document.getElementById('newPermissionsMatrix');
    const messageEl = document.getElementById('addUserMessage');
    const tableMessageEl = document.getElementById('usersTableMessage');

    const modalEl = document.getElementById('editUserModal');
    const editUserIdEl = document.getElementById('editUserId');
    const editEmailEl = document.getElementById('editEmail');
    const editRoleEl = document.getElementById('editRole');
    const editPermissionsSectionEl = document.getElementById('editPermissionsSection');
    const editPermissionsMatrixEl = document.getElementById('editPermissionsMatrix');
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
    const canEditUserManagement = String(document.body.getAttribute('data-can-edit-user-management') || '').toLowerCase() === 'true';

    const PERMISSION_GROUPS = [
        {
            key: 'workspace',
            label: 'PMS4 Deck V0 Data',
            description: 'Operational data-entry pages and their child pages.',
            items: [
                {
                    key: 'PMS_DATA_ENTRY',
                    label: 'PMS4 Deck V0 Data',
                    description: 'All child pages under Top Priorities, weekly priorities, daily performance, and daily sections.'
                },
                {
                    key: 'PRODUCTION_METRICS_DATA',
                    label: 'Production Metrics Data',
                    description: 'Metrics entry, imports, and updates for production metrics.'
                }
            ]
        },
        {
            key: 'configuration',
            label: 'Configuration Pages',
            description: 'Setup screens that control daily boards, trackers, and KPI modules.',
            items: [
                { key: 'ISSUE_BOARD_CONFIGURATION', label: 'Issue Board Configuration', description: 'Issue board templates and assignment setup.' },
                { key: 'GEMBA_WALK_CONFIGURATION', label: 'Gemba Walk Configuration', description: 'Schedules and settings for gemba walk planning.' },
                { key: 'LEADERSHIP_GEMBA_TRACKER_CONFIGURATION', label: 'Safety Gemba Tracker Config', description: 'Leadership safety gemba tracker configuration.' },
                { key: 'TRAINING_SCHEDULE_CONFIGURATION', label: 'Training Schedule Config', description: 'Training schedule periods and configuration data.' },
                { key: 'MEETING_AGENDA_CONFIGURATION', label: 'PMS Agenda Config', description: 'Meeting agenda configuration and period updates.' },
                { key: 'PROCESS_CONFIRMATION_CONFIGURATION', label: 'Process Confirmation Config', description: 'Process confirmation templates and period updates.' },
                { key: 'ABNORMALITY_TRACKER_CONFIGURATION', label: 'Abnormality Tracker Config', description: 'Abnormality tracker lists and save actions.' },
                { key: 'HS_CROSS_DAILY_CONFIGURATION', label: 'H&S Cross Daily Config', description: 'Health and safety daily cross settings.' },
                { key: 'LSR_TRACKING_CONFIGURATION', label: 'LSR Tracking Config', description: 'LSR daily tracking settings and updates.' },
                { key: 'KPI_FOOTER_BUTTONS', label: 'KPI Footer Buttons', description: 'Footer button labels, URLs, and uploads.' },
                { key: 'KPI_TARGET_CROSS_COLOR', label: 'KPI Target Cross Color', description: 'Color rules used for KPI cross alert states.' },
                { key: 'EMAIL_CONFIGURATION', label: 'Email Configuration', description: 'SMTP settings and outbound mail test configuration.' }
            ]
        },
        {
            key: 'administration',
            label: 'Administration',
            description: 'Administrative screens for user and license access.',
            items: [
                { key: 'USER_MANAGEMENT', label: 'User Management', description: 'User list, role updates, and permission assignment.' },
                { key: 'LICENSE_MANAGEMENT', label: 'License Management', description: 'License review, decode, and activation controls.' }
            ]
        }
    ];

    const PERMISSION_LABELS = PERMISSION_GROUPS
        .flatMap(function (group) { return group.items; })
        .reduce(function (accumulator, item) {
            accumulator[item.key] = item.label;
            return accumulator;
        }, {});

    const ALL_PERMISSION_KEYS = Object.keys(PERMISSION_LABELS);

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

    function permissionLabel(permissionKey) {
        return PERMISSION_LABELS[permissionKey] || permissionKey;
    }

    function escapeAttribute(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function buildPermissionTooltip(title, items) {
        if (!items.length) {
            return title + '\nNo pages assigned';
        }

        return title + '\n' + items.map(function (item) {
            return '- ' + item;
        }).join('\n');
    }

    function formatPermissionSummary(user) {
        const view = new Set(readArray(user.viewPermissions));
        const edit = new Set(readArray(user.editPermissions));
        const viewLabels = Array.from(view)
            .filter(function (permissionKey) { return PERMISSION_LABELS[permissionKey]; })
            .map(permissionLabel)
            .sort();

        const editLabels = Array.from(edit)
            .filter(function (permissionKey) { return PERMISSION_LABELS[permissionKey]; })
            .map(permissionLabel)
            .sort();

        const touchedLabels = Array.from(new Set(viewLabels.concat(editLabels)));

        if (!touchedLabels.length) {
            return '<span class="permission-empty-state">No page access assigned</span>';
        }

        const allTooltip = buildPermissionTooltip('Assigned pages', touchedLabels);
        const viewTooltip = buildPermissionTooltip('View access', viewLabels);
        const editTooltip = buildPermissionTooltip('Edit access', editLabels);

        return '' +
            '<div class="permission-summary">' +
                '<div class="permission-summary-icons">' +
                    '<span class="permission-icon-badge all" title="' + escapeAttribute(allTooltip) + '" aria-label="' + escapeAttribute(allTooltip) + '">' +
                        '<i class="fas fa-shield-alt"></i>' +
                        '<span class="permission-icon-count">' + touchedLabels.length + '</span>' +
                    '</span>' +
                    '<span class="permission-icon-badge view" title="' + escapeAttribute(viewTooltip) + '" aria-label="' + escapeAttribute(viewTooltip) + '">' +
                        '<i class="fas fa-eye"></i>' +
                        '<span class="permission-icon-count">' + view.size + '</span>' +
                    '</span>' +
                    '<span class="permission-icon-badge edit" title="' + escapeAttribute(editTooltip) + '" aria-label="' + escapeAttribute(editTooltip) + '">' +
                        '<i class="fas fa-pen"></i>' +
                        '<span class="permission-icon-count">' + edit.size + '</span>' +
                    '</span>' +
                '</div>' +
                '<div class="permission-summary-caption" title="' + escapeAttribute(allTooltip) + '">' +
                    escapeHtml(touchedLabels.slice(0, 2).join(', ')) + (touchedLabels.length > 2 ? ' +' + (touchedLabels.length - 2) + ' more' : '') +
                '</div>' +
            '</div>';
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

    function renderPermissionMatrix(scope, container) {
        if (!container) {
            return;
        }

        container.innerHTML = PERMISSION_GROUPS.map(function (group) {
            const cards = group.items.map(function (item) {
                return '' +
                    '<div class="permission-card">' +
                        '<div class="permission-card-copy">' +
                            '<span class="permission-card-title">' + escapeHtml(item.label) + '</span>' +
                            '<span class="permission-card-description">' + escapeHtml(item.description) + '</span>' +
                        '</div>' +
                        '<div class="permission-card-toggles">' +
                            '<label class="permission-switch view">' +
                                '<input type="checkbox" data-scope="' + scope + '" data-group="' + group.key + '" data-page="' + item.key + '" data-type="view">' +
                                '<span>View</span>' +
                            '</label>' +
                            '<label class="permission-switch edit">' +
                                '<input type="checkbox" data-scope="' + scope + '" data-group="' + group.key + '" data-page="' + item.key + '" data-type="edit">' +
                                '<span>Edit</span>' +
                            '</label>' +
                        '</div>' +
                    '</div>';
            }).join('');

            return '' +
                '<section class="permission-group-card">' +
                    '<div class="permission-group-header">' +
                        '<div>' +
                            '<span class="permission-group-kicker">' + escapeHtml(group.label) + '</span>' +
                            '<p>' + escapeHtml(group.description) + '</p>' +
                        '</div>' +
                        '<div class="permission-group-actions">' +
                            '<button type="button" class="permission-bulk-btn" data-scope="' + scope + '" data-group="' + group.key + '" data-bulk="view">View all</button>' +
                            '<button type="button" class="permission-bulk-btn" data-scope="' + scope + '" data-group="' + group.key + '" data-bulk="edit">Edit all</button>' +
                            '<button type="button" class="permission-bulk-btn clear" data-scope="' + scope + '" data-group="' + group.key + '" data-bulk="clear">Clear</button>' +
                        '</div>' +
                    '</div>' +
                    '<div class="permission-card-grid">' + cards + '</div>' +
                '</section>';
        }).join('');
    }

    function applyGroupSelection(scope, groupKey, mode) {
        const groupInputs = document.querySelectorAll('input[data-scope="' + scope + '"][data-group="' + groupKey + '"]');
        if (!groupInputs.length) {
            return;
        }

        groupInputs.forEach(function (input) {
            if (mode === 'clear') {
                input.checked = false;
                return;
            }

            if (mode === 'view' && input.dataset.type === 'view') {
                input.checked = true;
            }

            if (mode === 'edit') {
                input.checked = true;
            }
        });

        syncPermissionDependencies(scope);
    }

    function buildDefaultAdminPermissions() {
        return {
            viewPermissions: ALL_PERMISSION_KEYS.slice(),
            editPermissions: ALL_PERMISSION_KEYS.slice()
        };
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
                                '<td>' + formatPermissionSummary(u) + '</td>' +
                '<td><span class="pms-status-badge active">' + escapeHtml(u.status || 'Active') + '</span></td>' +
                                '<td class="pms-user-actions">' +
                                (canEditUserManagement
                                        ? '<button type="button" class="pms-action-btn edit" data-id="' + u.id + '" data-email="' + escapeHtml(u.email || '') + '" data-role="' + escapeHtml(u.role) + '" data-view-permissions="' + escapeHtml(readArray(u.viewPermissions).join(',')) + '" data-edit-permissions="' + escapeHtml(readArray(u.editPermissions).join(',')) + '"><i class="fas fa-pen"></i> Edit</button>' +
                                            '<button type="button" class="pms-action-btn delete" data-id="' + u.id + '"><i class="fas fa-trash"></i> Delete</button>'
                                        : '<span class="permission-view-only"><i class="fas fa-eye"></i> View only</span>') +
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
            ? buildDefaultAdminPermissions()
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
        if (!canEditUserManagement) return;
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
            ? buildDefaultAdminPermissions()
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

    function syncPermissionDependencies(scope) {
        const editInputs = document.querySelectorAll('input[data-scope="' + scope + '"][data-type="edit"]');
        editInputs.forEach(function (input) {
            const page = String(input.dataset.page || '').toUpperCase();
            const relatedView = document.querySelector('input[data-scope="' + scope + '"][data-type="view"][data-page="' + page + '"]');
            if (!relatedView) {
                return;
            }

            if (input.checked) {
                relatedView.checked = true;
            }

            if (!relatedView.checked) {
                input.checked = false;
            }
        });
    }

    function bindPermissionBulkActions(container, scope) {
        if (!container) {
            return;
        }

        container.addEventListener('click', function (event) {
            const button = event.target.closest('.permission-bulk-btn');
            if (!button || button.dataset.scope !== scope) {
                return;
            }

            applyGroupSelection(scope, button.dataset.group, button.dataset.bulk);
        });
    }

    renderPermissionMatrix('new', newPermissionsMatrixEl);
    renderPermissionMatrix('edit', editPermissionsMatrixEl);

    bindPermissionRules('new');
    bindPermissionRules('edit');
    bindPermissionBulkActions(newPermissionsMatrixEl, 'new');
    bindPermissionBulkActions(editPermissionsMatrixEl, 'edit');

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
