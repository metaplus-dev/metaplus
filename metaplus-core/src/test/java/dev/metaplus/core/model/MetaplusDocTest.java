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
                "updatedAt", Instant.parse("2026-03-22T10:15:30Z"),
                "updatedBy", "agent"
        ));

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(idea);
        doc.setEdit(edit);

        assertEquals("data:mysql:main:warehouse.sales.orders", doc.getIdeaFqmn());
        assertEquals("data", doc.getIdeaDomain());
        assertEquals("mysql", doc.getIdeaSystem());
        assertEquals("main", doc.getIdeaInstance());
        assertEquals("warehouse.sales.orders", doc.getIdeaEntity());

        assertEquals(3, doc.getEditMetaVersion());
        assertEquals(Instant.parse("2026-03-20T10:15:30Z"), doc.getEditMetaCreatedAt());
        assertEquals("syncer", doc.getEditMetaCreatedBy());
        assertEquals(Instant.parse("2026-03-21T10:15:30Z"), doc.getEditMetaUpdatedAt());
        assertEquals("syncer-2", doc.getEditMetaUpdatedBy());
        assertEquals(Instant.parse("2026-03-23T10:15:30Z"), doc.getEditMetaDeletedAt());
        assertEquals("syncer-3", doc.getEditMetaDeletedBy());
        assertEquals(Instant.parse("2026-03-24T10:15:30Z"), doc.getEditMetaRestoredAt());
        assertEquals("syncer-4", doc.getEditMetaRestoredBy());

        assertEquals(4, doc.getEditPlusVersion());
        assertEquals(Instant.parse("2026-03-22T10:15:30Z"), doc.getEditPlusUpdatedAt());
        assertEquals("agent", doc.getEditPlusUpdatedBy());
    }

    @Test
    void metaplusDocDeclaresExpectedSchemaResource() {
        ValidJsonSchema annotation = MetaplusDoc.class.getAnnotation(ValidJsonSchema.class);

        assertNotNull(annotation);
        assertEquals("metaplus_doc.json", annotation.ref());
        assertNotNull(MetaplusDoc.class.getClassLoader().getResource("json-schemas/metaplus_doc.json"));
    }
}
