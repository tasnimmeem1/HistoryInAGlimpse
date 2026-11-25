package com.historyinaglimpse.animation_backend;

import com.fasterxml.jackson.annotation.JsonAlias;

/** A historical record saved by the current account. */
public record SavedSelection(
        @JsonAlias("record_id") String recordId,
        @JsonAlias("saved_at") String savedAt) {
}
