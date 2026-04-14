package dev.metaplus.core.model.search;

import dev.metaplus.core.model.MetaplusDoc;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchResponseTest {

    @Test
    void fromEsResBodyMapsHitsAndMetadata() {
        JsonObject esResBody = JsonObject.of(
                "hits", JsonObject.of(
                        "total", JsonObject.of("value", 2L),
                        "max_score", 1.5,
                        "hits", JsonArray.of(
                                JsonObject.of(
                                        "_id", "doc-1",
                                        "_source", JsonObject.of(
                                                "idea", JsonObject.of(
                                                        "fqmn", "data:mysql:main:warehouse.sales.orders",
                                                        "domain", "data",
                                                        "system", "mysql",
                                                        "instance", "main",
                                                        "entity", "warehouse.sales.orders"
                                                )
                                        )
                                ),
                                JsonObject.of(
                                        "_id", "doc-2",
                                        "_source", JsonObject.of(
                                                "idea", JsonObject.of(
                                                        "fqmn", "data:mysql:main:warehouse.sales.customers",
                                                        "domain", "data",
                                                        "system", "mysql",
                                                        "instance", "main",
                                                        "entity", "warehouse.sales.customers"
                                                )
                                        )
                                )
                        )
                )
        );

        SearchResponse<MetaplusDoc> response = SearchResponse.fromEsResBody(esResBody, MetaplusDoc.class);

        assertEquals(2L, response.getTotal());
        assertEquals(1.5, response.getMaxScore());
        assertEquals(2, response.getHitsSize());
        assertEquals("data:mysql:main:warehouse.sales.orders", response.getHitsSource(0).getIdeaFqmn());
        assertEquals("doc-2", response.getHits().get(1).getInnerId());
    }

    @Test
    void fromEsResBodyReturnsNullForNullInput() {
        assertNull(SearchResponse.fromEsResBody(null, MetaplusDoc.class));
    }
}
