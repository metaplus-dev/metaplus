package dev.metaplus.backend.server.domain;

import dev.metaplus.core.model.DomainDoc;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.MetaplusDoc;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.schema.ValidationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaStoreTest {

    @Test
    void validateDoc_usesBuiltInSchemaRef() {
        DomainStore domainStore = new DomainStore();
        SchemaStore schemaStore = new SchemaStore(domainStore);

        DomainDoc domainDoc = new DomainDoc();
        domainDoc.setMetaDomainName("demo");
        domainDoc.setMetaSchema(JsonObject.of(
                "$ref", "https://metaplus.dev/json-schemas/metaplus_doc.json"));
        domainStore.putDomainDoc(domainDoc);
        schemaStore.putDomainDoc(domainDoc);

        MetaplusDoc validDoc = new MetaplusDoc();
        validDoc.setIdea(Idea.of("demo:mysql:main:orders"));
        validDoc.setMeta(new JsonObject());
        validDoc.setPlus(new JsonObject());
        validDoc.setEdit(new JsonObject());

        MetaplusDoc invalidDoc = new MetaplusDoc();
        invalidDoc.setIdea(Idea.of("demo:mysql:main:orders"));
        invalidDoc.setMeta(new JsonObject());

        assertDoesNotThrow(() -> schemaStore.validateDoc(validDoc));
        assertThrows(ValidationException.class, () -> schemaStore.validateDoc(invalidDoc));
    }

    @Test
    void putDomainDoc_replacesCachedCompiledSchema() {
        DomainStore domainStore = new DomainStore();
        SchemaStore schemaStore = new SchemaStore(domainStore);

        DomainDoc domainDocV1 = new DomainDoc();
        domainDocV1.setMetaDomainName("demo");
        domainDocV1.setMetaSchema(_schemaRequiringPlusField("foo"));
        domainStore.putDomainDoc(domainDocV1);
        schemaStore.putDomainDoc(domainDocV1);

        MetaplusDoc doc = new MetaplusDoc();
        doc.setIdea(Idea.of("demo:mysql:main:orders"));
        doc.setMeta(new JsonObject());
        doc.setPlus(JsonObject.of("foo", true));
        doc.setEdit(new JsonObject());

        assertDoesNotThrow(() -> schemaStore.validateDoc(doc));

        DomainDoc domainDocV2 = new DomainDoc();
        domainDocV2.setMetaDomainName("demo");
        domainDocV2.setMetaSchema(_schemaRequiringPlusField("bar"));
        domainStore.putDomainDoc(domainDocV2);
        schemaStore.putDomainDoc(domainDocV2);

        assertThrows(ValidationException.class, () -> schemaStore.validateDoc(doc));
    }

    private static JsonObject _schemaRequiringPlusField(String fieldName) {
        return JsonObject.of(
                "$id", "https://metaplus.dev/schemas/" + fieldName + ".json",
                "type", "object",
                "properties", JsonObject.of(
                        "idea", JsonObject.of(
                                "$ref", "https://metaplus.dev/json-schemas/base.json#/$defs/idea"),
                        "meta", JsonObject.of("type", "object"),
                        "plus", JsonObject.of(
                                "type", "object",
                                "required", JsonArray.of(fieldName)),
                        "edit", JsonObject.of("type", "object")),
                "required", JsonArray.of("idea", "meta", "plus"),
                "additionalProperties", false);
    }
}
