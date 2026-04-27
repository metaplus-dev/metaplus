package dev.metaplus.backend.server.domain;

import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.core.model.DomainDoc;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesStoreTest {

    @Test
    void composeScript_includesInheritedDerivedAssignmentsAndCachesCompiledFragment() {
        DomainStore domainStore = new DomainStore();
        ValuesStore valuesStore = new ValuesStore(domainStore);

        domainStore.putDomainDoc(_domainDoc("base", null, JsonObject.of(
                "properties", JsonObject.of(
                        "idea", JsonObject.of(
                                "properties", JsonObject.of(
                                        "fqmn", JsonObject.of("$value", "${idea.domain}:${idea.system}")
                                )
                        )
                )
        )));
        domainStore.putDomainDoc(_domainDoc("child", "base", JsonObject.of(
                "properties", JsonObject.of(
                        "plus", JsonObject.of(
                                "properties", JsonObject.of(
                                        "code", JsonObject.of("$value", "child_${idea.domain}")
                                )
                        )
                )
        )));

        String script = valuesStore.composeScript("child", "ctx._source.meta.version = 1");
        String derived1 = valuesStore.getDerivedAssignmentScriptOrElseThrow("child");
        String derived2 = valuesStore.getDerivedAssignmentScriptOrElseThrow("child");

        assertTrue(script.contains("putByPath(ctx._source, 'idea.fqmn', idea.domain + ':' + idea.system);"));
        assertTrue(script.contains("putByPath(ctx._source, 'plus.code', 'child_' + idea.domain);"));
        assertTrue(script.contains("ctx._source.meta.version = 1;"));
        assertSame(derived1, derived2);
    }

    @Test
    void toPainlessValueExpr_supportsLiteralAndSimplePathOnly() {
        assertEquals("'prefix_' + idea.entity + '_suffix'",
                ValueExprUtil.toPainlessValueExpr("prefix_${idea.entity}_suffix"));
        assertEquals("'plain_text'", ValueExprUtil.toPainlessValueExpr("plain_text"));
    }

    @Test
    void toLogicalFieldPath_stripsMappingsPropertiesSegments() {
        assertEquals("idea.fqmn",
                ValueExprUtil.toLogicalFieldPath("$.properties.idea.properties.fqmn"));
        assertEquals("meta.storage.index",
                ValueExprUtil.toLogicalFieldPath("$.properties.meta.properties.storage.properties.index"));
    }

    @Test
    void toLogicalFieldPath_keepsActualPropertiesFieldName() {
        assertEquals("properties",
                ValueExprUtil.toLogicalFieldPath("$.properties.properties"));
        assertEquals("meta.properties",
                ValueExprUtil.toLogicalFieldPath("$.properties.meta.properties.properties"));
        assertEquals("meta.properties.name",
                ValueExprUtil.toLogicalFieldPath("$.properties.meta.properties.properties.properties.name"));
    }

    @Test
    void toPainlessValueExpr_rejectsUnsafePlaceholderReference() {
        assertThrows(BackendServerException.class,
                () -> ValueExprUtil.toPainlessValueExpr("${idea.entity); ctx.op='delete'; //}"));
        assertThrows(BackendServerException.class,
                () -> ValueExprUtil.toPainlessValueExpr("${$.idea.entity}"));
        assertThrows(BackendServerException.class,
                () -> ValueExprUtil.toPainlessValueExpr("${other.entity}"));
    }

    @Test
    void composeScript_writesDerivedWithPutByPathAndKeepsPropertiesFieldSafe() {
        DomainStore domainStore = new DomainStore();
        ValuesStore valuesStore = new ValuesStore(domainStore);

        domainStore.putDomainDoc(_domainDoc("domain", null, JsonObject.of(
                "properties", JsonObject.of(
                        "meta", JsonObject.of(
                                "properties", JsonObject.of(
                                        "properties", JsonObject.of(
                                                "properties", JsonObject.of(
                                                        "name", JsonObject.of("$value", "p_${idea.domain}")
                                                )
                                        )
                                )
                        )
                )
        )));

        String script = valuesStore.composeScript("domain", "");

        assertTrue(script.contains("putByPath(ctx._source, 'meta.properties.name', 'p_' + idea.domain);"));
        assertFalse(script.contains("\n'p_' + idea.domain\n"));
        assertFalse(script.contains("merge(sync, params.sync);"));
    }

    @Test
    void composeScript_ordersTemplateParentBeforeChildAndChildOverrideLater() {
        DomainStore domainStore = new DomainStore();
        ValuesStore valuesStore = new ValuesStore(domainStore);

        domainStore.putDomainDoc(_domainDoc("base", null, JsonObject.of(
                "properties", JsonObject.of(
                        "idea", JsonObject.of(
                                "properties", JsonObject.of(
                                        "fqmn", JsonObject.of("$value", "${idea.domain}_base")
                                )
                        )
                )
        )));
        domainStore.putDomainDoc(_domainDoc("child", "base", JsonObject.of(
                "properties", JsonObject.of(
                        "idea", JsonObject.of(
                                "properties", JsonObject.of(
                                        "fqmn", JsonObject.of("$value", "${idea.domain}_child")
                                )
                        )
                )
        )));

        String script = valuesStore.composeScript("child", "");
        String parentAssignment = "putByPath(ctx._source, 'idea.fqmn', idea.domain + '_base');";
        String childAssignment = "putByPath(ctx._source, 'idea.fqmn', idea.domain + '_child');";

        assertTrue(script.contains(parentAssignment));
        assertTrue(script.contains(childAssignment));
        assertTrue(script.indexOf(parentAssignment) < script.indexOf(childAssignment));
    }

    @Test
    void composeScript_recompilesCachedChildWhenParentMappingsChange() {
        DomainStore domainStore = new DomainStore();
        ValuesStore valuesStore = new ValuesStore(domainStore);

        domainStore.putDomainDoc(_domainDoc("base", null, JsonObject.of(
                "properties", JsonObject.of(
                        "idea", JsonObject.of(
                                "properties", JsonObject.of(
                                        "fqmn", JsonObject.of("$value", "v1_${idea.domain}")
                                )
                        )
                )
        )));
        domainStore.putDomainDoc(_domainDoc("child", "base", JsonObject.of(
                "properties", JsonObject.of()
        )));

        String scriptV1 = valuesStore.composeScript("child", "");

        domainStore.putDomainDoc(_domainDoc("base", null, JsonObject.of(
                "properties", JsonObject.of(
                        "idea", JsonObject.of(
                                "properties", JsonObject.of(
                                        "fqmn", JsonObject.of("$value", "v2_${idea.domain}")
                                )
                        )
                )
        )));

        String scriptV2 = valuesStore.composeScript("child", "");

        assertTrue(scriptV1.contains("putByPath(ctx._source, 'idea.fqmn', 'v1_' + idea.domain);"));
        assertTrue(scriptV2.contains("putByPath(ctx._source, 'idea.fqmn', 'v2_' + idea.domain);"));
        assertFalse(scriptV2.contains("putByPath(ctx._source, 'idea.fqmn', 'v1_' + idea.domain);"));
    }

    private static DomainDoc _domainDoc(String name, String template, JsonObject mappings) {
        DomainDoc domainDoc = new DomainDoc();
        domainDoc.setMetaDomainName(name);
        if (template != null) {
            domainDoc.setMetaDomainTemplate(template);
        }
        domainDoc.setMetaStorageMappings(mappings);
        return domainDoc;
    }
}
