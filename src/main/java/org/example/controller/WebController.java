package org.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "redirect:/kpi-dashboard";
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
    public String settings(@RequestParam(value = "config", required = false) String config, Model model) {
        if (config == null || config.isBlank()) {
            return "redirect:/pms/top-priorities";
        }

        String normalizedConfig = config.trim().toLowerCase();
        return switch (normalizedConfig) {
            case "metrics-data" -> settingsView(model, "metrics-data", "", "Production Metrics Data");
            case "issue-board" -> settingsView(model, "issue-board", "", "Issue Board");
            case "gemba-schedule" -> settingsView(model, "gemba-schedule", "", "Gemba Walk");
            case "leadership-gemba-tracker" -> settingsView(model, "leadership-gemba-tracker", "", "Safety Gemba - Tracker");
            case "training-schedule" -> settingsView(model, "training-schedule", "", "Training Schedule");
            case "meeting-agenda" -> settingsView(model, "meeting-agenda", "", "PMS Agenda");
            case "process-confirmation" -> settingsView(model, "process-confirmation", "", "PMS Process Confirmation");
            case "abnormality-tracker" -> settingsView(model, "abnormality-tracker", "", "Abnormality Tracker");
            case "hs-cross" -> settingsView(model, "hs-cross", "", "H&S Cross Daily");
            case "lsr-tracking" -> settingsView(model, "lsr-tracking", "", "LSR Tracking");
            case "kpi-footer-buttons" -> settingsView(model, "kpi-footer-buttons", "", "KPI Footer Buttons");
            case "kpi-cross-color" -> settingsView(model, "kpi-cross-color", "", "KPI Target Cross Color");
            case "license" -> settingsView(model, "license", "", "License Management");
            default -> "redirect:/pms/top-priorities";
        };
    }

    @GetMapping("/pms/top-priorities")
    public String topPriorities(Model model) {
        return settingsView(model, "priorities", "", "Top 3 Priorities");
    }

    @GetMapping("/pms/weekly-priorities")
    public String weeklyPriorities(Model model) {
        return settingsView(model, "weekly-priorities", "", "Top 3 Weekly Priorities");
    }

    @GetMapping("/pms/daily-performance")
    public String dailyPerformance(Model model) {
        return settingsView(model, "daily-performance", "", "Daily Performance");
    }

    @GetMapping("/pms/people-daily")
    public String peopleDaily(Model model) {
        return settingsView(model, "daily-section", "PEOPLE", "People - Daily");
    }

    @GetMapping("/pms/quality-daily")
    public String qualityDaily(Model model) {
        return settingsView(model, "daily-section", "QUALITY", "Quality - Daily");
    }

    @GetMapping("/pms/service-daily")
    public String serviceDaily(Model model) {
        return settingsView(model, "daily-section", "SERVICE", "Service - Daily");
    }

    @GetMapping("/pms/cost-daily")
    public String costDaily(Model model) {
        return settingsView(model, "daily-section", "COST", "Cost - Daily");
    }

    @GetMapping("/pms/production-metrics")
    public String productionMetrics(Model model) {
        return settingsView(model, "metrics-data", "", "Production Metrics Data");
    }

    @GetMapping("/config/issue-board")
    public String issueBoardConfig(Model model) {
        return settingsView(model, "issue-board", "", "Issue Board");
    }

    @GetMapping("/config/gemba-walk")
    public String gembaWalkPage(Model model) {
        return settingsView(model, "gemba-schedule", "", "Gemba Walk");
    }

    @GetMapping("/config/safety-gemba")
    public String safetyGembaConfig(Model model) {
        return settingsView(model, "leadership-gemba-tracker", "", "Safety Gemba Tracker");
    }

    @GetMapping("/config/training")
    public String trainingConfig(Model model) {
        return settingsView(model, "training-schedule", "", "Training Schedule Config");
    }

    @GetMapping("/config/pms-agenda")
    public String pmsAgendaConfig(Model model) {
        return settingsView(model, "meeting-agenda", "", "PMS Agenda Config");
    }

    @GetMapping("/config/process-confirmation")
    public String processConfirmationConfig(Model model) {
        return settingsView(model, "process-confirmation", "", "PMS Process Confirmation Config");
    }

    @GetMapping("/config/abnormality")
    public String abnormalityConfig(Model model) {
        return settingsView(model, "abnormality-tracker", "", "Abnormality Tracker Config");
    }

    @GetMapping("/config/hs-daily")
    public String hsDailyConfig(Model model) {
        return settingsView(model, "hs-cross", "", "H&S Cross Daily Config");
    }

    @GetMapping("/config/lsr")
    public String lsrConfig(Model model) {
        return settingsView(model, "lsr-tracking", "", "LSR Tracking Config");
    }

    @GetMapping("/client-selection")
    public String clientSelection() {
        return "client-selection";
    }

    @GetMapping("/add-metrics")
    public String addMetrics() {
        return "add-metrics";
    }

    @GetMapping("/add-daily-data")
    public String addDailyData() {
        return "add-daily-data";
    }

    @GetMapping("/pms-configuration")
    public String pmsConfiguration() {
        return "pms-configuration";
    }

    @GetMapping("/email-configuration")
    public String emailConfiguration() {
        return "email-configuration";
    }

    private String settingsView(Model model, String activePage, String activeType, String activeTitle) {
        model.addAttribute("activePage", activePage);
        model.addAttribute("activeType", activeType);
        model.addAttribute("activeTitle", activeTitle);
        return "settings";
    }
}
