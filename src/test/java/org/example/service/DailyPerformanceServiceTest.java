package org.example.service;

import org.example.entity.*;
import org.example.repository.DailyPerformanceRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class DailyPerformanceServiceTest {
    @Test void storesCustomFieldsAndSupportsRemovingAllFields() {
        var repository = mock(DailyPerformanceRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new DailyPerformanceService(repository);
        var input = new DailyPerformance();
        input.setFields(List.of(new DailyPerformanceField("Output", "HL", "12.50"),
                new DailyPerformanceField("Status", "", "No production")));
        var saved = service.saveOrUpdateToday(input);
        assertEquals(input.getFields(), saved.getFields());
        assertEquals(LocalDate.now(), saved.getDate());
        assertNotNull(saved.getMonthTarget());
        when(repository.findByDate(LocalDate.now())).thenReturn(Optional.of(saved));
        input.setFields(List.of());
        assertTrue(service.saveOrUpdateToday(input).getFields().isEmpty());
    }

    @Test void legacySavesPreserveConfiguredFieldsAndExistingNumericValues() {
        var repository = mock(DailyPerformanceRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new DailyPerformanceService(repository);
        var legacy = new DailyPerformance();
        legacy.setMonthTarget(100.0); legacy.setActualMtd(20.0);
        legacy.setDailyTarget(10.0); legacy.setYesterday(9.0);
        var saved = service.saveOrUpdateToday(legacy);
        assertNull(saved.getFields());
        when(repository.findByDate(LocalDate.now())).thenReturn(Optional.of(saved));
        var custom = new DailyPerformance();
        custom.setFields(List.of(new DailyPerformanceField("Output", "HL", "0")));
        service.saveOrUpdateToday(custom);
        assertEquals(100.0, saved.getMonthTarget());
        service.saveOrUpdateToday(legacy);
        assertEquals(custom.getFields(), saved.getFields());
    }

    @Test void rejectsInvalidFieldsBeforeWriting() {
        var repository = mock(DailyPerformanceRepository.class);
        var service = new DailyPerformanceService(repository);
        for (var field : List.of(new DailyPerformanceField(" ", "", "1"),
                new DailyPerformanceField("Output", "", " "),
                new DailyPerformanceField("x".repeat(121), "", "1"))) {
            var input = new DailyPerformance(); input.setFields(List.of(field));
            assertThrows(IllegalArgumentException.class, () -> service.saveOrUpdateToday(input));
        }
        var input = new DailyPerformance();
        input.setFields(Collections.nCopies(31, new DailyPerformanceField("Output", "", "1")));
        assertThrows(IllegalArgumentException.class, () -> service.saveOrUpdateToday(input));
        verifyNoInteractions(repository);
    }

    @Test void converterPreservesTextPrecisionAndDistinguishesLegacyFromEmpty() {
        var converter = new DailyPerformanceFieldsConverter();
        var fields = List.of(new DailyPerformanceField("Output <test>", "HL", "12.50"),
                new DailyPerformanceField("Status", "", "Ready & waiting"));
        assertEquals(fields, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(fields)));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals(List.of(), converter.convertToEntityAttribute("[]"));
    }
}
