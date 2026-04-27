package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.domain.ValuesStore;
import dev.metaplus.core.exception.MetaplusException;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.patch.PatchOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocDaoReindexTest {

    private EsClient esClient;
    private ValuesStore valuesStore;
    private DocDao docDao;

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        valuesStore = mock(ValuesStore.class);
        IndexDao indexDao = mock(IndexDao.class);

        when(valuesStore.composeScript(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(indexDao.existIndex(anyString())).thenReturn(true);
        doNothing().when(indexDao).createIndex(anyString(), any(JsonObject.class));

        docDao = new DocDao();
        ReflectionTestUtils.setField(docDao, "esClient", esClient);
        ReflectionTestUtils.setField(docDao, "valuesStore", valuesStore);
        ReflectionTestUtils.setField(docDao, "indexDao", indexDao);
    }

    @Test
    void reindexDeletesOriginalOnlyAfterSuccessfulReindexBack() {
        String oldFqmn = "old:mysql:main:t1";
        String newFqmn = "new:mysql:main:t1";

        AtomicInteger reindexCall = new AtomicInteger(0);
        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (!"/_reindex".equals(uri.getPath())) {
                throw new IllegalStateException("Unexpected uri: " + uri);
            }
            if (reindexCall.incrementAndGet() == 1) {
                return success(JsonObject.of("created", 1));
            }
            return new EsResponse(500, JsonObject.of("error", "boom"));
        });
        when(esClient.get(any(URI.class))).thenReturn(success(JsonObject.of(
                "_source", JsonObject.of("idea", JsonObject.of("fqmn", newFqmn))
        )));

        MetaplusException ex = assertThrows(MetaplusException.class,
                () -> docDao.reindex(oldFqmn, doc(newFqmn), "ctx._source.x = 1;", null));

        assertTrue(ex.getMessage().contains("DocDao.reindex failed: target=fqmn=old:mysql:main:t1, step=3, status=500"));
        verify(esClient, never()).delete(any(URI.class));
    }

    @Test
    void reindexRejectsAsyncExecutionMode() {
        PatchOptions patchOptions = new PatchOptions();
        patchOptions.setExecutionMode(PatchOptions.ExecutionMode.ASYNC);

        MetaplusException ex = assertThrows(MetaplusException.class,
                () -> docDao.reindex("old:mysql:main:t1", doc("new:mysql:main:t1"), "ctx._source.x = 1;", patchOptions));

        assertEquals("DocDao.reindex failed: target=patchOptions, reason=executionMode=ASYNC is not supported", ex.getMessage());
        verify(esClient, never()).post(any(URI.class), any(JsonObject.class));
    }

    @Test
    void reindexUsesOldDomainAsSourceAndNewDomainAsDestination() {
        String oldFqmn = "old:mysql:main:t1";
        String newFqmn = "new:mysql:main:t1";
        List<JsonObject> reindexBodies = new ArrayList<>();

        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            JsonObject body = invocation.getArgument(1);
            if (!"/_reindex".equals(uri.getPath())) {
                throw new IllegalStateException("Unexpected uri: " + uri);
            }
            reindexBodies.add(body);
            return success(JsonObject.of("created", 1));
        });
        when(esClient.get(any(URI.class))).thenReturn(success(JsonObject.of(
                "_source", JsonObject.of("idea", JsonObject.of("fqmn", newFqmn))
        )));
        when(esClient.delete(any(URI.class))).thenReturn(success(JsonObject.of("result", "deleted")));

        docDao.reindex(oldFqmn, doc(newFqmn), "ctx._source.x = 1;", null);

        assertEquals(2, reindexBodies.size());
        assertEquals("i_metaplus_domain_old", reindexBodies.get(0).getJsonObject("source").getString("index"));
        assertEquals("i_metaplus_domain_new", reindexBodies.get(1).getJsonObject("dest").getString("index"));
        verify(valuesStore).composeScript(eq("new"), anyString());
    }

    @Test
    void reindexRejectsUnchangedTransformedFqmn() {
        String fqmn = "old:mysql:main:t1";

        when(esClient.post(any(URI.class), any(JsonObject.class))).thenReturn(success(JsonObject.of("created", 1)));
        when(esClient.get(any(URI.class))).thenReturn(success(JsonObject.of(
                "_source", JsonObject.of("idea", JsonObject.of("fqmn", fqmn))
        )));

        MetaplusException ex = assertThrows(MetaplusException.class,
                () -> docDao.reindex(fqmn, doc("new:mysql:main:t1"), "ctx._source.x = 1;", null));

        assertEquals("DocDao.reindex failed: target=fqmn=old:mysql:main:t1, step=2, reason=transformed fqmn is unchanged", ex.getMessage());
        verify(esClient, never()).delete(any(URI.class));
    }

    @Test
    void reindexRejectsTransformedDomainMismatch() {
        String fqmn = "old:mysql:main:t1";

        when(esClient.post(any(URI.class), any(JsonObject.class))).thenReturn(success(JsonObject.of("created", 1)));
        when(esClient.get(any(URI.class))).thenReturn(success(JsonObject.of(
                "_source", JsonObject.of("idea", JsonObject.of("fqmn", "other:mysql:main:t1"))
        )));

        MetaplusException ex = assertThrows(MetaplusException.class,
                () -> docDao.reindex(fqmn, doc("new:mysql:main:t1"), "ctx._source.x = 1;", null));

        assertEquals("DocDao.reindex failed: target=fqmn=old:mysql:main:t1, step=2, reason=transformed domain does not match target doc domain", ex.getMessage());
        verify(esClient, never()).delete(any(URI.class));
    }

    private static MetaplusDoc doc(String fqmn) {
        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(Idea.of(fqmn));
        return doc;
    }

    private static EsResponse success(JsonObject body) {
        return new EsResponse(200, body);
    }
}
