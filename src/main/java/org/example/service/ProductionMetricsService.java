package org.example.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.example.entity.ProductionMetricCustomDefinition;
import org.example.entity.ProductionMetricCustomValue;
import org.example.entity.ProductionMetrics;
import org.example.model.CustomMetricDefinitionPayload;
import org.example.model.CustomMetricValueSnapshot;
import org.example.model.MetricsEntryBundlePayload;
import org.example.model.MetricsEntryPayload;
import org.example.repository.ProductionMetricCustomDefinitionRepository;
import org.example.repository.ProductionMetricCustomValueRepository;
import org.example.repository.ProductionMetricsRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionMetricsService {

    private final ProductionMetricsRepository repository;
    private final ProductionMetricCustomDefinitionRepository customDefinitionRepository;
    private final ProductionMetricCustomValueRepository customValueRepository;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ENTRY_TYPE_ACTUAL = "ACTUAL";
    private static final String ENTRY_TYPE_TARGET = "TARGET";
    private static final Set<String> SUPPORTED_SECTIONS = Set.of("PEOPLE", "QUALITY", "SERVICE", "COST");
    private static final Pattern CUSTOM_FIELD_PATTERN = Pattern.compile("^customMetric(\\d+)(FtdActual|FtdTarget|MtdActual|MtdTarget|YtdActual|YtdTarget)$");
    private static final List<String> PEOPLE_FIELDS = List.of(
        "productionProductivityFtdActual", "productionProductivityFtdTarget", "productionProductivityMtdActual", "productionProductivityMtdTarget", "productionProductivityYtdActual", "productionProductivityYtdTarget",
        "logisticsProductivityFtdActual", "logisticsProductivityFtdTarget", "logisticsProductivityMtdActual", "logisticsProductivityMtdTarget", "logisticsProductivityYtdActual", "logisticsProductivityYtdTarget"
    );
    private static final List<String> QUALITY_FIELDS = List.of(
        "kpiSensoryScoreFtdActual", "kpiSensoryScoreFtdTarget", "kpiSensoryScoreMtdActual", "kpiSensoryScoreMtdTarget", "kpiSensoryScoreYtdActual", "kpiSensoryScoreYtdTarget",
        "kpiConsumerComplaintUnitsMhlFtdActual", "kpiConsumerComplaintUnitsMhlFtdTarget", "kpiConsumerComplaintUnitsMhlMtdActual", "kpiConsumerComplaintUnitsMhlMtdTarget", "kpiConsumerComplaintUnitsMhlYtdActual", "kpiConsumerComplaintUnitsMhlYtdTarget",
        "kpiCustomerComplaintUnitsMhlFtdActual", "kpiCustomerComplaintUnitsMhlFtdTarget", "kpiCustomerComplaintUnitsMhlMtdActual", "kpiCustomerComplaintUnitsMhlMtdTarget", "kpiCustomerComplaintUnitsMhlYtdActual", "kpiCustomerComplaintUnitsMhlYtdTarget"
    );
    private static final List<String> SERVICE_FIELDS = List.of(
        "noOfBrewsFtdActual", "noOfBrewsFtdTarget", "noOfBrewsMtdActual", "noOfBrewsMtdTarget", "noOfBrewsYtdActual", "noOfBrewsYtdTarget",
        "dispatchFtdActual", "dispatchFtdTarget", "dispatchMtdActual", "dispatchMtdTarget", "dispatchYtdActual", "dispatchYtdTarget",
        "processConfirmationBpFtdActual", "processConfirmationBpFtdTarget", "processConfirmationBpMtdActual", "processConfirmationBpMtdTarget", "processConfirmationBpYtdActual", "processConfirmationBpYtdTarget",
        "processConfirmationPackFtdActual", "processConfirmationPackFtdTarget", "processConfirmationPackMtdActual", "processConfirmationPackMtdTarget", "processConfirmationPackYtdActual", "processConfirmationPackYtdTarget",
        "kpiOeeFtdActual", "kpiOeeFtdTarget", "kpiOeeMtdActual", "kpiOeeMtdTarget", "kpiOeeYtdActual", "kpiOeeYtdTarget",
        "kpiBeerLossFtdActual", "kpiBeerLossFtdTarget", "kpiBeerLossMtdActual", "kpiBeerLossMtdTarget", "kpiBeerLossYtdActual", "kpiBeerLossYtdTarget",
        "kpiWurHlHlFtdActual", "kpiWurHlHlFtdTarget", "kpiWurHlHlMtdActual", "kpiWurHlHlMtdTarget", "kpiWurHlHlYtdActual", "kpiWurHlHlYtdTarget"
    );
    private static final List<String> COST_FIELDS = List.of(
        "kpiElectricityKwhHlFtdActual", "kpiElectricityKwhHlFtdTarget", "kpiElectricityKwhHlMtdActual", "kpiElectricityKwhHlMtdTarget", "kpiElectricityKwhHlYtdActual", "kpiElectricityKwhHlYtdTarget",
        "kpiEnergyKwhHlFtdActual", "kpiEnergyKwhHlFtdTarget", "kpiEnergyKwhHlMtdActual", "kpiEnergyKwhHlMtdTarget", "kpiEnergyKwhHlYtdActual", "kpiEnergyKwhHlYtdTarget",
        "kpiRgbRatioFtdActual", "kpiRgbRatioFtdTarget", "kpiRgbRatioMtdActual", "kpiRgbRatioMtdTarget", "kpiRgbRatioYtdActual", "kpiRgbRatioYtdTarget"
    );
    private static final List<String> ALL_METRIC_FIELDS = buildAllMetricFields();

    private static List<String> buildAllMetricFields() {
        List<String> all = new ArrayList<>();
        all.addAll(PEOPLE_FIELDS);
        all.addAll(QUALITY_FIELDS);
        all.addAll(SERVICE_FIELDS);
        all.addAll(COST_FIELDS);
        return all;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void consolidateLegacySplitRows() {
        List<ProductionMetrics> allRecords = repository.findAll();
        if (allRecords.isEmpty()) {
            return;
        }

        List<ProductionMetrics> targetRowsToDelete = new ArrayList<>();
        for (ProductionMetrics record : allRecords) {
            if (record.getDate() == null || ENTRY_TYPE_TARGET.equalsIgnoreCase(record.getEntryType())) {
                continue;
            }

            LocalDate actualDate = record.getDate().toLocalDate();
            if (record.getTargetDate() == null) {
                record.setTargetDate(actualDate.plusDays(1).atStartOfDay());
            }

            findLegacyTargetRecord(record.getTargetDate().toLocalDate()).ifPresent(legacyTarget -> {
                copyTargetFields(record, legacyTarget);
                targetRowsToDelete.add(legacyTarget);
            });

            if (record.getEntryType() != null && !record.getEntryType().isBlank()) {
                record.setEntryType(null);
            }
            repository.save(record);
        }

        if (!targetRowsToDelete.isEmpty()) {
            repository.deleteAll(targetRowsToDelete);
        }
    }

    // CRUD Operations
    public List<ProductionMetrics> getAllRecords() {
        List<ProductionMetrics> records = repository.findAll();
        attachCustomMetricSnapshots(records);
        return records;
    }

    public Optional<ProductionMetrics> getRecordById(Long id) {
        return repository.findById(id);
    }

    public Optional<ProductionMetrics> getRecordByDate(LocalDateTime date) {
        return repository.findByDate(date).map(this::withCustomMetricSnapshots);
    }

    public Optional<ProductionMetrics> getRecordByDate(LocalDate date) {
        return findNormalizedRecordByActualDate(date).map(this::withCustomMetricSnapshots);
    }

    public Optional<ProductionMetrics> getRecordByDay(LocalDate date) {
        return findNormalizedRecordByActualDate(date).map(this::withCustomMetricSnapshots);
    }

    public List<ProductionMetrics> getRecordsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<ProductionMetrics> records = repository.findByDateBetween(startDate, endDate);
        attachCustomMetricSnapshots(records);
        return records;
    }

    public List<ProductionMetrics> getCurrentMonthData() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.withDayOfMonth(today.lengthOfMonth());

        List<ProductionMetrics> records = buildNormalizedRecordsBetween(firstDay, lastDay);
        attachCustomMetricSnapshots(records);
        return records;
    }

    public List<ProductionMetrics> getRecordsByMonth(int month, int year) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<ProductionMetrics> records = buildNormalizedRecordsBetween(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        attachCustomMetricSnapshots(records);
        return records;
    }

    public List<CustomMetricDefinitionPayload> getCustomMetricDefinitions() {
        return customDefinitionRepository.findByActiveTrueOrderBySectionAscDisplayOrderAscIdAsc()
                .stream()
                .map(this::toCustomMetricDefinitionPayload)
                .toList();
    }

    @Transactional
    public CustomMetricDefinitionPayload createCustomMetricDefinition(CustomMetricDefinitionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Custom metric definition is required");
        }

        String section = normalizeSection(payload.getSection());
        String label = normalizeLabel(payload.getLabel());
        String unit = normalizeUnit(payload.getUnit());
        Integer decimals = normalizeDecimals(payload.getDecimals());
        Integer displayOrder = payload.getDisplayOrder();
        if (displayOrder == null || displayOrder < 0) {
            displayOrder = (int) customDefinitionRepository.countBySectionAndActiveTrue(section);
        }

        ProductionMetricCustomDefinition definition = new ProductionMetricCustomDefinition();
        definition.setSection(section);
        definition.setLabel(label);
        definition.setUnit(unit);
        definition.setDecimals(decimals);
        definition.setDisplayOrder(displayOrder);
        definition.setActive(Boolean.TRUE);
        definition.setMetricKey(createUniqueMetricKey(payload.getMetricKey(), label));

        return toCustomMetricDefinitionPayload(customDefinitionRepository.save(definition));
    }

    public Optional<MetricsEntryPayload> getMetricsEntry(LocalDate date) {
        validateActualEntryDate(date);
        return findNormalizedRecordByActualDate(date).map(this::toMetricsEntryPayload);
    }

    @Transactional
    public MetricsEntryPayload saveMetricsEntry(MetricsEntryPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Metrics payload is required");
        }

        LocalDate date = payload.getDate();
        validateActualEntryDate(date);
        ProductionMetrics metrics = findPrimaryRecordByActualDate(date).orElseGet(ProductionMetrics::new);
        metrics.setDate(date.atStartOfDay());
        metrics.setTargetDate(date.plusDays(1).atStartOfDay());
        metrics.setEntryType(null);

        boolean updated = false;
        updated |= applySectionValuesIfPresent(metrics, payload.getPeople(), PEOPLE_FIELDS, "People");
        updated |= applySectionValuesIfPresent(metrics, payload.getQuality(), QUALITY_FIELDS, "Quality");
        updated |= applySectionValuesIfPresent(metrics, payload.getService(), SERVICE_FIELDS, "Service");
        updated |= applySectionValuesIfPresent(metrics, payload.getCost(), COST_FIELDS, "Cost");

        if (!updated) {
            throw new IllegalArgumentException("At least one metrics category is required");
        }

        return toMetricsEntryPayload(repository.save(metrics));
    }

    public Optional<MetricsEntryBundlePayload> getMetricsEntryBundle(LocalDate actualDate) {
        validateActualEntryDate(actualDate);
        return findNormalizedRecordByActualDate(actualDate).map(this::toMetricsEntryBundlePayload);
    }

    @Transactional
    public MetricsEntryBundlePayload saveMetricsEntryBundle(MetricsEntryBundlePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Metrics payload is required");
        }

        LocalDate actualDate = payload.getActualDate();
        validateActualEntryDate(actualDate);

        LocalDate targetDate = actualDate.plusDays(1);
        if (payload.getTargetDate() != null && !targetDate.equals(payload.getTargetDate())) {
            throw new IllegalArgumentException("Target date must be the next day of Actual date");
        }

        ProductionMetrics metrics = findPrimaryRecordByActualDate(actualDate).orElseGet(ProductionMetrics::new);
        metrics.setDate(actualDate.atStartOfDay());
        metrics.setTargetDate(targetDate.atStartOfDay());
        metrics.setEntryType(null);

        MetricsEntryPayload actualPayload = payload.getActual() != null ? payload.getActual() : new MetricsEntryPayload();
        MetricsEntryPayload targetPayload = payload.getTarget() != null ? payload.getTarget() : new MetricsEntryPayload();
        Map<Long, ProductionMetricCustomDefinition> definitionCache = new HashMap<>();
        Map<Long, ProductionMetricCustomValue> customValuesToSave = new LinkedHashMap<>();

        boolean updated = false;
        updated |= applySectionValuesIfPresent(metrics, actualPayload.getPeople(), PEOPLE_FIELDS, "People");
        updated |= applySectionValuesIfPresent(metrics, actualPayload.getQuality(), QUALITY_FIELDS, "Quality");
        updated |= applySectionValuesIfPresent(metrics, actualPayload.getService(), SERVICE_FIELDS, "Service");
        updated |= applySectionValuesIfPresent(metrics, actualPayload.getCost(), COST_FIELDS, "Cost");
        updated |= applySectionValuesIfPresent(metrics, targetPayload.getPeople(), PEOPLE_FIELDS, "People");
        updated |= applySectionValuesIfPresent(metrics, targetPayload.getQuality(), QUALITY_FIELDS, "Quality");
        updated |= applySectionValuesIfPresent(metrics, targetPayload.getService(), SERVICE_FIELDS, "Service");
        updated |= applySectionValuesIfPresent(metrics, targetPayload.getCost(), COST_FIELDS, "Cost");
        updated |= applyCustomSectionValuesIfPresent(metrics, actualPayload.getPeople(), "PEOPLE", customValuesToSave, definitionCache);
        updated |= applyCustomSectionValuesIfPresent(metrics, actualPayload.getQuality(), "QUALITY", customValuesToSave, definitionCache);
        updated |= applyCustomSectionValuesIfPresent(metrics, actualPayload.getService(), "SERVICE", customValuesToSave, definitionCache);
        updated |= applyCustomSectionValuesIfPresent(metrics, actualPayload.getCost(), "COST", customValuesToSave, definitionCache);
        updated |= applyCustomSectionValuesIfPresent(metrics, targetPayload.getPeople(), "PEOPLE", customValuesToSave, definitionCache);
        updated |= applyCustomSectionValuesIfPresent(metrics, targetPayload.getQuality(), "QUALITY", customValuesToSave, definitionCache);
        updated |= applyCustomSectionValuesIfPresent(metrics, targetPayload.getService(), "SERVICE", customValuesToSave, definitionCache);
        updated |= applyCustomSectionValuesIfPresent(metrics, targetPayload.getCost(), "COST", customValuesToSave, definitionCache);

        if (!updated) {
            throw new IllegalArgumentException("At least one metrics category is required");
        }

        deleteLegacyTargetRecord(targetDate);
        ProductionMetrics saved = repository.save(metrics);
        saveCustomMetricValues(saved, customValuesToSave);
        return toMetricsEntryBundlePayload(saved);
    }

    public ProductionMetrics createRecord(ProductionMetrics metrics) {
        metrics.setDate(normalizeToStartOfDay(metrics.getDate()));
        validateMetricsDateForEntry(metrics);
        if (repository.existsByDate(metrics.getDate())) {
            throw new RuntimeException("Record already exists for date: " + metrics.getDate());
        }
        return repository.save(metrics);
    }

    public ProductionMetrics updateRecord(Long id, ProductionMetrics metrics) {
        return repository.findById(id)
                .map(existing -> updateFields(existing, metrics))
                .orElseThrow(() -> new RuntimeException("Record not found with id: " + id));
    }

    public ProductionMetrics upsertByDate(ProductionMetrics metrics) {
        metrics.setDate(normalizeToStartOfDay(metrics.getDate()));
        validateMetricsDateForEntry(metrics);
        Optional<ProductionMetrics> existing = repository.findByDate(metrics.getDate().toLocalDate());
        if (existing.isPresent()) {
            return updateFields(existing.get(), metrics);
        }
        return repository.save(metrics);
    }

    @Transactional
    public ProductionMetrics patchRecordByDate(LocalDate date, ProductionMetrics patch) {
        LocalDateTime normalizedDate = date.atStartOfDay();
        validateActualEntryDate(date);

        List<ProductionMetrics> dayRecords = repository.findByDateBetweenOrderByDateAsc(
                normalizedDate,
                date.atTime(23, 59, 59)
        );

        ProductionMetrics target;
        if (dayRecords.isEmpty()) {
            target = new ProductionMetrics();
            target.setDate(normalizedDate);
        } else {
            target = dayRecords.get(0);
            target.setDate(normalizedDate);
            if (dayRecords.size() > 1) {
                repository.deleteAll(dayRecords.subList(1, dayRecords.size()));
            }
        }

        applyPartialUpdate(target, patch);
        return repository.save(target);
    }

    @Transactional
    public List<ProductionMetrics> bulkUpdateRecords(List<ProductionMetrics> updates) {
        if (updates == null || updates.isEmpty()) {
            return List.of();
        }

        List<ProductionMetrics> persisted = new java.util.ArrayList<>();

        for (ProductionMetrics update : updates) {
            if (update.getId() == null) {
                throw new IllegalArgumentException("Record id is required for bulk update");
            }

            ProductionMetrics existing = repository.findById(update.getId())
                    .orElseThrow(() -> new RuntimeException("Record not found with id: " + update.getId()));

            validateDateAllowedForEntry(existing.getDate());

            applyPartialUpdate(existing, update);
            persisted.add(repository.save(existing));
        }

        return persisted;
    }

    private void applyPartialUpdate(ProductionMetrics existing, ProductionMetrics update) {
        BeanWrapperImpl sourceWrapper = new BeanWrapperImpl(update);
        BeanWrapperImpl targetWrapper = new BeanWrapperImpl(existing);

        for (java.beans.PropertyDescriptor descriptor : sourceWrapper.getPropertyDescriptors()) {
            String field = descriptor.getName();
            if (isReadOnlyMetricsField(field)) {
                continue;
            }

            Object value = sourceWrapper.getPropertyValue(field);
            if (value == null) {
                continue;
            }

            if (value instanceof Number numberValue && numberValue.doubleValue() < 0) {
                throw new IllegalArgumentException(field + " cannot be negative");
            }

            targetWrapper.setPropertyValue(field, value);
        }
    }

    private ProductionMetrics updateFields(ProductionMetrics existing, ProductionMetrics metrics) {
        metrics.setDate(normalizeToStartOfDay(metrics.getDate()));
        validateMetricsDateForEntry(metrics);
        existing.setDate(metrics.getDate());
        BeanWrapperImpl sourceWrapper = new BeanWrapperImpl(metrics);
        BeanWrapperImpl targetWrapper = new BeanWrapperImpl(existing);

        for (java.beans.PropertyDescriptor descriptor : sourceWrapper.getPropertyDescriptors()) {
            String field = descriptor.getName();
            if (isReadOnlyMetricsField(field)) {
                continue;
            }

            Object value = sourceWrapper.getPropertyValue(field);
            if (value instanceof Number numberValue && numberValue.doubleValue() < 0) {
                throw new IllegalArgumentException(field + " cannot be negative");
            }
            targetWrapper.setPropertyValue(field, value);
        }
        return repository.save(existing);
    }

    private boolean isReadOnlyMetricsField(String field) {
        return "class".equals(field)
                || "id".equals(field)
                || "date".equals(field)
                || "entryType".equals(field)
                || "createdAt".equals(field)
                || "updatedAt".equals(field);
    }

    private LocalDateTime normalizeToStartOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toLocalDate().atStartOfDay();
    }

    private MetricsEntryPayload toMetricsEntryPayload(ProductionMetrics metrics) {
        MetricsEntryPayload payload = new MetricsEntryPayload();
        payload.setDate(metrics.getDate() != null ? metrics.getDate().toLocalDate() : null);
        payload.setEntryType(ENTRY_TYPE_ACTUAL);
        payload.setPeople(extractSectionValues(metrics, PEOPLE_FIELDS));
        payload.setQuality(extractSectionValues(metrics, QUALITY_FIELDS));
        payload.setService(extractSectionValues(metrics, SERVICE_FIELDS));
        payload.setCost(extractSectionValues(metrics, COST_FIELDS));
        appendCustomSectionValues(payload, metrics);
        return payload;
    }

    private MetricsEntryBundlePayload toMetricsEntryBundlePayload(ProductionMetrics metrics) {
        LocalDate actualDate = metrics.getDate() != null ? metrics.getDate().toLocalDate() : null;
        LocalDate targetDate = metrics.getTargetDate() != null
                ? metrics.getTargetDate().toLocalDate()
                : (actualDate != null ? actualDate.plusDays(1) : null);

        MetricsEntryPayload actualPayload = emptyEntryPayload(actualDate, ENTRY_TYPE_ACTUAL);
        MetricsEntryPayload targetPayload = emptyEntryPayload(targetDate, ENTRY_TYPE_TARGET);
        actualPayload.setPeople(extractSectionValues(metrics, PEOPLE_FIELDS));
        actualPayload.setQuality(extractSectionValues(metrics, QUALITY_FIELDS));
        actualPayload.setService(extractSectionValues(metrics, SERVICE_FIELDS));
        actualPayload.setCost(extractSectionValues(metrics, COST_FIELDS));
        targetPayload.setPeople(extractSectionValues(metrics, PEOPLE_FIELDS));
        targetPayload.setQuality(extractSectionValues(metrics, QUALITY_FIELDS));
        targetPayload.setService(extractSectionValues(metrics, SERVICE_FIELDS));
        targetPayload.setCost(extractSectionValues(metrics, COST_FIELDS));
        appendCustomSectionValues(actualPayload, targetPayload, metrics);
        return new MetricsEntryBundlePayload(actualDate, targetDate, actualPayload, targetPayload);
    }

    private void appendCustomSectionValues(MetricsEntryPayload payload, ProductionMetrics metrics) {
        appendCustomSectionValues(payload, emptyEntryPayload(payload.getDate(), ENTRY_TYPE_TARGET), metrics);
    }

    private void appendCustomSectionValues(MetricsEntryPayload actualPayload, MetricsEntryPayload targetPayload, ProductionMetrics metrics) {
        List<CustomMetricValueSnapshot> snapshots = resolveCustomMetricSnapshots(metrics);
        if (snapshots.isEmpty()) {
            return;
        }

        for (CustomMetricValueSnapshot snapshot : snapshots) {
            Map<String, Double> actualSection = resolveSectionMap(actualPayload, snapshot.getSection());
            Map<String, Double> targetSection = resolveSectionMap(targetPayload, snapshot.getSection());
            if (actualSection == null || targetSection == null) {
                continue;
            }

            Long definitionId = snapshot.getDefinitionId();
            actualSection.put(buildCustomFieldName(definitionId, "FtdActual"), snapshot.getFtdActual());
            actualSection.put(buildCustomFieldName(definitionId, "MtdActual"), snapshot.getMtdActual());
            actualSection.put(buildCustomFieldName(definitionId, "YtdActual"), snapshot.getYtdActual());
            targetSection.put(buildCustomFieldName(definitionId, "FtdTarget"), snapshot.getFtdTarget());
            targetSection.put(buildCustomFieldName(definitionId, "MtdTarget"), snapshot.getMtdTarget());
            targetSection.put(buildCustomFieldName(definitionId, "YtdTarget"), snapshot.getYtdTarget());
        }
    }

    private Map<String, Double> extractSectionValues(ProductionMetrics metrics, List<String> fields) {
        Map<String, Double> sectionValues = new LinkedHashMap<>();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(metrics);

        for (String field : fields) {
            Object value = wrapper.getPropertyValue(field);
            sectionValues.put(field, value instanceof Number ? ((Number) value).doubleValue() : null);
        }

        return sectionValues;
    }

    private boolean applySectionValuesIfPresent(ProductionMetrics target, Map<String, Double> values, List<String> fields, String sectionName) {
        if (values == null || values.isEmpty()) {
            return false;
        }

        BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
        for (String field : fields) {
            Double value = values.get(field);
            if (value == null) {
                continue;
            }
            if (value < 0) {
                throw new IllegalArgumentException(field + " cannot be negative");
            }
            wrapper.setPropertyValue(field, value);
        }

        return true;
    }

    private boolean applyCustomSectionValuesIfPresent(
            ProductionMetrics metrics,
            Map<String, Double> values,
            String expectedSection,
            Map<Long, ProductionMetricCustomValue> customValuesToSave,
            Map<Long, ProductionMetricCustomDefinition> definitionCache
    ) {
        if (values == null || values.isEmpty()) {
            return false;
        }

        boolean updated = false;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            CustomFieldDescriptor descriptor = parseCustomField(entry.getKey());
            if (descriptor == null || entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException(entry.getKey() + " cannot be negative");
            }

            ProductionMetricCustomDefinition definition = definitionCache.computeIfAbsent(
                    descriptor.definitionId(),
                    this::getRequiredCustomDefinition
            );
            if (!expectedSection.equalsIgnoreCase(definition.getSection())) {
                throw new IllegalArgumentException("Custom metric does not belong to " + expectedSection + " section");
            }

            ProductionMetricCustomValue customValue = customValuesToSave.computeIfAbsent(
                    descriptor.definitionId(),
                    id -> findExistingOrCreateCustomValue(metrics, definition)
            );
            customValue.setDefinition(definition);
            applyCustomFieldValue(customValue, descriptor.valueKey(), entry.getValue());
            updated = true;
        }

        return updated;
    }

    private MetricsEntryPayload emptyEntryPayload(LocalDate date, String entryType) {
        MetricsEntryPayload payload = new MetricsEntryPayload();
        payload.setDate(date);
        payload.setEntryType(entryType);
        payload.setPeople(new LinkedHashMap<>());
        payload.setQuality(new LinkedHashMap<>());
        payload.setService(new LinkedHashMap<>());
        payload.setCost(new LinkedHashMap<>());
        return payload;
    }

    private boolean isEntryPayloadEmpty(MetricsEntryPayload payload) {
        if (payload == null) {
            return true;
        }
        return isSectionEmpty(payload.getPeople())
                && isSectionEmpty(payload.getQuality())
                && isSectionEmpty(payload.getService())
                && isSectionEmpty(payload.getCost());
    }

    private boolean isSectionEmpty(Map<String, Double> section) {
        if (section == null || section.isEmpty()) {
            return true;
        }
        return section.values().stream().allMatch(value -> value == null);
    }

    private void validateActualEntryDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Metrics date is required");
        }

        LocalDate today = LocalDate.now();
        if (date.isEqual(today)) {
            throw new IllegalArgumentException("Today's data entry is not allowed. Please select a previous date.");
        }
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Future dates are not allowed for production metrics");
        }
    }

    private void validateDateAllowedForEntry(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("Metrics date is required");
        }
        validateActualEntryDate(dateTime.toLocalDate());
    }

    private void validateMetricsDateForEntry(ProductionMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("Metrics payload is required");
        }
        validateDateAllowedForEntry(metrics.getDate());
    }

    private Optional<ProductionMetrics> findPrimaryRecordByActualDate(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }

        List<ProductionMetrics> dayRecords = repository.findByDateBetweenOrderByDateAsc(
                date.atStartOfDay(),
                date.atTime(23, 59, 59)
        );

        return dayRecords.stream()
                .filter(record -> !ENTRY_TYPE_TARGET.equalsIgnoreCase(record.getEntryType()))
                .findFirst();
    }

    private Optional<ProductionMetrics> findLegacyTargetRecord(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }

        return repository.findByDateBetweenOrderByDateAsc(date.atStartOfDay(), date.atTime(23, 59, 59))
                .stream()
                .filter(record -> ENTRY_TYPE_TARGET.equalsIgnoreCase(record.getEntryType()))
                .findFirst();
    }

    private Optional<ProductionMetrics> findNormalizedRecordByActualDate(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }

        Optional<ProductionMetrics> primaryRecord = findPrimaryRecordByActualDate(date);
        if (primaryRecord.isPresent()) {
            return Optional.of(normalizeStoredRecord(primaryRecord.get()));
        }

        Optional<ProductionMetrics> legacyActual = repository.findByDateBetweenOrderByDateAsc(
                        date.atStartOfDay(),
                        date.atTime(23, 59, 59))
                .stream()
                .filter(record -> ENTRY_TYPE_ACTUAL.equalsIgnoreCase(record.getEntryType()))
                .findFirst();

        return legacyActual.map(this::normalizeStoredRecord);
    }

    private List<ProductionMetrics> buildNormalizedRecordsBetween(LocalDate startDate, LocalDate endDate) {
        List<ProductionMetrics> rawRecords = repository.findByDateBetweenOrderByDateAsc(
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59)
        );
        if (rawRecords.isEmpty()) {
            return List.of();
        }

        List<ProductionMetrics> normalizedRecords = new ArrayList<>();
        for (ProductionMetrics record : rawRecords) {
            if (record.getDate() == null || ENTRY_TYPE_TARGET.equalsIgnoreCase(record.getEntryType())) {
                continue;
            }
            normalizedRecords.add(normalizeStoredRecord(record));
        }
        return normalizedRecords;
    }

    private ProductionMetrics normalizeStoredRecord(ProductionMetrics source) {
        ProductionMetrics normalized = copyMetricsRecord(source);
        if (normalized.getTargetDate() == null && normalized.getDate() != null) {
            normalized.setTargetDate(normalized.getDate().toLocalDate().plusDays(1).atStartOfDay());
        }

        LocalDate targetLookupDate = normalized.getTargetDate() != null
                ? normalized.getTargetDate().toLocalDate()
                : null;
        Optional<ProductionMetrics> legacyTarget = findLegacyTargetRecord(targetLookupDate);
        legacyTarget.ifPresent(targetRecord -> copyTargetFields(normalized, targetRecord));
        return normalized;
    }

    private ProductionMetrics copyMetricsRecord(ProductionMetrics source) {
        ProductionMetrics copy = new ProductionMetrics();
        BeanWrapperImpl sourceWrapper = new BeanWrapperImpl(source);
        BeanWrapperImpl targetWrapper = new BeanWrapperImpl(copy);
        for (java.beans.PropertyDescriptor descriptor : sourceWrapper.getPropertyDescriptors()) {
            String field = descriptor.getName();
            if ("class".equals(field)) {
                continue;
            }
            targetWrapper.setPropertyValue(field, sourceWrapper.getPropertyValue(field));
        }
        copy.setCustomMetrics(new ArrayList<>());
        return copy;
    }

    private void copyTargetFields(ProductionMetrics target, ProductionMetrics source) {
        BeanWrapperImpl sourceWrapper = new BeanWrapperImpl(source);
        BeanWrapperImpl targetWrapper = new BeanWrapperImpl(target);
        for (java.beans.PropertyDescriptor descriptor : sourceWrapper.getPropertyDescriptors()) {
            String field = descriptor.getName();
            if (!field.endsWith("Target")) {
                continue;
            }
            Object value = sourceWrapper.getPropertyValue(field);
            if (value != null) {
                targetWrapper.setPropertyValue(field, value);
            }
        }
    }

    private void deleteLegacyTargetRecord(LocalDate targetDate) {
        findLegacyTargetRecord(targetDate).ifPresent(record -> {
            if (record.getId() != null) {
                repository.deleteById(record.getId());
            }
        });
    }

    public void deleteRecord(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void deleteByDate(LocalDateTime date) {
        repository.deleteByDate(date);
    }

    public void deleteAllRecords() {
        repository.deleteAll();
    }

    private ProductionMetrics withCustomMetricSnapshots(ProductionMetrics metrics) {
        if (metrics == null) {
            return null;
        }
        attachCustomMetricSnapshots(List.of(metrics));
        return metrics;
    }

    private void attachCustomMetricSnapshots(Collection<ProductionMetrics> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }

        List<ProductionMetrics> validRecords = metrics.stream()
                .filter(record -> record != null && record.getId() != null)
                .toList();
        if (validRecords.isEmpty()) {
            metrics.forEach(record -> {
                if (record != null) {
                    record.setCustomMetrics(new ArrayList<>());
                }
            });
            return;
        }

        List<Long> recordIds = validRecords.stream().map(ProductionMetrics::getId).toList();
        Map<Long, List<CustomMetricValueSnapshot>> snapshotsByMetricId = customValueRepository.findByProductionMetricsIdIn(recordIds)
                .stream()
                .collect(Collectors.groupingBy(
                value -> value.getProductionMetrics().getId(),
                        LinkedHashMap::new,
                Collectors.mapping(this::toCustomMetricValueSnapshot, Collectors.toList())
                ));

        for (ProductionMetrics record : validRecords) {
            List<CustomMetricValueSnapshot> snapshots = snapshotsByMetricId.getOrDefault(record.getId(), List.of())
                    .stream()
                .sorted(Comparator
                    .comparingInt((CustomMetricValueSnapshot snapshot) -> snapshot.getDisplayOrder() != null ? snapshot.getDisplayOrder() : Integer.MAX_VALUE)
                    .thenComparingLong(snapshot -> snapshot.getDefinitionId() != null ? snapshot.getDefinitionId() : Long.MAX_VALUE))
                    .toList();
            record.setCustomMetrics(new ArrayList<>(snapshots));
        }
    }

    private List<CustomMetricValueSnapshot> resolveCustomMetricSnapshots(ProductionMetrics metrics) {
        if (metrics == null || metrics.getId() == null) {
            return List.of();
        }
        List<ProductionMetricCustomValue> values = customValueRepository.findByProductionMetricsId(metrics.getId());
        return values.stream()
                .map(this::toCustomMetricValueSnapshot)
            .sorted(Comparator
                .comparingInt((CustomMetricValueSnapshot snapshot) -> snapshot.getDisplayOrder() != null ? snapshot.getDisplayOrder() : Integer.MAX_VALUE)
                .thenComparingLong(snapshot -> snapshot.getDefinitionId() != null ? snapshot.getDefinitionId() : Long.MAX_VALUE))
                .toList();
    }

    private CustomMetricValueSnapshot toCustomMetricValueSnapshot(ProductionMetricCustomValue value) {
        ProductionMetricCustomDefinition definition = value.getDefinition();
        return new CustomMetricValueSnapshot(
                definition != null ? definition.getId() : null,
                definition != null ? definition.getMetricKey() : null,
                definition != null ? definition.getSection() : null,
                definition != null ? definition.getLabel() : null,
                definition != null ? definition.getUnit() : null,
                definition != null ? definition.getDecimals() : null,
                definition != null ? definition.getDisplayOrder() : null,
                value.getFtdActual(),
                value.getFtdTarget(),
                value.getMtdActual(),
                value.getMtdTarget(),
                value.getYtdActual(),
                value.getYtdTarget()
        );
    }

    private void saveCustomMetricValues(ProductionMetrics savedMetrics, Map<Long, ProductionMetricCustomValue> customValuesToSave) {
        if (savedMetrics == null || savedMetrics.getId() == null || customValuesToSave == null || customValuesToSave.isEmpty()) {
            return;
        }

        for (ProductionMetricCustomValue value : customValuesToSave.values()) {
            value.setProductionMetrics(savedMetrics);
        }
        customValueRepository.saveAll(customValuesToSave.values());
    }

    private ProductionMetricCustomValue findExistingOrCreateCustomValue(ProductionMetrics metrics, ProductionMetricCustomDefinition definition) {
        if (metrics != null && metrics.getId() != null) {
            Optional<ProductionMetricCustomValue> existing = customValueRepository.findByProductionMetricsIdAndDefinitionId(metrics.getId(), definition.getId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ProductionMetricCustomValue value = new ProductionMetricCustomValue();
        value.setProductionMetrics(metrics);
        value.setDefinition(definition);
        return value;
    }

    private void applyCustomFieldValue(ProductionMetricCustomValue customValue, String valueKey, Double value) {
        switch (valueKey) {
            case "FtdActual" -> customValue.setFtdActual(value);
            case "FtdTarget" -> customValue.setFtdTarget(value);
            case "MtdActual" -> customValue.setMtdActual(value);
            case "MtdTarget" -> customValue.setMtdTarget(value);
            case "YtdActual" -> customValue.setYtdActual(value);
            case "YtdTarget" -> customValue.setYtdTarget(value);
            default -> throw new IllegalArgumentException("Unsupported custom field key: " + valueKey);
        }
    }

    private Map<String, Double> resolveSectionMap(MetricsEntryPayload payload, String section) {
        if (payload == null || section == null) {
            return null;
        }

        return switch (section.toUpperCase(Locale.ENGLISH)) {
            case "PEOPLE" -> payload.getPeople();
            case "QUALITY" -> payload.getQuality();
            case "SERVICE" -> payload.getService();
            case "COST" -> payload.getCost();
            default -> null;
        };
    }

    private String buildCustomFieldName(Long definitionId, String valueKey) {
        return "customMetric" + definitionId + valueKey;
    }

    private CustomFieldDescriptor parseCustomField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }

        Matcher matcher = CUSTOM_FIELD_PATTERN.matcher(fieldName);
        if (!matcher.matches()) {
            return null;
        }

        return new CustomFieldDescriptor(Long.parseLong(matcher.group(1)), matcher.group(2));
    }

    private ProductionMetricCustomDefinition getRequiredCustomDefinition(Long definitionId) {
        return customDefinitionRepository.findById(definitionId)
                .filter(definition -> Boolean.TRUE.equals(definition.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("Custom metric definition not found: " + definitionId));
    }

    private CustomMetricDefinitionPayload toCustomMetricDefinitionPayload(ProductionMetricCustomDefinition definition) {
        return new CustomMetricDefinitionPayload(
                definition.getId(),
                definition.getMetricKey(),
                definition.getSection(),
                definition.getLabel(),
                definition.getUnit(),
                definition.getDecimals(),
                definition.getDisplayOrder()
        );
    }

    private String normalizeSection(String section) {
        String normalized = section == null ? "" : section.trim().toUpperCase(Locale.ENGLISH);
        if (!SUPPORTED_SECTIONS.contains(normalized)) {
            throw new IllegalArgumentException("Section must be one of People, Quality, Service, or Cost");
        }
        return normalized;
    }

    private String normalizeLabel(String label) {
        String normalized = label == null ? "" : label.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Custom metric label is required");
        }
        return normalized;
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "-" : unit.trim().isEmpty() ? "-" : unit.trim();
    }

    private Integer normalizeDecimals(Integer decimals) {
        int resolved = decimals == null ? 2 : decimals;
        if (resolved < 0 || resolved > 4) {
            throw new IllegalArgumentException("Decimal places must be between 0 and 4");
        }
        return resolved;
    }

    private String createUniqueMetricKey(String requestedKey, String label) {
        String baseKey = slugifyMetricKey(requestedKey == null || requestedKey.isBlank() ? label : requestedKey);
        String candidate = baseKey;
        int suffix = 2;

        while (customDefinitionRepository.existsByMetricKeyIgnoreCase(candidate)) {
            candidate = baseKey + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slugifyMetricKey(String input) {
        String normalized = input == null ? "custom-metric" : input.trim().toLowerCase(Locale.ENGLISH);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "custom-metric" : normalized;
    }

    private record CustomFieldDescriptor(Long definitionId, String valueKey) {
    }

    // CSV Template Export (single-file format with all 4 sections)
    public ByteArrayInputStream exportTemplateCSV() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> headers = new ArrayList<>();
        headers.add("actualDate");
        headers.addAll(ALL_METRIC_FIELDS);

        try (CSVPrinter csvPrinter = new CSVPrinter(
                new PrintWriter(out),
                CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0]))
        )) {
            List<String> sampleRow = new ArrayList<>();
            sampleRow.add(LocalDate.now().minusDays(1).toString());
            for (int i = 0; i < ALL_METRIC_FIELDS.size(); i++) {
                sampleRow.add("");
            }
            csvPrinter.printRecord(sampleRow);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    // CSV Export (same unified format as template)
    public ByteArrayInputStream exportToCSV() throws IOException {
        List<ProductionMetrics> records = repository.findAll();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> headers = new ArrayList<>();
        headers.add("actualDate");
        headers.addAll(ALL_METRIC_FIELDS);

        try (CSVPrinter csvPrinter = new CSVPrinter(
                new PrintWriter(out),
                CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0]))
        )) {
            for (ProductionMetrics record : records) {
                if (record.getDate() == null || ENTRY_TYPE_TARGET.equalsIgnoreCase(record.getEntryType())) {
                    continue;
                }

                BeanWrapperImpl wrapper = new BeanWrapperImpl(record);
                List<Object> row = new ArrayList<>();
                row.add(record.getDate().toLocalDate().toString());
                for (String field : ALL_METRIC_FIELDS) {
                    row.add(wrapper.getPropertyValue(field));
                }
                csvPrinter.printRecord(row);
            }
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    // CSV Import (single-file format with all 4 sections)
    public void importFromCSV(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            int recordCount = 0;
            int successCount = 0;
            StringBuilder errorMessages = new StringBuilder();

            for (CSVRecord csvRecord : csvParser) {
                recordCount++;
                try {
                    LocalDate actualDate = parseCsvActualDate(getColumnValue(csvRecord, "actualDate", "date", "Date"));
                    if (actualDate == null) {
                        errorMessages.append("Row ").append(recordCount).append(": actualDate is required\n");
                        continue;
                    }

                    validateActualEntryDate(actualDate);

                    if (!hasAtLeastOneMetricValue(csvRecord)) {
                        errorMessages.append("Row ").append(recordCount)
                                .append(": At least one metric value is required when actualDate is provided\n");
                        continue;
                    }

                    ProductionMetrics metrics = findPrimaryRecordByActualDate(actualDate).orElseGet(ProductionMetrics::new);
                    metrics.setDate(actualDate.atStartOfDay());
                    metrics.setTargetDate(actualDate.plusDays(1).atStartOfDay());
                    metrics.setEntryType(null);

                    BeanWrapperImpl wrapper = new BeanWrapperImpl(metrics);
                    boolean hasAnyMetricValue = false;

                    for (String field : ALL_METRIC_FIELDS) {
                        String rawValue = getColumnValue(csvRecord, field, toSnakeCase(field));
                        if (rawValue == null || rawValue.trim().isEmpty()) {
                            continue;
                        }

                        Double parsedValue = parseDouble(rawValue);
                        if (parsedValue == null) {
                            throw new IllegalArgumentException("Invalid numeric value for " + field + ": " + rawValue);
                        }

                        if (parsedValue < 0) {
                            throw new IllegalArgumentException(field + " cannot be negative");
                        }

                        wrapper.setPropertyValue(field, parsedValue);
                        hasAnyMetricValue = true;
                    }

                    repository.save(metrics);
                    successCount++;
                } catch (Exception e) {
                    String errorMsg = "Row " + recordCount + ": " + e.getMessage();
                    System.err.println(errorMsg);
                    errorMessages.append(errorMsg).append("\n");
                    // Continue processing other records instead of stopping
                }
            }

            String resultMessage = "Successfully imported " + successCount + " out of " + recordCount + " records";
            System.out.println(resultMessage);

            if (errorMessages.length() > 0) {
                System.err.println("Errors encountered:\n" + errorMessages.toString());
            }

            if (successCount == 0 && recordCount > 0) {
                throw new IOException("Failed to import any records. " + errorMessages.toString());
            }

            if (successCount > 0 && errorMessages.length() > 0) {
                throw new IOException(resultMessage + ". Some rows failed:\n" + errorMessages);
            }
        }
    }

    private String getColumnValue(CSVRecord csvRecord, String... columnNames) {
        for (String columnName : columnNames) {
            try {
                if (csvRecord.isMapped(columnName)) {
                    return csvRecord.get(columnName);
                }
            } catch (IllegalArgumentException e) {
                // Column not found, try next name
            }
        }
        // Return empty string if no matching column found
        return "";
    }

    private boolean hasAtLeastOneMetricValue(CSVRecord csvRecord) {
        for (String field : ALL_METRIC_FIELDS) {
            String rawValue = getColumnValue(csvRecord, field, toSnakeCase(field));
            if (rawValue != null && !rawValue.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Double parseDouble(String value) {
        try {
            return value != null && !value.trim().isEmpty() ? Double.parseDouble(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toSnakeCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                out.append('_');
            }
            out.append(Character.toLowerCase(ch));
        }
        return out.toString();
    }

    private LocalDate parseCsvActualDate(String value) {
        LocalDateTime dt = parseDateTime(value);
        return dt == null ? null : dt.toLocalDate();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmedValue = value.trim();

        // Try multiple date formats
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),  // 2026-03-02 00:00:00
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),     // 02-03-2026 00:00
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),  // 02-03-2026 00:00:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),     // 2026-03-02 00:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),           // 2026-03-02
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),           // 02-03-2026
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),  // 03/02/2026 00:00:00
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),     // 03/02/2026 00:00
            DateTimeFormatter.ofPattern("MM/dd/yyyy")            // 03/02/2026
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                // Try parsing with time
                return LocalDateTime.parse(trimmedValue, formatter);
            } catch (Exception e1) {
                try {
                    // Try parsing as date only and set time to 00:00:00
                    return java.time.LocalDate.parse(trimmedValue, formatter).atStartOfDay();
                } catch (Exception e2) {
                    // Continue to next format
                }
            }
        }

        System.err.println("Failed to parse date: " + trimmedValue);
        return null;
    }
}
