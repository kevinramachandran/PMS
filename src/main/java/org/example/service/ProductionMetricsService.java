package org.example.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.example.entity.ProductionMetrics;
import org.example.model.MetricsEntryPayload;
import org.example.repository.ProductionMetricsRepository;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductionMetricsService {

    private final ProductionMetricsRepository repository;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> PEOPLE_FIELDS = List.of(
        "productionProductivityFtdActual", "productionProductivityFtdTarget", "productionProductivityMtdActual", "productionProductivityYtdActual",
        "logisticsProductivityFtdActual", "logisticsProductivityFtdTarget", "logisticsProductivityMtdActual", "logisticsProductivityYtdActual"
    );
    private static final List<String> QUALITY_FIELDS = List.of(
        "kpiSensoryScoreFtdActual", "kpiSensoryScoreFtdTarget", "kpiSensoryScoreMtdActual", "kpiSensoryScoreYtdActual",
        "kpiConsumerComplaintUnitsMhlFtdActual", "kpiConsumerComplaintUnitsMhlFtdTarget", "kpiConsumerComplaintUnitsMhlMtdActual", "kpiConsumerComplaintUnitsMhlYtdActual",
        "kpiCustomerComplaintUnitsMhlFtdActual", "kpiCustomerComplaintUnitsMhlFtdTarget", "kpiCustomerComplaintUnitsMhlMtdActual", "kpiCustomerComplaintUnitsMhlYtdActual"
    );
    private static final List<String> SERVICE_FIELDS = List.of(
        "noOfBrewsFtdActual", "noOfBrewsFtdTarget", "noOfBrewsMtdActual", "noOfBrewsYtdActual",
        "dispatchFtdActual", "dispatchFtdTarget", "dispatchMtdActual", "dispatchYtdActual",
        "processConfirmationBpFtdActual", "processConfirmationBpFtdTarget", "processConfirmationBpMtdActual", "processConfirmationBpYtdActual",
        "processConfirmationPackMtdActual", "processConfirmationPackMtdTarget", "processConfirmationPackYtdActual",
        "kpiOeeFtdActual", "kpiOeeFtdTarget", "kpiOeeMtdActual", "kpiOeeYtdActual",
        "kpiBeerLossFtdActual", "kpiBeerLossFtdTarget", "kpiBeerLossMtdActual", "kpiBeerLossYtdActual",
        "kpiWurHlHlFtdActual", "kpiWurHlHlFtdTarget", "kpiWurHlHlMtdActual", "kpiWurHlHlYtdActual"
    );
    private static final List<String> COST_FIELDS = List.of(
        "kpiElectricityKwhHlFtdActual", "kpiElectricityKwhHlFtdTarget", "kpiElectricityKwhHlMtdActual", "kpiElectricityKwhHlYtdActual",
        "kpiEnergyKwhHlFtdActual", "kpiEnergyKwhHlFtdTarget", "kpiEnergyKwhHlMtdActual", "kpiEnergyKwhHlYtdActual",
        "kpiRgbRatioFtdActual", "kpiRgbRatioFtdTarget", "kpiRgbRatioMtdActual", "kpiRgbRatioYtdActual"
    );

    // CRUD Operations
    public List<ProductionMetrics> getAllRecords() {
        return repository.findAll();
    }

    public Optional<ProductionMetrics> getRecordById(Long id) {
        return repository.findById(id);
    }

    public Optional<ProductionMetrics> getRecordByDate(LocalDateTime date) {
        return repository.findByDate(date);
    }

    public Optional<ProductionMetrics> getRecordByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public Optional<ProductionMetrics> getRecordByDay(LocalDate date) {
        return repository.findByDate(date);
    }

    public List<ProductionMetrics> getRecordsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByDateBetween(startDate, endDate);
    }

    public List<ProductionMetrics> getCurrentMonthData() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.withDayOfMonth(today.lengthOfMonth());

        LocalDateTime startDate = firstDay.atStartOfDay();
        LocalDateTime endDate = lastDay.atTime(23, 59, 59);

        return repository.findByDateBetweenOrderByDateAsc(startDate, endDate);
    }

    public List<ProductionMetrics> getRecordsByMonth(int month, int year) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59);
        return repository.findByDateBetweenOrderByDateAsc(startDate, endDate);
    }

    public Optional<MetricsEntryPayload> getMetricsEntry(LocalDate date) {
        validateEntryDate(date);
        return repository.findByDate(date).map(this::toMetricsEntryPayload);
    }

    @Transactional
    public MetricsEntryPayload saveMetricsEntry(MetricsEntryPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Metrics payload is required");
        }

        LocalDate date = payload.getDate();
        validateEntryDate(date);

        ProductionMetrics metrics = repository.findByDate(date).orElseGet(ProductionMetrics::new);
        metrics.setDate(date.atStartOfDay());

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
        validateEntryDate(date);

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
        setIfPresent(existing::setProductionProductivityFtdActual, update.getProductionProductivityFtdActual(), "productionProductivityFtdActual");
        setIfPresent(existing::setProductionProductivityFtdTarget, update.getProductionProductivityFtdTarget(), "productionProductivityFtdTarget");
        setIfPresent(existing::setProductionProductivityMtdActual, update.getProductionProductivityMtdActual(), "productionProductivityMtdActual");
        setIfPresent(existing::setProductionProductivityYtdActual, update.getProductionProductivityYtdActual(), "productionProductivityYtdActual");
        setIfPresent(existing::setLogisticsProductivityFtdActual, update.getLogisticsProductivityFtdActual(), "logisticsProductivityFtdActual");
        setIfPresent(existing::setLogisticsProductivityFtdTarget, update.getLogisticsProductivityFtdTarget(), "logisticsProductivityFtdTarget");
        setIfPresent(existing::setLogisticsProductivityMtdActual, update.getLogisticsProductivityMtdActual(), "logisticsProductivityMtdActual");
        setIfPresent(existing::setLogisticsProductivityYtdActual, update.getLogisticsProductivityYtdActual(), "logisticsProductivityYtdActual");
        setIfPresent(existing::setProcessConfirmationBpFtdActual, update.getProcessConfirmationBpFtdActual(), "processConfirmationBpFtdActual");
        setIfPresent(existing::setProcessConfirmationBpFtdTarget, update.getProcessConfirmationBpFtdTarget(), "processConfirmationBpFtdTarget");
        setIfPresent(existing::setProcessConfirmationBpMtdActual, update.getProcessConfirmationBpMtdActual(), "processConfirmationBpMtdActual");
        setIfPresent(existing::setProcessConfirmationBpYtdActual, update.getProcessConfirmationBpYtdActual(), "processConfirmationBpYtdActual");
        setIfPresent(existing::setProcessConfirmationPackMtdActual, update.getProcessConfirmationPackMtdActual(), "processConfirmationPackMtdActual");
        setIfPresent(existing::setProcessConfirmationPackMtdTarget, update.getProcessConfirmationPackMtdTarget(), "processConfirmationPackMtdTarget");
        setIfPresent(existing::setProcessConfirmationPackYtdActual, update.getProcessConfirmationPackYtdActual(), "processConfirmationPackYtdActual");
        setIfPresent(existing::setKpiOeeFtdActual, update.getKpiOeeFtdActual(), "kpiOeeFtdActual");
        setIfPresent(existing::setKpiOeeFtdTarget, update.getKpiOeeFtdTarget(), "kpiOeeFtdTarget");
        setIfPresent(existing::setKpiOeeMtdActual, update.getKpiOeeMtdActual(), "kpiOeeMtdActual");
        setIfPresent(existing::setKpiOeeYtdActual, update.getKpiOeeYtdActual(), "kpiOeeYtdActual");
        setIfPresent(existing::setKpiSensoryScoreFtdActual, update.getKpiSensoryScoreFtdActual(), "kpiSensoryScoreFtdActual");
        setIfPresent(existing::setKpiSensoryScoreFtdTarget, update.getKpiSensoryScoreFtdTarget(), "kpiSensoryScoreFtdTarget");
        setIfPresent(existing::setKpiSensoryScoreMtdActual, update.getKpiSensoryScoreMtdActual(), "kpiSensoryScoreMtdActual");
        setIfPresent(existing::setKpiSensoryScoreYtdActual, update.getKpiSensoryScoreYtdActual(), "kpiSensoryScoreYtdActual");
        setIfPresent(existing::setKpiWurHlHlFtdActual, update.getKpiWurHlHlFtdActual(), "kpiWurHlHlFtdActual");
        setIfPresent(existing::setKpiWurHlHlFtdTarget, update.getKpiWurHlHlFtdTarget(), "kpiWurHlHlFtdTarget");
        setIfPresent(existing::setKpiWurHlHlMtdActual, update.getKpiWurHlHlMtdActual(), "kpiWurHlHlMtdActual");
        setIfPresent(existing::setKpiWurHlHlYtdActual, update.getKpiWurHlHlYtdActual(), "kpiWurHlHlYtdActual");
        setIfPresent(existing::setKpiElectricityKwhHlFtdActual, update.getKpiElectricityKwhHlFtdActual(), "kpiElectricityKwhHlFtdActual");
        setIfPresent(existing::setKpiElectricityKwhHlFtdTarget, update.getKpiElectricityKwhHlFtdTarget(), "kpiElectricityKwhHlFtdTarget");
        setIfPresent(existing::setKpiElectricityKwhHlMtdActual, update.getKpiElectricityKwhHlMtdActual(), "kpiElectricityKwhHlMtdActual");
        setIfPresent(existing::setKpiElectricityKwhHlYtdActual, update.getKpiElectricityKwhHlYtdActual(), "kpiElectricityKwhHlYtdActual");
        setIfPresent(existing::setKpiEnergyKwhHlFtdActual, update.getKpiEnergyKwhHlFtdActual(), "kpiEnergyKwhHlFtdActual");
        setIfPresent(existing::setKpiEnergyKwhHlFtdTarget, update.getKpiEnergyKwhHlFtdTarget(), "kpiEnergyKwhHlFtdTarget");
        setIfPresent(existing::setKpiEnergyKwhHlMtdActual, update.getKpiEnergyKwhHlMtdActual(), "kpiEnergyKwhHlMtdActual");
        setIfPresent(existing::setKpiEnergyKwhHlYtdActual, update.getKpiEnergyKwhHlYtdActual(), "kpiEnergyKwhHlYtdActual");
        setIfPresent(existing::setKpiRgbRatioFtdActual, update.getKpiRgbRatioFtdActual(), "kpiRgbRatioFtdActual");
        setIfPresent(existing::setKpiRgbRatioFtdTarget, update.getKpiRgbRatioFtdTarget(), "kpiRgbRatioFtdTarget");
        setIfPresent(existing::setKpiRgbRatioMtdActual, update.getKpiRgbRatioMtdActual(), "kpiRgbRatioMtdActual");
        setIfPresent(existing::setKpiRgbRatioYtdActual, update.getKpiRgbRatioYtdActual(), "kpiRgbRatioYtdActual");
        setIfPresent(existing::setKpiBeerLossFtdActual, update.getKpiBeerLossFtdActual(), "kpiBeerLossFtdActual");
        setIfPresent(existing::setKpiBeerLossFtdTarget, update.getKpiBeerLossFtdTarget(), "kpiBeerLossFtdTarget");
        setIfPresent(existing::setKpiBeerLossMtdActual, update.getKpiBeerLossMtdActual(), "kpiBeerLossMtdActual");
        setIfPresent(existing::setKpiBeerLossYtdActual, update.getKpiBeerLossYtdActual(), "kpiBeerLossYtdActual");
        setIfPresent(existing::setKpiConsumerComplaintUnitsMhlFtdActual, update.getKpiConsumerComplaintUnitsMhlFtdActual(), "kpiConsumerComplaintUnitsMhlFtdActual");
        setIfPresent(existing::setKpiConsumerComplaintUnitsMhlFtdTarget, update.getKpiConsumerComplaintUnitsMhlFtdTarget(), "kpiConsumerComplaintUnitsMhlFtdTarget");
        setIfPresent(existing::setKpiConsumerComplaintUnitsMhlMtdActual, update.getKpiConsumerComplaintUnitsMhlMtdActual(), "kpiConsumerComplaintUnitsMhlMtdActual");
        setIfPresent(existing::setKpiConsumerComplaintUnitsMhlYtdActual, update.getKpiConsumerComplaintUnitsMhlYtdActual(), "kpiConsumerComplaintUnitsMhlYtdActual");
        setIfPresent(existing::setKpiCustomerComplaintUnitsMhlFtdActual, update.getKpiCustomerComplaintUnitsMhlFtdActual(), "kpiCustomerComplaintUnitsMhlFtdActual");
        setIfPresent(existing::setKpiCustomerComplaintUnitsMhlFtdTarget, update.getKpiCustomerComplaintUnitsMhlFtdTarget(), "kpiCustomerComplaintUnitsMhlFtdTarget");
        setIfPresent(existing::setKpiCustomerComplaintUnitsMhlMtdActual, update.getKpiCustomerComplaintUnitsMhlMtdActual(), "kpiCustomerComplaintUnitsMhlMtdActual");
        setIfPresent(existing::setKpiCustomerComplaintUnitsMhlYtdActual, update.getKpiCustomerComplaintUnitsMhlYtdActual(), "kpiCustomerComplaintUnitsMhlYtdActual");
    }

    private void setIfPresent(java.util.function.Consumer<Double> setter, Double value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        setter.accept(value);
    }

    private ProductionMetrics updateFields(ProductionMetrics existing, ProductionMetrics metrics) {
        metrics.setDate(normalizeToStartOfDay(metrics.getDate()));
        validateMetricsDateForEntry(metrics);
        existing.setDate(metrics.getDate());
        existing.setProductionProductivityFtdActual(metrics.getProductionProductivityFtdActual());
        existing.setProductionProductivityFtdTarget(metrics.getProductionProductivityFtdTarget());
        existing.setProductionProductivityMtdActual(metrics.getProductionProductivityMtdActual());
        existing.setProductionProductivityYtdActual(metrics.getProductionProductivityYtdActual());
        existing.setLogisticsProductivityFtdActual(metrics.getLogisticsProductivityFtdActual());
        existing.setLogisticsProductivityFtdTarget(metrics.getLogisticsProductivityFtdTarget());
        existing.setLogisticsProductivityMtdActual(metrics.getLogisticsProductivityMtdActual());
        existing.setLogisticsProductivityYtdActual(metrics.getLogisticsProductivityYtdActual());
        existing.setKpiSensoryScoreFtdActual(metrics.getKpiSensoryScoreFtdActual());
        existing.setKpiSensoryScoreFtdTarget(metrics.getKpiSensoryScoreFtdTarget());
        existing.setKpiSensoryScoreMtdActual(metrics.getKpiSensoryScoreMtdActual());
        existing.setKpiSensoryScoreMtdTarget(metrics.getKpiSensoryScoreMtdTarget());
        existing.setKpiSensoryScoreYtdActual(metrics.getKpiSensoryScoreYtdActual());
        existing.setKpiConsumerComplaintUnitsMhlFtdActual(metrics.getKpiConsumerComplaintUnitsMhlFtdActual());
        existing.setKpiConsumerComplaintUnitsMhlFtdTarget(metrics.getKpiConsumerComplaintUnitsMhlFtdTarget());
        existing.setKpiConsumerComplaintUnitsMhlMtdActual(metrics.getKpiConsumerComplaintUnitsMhlMtdActual());
        existing.setKpiConsumerComplaintUnitsMhlYtdActual(metrics.getKpiConsumerComplaintUnitsMhlYtdActual());
        existing.setKpiCustomerComplaintUnitsMhlFtdActual(metrics.getKpiCustomerComplaintUnitsMhlFtdActual());
        existing.setKpiCustomerComplaintUnitsMhlFtdTarget(metrics.getKpiCustomerComplaintUnitsMhlFtdTarget());
        existing.setKpiCustomerComplaintUnitsMhlMtdActual(metrics.getKpiCustomerComplaintUnitsMhlMtdActual());
        existing.setKpiCustomerComplaintUnitsMhlYtdActual(metrics.getKpiCustomerComplaintUnitsMhlYtdActual());
        existing.setProcessConfirmationBpFtdActual(metrics.getProcessConfirmationBpFtdActual());
        existing.setProcessConfirmationBpFtdTarget(metrics.getProcessConfirmationBpFtdTarget());
        existing.setProcessConfirmationBpMtdActual(metrics.getProcessConfirmationBpMtdActual());
        existing.setProcessConfirmationBpYtdActual(metrics.getProcessConfirmationBpYtdActual());
        existing.setProcessConfirmationPackMtdActual(metrics.getProcessConfirmationPackMtdActual());
        existing.setProcessConfirmationPackMtdTarget(metrics.getProcessConfirmationPackMtdTarget());
        existing.setProcessConfirmationPackYtdActual(metrics.getProcessConfirmationPackYtdActual());
        existing.setKpiOeeFtdActual(metrics.getKpiOeeFtdActual());
        existing.setKpiOeeFtdTarget(metrics.getKpiOeeFtdTarget());
        existing.setKpiOeeMtdActual(metrics.getKpiOeeMtdActual());
        existing.setKpiOeeMtdTarget(metrics.getKpiOeeMtdTarget());
        existing.setKpiOeeYtdActual(metrics.getKpiOeeYtdActual());
        existing.setKpiBeerLossFtdActual(metrics.getKpiBeerLossFtdActual());
        existing.setKpiBeerLossFtdTarget(metrics.getKpiBeerLossFtdTarget());
        existing.setKpiBeerLossMtdActual(metrics.getKpiBeerLossMtdActual());
        existing.setKpiBeerLossMtdTarget(metrics.getKpiBeerLossMtdTarget());
        existing.setKpiBeerLossYtdActual(metrics.getKpiBeerLossYtdActual());
        existing.setKpiWurHlHlFtdActual(metrics.getKpiWurHlHlFtdActual());
        existing.setKpiWurHlHlFtdTarget(metrics.getKpiWurHlHlFtdTarget());
        existing.setKpiWurHlHlMtdActual(metrics.getKpiWurHlHlMtdActual());
        existing.setKpiWurHlHlMtdTarget(metrics.getKpiWurHlHlMtdTarget());
        existing.setKpiWurHlHlYtdActual(metrics.getKpiWurHlHlYtdActual());
        existing.setKpiElectricityKwhHlFtdActual(metrics.getKpiElectricityKwhHlFtdActual());
        existing.setKpiElectricityKwhHlFtdTarget(metrics.getKpiElectricityKwhHlFtdTarget());
        existing.setKpiElectricityKwhHlMtdActual(metrics.getKpiElectricityKwhHlMtdActual());
        existing.setKpiElectricityKwhHlMtdTarget(metrics.getKpiElectricityKwhHlMtdTarget());
        existing.setKpiElectricityKwhHlYtdActual(metrics.getKpiElectricityKwhHlYtdActual());
        existing.setKpiEnergyKwhHlFtdActual(metrics.getKpiEnergyKwhHlFtdActual());
        existing.setKpiEnergyKwhHlFtdTarget(metrics.getKpiEnergyKwhHlFtdTarget());
        existing.setKpiEnergyKwhHlMtdActual(metrics.getKpiEnergyKwhHlMtdActual());
        existing.setKpiEnergyKwhHlMtdTarget(metrics.getKpiEnergyKwhHlMtdTarget());
        existing.setKpiEnergyKwhHlYtdActual(metrics.getKpiEnergyKwhHlYtdActual());
        existing.setKpiRgbRatioFtdActual(metrics.getKpiRgbRatioFtdActual());
        existing.setKpiRgbRatioFtdTarget(metrics.getKpiRgbRatioFtdTarget());
        existing.setKpiRgbRatioMtdActual(metrics.getKpiRgbRatioMtdActual());
        existing.setKpiRgbRatioMtdTarget(metrics.getKpiRgbRatioMtdTarget());
        existing.setKpiRgbRatioYtdActual(metrics.getKpiRgbRatioYtdActual());
        return repository.save(existing);
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
        payload.setPeople(extractSectionValues(metrics, PEOPLE_FIELDS));
        payload.setQuality(extractSectionValues(metrics, QUALITY_FIELDS));
        payload.setService(extractSectionValues(metrics, SERVICE_FIELDS));
        payload.setCost(extractSectionValues(metrics, COST_FIELDS));
        return payload;
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

    private void validateEntryDate(LocalDate date) {
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
        validateEntryDate(dateTime.toLocalDate());
    }

    private void validateMetricsDateForEntry(ProductionMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("Metrics payload is required");
        }
        validateDateAllowedForEntry(metrics.getDate());
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

    // CSV Export
    public ByteArrayInputStream exportToCSV() throws IOException {
        List<ProductionMetrics> records = repository.findAll();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CSVPrinter csvPrinter = new CSVPrinter(
                new PrintWriter(out),
                CSVFormat.DEFAULT.withHeader(
                        "date",
                        "production_productivity_ftd_actual",
                        "production_productivity_ftd_target",
                        "logistics_productivity_ftd_actual",
                        "logistics_productivity_ftd_target",
                        "kpi_sensory_score_ftd_actual",
                        "kpi_sensory_score_ftd_target",
                        "kpi_sensory_score_mtd_actual",
                        "kpi_sensory_score_mtd_target",
                        "kpi__consumer_complaint_units_/_mhl_ftd_actual",
                        "kpi__consumer_complaint__units_/_mhl_ftd_target",
                        "kpi__customer_complaint__units_/_mhl_ftd_actual",
                        "kpi__customer_complaint__units_/_mhl_ftd_target",
                        "process_confirmation_b&p_ftd_actual",
                        "process_confirmation_b&p_ftd_target",
                        "process_confirmation_pack_mtd_actual",
                        "process_confirmation_pack_mtd_target",
                        "kpi__oee__ftd_actual",
                        "kpi__oee__ftd_target",
                        "kpi__oee__mtd_actual",
                        "kpi__oee__mtd_target",
                        "kpi__beer_loss__ftd_actual",
                        "kpi__beer_loss__ftd_target",
                        "kpi__beer_loss__mtd_actual",
                        "kpi__beer_loss__mtd_target",
                        "kpi__wur_hl/hl_ftd_actual",
                        "kpi__wur_hl/hl_ftd_target",
                        "kpi__wur_hl/hl_mtd_actual",
                        "kpi__wur_hl/hl_mtd_target",
                        "kpi__electricity_kwh/hl_ftd_actual",
                        "kpi__electricity_kwh/hl_ftd_target",
                        "kpi__electricity_kwh/hl_mtd_actual",
                        "kpi__electricity_kwh/hl_mtd_target",
                        "kpi__energy_kwh/hl_ftd_actual",
                        "kpi__energy_kwh/hl_ftd_target",
                        "kpi__energy_kwh/hl_mtd_actual",
                        "kpi__energy_kwh/hl_mtd_target",
                        "kpi__rgb_ratio__ftd_actual",
                        "kpi__rgb_ratio__ftd_target",
                        "kpi__rgb_ratio__mtd_actual",
                        "kpi__rgb_ratio__mtd_target"
                )
        );

        for (ProductionMetrics record : records) {
            csvPrinter.printRecord(
                    record.getDate() != null ? record.getDate().format(DATE_TIME_FORMATTER) : "",
                    record.getProductionProductivityFtdActual(),
                    record.getProductionProductivityFtdTarget(),
                    record.getLogisticsProductivityFtdActual(),
                    record.getLogisticsProductivityFtdTarget(),
                    record.getKpiSensoryScoreFtdActual(),
                    record.getKpiSensoryScoreFtdTarget(),
                    record.getKpiSensoryScoreMtdActual(),
                    record.getKpiSensoryScoreMtdTarget(),
                    record.getKpiConsumerComplaintUnitsMhlFtdActual(),
                    record.getKpiConsumerComplaintUnitsMhlFtdTarget(),
                    record.getKpiCustomerComplaintUnitsMhlFtdActual(),
                    record.getKpiCustomerComplaintUnitsMhlFtdTarget(),
                    record.getProcessConfirmationBpFtdActual(),
                    record.getProcessConfirmationBpFtdTarget(),
                    record.getProcessConfirmationPackMtdActual(),
                    record.getProcessConfirmationPackMtdTarget(),
                    record.getKpiOeeFtdActual(),
                    record.getKpiOeeFtdTarget(),
                    record.getKpiOeeMtdActual(),
                    record.getKpiOeeMtdTarget(),
                    record.getKpiBeerLossFtdActual(),
                    record.getKpiBeerLossFtdTarget(),
                    record.getKpiBeerLossMtdActual(),
                    record.getKpiBeerLossMtdTarget(),
                    record.getKpiWurHlHlFtdActual(),
                    record.getKpiWurHlHlFtdTarget(),
                    record.getKpiWurHlHlMtdActual(),
                    record.getKpiWurHlHlMtdTarget(),
                    record.getKpiElectricityKwhHlFtdActual(),
                    record.getKpiElectricityKwhHlFtdTarget(),
                    record.getKpiElectricityKwhHlMtdActual(),
                    record.getKpiElectricityKwhHlMtdTarget(),
                    record.getKpiEnergyKwhHlFtdActual(),
                    record.getKpiEnergyKwhHlFtdTarget(),
                    record.getKpiEnergyKwhHlMtdActual(),
                    record.getKpiEnergyKwhHlMtdTarget(),
                    record.getKpiRgbRatioFtdActual(),
                    record.getKpiRgbRatioFtdTarget(),
                    record.getKpiRgbRatioMtdActual(),
                    record.getKpiRgbRatioMtdTarget()
            );
        }

        csvPrinter.flush();
        csvPrinter.close();

        return new ByteArrayInputStream(out.toByteArray());
    }

    // CSV Import
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
                    ProductionMetrics metrics = new ProductionMetrics();

                    metrics.setDate(parseDateTime(getColumnValue(csvRecord, "date", "Date")));

                    // Skip if date is null
                    if (metrics.getDate() == null) {
                        errorMessages.append("Row ").append(recordCount).append(": Invalid or missing date\n");
                        continue;
                    }

                    metrics.setProductionProductivityFtdActual(parseDouble(getColumnValue(csvRecord, "production_productivity_ftd_actual", "Production productivity_FTD Actual")));
                    metrics.setProductionProductivityFtdTarget(parseDouble(getColumnValue(csvRecord, "production_productivity_ftd_target", "Production productivity_FTD Target")));
                    metrics.setLogisticsProductivityFtdActual(parseDouble(getColumnValue(csvRecord, "logistics_productivity_ftd_actual", "Logistics productivity_FTD Actual")));
                    metrics.setLogisticsProductivityFtdTarget(parseDouble(getColumnValue(csvRecord, "logistics_productivity_ftd_target", "Logistics productivity_FTD Target")));
                    metrics.setKpiSensoryScoreFtdActual(parseDouble(getColumnValue(csvRecord, "kpi_sensory_score_ftd_actual", "KPI Sensory Score_FTD Actual")));
                    metrics.setKpiSensoryScoreFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi_sensory_score_ftd_target", "KPI Sensory Score_FTD Target")));
                    metrics.setKpiSensoryScoreMtdActual(parseDouble(getColumnValue(csvRecord, "kpi_sensory_score_mtd_actual", "KPI Sensory Score_MTD Actual")));
                    metrics.setKpiSensoryScoreMtdTarget(parseDouble(getColumnValue(csvRecord, "kpi_sensory_score_mtd_target", "KPI Sensory Score_MTD Target")));
                    metrics.setKpiConsumerComplaintUnitsMhlFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__consumer_complaint_units_/_mhl_ftd_actual", "KPI - Consumer Complaint (Units / mHL)_FTD Actual")));
                    metrics.setKpiConsumerComplaintUnitsMhlFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__consumer_complaint__units_/_mhl_ftd_target", "KPI - Consumer Complaint (Units / mHL)_FTD Target")));
                    metrics.setKpiCustomerComplaintUnitsMhlFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__customer_complaint__units_/_mhl_ftd_actual", "KPI - Customer Complaint (Units / mHL)_FTD Actual")));
                    metrics.setKpiCustomerComplaintUnitsMhlFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__customer_complaint__units_/_mhl_ftd_target", "KPI - Customer Complaint (Units / mHL)_FTD Target")));
                    metrics.setProcessConfirmationBpFtdActual(parseDouble(getColumnValue(csvRecord, "process_confirmation_b&p_ftd_actual", "Process Confirmation B&P_FTD Actual")));
                    metrics.setProcessConfirmationBpFtdTarget(parseDouble(getColumnValue(csvRecord, "process_confirmation_b&p_ftd_target", "Process Confirmation B&P_FTD Target")));
                    metrics.setProcessConfirmationPackMtdActual(parseDouble(getColumnValue(csvRecord, "process_confirmation_pack_mtd_actual", "Process Confirmation Pack_MTD Actual")));
                    metrics.setProcessConfirmationPackMtdTarget(parseDouble(getColumnValue(csvRecord, "process_confirmation_pack_mtd_target", "Process Confirmation Pack_MTD Target")));
                    metrics.setKpiOeeFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__oee__ftd_actual", "KPI - OEE (%)_FTD Actual")));
                    metrics.setKpiOeeFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__oee__ftd_target", "KPI - OEE (%)_FTD Target")));
                    metrics.setKpiOeeMtdActual(parseDouble(getColumnValue(csvRecord, "kpi__oee__mtd_actual", "KPI - OEE (%)_MTD Actual")));
                    metrics.setKpiOeeMtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__oee__mtd_target", "KPI - OEE (%)_MTD Target")));
                    metrics.setKpiBeerLossFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__beer_loss__ftd_actual", "KPI - Beer Loss (%)_FTD Actual")));
                    metrics.setKpiBeerLossFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__beer_loss__ftd_target", "KPI - Beer Loss (%)_FTD Target")));
                    metrics.setKpiBeerLossMtdActual(parseDouble(getColumnValue(csvRecord, "kpi__beer_loss__mtd_actual", "KPI - Beer Loss (%)_MTD Actual")));
                    metrics.setKpiBeerLossMtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__beer_loss__mtd_target", "KPI - Beer Loss (%)_MTD Target")));
                    metrics.setKpiWurHlHlFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__wur_hl/hl_ftd_actual", "KPI - WUR (hl/hl)_FTD Actual")));
                    metrics.setKpiWurHlHlFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__wur_hl/hl_ftd_target", "KPI - WUR (hl/hl)_FTD Target")));
                    metrics.setKpiWurHlHlMtdActual(parseDouble(getColumnValue(csvRecord, "kpi__wur_hl/hl_mtd_actual", "KPI - WUR (hl/hl)_MTD Actual")));
                    metrics.setKpiWurHlHlMtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__wur_hl/hl_mtd_target", "KPI - WUR (hl/hl)_MTD Target")));
                    metrics.setKpiElectricityKwhHlFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__electricity_kwh/hl_ftd_actual", "KPI - Electricity (kwh/hl)_FTD Actual")));
                    metrics.setKpiElectricityKwhHlFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__electricity_kwh/hl_ftd_target", "KPI - Electricity (kwh/hl)_FTD Target")));
                    metrics.setKpiElectricityKwhHlMtdActual(parseDouble(getColumnValue(csvRecord, "kpi__electricity_kwh/hl_mtd_actual", "KPI - Electricity (kwh/hl)_MTD Actual")));
                    metrics.setKpiElectricityKwhHlMtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__electricity_kwh/hl_mtd_target", "KPI - Electricity (kwh/hl)_MTD Target")));
                    metrics.setKpiEnergyKwhHlFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__energy_kwh/hl_ftd_actual", "KPI - Energy (kwh/hl)_FTD Actual")));
                    metrics.setKpiEnergyKwhHlFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__energy_kwh/hl_ftd_target", "KPI - Energy (kwh/hl)_FTD Target")));
                    metrics.setKpiEnergyKwhHlMtdActual(parseDouble(getColumnValue(csvRecord, "kpi__energy_kwh/hl_mtd_actual", "KPI - Energy (kwh/hl)_MTD Actual")));
                    metrics.setKpiEnergyKwhHlMtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__energy_kwh/hl_mtd_target", "KPI - Energy (kwh/hl)_MTD Target")));
                    metrics.setKpiRgbRatioFtdActual(parseDouble(getColumnValue(csvRecord, "kpi__rgb_ratio__ftd_actual", "KPI - RGB Ratio (%)_FTD Actual")));
                    metrics.setKpiRgbRatioFtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__rgb_ratio__ftd_target", "KPI - RGB Ratio (%)_FTD Target")));
                    metrics.setKpiRgbRatioMtdActual(parseDouble(getColumnValue(csvRecord, "kpi__rgb_ratio__mtd_actual", "KPI - RGB Ratio (%)_MTD Actual")));
                    metrics.setKpiRgbRatioMtdTarget(parseDouble(getColumnValue(csvRecord, "kpi__rgb_ratio__mtd_target", "KPI - RGB Ratio (%)_MTD Target")));

                    upsertByDate(metrics);
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

    private Double parseDouble(String value) {
        try {
            return value != null && !value.trim().isEmpty() ? Double.parseDouble(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
