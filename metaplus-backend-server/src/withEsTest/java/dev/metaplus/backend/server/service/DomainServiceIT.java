package dev.metaplus.backend.server.service;

import dev.metaplus.backend.server.dao.DocDao;
import dev.metaplus.backend.server.dao.IndexDao;
import dev.metaplus.backend.server.domain.DomainStore;
import dev.metaplus.backend.server.domain.SchemaStore;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValuesStore;
import dev.metaplus.backend.server.es.EsIntegrationTestSupport;
import dev.metaplus.core.json.Jsons;
import dev.metaplus.core.model.DomainDoc;
import dev.metaplus.core.model.Idea;
import org.sjf4j.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainServiceIT extends EsIntegrationTestSupport {

    private static final String DOMAIN_NONE_RESOURCE = "domains/domain_none.json";
    private static final String DOMAIN_DOMAIN_RESOURCE = "domains/domain_domain.json";

    private final String domainIndexName = StorageUtil.storageIndex(DomainStore.DOMAIN_DOMAIN);

    private DomainStore domainStore;
    private SchemaStore schemaStore;
    private ValuesStore valuesStore;
    private IndexDao indexDao;
    private DocDao docDao;
    private DomainService domainService;

    @BeforeEach
    void setUpService() {
        domainStore = new DomainStore();
        schemaStore = new SchemaStore(domainStore);
        valuesStore = new ValuesStore(domainStore);

        indexDao = new IndexDao(esClient);

        docDao = new DocDao(esClient, valuesStore, indexDao);

        domainService = new DomainService(docDao, indexDao, domainStore, schemaStore, valuesStore,
                new PrivilegeService());

        _bootstrapSystemDomains();
    }

    @AfterEach
    void tearDownIndexes() {
        if (esClient != null) {
            deleteIndexIfExists(domainIndexName);
        }
    }

    @Test
    void readDomain_fallsBackToEsAndWarmsStores() {
        String customDomainName = "orders_it_read";
        DomainDoc customDomainDoc = _newCustomDomainDoc(customDomainName);
        indexDocument(domainIndexName, customDomainDoc.getIdeaFqmn(), customDomainDoc);
        refreshIndex(domainIndexName);

        assertNull(domainStore.getDomainDoc(customDomainName));

        DomainDoc loaded = domainService.readDomain(customDomainName);

        assertNotNull(loaded);
        assertEquals(customDomainName, loaded.getMetaDomainName());
        assertEquals("domain:metaplus:main:" + customDomainName, loaded.getIdeaFqmn());
        assertNotNull(domainStore.getDomainDoc(customDomainName));
        assertTrue(valuesStore.getDerivedAssignmentScriptOrElseThrow(customDomainName)
                .contains("putByPath(ctx._source, 'idea.fqmn'"));
        assertNotNull(docDao.read("domain:metaplus:main:" + customDomainName, null));
    }

    private void _bootstrapSystemDomains() {
        DomainDoc noneDomainDoc = _readDomainDocResource(DOMAIN_NONE_RESOURCE);
        DomainDoc domainDomainDoc = _readDomainDocResource(DOMAIN_DOMAIN_RESOURCE);

        domainStore.putDomainDoc(noneDomainDoc);
        schemaStore.putDomainDoc(noneDomainDoc);
        valuesStore.putDomainDoc(noneDomainDoc);

        domainStore.putDomainDoc(domainDomainDoc);
        schemaStore.putDomainDoc(domainDomainDoc);
        valuesStore.putDomainDoc(domainDomainDoc);

        deleteIndexIfExists(domainIndexName);
        indexDao.createIndex(domainIndexName, StorageUtil.pureStorage(domainStore.getMergedStorage(DomainStore.DOMAIN_DOMAIN)));
        indexDocument(domainIndexName, noneDomainDoc.getIdeaFqmn(), noneDomainDoc);
        indexDocument(domainIndexName, domainDomainDoc.getIdeaFqmn(), domainDomainDoc);
        refreshIndex(domainIndexName);
    }

    private DomainDoc _readDomainDocResource(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing resource '" + resourcePath + "'.");
            }
            return Jsons.fromJson(inputStream, DomainDoc.class);
        } catch (Exception e) {
            throw new IllegalStateException("Read resource '" + resourcePath + "' failed.", e);
        }
    }

    private DomainDoc _newCustomDomainDoc(String domainName) {
        DomainDoc domainDoc = new DomainDoc();
        domainDoc.setIdea(Idea.of("domain:metaplus:main:" + domainName));
        domainDoc.setMetaDomainName(domainName);
        domainDoc.setMetaDomainTemplate("none");
        domainDoc.setMetaDomainDesc("Integration test domain");
        domainDoc.setMetaStorageMappings(domainStore.getDomainDocOrElseThrow("none").getMetaStorageMappings().deepCopy());
        domainDoc.setMetaSchema(domainStore.getDomainDocOrElseThrow("none").getMetaSchema().deepCopy());
        domainDoc.setPlus(new JsonObject());
        return domainDoc;
    }
}
