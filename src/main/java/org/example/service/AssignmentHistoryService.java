package org.example.service;

import org.example.entity.AppUser;
import org.example.entity.AssignmentHistory;
import org.example.repository.AppUserRepository;
import org.example.repository.AssignmentHistoryRepository;
import org.example.util.RoleAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

@Service
public class AssignmentHistoryService {
    private final AssignmentHistoryRepository historyRepository;
    private final AppUserRepository userRepository;

    public AssignmentHistoryService(AssignmentHistoryRepository historyRepository, AppUserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    public List<AssignmentHistory> history(String module, Long recordId) {
        return historyRepository.findByModuleAndRecordIdOrderByAssignedAtDescIdDesc(module, recordId);
    }

    public List<Map<String, String>> eligibleUsers(String scope) {
        return userRepository.findAll().stream().filter(this::isActive)
                .filter(user -> isHod(user) || isOperational(user))
                .filter(user -> matchesScope(user, scope))
                .map(user -> {
                    Map<String, String> option = new LinkedHashMap<>();
                    option.put("username", clean(user.getUsername()));
                    option.put("label", clean(user.getName()).isBlank() ? clean(user.getUsername()) : clean(user.getName()));
                    option.put("designation", clean(user.getDesignation()));
                    return option;
                }).toList();
    }

    @Transactional
    public void record(String module, Long recordId, String oldAssignee, String newAssignee,
                       String remarks, String actorUsername, String scope) {
        String from = clean(oldAssignee);
        String to = clean(newAssignee);
        if (from.equalsIgnoreCase(to)) return;
        if (to.isBlank()) return;
        AppUser target = resolve(to);
        AppUser actor = resolve(actorUsername);
        boolean workflowModule = "gemba-walk".equalsIgnoreCase(clean(module))
                || "abnormality-reporting".equalsIgnoreCase(clean(module))
                || "gemba-kaizen".equalsIgnoreCase(clean(module))
                || "carlex-process-confirmation".equalsIgnoreCase(clean(module));
        if (target == null || !isActive(target)) throw new IllegalArgumentException("The selected assignee is not active");
        if (!from.isBlank() && (remarks == null || remarks.trim().isBlank())) {
            throw new IllegalArgumentException("Remarks are required for reassignment");
        }
        if (from.isBlank()) {
            if (!isHod(target) || (hasScopedHod(scope) && !matchesScope(target, scope))) {
                throw new IllegalArgumentException("The first assignment must be an Area HoD");
            }
        } else {
            if (!workflowModule && (historyRepository.countByModuleAndRecordId(module, recordId) >= 2 || !isHod(actor))) {
                throw new IllegalArgumentException("Only the HoD can make one escalation to an Engineer, Executive, or Operator");
            }
            if (workflowModule && actor != null && !isHod(actor) && !isOperational(actor) && !matchesUser(actor, from)) {
                throw new IllegalArgumentException("Only the current responsible user or Area HoD can reassign this record");
            }
            boolean validWorkflowTarget = workflowModule && (isHod(target) || isOperational(target));
            boolean validLegacyTarget = !workflowModule && isOperational(target);
            if ((!validWorkflowTarget && !validLegacyTarget) || !matchesScope(target, scope)) {
                throw new IllegalArgumentException("The escalation target must be an Engineer, Executive, or Operator in the same area");
            }
        }
        AssignmentHistory entry = new AssignmentHistory();
        entry.setModule(module); entry.setRecordId(recordId); entry.setFromAssignee(from);
        entry.setToAssignee(to); entry.setAssignedBy(clean(actorUsername)); entry.setRemarks(clean(remarks));
        entry.setEscalationLevel(from.isBlank() ? 0 : 1); entry.setAssignedAt(LocalDateTime.now());
        historyRepository.save(entry);
    }

    private AppUser resolve(String value) {
        String text = clean(value);
        if (text.isBlank()) return null;
        return userRepository.findByUsernameIgnoreCase(text).orElseGet(() -> userRepository.findAll().stream()
                .filter(user -> text.equalsIgnoreCase(clean(user.getName()))).findFirst().orElse(null));
    }
    private boolean isHod(AppUser user) { String designation = upper(user == null ? "" : user.getDesignation()); return user != null && (RoleAccess.isHod(user.getRole()) || designation.contains("HOD") || designation.contains("HEAD_OF_DEPARTMENT") || designation.contains("DEPARTMENT_HEAD") || designation.contains("AREA_HEAD")); }
    private boolean isOperational(AppUser user) { String role=RoleAccess.normalize(user == null ? "" : user.getRole()); String designation=upper(user == null ? "" : user.getDesignation()); return SetRoles.OPERATIONAL.contains(role) || SetRoles.OPERATIONAL.contains(designation); }
    private boolean isActive(AppUser user) { return user != null && !"INACTIVE".equalsIgnoreCase(clean(user.getStatus())); }
    private boolean matchesScope(AppUser user, String scope) { String expected=compact(scope); if(expected.isBlank()) return true; return matchesAnyArea(user == null ? "" : user.getArea(), expected) || expected.equals(compact(user == null ? "" : user.getDepartment())); }
    private boolean matchesAnyArea(String userAreas, String expected) { return List.of(clean(userAreas).split(",")).stream().map(this::compact).anyMatch(expected::equals); }
    private boolean matchesUser(AppUser user, String value) { String expected=compactUser(value); if(user == null || expected.isBlank()) return false; return expected.equals(compactUser(user.getUsername())) || expected.equals(compactUser(user.getName())) || expected.equals(compactUser(user.getEmail())) || expected.equals(compactUser(user.getEmployeeId())); }
    private boolean hasScopedHod(String scope) { String expected=compact(scope); return expected.isBlank() || userRepository.findAll().stream().filter(this::isActive).filter(this::isHod).anyMatch(user -> matchesScope(user, scope)); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String upper(String value) { return clean(value).toUpperCase(Locale.ROOT); }
    private String compact(String value) { return upper(value).replaceAll("[^A-Z0-9]", ""); }
    private String compactUser(String value) { return upper(value).replaceAll("[^A-Z0-9@.]", ""); }
    private static final class SetRoles { private static final java.util.Set<String> OPERATIONAL = java.util.Set.of("ENGINEER", "EXECUTIVE", "OPERATOR"); }
}
