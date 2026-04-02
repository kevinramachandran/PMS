package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "process_confirmation_configs")
public class ProcessConfirmationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate configDate;

    @Column(length = 40)
    private String periodLabel;

    @Column(length = 180)
    private String kpiTitle;

    @Column(length = 80)
    private String targetLabel;

    @Column(length = 140)
    private String responsible;

    @Column(length = 40)
    private String monthLabel;

    private Integer janScore;
    private Integer febScore;
    private Integer marScore;
    private Integer aprScore;
    private Integer mayScore;
    private Integer junScore;
    private Integer julScore;
    private Integer augScore;
    private Integer sepScore;
    private Integer octScore;
    private Integer novScore;
    private Integer decScore;
    private Integer ytdScore;

    @Column(length = 500)
    private String question1;

    @Column(length = 500)
    private String question2;

    @Column(length = 500)
    private String question3;

    @Column(length = 500)
    private String question4;

    @Column(length = 500)
    private String question5;

    @Column(length = 500)
    private String question6;

    @Column(length = 500)
    private String question7;

    @Column(length = 500)
    private String question8;

    @Column(length = 500)
    private String question9;

    @Column(length = 500)
    private String question10;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q1Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q2Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q3Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q4Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q5Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q6Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q7Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q8Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q9Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String q10Statuses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String totalStatuses;

    @PrePersist
    public void prePersist() {
        if (configDate == null) {
            configDate = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getConfigDate() { return configDate; }
    public void setConfigDate(LocalDate configDate) { this.configDate = configDate; }

    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }

    public String getKpiTitle() { return kpiTitle; }
    public void setKpiTitle(String kpiTitle) { this.kpiTitle = kpiTitle; }

    public String getTargetLabel() { return targetLabel; }
    public void setTargetLabel(String targetLabel) { this.targetLabel = targetLabel; }

    public String getResponsible() { return responsible; }
    public void setResponsible(String responsible) { this.responsible = responsible; }

    public String getMonthLabel() { return monthLabel; }
    public void setMonthLabel(String monthLabel) { this.monthLabel = monthLabel; }

    public Integer getJanScore() { return janScore; }
    public void setJanScore(Integer janScore) { this.janScore = janScore; }

    public Integer getFebScore() { return febScore; }
    public void setFebScore(Integer febScore) { this.febScore = febScore; }

    public Integer getMarScore() { return marScore; }
    public void setMarScore(Integer marScore) { this.marScore = marScore; }

    public Integer getAprScore() { return aprScore; }
    public void setAprScore(Integer aprScore) { this.aprScore = aprScore; }

    public Integer getMayScore() { return mayScore; }
    public void setMayScore(Integer mayScore) { this.mayScore = mayScore; }

    public Integer getJunScore() { return junScore; }
    public void setJunScore(Integer junScore) { this.junScore = junScore; }

    public Integer getJulScore() { return julScore; }
    public void setJulScore(Integer julScore) { this.julScore = julScore; }

    public Integer getAugScore() { return augScore; }
    public void setAugScore(Integer augScore) { this.augScore = augScore; }

    public Integer getSepScore() { return sepScore; }
    public void setSepScore(Integer sepScore) { this.sepScore = sepScore; }

    public Integer getOctScore() { return octScore; }
    public void setOctScore(Integer octScore) { this.octScore = octScore; }

    public Integer getNovScore() { return novScore; }
    public void setNovScore(Integer novScore) { this.novScore = novScore; }

    public Integer getDecScore() { return decScore; }
    public void setDecScore(Integer decScore) { this.decScore = decScore; }

    public Integer getYtdScore() { return ytdScore; }
    public void setYtdScore(Integer ytdScore) { this.ytdScore = ytdScore; }

    public String getQuestion1() { return question1; }
    public void setQuestion1(String question1) { this.question1 = question1; }

    public String getQuestion2() { return question2; }
    public void setQuestion2(String question2) { this.question2 = question2; }

    public String getQuestion3() { return question3; }
    public void setQuestion3(String question3) { this.question3 = question3; }

    public String getQuestion4() { return question4; }
    public void setQuestion4(String question4) { this.question4 = question4; }

    public String getQuestion5() { return question5; }
    public void setQuestion5(String question5) { this.question5 = question5; }

    public String getQuestion6() { return question6; }
    public void setQuestion6(String question6) { this.question6 = question6; }

    public String getQuestion7() { return question7; }
    public void setQuestion7(String question7) { this.question7 = question7; }

    public String getQuestion8() { return question8; }
    public void setQuestion8(String question8) { this.question8 = question8; }

    public String getQuestion9() { return question9; }
    public void setQuestion9(String question9) { this.question9 = question9; }

    public String getQuestion10() { return question10; }
    public void setQuestion10(String question10) { this.question10 = question10; }

    public String getQ1Statuses() { return q1Statuses; }
    public void setQ1Statuses(String q1Statuses) { this.q1Statuses = q1Statuses; }

    public String getQ2Statuses() { return q2Statuses; }
    public void setQ2Statuses(String q2Statuses) { this.q2Statuses = q2Statuses; }

    public String getQ3Statuses() { return q3Statuses; }
    public void setQ3Statuses(String q3Statuses) { this.q3Statuses = q3Statuses; }

    public String getQ4Statuses() { return q4Statuses; }
    public void setQ4Statuses(String q4Statuses) { this.q4Statuses = q4Statuses; }

    public String getQ5Statuses() { return q5Statuses; }
    public void setQ5Statuses(String q5Statuses) { this.q5Statuses = q5Statuses; }

    public String getQ6Statuses() { return q6Statuses; }
    public void setQ6Statuses(String q6Statuses) { this.q6Statuses = q6Statuses; }

    public String getQ7Statuses() { return q7Statuses; }
    public void setQ7Statuses(String q7Statuses) { this.q7Statuses = q7Statuses; }

    public String getQ8Statuses() { return q8Statuses; }
    public void setQ8Statuses(String q8Statuses) { this.q8Statuses = q8Statuses; }

    public String getQ9Statuses() { return q9Statuses; }
    public void setQ9Statuses(String q9Statuses) { this.q9Statuses = q9Statuses; }

    public String getQ10Statuses() { return q10Statuses; }
    public void setQ10Statuses(String q10Statuses) { this.q10Statuses = q10Statuses; }

    public String getTotalStatuses() { return totalStatuses; }
    public void setTotalStatuses(String totalStatuses) { this.totalStatuses = totalStatuses; }
}
