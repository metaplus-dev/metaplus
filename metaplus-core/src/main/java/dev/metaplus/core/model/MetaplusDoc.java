package dev.metaplus.core.model;

import dev.metaplus.core.json.Jsons;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.path.JsonPath;

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
 *         "entity": "..."
 *     },
 *     "meta": {...},
 *     "plus": {...},
 *     "edit": {
 *         "meta": {
 *             "version": 0,
 *             "updatedAt": "...",
 *             "updatedBy": "...",
 *         },
 *         "plus": {
 *             "version": 0,
 *             "updatedAt": "...",
 *             "updatedBy": "...",
 *         }
 *     },
 * }
 *
 */
@ValidJsonSchema(ref = "metaplus_doc.json")
@Getter @Setter
public class MetaplusDoc extends JsonObject {

    @NotNull
    protected Idea idea;
    protected JsonObject meta;
    protected JsonObject plus;
    protected JsonObject edit;

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

    // Edit
    private static final JsonPath PATH_EDIT_META_VERSION = JsonPath.compile("$.edit.meta.version");
    private static final JsonPath PATH_EDIT_META_CREATED_AT = JsonPath.compile("$.edit.meta.createdAt");
    private static final JsonPath PATH_EDIT_META_CREATED_BY = JsonPath.compile("$.edit.meta.createdBy");
    private static final JsonPath PATH_EDIT_META_UPDATED_AT = JsonPath.compile("$.edit.meta.updatedAt");
    private static final JsonPath PATH_EDIT_META_UPDATED_BY = JsonPath.compile("$.edit.meta.updatedBy");
    private static final JsonPath PATH_EDIT_META_DELETED_AT = JsonPath.compile("$.edit.meta.deletedAt");
    private static final JsonPath PATH_EDIT_META_DELETED_BY = JsonPath.compile("$.edit.meta.deletedBy");
    private static final JsonPath PATH_EDIT_META_RESTORED_AT = JsonPath.compile("$.edit.meta.restoredAt");
    private static final JsonPath PATH_EDIT_META_RESTORED_BY = JsonPath.compile("$.edit.meta.restoredBy");

    public int getMetaVersion() {
        return Jsons.cachedPath("$.edit.meta.version").getInt(this, 0);
    }
    public void setMetaVersion(int metaVersion) {
        Jsons.cachedPath("$.edit.meta.version").ensurePut(this, metaVersion);
    }
    public Instant getMetaCreatedAt() {
        return Jsons.cachedPath("$.edit.meta.createdAt").get(this, Instant.class);
    }
    public String getMetaCreatedBy() {
        return Jsons.cachedPath("$.edit.meta.createdBy").getString(this);
    }
    public Instant getMetaUpdatedAt() {
        return Jsons.cachedPath("$.edit.meta.updatedAt").get(this, Instant.class);
    }
    public String getMetaUpdatedBy() {
        return Jsons.cachedPath("$.edit.meta.updatedBy").getString(this);
    }
    public Instant getMetaDeletedAt() {
        return Jsons.cachedPath("$.edit.meta.deletedAt").get(this, Instant.class);
    }
    public String getMetaDeletedBy() {
        return Jsons.cachedPath("$.edit.meta.deletedBy").getString(this);
    }
    public Instant getMetaRestoredAt() {
        return Jsons.cachedPath("$.edit.meta.restoredAt").get(this, Instant.class);
    }
    public String getMetaRestoredBy() {
        return Jsons.cachedPath("$.edit.meta.restoredBy").getString(this);
    }

    public int getPlusVersion() {
        return Jsons.cachedPath("$.edit.plus.version").getInt(this, 0);
    }
    public Instant getPlusCreatedAt() {
        return Jsons.cachedPath("$.edit.plus.createdAt").get(this, Instant.class);
    }
    public String getPlusCreatedBy() {
        return Jsons.cachedPath("$.edit.plus.createdBy").getString(this);
    }
    public Instant getPlusUpdatedAt() {
        return Jsons.cachedPath("$.edit.plus.updatedAt").get(this, Instant.class);
    }
    public String getPlusUpdatedBy() {
        return Jsons.cachedPath("$.edit.plus.updatedBy").getString(this);
    }
    public Instant getPlusDeletedAt() {
        return Jsons.cachedPath("$.edit.plus.deletedAt").get(this, Instant.class);
    }
    public String getPlusDeletedBy() {
        return Jsons.cachedPath("$.edit.plus.deletedBy").getString(this);
    }
    public Instant getPlusRestoredAt() {
        return Jsons.cachedPath("$.edit.plus.restoredAt").get(this, Instant.class);
    }
    public String getPlusRestoredBy() {
        return Jsons.cachedPath("$.edit.plus.restoredBy").getString(this);
    }

}
