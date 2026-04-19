package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "page_view_permissions", length = 500)
    private String pageViewPermissions;

    @Column(name = "page_edit_permissions", length = 500)
    private String pageEditPermissions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPageViewPermissions() {
        return pageViewPermissions;
    }

    public void setPageViewPermissions(String pageViewPermissions) {
        this.pageViewPermissions = pageViewPermissions;
    }

    public String getPageEditPermissions() {
        return pageEditPermissions;
    }

    public void setPageEditPermissions(String pageEditPermissions) {
        this.pageEditPermissions = pageEditPermissions;
    }
}
