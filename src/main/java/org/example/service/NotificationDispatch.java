package org.example.service;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/** Sends notifications only for committed changes, without holding up the save request. */
final class NotificationDispatch {
    private NotificationDispatch() {}

    static void afterCommit(Runnable notification) {
        Runnable dispatch = () -> CompletableFuture.runAsync(() -> {
            try {
                notification.run();
            } catch (Exception ex) {
                LoggerFactory.getLogger(NotificationDispatch.class)
                        .error("Notification failed; the saved record is unaffected", ex);
            }
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }
}
