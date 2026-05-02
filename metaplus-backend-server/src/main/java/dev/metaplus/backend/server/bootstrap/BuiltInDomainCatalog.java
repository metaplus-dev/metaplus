package dev.metaplus.backend.server.bootstrap;

import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.core.json.Jsons;
import dev.metaplus.core.model.DomainDoc;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class BuiltInDomainCatalog {

    private static final String BUILT_IN_CREATED_BY = "bootstrap";

    private static final List<String> BUILT_IN_DOMAIN_RESOURCES = List.of(
            "domains/domain_none.json",
            "domains/domain_domain.json");

    /**
     * Return all built-in domain docs from bundled resources.
     */
    public List<DomainDoc> listBuiltInDomainDocs() {
        List<DomainDoc> domainDocs = new ArrayList<>();
        for (String resourcePath : BUILT_IN_DOMAIN_RESOURCES) {
            domainDocs.add(_readDomainDocResource(resourcePath));
        }
        return domainDocs;
    }

    private DomainDoc _readDomainDocResource(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new BackendServerException("BuiltInDomainCatalog.read failed for resource=" + resourcePath
                        + ": resource not found");
            }
            DomainDoc domainDoc = Jsons.fromJson(inputStream, DomainDoc.class);
            domainDoc.setEditMetaCreatedAt(Instant.now());
            domainDoc.setEditMetaCreatedBy(BUILT_IN_CREATED_BY);
            return domainDoc;
        } catch (Exception e) {
            throw new BackendServerException("BuiltInDomainCatalog.read failed for resource=" + resourcePath, e);
        }
    }
    
}
