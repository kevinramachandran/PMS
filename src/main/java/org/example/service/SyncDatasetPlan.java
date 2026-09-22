package org.example.service;

import java.util.*;

/** One dependency order for both existing saved configurations and new selections. */
public final class SyncDatasetPlan {
    private SyncDatasetPlan() { }

    private static final List<String> ORDER = List.of(
            "plant-master:PLANT", "plant-master:DEPARTMENT", "plant-master:PROCESS_AREA", "plant-master:DESIGNATION",
            "kaizen-master:CLASSIFICATION_OF_KAIZEN", "abnormality-master:ABT_TAG_TYPE",
            "abnormality-master:ABNORMALITY_DEFECT_TYPE", "walk-master:GEMBA_CATEGORY", "walk-master:LIFE_SAVER_RULE",
            "process-master:ZM_OBSERVATION", "process-master:PM_OBSERVATION", "process-master:OM_OBSERVATION",
            "process-master:QM_OBSERVATION", "users", "gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation");

    static int priority(String dataset, String category) {
        int index = ORDER.indexOf(dataset + (category.isBlank() ? "" : ":" + category));
        if (index < 0) throw new IllegalArgumentException("Unsupported sync dataset: " + dataset + ":" + category);
        return index;
    }

    public static List<String> resolve(String configured) {
        Set<String> selected = new HashSet<>();
        for (String line : Objects.toString(configured, "").split("\\R")) {
            if (line.isBlank()) continue;
            String[] parts = line.trim().split(":", -1);
            String spec = parts[0].trim().toLowerCase(Locale.ROOT);
            if (parts.length == 2) spec += ":" + parts[1].trim().toUpperCase(Locale.ROOT);
            if (parts.length > 2 || !ORDER.contains(spec))
                throw new IllegalArgumentException("Unsupported sync dataset: " + line.trim());
            include(spec, selected);
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("Select at least one sync dataset");
        return ORDER.stream().filter(selected::contains).toList();
    }

    private static void include(String spec, Set<String> selected) {
        if (!selected.add(spec)) return;
        if (spec.startsWith("plant-master:") && !spec.equals("plant-master:PLANT")) include("plant-master:PLANT", selected);
        if (spec.equals("plant-master:PROCESS_AREA")) include("plant-master:DEPARTMENT", selected);
        if (List.of("gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation").contains(spec)) include("users", selected);
        if (spec.equals("users")) ORDER.stream().filter(s -> s.startsWith("plant-master:")).forEach(s -> include(s, selected));
        String prefix = switch (spec) {
            case "gemba-walk" -> "walk-master:";
            case "gemba-kaizen" -> "kaizen-master:";
            case "abnormality" -> "abnormality-master:";
            case "process-confirmation" -> "process-master:";
            default -> "";
        };
        if (!prefix.isEmpty()) ORDER.stream().filter(s -> s.startsWith(prefix)).forEach(s -> include(s, selected));
    }
}
