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


    public void putDomainDoc(DomainDoc domainDoc) {
        _validateDomainDoc(domainDoc);
        domainCache.put(domainDoc.getMetaDomainName(), domainDoc);
    }

    public DomainDoc getDomainDoc(@NonNull String domainName) {
        return domainCache.get(domainName);
    }

    public List<DomainDoc> getAllDomainDocs() {
        return new ArrayList<>(domainCache.values());
    }

    public DomainDoc getDomainDocOrElseThrow(String domainName) {
        DomainDoc domainDoc = getDomainDoc(domainName);
        if (null == domainDoc) {
            throw new BackendServerException("Domain '" + domainName + "' does not exist.");
        }
        return domainDoc;
    }

    public boolean existDomain(String domainName) {
        return DOMAIN_DOMAIN.equals(domainName) || null != getDomainDoc(domainName);
    }

    public void deleteDomain(String domainName) {
        domainCache.remove(domainName);
    }

    public void clear() {
        domainCache.clear();
    }

    public Set<String> customDomainSet() {
        Set<String> domains = new HashSet<>();
        domainCache.forEach((domain, domainDoc) -> {
            if (!domainDoc.isMetaDomainSystem() && !domainDoc.isMetaDomainAbstract()) {
                domains.add(domain);
            }
        });
        return domains;
    }

    public String getTemplateDomain(String domainName) {
        DomainDoc domainDoc = getDomainDocOrElseThrow(domainName);
        return domainDoc.getMetaDomainTemplate();
    }

    public JsonObject getMergedPureStorage(String domainName) {
        return StorageUtil.pureStorage(getMergedStorage(domainName));
    }

    public JsonObject getMergedStorage(String domainName) {
        return getMergedStorage(getDomainDocOrElseThrow(domainName));
    }

    public JsonObject getMergedMappings(String domainName) {
        JsonObject mergedStorage = getMergedStorage(domainName);
        return mergedStorage == null ? null : mergedStorage.getJsonObject("mappings");
    }

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
