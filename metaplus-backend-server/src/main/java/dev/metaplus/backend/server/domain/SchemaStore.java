package dev.metaplus.backend.server.domain;

import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.core.model.DomainDoc;
import dev.metaplus.core.model.MetaplusDoc;
import lombok.NonNull;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.ObjectSchema;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.ValidationException;
import org.sjf4j.schema.ValidationResult;
import org.sjf4j.node.Nodes;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SchemaStore {

    private static final String CORE_SCHEMA_REF_PREFIX = "https://metaplus.dev/json-schemas/";

    private static final String[] CORE_SCHEMA_REFS = {
            "base.json",
            "metaplus_doc.json",
            "domain_doc.json",
            "patch_request.json",
            "patch_response.json",
            "search_request.json",
            "search_response.json",
            "agg_request.json",
            "agg_response.json"
    };


    private final DomainStore domainStore;
    private final SchemaRegistry coreSchemaRegistry;
    private final Map<String, JsonSchema> domainSchemaCache = new ConcurrentHashMap<>();

    public SchemaStore(DomainStore domainStore) {
        this.domainStore = domainStore;
        this.coreSchemaRegistry = _buildCoreSchemaRegistry();
    }

    public void putDomainDoc(@NonNull DomainDoc domainDoc) {
        String domainName = domainDoc.getMetaDomainName();
        Assert.hasText(domainName, "meta.domain.name must not be blank");

        JsonSchema compiledSchema = validateDomainSchema(domainDoc);
        domainSchemaCache.put(domainName, compiledSchema);
    }

    public void deleteDomain(String domainName) {
        domainSchemaCache.remove(domainName);
    }

    public void clear() {
        domainSchemaCache.clear();
    }

    public void validateDoc(@NonNull MetaplusDoc doc) {
        String domainName = doc.getIdeaDomain();
        if (!StringUtils.hasText(domainName)) {
            throw new BackendServerException("SchemaStore.validateDoc failed: idea.domain must not be blank");
        }

        JsonSchema compiledSchema = _getOrCompileSchema(domainName);
        compiledSchema.requireValid(doc);
    }

    public JsonSchema validateDomainSchema(@NonNull DomainDoc domainDoc) {
        String domainName = domainDoc.getMetaDomainName();
        Assert.hasText(domainName, "meta.domain.name must not be blank");

        JsonObject schemaNode = domainDoc.getMetaSchema();
        if (schemaNode == null) {
            throw new BackendServerException("SchemaStore.validateDomainSchema failed: target=domain=" + domainName
                    + ", reason=meta.schema must not be null");
        }
        _validateSchemaRefs(domainName, schemaNode);
        return _compileSchema(schemaNode);
    }

    private JsonSchema _getOrCompileSchema(String domainName) {
        DomainDoc domainDoc = domainStore.getDomainDocOrElseThrow(domainName);
        JsonObject schemaNode = domainDoc.getMetaSchema();
        if (schemaNode == null) {
            throw new BackendServerException("SchemaStore.validateDoc failed for domain=" + domainName
                    + ": meta.schema must not be null");
        }

        JsonSchema cached = domainSchemaCache.get(domainName);
        if (cached != null) {
            return cached;
        }

        JsonSchema compiledSchema = _compileSchema(schemaNode);
        domainSchemaCache.put(domainName, compiledSchema);
        return compiledSchema;
    }

    private JsonSchema _compileSchema(JsonObject schemaNode) {
        JsonSchema jsonSchema = JsonSchema.fromNode(schemaNode.deepCopy());
        jsonSchema.compile(coreSchemaRegistry);
        jsonSchema.validate(new JsonObject());
        return jsonSchema;
    }

    private SchemaRegistry _buildCoreSchemaRegistry() {
        SchemaRegistry schemaRegistry = new SchemaRegistry();
        for (String ref : CORE_SCHEMA_REFS) {
            _registerCoreSchema(schemaRegistry, ref);
        }
        return schemaRegistry;
    }

    private void _registerCoreSchema(SchemaRegistry schemaRegistry, String ref) {
        ObjectSchema schema = SchemaRegistry.loadSchemaFromResource("json-schemas/" + ref);
        if (schema == null) {
            throw new BackendServerException("SchemaStore.init failed for coreSchema=" + ref
                    + ": schema resource not found");
        }
        schema.compile(schemaRegistry);
        schemaRegistry.register(schema);
    }

    private void _validateSchemaRefs(String domainName, JsonObject schemaNode) {
        schemaNode.walk(Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.TOP_DOWN, -1, (path, node) -> {
            if (JsonType.of(node).isObject()) {
                String ref = Nodes.getInObject(node, "$ref", String.class);
                if (ref != null) {
                    _validateSchemaRef(domainName, ref);
                }
            }
            return true;
        });
    }

    private void _validateSchemaRef(String domainName, String ref) {
        if (!StringUtils.hasText(ref)) {
            throw new BackendServerException("SchemaStore.validateDomainSchema failed: target=domain=" + domainName
                    + ", reason=meta.schema contains blank $ref");
        }
        if (ref.startsWith("#") || ref.startsWith(CORE_SCHEMA_REF_PREFIX)) {
            return;
        }
        throw new BackendServerException("SchemaStore.validateDomainSchema failed: target=domain=" + domainName
                + ", reason=meta.schema contains unsupported $ref '" + ref + "'. Only local fragment refs and built-in core schema refs are supported");
    }
}
