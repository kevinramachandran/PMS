package org.example.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SyncDatasetPlanTest {
    @Test void oldRecordOnlyConfigurationIncludesDependenciesInOrder() {
        var plan = SyncDatasetPlan.resolve("gemba-walk\ngemba-kaizen\nabnormality\nprocess-confirmation\nusers");
        assertEquals(18, plan.size());
        assertEquals(List.of("plant-master:PLANT", "plant-master:DEPARTMENT", "plant-master:PROCESS_AREA", "plant-master:DESIGNATION"), plan.subList(0, 4));
        assertTrue(plan.indexOf("walk-master:LIFE_SAVER_RULE") < plan.indexOf("users"));
        assertTrue(plan.indexOf("users") < plan.indexOf("gemba-walk"));
    }

    @Test void selectiveSyncAddsOnlyRequiredModulesAndDeduplicates() {
        var plan = SyncDatasetPlan.resolve(" Gemba-Walk \ngemba-walk\nplant-master:plant");
        assertEquals(8, plan.size());
        assertFalse(plan.contains("gemba-kaizen"));
        assertFalse(plan.contains("kaizen-master:CLASSIFICATION_OF_KAIZEN"));
        assertEquals(plan, SyncDatasetPlan.resolve(String.join("\n", plan)));
    }

    @Test void masterOnlySyncDoesNotPullUsersOrRecords() {
        assertEquals(List.of("plant-master:PLANT", "plant-master:DEPARTMENT", "plant-master:PROCESS_AREA"),
                SyncDatasetPlan.resolve("plant-master:PROCESS_AREA"));
    }

    @Test void rejectsUnknownOrEmptySelectionsBeforeDownloading() {
        for (String invalid : List.of("", "unknown", "plant-master", "users:PLANT", "plant-master:../PLANT", "users:foo:bar"))
            assertThrows(IllegalArgumentException.class, () -> SyncDatasetPlan.resolve(invalid));
    }
}
