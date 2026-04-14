package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kpi_footer_buttons_config")
public class KpiFooterButtonsConfig {

    @Id
    private Long id;

    @Column(length = 120)
    private String button1Label;

    @Column(length = 1000)
    private String button1Url;

    @Column(length = 120)
    private String button2Label;

    @Column(length = 1000)
    private String button2Url;

    @Column(length = 20)
    private String button1Type; // "link" or "file"

    @Column(length = 1000)
    private String button1File; // stored UUID filename

    @Column(length = 500)
    private String button1FileName; // original display filename

    @Column(length = 20)
    private String button2Type; // "link" or "file"

    @Column(length = 1000)
    private String button2File; // stored UUID filename

    @Column(length = 500)
    private String button2FileName; // original display filename

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getButton1Label() {
        return button1Label;
    }

    public void setButton1Label(String button1Label) {
        this.button1Label = button1Label;
    }

    public String getButton1Url() {
        return button1Url;
    }

    public void setButton1Url(String button1Url) {
        this.button1Url = button1Url;
    }

    public String getButton2Label() {
        return button2Label;
    }

    public void setButton2Label(String button2Label) {
        this.button2Label = button2Label;
    }

    public String getButton2Url() {
        return button2Url;
    }

    public void setButton2Url(String button2Url) {
        this.button2Url = button2Url;
    }

    public String getButton1Type() {
        return button1Type;
    }

    public void setButton1Type(String button1Type) {
        this.button1Type = button1Type;
    }

    public String getButton1File() {
        return button1File;
    }

    public void setButton1File(String button1File) {
        this.button1File = button1File;
    }

    public String getButton1FileName() {
        return button1FileName;
    }

    public void setButton1FileName(String button1FileName) {
        this.button1FileName = button1FileName;
    }

    public String getButton2Type() {
        return button2Type;
    }

    public void setButton2Type(String button2Type) {
        this.button2Type = button2Type;
    }

    public String getButton2File() {
        return button2File;
    }

    public void setButton2File(String button2File) {
        this.button2File = button2File;
    }

    public String getButton2FileName() {
        return button2FileName;
    }

    public void setButton2FileName(String button2FileName) {
        this.button2FileName = button2FileName;
    }
}
