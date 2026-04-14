package dev.metaplus.core.model.patch;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;


/**
 * {
 *     "lang": "...",
 *     "source": "...",
 *     "params": {...}
 * }
 *
 */
@Getter @Setter
public class Script extends JsonObject {

    private String lang;
    private String source;
    private JsonObject params;
    private JsonObject options;

}
