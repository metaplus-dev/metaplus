package dev.metaplus.core.model;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetaplusDocTest {

    @Test
    void accessorsReadIdeaAndInfoMetadata() {
        MetaplusDoc.Idea idea = new MetaplusDoc.Idea();
        idea.setFqmn("data:mysql:main:warehouse.sales.orders");
        idea.setDomain("data");
        idea.setSystem("mysql");
        idea.setInstance("main");
        idea.setEntity("warehouse.sales.orders");

        MetaplusDoc.Info info = new MetaplusDoc.Info();
        info.setMeta(JsonObject.of(
                "version", 3,
                "createdAt", Instant.parse("2026-03-20T10:15:30Z"),
                "createdBy", "syncer",
                "updatedAt", Instant.parse("2026-03-21T10:15:30Z"),
                "updatedBy", "syncer-2"
        ));
        info.setPlus(JsonObject.of(
                "version", 4,
                "updatedAt", Instant.parse("2026-03-22T10:15:30Z"),
                "updatedBy", "agent"
        ));

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(idea);
        doc.setInfo(info);

        assertEquals("data:mysql:main:warehouse.sales.orders", doc.getIdeaFqmn());
        assertEquals("data", doc.getIdeaDomain());
        assertEquals("mysql", doc.getIdeaSystem());
        assertEquals("main", doc.getIdeaInstance());
        assertEquals("warehouse.sales.orders", doc.getIdeaEntity());

        assertEquals(3, doc.getMetaVersion());
        assertEquals(Instant.parse("2026-03-20T10:15:30Z"), doc.getMetaCreatedAt());
        assertEquals("syncer", doc.getMetaCreatedBy());
        assertEquals(Instant.parse("2026-03-21T10:15:30Z"), doc.getMetaUpdatedAt());
        assertEquals("syncer-2", doc.getMetaUpdatedBy());

        assertEquals(4, doc.getPlusVersion());
        assertEquals(Instant.parse("2026-03-22T10:15:30Z"), doc.getPlusUpdatedAt());
        assertEquals("agent", doc.getPlusUpdatedBy());
    }

    @Test
    void metaplusDocDeclaresExpectedSchemaResource() {
        ValidJsonSchema annotation = MetaplusDoc.class.getAnnotation(ValidJsonSchema.class);

        assertNotNull(annotation);
        assertEquals("metaplus-doc.json", annotation.ref());
        assertNotNull(MetaplusDoc.class.getClassLoader().getResource("json-schemas/metaplus-doc.json"));
    }
}
