package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValueStore;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.patch.PatchOptions;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.patch.Script;
import dev.metaplus.core.model.search.Query;
import dev.metaplus.core.model.search.SearchOptions;
import dev.metaplus.core.model.search.SearchRequest;
import dev.metaplus.core.model.search.SearchResponse;
import lombok.NonNull;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class TrashDao {

    @Autowired
    private EsClient esClient;

    public static final String DOMAIN_TRASH = "trash";
    public static final String INDEX_TRASH = StorageUtil.storageIndex(DOMAIN_TRASH);

    /**
     * Upsert one document into trash by id.
     */
    public Result upsertById(@NonNull MetaplusDoc doc, PatchOptions patchOptions) {
        String id = doc.getIdeaFqmn();
        Assert.hasText(id, "doc.idea.fqmn must not be blank");

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{id}");
        _applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(INDEX_TRASH, id);

        EsResponse response = esClient.put(uri, doc);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("upsertById", _targetDocId(id), response);
        }
        return response.getOpResult();
    }

    /**
     * Copy one live document into trash.
     */
    public Result copy(@NonNull String fqmn, Script script, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String sourceIndex = StorageUtil.storageIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/_reindex");
        _applyByQueryOptions(builder, patchOptions, "copy");
        URI uri = builder.build().toUri();

        JsonObject body = JsonObject.of(
                "source", JsonObject.of(
                        "index", sourceIndex,
                        "query", JsonObject.of(
                                "ids", JsonObject.of("values", JsonArray.of(fqmn)))),
                "dest", JsonObject.of("index", INDEX_TRASH),
                "script", _copyScriptForTrash(script));

        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("copy", _targetFqmn(fqmn), response);
        }
        return response.getOpResult();
    }

    /**
     * Copy matching live documents into trash.
     */
    public Result copyByQuery(@NonNull String domainName, @NonNull Query query, Script script,
                              PatchOptions patchOptions) {
        String sourceIndex = StorageUtil.storageIndex(domainName);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/_reindex");
        _applyByQueryOptions(builder, patchOptions, "copyByQuery");
        URI uri = builder.build().toUri();

        JsonObject body = JsonObject.of(
                "source", JsonObject.of(
                        "index", sourceIndex,
                        "query", query),
                "dest", JsonObject.of("index", INDEX_TRASH),
                "script", _copyScriptForTrash(script));

        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("copyByQuery", _targetDomain(domainName), response);
        }
        return response.getOpResult();
    }

    /**
     * Count trash versions for one fqmn.
     */
    public int count(@NonNull String fqmn, SearchOptions searchOptions) {
        URI uri = _buildCountUri(searchOptions);

        Query query = new Query();
        query.put("term", JsonObject.of("idea.fqmn", fqmn));

        EsResponse response = esClient.post(uri, JsonObject.of("query", query));
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("count", _targetFqmn(fqmn), response);
        }
        return response.getBody().getInt("count", 0);
    }

    /**
     * Read multiple trash versions for one fqmn.
     */
    public SearchResponse<MetaplusDoc> readMulti(@NonNull String fqmn, int size, SearchOptions searchOptions) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_search")
                .queryParam("version", true)
                .queryParam("size", size <= 0 ? 20 : size);
        _applySearchOptions(builder, searchOptions);
        URI uri = builder.build(INDEX_TRASH);

        SearchRequest request = new SearchRequest();
        Query query = new Query();
        query.put("term", JsonObject.of("idea.fqmn", fqmn));
        request.setQuery(query);
        request.setSort(JsonArray.of(JsonObject.of("edit.meta.deletedAt", JsonObject.of("order", "desc"))));

        EsResponse response = esClient.post(uri, request);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("readMulti", _targetFqmn(fqmn), response);
        }
        return response.getBodyAsSearchResponse(MetaplusDoc.class);
    }

    /**
     * Read one trash document by internal id.
     */
    public MetaplusDoc readById(@NonNull String id, PatchOptions patchOptions) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{id}");
        _applySingleDocReadOptions(builder, patchOptions);
        URI uri = builder.build(INDEX_TRASH, id);

        EsResponse response = esClient.get(uri);
        if (response.isSuccess()) {
            return response.getBodyAsMetaplusDoc();
        }
        if (response.isNotFound()) {
            return null;
        }
        throw _failureWithEsResponse("readById", _targetDocId(id), response);
    }

    /**
     * Count trash documents by domain or across all domains.
     */
    public int countByDomain(String domain, SearchOptions searchOptions) {
        URI uri = _buildCountUri(searchOptions);

        JsonObject body = JsonObject.of();
        if (StringUtils.hasText(domain) && !"*".equals(domain)) {
            Query query = new Query();
            query.put("term", JsonObject.of("idea.domain", domain));
            body.put("query", query);
        }

        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("countByDomain", _targetDomainOrAll(domain), response);
        }
        return response.getBody().getInt("count", 0);
    }

    /**
     * List trash document ids by domain or across all domains.
     */
    public SearchResponse<MetaplusDoc> listByDomain(String domain, int size, SearchOptions searchOptions) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_search")
                .queryParam("version", true)
                .queryParam("size", size <= 0 ? 20 : size);
        _applySearchOptions(builder, searchOptions);
        URI uri = builder.build(INDEX_TRASH);

        SearchRequest request = new SearchRequest();
        if (StringUtils.hasText(domain) && !"*".equals(domain)) {
            Query query = new Query();
            query.put("term", JsonObject.of("idea.domain", domain));
            request.setQuery(query);
        }

        request.setSort(JsonArray.of(JsonObject.of("edit.meta.deletedAt", JsonObject.of("order", "desc"))));
        request.put("_source", JsonArray.of("idea.fqmn", "edit.meta.deletedAt"));

        EsResponse response = esClient.post(uri, request);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("listByDomain", _targetDomainOrAll(domain), response);
        }
        return response.getBodyAsSearchResponse(MetaplusDoc.class);
    }

    /**
     * Clear trash documents by domain or across all domains.
     */
    public Result clearByDomain(String domain, PatchOptions patchOptions) {
        String target = _targetDomainOrAll(domain);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_delete_by_query");
        _applyByQueryOptions(builder, patchOptions, "clearByDomain");
        URI uri = builder.build(INDEX_TRASH);

        Query query = new Query();
        if (StringUtils.hasText(domain) && !"*".equals(domain)) {
            query.put("term", JsonObject.of("idea.domain", domain));
        } else {
            query.put("match_all", JsonObject.of());
        }

        EsResponse response = esClient.post(uri, JsonObject.of("query", query));
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("clearByDomain", target, response);
        }
        return response.getOpResult();
    }

    private URI _buildCountUri(SearchOptions searchOptions) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_count");
        _applySearchOptions(builder, searchOptions);
        return builder.build(INDEX_TRASH);
    }

    private void _applySingleDocWriteOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        if (patchOptions == null) {
            return;
        }
        _applyRefresh(builder, patchOptions.getRefresh(), "singleDocWrite");
        _applyRouteKey(builder, patchOptions.getRouteKey());
    }

    private void _applySingleDocReadOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        if (patchOptions == null) {
            return;
        }
        if (patchOptions.getRefresh() != null || patchOptions.getExecutionMode() != null) {
            throw _failureWithReason("readById", "patchOptions", "refresh/executionMode is not supported");
        }
        _applyRouteKey(builder, patchOptions.getRouteKey());
        if (patchOptions.getReadFresh() != null) {
            builder.queryParam("realtime", patchOptions.getReadFresh());
        }
    }

    private void _applyByQueryOptions(UriComponentsBuilder builder, PatchOptions patchOptions, String operation) {
        if (patchOptions == null) {
            return;
        }
        _applyRefresh(builder, patchOptions.getRefresh(), operation);
        _applyRouteKey(builder, patchOptions.getRouteKey());
        if (patchOptions.getExecutionMode() != null) {
            builder.queryParam("wait_for_completion", patchOptions.getExecutionMode() == PatchOptions.ExecutionMode.SYNC);
        }
        if (patchOptions.getReadFresh() != null) {
            throw _failureWithReason(operation, "patchOptions", "readFresh is not supported");
        }
    }

    private void _applySearchOptions(UriComponentsBuilder builder, SearchOptions searchOptions) {
        if (searchOptions == null) {
            return;
        }
        if (searchOptions.getRouteKey() != null) {
            builder.queryParam("routing", searchOptions.getRouteKey());
        }
    }

    private void _applyRouteKey(UriComponentsBuilder builder, String routeKey) {
        if (routeKey != null) {
            builder.queryParam("routing", routeKey);
        }
    }

    private void _applyRefresh(UriComponentsBuilder builder, PatchOptions.RefreshMode refreshMode, String operation) {
        if (refreshMode == null || refreshMode == PatchOptions.RefreshMode.DEFAULT) {
            return;
        }
        if (refreshMode == PatchOptions.RefreshMode.IMMEDIATE) {
            builder.queryParam("refresh", true);
            return;
        }
        if (refreshMode == PatchOptions.RefreshMode.WAIT_UNTIL) {
            throw _failureWithReason(operation, "patchOptions", "refresh=WAIT_UNTIL is not supported");
        }
    }

    private Script _copyScriptForTrash(Script script) {
        Script copied = new Script();
        if (script != null) {
            copied.mergeWithCopy(script);
        }
        if (copied.getParams() == null) {
            copied.setParams(new JsonObject());
        }
        String source = copied.getSource();
        if (source == null) {
            source = "";
        }
        source = source.trim();
        if (!source.isEmpty() && !source.endsWith(";") && !source.endsWith("}")) {
            source = source + ";";
        }
        copied.setSource(ValueStore.SCRIPT_BEFORE_ALL_IN_ONE + source + "ctx._id = null;");
        return copied;
    }

    private String _targetFqmn(String fqmn) {
        return "fqmn=" + fqmn;
    }

    private String _targetDocId(String id) {
        return "id=" + id;
    }

    private String _targetDomain(String domain) {
        return "domain=" + domain;
    }

    private String _targetDomainOrAll(String domain) {
        if (!StringUtils.hasText(domain) || "*".equals(domain)) {
            return "domain=*";
        }
        return _targetDomain(domain);
    }

    private BackendServerException _failureWithEsResponse(String operation, String target, EsResponse response) {
        return new BackendServerException("TrashDao." + operation + " failed for " + target
                + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
    }

    private BackendServerException _failureWithReason(String operation, String target, String reason) {
        return new BackendServerException("TrashDao." + operation + " failed for " + target
                + ": " + reason);
    }


}
