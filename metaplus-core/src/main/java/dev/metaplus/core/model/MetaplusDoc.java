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
    private static final JsonPath PATH_EDIT_META = JsonPath.compile("$.edit.meta");
    private static final JsonPath PATH_EDIT_META_VERSION = JsonPath.compile("$.edit.meta.version");
    private static final JsonPath PATH_EDIT_META_CREATED_AT = JsonPath.compile("$.edit.meta.createdAt");
    private static final JsonPath PATH_EDIT_META_CREATED_BY = JsonPath.compile("$.edit.meta.createdBy");
    private static final JsonPath PATH_EDIT_META_UPDATED_AT = JsonPath.compile("$.edit.meta.updatedAt");
    private static final JsonPath PATH_EDIT_META_UPDATED_BY = JsonPath.compile("$.edit.meta.updatedBy");
    private static final JsonPath PATH_EDIT_META_DELETED_AT = JsonPath.compile("$.edit.meta.deletedAt");
    private static final JsonPath PATH_EDIT_META_DELETED_BY = JsonPath.compile("$.edit.meta.deletedBy");
    private static final JsonPath PATH_EDIT_META_RESTORED_AT = JsonPath.compile("$.edit.meta.restoredAt");
    private static final JsonPath PATH_EDIT_META_RESTORED_BY = JsonPath.compile("$.edit.meta.restoredBy");
    private static final JsonPath PATH_EDIT_PLUS = JsonPath.compile("$.edit.plus");
    private static final JsonPath PATH_EDIT_PLUS_VERSION = JsonPath.compile("$.edit.plus.version");
    private static final JsonPath PATH_EDIT_PLUS_UPDATED_AT = JsonPath.compile("$.edit.plus.updatedAt");
    private static final JsonPath PATH_EDIT_PLUS_UPDATED_BY = JsonPath.compile("$.edit.plus.updatedBy");

    public JsonObject getEditMeta() {
        return PATH_EDIT_META.getJsonObject(this);
    }

    public void setEditMeta(JsonObject value) {
        PATH_EDIT_META.ensurePut(this, value);
    }

    public int getEditMetaVersion() {
        return PATH_EDIT_META_VERSION.getInt(this, 0);
    }

    public void setEditMetaVersion(int value) {
        PATH_EDIT_META_VERSION.ensurePut(this, value);
    }

    /** Canonical UTC timestamp represented as an RFC 3339 date-time string. */
    public Instant getEditMetaCreatedAt() {
        return PATH_EDIT_META_CREATED_AT.get(this, Instant.class);
    }

    public void setEditMetaCreatedAt(Instant value) {
        PATH_EDIT_META_CREATED_AT.ensurePut(this, value);
    }

    /** Human or agent identifier that performed an operation. */
    public String getEditMetaCreatedBy() {
        return PATH_EDIT_META_CREATED_BY.getString(this);
    }

    public void setEditMetaCreatedBy(String value) {
        PATH_EDIT_META_CREATED_BY.ensurePut(this, value);
    }

    /** Canonical UTC timestamp represented as an RFC 3339 date-time string. */
    public Instant getEditMetaUpdatedAt() {
        return PATH_EDIT_META_UPDATED_AT.get(this, Instant.class);
    }

    public void setEditMetaUpdatedAt(Instant value) {
        PATH_EDIT_META_UPDATED_AT.ensurePut(this, value);
    }

    /** Human or agent identifier that performed an operation. */
    public String getEditMetaUpdatedBy() {
        return PATH_EDIT_META_UPDATED_BY.getString(this);
    }

    public void setEditMetaUpdatedBy(String value) {
        PATH_EDIT_META_UPDATED_BY.ensurePut(this, value);
    }

    /** Canonical UTC timestamp represented as an RFC 3339 date-time string. */
    public Instant getEditMetaDeletedAt() {
        return PATH_EDIT_META_DELETED_AT.get(this, Instant.class);
    }

    public void setEditMetaDeletedAt(Instant value) {
        PATH_EDIT_META_DELETED_AT.ensurePut(this, value);
    }

    /** Human or agent identifier that performed an operation. */
    public String getEditMetaDeletedBy() {
        return PATH_EDIT_META_DELETED_BY.getString(this);
    }

    public void setEditMetaDeletedBy(String value) {
        PATH_EDIT_META_DELETED_BY.ensurePut(this, value);
    }

    /** Canonical UTC timestamp represented as an RFC 3339 date-time string. */
    public Instant getEditMetaRestoredAt() {
        return PATH_EDIT_META_RESTORED_AT.get(this, Instant.class);
    }

    public void setEditMetaRestoredAt(Instant value) {
        PATH_EDIT_META_RESTORED_AT.ensurePut(this, value);
    }

    /** Human or agent identifier that performed an operation. */
    public String getEditMetaRestoredBy() {
        return PATH_EDIT_META_RESTORED_BY.getString(this);
    }

    public void setEditMetaRestoredBy(String value) {
        PATH_EDIT_META_RESTORED_BY.ensurePut(this, value);
    }

    public JsonObject getEditPlus() {
        return PATH_EDIT_PLUS.getJsonObject(this);
    }

    public void setEditPlus(JsonObject value) {
        PATH_EDIT_PLUS.ensurePut(this, value);
    }

    public int getEditPlusVersion() {
        return PATH_EDIT_PLUS_VERSION.getInt(this, 0);
    }

    public void setEditPlusVersion(int value) {
        PATH_EDIT_PLUS_VERSION.ensurePut(this, value);
    }

    /** Canonical UTC timestamp represented as an RFC 3339 date-time string. */
    public Instant getEditPlusUpdatedAt() {
        return PATH_EDIT_PLUS_UPDATED_AT.get(this, Instant.class);
    }

    public void setEditPlusUpdatedAt(Instant value) {
        PATH_EDIT_PLUS_UPDATED_AT.ensurePut(this, value);
    }

    /** Human or agent identifier that performed an operation. */
    public String getEditPlusUpdatedBy() {
        return PATH_EDIT_PLUS_UPDATED_BY.getString(this);
    }

    public void setEditPlusUpdatedBy(String value) {
        PATH_EDIT_PLUS_UPDATED_BY.ensurePut(this, value);
    }
}
