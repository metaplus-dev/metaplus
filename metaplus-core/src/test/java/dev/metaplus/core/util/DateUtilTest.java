package dev.metaplus.core.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateUtilTest {

    @Test
    void formatNormalizesToCanonicalUtcInstant() {
        Instant instant = Instant.parse("2025-09-03T02:30:00Z");

        assertEquals("2025-09-03T02:30:00Z", DateUtil.format(instant));
        assertEquals("2025-09-03T02:30:00Z", DateUtil.format(instant.toEpochMilli()));
    }

    @Test
    void parseCanonicalAcceptsCanonicalUtcOnly() {
        Instant instant = Instant.parse("2025-09-03T02:30:00Z");

        assertEquals(instant, DateUtil.parseCanonical("2025-09-03T02:30:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> DateUtil.parseCanonical("2025-09-03T10:30:00+08:00"));
        assertThrows(IllegalArgumentException.class,
                () -> DateUtil.toEpochMilli("2025-09-03 10:30:00"));
    }

    @Test
    void parseFlexibleSupportsIsoAndBoundaryFormats() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        ZoneId systemZone = ZoneId.systemDefault();

        assertEquals(Instant.parse("2025-09-03T02:30:00Z"),
                DateUtil.parseFlexible("2025-09-03T10:30:00+08:00"));
        assertEquals(Instant.parse("2025-09-03T02:30:00Z"),
                DateUtil.parseFlexible("2025-09-03T10:30:00+08:00[Asia/Shanghai]"));
        assertEquals(Instant.ofEpochMilli(1756866600000L),
                DateUtil.parseFlexible("1756866600000"));
        assertEquals(LocalDateTime.parse("2025-09-03T10:30:00").atZone(systemZone).toInstant(),
                DateUtil.parseFlexible("2025-09-03T10:30:00"));
        assertEquals(LocalDate.parse("2025-09-03").atStartOfDay(systemZone).toInstant(),
                DateUtil.parseFlexible("2025-09-03"));
        assertEquals(Instant.parse("2025-09-03T02:30:00Z"),
                DateUtil.parseFlexible("2025-09-03T10:30:00", shanghai));
        assertEquals(Instant.parse("2025-09-02T16:00:00Z"),
                DateUtil.parseFlexible("2025-09-03", shanghai));
    }

    @Test
    void parseFlexibleRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> DateUtil.parseFlexible("not-a-time"));

        assertTrue(DateUtil.parseFlexible("2025-09-03T02:30:00Z")
                .equals(Instant.parse("2025-09-03T02:30:00Z")));
        assertThrows(IllegalArgumentException.class,
                () -> DateUtil.parseFlexible("2025-09-03 10:30:00", ZoneOffset.UTC));
    }

    @Test
    void toYmdIntUsesUtcByDefaultAndSupportsExplicitZone() {
        Instant instant = Instant.parse("2025-09-02T16:30:00Z");

        assertEquals(20250902, DateUtil.toYmdInt(instant));
        assertEquals(20250903, DateUtil.toYmdInt(instant, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void formatAndEpochConversionsSupportOffsetDateTimeOverloads() {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2025-09-03T10:30:00+08:00");
        Instant instant = offsetDateTime.toInstant();

        assertEquals("2025-09-03T02:30:00Z", DateUtil.format(offsetDateTime));
        assertEquals(instant.toEpochMilli(), DateUtil.toEpochMilli(instant));
        assertEquals(instant.toEpochMilli(), DateUtil.toEpochMilli(offsetDateTime));
        assertEquals(instant.toEpochMilli(), DateUtil.toEpochMilli("2025-09-03T02:30:00Z"));
        assertTrue(DateUtil.now().endsWith("Z"));
    }

    @Test
    void nonNullContractsRejectNullInputs() {
        Instant instant = Instant.parse("2025-09-03T02:30:00Z");

        assertThrows(NullPointerException.class, () -> DateUtil.format((Instant) null));
        assertThrows(NullPointerException.class, () -> DateUtil.format((OffsetDateTime) null));
        assertThrows(NullPointerException.class, () -> DateUtil.toYmdInt(null));
        assertThrows(NullPointerException.class, () -> DateUtil.toYmdInt(instant, null));
        assertThrows(NullPointerException.class, () -> DateUtil.toEpochMilli((Instant) null));
        assertThrows(NullPointerException.class, () -> DateUtil.toEpochMilli((OffsetDateTime) null));
        assertThrows(NullPointerException.class, () -> DateUtil.toEpochMilli((String) null));
        assertThrows(NullPointerException.class, () -> DateUtil.parseCanonical(null));
        assertThrows(NullPointerException.class, () -> DateUtil.parseFlexible(null));
        assertThrows(NullPointerException.class, () -> DateUtil.parseFlexible("2025-09-03T02:30:00Z", null));
    }
}
