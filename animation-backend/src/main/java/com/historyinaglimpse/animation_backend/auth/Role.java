package com.historyinaglimpse.animation_backend.auth;

/** Account roles, from least to most access. */
public enum Role {
    /** Browses the timeline and shared stories. */
    USER,
    /** Can also moderate shared stories and manage accounts. */
    ADMIN;

    public boolean atLeast(Role required) {
        return ordinal() >= required.ordinal();
    }
}
