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

    @Column(length = 160)
    private String name;

    @Column(name = "employee_id", unique = true, length = 80)
    private String employeeId;

    @Column(length = 120)
    private String department;

    @Column(length = 120)
    private String area;

    @Column(length = 120)
    private String plant;

    @Column(length = 120)
    private String designation;

    @Column(name = "reporting_manager", length = 160)
    private String reportingManager;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 20)
    private String status = "ACTIVE";

    @Column(name = "page_view_permissions", columnDefinition = "TEXT")
    private String pageViewPermissions;

    @Column(name = "page_edit_permissions", columnDefinition = "TEXT")
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getPlant() {
        return plant;
    }

    public void setPlant(String plant) {
        this.plant = plant;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getReportingManager() {
        return reportingManager;
    }

    public void setReportingManager(String reportingManager) {
        this.reportingManager = reportingManager;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
