package dev.metaplus.core.model;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetaplusDocTest {

    @Test
    void accessorsReadIdeaAndEditMetadata() {
        Idea idea = new Idea();
        idea.setFqmn("data:mysql:main:warehouse.sales.orders");
        idea.setDomain("data");
        idea.setSystem("mysql");
        idea.setInstance("main");
        idea.setEntity("warehouse.sales.orders");

        JsonObject edit = new JsonObject();
        edit.put("meta", JsonObject.of(
                "version", 3,
                "createdAt", Instant.parse("2026-03-20T10:15:30Z"),
                "createdBy", "syncer",
                "updatedAt", Instant.parse("2026-03-21T10:15:30Z"),
                "updatedBy", "syncer-2",
                "deletedAt", Instant.parse("2026-03-23T10:15:30Z"),
                "deletedBy", "syncer-3",
                "restoredAt", Instant.parse("2026-03-24T10:15:30Z"),
                "restoredBy", "syncer-4"
        ));
        edit.put("plus", JsonObject.of(
                "version", 4,
                "createdAt", Instant.parse("2026-03-21T08:00:00Z"),
                "createdBy", "agent-0",
                "updatedAt", Instant.parse("2026-03-22T10:15:30Z"),
                "updatedBy", "agent",
                "deletedAt", Instant.parse("2026-03-25T10:15:30Z"),
                "deletedBy", "agent-2",
                "restoredAt", Instant.parse("2026-03-26T10:15:30Z"),
                "restoredBy", "agent-3"
        ));

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(idea);
        doc.setEdit(edit);

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
        assertEquals(Instant.parse("2026-03-23T10:15:30Z"), doc.getMetaDeletedAt());
        assertEquals("syncer-3", doc.getMetaDeletedBy());
        assertEquals(Instant.parse("2026-03-24T10:15:30Z"), doc.getMetaRestoredAt());
        assertEquals("syncer-4", doc.getMetaRestoredBy());

        assertEquals(4, doc.getPlusVersion());
        assertEquals(Instant.parse("2026-03-21T08:00:00Z"), doc.getPlusCreatedAt());
        assertEquals("agent-0", doc.getPlusCreatedBy());
        assertEquals(Instant.parse("2026-03-22T10:15:30Z"), doc.getPlusUpdatedAt());
        assertEquals("agent", doc.getPlusUpdatedBy());
        assertEquals(Instant.parse("2026-03-25T10:15:30Z"), doc.getPlusDeletedAt());
        assertEquals("agent-2", doc.getPlusDeletedBy());
        assertEquals(Instant.parse("2026-03-26T10:15:30Z"), doc.getPlusRestoredAt());
        assertEquals("agent-3", doc.getPlusRestoredBy());
    }

    @Test
    void metaplusDocDeclaresExpectedSchemaResource() {
        ValidJsonSchema annotation = MetaplusDoc.class.getAnnotation(ValidJsonSchema.class);

        assertNotNull(annotation);
        assertEquals("metaplus_doc.json", annotation.ref());
        assertNotNull(MetaplusDoc.class.getClassLoader().getResource("json-schemas/metaplus_doc.json"));
    }
}
