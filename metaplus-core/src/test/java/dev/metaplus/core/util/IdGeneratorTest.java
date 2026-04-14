package dev.metaplus.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorTest {

    @Test
    void newId20ReturnsBase62IdWithExpectedLayout() {
        String id = IdGenerator.newId20("table");

        assertEquals(20, id.length());
        assertTrue(id.startsWith("mp"));
        assertTrue(id.matches("[0-9A-Za-z]{20}"));
    }

    @Test
    void newId20KeepsKeyCodeStableAndUsesDefaultForNullKey() {
        String first = IdGenerator.newId20("table");
        String second = IdGenerator.newId20("table");
        String nullKey = IdGenerator.newId20(null);

        assertEquals(first.substring(2, 4), second.substring(2, 4));
        assertEquals("00", nullKey.substring(2, 4));
        assertNotEquals(first, second);
    }
}
