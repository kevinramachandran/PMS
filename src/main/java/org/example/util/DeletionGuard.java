package org.example.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

public final class DeletionGuard {
    private DeletionGuard() {}

    public static void checkRemoval(boolean removesExisting) {
        if (removesExisting && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && !Boolean.TRUE.equals(attributes.getRequest().getAttribute("canDeleteRecords"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
