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

    @GetMapping("/gemba-walk-config")
    public String gembaWalkConfig() {
        return "gemba-walk-config";
    }

    @GetMapping("/gemba-findings")
    public String gembaFindings(Model model) {
        return "redirect:/gemba-reporting";
    }

    @GetMapping("/gemba-reporting")
    public String gembaReporting() {
        return "gemba-reporting";
    }

    @GetMapping("/user-dashboard")
    public String userDashboard(Model model) {
        model.addAttribute("isUserDashboard", true);
        return placeholderView(model, "User Dashboard", "");
    }

    @GetMapping("/leadership-gemba-tracker")
    public String leadershipGembaTracker() {
        return "leadership-gemba-tracker";
    }

    @GetMapping("/gemba-kaizen")
    public String gembaKaizen() {
        return "gemba-kaizen";
    }

    @GetMapping("/gemba-kaizen-config")
    public String gembaKaizenConfig() {
        return "gemba-kaizen-config";
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

    @GetMapping("/abnormality-reporting")
    public String abnormalityReporting() {
        return "abnormality-reporting";
    }

    @GetMapping("/abnormality-reporting-config")
    public String abnormalityReportingConfig() {
        return "abnormality-reporting-config";
    }

    @GetMapping("/settings")
    public String settings(@RequestParam(value = "config", required = false) String config, Model model) {
        if (config == null || config.isBlank()) {
            return "redirect:/pms/top-priorities";
        }

        String normalizedConfig = config.trim().toLowerCase();
        return switch (normalizedConfig) {
            case "metrics-data" -> settingsView(model, "metrics-data", "", "Production KPI Data");
            case "issue-board" -> settingsView(model, "issue-board", "", "Issue Board");
            case "master-gemba-walk" -> settingsView(model, "master-gemba-walk", "", "Gemba Walk");
            case "gemba-schedule" -> settingsView(model, "gemba-schedule", "", "Gemba Walk");
            case "master-gemba-kaizen" -> settingsView(model, "master-gemba-kaizen", "", "Gemba Kaizen");
            case "leadership-gemba-tracker" -> settingsView(model, "leadership-gemba-tracker", "", "Safety Gemba - Tracker");
            case "training-schedule" -> settingsView(model, "training-schedule", "", "Training Schedule");
            case "meeting-agenda" -> settingsView(model, "meeting-agenda", "", "PMS Agenda");
            case "master-process" -> settingsView(model, "master-process", "", "Process");
            case "process-confirmation" -> settingsView(model, "process-confirmation", "", "PMS Process Confirmation");
            case "master-abnormality" -> settingsView(model, "master-abnormality", "", "Abnormality");
            case "abnormality-tracker" -> settingsView(model, "abnormality-tracker", "", "Abnormality Tracker");
            case "hs-cross" -> settingsView(model, "hs-cross", "", "H&S Cross Daily");
            case "lsr-tracking" -> settingsView(model, "lsr-tracking", "", "LSR Tracking");
            case "info-portal" -> settingsView(model, "info-portal", "", "Info Portal");
            case "kpi-cross-color" -> settingsView(model, "kpi-cross-color", "", "KPI Target Cross Color");
            case "kpi-rename-dashboard" -> settingsView(model, "kpi-rename-dashboard", "", "KPI Configuration");
            case "kpi-plant-name" -> settingsView(model, "kpi-plant-name", "", "Plant");
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
        return settingsView(model, "metrics-data", "", "Production KPI Data");
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

    @GetMapping("/{externalUrl:www\\..+}")
    public String redirectBareExternalUrl(@PathVariable String externalUrl) {
        return "redirect:https://" + externalUrl;
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

    @GetMapping("/smtp-configuration")
    public String smtpConfiguration(Model model) {
        model.addAttribute("emailPageMode", "smtp");
        model.addAttribute("emailPageTitle", "SMTP Config");
        model.addAttribute("emailPageDescription", "Configure outgoing SMTP server, sender identity, and email sending status.");
        return "email-configuration";
    }

    @GetMapping("/email-configuration")
    public String emailConfiguration(Model model) {
        model.addAttribute("emailPageMode", "scheduler");
        model.addAttribute("emailPageTitle", "Email Scheduler");
        model.addAttribute("emailPageDescription", "Configure recurring group emails for Abnormality Reporting, Gemba Walk, and Gemba Kaizen.");
        return "email-configuration";
    }

    private String settingsView(Model model, String activePage, String activeType, String activeTitle) {
        model.addAttribute("activePage", activePage);
        model.addAttribute("activeType", activeType);
        model.addAttribute("activeTitle", activeTitle);
        return "settings";
    }

    private String placeholderView(Model model, String title, String message) {
        model.addAttribute("placeholderTitle", title);
        model.addAttribute("placeholderMessage", message);
        return "placeholder-page";
    }
}
