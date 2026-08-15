package org.example.service;

import org.example.entity.AbnormalityTrackerEntry;
import org.example.repository.AbnormalityTrackerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AbnormalityTrackerService {

    private static final DateTimeFormatter PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("MMM''yyyy", Locale.ENGLISH);

    private final AbnormalityTrackerRepository repository;

    public AbnormalityTrackerService(AbnormalityTrackerRepository repository) {
        this.repository = repository;
    }

    public List<AbnormalityTrackerEntry> getByPeriodLabel(String periodLabel) {
        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(periodLabel);
    }

    public List<AbnormalityTrackerEntry> getLatest() {
        Optional<AbnormalityTrackerEntry> latest = repository.findTopByOrderByRecordDateDescIdDesc();
        if (latest.isEmpty() || latest.get().getPeriodLabel() == null) {
            return Collections.emptyList();
        }
        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(latest.get().getPeriodLabel());
    }

    public List<String> getAvailablePeriods() {
        return repository.findDistinctPeriodLabelsDesc();
    }

    @Transactional
    public List<AbnormalityTrackerEntry> replaceByPeriod(String periodLabel,
                                                          List<AbnormalityTrackerEntry> items) {
        repository.deleteByPeriodLabel(periodLabel);

        int row = 1;
        for (AbnormalityTrackerEntry item : items) {
            item.setId(null);
            item.setPeriodLabel(periodLabel);
            item.setRecordDate(LocalDate.now());
            if (item.getRowOrder() == null || item.getRowOrder() < 1) {
                item.setRowOrder(row);
            }
            row++;
        }

        return repository.saveAll(items);
    }

    @Transactional
    public List<AbnormalityTrackerEntry> importCsv(String periodLabel, MultipartFile file) throws Exception {
        if (periodLabel == null || periodLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Period label is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        List<AbnormalityTrackerEntry> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                if (row == 1 && line.toLowerCase(Locale.ENGLISH).contains("department")) {
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> cells = parseCsvLine(line);
                if (cells.isEmpty() || cells.get(0).trim().isEmpty()) {
                    continue;
                }

                AbnormalityTrackerEntry item = new AbnormalityTrackerEntry();
                item.setRowOrder(items.size() + 1);
                item.setDepartment(cells.get(0).trim());
                item.setYellowTags(parseIntegerCell(cells, 1));
                item.setRedTags(parseIntegerCell(cells, 2));
                item.setClosurePercent(parseDoubleCell(cells, 3));
                items.add(item);
            }
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("CSV does not contain any abnormality rows");
        }

        return replaceByPeriod(periodLabel.trim(), items);
    }

    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private Integer parseIntegerCell(List<String> cells, int index) {
        if (cells.size() <= index || cells.get(index).trim().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(cells.get(index).trim());
    }

    private Double parseDoubleCell(List<String> cells, int index) {
        if (cells.size() <= index || cells.get(index).trim().isEmpty()) {
            return 0.0;
        }
        return Double.parseDouble(cells.get(index).trim());
    }
}
