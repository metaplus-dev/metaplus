package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValuesStore;
import dev.metaplus.backend.server.es.EsIntegrationTestSupport;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.patch.Script;
import dev.metaplus.core.model.search.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocDaoReindexByQueryIT extends EsIntegrationTestSupport {

    private IndexDao indexDao;
    private DocDao docDao;
    private String indexName;

    @BeforeEach
    void setUpDao() {
        indexDao = new IndexDao();
        ReflectionTestUtils.setField(indexDao, "esClient", esClient);

        ValuesStore valuesStore = mock(ValuesStore.class);
        when(valuesStore.composeScript(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        docDao = new DocDao();
        ReflectionTestUtils.setField(docDao, "esClient", esClient);
        ReflectionTestUtils.setField(docDao, "valuesStore", valuesStore);
        ReflectionTestUtils.setField(docDao, "indexDao", indexDao);
    }

    @AfterEach
    void tearDownIndex() {
        if (indexName != null) {
            deleteIndexIfExists(indexName);
        }
    }

    @Test
    void reindexByQueryPagesAllDocsAndKeepsReindexedDocs() {
        String domain = "docdao_reindex_batch";
        indexName = StorageUtil.getDomainIndex(domain);
        createDomainIndex(indexName);

        int totalDocs = 1005;
        for (int i = 0; i < totalDocs; i++) {
            String oldFqmn = fqmn(domain, "d" + i);
            indexDocument(indexName, oldFqmn, JsonObject.of(
                    "idea", JsonObject.of("fqmn", oldFqmn, "domain", domain),
                    "meta", JsonObject.of("group", "all"),
                    "plus", JsonObject.of()
            ));
        }
        refreshIndex(indexName);

        Script script = new Script();
        script.setSource("ctx._source.idea.fqmn = ctx._source.idea.fqmn + '_new';");

        Map<String, String> fqmnMapping = new LinkedHashMap<>();
        Result result = docDao.reindexByQuery(domain, new Query(), script, fqmnMapping, null);

        refreshIndex(indexName);

        assertEquals(totalDocs, result.getTotal());
        assertEquals(totalDocs, fqmnMapping.size());
        assertEquals(totalDocs, docDao.countByDomain(domain, null));

        String sampleOld = fqmn(domain, "d0");
        String sampleNew = sampleOld + "_new";
        assertTrue(docDao.exist(sampleNew, null));
        assertFalse(docDao.exist(sampleOld, null));
    }

    @Test
    void reindexByQueryDeletesOnlyCapturedOriginalIds() {
        String domain = "docdao_reindex_delete";
        indexName = StorageUtil.getDomainIndex(domain);
        createDomainIndex(indexName);

        String oldA = fqmn(domain, "a");
        String oldB = fqmn(domain, "b");
        String untouched = fqmn(domain, "c");

        indexDocument(indexName, oldA, JsonObject.of(
                "idea", JsonObject.of("fqmn", oldA, "domain", domain),
                "meta", JsonObject.of("group", "g1"),
                "plus", JsonObject.of()));
        indexDocument(indexName, oldB, JsonObject.of(
                "idea", JsonObject.of("fqmn", oldB, "domain", domain),
                "meta", JsonObject.of("group", "g1"),
                "plus", JsonObject.of()));
        indexDocument(indexName, untouched, JsonObject.of(
                "idea", JsonObject.of("fqmn", untouched, "domain", domain),
                "meta", JsonObject.of("group", "g2"),
                "plus", JsonObject.of()));
        refreshIndex(indexName);

        Query query = new Query();
        query.addBoolFilterTerm("meta.group", "g1");
        Script script = new Script();
        script.setSource("ctx._source.idea.fqmn = ctx._source.idea.fqmn + '_renamed';");

        Map<String, String> fqmnMapping = new LinkedHashMap<>();
        docDao.reindexByQuery(domain, query, script, fqmnMapping, null);

        refreshIndex(indexName);

        assertEquals(2, fqmnMapping.size());
        assertTrue(docDao.exist(oldA + "_renamed", null));
        assertTrue(docDao.exist(oldB + "_renamed", null));
        assertFalse(docDao.exist(oldA, null));
        assertFalse(docDao.exist(oldB, null));
        assertTrue(docDao.exist(untouched, null));
        assertEquals(3, docDao.countByDomain(domain, null));
    }

    private void createDomainIndex(String targetIndexName) {
        indexDao.createIndex(targetIndexName, JsonObject.of(
                "settings", JsonObject.of(
                        "index", JsonObject.of(
                                "number_of_shards", 1,
                                "number_of_replicas", 0
                        )
                ),
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "idea", JsonObject.of(
                                        "properties", JsonObject.of(
                                                "fqmn", JsonObject.of("type", "keyword"),
                                                "domain", JsonObject.of("type", "keyword")
                                        )
                                ),
                                "meta", JsonObject.of(
                                        "properties", JsonObject.of(
                                                "group", JsonObject.of("type", "keyword")
                                        )
                                )
                        )
                )
        ));
    }

    private String fqmn(String domain, String name) {
        return domain + ":mysql:main:" + name;
    }
}
