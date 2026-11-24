package com.historyinaglimpse.animation_backend;

import com.fasterxml.jackson.annotation.JsonAlias;

/** A community story stored in public.shared_stories. */
public record Story(
        Long id,
        @JsonAlias("author_username") String authorUsername,
        @JsonAlias("author_display_name") String authorDisplayName,
        String title,
        String content,
        String tags,
        @JsonAlias("created_at") String createdAt,
        @JsonAlias("updated_at") String updatedAt) {
}
