package dev.metaplus.core.util;

import lombok.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Canonical timestamp contract:
 * - store and emit UTC timestamps with ISO-8601 instant format
 * - example: 2025-09-03T02:30:00.123Z
 *
 * Compatibility parsing accepts an explicit fallback zone and otherwise uses
 * the system default zone for inputs that do not contain timezone information.
 */
public final class DateUtil {

    private DateUtil() {
    }

    public static String now() {
        return format(Instant.now());
    }

    public static String format(@NonNull Instant instant) {
        return instant.toString();
    }

    public static String format(@NonNull OffsetDateTime offsetDateTime) {
        return format(offsetDateTime.toInstant());
    }

    public static String format(long epochMilli) {
        return format(Instant.ofEpochMilli(epochMilli));
    }

    public static int toYmdInt(@NonNull Instant instant) {
        return toYmdInt(instant, ZoneOffset.UTC);
    }

    public static int toYmdInt(@NonNull Instant instant, @NonNull ZoneId zoneId) {
        ZonedDateTime zonedDateTime = instant.atZone(zoneId);
        return zonedDateTime.getYear() * 10000
                + zonedDateTime.getMonthValue() * 100
                + zonedDateTime.getDayOfMonth();
    }

    public static long toEpochMilli(@NonNull Instant instant) {
        return instant.toEpochMilli();
    }

    public static long toEpochMilli(@NonNull OffsetDateTime offsetDateTime) {
        return offsetDateTime.toInstant().toEpochMilli();
    }

    public static long toEpochMilli(@NonNull String formatted) {
        return parseCanonical(formatted).toEpochMilli();
    }

    public static Instant parseCanonical(@NonNull String formatted) {
        Instant instant;
        try {
            instant = Instant.parse(formatted);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to parse canonical timestamp '" + formatted + "'.", e);
        }
        String canonical = format(instant);
        if (!canonical.equals(formatted)) {
            throw new IllegalArgumentException("Expected canonical UTC timestamp but got '" + formatted + "'.");
        }
        return instant;
    }

    public static Instant parseFlexible(@NonNull String formatted, @NonNull ZoneId defaultZoneId) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(formatted));
        } catch (RuntimeException ignored) {
        }

        try {
            return OffsetDateTime.parse(formatted).toInstant();
        } catch (RuntimeException ignored) {
        }

        try {
            return ZonedDateTime.parse(formatted).toInstant();
        } catch (RuntimeException ignored) {
        }

        try {
            return LocalDateTime.parse(formatted).atZone(defaultZoneId).toInstant();
        } catch (RuntimeException ignored) {
        }

        try {
            return LocalDate.parse(formatted).atStartOfDay(defaultZoneId).toInstant();
        } catch (RuntimeException ignored) {
        }

        throw new IllegalArgumentException("Failed to parse timestamp '" + formatted + "'.");
    }

    public static Instant parseFlexible(@NonNull String formatted) {
        return parseFlexible(formatted, ZoneId.systemDefault());
    }

}
