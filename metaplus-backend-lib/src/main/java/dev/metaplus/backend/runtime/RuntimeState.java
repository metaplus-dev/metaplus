package dev.metaplus.backend.runtime;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.time.Instant;

/**
 * Runtime sidecar state for a single FQMN.
 *
 * <p>This model captures operational timestamps used by schedulers and workers.
 * It is intentionally separate from MetaplusDoc so runtime execution state does
 * not pollute source metadata or plus enrichments.</p>
 */
@Getter @Setter
public class RuntimeState extends JsonObject {

    /** Stable identity of the source-aligned object. */
    private String fqmn;
    /** Domain extracted from fqmn for filtering and partition-style queries. */
    private String domain;
    /** Last time the source-aligned object was upserted. */
    private Instant upsertedAt;
    /** Last time the source-aligned object was marked deleted. */
    private Instant deletedAt;
    /** Last completion time of the sampling job. */
    private Instant lastSamplingAt;
    /** Last completion time of the LLM generation job. */
    private Instant lastLlmGenAt;

    public RuntimeState() {
        super();
    }

    public RuntimeState(JsonObject target) {
        super(target);
    }

}
