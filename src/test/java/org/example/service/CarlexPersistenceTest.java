package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.CarlexProcessConfirmation;
import org.example.repository.AppUserRepository;
import org.example.repository.CarlexProcessConfirmationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class CarlexPersistenceTest {
    @Autowired CarlexProcessConfirmationRepository repository;
    @Autowired TestEntityManager entityManager;

    @Test
    void pmOnlyRecordAndReassignmentSurviveReload() {
        var plants = mock(PlantMasterDataService.class);
        when(plants.names(PlantMasterDataService.DEPARTMENT)).thenReturn(List.of("Packaging"));
        when(plants.names(PlantMasterDataService.PROCESS_AREA)).thenReturn(List.of("Line 1"));
        var users = mock(AppUserRepository.class);
        var engineer = new org.example.entity.AppUser();
        engineer.setUsername("engineer");
        engineer.setDepartment("Packaging");
        engineer.setRole("ENGINEER");
        when(users.findAll()).thenReturn(List.of(engineer));
        var service = new CarlexProcessConfirmationService(repository, mock(AssignmentHistoryService.class),
                users, mock(EmailConfigService.class), plants, new ObjectMapper());
        var request = new CarlexProcessConfirmation();
        request.department = "Packaging";
        request.areaOfGwProcessConfirmationConducted = "Line 1";
        request.pmObservationsJson = "[{\"description\":\"PM issue\",\"counterMeasureActions\":\"Repair\",\"status\":\"P\"}]";
        request.zmObservationsJson = "[]";
        request.qmObservationsJson = "[]";
        Long id = service.create(request, "tester", "ADMIN").id;
        entityManager.flush();
        entityManager.clear();
        request.reassignedTo1 = "engineer";
        request.reassignment1Remark = "Please investigate";
        service.update(id, request, "tester", "ADMIN").orElseThrow();
        entityManager.flush();
        entityManager.clear();
        var saved = repository.findById(id).orElseThrow();
        assertEquals("Packaging", saved.department);
        assertEquals("engineer", saved.reassignedTo1);
        assertEquals("Please investigate", saved.reassignment1Remark);
        assertEquals(1, saved.observations.size());
        assertEquals("PM", saved.observations.get(0).getGroupType());
        request.reassignedTo2 = "engineer";
        request.reassignment2Remark = "Second stage follow-up";
        service.update(id, request, "tester", "ADMIN").orElseThrow();
        entityManager.flush();
        entityManager.clear();
        var reloaded = repository.findById(id).orElseThrow();
        assertEquals("engineer", reloaded.reassignedTo1);
        assertEquals("Please investigate", reloaded.reassignment1Remark);
        assertEquals("engineer", reloaded.reassignedTo2);
        assertEquals("Second stage follow-up", reloaded.reassignment2Remark);
    }

    @Test
    void createsAndUpdatesAllDynamicObservations() throws Exception {
        var plants = mock(PlantMasterDataService.class);
        when(plants.names(PlantMasterDataService.DEPARTMENT)).thenReturn(List.of("Packaging"));
        when(plants.names(PlantMasterDataService.PROCESS_AREA)).thenReturn(List.of("Line 1"));
        var mapper = new ObjectMapper();
        var service = new CarlexProcessConfirmationService(repository, mock(AssignmentHistoryService.class),
                mock(AppUserRepository.class), mock(EmailConfigService.class), plants, mapper);
        CarlexProcessConfirmation request = request("Original", "Third original");
        Long id = service.create(request, "tester", "ADMIN").id;
        entityManager.flush();
        entityManager.clear();
        assertEquals(3, repository.findById(id).orElseThrow().observations.size());

        service.update(id, request("Updated", "Third updated"), "tester", "ADMIN").orElseThrow();
        entityManager.flush();
        entityManager.clear();
        var saved = service.get(id).orElseThrow();
        var rows = mapper.readTree(saved.zmObservationsJson);
        assertEquals(3, rows.size());
        assertEquals("Updated", rows.get(0).get("description").asText());
        assertEquals("Third updated", rows.get(2).get("description").asText());
        assertEquals("image.png", rows.get(2).get("observationImage").asText());
        assertEquals("Repair", rows.get(2).get("counterMeasureActions").asText());
    }

    private CarlexProcessConfirmation request(String first, String third) {
        var record = new CarlexProcessConfirmation();
        record.department = "Packaging";
        record.areaOfGwProcessConfirmationConducted = "Line 1";
        record.zm1Description = first;
        record.zm1CounterMeasureActions = "Repair";
        record.zm1Status = "P";
        record.zmObservationsJson = "[{\"description\":\"" + first + "\",\"counterMeasureActions\":\"Repair\",\"status\":\"P\"},"
                + "{\"description\":\"Second\",\"counterMeasureActions\":\"Repair\",\"status\":\"D\"},"
                + "{\"description\":\"" + third + "\",\"counterMeasureActions\":\"Repair\",\"status\":\"C\",\"observationImage\":\"image.png\"}]";
        record.pmObservationsJson = "[]";
        record.qmObservationsJson = "[]";
        return record;
    }
}
