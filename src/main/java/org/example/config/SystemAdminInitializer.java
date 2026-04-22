package org.example.config;

import org.example.entity.AppUser;
import org.example.repository.AppUserRepository;
import org.example.util.RoleAccess;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminInitializer {

    public static final String SYSTEM_ADMIN_USERNAME = "systemadmin";
    private static final String DEFAULT_EMAIL = "system.admin@local";
    private static final String DEFAULT_PASSWORD = "Admin123";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SystemAdminInitializer(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSystemAdminUser() {
        AppUser user = appUserRepository.findByUsernameIgnoreCase(SYSTEM_ADMIN_USERNAME)
                .orElseGet(AppUser::new);

        boolean isNewUser = user.getId() == null;
        if (isNewUser) {
            user.setUsername(SYSTEM_ADMIN_USERNAME);
            user.setEmail(DEFAULT_EMAIL);
            user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        }

        user.setRole(RoleAccess.ADMIN);
        user.setPageViewPermissions(String.join(",", RoleAccess.CONFIG_PAGES));
        user.setPageEditPermissions(String.join(",", RoleAccess.CONFIG_PAGES));
        appUserRepository.save(user);
    }
}
