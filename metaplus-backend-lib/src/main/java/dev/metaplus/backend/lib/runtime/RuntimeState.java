package dev.metaplus.backend.lib.runtime;

import dev.metaplus.core.model.Idea;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

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
    private Idea idea;
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

    public String getFqmn() {
        return idea == null ? null : idea.getFqmn();
    }

    public String getDomain() {
        return idea == null ? null : idea.getDomain();
    }

}
