package org.example.service;

import org.example.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import static org.mockito.Mockito.*;

class ProductionMetricsMonthBoundaryTest {
    @Test
    void includesRowsForYesterdayTargetsAcrossMonthYearAndLeapBoundaries() {
        for (LocalDate first : new LocalDate[] {
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 1, 1),
                LocalDate.of(2024, 3, 1), LocalDate.of(2026, 3, 1) }) {
            var repository = mock(ProductionMetricsRepository.class);
            var service = new ProductionMetricsService(repository,
                    mock(ProductionMetricCustomDefinitionRepository.class),
                    mock(ProductionMetricCustomValueRepository.class),
                    mock(ProductionMetricSystemDefinitionRepository.class), mock(JdbcTemplate.class));
            service.getRecordsByMonth(first.getMonthValue(), first.getYear(), first);
            verify(repository).findByDateBetweenOrderByDateAsc(
                    first.minusDays(2).atStartOfDay(), first.atTime(23, 59, 59));
            clearInvocations(repository);
            service.getRecordsByMonth(first.getMonthValue(), first.getYear());
            verify(repository).findByDateBetweenOrderByDateAsc(
                    first.minusDays(2).atStartOfDay(), first.withDayOfMonth(first.lengthOfMonth()).atTime(23, 59, 59));
        }
    }
}
