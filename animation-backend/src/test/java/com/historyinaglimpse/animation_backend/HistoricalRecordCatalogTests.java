package com.historyinaglimpse.animation_backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalRecordCatalogTests {
    @Test
    void loadsBaseAndTimelineResourceRecordsWithStableIds() {
        HistoricalRecordCatalog catalog = new HistoricalRecordCatalog();
        assertTrue(catalog.all().size() >= 80);

        HistoricalRecord stoneAge = catalog.all().stream()
                .filter(record -> record.title().equals("Stone Age life"))
                .findFirst().orElseThrow();
        assertEquals("stone-age-life-10000", stoneAge.id());
        assertFalse(stoneAge.description().isBlank());
        assertEquals(stoneAge, catalog.find(stoneAge.id()).orElseThrow());
        assertTrue(catalog.find("not-a-record").isEmpty());
    }
}
