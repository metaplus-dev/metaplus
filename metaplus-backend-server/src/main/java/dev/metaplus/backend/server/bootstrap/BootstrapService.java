package dev.metaplus.backend.server.bootstrap;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.dao.DocDao;
import dev.metaplus.backend.server.dao.IndexDao;
import dev.metaplus.backend.server.domain.DomainStore;
import dev.metaplus.backend.server.domain.SchemaStore;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValuesStore;
import dev.metaplus.core.model.DomainDoc;
import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.search.SearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BootstrapService {

    private final BuiltInDomainCatalog builtInDomainCatalog;
    private final EsClient esClient;
    private final IndexDao indexDao;
    private final DocDao docDao;
    private final DomainStore domainStore;
    private final SchemaStore schemaStore;
    private final ValuesStore valuesStore;

    public BootstrapReport bootstrapBuiltInsAndLoadDomainRegistry() {
        BootstrapReport report = new BootstrapReport();
        List<DomainDoc> builtInDomainDocs = builtInDomainCatalog.listBuiltInDomainDocs();
        DomainStore stagingDomainStore = _buildStagingDomainStore(builtInDomainDocs);
        String domainIndex = StorageUtil.storageIndex(DomainStore.DOMAIN_DOMAIN);

        if (!indexDao.existIndex(domainIndex)) {
            indexDao.createIndex(domainIndex, stagingDomainStore.getMergedPureStorage(DomainStore.DOMAIN_DOMAIN));
            report.markCreatedDomainIndex();
        }

        for (DomainDoc domainDoc : builtInDomainDocs) {
            if (docDao.exist(domainDoc.getIdeaFqmn(), null)) {
                report.addSkippedBuiltInDomain(domainDoc.getMetaDomainName());
                continue;
            }
            _indexDomainDoc(domainIndex, domainDoc);
            report.addCreatedBuiltInDomain(domainDoc.getMetaDomainName());
        }

        indexDao.refreshIndex(domainIndex);
        _loadDomainRegistry(report);
        return report;
    }

    public BootstrapReport verifyBuiltInsAndLoadDomainRegistry() {
        BootstrapReport report = new BootstrapReport();
        String domainIndex = StorageUtil.storageIndex(DomainStore.DOMAIN_DOMAIN);
        if (!indexDao.existIndex(domainIndex)) {
            throw new BackendServerException("DomainBootstrapService.verify failed for index=" + domainIndex
                    + ": bootstrap is required before server startup");
        }

        for (DomainDoc builtInDomainDoc : builtInDomainCatalog.listBuiltInDomainDocs()) {
            if (!docDao.exist(builtInDomainDoc.getIdeaFqmn(), null)) {
                throw new BackendServerException("DomainBootstrapService.verify failed for domain="
                        + builtInDomainDoc.getMetaDomainName() + ": built-in domain doc is missing");
            }
        }

        _loadDomainRegistry(report);
        return report;
    }

    private DomainStore _buildStagingDomainStore(List<DomainDoc> builtInDomainDocs) {
        DomainStore stagingDomainStore = new DomainStore();
        SchemaStore stagingSchemaStore = new SchemaStore(stagingDomainStore);
        ValuesStore stagingValuesStore = new ValuesStore(stagingDomainStore);
        for (DomainDoc domainDoc : builtInDomainDocs) {
            stagingDomainStore.putDomainDoc(domainDoc);
            stagingSchemaStore.putDomainDoc(domainDoc);
            stagingValuesStore.putDomainDoc(domainDoc);
        }
        return stagingDomainStore;
    }

    private void _indexDomainDoc(String index, DomainDoc domainDoc) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}").build(index, domainDoc.getIdeaFqmn());
        EsResponse response = esClient.put(uri, domainDoc);
        if (!response.isSuccess()) {
            throw new BackendServerException("DomainBootstrapService.bootstrap failed for fqmn="
                    + domainDoc.getIdeaFqmn() + ", status=" + response.getStatusCode()
                    + ", body=" + response.getBody());
        }
    }

    private void _loadDomainRegistry(BootstrapReport report) {
        _clearDomainCaches();

        int total = docDao.countByDomain(DomainStore.DOMAIN_DOMAIN, null);
        if (total <= 0) {
            throw new BackendServerException("DomainBootstrapService.load failed for domain=domain: no domain docs found");
        }

        SearchResponse<MetaplusDoc> response = docDao.readByDomain(DomainStore.DOMAIN_DOMAIN, total, null, null);
        if (response.getTotal() > response.getHitsSize()) {
            throw new BackendServerException("DomainBootstrapService.load failed for domain=domain: expected "
                    + response.getTotal() + " docs but only loaded " + response.getHitsSize());
        }

        for (MetaplusDoc doc : response.getSources()) {
            DomainDoc domainDoc = doc.bindNode(DomainDoc.class);
            domainStore.putDomainDoc(domainDoc);
            schemaStore.putDomainDoc(domainDoc);
            valuesStore.putDomainDoc(domainDoc);
        }
        report.setLoadedDomainCount(response.getHitsSize());
    }

    private void _clearDomainCaches() {
        domainStore.clear();
        schemaStore.clear();
        valuesStore.clear();
    }
}
