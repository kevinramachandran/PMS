package org.example.config;

import org.example.entity.AppUser;
import org.example.repository.AppUserRepository;
import org.example.util.RoleAccess;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminInitializer {

    public static final String SYSTEM_ADMIN_USERNAME = "systemadmin";
    private static final String DEFAULT_EMAIL = "system.admin@local";
    private static final String DEFAULT_PASSWORD = "Admin@2026";

    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SystemAdminInitializer(AppUserRepository appUserRepository, JdbcTemplate jdbcTemplate) {
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSystemAdminUser() {
        ensurePermissionColumnsCanStoreAllPages();
        ensureCarlexProcessConfirmationColumns();
        ensureReassignmentColumns();
        ensureAdminUser(SYSTEM_ADMIN_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD, true);
    }

    private void ensurePermissionColumnsCanStoreAllPages() {
        try {
            jdbcTemplate.execute("ALTER TABLE app_users MODIFY COLUMN page_view_permissions TEXT NULL");
            jdbcTemplate.execute("ALTER TABLE app_users MODIFY COLUMN page_edit_permissions TEXT NULL");
        } catch (DataAccessException ex) {
            throw new IllegalStateException("Unable to widen app_users permission columns before creating admin users", ex);
        }
    }

    private void ensureCarlexProcessConfirmationColumns() {
        if (!tableExists("carlex_process_confirmations")) {
            return;
        }
        // Older installations used large VARCHARs for observation text. Their declared
        // widths exhaust MySQL's row budget even when the actual values are short.
        jdbcTemplate.queryForList("SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'carlex_process_confirmations' "
                + "AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH > 255 "
                + "AND COLUMN_DEFAULT IS NULL").forEach(column -> {
            String name = String.valueOf(column.get("COLUMN_NAME"));
            if (!name.matches("[A-Za-z0-9_]+")) {
                throw new IllegalStateException("Unexpected CarlEX column name");
            }
            String nullable = "YES".equals(column.get("IS_NULLABLE")) ? " NULL" : " NOT NULL";
            jdbcTemplate.execute("ALTER TABLE carlex_process_confirmations MODIFY COLUMN `" + name + "` TEXT" + nullable);
        });
        addColumnIfMissing("carlex_process_confirmations", "assigned_to", "TEXT NULL");
        addColumnIfMissing("carlex_process_confirmations", "department", "TEXT NULL");
        addColumnIfMissing("carlex_process_confirmations", "reassigned_to1", "TEXT NULL");
        addColumnIfMissing("carlex_process_confirmations", "reassignment1_remark", "TEXT NULL");
        addColumnIfMissing("carlex_process_confirmations", "assignment_remark", "TEXT NULL");
        addColumnIfMissing("carlex_process_confirmations", "reassigned_to2", "TEXT NULL");
        addColumnIfMissing("carlex_process_confirmations", "reassignment2_remark", "TEXT NULL");
    }

    private void ensureReassignmentColumns() {
        ensureReassignmentColumns("gemba_walk_records");
        ensureReassignmentColumns("gemba_kaizen_records");
        ensureReassignmentColumns("abnormality_reporting_records");
    }

    private void ensureReassignmentColumns(String table) {
        if (!tableExists(table)) return;
        addColumnIfMissing(table, "reassigned_to1", "VARCHAR(160) NULL");
        addColumnIfMissing(table, "reassignment1_remark", "TEXT NULL");
        addColumnIfMissing(table, "reassigned_to2", "VARCHAR(160) NULL");
        addColumnIfMissing(table, "reassignment2_remark", "TEXT NULL");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private void ensureAdminUser(String username, String email, String password, boolean resetPasswordOnStartup) {
        AppUser user = appUserRepository.findByUsernameIgnoreCase(username)
                .orElseGet(AppUser::new);

        boolean isNewUser = user.getId() == null;
        if (isNewUser) {
            user.setUsername(username);
            user.setEmail(email);
        }

        if (isNewUser || resetPasswordOnStartup) {
            user.setPassword(passwordEncoder.encode(password));
        }

        if (user.getName() == null || user.getName().isBlank()) {
            user.setName("System Admin");
        }
        user.setStatus("ACTIVE");
        user.setRole(RoleAccess.ADMIN);
        user.setPageViewPermissions(String.join(",", RoleAccess.CONFIG_PAGES));
        user.setPageEditPermissions(String.join(",", RoleAccess.CONFIG_PAGES));
        appUserRepository.save(user);
    }
}
