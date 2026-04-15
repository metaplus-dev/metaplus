package dev.metaplus.backend.runtime;

import dev.metaplus.backend.es.EsIntegrationTestSupport;
import dev.metaplus.core.model.search.SearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuntimeStateStoreIT extends EsIntegrationTestSupport {

    private static final String RUNTIME_INDEX_NAME = "i_metaplus_runtime";
    private static final String RUNTIME_INDEX_RESOURCE = "es/i_metaplus_runtime.json";

    private String indexName;
    private RuntimeStateStore store;

    @BeforeEach
    void setUpStore() {
        indexName = uniqueIndexName(RUNTIME_INDEX_NAME);
        recreateIndex(indexName, RUNTIME_INDEX_RESOURCE);
        store = new RuntimeStateStore(esClient, indexName);
    }

    @AfterEach
    void tearDownStore() {
        deleteIndexIfExists(indexName);
    }

    @Test
    void markJobCompletedRoundTripsRuntimeStateAgainstRealEs() {
        String fqmn = "data:mysql:main:warehouse.sales.orders";

        store.markJobCompleted(fqmn, RuntimeJobType.LLM_GEN);

        RuntimeState runtimeState = store.get(fqmn);
        assertNotNull(runtimeState);
        assertEquals(fqmn, runtimeState.getFqmn());
        assertEquals("data", runtimeState.getDomain());
        assertNotNull(runtimeState.getLastLlmGenAt());
        assertNull(runtimeState.getDeletedAt());
    }

    @Test
    void searchPendingByJobReturnsOnlyPendingDocuments() {
        indexRuntimeState("data:mysql:main:warehouse.sales.a", "2026-03-20T10:00:00Z", null, null);
        indexRuntimeState("data:mysql:main:warehouse.sales.b", "2026-03-20T11:00:00Z", "2026-03-24T09:00:00Z", null);
        indexRuntimeState("data:mysql:main:warehouse.sales.c", "2026-03-20T12:00:00Z", "2026-03-26T09:00:00Z", null);
        indexRuntimeState("data:mysql:main:warehouse.sales.d", "2026-03-20T13:00:00Z", "2026-03-24T08:00:00Z",
                "2026-03-24T08:30:00Z");
        indexRuntimeState("ml:mysql:main:model.features.e", "2026-03-20T14:00:00Z", null, null);
        refreshIndex(indexName);

        SearchResponse<RuntimeState> response = store.searchPendingByJob(
                "data", RuntimeJobType.SAMPLING, "2026-03-25T10:00:00Z", 10, null);

        assertEquals(2, response.getHitsSize());
        assertEquals(List.of(
                "data:mysql:main:warehouse.sales.a",
                "data:mysql:main:warehouse.sales.b"),
                response.getSources().stream().map(RuntimeState::getFqmn).toList());
    }

    @Test
    void searchPendingByJobSupportsStableSearchAfterPaging() {
        String completedAt = "2026-03-24T09:00:00Z";
        String firstUpsertedAt = "2026-03-20T10:00:00Z";

        indexRuntimeState("data:mysql:main:warehouse.sales.a", firstUpsertedAt, completedAt, null);
        indexRuntimeState("data:mysql:main:warehouse.sales.b", "2026-03-20T11:00:00Z", completedAt, null);
        indexRuntimeState("data:mysql:main:warehouse.sales.c", "2026-03-20T11:00:00Z", completedAt, null);
        refreshIndex(indexName);

        SearchResponse<RuntimeState> firstPage = store.searchPendingByJob(
                "data", RuntimeJobType.SAMPLING, "2026-03-25T10:00:00Z", 1, null);
        SearchResponse<RuntimeState> nextPage = store.searchPendingByJob(
                "data", RuntimeJobType.SAMPLING, "2026-03-25T10:00:00Z", 10,
                JsonArray.of(completedAt, firstUpsertedAt, "data:mysql:main:warehouse.sales.a"));

        assertEquals(List.of("data:mysql:main:warehouse.sales.a"),
                firstPage.getSources().stream().map(RuntimeState::getFqmn).toList());
        assertEquals(List.of(
                "data:mysql:main:warehouse.sales.b",
                "data:mysql:main:warehouse.sales.c"),
                nextPage.getSources().stream().map(RuntimeState::getFqmn).toList());
    }

    @Test
    void clearByDomainRemovesOnlyMatchingDomainDocuments() {
        String dataFqmn = "data:mysql:main:warehouse.sales.orders";
        String mlFqmn = "ml:mysql:main:model.features.orders";

        indexRuntimeState(dataFqmn, "2026-03-20T10:00:00Z", null, null);
        indexRuntimeState(mlFqmn, "2026-03-20T11:00:00Z", null, null);
        refreshIndex(indexName);

        store.clearByDomain("data");
        refreshIndex(indexName);

        assertNull(store.get(dataFqmn));
        assertNotNull(store.get(mlFqmn));
    }

    private void indexRuntimeState(String fqmn, String upsertedAt, String lastSamplingAt, String deletedAt) {
        JsonObject document = JsonObject.of(
                "fqmn", fqmn,
                "domain", fqmn.substring(0, fqmn.indexOf(':')),
                "upsertedAt", upsertedAt
        );
        if (lastSamplingAt != null) {
            document.put("lastSamplingAt", lastSamplingAt);
        }
        if (deletedAt != null) {
            document.put("deletedAt", deletedAt);
        }
        indexDocument(indexName, fqmn, document);
    }
}
