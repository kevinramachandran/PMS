package org.example.service;

import org.example.entity.AppUser;
import org.example.entity.GembaWalkObservation;
import org.example.entity.GembaWalkRecord;
import org.example.repository.AppUserRepository;
import org.example.repository.GembaWalkRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class GembaWalkConfigService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final GembaWalkRecordRepository repository;
    private final GembaWalkMasterDataService masterDataService;
    private final AppUserRepository userRepository;
    private final EmailConfigService emailConfigService;

    public GembaWalkConfigService(GembaWalkRecordRepository repository,
                                  GembaWalkMasterDataService masterDataService,
                                  AppUserRepository userRepository,
                                  EmailConfigService emailConfigService) {
        this.repository = repository;
        this.masterDataService = masterDataService;
        this.userRepository = userRepository;
        this.emailConfigService = emailConfigService;
    }

    public Optional<GembaWalkRecord> find(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public GembaWalkRecord create(GembaWalkRecord record, String username) {
        applyDefaults(record, username);
        replaceObservations(record, record.getObservations());
        GembaWalkRecord saved = repository.save(record);
        notifyAreaHod(saved, "Gemba Walk Observation Submitted: " + label(saved), submittedBody(saved));
        return saved;
    }

    @Transactional
    public Optional<GembaWalkRecord> update(Long id, GembaWalkRecord incoming, String username) {
        return repository.findById(id).map(existing -> {
            boolean hadOpenObservation = hasOpenObservation(existing);
            existing.setScheduleItemId(incoming.getScheduleItemId());
            existing.setStartTime(trim(incoming.getStartTime()));
            existing.setCompletionTime(trim(incoming.getCompletionTime()));
            existing.setEmail(trim(incoming.getEmail()));
            existing.setManagerName(trim(incoming.getManagerName()));
            existing.setDateOfLeadershipSafetyWalkConducted(incoming.getDateOfLeadershipSafetyWalkConducted());
            existing.setManagementSafetyWalkWeek(trim(incoming.getManagementSafetyWalkWeek()));
            existing.setLocationOfMswConducted(trim(incoming.getLocationOfMswConducted()));
            existing.setResponsibility(trim(incoming.getResponsibility()));
            applyDefaults(existing, username);
            replaceObservations(existing, incoming.getObservations());
            GembaWalkRecord saved = repository.save(existing);
            if (hadOpenObservation && !hasOpenObservation(saved)) {
                notifyClosed(saved);
            }
            return saved;
        });
    }

    public Map<String, Object> options(String username, String location) {
        Map<String, Object> options = new LinkedHashMap<>();
        Optional<AppUser> currentUser = currentUser(username);
        options.put("currentUser", currentUser.map(this::userOption).orElse(Map.of()));
        options.put("gembaCategories", masterDataService.names(GembaWalkMasterDataService.GEMBA_CATEGORY));
        options.put("lifeSaverRules", masterDataService.names(GembaWalkMasterDataService.LIFE_SAVER_RULE));
        options.put("responsibilityUsers", responsibilityUsers(location));
        options.put("defaultResponsibility", defaultResponsibility(location).orElse(""));
        return options;
    }

    private void applyDefaults(GembaWalkRecord record, String username) {
        currentUser(username).ifPresent(user -> {
            if (isBlank(record.getEmail())) {
                record.setEmail(trim(user.getEmail()));
            }
            if (isBlank(record.getManagerName())) {
                record.setManagerName(firstNonBlank(user.getName(), user.getUsername()));
            }
        });
        if (isBlank(record.getStartTime())) {
            record.setStartTime(LocalTime.now().format(TIME_FORMATTER));
        }
        if (isBlank(record.getCompletionTime())) {
            record.setCompletionTime(LocalTime.now().format(TIME_FORMATTER));
        }
        if (record.getDateOfLeadershipSafetyWalkConducted() == null) {
            record.setDateOfLeadershipSafetyWalkConducted(LocalDate.now());
        }
        if (isBlank(record.getResponsibility())) {
            defaultResponsibility(record.getLocationOfMswConducted()).ifPresent(record::setResponsibility);
        }
    }

    private void replaceObservations(GembaWalkRecord record, List<GembaWalkObservation> observations) {
        record.getObservations().clear();
        List<GembaWalkObservation> source = observations == null ? List.of() : observations;
        int order = 1;
        for (GembaWalkObservation observation : source) {
            GembaWalkObservation item = new GembaWalkObservation();
            item.setObservationOrder(order++);
            item.setObservationDescription(trim(observation.getObservationDescription()));
            item.setPictureImage(trim(observation.getPictureImage()));
            item.setGembaCategory(trim(observation.getGembaCategory()));
            item.setLifeSaverRule(trim(observation.getLifeSaverRule()));
            item.setStatus(normalizeStatus(observation.getStatus()));
            item.setRecord(record);
            record.getObservations().add(item);
        }
    }

    private boolean hasOpenObservation(GembaWalkRecord record) {
        return record.getObservations().stream()
                .anyMatch(observation -> !"Closed".equalsIgnoreCase(trim(observation.getStatus())));
    }

    private void notifyClosed(GembaWalkRecord record) {
        List<String> recipients = new ArrayList<>(areaHodEmails(record.getLocationOfMswConducted()));
        if (!isBlank(record.getEmail()) && !recipients.contains(record.getEmail())) {
            recipients.add(record.getEmail());
        }
        emailConfigService.sendEmail(recipients, "Gemba Walk Observation Closed: " + label(record), closedBody(record));
    }

    private void notifyAreaHod(GembaWalkRecord record, String subject, String body) {
        emailConfigService.sendEmail(areaHodEmails(record.getLocationOfMswConducted()), subject, body);
    }

    private List<String> areaHodEmails(String location) {
        return activeUsers().stream()
                .filter(user -> isAreaMatch(user, location))
                .filter(this::isHod)
                .map(AppUser::getEmail)
                .filter(email -> !isBlank(email))
                .distinct()
                .toList();
    }

    private Optional<String> defaultResponsibility(String location) {
        return activeUsers().stream()
                .filter(user -> isAreaMatch(user, location))
                .filter(this::isHod)
                .map(user -> firstNonBlank(user.getUsername(), user.getName()))
                .findFirst();
    }

    private List<Map<String, String>> responsibilityUsers(String location) {
        List<AppUser> scoped = activeUsers().stream()
                .filter(user -> isAreaMatch(user, location))
                .filter(user -> isHod(user) || isAssignable(user))
                .toList();
        List<AppUser> users = scoped.isEmpty()
                ? activeUsers().stream().filter(user -> isHod(user) || isAssignable(user)).toList()
                : scoped;
        return users.stream().map(this::userOption).toList();
    }

    private List<AppUser> activeUsers() {
        return userRepository.findAll().stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(trim(user.getStatus())))
                .toList();
    }

    private Optional<AppUser> currentUser(String username) {
        if (isBlank(username)) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username);
    }

    private boolean isAreaMatch(AppUser user, String location) {
        if (isBlank(location)) {
            return true;
        }
        String normalizedLocation = location.trim().toLowerCase(Locale.ENGLISH);
        return trim(user.getArea()).toLowerCase(Locale.ENGLISH).equals(normalizedLocation)
                || trim(user.getDepartment()).toLowerCase(Locale.ENGLISH).equals(normalizedLocation);
    }

    private boolean isHod(AppUser user) {
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return designation.contains("HOD") || designation.contains("HEAD_OF_DEPARTMENT");
    }

    private boolean isAssignable(AppUser user) {
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return designation.equals("ENGINEER") || designation.equals("EXECUTIVE") || designation.equals("OPERATOR");
    }

    private Map<String, String> userOption(AppUser user) {
        String username = firstNonBlank(user.getUsername(), user.getEmail());
        String label = firstNonBlank(user.getName(), user.getUsername());
        return Map.of(
                "username", username,
                "label", label,
                "email", trim(user.getEmail())
        );
    }

    private String normalizeStatus(String status) {
        return "Closed".equalsIgnoreCase(trim(status)) ? "Closed" : "Open";
    }

    private String label(GembaWalkRecord record) {
        return "#" + record.getId();
    }

    private String submittedBody(GembaWalkRecord record) {
        return "Gemba Walk observation " + label(record) + " has been submitted for " + trim(record.getLocationOfMswConducted()) + ".";
    }

    private String closedBody(GembaWalkRecord record) {
        return "Gemba Walk observation " + label(record) + " has been closed.";
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? trim(second) : trim(first);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
