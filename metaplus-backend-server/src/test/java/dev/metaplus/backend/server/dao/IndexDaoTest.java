package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexDaoTest {

    private EsClient esClient;
    private IndexDao indexDao;

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        indexDao = new IndexDao();
        ReflectionTestUtils.setField(indexDao, "esClient", esClient);
    }

    @Test
    void createIndexUsesNormalizedFailureMessage() {
        when(esClient.put(any(URI.class), any(JsonObject.class)))
                .thenReturn(new EsResponse(500, JsonObject.of("error", "boom")));

        BackendServerException ex = assertThrows(BackendServerException.class,
                () -> indexDao.createIndex("i_demo", JsonObject.of()));

        assertEquals("IndexDao.createIndex failed: target=index=i_demo, status=500, body=J{error=boom}",
                ex.getMessage());
    }

    @Test
    void existIndexUsesNormalizedFailureMessage() {
        when(esClient.head(any(URI.class)))
                .thenReturn(new EsResponse(400, JsonObject.of("error", "bad_request")));

        BackendServerException ex = assertThrows(BackendServerException.class,
                () -> indexDao.existIndex("_bad"));

        assertEquals("IndexDao.existIndex failed: target=index=_bad, status=400, body=J{error=bad_request}",
                ex.getMessage());
    }
}
