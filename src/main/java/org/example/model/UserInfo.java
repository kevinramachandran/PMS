package org.example.model;

import java.util.Set;

public class UserInfo {

    private final String username;
    private final String email;
    private final String password;
    private final String role;
    private final Set<String> viewPermissions;
    private final Set<String> editPermissions;

    public UserInfo(String username, String email, String password, String role) {
        this(username, email, password, role, Set.of(), Set.of());
    }

    public UserInfo(String username,
                    String email,
                    String password,
                    String role,
                    Set<String> viewPermissions,
                    Set<String> editPermissions) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.viewPermissions = viewPermissions == null ? Set.of() : Set.copyOf(viewPermissions);
        this.editPermissions = editPermissions == null ? Set.of() : Set.copyOf(editPermissions);
    }

    public UserInfo(String username, String password, String role) {
        this(username, "", password, role);
    }

    public String getUsername() { return username; }
    public String getEmail()    { return email; }
    public String getPassword() { return password; }
    public String getRole()     { return role; }
    public Set<String> getViewPermissions() { return viewPermissions; }
    public Set<String> getEditPermissions() { return editPermissions; }
}
