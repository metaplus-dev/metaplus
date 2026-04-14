package dev.metaplus.core.model.patch;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PatchSchemaBindingTest {

    @Test
    void patchModelsBindToExpectedJsonSchemas() {
        assertSchemaRef(PatchRequest.class, "patch-request.json");
        assertSchemaRef(PatchResponse.class, "patch-response.json");
    }

    private static void assertSchemaRef(Class<?> type, String expectedRef) {
        ValidJsonSchema annotation = type.getAnnotation(ValidJsonSchema.class);
        assertNotNull(annotation, () -> type.getSimpleName() + " should declare @ValidJsonSchema");
        assertEquals(expectedRef, annotation.ref());
        assertNotNull(type.getClassLoader().getResource("json-schemas/" + expectedRef),
                () -> "Missing schema resource json-schemas/" + expectedRef);
    }
}
