package com.historyinaglimpse.animation_backend;

/** Public, read-only record from the timeline catalog. */
public record HistoricalRecord(
        String id,
        String title,
        int year,
        int lane,
        String date,
        String description,
        String source) {
}
