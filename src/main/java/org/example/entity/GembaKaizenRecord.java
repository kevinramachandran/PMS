package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "gemba_kaizen_records")
public class GembaKaizenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 160)
    private String name;

    @Column(length = 20)
    private String lastModifiedTime;

    @Column(length = 160)
    private String gembaKaizenProviderName;

    @Column(length = 80)
    private String employeeIdHoNumber;

    @Column(length = 160)
    private String department;

    @Column(length = 160)
    private String classificationOfKaizen;

    @Column(length = 160)
    private String gembaKaizenLocation;

    private LocalDate gembaKaizenGenerationDate;

    @Column(length = 1000)
    private String kaizenIdea;

    @Column(length = 255)
    private String pictureImage;

    @Column(length = 1000)
    private String benefitsOfKaizen;

    @Column(length = 10)
    private String isKaizenImplemented;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(String lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public String getGembaKaizenProviderName() {
        return gembaKaizenProviderName;
    }

    public void setGembaKaizenProviderName(String gembaKaizenProviderName) {
        this.gembaKaizenProviderName = gembaKaizenProviderName;
    }

    public String getEmployeeIdHoNumber() {
        return employeeIdHoNumber;
    }

    public void setEmployeeIdHoNumber(String employeeIdHoNumber) {
        this.employeeIdHoNumber = employeeIdHoNumber;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getClassificationOfKaizen() {
        return classificationOfKaizen;
    }

    public void setClassificationOfKaizen(String classificationOfKaizen) {
        this.classificationOfKaizen = classificationOfKaizen;
    }

    public String getGembaKaizenLocation() {
        return gembaKaizenLocation;
    }

    public void setGembaKaizenLocation(String gembaKaizenLocation) {
        this.gembaKaizenLocation = gembaKaizenLocation;
    }

    public LocalDate getGembaKaizenGenerationDate() {
        return gembaKaizenGenerationDate;
    }

    public void setGembaKaizenGenerationDate(LocalDate gembaKaizenGenerationDate) {
        this.gembaKaizenGenerationDate = gembaKaizenGenerationDate;
    }

    public String getKaizenIdea() {
        return kaizenIdea;
    }

    public void setKaizenIdea(String kaizenIdea) {
        this.kaizenIdea = kaizenIdea;
    }

    public String getPictureImage() {
        return pictureImage;
    }

    public void setPictureImage(String pictureImage) {
        this.pictureImage = pictureImage;
    }

    public String getBenefitsOfKaizen() {
        return benefitsOfKaizen;
    }

    public void setBenefitsOfKaizen(String benefitsOfKaizen) {
        this.benefitsOfKaizen = benefitsOfKaizen;
    }

    public String getIsKaizenImplemented() {
        return isKaizenImplemented;
    }

    public void setIsKaizenImplemented(String isKaizenImplemented) {
        this.isKaizenImplemented = isKaizenImplemented;
    }
}
