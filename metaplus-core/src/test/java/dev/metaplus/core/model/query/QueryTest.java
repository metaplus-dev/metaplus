package dev.metaplus.core.model.query;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryTest {

    @Test
    void addBoolFilterTermAppendsTermsAndIgnoresNullValues() {
        Query query = new Query();

        query.addBoolFilterTerm("domain", "data");
        query.addBoolFilterTerm("system", null);
        query.addBoolFilterTerm("instance", "main");

        JsonArray filters = query.getJsonObject("bool").getJsonArray("filter");
        assertEquals(2, filters.size());
        assertEquals("data", filters.getJsonObject(0).getJsonObject("term").getString("domain"));
        assertEquals("main", filters.getJsonObject(1).getJsonObject("term").getString("instance"));
        assertNull(query.getJsonObject("bool").getJsonArray("must"));
    }
}
