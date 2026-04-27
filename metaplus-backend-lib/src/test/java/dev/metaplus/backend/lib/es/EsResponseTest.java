package dev.metaplus.backend.lib.es;

import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.core.model.patch.Result;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EsResponseTest {

    @Test
    void bulkErrorsMakeResponseFailEvenWithHttp200() {
        JsonObject body = new JsonObject();
        body.put("errors", true);
        body.put("items", List.of(JsonObject.of("index", JsonObject.of("status", 409))));
        EsResponse response = new EsResponse(200, body);

        assertTrue(response.isHttpSuccess());
        assertTrue(response.hasBulkErrors());
        assertFalse(response.isSuccess());
    }

    @Test
    void opResultParsesSingleWriteResponse() {
        EsResponse response = new EsResponse(200, JsonObject.of(
                "_id", "doc-1",
                "_version", 3,
                "result", "updated"
        ));
        Result result = response.getResult();

        assertEquals("doc-1", result.getInnerId());
        assertEquals(3L, result.getInnerVersion());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getTotal());
    }

    @Test
    void opResultFallsBackToCountersWhenResultValueIsUnknown() {
        EsResponse response = new EsResponse(200, JsonObject.of(
                "result", "partial",
                "created", 2,
                "deleted", 1,
                "noops", 3
        ));
        Result result = response.getResult();

        assertEquals(2, result.getCreated());
        assertEquals(1, result.getDeleted());
        assertEquals(3, result.getNoops());
        assertEquals(6, result.getTotal());
    }

    @Test
    void getResultAggregatesBulkItemsAcrossOperationTypes() {
        EsResponse response = new EsResponse(200, JsonObject.of(
                "errors", false,
                "items", List.of(
                        JsonObject.of("index", JsonObject.of("_id", "doc-1", "_version", 1, "result", "created")),
                        JsonObject.of("update", JsonObject.of("_id", "doc-2", "_version", 2, "result", "updated")),
                        JsonObject.of("delete", JsonObject.of("_id", "doc-3", "_version", 3, "result", "not_found")),
                        JsonObject.of("create", JsonObject.of("_id", "doc-4", "_version", 4, "result", "noop"))
                )
        ));

        Result result = response.getResult();

        assertEquals(1, result.getCreated());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getNotFound());
        assertEquals(1, result.getNoops());
        assertEquals(4, result.getTotal());
    }

    @Test
    void bulkHelpersExposeExplicitBulkAndSingleOperationViews() {
        JsonObject bulkBody = JsonObject.of(
                "items", List.of(
                        JsonObject.of("index", JsonObject.of("result", "created")),
                        JsonObject.of("update", JsonObject.of("result", "deleted"))
                )
        );
        EsResponse bulkResponse = new EsResponse(200, bulkBody);
        EsResponse opResponse = new EsResponse(200, JsonObject.of("result", "updated"));

        assertEquals(2, bulkResponse.getBulkResult().getTotal());
        assertEquals(1, bulkResponse.getBulkResult().getCreated());
        assertEquals(1, bulkResponse.getBulkResult().getDeleted());
        assertEquals(1, opResponse.getOpResult().getUpdated());
        assertNull(opResponse.getBulkResult());
    }

}
