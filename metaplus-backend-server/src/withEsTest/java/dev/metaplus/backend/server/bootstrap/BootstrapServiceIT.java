package dev.metaplus.backend.server.bootstrap;

import dev.metaplus.backend.server.dao.DocDao;
import dev.metaplus.backend.server.dao.IndexDao;
import dev.metaplus.backend.server.domain.DomainStore;
import dev.metaplus.backend.server.domain.SchemaStore;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValueStore;
import dev.metaplus.backend.server.es.EsIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapServiceIT extends EsIntegrationTestSupport {

    private final String domainIndexName = StorageUtil.storageIndex(DomainStore.DOMAIN_DOMAIN);

    private DomainStore domainStore;
    private SchemaStore schemaStore;
    private ValueStore valueStore;
    private IndexDao indexDao;
    private DocDao docDao;
    private BootstrapService domainBootstrapService;

    @BeforeEach
    void setUpService() {
        domainStore = new DomainStore();
        schemaStore = new SchemaStore(domainStore);
        valueStore = new ValueStore(domainStore);
        indexDao = new IndexDao(esClient);
        docDao = new DocDao(esClient, valueStore, indexDao);
        domainBootstrapService = new BootstrapService(new BuiltInDomainCatalog(), esClient, indexDao, docDao,
                domainStore, schemaStore, valueStore);
    }

    @AfterEach
    void tearDownIndexes() {
        if (esClient != null) {
            deleteIndexIfExists(domainIndexName);
        }
    }

    @Test
    void bootstrapBuiltInsAndLoadDomainRegistry_isIdempotentAndWarmsStores() {
        deleteIndexIfExists(domainIndexName);

        BootstrapReport first = domainBootstrapService.bootstrapBuiltInsAndLoadDomainRegistry();
        BootstrapReport second = domainBootstrapService.bootstrapBuiltInsAndLoadDomainRegistry();

        assertTrue(first.isCreatedDomainIndex());
        assertEquals(2, first.getCreatedBuiltInDomains().size());
        assertEquals(0, first.getSkippedBuiltInDomains().size());
        assertTrue(indexDao.existIndex(domainIndexName));
        assertNotNull(domainStore.getDomainDoc("none"));
        assertNotNull(domainStore.getDomainDoc("domain"));
        assertNotNull(schemaStore.validateDomainSchema(domainStore.getDomainDocOrElseThrow("none")));
        assertFalse(valueStore.getDerivedAssignmentScriptOrElseThrow("domain").isBlank());

        assertFalse(second.isCreatedDomainIndex());
        assertEquals(0, second.getCreatedBuiltInDomains().size());
        assertEquals(2, second.getSkippedBuiltInDomains().size());
        assertEquals(2, second.getLoadedDomainCount());
    }
}
