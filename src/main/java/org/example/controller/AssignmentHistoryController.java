package org.example.controller;

import org.example.entity.AssignmentHistory;
import org.example.service.AssignmentHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignment-history")
public class AssignmentHistoryController {
    private final AssignmentHistoryService service;
    public AssignmentHistoryController(AssignmentHistoryService service) { this.service = service; }
    @GetMapping("/{module}/{recordId}")
    public List<AssignmentHistory> history(@PathVariable String module, @PathVariable Long recordId) { return service.history(module, recordId); }
}
