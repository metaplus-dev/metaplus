package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.domain.ValueStore;
import dev.metaplus.core.exception.MetaplusException;
import dev.metaplus.core.model.patch.PatchOptions;
import dev.metaplus.core.model.patch.Script;
import dev.metaplus.core.model.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocDaoReindexByQueryTest {

    private EsClient esClient;
    private DocDao docDao;

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        ValueStore valueStore = mock(ValueStore.class);
        IndexDao indexDao = mock(IndexDao.class);

        when(valueStore.composeScript(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        doNothing().when(indexDao).createIndex(anyString(), any(JsonObject.class));
        doNothing().when(indexDao).deleteIndex(anyString());

        docDao = new DocDao(esClient, valueStore, indexDao);
    }

    @Test
    void reindexByQueryPagesAllTmpDocsAndUsesSearchAfter() {
        int total = 1005;
        AtomicInteger reindexCalls = new AtomicInteger(0);
        AtomicInteger searchCalls = new AtomicInteger(0);

        List<JsonObject> searchBodies = new ArrayList<>();
        List<JsonObject> deleteBodies = new ArrayList<>();

        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            JsonObject body = invocation.getArgument(1);
            String path = uri.getPath();

            if ("/_reindex".equals(path)) {
                int call = reindexCalls.incrementAndGet();
                if (call == 1) {
                    return _success(JsonObject.of("created", total));
                }
                return _success(JsonObject.of("created", total));
            }

            if (path != null && path.endsWith("/_search")) {
                searchBodies.add(body);
                int call = searchCalls.getAndIncrement();
                if (call == 0) {
                    return _success(_searchPage(0, 1000));
                }
                if (call == 1) {
                    return _success(_searchPage(1000, 5));
                }
                return _success(_emptySearchPage(total));
            }

            if (path != null && path.endsWith("/_delete_by_query")) {
                deleteBodies.add(body);
                int deleted = body.getJsonObject("query").getJsonObject("ids").getJsonArray("values").size();
                return _success(JsonObject.of("deleted", deleted));
            }

            throw new IllegalStateException("Unexpected ES uri: " + uri);
        });

        Script script = new Script();
        script.setSource("ctx._source.idea.fqmn = ctx._source.idea.fqmn + '_new';");

        Map<String, String> fqmnMapping = new LinkedHashMap<>();
        docDao.reindexByQuery("demo", new Query(), script, fqmnMapping, null);

        assertEquals(total, fqmnMapping.size());
        assertEquals("demo:mysql:main:n1004_new", fqmnMapping.get("demo:mysql:main:n1004"));
        assertEquals(2, reindexCalls.get());

        assertEquals(3, searchBodies.size());
        assertNull(searchBodies.get(0).getJsonArray("search_after"));
        assertNotNull(searchBodies.get(1).getJsonArray("search_after"));
        assertEquals(999L, searchBodies.get(1).getJsonArray("search_after").getLong(0));

        assertEquals(2, deleteBodies.size());
        assertEquals(1000, deleteBodies.get(0).getJsonObject("query").getJsonObject("ids").getJsonArray("values").size());
        assertEquals(5, deleteBodies.get(1).getJsonObject("query").getJsonObject("ids").getJsonArray("values").size());
    }

    @Test
    void reindexByQueryDeletesByIdsNotByOriginalBroadQuery() {
        Query broadQuery = new Query();
        broadQuery.addBoolFilterTerm("meta.group", "g1");

        AtomicInteger searchCalls = new AtomicInteger(0);
        List<JsonObject> deleteBodies = new ArrayList<>();

        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            JsonObject body = invocation.getArgument(1);
            String path = uri.getPath();

            if ("/_reindex".equals(path)) {
                return _success(JsonObject.of("created", 2));
            }
            if (path != null && path.endsWith("/_search")) {
                if (searchCalls.getAndIncrement() == 0) {
                    return _success(_searchPage(0, 2));
                }
                return _success(_emptySearchPage(2));
            }
            if (path != null && path.endsWith("/_delete_by_query")) {
                deleteBodies.add(body);
                return _success(JsonObject.of("deleted", 2));
            }
            throw new IllegalStateException("Unexpected ES uri: " + uri);
        });

        Script script = new Script();
        script.setSource("ctx._source.idea.fqmn = ctx._source.idea.fqmn + '_new';");

        Map<String, String> fqmnMapping = new LinkedHashMap<>();
        docDao.reindexByQuery("demo", broadQuery, script, fqmnMapping, null);

        assertEquals(1, deleteBodies.size());
        JsonObject deleteBody = deleteBodies.get(0);
        JsonObject deleteQuery = deleteBody.getJsonObject("query");
        assertNotNull(deleteQuery.getJsonObject("ids"));
        assertNull(deleteQuery.getJsonObject("bool"));
        assertEquals("demo:mysql:main:n0", deleteQuery.getJsonObject("ids").getJsonArray("values").getString(0));
    }

    @Test
    void reindexByQueryRejectsAsyncExecutionMode() {
        PatchOptions patchOptions = new PatchOptions();
        patchOptions.setExecutionMode(PatchOptions.ExecutionMode.ASYNC);

        Script script = new Script();
        script.setSource("ctx._source.idea.fqmn = ctx._source.idea.fqmn + '_new';");

        MetaplusException ex = assertThrows(MetaplusException.class,
                () -> docDao.reindexByQuery("demo", new Query(), script, new LinkedHashMap<>(), patchOptions));

        assertEquals("DocDao.reindexByQuery failed for patchOptions: executionMode=ASYNC is not supported", ex.getMessage());
    }

    @Test
    void reindexByQueryRejectsCrossDomainMove() {
        AtomicInteger searchCalls = new AtomicInteger(0);

        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            String path = uri.getPath();
            if ("/_reindex".equals(path)) {
                return _success(JsonObject.of("created", 1));
            }
            if (path != null && path.endsWith("/_search")) {
                if (searchCalls.getAndIncrement() == 0) {
                    return _success(JsonObject.of(
                            "hits", JsonObject.of(
                                    "total", JsonObject.of("value", 1),
                                    "hits", JsonArray.of(JsonObject.of(
                                            "_id", "demo:mysql:main:n0",
                                            "_source", JsonObject.of("idea", JsonObject.of("fqmn", "other:mysql:main:n0")),
                                            "sort", JsonArray.of(0L)
                                    ))
                            )
                    ));
                }
                return _success(_emptySearchPage(1));
            }
            if (path != null && path.endsWith("/_delete_by_query")) {
                return _success(JsonObject.of("deleted", 1));
            }
            throw new IllegalStateException("Unexpected ES uri: " + uri);
        });

        Script script = new Script();
        script.setSource("ctx._source.idea.fqmn = 'other:mysql:main:n0';");

        MetaplusException ex = assertThrows(MetaplusException.class,
                () -> docDao.reindexByQuery("demo", new Query(), script, new LinkedHashMap<>(), null));

        assertEquals("DocDao.reindexByQuery failed for domain=demo, step=2: cross-domain move is not supported for demo:mysql:main:n0", ex.getMessage());
    }

    private static EsResponse _success(JsonObject body) {
        return new EsResponse(200, body);
    }

    private static JsonObject _searchPage(int start, int size) {
        JsonArray hits = new JsonArray();
        for (int i = start; i < start + size; i++) {
            String oldFqmn = "demo:mysql:main:n" + i;
            hits.add(JsonObject.of(
                    "_id", oldFqmn,
                    "_source", JsonObject.of("idea", JsonObject.of("fqmn", oldFqmn + "_new")),
                    "sort", JsonArray.of((long) i)
            ));
        }
        return JsonObject.of(
                "hits", JsonObject.of(
                        "total", JsonObject.of("value", 1005),
                        "hits", hits
                )
        );
    }

    private static JsonObject _emptySearchPage(int total) {
        return JsonObject.of(
                "hits", JsonObject.of(
                        "total", JsonObject.of("value", total),
                        "hits", JsonArray.of()
                )
        );
    }
}
