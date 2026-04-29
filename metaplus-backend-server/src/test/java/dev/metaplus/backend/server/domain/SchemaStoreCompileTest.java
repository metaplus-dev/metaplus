package dev.metaplus.backend.server.domain;

import dev.metaplus.core.model.DomainDoc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import dev.metaplus.backend.server.BackendServerException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaStoreCompileTest {

    @Test
    void validateDomainSchema_acceptsBuiltInSchemaRef() {
        SchemaStore schemaStore = new SchemaStore(new DomainStore());

        DomainDoc domainDoc = new DomainDoc();
        domainDoc.setMetaDomainName("demo");
        domainDoc.setMetaSchema(JsonObject.of(
                "$ref", "https://metaplus.dev/json-schemas/metaplus_doc.json"));

        assertDoesNotThrow(() -> schemaStore.validateDomainSchema(domainDoc));
    }

    @Test
    void validateDomainSchema_rejectsMissingSchema() {
        SchemaStore schemaStore = new SchemaStore(new DomainStore());

        DomainDoc domainDoc = new DomainDoc();
        domainDoc.setMetaDomainName("demo");

        BackendServerException ex = assertThrows(BackendServerException.class,
                () -> schemaStore.validateDomainSchema(domainDoc));
        Assertions.assertEquals("SchemaStore.validateDomainSchema failed: target=domain=demo, reason=meta.schema must not be null",
                ex.getMessage());
    }
}
