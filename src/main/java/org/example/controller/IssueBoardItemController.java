package org.example.controller;

import org.example.entity.IssueBoardItem;
import org.example.service.IssueBoardItemService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/issue-board")
@CrossOrigin
public class IssueBoardItemController {

    private final IssueBoardItemService service;

    public IssueBoardItemController(IssueBoardItemService service) {
        this.service = service;
    }

    @GetMapping("/date/{date}")
    public List<IssueBoardItem> getByDate(@PathVariable String date) {
        return service.getByBoardDate(LocalDate.parse(date));
    }

    @GetMapping("/latest")
    public List<IssueBoardItem> getLatest() {
        return service.getLatestBoard();
    }

    @PostMapping("/replace/date/{date}")
    public List<IssueBoardItem> replaceByDate(
            @PathVariable String date,
            @RequestBody List<IssueBoardItem> items) {
        return service.replaceByBoardDate(LocalDate.parse(date), items);
    }
}
