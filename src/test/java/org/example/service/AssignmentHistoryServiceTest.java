package org.example.service;

import org.example.entity.AssignmentHistory;
import org.example.repository.AssignmentHistoryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AssignmentHistoryServiceTest {

    @Test
    void allowsOnlyOneNewStagePerSave() {
        var service = new AssignmentHistoryService(mock(AssignmentHistoryRepository.class));
        assertDoesNotThrow(() -> service.validateTransition(true, "", "hod", "", "", "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> service.validateTransition(true, "", "hod", "", "engineer", "Reason", "", "", ""));
        assertDoesNotThrow(() -> service.validateTransition(false, "hod", "hod", "", "engineer", "Reason", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> service.validateTransition(false, "hod", "hod", "", "engineer", "Reason", "", "operator", "Reason"));
        assertDoesNotThrow(() -> service.validateTransition(false, "hod", "hod", "engineer", "engineer", "Reason", "", "operator", "Reason"));
        assertThrows(IllegalArgumentException.class, () -> service.validateTransition(false, "hod", "hod", "engineer", "other", "Reason", "", "", ""));
    }

    @Test
    void recordsEveryReassignmentWithoutChangingTheModuleRoleWorkflow() {
        AssignmentHistoryRepository repository = mock(AssignmentHistoryRepository.class);
        AssignmentHistoryService service = new AssignmentHistoryService(repository);

        assertDoesNotThrow(() -> service.record("gemba-walk", 41L, "", "area-hod", "", "creator", "Packaging"));
        assertDoesNotThrow(() -> service.record("gemba-walk", 41L, "area-hod", "engineer", "Line support", "area-hod", "Packaging"));
        assertDoesNotThrow(() -> service.record("gemba-walk", 41L, "engineer", "operator", "Shift handover", "engineer", "Packaging"));
        assertDoesNotThrow(() -> service.record("gemba-walk", 41L, "operator", "executive", "Follow-up", "operator", "Packaging"));

        verify(repository, times(4)).save(any(AssignmentHistory.class));
    }

    @Test
    void ignoresAnUnchangedAssigneeAndRequiresRemarksOnlyForAReassignment() {
        AssignmentHistoryRepository repository = mock(AssignmentHistoryRepository.class);
        AssignmentHistoryService service = new AssignmentHistoryService(repository);

        service.record("abnormality-reporting", 8L, "engineer", "engineer", "", "engineer", "Packaging");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.record("abnormality-reporting", 8L, "engineer", "operator", "", "engineer", "Packaging"));

        verify(repository, times(0)).save(any(AssignmentHistory.class));
        assertEquals("Remarks are required for reassignment", exception.getMessage());
    }
}
