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
}
