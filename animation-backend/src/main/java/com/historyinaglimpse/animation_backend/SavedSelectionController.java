package com.historyinaglimpse.animation_backend;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/saved-selections")
public class SavedSelectionController {
    private final SavedSelectionService selections;

    public SavedSelectionController(SavedSelectionService selections) {
        this.selections = selections;
    }

    public record SaveRequest(String recordId) { }

    @GetMapping
    public List<SavedSelection> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return selections.list(authorization);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSelection save(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody SaveRequest body) {
        return selections.save(authorization, body.recordId());
    }

    @DeleteMapping("/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String recordId) {
        selections.remove(authorization, recordId);
    }
}
