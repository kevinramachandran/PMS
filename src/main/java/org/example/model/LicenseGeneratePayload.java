package org.example.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public class LicenseGeneratePayload {

    private String vendorName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateTo;

    private Integer userCount;
    private String licenseText;

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public Integer getUserCount() {
        return userCount;
    }

    public void setUserCount(Integer userCount) {
        this.userCount = userCount;
    }

    public String getLicenseText() {
        return licenseText;
    }

    public void setLicenseText(String licenseText) {
        this.licenseText = licenseText;
    }
}
