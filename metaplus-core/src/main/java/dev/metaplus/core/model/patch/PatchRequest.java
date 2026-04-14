package dev.metaplus.core.model.patch;

import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.query.Query;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.time.Instant;

/**
 *
 * {
 *     "method": "...",
 *     "domain": "...",
 *     "source": "<system>/<instance>",
 *     "entity": "...",
 *     "patchedAt": "...",
 *     "patchedBy": "...",
 *     "doc": {
 *         "idea": {...},
 *         "meta": {...},
 *         "plus": {...},
 *         "info": {...}
 *     },
 *     "query": {...},
 *     "script": {
 *         "source": "...",
 *         "params": {...},
 *     },
 * }
 *
 */
@Getter @Setter
@ValidJsonSchema(ref = "patch-request.json")
public class PatchRequest extends JsonObject {

    private PatchMethod method;
    private String domain;
    private String fqmn;
    private Instant patchedAt;
    private String patchedBy;

    private MetaplusDoc doc;
    private Query query;
    private Script script;

}
