package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "meeting_agenda_configs")
@SecondaryTables({
    @SecondaryTable(name = "meeting_agenda_config_details"),
    @SecondaryTable(name = "meeting_agenda_config_agenda_points")
})
public class MeetingAgendaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate configDate;

    @Column(length = 40)
    private String periodLabel;

    @Column(length = 255)
    private String headerTitle;

    @Column(length = 120)
    private String frequency;

    @Column(length = 255)
    private String meetingTime;

    @Column(length = 255)
    private String meetingPlace;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String purposeDaily;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String purposeWeekly;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String participants;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String rolesResponsibilities;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String inputsDaily;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String inputsFriday;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String outputsDaily;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String outputsFriday;

    @Lob
    @Column(table = "meeting_agenda_config_details", columnDefinition = "TEXT")
    private String groundRules;

    @Column(length = 160)
    private String agenda1Title;

    @Lob
    @Column(table = "meeting_agenda_config_agenda_points", columnDefinition = "TEXT")
    private String agenda1Points;

    @Column(length = 80)
    private String agenda1Time;

    @Column(length = 160)
    private String agenda2Title;

    @Lob
    @Column(table = "meeting_agenda_config_agenda_points", columnDefinition = "TEXT")
    private String agenda2Points;

    @Column(length = 80)
    private String agenda2Time;

    @Column(length = 160)
    private String agenda3Title;

    @Lob
    @Column(table = "meeting_agenda_config_agenda_points", columnDefinition = "TEXT")
    private String agenda3Points;

    @Column(length = 80)
    private String agenda3Time;

    @Column(length = 160)
    private String agenda4Title;

    @Lob
    @Column(table = "meeting_agenda_config_agenda_points", columnDefinition = "TEXT")
    private String agenda4Points;

    @Column(length = 80)
    private String agenda4Time;

    @Column(length = 160)
    private String agenda5Title;

    @Lob
    @Column(table = "meeting_agenda_config_agenda_points", columnDefinition = "TEXT")
    private String agenda5Points;

    @Column(length = 80)
    private String agenda5Time;

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

    public String getHeaderTitle() { return headerTitle; }
    public void setHeaderTitle(String headerTitle) { this.headerTitle = headerTitle; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getMeetingTime() { return meetingTime; }
    public void setMeetingTime(String meetingTime) { this.meetingTime = meetingTime; }

    public String getMeetingPlace() { return meetingPlace; }
    public void setMeetingPlace(String meetingPlace) { this.meetingPlace = meetingPlace; }

    public String getPurposeDaily() { return purposeDaily; }
    public void setPurposeDaily(String purposeDaily) { this.purposeDaily = purposeDaily; }

    public String getPurposeWeekly() { return purposeWeekly; }
    public void setPurposeWeekly(String purposeWeekly) { this.purposeWeekly = purposeWeekly; }

    public String getParticipants() { return participants; }
    public void setParticipants(String participants) { this.participants = participants; }

    public String getRolesResponsibilities() { return rolesResponsibilities; }
    public void setRolesResponsibilities(String rolesResponsibilities) { this.rolesResponsibilities = rolesResponsibilities; }

    public String getInputsDaily() { return inputsDaily; }
    public void setInputsDaily(String inputsDaily) { this.inputsDaily = inputsDaily; }

    public String getInputsFriday() { return inputsFriday; }
    public void setInputsFriday(String inputsFriday) { this.inputsFriday = inputsFriday; }

    public String getOutputsDaily() { return outputsDaily; }
    public void setOutputsDaily(String outputsDaily) { this.outputsDaily = outputsDaily; }

    public String getOutputsFriday() { return outputsFriday; }
    public void setOutputsFriday(String outputsFriday) { this.outputsFriday = outputsFriday; }

    public String getGroundRules() { return groundRules; }
    public void setGroundRules(String groundRules) { this.groundRules = groundRules; }

    public String getAgenda1Title() { return agenda1Title; }
    public void setAgenda1Title(String agenda1Title) { this.agenda1Title = agenda1Title; }

    public String getAgenda1Points() { return agenda1Points; }
    public void setAgenda1Points(String agenda1Points) { this.agenda1Points = agenda1Points; }

    public String getAgenda1Time() { return agenda1Time; }
    public void setAgenda1Time(String agenda1Time) { this.agenda1Time = agenda1Time; }

    public String getAgenda2Title() { return agenda2Title; }
    public void setAgenda2Title(String agenda2Title) { this.agenda2Title = agenda2Title; }

    public String getAgenda2Points() { return agenda2Points; }
    public void setAgenda2Points(String agenda2Points) { this.agenda2Points = agenda2Points; }

    public String getAgenda2Time() { return agenda2Time; }
    public void setAgenda2Time(String agenda2Time) { this.agenda2Time = agenda2Time; }

    public String getAgenda3Title() { return agenda3Title; }
    public void setAgenda3Title(String agenda3Title) { this.agenda3Title = agenda3Title; }

    public String getAgenda3Points() { return agenda3Points; }
    public void setAgenda3Points(String agenda3Points) { this.agenda3Points = agenda3Points; }

    public String getAgenda3Time() { return agenda3Time; }
    public void setAgenda3Time(String agenda3Time) { this.agenda3Time = agenda3Time; }

    public String getAgenda4Title() { return agenda4Title; }
    public void setAgenda4Title(String agenda4Title) { this.agenda4Title = agenda4Title; }

    public String getAgenda4Points() { return agenda4Points; }
    public void setAgenda4Points(String agenda4Points) { this.agenda4Points = agenda4Points; }

    public String getAgenda4Time() { return agenda4Time; }
    public void setAgenda4Time(String agenda4Time) { this.agenda4Time = agenda4Time; }

    public String getAgenda5Title() { return agenda5Title; }
    public void setAgenda5Title(String agenda5Title) { this.agenda5Title = agenda5Title; }

    public String getAgenda5Points() { return agenda5Points; }
    public void setAgenda5Points(String agenda5Points) { this.agenda5Points = agenda5Points; }

    public String getAgenda5Time() { return agenda5Time; }
    public void setAgenda5Time(String agenda5Time) { this.agenda5Time = agenda5Time; }
}
