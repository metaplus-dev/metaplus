package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.domain.ValueStore;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.patch.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocDaoCreateTest {

    private EsClient esClient;
    private ValueStore valueStore;
    private DocDao docDao;

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        valueStore = mock(ValueStore.class);
        docDao = new DocDao(esClient, valueStore, mock(IndexDao.class));
    }

    @Test
    void createUsesCreateOnlyScriptedUpdate() {
        AtomicReference<URI> uriRef = new AtomicReference<>();
        AtomicReference<JsonObject> bodyRef = new AtomicReference<>();
        when(valueStore.composeScript("demo", null)).thenReturn("ctx._source.idea = params.idea;");
        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            uriRef.set(invocation.getArgument(0));
            bodyRef.set(invocation.getArgument(1));
            return new EsResponse(201, JsonObject.of("_id", "demo:mysql:main:orders", "result", "created"));
        });

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(Idea.of("demo:mysql:main:orders"));
        doc.setMeta(new JsonObject());
        doc.setPlus(new JsonObject());

        docDao.create(doc, null, null);

        assertNotNull(uriRef.get());
        assertEquals("/i_metaplus_domain_demo/_update/demo%3Amysql%3Amain%3Aorders", uriRef.get().toString());
        assertNotNull(bodyRef.get());
        assertEquals(true, bodyRef.get().getBoolean("scripted_upsert"));
        assertEquals("if (ctx.op != 'create') { ctx.op = 'none'; return; }ctx._source.idea = params.idea;",
                bodyRef.get().getJsonObject("script").getString("source"));
    }

    @Test
    void createUsesNormalizedFailureMessage() {
        when(valueStore.composeScript("demo", null)).thenReturn("ctx._source.idea = params.idea;");
        when(esClient.post(any(URI.class), any(JsonObject.class)))
                .thenReturn(new EsResponse(409, JsonObject.of("error", "version_conflict_engine_exception")));

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(Idea.of("demo:mysql:main:orders"));
        doc.setMeta(new JsonObject());
        doc.setPlus(new JsonObject());

        BackendServerException ex = assertThrows(BackendServerException.class, () -> docDao.create(doc, null, null));

        assertEquals("DocDao.create failed for fqmn=demo:mysql:main:orders, status=409, body=J{error=version_conflict_engine_exception}",
                ex.getMessage());
    }

    @Test
    void createReturnsNoopWhenDocumentAlreadyExists() {
        when(valueStore.composeScript("demo", null)).thenReturn("ctx._source.idea = params.idea;");
        when(esClient.post(any(URI.class), any(JsonObject.class)))
                .thenReturn(new EsResponse(200, JsonObject.of("_id", "demo:mysql:main:orders", "result", "noop")));

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(Idea.of("demo:mysql:main:orders"));
        doc.setMeta(new JsonObject());
        doc.setPlus(new JsonObject());

        Result result = docDao.create(doc, null, null);

        assertEquals(1, result.getNoops());
        assertEquals(0, result.getCreated());
    }
}
