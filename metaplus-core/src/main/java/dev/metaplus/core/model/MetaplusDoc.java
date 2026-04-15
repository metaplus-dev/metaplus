package dev.metaplus.core.model;

import dev.metaplus.core.json.Jsons;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.time.Instant;


/**
 * Schema:
 * <p>
 * {
 *     "idea": {
 *         "fqmn": "...",
 *         "domain": "...",
 *         "system": "...",
 *         "instance": "...",
 *         "entity": "...",
 *         "version": 0
 *     },
 *     "meta": {...},
 *     "plus": {...},
 *     "info": {
 *         "meta": {
 *             "version": 0,
 *             "createdAt": "...",
 *             "createdBy": "...",
 *             "updatedAt": "...",
 *             "updatedBy": "..."
 *         },
 *         "plus": {
 *             "version": 0,
 *             "updatedAt": "...",
 *             "updatedBy": "..."
 *         }
 *     },
 * }
 *
 */
@ValidJsonSchema(ref = "metaplus-doc.json")
@Getter @Setter
public class MetaplusDoc extends JsonObject {

    protected Idea idea;
    protected JsonObject meta;
    protected JsonObject plus;
    protected Info info;

    // Idea

    public String getIdeaFqmn() {
        return idea.getFqmn();
    }
    public String getIdeaDomain() {
        return idea.getDomain();
    }
    public String getIdeaSystem() {
        return idea.getSystem();
    }
    public String getIdeaInstance() {
        return idea.getInstance();
    }
    public String getIdeaEntity() {
        return idea.getEntity();
    }

    // Info

    @Getter @Setter
    public static class Info extends JsonObject {
        private String innerId;
        private Integer innerVersion;
        private JsonObject meta;
        private JsonObject plus;
    }

    public int getMetaVersion() {
        return Jsons.cachedPath("$.info.meta.version").getInt(this, 0);
    }
    public Instant getMetaCreatedAt() {
        return Jsons.cachedPath("$.info.meta.createdAt").get(this, Instant.class);
    }
    public String getMetaCreatedBy() {
        return Jsons.cachedPath("$.info.meta.createdBy").getString(this);
    }
    public Instant getMetaUpdatedAt() {
        return Jsons.cachedPath("$.info.meta.updatedAt").get(this, Instant.class);
    }
    public String getMetaUpdatedBy() {
        return Jsons.cachedPath("$.info.meta.updatedBy").getString(this);
    }

    public int getPlusVersion() {
        return Jsons.cachedPath("$.info.plus.version").getInt(this, 0);
    }
    public Instant getPlusUpdatedAt() {
        return Jsons.cachedPath("$.info.plus.updatedAt").get(this, Instant.class);
    }
    public String getPlusUpdatedBy() {
        return Jsons.cachedPath("$.info.plus.updatedBy").getString(this);
    }

}
