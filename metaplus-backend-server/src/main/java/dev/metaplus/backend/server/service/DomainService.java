package dev.metaplus.backend.server.service;

import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.dao.DocDao;
import dev.metaplus.backend.server.dao.IndexDao;
import dev.metaplus.backend.server.domain.DomainStore;
import dev.metaplus.backend.server.domain.SchemaStore;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValueStore;
import dev.metaplus.core.model.DomainDoc;
import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.patch.PatchOptions;
import dev.metaplus.core.model.patch.PatchRequest;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.search.SearchResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonObject;
import org.springframework.stereotype.Service;

import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class DomainService {

    private final DocDao docDao;
    private final IndexDao indexDao;
    private final DomainStore domainStore;
    private final SchemaStore schemaStore;
    private final ValueStore valueStore;
    private final PrivilegeService privilegeService;


    /**
     * Return all persisted domain docs.
     */
    public SearchResponse<MetaplusDoc> getAllDomains(int size) {
        return docDao.readByDomain(DomainStore.DOMAIN_DOMAIN, size, null, null);
    }

    /**
     * Read one domain, preferring the in-memory cache.
     */
    public DomainDoc readDomain(String domainName) {
        DomainDoc domainDoc = domainStore.getDomainDoc(domainName);
        if (domainDoc != null) {
            return domainDoc;
        }
        return readDomainDirectly(domainName, null);
    }

    /**
     * Read one domain directly from storage and warm caches.
     */
    public DomainDoc readDomainDirectly(String domainName, PatchOptions patchOptions) {
        MetaplusDoc persistedDoc = docDao.read("domain:metaplus:main:" + domainName, patchOptions);
        if (persistedDoc == null) {
            return null;
        }

        DomainDoc domainDoc = persistedDoc.bindNode(DomainDoc.class);
        domainStore.putDomainDoc(domainDoc);
        schemaStore.putDomainDoc(domainDoc);
        valueStore.putDomainDoc(domainDoc);
        return domainDoc;
    }

    /**
     * Check whether one domain exists in the in-memory cache.
     */
    public boolean existDomain(String domainName) {
        return domainStore.existDomain(domainName);
    }

    /**
     * Return all custom concrete domains.
     */
    public Set<String> customDomainSet() {
        return domainStore.customDomainSet();
    }


    /**
     * Create one domain and its backing index if needed.
     */
    public Result createDomain(@NonNull PatchRequest patch, PatchOptions patchOptions) {
        DomainDoc domainDoc = patch.getDoc().bindNode(DomainDoc.class);
        String domain = domainDoc.getMetaDomainName();

        // 1. validate
        if (!DomainStore.DOMAIN_DOMAIN.equals(domain)) {
            throw new IllegalArgumentException("DomainService.createDomain failed for domain=" + domain
                    + ": idea.domain must be 'domain'");
        }
        if (domainStore.existDomain(domain)) {
            throw new BackendServerException("DomainService.createDomain failed for domain=" + domain
                    + ": already exists");
        }
        schemaStore.validateDoc(domainDoc);
        schemaStore.validateDomainSchema(domainDoc);

        // 2. privilege
        privilegeService.checkPrivilege();

        boolean createdIndex = false;

        // 3. index
        String index = StorageUtil.storageIndex(domain);
        if (!domainDoc.isMetaDomainAbstract()) {
            if (!indexDao.existIndex(index)) {
                JsonObject storage = domainStore.getMergedStorage(domainDoc);
                JsonObject pureStorage = StorageUtil.pureStorage(storage);
                log.info("domain:{}, storage: {}", domain, storage);
                indexDao.createIndex(index, pureStorage);
                createdIndex = true;
            }
        }

        // 4. create
        domainDoc.setEditMetaCreatedAt(patch.getPatchedAt());
        domainDoc.setEditMetaCreatedBy(patch.getPatchedBy());
        Result result;
        try {
            result = docDao.create(domainDoc, null, patchOptions);
        } catch (RuntimeException e) {
            if (createdIndex) {
                indexDao.deleteIndex(index);
            }
            throw e;
        }
        if (result.getNoops() > 0) {
            throw new BackendServerException("DomainService.createDomain failed for domain=" + domain
                    + ": already exists");
        }

        // 5. put stores
        MetaplusDoc persistedDoc = docDao.read(domainDoc.getIdeaFqmn(), null);
        if (persistedDoc == null) {
            throw new BackendServerException("DomainService.createDomain failed for domain=" + domain
                    + ": created domain doc cannot be read back");
        }
        DomainDoc persistedDomainDoc = persistedDoc.bindNode(DomainDoc.class);
        domainStore.putDomainDoc(persistedDomainDoc);
        schemaStore.putDomainDoc(persistedDomainDoc);
        valueStore.putDomainDoc(persistedDomainDoc);
        return result;
    }


    /**
     * Update one domain and refresh related caches.
     */
    public Result updateDomain(@NonNull PatchRequest patch, PatchOptions patchOptions) {
        DomainDoc domainDoc = patch.getDoc().bindNode(DomainDoc.class);
        String domain = domainDoc.getMetaDomainName();

        // 1. validate
        if (!DomainStore.DOMAIN_DOMAIN.equals(domainDoc.getIdeaDomain())) {
            throw new IllegalArgumentException("DomainService.updateDomain failed for domain=" + domain
                    + ": idea.domain must be 'domain'");
        }
        if (!domainStore.existDomain(domain)) {
            throw new BackendServerException("DomainService.updateDomain failed for domain=" + domain
                    + ": does not exist");
        }
        schemaStore.validateDoc(domainDoc);
        schemaStore.validateDomainSchema(domainDoc);

        // 2. privilege
        privilegeService.checkPrivilege();

        // 3. index
        String index = StorageUtil.storageIndex(domain);
        if (!domainDoc.isMetaDomainAbstract()) {
            JsonObject storage = domainStore.getMergedStorage(domainDoc);
            JsonObject pureStorage = StorageUtil.pureStorage(storage);
            JsonObject mappings = pureStorage.getJsonObject("mappings");
            JsonObject settings = pureStorage.getJsonObject("settings");
            if (indexDao.existIndex(index)) {
                if (mappings != null && !mappings.isEmpty()) {
                    indexDao.updateMappings(index, mappings);
                }
                if (settings != null && !settings.isEmpty()) {
                    indexDao.updateSettings(index, settings);
                }
            } else {
                log.info("domain:{}, storage: {}", domain, storage);
                indexDao.createIndex(index, pureStorage);
            }
        }

        // 4. update
        domainDoc.setEditMetaUpdatedAt(patch.getPatchedAt());
        domainDoc.setEditMetaUpdatedBy(patch.getPatchedBy());
        Result result = docDao.update(domainDoc, null, patchOptions);

        // 5. put stores
        MetaplusDoc persistedDoc = docDao.read(domainDoc.getIdeaFqmn(), null);
        if (persistedDoc == null) {
            throw new BackendServerException("DomainService.updateDomain failed for domain=" + domain
                    + ": updated domain doc cannot be read back");
        }
        DomainDoc persistedDomainDoc = persistedDoc.bindNode(DomainDoc.class);
        domainStore.putDomainDoc(persistedDomainDoc);
        schemaStore.putDomainDoc(persistedDomainDoc);
        valueStore.putDomainDoc(persistedDomainDoc);
        return result;
    }

    /**
     * Delete one domain and its backing index when allowed.
     */
    public Result deleteDomain(@NonNull String domainName, boolean force, PatchOptions patchOptions) {
        // 1. validate
        if (!domainStore.existDomain(domainName)) {
            throw new BackendServerException("DomainService.deleteDomain failed for domain=" + domainName
                    + ": does not exist");
        }

        DomainDoc domainDoc = domainStore.getDomainDocOrElseThrow(domainName);
        if (domainDoc.isMetaDomainSystem()) {
            throw new BackendServerException("DomainService.deleteDomain failed for domain=" + domainName
                    + ": system domain can not be deleted");
        }

        // 2. privilege
        privilegeService.checkPrivilege();

        // 3. index
        if (!domainDoc.isMetaDomainAbstract()) {
            String index = StorageUtil.storageIndex(domainName);
            if (indexDao.existIndex(index)) {
                if (!force) {
                    int docCount = docDao.countByDomain(domainName, null);
                    if (docCount > 0) {
                        throw new BackendServerException("DomainService.deleteDomain failed for domain=" + domainName
                                + ": domain has " + docCount + " docs");
                    }
                }
                indexDao.deleteIndex(index);
            }
        }

        // 4. delete doc
        Result result = docDao.delete(domainDoc.getIdeaFqmn(), patchOptions);

        // 5. delete stores
        domainStore.deleteDomain(domainName);
        schemaStore.deleteDomain(domainName);
        valueStore.deleteDomain(domainName);
        return result;
    }

//    public Result deleteDomain(@NonNull String domainName, boolean force,
//                               MultiValueMap<String, String> queryParams) {
//        // 1. check
//        validateDomain(domainName);
//
//        // 1.5 check if system
//        DomainDoc domainDoc = domainStore.getDomainDoc(domainName);
//        if (domainDoc.isDomainSystem()) {
//            throw new MetaplusException("System domain '" + domainName + "' can not be deleted.");
//        }
//
//        // 2. check if index does not exist
//        if (!domainDoc.isDomainAbstract()) {
//            String index = domainDoc.getDomainIndex();
//            if (indexDao.existIndex(index)) {
//                // 3. check if index haves any docs
//                if (!force) {
//                    int docCount = docDao.countByDomain(domainName, null);
//                    if (docCount > 0) {
//                        throw new MetaplusException("Domain '" + domainName + "' has " + docCount
//                                + " docs. Only empty domain can be deleted.");
//                    }
//                }
//                // 4. delete index
//                indexDao.deleteIndex(index);
//            }
//        }
//
//        // 5. delete domain doc
//        Result result = docDao.delete(domainDoc.getIdeaFqmn(), queryParams);
//
//        // 6. unregister manage
//        domainManager.unregisterDomain(domainName);
//        return result;
//    }

//    public DomainDoc readDomainDirectly(String domain, MultiValueMap<String, String> queryParams) {
//        String fqmn = Idea.packFqmn("", DomainStore.DOMAIN_DOMAIN, domain);
//        MetaplusDoc doc = docDao.read(fqmn, queryParams);
//        if (null != doc) {
//            DomainDoc domainDoc = new DomainDoc(doc);
//            domainManager.registerDomain(domainDoc);
//            return domainDoc;
//        }
//        return null;
//    }
//
//    public DomainDoc readDomain(String domainName) {
//        return domainStore.getDomainDoc(domainName);
//    }
//
//
//    public boolean existDomain(String domainName) {
//        return domainStore.existDomain(domainName);
//    }
//
//
//    public Set<String> customDomainSet() {
//        return domainStore.customDomainSet();
//    }
//
//    public MetaplusDoc genSampleDoc(String domainName) {
//        DomainDoc domainDoc = domainStore.getDomainDocOrElseThrow(domainName);
//        return domainStore.genSampleDoc(domainDoc);
//    }
//
//    public String codegenJava(String domainName, String packageName) {
//        validateDomain(domainName);
//        DomainDoc domainDoc = domainStore.getDomainDoc(domainName);
//        if (null == domainDoc) {
//            throw new IllegalArgumentException("Can not find domain '" + domainName + "'.");
//        }
//
//        StringBuilder sb = JavaCodeGenerator.buildDocClass(domainDoc, packageName);
//        return sb.toString();
//    }
//
//    public JsonObject genJsonSchema(String domainName, boolean briefly, String idRef) {
//        validateDomain(domainName);
//        DomainDoc domainDoc = domainStore.getDomainDoc(domainName);
//        Schema richSchema = domainStore.genRichSchemaMerged(domainDoc);
//        JsonObject jsonSchema = JsonSchemaGenerator.buildJsonSchema(domainDoc, richSchema, briefly, idRef);
//        return jsonSchema;
//    }
//
//    public List<String> listPredefined() {
//        return PredefinedDomains.listPredefinedDomains();
//    }
//
//    public DomainDoc readPredefined(String domain) {
//        return PredefinedDomains.getPredefinedDomain(domain);
//    }
//
//    public Result createPredefined(String domain, MultiValueMap<String, String> queryParams) {
//        DomainDoc domainDoc = PredefinedDomains.getPredefinedDomain(domain);
//        if (null == domainDoc) {
//            throw new IllegalArgumentException("No predefined domain naming '" + domain + "'");
//        }
//        if (domainDoc.isDomainAbstract()) {
//            throw new IllegalArgumentException("Can not create an abstract domain '" + domain + "'");
//        }
//        return createDomain(new MetaplusPatch(domainDoc), queryParams);
//    }



}
