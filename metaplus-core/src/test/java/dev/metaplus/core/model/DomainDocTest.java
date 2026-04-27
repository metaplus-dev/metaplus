package dev.metaplus.core.model;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainDocTest {

    @Test
    void accessorsReadDeclaredDomainContracts() {
        DomainDoc doc = new DomainDoc();
        doc.setIdea(Idea.of("domain:metaplus:main:domain"));
        doc.setMeta(JsonObject.of(
                "domain", JsonObject.of(
                        "name", "domain",
                        "desc", "The domain that defines all domains.",
                        "template", "none",
                        "abstract", false,
                        "system", true,
                        "weight", 1.5
                ),
                "storage", JsonObject.of(
                        "mappings", JsonObject.of("properties", JsonObject.of())
                ),
                "schema", JsonObject.of(
                        "$ref", "https://metaplus.dev/json-schemas/domain_doc.json"
                )
        ));

        assertEquals("domain", doc.getMetaDomainName());
        assertEquals("The domain that defines all domains.", doc.getMetaDomainDesc());
        assertEquals("none", doc.getMetaDomainTemplate());
        assertEquals(Boolean.FALSE, doc.isMetaDomainAbstract());
        assertEquals(Boolean.TRUE, doc.isMetaDomainSystem());
        assertEquals(Double.valueOf(1.5), doc.getMetaDomainWeight());
    }

    @Test
    void domainWeightDefaultsToOneWhenOmitted() {
        DomainDoc doc = new DomainDoc();
        doc.setMeta(JsonObject.of(
                "domain", JsonObject.of(
                        "name", "domain"
                )
        ));

        assertEquals(Double.valueOf(1.0), doc.getMetaDomainWeight());
    }

    @Test
    void domainDocDeclaresExpectedSchemaResource() {
        ValidJsonSchema annotation = DomainDoc.class.getAnnotation(ValidJsonSchema.class);

        assertNotNull(annotation);
        assertEquals("domain_doc.json", annotation.ref());
        assertNotNull(DomainDoc.class.getClassLoader().getResource("json-schemas/domain_doc.json"));
    }
}
