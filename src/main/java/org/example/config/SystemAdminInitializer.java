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

        user.setRole(RoleAccess.ADMIN);
        user.setPageViewPermissions(String.join(",", RoleAccess.CONFIG_PAGES));
        user.setPageEditPermissions(String.join(",", RoleAccess.CONFIG_PAGES));
        appUserRepository.save(user);
    }
}
