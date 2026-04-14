package dev.metaplus.core.util;

import dev.metaplus.core.exception.MetaplusException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdeaUtilTest {

    @Test
    void segmentsFromFqmnSplitsCanonicalIdentity() {
        assertArrayEquals(
                new String[]{"data", "mysql", "main", "warehouse.sales.orders"},
                IdeaUtil.segmentsFromFqmn("data:mysql:main:warehouse.sales.orders")
        );
    }

    @Test
    void segmentsFromFqmnRejectsNonCanonicalIdentity() {
        MetaplusException ex = assertThrows(MetaplusException.class,
                () -> IdeaUtil.segmentsFromFqmn("data:mysql:main"));

        assertEquals("Invalid fqmn: data:mysql:main", ex.getMessage());
    }

    @Test
    void domainFromFqmnReturnsLeadingSegment() {
        assertEquals("data", IdeaUtil.domainFromFqmn("data:mysql:main:warehouse.sales.orders"));
    }
}
