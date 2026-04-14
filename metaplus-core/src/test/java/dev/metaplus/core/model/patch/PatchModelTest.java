package dev.metaplus.core.model.patch;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchModelTest {

    @Test
    void patchMethodMapsStableHttpMethodNames() {
        assertEquals("/meta/update", PatchMethod.META_UPDATE.methodName());
        assertEquals(PatchMethod.PLUS_SCRIPT, PatchMethod.of("/plus/script"));
        assertNull(PatchMethod.of("/unknown"));
        assertEquals("/plus/updateByQuery", PatchMethod.PLUS_UPDATE_BY_QUERY.toString());
    }

    @Test
    void patchResponseFactoriesExposeSemanticStatusHelpers() {
        Result result = Result.fromEsBody(JsonObject.of("result", "updated", "_id", "doc-1", "_version", 7L));

        PatchResponse ok = PatchResponse.ok(result);
        PatchResponse notFound = PatchResponse.notFound();

        assertTrue(ok.isSuccess());
        assertFalse(ok.isNotFound());
        assertEquals(1, ok.getBody().getUpdated());

        assertFalse(notFound.isSuccess());
        assertTrue(notFound.isNotFound());
        assertEquals("not found", notFound.getMsg());
    }

    @Test
    void resultParsesSingleDocumentAndBulkCounters() {
        Result single = Result.fromEsBody(JsonObject.of(
                "_id", "doc-1",
                "_version", 3L,
                "result", "created"
        ));
        Result bulk = Result.fromEsBody(JsonObject.of(
                "created", 1,
                "updated", 2,
                "deleted", 3,
                "not_found", 4,
                "noops", 5
        ));

        single.plus(bulk);

        assertEquals("doc-1", single.getInnerId());
        assertEquals(3L, single.getInnerVersion());
        assertEquals(2, single.getCreated());
        assertEquals(2, single.getUpdated());
        assertEquals(3, single.getDeleted());
        assertEquals(5, single.getNoops());
        assertEquals(4, single.getNotFound());
        assertEquals(16, single.getTotal());
    }

    @Test
    void resultTreatsNoopAndUnknownResponsesCorrectly() {
        Result noop = Result.fromEsBody(JsonObject.of("result", "noop"));
        Result unknown = Result.fromEsBody(JsonObject.of("result", "ignored", "updated", 9));

        assertEquals(1, noop.getNoops());
        assertEquals(1, noop.getTotal());
        assertEquals(9, unknown.getUpdated());
        assertEquals(9, unknown.getTotal());
        assertNull(Result.fromEsBody(null));
    }
}
