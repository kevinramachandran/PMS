package org.example.service;

import org.example.entity.*;
import org.example.repository.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GembaWalkSyncRegressionTest {
    @Test void deletionRequiresEditEligibilityAndCreateCannotOverwriteAnId() {
        var repository = mock(GembaWalkRecordRepository.class);
        var users = mock(AppUserRepository.class);
        var viewer = new AppUser(); viewer.setUsername("viewer"); viewer.setName("Viewer");
        when(users.findByUsernameIgnoreCase("viewer")).thenReturn(Optional.of(viewer));
        var record = new GembaWalkRecord(); record.setId(8L); record.setManagerName("Viewer"); record.setCreatedBy("another-user");
        when(repository.findById(8L)).thenReturn(Optional.of(record));
        var service = new GembaWalkConfigService(repository, mock(GembaWalkMasterDataService.class), mock(PlantMasterDataService.class),
                users, mock(EmailConfigService.class), mock(AssignmentHistoryService.class));
        assertThrows(IllegalArgumentException.class, () -> service.delete(8L, "viewer", "USER"));
        verify(repository, never()).deleteById(anyLong());
        assertThrows(IllegalArgumentException.class, () -> service.create(record, "viewer", "ADMIN"));
        verify(repository, never()).save(any());
        assertTrue(service.delete(8L, "admin", "ADMIN")); verify(repository).deleteById(8L);
    }
    @Test void csvRoundTripRetainsSavedLocationMissingFromCurrentMasters() throws Exception {
        var repository = mock(GembaWalkRecordRepository.class);
        var plants = mock(PlantMasterDataService.class);
        when(plants.names(PlantMasterDataService.PROCESS_AREA)).thenReturn(List.of("P1"));
        var record = new GembaWalkRecord(); record.setId(9L); record.setLocationOfMswConducted("Area2");
        record.setDepartment("Original department"); record.setFinalComments("Original comment");
        when(repository.findById(9L)).thenReturn(Optional.of(record));
        when(repository.findAll()).thenReturn(List.of(record));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var walk = new GembaWalkConfigService(repository, mock(GembaWalkMasterDataService.class), plants,
                mock(AppUserRepository.class), mock(EmailConfigService.class), mock(AssignmentHistoryService.class));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var sync = new DataSyncService(mapper, mock(GembaKaizenConfigService.class), mock(AbnormalityReportingConfigService.class),
                walk, mock(CarlexProcessConfirmationService.class), plants, mock(GembaKaizenMasterDataService.class),
                mock(AbnormalityMasterDataService.class), mock(GembaWalkMasterDataService.class), mock(ProcessMasterDataService.class), mock(AuthService.class), new MasterReferenceService(mock(jakarta.persistence.EntityManager.class, RETURNS_DEEP_STUBS), mapper));
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("username", "admin"); session.setAttribute("role", "ADMIN");
        var csv = sync.exportCsv("gemba-walk", "", session);
        assertEquals(1, sync.importCsv("gemba-walk", "", csv, session).get("unchanged"));
        assertEquals("Area2", record.getLocationOfMswConducted());
        assertEquals("Original comment", record.getFinalComments());
        sync.importCsv("gemba-walk", "", "id,finalComments\n9,Updated comment", session);
        assertEquals("Updated comment", record.getFinalComments());
        assertEquals("Area2", record.getLocationOfMswConducted());
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("gemba-walk", "", "id,locationOfMswConducted\n9,Other", session));
    }

    @Test void newWalkStillRequiresConfiguredLocation() {
        var repository = mock(GembaWalkRecordRepository.class);
        var plants = mock(PlantMasterDataService.class);
        when(plants.names(PlantMasterDataService.PROCESS_AREA)).thenReturn(List.of("P1"));
        var service = new GembaWalkConfigService(repository, mock(GembaWalkMasterDataService.class), plants,
                mock(AppUserRepository.class), mock(EmailConfigService.class), mock(AssignmentHistoryService.class));
        var request = new GembaWalkRecord(); request.setLocationOfMswConducted("Area2");
        var error = assertThrows(IllegalArgumentException.class, () -> service.create(request, "admin", "ADMIN"));
        assertTrue(error.getMessage().contains("Location of MSW Conducted"));
        verify(repository, never()).save(any());
    }

    @Test void createRetainsObservationsAndUpdateRetainsCreator() {
        var repository = mock(GembaWalkRecordRepository.class);
        var users = mock(AppUserRepository.class);
        var actor = new AppUser(); actor.setUsername("editor"); actor.setName("Editor"); actor.setEmail("editor@example.test");
        when(users.findByUsernameIgnoreCase("editor")).thenReturn(Optional.of(actor));
        when(repository.save(any())).thenAnswer(call -> { GembaWalkRecord value = call.getArgument(0); value.setId(1L); return value; });
        var service = new GembaWalkConfigService(repository, mock(GembaWalkMasterDataService.class),
                mock(PlantMasterDataService.class), users, mock(EmailConfigService.class), mock(AssignmentHistoryService.class));
        var record = new GembaWalkRecord();
        var observation = new GembaWalkObservation(); observation.setObservationDescription("Keep this observation"); observation.setStatus("Open");
        record.setObservations(new ArrayList<>(List.of(observation)));
        var saved = service.create(record, "editor", "ADMIN");
        assertEquals(1, saved.getObservations().size());
        assertEquals("Keep this observation", saved.getObservations().get(0).getObservationDescription());
        assertSame(saved, saved.getObservations().get(0).getRecord());
        saved.setCreatedBy("original"); saved.setManagerName("Original"); saved.setEmail("original@example.test");
        when(repository.findById(1L)).thenReturn(Optional.of(saved));
        var updated = service.update(1L, saved, "editor", "ADMIN").orElseThrow();
        assertEquals("original", updated.getCreatedBy());
        assertEquals("Original", updated.getManagerName());
        assertEquals("original@example.test", updated.getEmail());
    }
}
