package org.example.model;

public class UserInfo {

    private final String username;
    private final String email;
    private final String password;
    private final String role;

    public UserInfo(String username, String email, String password, String role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public UserInfo(String username, String password, String role) {
        this(username, "", password, role);
    }

    public String getUsername() { return username; }
    public String getEmail()    { return email; }
    public String getPassword() { return password; }
    public String getRole()     { return role; }
}
