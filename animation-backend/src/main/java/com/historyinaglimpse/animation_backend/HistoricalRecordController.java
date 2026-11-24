package com.historyinaglimpse.animation_backend;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/historical-records")
public class HistoricalRecordController {
    private final HistoricalRecordCatalog catalog;

    public HistoricalRecordController(HistoricalRecordCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<HistoricalRecord> list() {
        return catalog.all();
    }

    @GetMapping("/{id}")
    public HistoricalRecord get(@PathVariable String id) {
        return catalog.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Historical record not found."));
    }
}
