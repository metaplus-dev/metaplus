package dev.metaplus.backend.server.domain;

import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.core.model.DomainDoc;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.node.Nodes;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DomainStore {

    public static final String DOMAIN_DOMAIN =  "domain";

    private final Map<String, DomainDoc> domainCache = new ConcurrentHashMap<>();


    /**
     * Put one domain doc into the cache.
     */
    public void putDomainDoc(DomainDoc domainDoc) {
        _validateDomainDoc(domainDoc);
        domainCache.put(domainDoc.getMetaDomainName(), domainDoc);
    }

    /**
     * Return one cached domain doc, or null.
     */
    public DomainDoc getDomainDoc(@NonNull String domainName) {
        return domainCache.get(domainName);
    }

    /**
     * Return all cached domain docs.
     */
    public List<DomainDoc> getAllDomainDocs() {
        return new ArrayList<>(domainCache.values());
    }

    /**
     * Return one cached domain doc, or fail if missing.
     */
    public DomainDoc getDomainDocOrElseThrow(String domainName) {
        DomainDoc domainDoc = getDomainDoc(domainName);
        if (null == domainDoc) {
            throw new BackendServerException("Domain '" + domainName + "' does not exist.");
        }
        return domainDoc;
    }

    /**
     * Check whether one domain exists.
     */
    public boolean existDomain(String domainName) {
        return DOMAIN_DOMAIN.equals(domainName) || null != getDomainDoc(domainName);
    }

    /**
     * Remove one domain from the cache.
     */
    public void deleteDomain(String domainName) {
        domainCache.remove(domainName);
    }

    /**
     * Clear all cached domains.
     */
    public void clear() {
        domainCache.clear();
    }

    /**
     * Return all non-system concrete domains.
     */
    public Set<String> customDomainSet() {
        Set<String> domains = new HashSet<>();
        domainCache.forEach((domain, domainDoc) -> {
            if (!domainDoc.isMetaDomainSystem() && !domainDoc.isMetaDomainAbstract()) {
                domains.add(domain);
            }
        });
        return domains;
    }

    /**
     * Return the template domain name for one domain.
     */
    public String getTemplateDomain(String domainName) {
        DomainDoc domainDoc = getDomainDocOrElseThrow(domainName);
        return domainDoc.getMetaDomainTemplate();
    }

    /**
     * Return merged storage without Metaplus-only metadata keys.
     */
    public JsonObject getMergedPureStorage(String domainName) {
        return StorageUtil.pureStorage(getMergedStorage(domainName));
    }

    /**
     * Return merged storage for one domain name.
     */
    public JsonObject getMergedStorage(String domainName) {
        return getMergedStorage(getDomainDocOrElseThrow(domainName));
    }

    /**
     * Return merged mappings for one domain name.
     */
    public JsonObject getMergedMappings(String domainName) {
        JsonObject mergedStorage = getMergedStorage(domainName);
        return mergedStorage == null ? null : mergedStorage.getJsonObject("mappings");
    }

    /**
     * Return merged storage for one domain doc.
     */
    public JsonObject getMergedStorage(DomainDoc domainDoc) {
        return _buildRichStorageRecursively(domainDoc);
    }



    /// private

    private JsonObject _buildRichStorageRecursively(DomainDoc domainDoc) {
        JsonObject richStorage = new JsonObject();
        String templateDomain = domainDoc.getMetaDomainTemplate();
        if (StringUtils.hasText(templateDomain)) {
            DomainDoc templateDomainDoc = getDomainDocOrElseThrow(templateDomain);
            JsonObject templateStorage = _buildRichStorageRecursively(templateDomainDoc);
            richStorage.merge(templateStorage, false, true);
        }
        richStorage.merge(domainDoc.getMetaStorage(), true, true);
        return richStorage;
    }

    private void _validateDomainDoc(DomainDoc domainDoc) {
        Assert.notNull(domainDoc, "domainDoc must not be null");

        String domainName = domainDoc.getMetaDomainName();
        Assert.hasText(domainName, "domainDoc.meta.domain.name must not be blank");

        String templateDomain = domainDoc.getMetaDomainTemplate();
        Assert.isTrue(!domainName.equals(templateDomain),
                "domainDoc.meta.domain.template must not reference itself: " + domainName);
    }


}
