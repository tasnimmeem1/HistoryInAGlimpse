package org.example.historyinaglimpse;

/** Who is using the app, from least to most access. Matches the backend's roles plus GUEST. */
public enum Role {
    /** Not logged in: timeline only. */
    GUEST,
    /** Registered: can share stories and save history. */
    USER,
    /** Can also moderate stories and manage accounts. */
    ADMIN;

    public boolean atLeast(Role required) {
        return ordinal() >= required.ordinal();
    }

    public String label() {
        return switch (this) {
            case GUEST -> "Guest";
            case USER -> "Member";
            case ADMIN -> "Administrator";
        };
    }
}
