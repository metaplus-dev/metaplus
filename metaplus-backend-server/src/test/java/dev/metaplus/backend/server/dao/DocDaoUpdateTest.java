package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.dao.IndexDao;
import dev.metaplus.backend.server.domain.ValuesStore;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.MetaplusDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocDaoUpdateTest {

    private EsClient esClient;
    private ValuesStore valuesStore;
    private DocDao docDao;

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        valuesStore = mock(ValuesStore.class);
        docDao = new DocDao(esClient, valuesStore, mock(IndexDao.class));
    }

    @Test
    void updateUsesUpdateEndpointWithoutUpsert() {
        AtomicReference<URI> uriRef = new AtomicReference<>();
        AtomicReference<JsonObject> bodyRef = new AtomicReference<>();
        when(valuesStore.composeScript("demo", null)).thenReturn("ctx._source.idea = params.idea;");
        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            uriRef.set(invocation.getArgument(0));
            bodyRef.set(invocation.getArgument(1));
            return new EsResponse(200, JsonObject.of("_id", "demo:mysql:main:orders", "result", "updated"));
        });

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(Idea.of("demo:mysql:main:orders"));
        doc.setMeta(new JsonObject());
        doc.setPlus(new JsonObject());

        docDao.update(doc, null, null);

        assertNotNull(uriRef.get());
        assertEquals("/i_metaplus_domain_demo/_update/demo%3Amysql%3Amain%3Aorders", uriRef.get().toString());
        assertNotNull(bodyRef.get());
        assertNull(bodyRef.get().get("upsert"));
        assertNull(bodyRef.get().get("scripted_upsert"));
        assertEquals("ctx._source.idea = params.idea;", bodyRef.get().getJsonObject("script").getString("source"));
    }

    @Test
    void updateUsesNormalizedFailureMessage() {
        when(valuesStore.composeScript("demo", null)).thenReturn("ctx._source.idea = params.idea;");
        when(esClient.post(any(URI.class), any(JsonObject.class)))
                .thenReturn(new EsResponse(404, JsonObject.of("error", "document_missing_exception")));

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(Idea.of("demo:mysql:main:orders"));
        doc.setMeta(new JsonObject());
        doc.setPlus(new JsonObject());

        BackendServerException ex = assertThrows(BackendServerException.class, () -> docDao.update(doc, null, null));

        assertEquals("DocDao.update failed: target=fqmn=demo:mysql:main:orders, status=404, body=J{error=document_missing_exception}",
                ex.getMessage());
    }
}
