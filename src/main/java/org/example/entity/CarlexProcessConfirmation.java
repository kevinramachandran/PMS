package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carlex_process_confirmations")
public class CarlexProcessConfirmation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public LocalTime startTime;
    public LocalTime completionTime;
    @Column(length = 160) public String email;
    @Column(length = 160) public String name;
    public LocalDateTime lastModifiedTime;
    @Column(length = 160) public String processConfirmationDoneBy;
    public LocalDate dateOfGwProcessConfirmationConducted;
    @Column(length = 80) public String gwPcWeek;
    @Column(length = 160) public String department;
    @Column(length = 160) public String areaOfGwProcessConfirmationConducted;
    @Column(length = 160) public String areaResponsibility;
    @Column(length = 160) public String assignedTo;
    @Transient public String assignmentRemark;

    @Column(name = "zm1_description", columnDefinition = "TEXT") public String zm1Description;
    @Column(name = "zm1_counter_measure_actions", columnDefinition = "TEXT") public String zm1CounterMeasureActions;
    @Column(name = "zm1_status", length = 40) public String zm1Status;
    @Column(name = "zm1_observation_image", length = 255) public String zm1ObservationImage;
    public Boolean anotherZmObservation;
    @Column(name = "zm2_description", columnDefinition = "TEXT") public String zm2Description;
    @Column(name = "zm2_counter_measure_actions", columnDefinition = "TEXT") public String zm2CounterMeasureActions;
    @Column(name = "zm2_status", length = 40) public String zm2Status;
    @Column(name = "zm2_observation_image", length = 255) public String zm2ObservationImage;

    @Column(name = "pm1_description", columnDefinition = "TEXT") public String pm1Description;
    @Column(name = "pm1_counter_measure_actions", columnDefinition = "TEXT") public String pm1CounterMeasureActions;
    @Column(name = "pm1_status", length = 40) public String pm1Status;
    @Column(name = "pm1_observation_image", length = 255) public String pm1ObservationImage;
    public Boolean anotherPmObservation;
    @Column(name = "pm2_description", columnDefinition = "TEXT") public String pm2Description;
    @Column(name = "pm2_counter_measure_actions", columnDefinition = "TEXT") public String pm2CounterMeasureActions;
    @Column(name = "pm2_status", length = 40) public String pm2Status;
    @Column(name = "pm2_observation_image", length = 255) public String pm2ObservationImage;

    @Column(name = "om1_description", columnDefinition = "TEXT") public String om1Description;
    @Column(name = "om1_counter_measure_actions", columnDefinition = "TEXT") public String om1CounterMeasureActions;
    @Column(name = "om1_status", length = 40) public String om1Status;
    @Column(name = "om1_observation_image", length = 255) public String om1ObservationImage;

    @Column(name = "qm1_description", columnDefinition = "TEXT") public String qm1Description;
    @Column(name = "qm1_counter_measure_actions", columnDefinition = "TEXT") public String qm1CounterMeasureActions;
    @Column(name = "qm1_status", length = 40) public String qm1Status;
    @Column(name = "qm1_observation_image", length = 255) public String qm1ObservationImage;
    public Boolean anotherQmObservation;
    @Column(name = "qm2_description", columnDefinition = "TEXT") public String qm2Description;
    @Column(name = "qm2_counter_measure_actions", columnDefinition = "TEXT") public String qm2CounterMeasureActions;
    @Column(name = "qm2_status", length = 40) public String qm2Status;
    @Column(name = "qm2_observation_image", length = 255) public String qm2ObservationImage;

    @Transient public String zmObservationsJson;
    @Transient public String pmObservationsJson;
    @Transient public String qmObservationsJson;

    @OneToMany(mappedBy = "confirmation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("groupType ASC, observationOrder ASC, id ASC")
    public List<CarlexProcessConfirmationObservation> observations = new ArrayList<>();
}
