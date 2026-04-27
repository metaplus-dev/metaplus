package dev.metaplus.core.model;


import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.path.JsonPath;

/**
 * Built-in domain definition document that extends MetaplusDoc with domain registration, storage, and schema contracts.
 *
 * JSON shape:
 * <pre>
 * {
 *   "meta": {
 *     "domain": {
 *       "name": "string",
 *       "desc": "string",
 *       "template": "string",
 *       "abstract": "boolean",
 *       "system": "boolean",
 *       "weight": "number"
 *     },
 *     "storage": {
 *       "mappings": "object",
 *       "settings": "object"
 *     },
 *     "schema": "object"
 *   }
 * }
 * </pre>
 */
@ValidJsonSchema(ref = "domain_doc.json")
public class DomainDoc extends MetaplusDoc {

    private static final JsonPath PATH_META_DOMAIN = JsonPath.compile("$.meta.domain");
    private static final JsonPath PATH_META_DOMAIN_NAME = JsonPath.compile("$.meta.domain.name");
    private static final JsonPath PATH_META_DOMAIN_DESC = JsonPath.compile("$.meta.domain.desc");
    private static final JsonPath PATH_META_DOMAIN_TEMPLATE = JsonPath.compile("$.meta.domain.template");
    private static final JsonPath PATH_META_DOMAIN_ABSTRACT = JsonPath.compile("$.meta.domain.abstract");
    private static final JsonPath PATH_META_DOMAIN_SYSTEM = JsonPath.compile("$.meta.domain.system");
    private static final JsonPath PATH_META_DOMAIN_WEIGHT = JsonPath.compile("$.meta.domain.weight");
    private static final JsonPath PATH_META_STORAGE = JsonPath.compile("$.meta.storage");
    private static final JsonPath PATH_META_STORAGE_MAPPINGS = JsonPath.compile("$.meta.storage.mappings");
    private static final JsonPath PATH_META_STORAGE_SETTINGS = JsonPath.compile("$.meta.storage.settings");
    private static final JsonPath PATH_META_SCHEMA = JsonPath.compile("$.meta.schema");

    public JsonObject getMetaDomain() {
        return PATH_META_DOMAIN.getJsonObject(this);
    }

    public void setMetaDomain(JsonObject value) {
        PATH_META_DOMAIN.ensurePut(this, value);
    }

    /** Registered domain name. */
    public String getMetaDomainName() {
        return PATH_META_DOMAIN_NAME.getString(this);
    }

    public void setMetaDomainName(String value) {
        PATH_META_DOMAIN_NAME.ensurePut(this, value);
    }

    /** Human-readable description of the domain. */
    public String getMetaDomainDesc() {
        return PATH_META_DOMAIN_DESC.getString(this);
    }

    public void setMetaDomainDesc(String value) {
        PATH_META_DOMAIN_DESC.ensurePut(this, value);
    }

    /** Parent template domain this domain extends. */
    public String getMetaDomainTemplate() {
        return PATH_META_DOMAIN_TEMPLATE.getString(this);
    }

    public void setMetaDomainTemplate(String value) {
        PATH_META_DOMAIN_TEMPLATE.ensurePut(this, value);
    }

    /** Whether the domain is a non-instantiable template. */
    public boolean isMetaDomainAbstract() {
        return PATH_META_DOMAIN_ABSTRACT.getBoolean(this, false);
    }

    public void setMetaDomainAbstract(boolean value) {
        PATH_META_DOMAIN_ABSTRACT.ensurePut(this, value);
    }

    /** Whether the domain is built-in and platform-owned. */
    public boolean isMetaDomainSystem() {
        return PATH_META_DOMAIN_SYSTEM.getBoolean(this, false);
    }

    public void setMetaDomainSystem(boolean value) {
        PATH_META_DOMAIN_SYSTEM.ensurePut(this, value);
    }

    /** Boost weight used for cross-index search. Defaults to 1.0 when omitted. */
    public double getMetaDomainWeight() {
        return PATH_META_DOMAIN_WEIGHT.getDouble(this, 1d);
    }

    public void setMetaDomainWeight(double value) {
        PATH_META_DOMAIN_WEIGHT.ensurePut(this, value);
    }

    /** Storage contract for the domain. */
    public JsonObject getMetaStorage() {
        return PATH_META_STORAGE.getJsonObject(this);
    }

    public void setMetaStorage(JsonObject value) {
        PATH_META_STORAGE.ensurePut(this, value);
    }

    public JsonObject getMetaStorageMappings() {
        return PATH_META_STORAGE_MAPPINGS.getJsonObject(this);
    }

    public void setMetaStorageMappings(JsonObject value) {
        PATH_META_STORAGE_MAPPINGS.ensurePut(this, value);
    }

    public JsonObject getMetaStorageSettings() {
        return PATH_META_STORAGE_SETTINGS.getJsonObject(this);
    }

    public void setMetaStorageSettings(JsonObject value) {
        PATH_META_STORAGE_SETTINGS.ensurePut(this, value);
    }

    /** Schema entry for the domain, either built-in ref or inline schema object. */
    public JsonObject getMetaSchema() {
        return PATH_META_SCHEMA.getJsonObject(this);
    }

    public void setMetaSchema(JsonObject value) {
        PATH_META_SCHEMA.ensurePut(this, value);
    }
}
