package dev.metaplus.backend.server.doc;

import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.es.EsIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexDaoIT extends EsIntegrationTestSupport {

    private final List<String> indexesToDelete = new ArrayList<>();

    private IndexDao indexDao;

    @BeforeEach
    void setUpDao() {
        indexDao = new IndexDao();
        ReflectionTestUtils.setField(indexDao, "esClient", esClient);
    }

    @AfterEach
    void tearDownIndexes() {
        for (String indexName : indexesToDelete) {
            deleteIndexIfExists(indexName);
        }
        indexesToDelete.clear();
    }

    @Test
    void createReadExistAndDeleteRoundTripAgainstRealEs() {
        String indexName = newIndexName();

        indexDao.createIndex(indexName, JsonObject.of(
                "settings", JsonObject.of(
                        "index", JsonObject.of(
                                "number_of_shards", 1,
                                "number_of_replicas", 0
                        )
                ),
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "name", JsonObject.of("type", "keyword")
                        )
                )
        ));

        assertTrue(indexDao.existIndex(indexName));

        JsonObject body = indexDao.readIndex(indexName);
        JsonObject indexBody = body.getJsonObject(indexName);
        assertNotNull(indexBody);
        assertEquals("keyword", indexBody.getJsonObject("mappings")
                .getJsonObject("properties")
                .getJsonObject("name")
                .getString("type"));

        indexDao.deleteIndex(indexName);
        indexesToDelete.remove(indexName);

        assertFalse(indexDao.existIndex(indexName));
    }

    @Test
    void existIndexReturnsFalseForMissingIndex() {
        assertFalse(indexDao.existIndex(newIndexName()));
    }

    @Test
    void existIndexThrowsForInvalidIndexName() {
        assertThrows(BackendServerException.class,
                () -> indexDao.existIndex("_bad"));
    }

    @Test
    void readIndexThrowsForMissingIndex() {
        assertThrows(BackendServerException.class,
                () -> indexDao.readIndex(newIndexName()));
    }

    @Test
    void updateSettingsAppliesSettingsToExistingIndex() {
        String indexName = createIndex();

        indexDao.updateSettings(indexName, JsonObject.of(
                "index", JsonObject.of(
                        "refresh_interval", "5s"
                )
        ));

        JsonObject indexBody = indexDao.readIndex(indexName).getJsonObject(indexName);
        assertEquals("5s", indexBody.getJsonObject("settings")
                .getJsonObject("index")
                .getString("refresh_interval"));
    }

    @Test
    void updateMappingsAddsFieldsToExistingIndex() {
        String indexName = createIndex();

        indexDao.updateMappings(indexName, JsonObject.of(
                "properties", JsonObject.of(
                        "description", JsonObject.of("type", "text")
                )
        ));

        JsonObject indexBody = indexDao.readIndex(indexName).getJsonObject(indexName);
        assertEquals("text", indexBody.getJsonObject("mappings")
                .getJsonObject("properties")
                .getJsonObject("description")
                .getString("type"));
    }

    @Test
    void deleteIndexIgnoresMissingIndex() {
        assertDoesNotThrow(() -> indexDao.deleteIndex(newIndexName()));
    }

    @Test
    void deleteIndexThrowsForInvalidIndexName() {
        assertThrows(BackendServerException.class,
                () -> indexDao.deleteIndex("_bad"));
    }

    @Test
    void statsIndexReturnsIndexedDocumentCounts() {
        String indexName = createIndex();
        indexDocument(indexName, "doc-1", JsonObject.of("name", "orders"));
        refreshIndex(indexName);

        JsonObject stats = indexDao.statsIndex(indexName);
        Object count = stats.getJsonObject("indices")
                .getJsonObject(indexName)
                .getJsonObject("primaries")
                .getJsonObject("docs")
                .get("count");

        assertEquals("1", String.valueOf(count));
    }

    private String createIndex() {
        String indexName = newIndexName();
        indexDao.createIndex(indexName, JsonObject.of(
                "settings", JsonObject.of(
                        "index", JsonObject.of(
                                "number_of_shards", 1,
                                "number_of_replicas", 0
                        )
                ),
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "name", JsonObject.of("type", "keyword")
                        )
                )
        ));
        return indexName;
    }

    private String newIndexName() {
        String indexName = uniqueIndexName("i_metaplus_indexdao");
        indexesToDelete.add(indexName);
        return indexName;
    }
}
