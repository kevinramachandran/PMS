package org.example.service;

import org.example.entity.IssueBoardItem;
import org.example.repository.IssueBoardItemRepository;
import org.example.repository.IssueBoardItemHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationRegressionTest {
    @Test
    void savedOpenIssuesSendMailForEveryTargetDateEvenWhenUnchanged() {
        var users = mock(org.example.repository.AppUserRepository.class);
        var email = mock(EmailConfigService.class);
        var user = new org.example.entity.AppUser();
        user.setUsername("owner");
        user.setEmail("owner@example.test");
        when(users.findByUsernameIgnoreCase("owner")).thenReturn(java.util.Optional.of(user));
        var service = new IssueBoardNotificationService(mock(IssueBoardItemRepository.class), users, email, "UTC");
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        for (LocalDate date : new LocalDate[]{today.minusDays(3), today, today.plusDays(1), today.plusDays(30), null}) {
            var item = new IssueBoardItem();
            item.setResponsible("owner");
            item.setProblem("Issue");
            item.setTargetDate(date);
            service.sendAssignmentNotification(today, 1, null, item);
            service.sendAssignmentNotification(today, 1, item, item);
        }
        verify(email, times(10)).sendEmail(eq(List.of("owner@example.test")), eq("Issue Assigned: Issue"), anyString(), eq(true), eq(true));
    }

    @Test
    void dailyReminderIncludesIssuesDueToday() {
        var repository = mock(IssueBoardItemRepository.class);
        var users = mock(org.example.repository.AppUserRepository.class);
        var email = mock(EmailConfigService.class);
        var user = new org.example.entity.AppUser();
        user.setEmail("owner@example.test");
        when(users.findByUsernameIgnoreCase("owner")).thenReturn(java.util.Optional.of(user));
        var item = new IssueBoardItem();
        item.setResponsible("owner");
        item.setProblem("Issue");
        item.setTargetDate(LocalDate.now(java.time.ZoneOffset.UTC));
        when(repository.findAllOpenItemsWithTargetDate()).thenReturn(List.of(item));
        new IssueBoardNotificationService(repository, users, email, "UTC").sendPendingIssueReminders();
        verify(email).sendEmail(eq(List.of("owner@example.test")), eq("Reminder: Issue due today - Issue"), contains("is due today"), eq(true), eq(true));
    }

    @Test
    void unchangedAssigneesDoNotRequireMembershipInTheNextStageOptions() {
        var walk = new org.example.entity.GembaWalkRecord();
        walk.setId(1L);
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                new GembaWalkConfigService(null, null, null, null, null, null),
                "validateResponsibility", "hod", "Packaging", "Line 1", null, "hod", "ENGINEER", walk));
        var kaizen = new org.example.entity.GembaKaizenRecord();
        kaizen.setId(1L);
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                new GembaKaizenConfigService(null, null, null, null, null, null),
                "validateAssignedTo", "hod", "Packaging", "Line 1", null, "ENGINEER", "hod", kaizen));
        var carlex = new org.example.entity.CarlexProcessConfirmation();
        carlex.id = 1L;
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                new CarlexProcessConfirmationService(null, null, null, null, null, null),
                "validateAssignedTo", "hod", "Packaging", "Line 1", null, "ENGINEER", "hod", carlex));
        var abnormality = new org.example.entity.AbnormalityReportingRecord();
        abnormality.setId(1L);
        abnormality.setAssignTo("hod");
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                new AbnormalityReportingConfigService(null, null, null, null, null, null),
                "validateAssignee", "hod", "Packaging", "Line 1", null, "ENGINEER", abnormality));
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void blankUserAreasDoNotCrashAbnormalitySaving() {
        var service = new AbnormalityReportingConfigService(null, null, null, null, null, null);
        for (String area : new String[]{null, "", "   "}) {
            assertEquals(false, ReflectionTestUtils.invokeMethod(service, "matchesAnyArea", area, "PACKAGING"));
        }
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "matchesAnyArea", "Brewing, Packaging", "PACKAGING"));
    }

    @Test
    void dispatchWaitsForCommitAndSkipsRollback() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        CountDownLatch sent = new CountDownLatch(1);
        NotificationDispatch.afterCommit(sent::countDown);
        assertEquals(1, sent.getCount());
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertEquals(1, sent.getCount());
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        NotificationDispatch.afterCommit(sent::countDown);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertTrue(sent.await(5, TimeUnit.SECONDS));
    }

    @Test
    void bulkEditPreservesPreviousAssigneeAndExtendedTargetById() {
        var repository = mock(IssueBoardItemRepository.class);
        var history = mock(IssueBoardItemHistoryRepository.class);
        var notifications = mock(IssueBoardNotificationService.class);
        var service = new IssueBoardItemService(repository, history, notifications);
        LocalDate date = LocalDate.of(2026, 9, 14);
        IssueBoardItem existing = new IssueBoardItem();
        existing.setId(7L);
        existing.setRowOrder(1);
        existing.setResponsible("old-user");
        existing.setTargetDateExtension1(date.plusDays(2));
        IssueBoardItem incoming = new IssueBoardItem();
        incoming.setId(7L);
        incoming.setRowOrder(3);
        incoming.setResponsible("new-user");
        when(repository.findByBoardDateOrderByRowOrderAscIdAsc(date)).thenReturn(List.of(existing));
        when(repository.findByBoardDateOrderByUpdatedAtDescIdDesc(date)).thenReturn(List.of(existing));
        service.replaceByBoardDate(date, List.of(incoming), "editor");
        var before = ArgumentCaptor.forClass(IssueBoardItem.class);
        var after = ArgumentCaptor.forClass(IssueBoardItem.class);
        verify(notifications, timeout(5000)).sendAssignmentNotification(eq(date), eq(3), before.capture(), after.capture());
        assertEquals("old-user", before.getValue().getResponsible());
        assertEquals(date.plusDays(2), before.getValue().getTargetDateExtension1());
        assertEquals("new-user", after.getValue().getResponsible());
    }
}
