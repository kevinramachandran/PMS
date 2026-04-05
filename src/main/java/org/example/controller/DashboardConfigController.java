package org.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard-config")
@CrossOrigin
public class DashboardConfigController {

    @Value("${dashboard.kpi.deck-title:PMS 4 deck V0_Dharuhera Brewery}")
    private String deckTitle;

    @Value("${dashboard.kpi.lsr.overview-target:85%}")
    private String lsrOverviewTarget;

    @Value("${dashboard.kpi.lsr.target-12:Target 50%}")
    private String lsrTarget12;

    @Value("${dashboard.kpi.lsr.target-5:Target 100%}")
    private String lsrTarget5;

    @GetMapping("/kpi")
    public Map<String, Object> getKpiDashboardConfig() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deckTitle", deckTitle);
        payload.put("lsrOverviewTarget", lsrOverviewTarget);
        payload.put("lsrTarget12", lsrTarget12);
        payload.put("lsrTarget5", lsrTarget5);

        payload.put("lsrFocusRules", List.of(
            List.of(
                "Slow down when Pedestrian in 2-3 truck length.",
                "Use LOTO when servicing or repairing equipment's",
                "Use PPE/harness while working at height",
                "Verify Oxygen content before entering",
                "ESI/PF (Legal documents) to be checked"
            ),
            List.of(
                "Never block walkways and fire exits",
                "Place locks and warning labels",
                "Rope of work areas post warning signs",
                "Never enter Confined space without work permit",
                "PPE adherence to be checked"
            ),
            List.of(
                "Follow speed limits",
                "Check the presence and working condition of protective devices",
                "Never access roof/fragile surface without work permit",
                "Never enter Confined space without Watchmen",
                "Work Permit adherence to be checked"
            )
        ));

        return payload;
    }
}
