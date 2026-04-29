package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.domain.ValuesStore;
import dev.metaplus.core.model.patch.Script;
import dev.metaplus.core.model.search.SearchOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrashDaoTest {

    private EsClient esClient;
    private TrashDao trashDao;

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        trashDao = new TrashDao();
        ReflectionTestUtils.setField(trashDao, "esClient", esClient);
    }

    @Test
    void copyUsesNormalizedFailureMessage() {
        when(esClient.post(any(URI.class), any(JsonObject.class)))
                .thenReturn(new EsResponse(500, JsonObject.of("error", "boom")));

        Script script = new Script();
        script.setSource("ctx._source.meta = params.meta");

        BackendServerException ex = assertThrows(BackendServerException.class,
                () -> trashDao.copy("data:mysql:main:orders", script, null));

        assertEquals("TrashDao.copy failed for fqmn=data:mysql:main:orders, status=500, body=J{error=boom}",
                ex.getMessage());
    }

    @Test
    void copyComposesReindexScriptWithoutMutatingCallerInput() {
        AtomicReference<JsonObject> bodyRef = new AtomicReference<>();
        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            bodyRef.set(invocation.getArgument(1));
            return new EsResponse(200, JsonObject.of("created", 1));
        });

        Script script = new Script();
        script.setSource("ctx._source.meta.flag = true");

        trashDao.copy("data:mysql:main:orders", script, null);

        assertNotNull(bodyRef.get());
        JsonObject sentScript = bodyRef.get().getJsonObject("script");
        assertNotNull(sentScript);
        assertTrue(sentScript.getString("source").startsWith(ValuesStore.SCRIPT_BEFORE_ALL_IN_ONE));
        assertTrue(sentScript.getString("source").endsWith("ctx._id = null;"));
        assertEquals("ctx._source.meta.flag = true", script.getSource());
    }

    @Test
    void readMultiShapesSearchQueryAndRouting() {
        AtomicReference<URI> uriRef = new AtomicReference<>();
        AtomicReference<JsonObject> bodyRef = new AtomicReference<>();
        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            uriRef.set(invocation.getArgument(0));
            bodyRef.set(invocation.getArgument(1));
            return new EsResponse(200, JsonObject.of(
                    "hits", JsonObject.of(
                            "total", JsonObject.of("value", 0),
                            "hits", JsonArray.of())));
        });

        SearchOptions options = new SearchOptions();
        options.setRouteKey("tenant-1");

        trashDao.readMulti("data:mysql:main:orders", 5, options);

        assertEquals("/i_metaplus_domain_trash/_search?version=true&size=5&routing=tenant-1", uriRef.get().toString());
        assertEquals("data:mysql:main:orders",
                bodyRef.get().getJsonObject("query").getJsonObject("term").getString("idea.fqmn"));
        assertEquals("desc",
                bodyRef.get().getJsonArray("sort").getJsonObject(0)
                        .getJsonObject("edit.meta.deletedAt").getString("order"));
    }
}
