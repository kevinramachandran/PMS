package org.example.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.example.entity.ProductionMetrics;
import org.example.repository.ProductionMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductionMetricsService {

    private final ProductionMetricsRepository repository;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    public List<ProductionMetrics> getRecordsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByDateBetween(startDate, endDate);
    }

    public ProductionMetrics createRecord(ProductionMetrics metrics) {
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
        Optional<ProductionMetrics> existing = repository.findByDate(metrics.getDate());
        if (existing.isPresent()) {
            return updateFields(existing.get(), metrics);
        }
        return repository.save(metrics);
    }

    private ProductionMetrics updateFields(ProductionMetrics existing, ProductionMetrics metrics) {
        existing.setDate(metrics.getDate());
        existing.setProductionProductivityFtdActual(metrics.getProductionProductivityFtdActual());
        existing.setProductionProductivityFtdTarget(metrics.getProductionProductivityFtdTarget());
        existing.setLogisticsProductivityFtdActual(metrics.getLogisticsProductivityFtdActual());
        existing.setLogisticsProductivityFtdTarget(metrics.getLogisticsProductivityFtdTarget());
        existing.setKpiSensoryScoreFtdActual(metrics.getKpiSensoryScoreFtdActual());
        existing.setKpiSensoryScoreFtdTarget(metrics.getKpiSensoryScoreFtdTarget());
        existing.setKpiSensoryScoreMtdActual(metrics.getKpiSensoryScoreMtdActual());
        existing.setKpiSensoryScoreMtdTarget(metrics.getKpiSensoryScoreMtdTarget());
        existing.setKpiConsumerComplaintUnitsMhlFtdActual(metrics.getKpiConsumerComplaintUnitsMhlFtdActual());
        existing.setKpiConsumerComplaintUnitsMhlFtdTarget(metrics.getKpiConsumerComplaintUnitsMhlFtdTarget());
        existing.setKpiCustomerComplaintUnitsMhlFtdActual(metrics.getKpiCustomerComplaintUnitsMhlFtdActual());
        existing.setKpiCustomerComplaintUnitsMhlFtdTarget(metrics.getKpiCustomerComplaintUnitsMhlFtdTarget());
        existing.setProcessConfirmationBpFtdActual(metrics.getProcessConfirmationBpFtdActual());
        existing.setProcessConfirmationBpFtdTarget(metrics.getProcessConfirmationBpFtdTarget());
        existing.setProcessConfirmationPackMtdActual(metrics.getProcessConfirmationPackMtdActual());
        existing.setProcessConfirmationPackMtdTarget(metrics.getProcessConfirmationPackMtdTarget());
        existing.setKpiOeeFtdActual(metrics.getKpiOeeFtdActual());
        existing.setKpiOeeFtdTarget(metrics.getKpiOeeFtdTarget());
        existing.setKpiOeeMtdActual(metrics.getKpiOeeMtdActual());
        existing.setKpiOeeMtdTarget(metrics.getKpiOeeMtdTarget());
        existing.setKpiBeerLossFtdActual(metrics.getKpiBeerLossFtdActual());
        existing.setKpiBeerLossFtdTarget(metrics.getKpiBeerLossFtdTarget());
        existing.setKpiBeerLossMtdActual(metrics.getKpiBeerLossMtdActual());
        existing.setKpiBeerLossMtdTarget(metrics.getKpiBeerLossMtdTarget());
        existing.setKpiWurHlHlFtdActual(metrics.getKpiWurHlHlFtdActual());
        existing.setKpiWurHlHlFtdTarget(metrics.getKpiWurHlHlFtdTarget());
        existing.setKpiWurHlHlMtdActual(metrics.getKpiWurHlHlMtdActual());
        existing.setKpiWurHlHlMtdTarget(metrics.getKpiWurHlHlMtdTarget());
        existing.setKpiElectricityKwhHlFtdActual(metrics.getKpiElectricityKwhHlFtdActual());
        existing.setKpiElectricityKwhHlFtdTarget(metrics.getKpiElectricityKwhHlFtdTarget());
        existing.setKpiElectricityKwhHlMtdActual(metrics.getKpiElectricityKwhHlMtdActual());
        existing.setKpiElectricityKwhHlMtdTarget(metrics.getKpiElectricityKwhHlMtdTarget());
        existing.setKpiEnergyKwhHlFtdActual(metrics.getKpiEnergyKwhHlFtdActual());
        existing.setKpiEnergyKwhHlFtdTarget(metrics.getKpiEnergyKwhHlFtdTarget());
        existing.setKpiEnergyKwhHlMtdActual(metrics.getKpiEnergyKwhHlMtdActual());
        existing.setKpiEnergyKwhHlMtdTarget(metrics.getKpiEnergyKwhHlMtdTarget());
        existing.setKpiRgbRatioFtdActual(metrics.getKpiRgbRatioFtdActual());
        existing.setKpiRgbRatioFtdTarget(metrics.getKpiRgbRatioFtdTarget());
        existing.setKpiRgbRatioMtdActual(metrics.getKpiRgbRatioMtdActual());
        existing.setKpiRgbRatioMtdTarget(metrics.getKpiRgbRatioMtdTarget());
        return repository.save(existing);
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
