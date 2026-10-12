package org.example.service;

import org.example.entity.AbnormalityReportingRecord;
import org.example.entity.AppUser;
import org.example.repository.AbnormalityReportingRecordRepository;
import org.example.repository.AppUserRepository;
import org.example.repository.AssignmentHistoryRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbnormalitySaveRegressionTest {
    @Test void standardUserGetsAreaHodAssigneeForSelectedDepartment() {
        var repository = mock(AbnormalityReportingRecordRepository.class);
        var masters = mock(AbnormalityMasterDataService.class);
        var plants = mock(PlantMasterDataService.class);
        var users = mock(AppUserRepository.class);
        var email = mock(EmailConfigService.class);

        AppUser actor = new AppUser();
        actor.setUsername("reporter"); actor.setRole("USER"); actor.setStatus("ACTIVE");
        actor.setDepartment("Dep1"); actor.setArea("Proc1");
        AppUser areaHod = new AppUser();
        areaHod.setUsername("area.hod"); areaHod.setName("Area HoD"); areaHod.setRole("USER");
        areaHod.setStatus("ACTIVE"); areaHod.setDepartment("Dep1"); areaHod.setArea("Proc1");
        areaHod.setDesignation("AREA HOD");
        when(users.findAll()).thenReturn(List.of(actor, areaHod));
        when(users.findByUsernameIgnoreCase("reporter")).thenReturn(Optional.of(actor));
        var service = new AbnormalityReportingConfigService(repository, masters, plants, users, email,
                new AssignmentHistoryService(mock(AssignmentHistoryRepository.class)));

        var options = service.departmentOptions("reporter", "USER", "Dep1", "Proc1", null);

        @SuppressWarnings("unchecked")
        var assignable = (List<java.util.Map<String, String>>) options.get("assignableUsers");
        assertEquals(1, assignable.size());
        assertEquals("area.hod", assignable.get(0).get("username"));
    }

    @Test void recognizesCommonHodDesignationVariantsForAssignment() {
        for (String designation : List.of("Are Hod", "Area Hod", "AREA_HOD", "Area Head", "AREAHEAD")) {
            var repository = mock(AbnormalityReportingRecordRepository.class);
            var users = mock(AppUserRepository.class);
            AppUser actor = new AppUser();
            actor.setUsername("reporter"); actor.setRole("USER"); actor.setStatus("ACTIVE");
            AppUser areaHod = new AppUser();
            areaHod.setUsername("hod"); areaHod.setRole("USER"); areaHod.setStatus("ACTIVE");
            areaHod.setDepartment("Dep1"); areaHod.setArea("Proc1"); areaHod.setDesignation(designation);
            when(users.findAll()).thenReturn(List.of(actor, areaHod));
            when(users.findByUsernameIgnoreCase("reporter")).thenReturn(Optional.of(actor));
            var service = new AbnormalityReportingConfigService(repository,
                    mock(AbnormalityMasterDataService.class), mock(PlantMasterDataService.class), users,
                    mock(EmailConfigService.class), new AssignmentHistoryService(mock(AssignmentHistoryRepository.class)));

            var options = service.departmentOptions("reporter", "USER", "Dep1", "Proc1", null);
            @SuppressWarnings("unchecked")
            var assignable = (List<java.util.Map<String, String>>) options.get("assignableUsers");
            assertEquals(1, assignable.size(), designation);
        }
    }

    @Test void createAndUpdateWithEmptyReassignmentSlotsDoNotThrow() {
        var repository = mock(AbnormalityReportingRecordRepository.class);
        var masters = mock(AbnormalityMasterDataService.class);
        var plants = mock(PlantMasterDataService.class);
        var users = mock(AppUserRepository.class);
        var email = mock(EmailConfigService.class);
        var assignee = new AppUser(); assignee.setUsername("priya"); assignee.setRole("USER");
        assignee.setDepartment("Dep1"); assignee.setArea("Proc1"); assignee.setStatus("ACTIVE");
        assignee.setDesignation("AREA HOD");
        when(users.findAll()).thenReturn(List.of(assignee));
        when(masters.names(AbnormalityMasterDataService.ABT_TAG_TYPE)).thenReturn(List.of("Tag 1"));
        when(masters.names(AbnormalityMasterDataService.ABNORMALITY_DEFECT_TYPE)).thenReturn(List.of("Change Request"));
        when(plants.names(PlantMasterDataService.DEPARTMENT)).thenReturn(List.of("Dep1"));
        when(plants.names(PlantMasterDataService.PROCESS_AREA)).thenReturn(List.of("Proc1"));
        when(repository.save(any())).thenAnswer(invocation -> {
            AbnormalityReportingRecord record = invocation.getArgument(0); record.setId(1L); return record;
        });
        var service = new AbnormalityReportingConfigService(repository, masters, plants, users, email,
                new AssignmentHistoryService(mock(AssignmentHistoryRepository.class)));
        var request = new AbnormalityReportingRecord();
        request.setDepartment("Dep1"); request.setAreaMachine("Proc1"); request.setTypeOfTag("Tag 1");
        request.setAbnormalityDefectType("Change Request"); request.setPriority("Medium"); request.setShift("A");
        request.setDateRaised(LocalDate.of(2026, 9, 10)); request.setComponent("Comp2");
        request.setDescription("Original"); request.setProposedAction("PAS"); request.setAssignTo("priya"); request.setTagStatus("Open");
        var saved = assertDoesNotThrow(() -> service.create(request, "kevin", "ADMIN"));
        when(repository.findById(1L)).thenReturn(Optional.of(saved));
        for (String blank : new String[]{null, "", "  "}) {
            request.setReassignedTo1(blank); request.setReassignedTo2(blank); request.setDescription("Desc Test");
            saved = assertDoesNotThrow(() -> service.update(1L, request, "kevin", "ADMIN").orElseThrow());
            assertEquals("Desc Test", saved.getDescription());
            assertNull(saved.getReassignedTo1());
        }
        // A copied legacy row may also have empty master fields.
        saved.setTypeOfTag(null); saved.setDepartment(null); saved.setAreaMachine(null); saved.setAbnormalityDefectType(null);
        assertDoesNotThrow(() -> service.update(1L, request, "kevin", "ADMIN"));
    }
}
