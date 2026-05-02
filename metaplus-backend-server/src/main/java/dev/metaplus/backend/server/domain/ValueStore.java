package dev.metaplus.backend.server.domain;

import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.core.model.DomainDoc;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonObject;
import org.sjf4j.node.Nodes;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
public class ValueStore {

    // Apply derived fields in stable section order.
    private static final List<String> DERIVED_SECTION_ORDER = Arrays.asList("idea", "meta", "plus");

    private final DomainStore domainStore;

    public ValueStore(DomainStore domainStore) {
        this.domainStore = domainStore;
    }

    public static final String SCRIPT_FUNC_MERGE = "" +
            " void merge(def obj1, def obj2) { " +
            "   if (obj1 == null || obj2 == null) { return; }" +
            "   for (def entry : obj2?.entrySet()) { " +
            "     if (obj1[entry.getKey()] instanceof Map && entry.getValue() instanceof Map) { " +
            "       merge(obj1[entry.getKey()], entry.getValue()); " +
            "     } else { " +
            "       obj1[entry.getKey()] = entry.getValue(); " +
            "     } " +
            "   } " +
            " }\n";

    public static final String SCRIPT_FUNC_REQUIRE_NON_EMPTY = "" +
            " String requireNonEmpty(String v) { " +
            "   if (v == null || v.length() == 0) { " +
            "     throw new IllegalArgumentException(\"Value is empty\"); " +
            "   } " +
            "   return v; " +
            " }\n";

    public static final String SCRIPT_FUNC_IF_NOT_EMPTY = "" +
            " String ifNotEmpty(String oristr, String newstr) { " +
            "   return (oristr == null || oristr == '') ? '' : newstr;" +
            " }\n";

    public static final String SCRIPT_FUNC_GET_BY_PATH = "" +
            " def getByPath(def root, String path) { " +
            "   if (root == null || path == null) return null;" +
            "   String[] ss = path.splitOnToken(\".\");" +
            "   def cur = root;" +
            "   for (int i = 0; i < ss.length; i++) {" +
            "     if (cur == null) return null;" +
            "     cur = cur[ss[i]];" +
            "   }" +
            "   return cur;" +
            " }\n";

    public static final String SCRIPT_FUNC_PUT_BY_PATH = "" +
            " void putByPath(def root, String path, def value) { " +
            "   String[] ss = path.splitOnToken(\".\");" +
            "   def cur = root;" +
            "   for (int i = 0; i < ss.length - 1; i++) {" +
            "     def key = ss[i];" +
            "     if (cur[key] == null) cur[key] = new HashMap();" +
            "     cur = cur[key];" +
            "   }" +
            "   cur[ss[ss.length - 1]] = value;" +
            " }\n";

//    public static final String SCRIPT_FUNC_RENAME_FQMN = "void renameFqmn(def oldfqmn, def fqmn) {"
//            + " if (fqmn?.org != null) {oldfqmn.org = fqmn.org;}"
//            + " if (fqmn?.domain != null) {oldfqmn.domain = fqmn.domain;}"
//            + " if (fqmn?.name != null) {oldfqmn.name = fqmn.name;}"
//            + " oldfqmn.fqmn = oldfqmn.org + \"::\" + oldfqmn.domain + \"::\" + oldfqmn.name;"
//            + "}\n";

    public static final String SCRIPT_ENSURE_SOURCE_AND_SECTIONS = "" +
            " if (ctx._source == null) { ctx._source = new HashMap(); }" +
            " if (ctx._source.idea == null) { ctx._source.idea = new HashMap(); }" +
            " if (ctx._source.meta == null) { ctx._source.meta = new HashMap(); }" +
            " if (ctx._source.plus == null) { ctx._source.plus = new HashMap(); }" +
            " if (ctx._source.edit == null) { ctx._source.edit = new HashMap(); }";

    public static final String SCRIPT_VARS = "" +
            " def idea = ctx._source.idea;" +
            " def meta = ctx._source.meta;" +
            " def plus = ctx._source.plus;" +
            " def edit = ctx._source.edit;";

    public static final String SCRIPT_MERGE_PARAMS_FQMN = "merge(idea, params.idea);";
    public static final String SCRIPT_MERGE_PARAMS_META = "merge(meta, params.meta);";
    public static final String SCRIPT_MERGE_PARAMS_PLUS = "merge(plus, params.plus);";

    // Shared script prefix for update and reindex operations.
    public static final String SCRIPT_BEFORE_ALL_IN_ONE = SCRIPT_FUNC_MERGE +
            SCRIPT_FUNC_IF_NOT_EMPTY + SCRIPT_FUNC_REQUIRE_NON_EMPTY +
            SCRIPT_FUNC_GET_BY_PATH + SCRIPT_FUNC_PUT_BY_PATH +
            SCRIPT_ENSURE_SOURCE_AND_SECTIONS +
            SCRIPT_VARS +
            SCRIPT_MERGE_PARAMS_FQMN +
            SCRIPT_MERGE_PARAMS_META + SCRIPT_MERGE_PARAMS_PLUS;


    // Cache compiled $value expressions by domain.
    private final Map<String, Map<String, String>> domainValueExprsCache = new ConcurrentHashMap<>();
    // Cache full derived assignment script by domain.
    private final Map<String, DerivedScriptCacheEntry> domainDerivedScriptCache = new ConcurrentHashMap<>();

    /**
     * Load value expressions from one domain doc.
     */
    public void putDomainDoc(DomainDoc domainDoc) {
        putFromMappings(domainDoc.getMetaDomainName(), domainDoc.getMetaStorageMappings());
    }

    /**
     * Rebuild cached value expressions from storage mappings.
     */
    public void putFromMappings(String domain, JsonObject mappings) {
        Map<String, String> valueExprs = new LinkedHashMap<>();
        if (mappings != null) {
            // Collect all declared $value expressions from storage mappings.
            mappings.walk(Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.TOP_DOWN, -1, (path, node) -> {
                String rawValueExpr = Nodes.getInObject(node, "$value", String.class);
                if (rawValueExpr != null) {
                    valueExprs.put(ValueExprUtil.toLogicalFieldPath(path.rootedPathExpr()),
                            ValueExprUtil.toPainlessValueExpr(rawValueExpr));
                }
                return true;
            });
        }
        domainValueExprsCache.put(domain, valueExprs);
        domainDerivedScriptCache.clear();
    }

    /**
     * Remove one domain from caches.
     */
    public void deleteDomain(String domain) {
        domainValueExprsCache.remove(domain);
        domainDerivedScriptCache.clear();
    }

    /**
     * Clear all cached domains.
     */
    public void clear() {
        domainValueExprsCache.clear();
        domainDerivedScriptCache.clear();
    }

    /**
     * Return cached value expressions, or null.
     */
    public Map<String, String> getValueExprs(String domain) {
        return domainValueExprsCache.get(domain);
    }

    /**
     * Return cached value expressions, or fail if missing.
     */
    public Map<String, String> getValueExprsOrElseThrow(String domain) {
        Map<String, String> valueExprs = getValueExprs(domain);
        if (null == valueExprs) {
            throw new BackendServerException("Domain '" + domain + "' does not exist.");
        }
        return valueExprs;
    }


    /**
     * Join cached value expressions for debugging.
     */
    public String getValueExprsAsString(String domain) {
        Map<String, String> valueExprs = getValueExprsOrElseThrow(domain);
        StringBuilder sb = new StringBuilder();
        valueExprs.forEach((key, valueExpr) -> sb.append(valueExpr).append("\n"));
        return sb.toString();
    }

    /**
     * Compose final painless script for one domain.
     */
    public String composeScript(String domain, String userSource) {
        // User script runs first, then derived assignments overwrite computed fields.
        return SCRIPT_BEFORE_ALL_IN_ONE + _normalizeUserSource(userSource) + getDerivedAssignmentScriptOrElseThrow(domain);
    }

    /**
     * Build derived assignment script from the full template chain.
     */
    public String getDerivedAssignmentScriptOrElseThrow(String domain) {
        domainStore.getDomainDocOrElseThrow(domain);
        List<String> templateChain = new ArrayList<>();
        _collectTemplateChainRootFirst(domain, templateChain);
        String signature = _buildTemplateChainSignature(templateChain);

        DerivedScriptCacheEntry cached = domainDerivedScriptCache.get(domain);
        if (cached != null && Objects.equals(cached.signature, signature)) {
            return cached.script;
        }

        String compiled = _compileDerivedAssignmentsWithTemplateOrder(templateChain);
        domainDerivedScriptCache.put(domain, new DerivedScriptCacheEntry(signature, compiled));
        return compiled;
    }

    // Normalize user script for safe concatenation.
    private String _normalizeUserSource(String userSource) {
        if (userSource == null) {
            return "";
        }
        String source = userSource.trim();
        if (!source.isEmpty() && !source.endsWith(";") && !source.endsWith("}")) {
            source = source + "; ";
        }
        return source;
    }

    // Compile assignments in parent-to-child order.
    private String _compileDerivedAssignmentsWithTemplateOrder(List<String> templateChain) {
        List<DerivedAssignment> orderedAssignments = new ArrayList<>();
        for (String currentDomain : templateChain) {
            // Parent template assignments are emitted before child assignments.
            DomainDoc currentDomainDoc = domainStore.getDomainDocOrElseThrow(currentDomain);
            orderedAssignments.addAll(_collectDerivedAssignments(currentDomainDoc.getMetaStorageMappings()));
        }

        StringBuilder script = new StringBuilder();
        for (DerivedAssignment assignment : orderedAssignments) {
            script.append("putByPath(ctx._source, '")
                    .append(assignment.path.replace("'", "\\'"))
                    .append("', ")
                    .append(assignment.expr)
                    .append(");\n");
        }
        return script.toString();
    }

    // Build a cache signature from template names and mappings.
    private String _buildTemplateChainSignature(List<String> templateChain) {
        StringBuilder signature = new StringBuilder();
        for (String currentDomain : templateChain) {
            DomainDoc currentDomainDoc = domainStore.getDomainDocOrElseThrow(currentDomain);
            signature.append(currentDomain).append('\n');
            JsonObject mappings = currentDomainDoc.getMetaStorageMappings();
            signature.append(mappings == null ? "null" : mappings.toString()).append('\n');
        }
        return signature.toString();
    }

    // Collect template chain from root template to current domain.
    private void _collectTemplateChainRootFirst(String domain, List<String> out) {
        String templateDomain = domainStore.getTemplateDomain(domain);
        if (StringUtils.hasText(templateDomain)) {
            _collectTemplateChainRootFirst(templateDomain, out);
        }
        // Root template first, current domain last.
        out.add(domain);
    }

    // Collect derived assignments from mappings.
    private List<DerivedAssignment> _collectDerivedAssignments(JsonObject mappings) {
        List<DerivedAssignment> assignments = new ArrayList<>();
        if (mappings == null) {
            return assignments;
        }
        JsonObject rootProperties = mappings.getJsonObject("properties");
        if (rootProperties == null) {
            return assignments;
        }
        for (String section : DERIVED_SECTION_ORDER) {
            JsonObject sectionNode = rootProperties.getJsonObject(section);
            _collectDerivedAssignmentsInNode(sectionNode, section, assignments);
        }
        return assignments;
    }

    // Walk one mapping node and collect nested $value assignments.
    private void _collectDerivedAssignmentsInNode(JsonObject objectNode, String logicalPath, List<DerivedAssignment> out) {
        if (objectNode == null) {
            return;
        }
        String rawValueExpr = objectNode.getString("$value");
        if (rawValueExpr != null && !logicalPath.isEmpty()) {
            out.add(new DerivedAssignment(logicalPath, ValueExprUtil.toPainlessValueExpr(rawValueExpr)));
        }

        JsonObject properties = objectNode.getJsonObject("properties");
        if (properties == null) {
            return;
        }
        properties.forEach((key, childNode) -> {
            String childPath = logicalPath.isEmpty() ? key : logicalPath + "." + key;
            _collectDerivedAssignmentsInNode(properties.getJsonObject(key), childPath, out);
        });
    }

    private static class DerivedAssignment {
        private final String path;
        private final String expr;

        private DerivedAssignment(String path, String expr) {
            this.path = path;
            this.expr = expr;
        }
    }

    private static class DerivedScriptCacheEntry {
        private final String signature;
        private final String script;

        private DerivedScriptCacheEntry(String signature, String script) {
            this.signature = signature;
            this.script = script;
        }
    }


}
