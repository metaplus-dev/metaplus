package dev.metaplus.backend.server.domain;

import dev.metaplus.backend.server.bootstrap.BuiltInDomainCatalog;
import dev.metaplus.core.model.DomainDoc;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainStoreTest {

    @Test
    void getMergedStorage_mergesTemplateStorageAndChildOverrides() {
        DomainStore store = new DomainStore();
        DomainDoc base = _domainDoc("base", null, JsonObject.of(
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "baseOnly", JsonObject.of("type", "keyword")
                        )
                ),
                "settings", JsonObject.of(
                        "index", JsonObject.of(
                                "number_of_shards", 1,
                                "refresh_interval", "30s"
                        )
                )
        ));
        DomainDoc child = _domainDoc("child", "base", JsonObject.of(
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "childOnly", JsonObject.of("type", "text")
                        )
                ),
                "settings", JsonObject.of(
                        "index", JsonObject.of(
                                "number_of_shards", 3
                        )
                )
        ));

        store.putDomainDoc(base);
        store.putDomainDoc(child);

        JsonObject merged = store.getMergedStorage(child);
        JsonObject mergedMappings = merged.getJsonObject("mappings").getJsonObject("properties");
        JsonObject mergedSettings = merged.getJsonObject("settings").getJsonObject("index");

        assertEquals("keyword", mergedMappings.getJsonObject("baseOnly").getString("type"));
        assertEquals("text", mergedMappings.getJsonObject("childOnly").getString("type"));
        assertEquals(Integer.valueOf(3), mergedSettings.getInt("number_of_shards"));
        assertEquals("30s", mergedSettings.getString("refresh_interval"));
    }

    @Test
    void getMergedStorage_doesNotMutateTemplateStorage() {
        DomainStore store = new DomainStore();
        DomainDoc base = _domainDoc("base", null, JsonObject.of(
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "baseOnly", JsonObject.of("type", "keyword")
                        )
                ),
                "settings", JsonObject.of(
                        "index", JsonObject.of(
                                "number_of_shards", 1
                        )
                )
        ));
        DomainDoc child = _domainDoc("child", "base", JsonObject.of(
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "childOnly", JsonObject.of("type", "text")
                        )
                ),
                "settings", JsonObject.of(
                        "index", JsonObject.of(
                                "refresh_interval", "1s"
                        )
                )
        ));

        store.putDomainDoc(base);
        store.putDomainDoc(child);

        store.getMergedStorage(child);

        JsonObject baseMappings = base.getMetaStorageMappings().getJsonObject("properties");
        JsonObject baseSettings = base.getMetaStorageSettings().getJsonObject("index");

        assertEquals("keyword", baseMappings.getJsonObject("baseOnly").getString("type"));
        assertNull(baseMappings.getJsonObject("childOnly"));
        assertEquals(Integer.valueOf(1), baseSettings.getInt("number_of_shards"));
        assertNull(baseSettings.getString("refresh_interval"));
    }

    @Test
    void toPureStorage_removesDollarPrefixedMetadataAndKeepsOnlyEsStorageSections() {
        JsonObject mergedStorage = JsonObject.of(
                "$kind", "storage",
                "index", "i_demo",
                "mappings", JsonObject.of(
                        "properties", JsonObject.of(
                                "name", JsonObject.of(
                                        "type", "keyword",
                                        "$value", "meta.domain.name"
                                )
                        )
                ),
                "settings", JsonObject.of(
                        "$comment", "internal only"
                )
        );

        JsonObject pureStorage = StorageUtil.pureStorage(mergedStorage);

        assertNull(pureStorage.getString("$kind"));
        assertNull(pureStorage.getString("index"));
        assertNull(pureStorage.getJsonObject("settings").getString("$comment"));
        assertNull(pureStorage.getJsonObject("mappings")
                .getJsonObject("properties")
                .getJsonObject("name")
                .getString("$value"));
        assertEquals("keyword", pureStorage.getJsonObject("mappings")
                .getJsonObject("properties")
                .getJsonObject("name")
                .getString("type"));
    }

    @Test
    void builtInDomainCatalog_docsKeepDerivedValueExpressions() {
        BuiltInDomainCatalog builtInDomainCatalog = new BuiltInDomainCatalog();
        DomainStore domainStore = new DomainStore();
        ValueStore valueStore = new ValueStore(domainStore);

        for (DomainDoc domainDoc : builtInDomainCatalog.listBuiltInDomainDocs()) {
            domainStore.putDomainDoc(domainDoc);
            valueStore.putDomainDoc(domainDoc);
        }

        assertEquals("${idea.domain}:${idea.system}:${idea.instance}:${idea.entity}",
                domainStore.getDomainDocOrElseThrow("none").getMetaStorageMappings()
                        .getJsonObject("properties")
                        .getJsonObject("idea")
                        .getJsonObject("properties")
                        .getJsonObject("fqmn")
                        .getString("$value"));
        String noneScript = valueStore.getDerivedAssignmentScriptOrElseThrow("none");
        String domainScript = valueStore.getDerivedAssignmentScriptOrElseThrow("domain");
        assertFalse(noneScript.isBlank());
        assertFalse(domainScript.isBlank());
    }

    @Test
    void putDomainDoc_rejectsBlankDomainName() {
        DomainStore store = new DomainStore();
        DomainDoc domainDoc = new DomainDoc();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> store.putDomainDoc(domainDoc));

        assertEquals("domainDoc.meta.domain.name must not be blank", ex.getMessage());
    }

    @Test
    void putDomainDoc_rejectsSelfTemplate() {
        DomainStore store = new DomainStore();
        DomainDoc domainDoc = _domainDoc("domain", "domain", new JsonObject());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> store.putDomainDoc(domainDoc));

        assertEquals("domainDoc.meta.domain.template must not reference itself: domain", ex.getMessage());
    }

    private static DomainDoc _domainDoc(String name, String template, JsonObject storage) {
        DomainDoc domainDoc = new DomainDoc();
        domainDoc.setMetaDomainName(name);
        if (template != null) {
            domainDoc.setMetaDomainTemplate(template);
        }
        if (storage != null) {
            domainDoc.setMetaStorage(storage);
        }
        return domainDoc;
    }
}
