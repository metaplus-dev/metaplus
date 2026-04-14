package dev.metaplus.core.util;

import dev.metaplus.core.exception.MetaplusException;
import org.sjf4j.schema.SchemaValidator;
import org.sjf4j.schema.ValidationException;
import org.sjf4j.schema.ValidationResult;

public class SchemaUtil {

    private static final SchemaValidator schemaValidator = new SchemaValidator().preloadDirectory(null);

    public static ValidationResult validate(Object pojo) {
        return schemaValidator.validate(pojo);
    }

    public static void requireValid(Object pojo) {
        ValidationResult result = schemaValidator.validate(pojo);
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

}
