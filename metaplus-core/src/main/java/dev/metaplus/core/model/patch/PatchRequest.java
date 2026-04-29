package dev.metaplus.core.model.patch;

import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.search.Query;
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
 *         "edit": {...}
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
@ValidJsonSchema(ref = "patch_request.json")
public class PatchRequest extends JsonObject {

    private PatchMethod method;
    private String domain;
    private String fqmn;

    private Query query;
    private Script script;
    private MetaplusDoc doc;

    private Instant patchedAt;
    private String patchedBy;

}
