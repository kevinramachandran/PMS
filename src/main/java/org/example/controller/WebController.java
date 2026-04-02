package org.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/home")
    public String homePage() {
        return "home";
    }

    @GetMapping("/kpi-dashboard")
    public String kpiDashboard() {
        return "kpi-dashboard";
    }

    @GetMapping("/issue-board")
    public String issueBoard() {
        return "issue-board";
    }

    @GetMapping("/gemba-schedule")
    public String gembaSchedule() {
        return "gemba-schedule";
    }

    @GetMapping("/leadership-gemba-tracker")
    public String leadershipGembaTracker() {
        return "leadership-gemba-tracker";
    }

    @GetMapping("/training-schedule")
    public String trainingSchedule() {
        return "training-schedule";
    }

    @GetMapping("/meeting-agenda")
    public String meetingAgenda() {
        return "meeting-agenda";
    }

    @GetMapping("/process-confirmation")
    public String processConfirmation() {
        return "process-confirmation";
    }

    @GetMapping("/abnormality-tracker")
    public String abnormalityTracker() {
        return "abnormality-tracker";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    @GetMapping("/add-metrics")
    public String addMetrics() {
        return "add-metrics";
    }

    @GetMapping("/add-daily-data")
    public String addDailyData() {
        return "add-daily-data";
    }
}
