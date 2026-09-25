package com.example.tickets.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Texts {

    private Texts() {
    }

    public static String strip(String value) {
        return value == null ? null : value.strip();
    }

    public static String blankToNull(String value) {
        String stripped = strip(value);
        return stripped == null || stripped.isEmpty() ? null : stripped;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** PostgreSQL stores microseconds; truncating keeps API responses equal to what is persisted. */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
