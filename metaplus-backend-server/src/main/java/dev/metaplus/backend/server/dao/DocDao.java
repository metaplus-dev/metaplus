package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValueStore;
import dev.metaplus.core.exception.MetaplusException;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.patch.PatchOptions;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.patch.Script;
import dev.metaplus.core.model.search.Query;
import dev.metaplus.core.model.search.SearchRequest;
import dev.metaplus.core.model.search.SearchResponse;
import dev.metaplus.core.util.IdGenerator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class DocDao {

    private final EsClient esClient;
    private final ValueStore valueStore;
    private final IndexDao indexDao;
    

    /**
     * Create or update a document by fqmn.
     * <p>
     * This uses scripted upsert, so the document is created when absent and updated when present.
     */
    public Result upsert(@NonNull MetaplusDoc doc, String source, PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(doc.getIdeaDomain());
        String fqmn = doc.getIdeaFqmn();
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_update/{fqmn}");
        _applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);
        JsonObject body = JsonObject.of(
                "scripted_upsert", true,
                "upsert", JsonObject.of(),
                "script", JsonObject.of(
                        "source", valueStore.composeScript(doc.getIdeaDomain(), source),
                        "params", doc));

        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw new BackendServerException("DocDao.upsert failed for fqmn=" + fqmn
                    + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
        }
        return response.getOpResult();
    }

    /**
     * Create a document only when it does not already exist.
     * <p>
     * This still runs the composed script, but the script exits with noop when the target document already exists.
     */
    public Result create(@NonNull MetaplusDoc doc, String source, PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(doc.getIdeaDomain());
        String fqmn = doc.getIdeaFqmn();

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_update/{fqmn}");
        _applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);

        JsonObject body = JsonObject.of(
                "scripted_upsert", true,
                "upsert", JsonObject.of(),
                "script", JsonObject.of(
                        "source", "if (ctx.op != 'create') { ctx.op = 'none'; return; }"
                                + valueStore.composeScript(doc.getIdeaDomain(), source),
                        "params", doc));

        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw new BackendServerException("DocDao.create failed for fqmn=" + fqmn
                    + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
        }
        return response.getOpResult();
    }

    /**
     * Update an existing document only.
     * <p>
     * This runs the composed script without upsert, so Elasticsearch fails when the target document does not exist.
     */
    public Result update(@NonNull MetaplusDoc doc, String source, PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(doc.getIdeaDomain());
        String fqmn = doc.getIdeaFqmn();

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_update/{fqmn}");
        _applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);

        JsonObject body = JsonObject.of(
                "script", JsonObject.of(
                        "source", valueStore.composeScript(doc.getIdeaDomain(), source),
                        "params", doc));

        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw new BackendServerException("DocDao.update failed: target=fqmn=" + fqmn
                    + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
        }
        return response.getOpResult();
    }
    /**
     * Run a script against one document.
     */
    public Result script(@NonNull String fqmn, @NonNull Script script, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.storageIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_update/{fqmn}");
        _applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);
        
        Script fixedScript = _copyScriptWithComposedSource(script, idea.getDomain());

        EsResponse response = esClient.post(uri, JsonObject.of("script", fixedScript));
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("script", "fqmn=" + fqmn, response);
        }
        return response.getOpResult();
    }

    /**
     * Reindex one document into its transformed destination fqmn.
     *
     * This first reindexes the source doc into a temp index, reads the transformed fqmn,
     * reindexes it back into the destination index, and deletes the original doc last.
     */
    public Result reindex(@NonNull String fqmn, @NonNull MetaplusDoc doc, String source,
                          PatchOptions patchOptions) {
        Idea oldIdea = Idea.of(fqmn);
        String sourceIndex = StorageUtil.storageIndex(oldIdea.getDomain());
        String targetDomain = doc.getIdeaDomain();
        _validateReindexOptions(patchOptions, "reindex");
        _validateSyncExecutionModeForReindex(patchOptions, "reindex");

        // Step 1 reindex
        _enableIndexReindexTmp0();
        URI uri1 = UriComponentsBuilder.fromPath("/_reindex")
                .queryParam("refresh", true)
                .build().toUri();
        JsonObject body1 = JsonObject.of(
                "source", JsonObject.of(
                        "index", sourceIndex,
                        "query", JsonObject.of(
                                "ids", JsonObject.of("values", JsonArray.of(fqmn)))),
                "dest", JsonObject.of(
                        "index", INDEX_REINDEX_TMP_0),
                "script", JsonObject.of(
                        "source", valueStore.composeScript(targetDomain, source),
                        "params", doc));

        EsResponse response = esClient.post(uri1, body1);
        if (!response.isSuccess()) {
            throw _failureWithStepAndEsResponse("reindex", "fqmn=" + fqmn, "1", response);
        }
        if (response.getOpResult().getTotal() == 0) {
            throw _failureWithStepAndReason("reindex", "fqmn=" + fqmn, "1", "source document not found");
        }

        // Step 2 resolve transformed fqmn/domain from tmp doc
        String transformedFqmn = _readTmpDocFqmn(INDEX_REINDEX_TMP_0, fqmn, "reindex", "fqmn=" + fqmn, "2");
        if (fqmn.equals(transformedFqmn)) {
            throw _failureWithStepAndReason("reindex", "fqmn=" + fqmn, "2",
                    "transformed fqmn is unchanged");
        }
        String transformedDomain = Idea.of(transformedFqmn).getDomain();
        if (!targetDomain.equals(transformedDomain)) {
            throw _failureWithStepAndReason("reindex", "fqmn=" + fqmn, "2",
                    "transformed domain does not match target doc domain");
        }
        String destinationIndex = StorageUtil.storageIndex(transformedDomain);

        // Step 3 reindex back
        UriComponentsBuilder builder3 = UriComponentsBuilder.fromPath("/_reindex");
        _applyReindexOptions(builder3, patchOptions, "reindex");
        URI uri3 = builder3.build().toUri();
        JsonObject body2 = JsonObject.of(
                "source", JsonObject.of(
                        "index", INDEX_REINDEX_TMP_0,
                        "query", JsonObject.of(
                                "ids", JsonObject.of("values", JsonArray.of(fqmn)))),
                "dest", JsonObject.of(
                        "index", destinationIndex),
                "script", JsonObject.of(
                        "source", "ctx._id = ctx._source.idea.fqmn;"));

        EsResponse response2 = esClient.post(uri3, body2);
        if (!response2.isSuccess()) {
            throw _failureWithStepAndEsResponse("reindex", "fqmn=" + fqmn, "3", response2);
        }

        // Step 4 delete old doc only after successful reindex-back
        EsResponse deleteResponse = _deleteRaw(fqmn, _toSingleDocWriteOptions(patchOptions));
        if (!deleteResponse.isSuccess()) {
            throw _failureWithStepAndEsResponse("reindex", "fqmn=" + fqmn, "4", deleteResponse);
        }

        return response2.getOpResult();
    }
    /**
     * Delete one document by fqmn.
     */
    public Result delete(@NonNull String fqmn, PatchOptions patchOptions) {
        EsResponse response = _deleteRaw(fqmn, patchOptions);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("delete", "fqmn=" + fqmn, response);
        }
        return response.getOpResult();
    }
    /**
     * Read one document by fqmn.
     */
    public MetaplusDoc read(@NonNull String fqmn, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.storageIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}");
        _applySingleDocReadOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);

        EsResponse response = esClient.get(uri);
        if (response.isSuccess()) {
            return response.getBodyAsMetaplusDoc();
        } else if (response.isNotFound()) {
            return null;
        } else {
            throw _failureWithEsResponse("read", "fqmn=" + fqmn, response);
        }
    }

    /**
     * Check whether one document exists.
     */
    public boolean exist(@NonNull String fqmn, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.storageIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}");
        _applySingleDocReadOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);

        EsResponse response = esClient.head(uri);
        if (response.isSuccess()) {
            return true;
        } else if (response.isNotFound()) {
            return false;
        } else {
            throw _failureWithEsResponse("exist", "fqmn=" + fqmn, response);
        }
    }
    /**
     * Update matching documents by query.
     */
    public Result updateByQuery(@NonNull String domainName, @NonNull Query query, @NonNull Script script,
                                PatchOptions patchOptions) {
        Script fixedScript = _copyScriptWithComposedSource(script, domainName);

        EsResponse response = _operateByQuery("_update_by_query", domainName, query, fixedScript, patchOptions);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("updateByQuery", "domain=" + domainName, response);
        }
        return response.getOpResult();
    }
    /**
     * Delete matching documents by query.
     */
    public Result deleteByQuery(@NonNull String domainName, @NonNull Query query,
                                PatchOptions patchOptions) {
        EsResponse response = _operateByQuery("_delete_by_query", domainName, query, null, patchOptions);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("deleteByQuery", "domain=" + domainName, response);
        }
        return response.getOpResult();
    }


    private EsResponse _operateByQuery(@NonNull String operate, @NonNull String domainName, @NonNull Query query,
                                       Script script, PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(domainName);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/{operate}");
        _applyByQueryOptions(builder, patchOptions, operate);
        URI uri = builder.build(index, operate);

        JsonObject body = JsonObject.of("query", query);
        if (null != script) body.put("script", script);
        return esClient.post(uri, body);
    }

    /**
     * Reindex matching documents and delete captured originals.
     */
    public Result reindexByQuery(@NonNull String domainName, @NonNull Query query, @NonNull Script script,
                                 Map<String, String> fqmnMapping, PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(domainName);
        _validateReindexOptions(patchOptions, "reindexByQuery");
        _validateSyncExecutionModeForReindex(patchOptions, "reindexByQuery");

        // Step 0 create tmpIndex
        String tmpIndex = _createIndexReindexTmp(domainName);
        try {
            // Step 1 reindex
            URI uri1 = UriComponentsBuilder.fromPath("/_reindex")
                    .queryParam("refresh", true)
                    .build().toUri();

            Script fixedScript = _copyScriptWithComposedSource(script, domainName);
            JsonObject source1 = JsonObject.of(
                    "index", index,
                    "sort", JsonArray.of(JsonObject.of("_doc", "asc")),
                    "size", REINDEX_BY_QUERY_BATCH_SIZE);
            if (!query.isEmpty()) {
                source1.put("query", query);
            }
            JsonObject body1 = JsonObject.of(
                    "source", source1,
                    "dest", JsonObject.of("index", tmpIndex),
                    "script", fixedScript);

            EsResponse response1 = esClient.post(uri1, body1);
            if (!response1.isSuccess()) {
                throw _failureWithStepAndEsResponse("reindexByQuery", "domain=" + domainName, "1", response1);
            }
            Result result1 = response1.getOpResult();
            if (result1.getCreated() == 0) {
                log.warn("DocDao.reindexByQuery skipped for domain={}, step=1: empty query", domainName);
                return result1;
            }

            // Step 2 fill fqmnRenameMapping
            List<String> oldFqmns = _collectFqmnMappingByPaging(tmpIndex, domainName, fqmnMapping);

            // Step 3 reindex back
            UriComponentsBuilder builder3 = UriComponentsBuilder.fromPath("/_reindex");
            _applyReindexOptions(builder3, patchOptions, "reindexByQuery");
            URI uri3 = builder3.build().toUri();
            JsonObject body3 = JsonObject.of(
                    "source", JsonObject.of("index", tmpIndex),
                    "dest", JsonObject.of("index", index),
                    "script", JsonObject.of(
                        "source", "ctx._id = ctx._source.idea.fqmn;"));

            EsResponse response3 = esClient.post(uri3, body3);
            if (!response3.isSuccess()) {
                throw _failureWithStepAndEsResponse("reindexByQuery", "domain=" + domainName, "3", response3);
            }

            // Step 4 delete old docs by captured ids
            _deleteByIdsInBatch(domainName, oldFqmns, patchOptions);

            return response3.getOpResult();

        } finally {
            // delete tmpIndex
            _deleteIndexReindexTmp(tmpIndex);
        }

    }


    /// by domain

    /**
     * Refresh one domain index.
     */
    public void refresh(String domain, PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(domain);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_refresh");
        _applyDomainRefreshOptions(builder, patchOptions);
        URI uri = builder.build(index);

        EsResponse response = esClient.post(uri);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("refresh", "domain=" + domain, response);
        }
    }

    /**
     * Read documents from one domain.
     */
    public SearchResponse<MetaplusDoc> readByDomain(@NonNull String domain, int size, JsonArray sort,
                                                    PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(domain);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_search");
        _applyDomainReadCountOptions(builder, patchOptions);
        URI uri = builder
                .queryParam("version", true)
                .queryParam("size", size)
                .build(index);

        JsonObject search = new JsonObject();
        if (null != sort) {
            search.put("sort", sort);
        }

        EsResponse response = esClient.post(uri, search);
        if (response.isSuccess()) {
            return response.getBodyAsSearchResponse(MetaplusDoc.class);
        } else {
            throw _failureWithEsResponse("readByDomain", "domain=" + domain, response);
        }
    }

    /**
     * Count documents in one domain.
     */
    public int countByDomain(String domain, PatchOptions patchOptions) {
        String index = StorageUtil.storageIndex(domain);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_count");
        _applyDomainReadCountOptions(builder, patchOptions);
        URI uri = builder.build(index);

        EsResponse response = esClient.get(uri);
        if (response.isSuccess()) {
            return response.getBody().getInt("count");
        } else {
            throw _failureWithEsResponse("countByDomain", "domain=" + domain, response);
        }
    }


    /// private

    // reindex
    private static final String INDEX_REINDEX_TMP_0 = "i_metaplus_reindex_tmp_0";
    private static final String INDEX_REINDEX_TMP_PREFIX = "i_metaplus_reindex_tmp_";
    private static final int REINDEX_BY_QUERY_BATCH_SIZE = 1000;

    private static final JsonObject LOWEST_COST_STORAGE = JsonObject.of(
            "settings", JsonObject.of(
                    "number_of_shards", 1,
                    "number_of_replicas", 0,
                    "refresh_interval", "-1",
                    "auto_expand_replicas", false),
            "mappings", JsonObject.of(
                    "properties", JsonObject.of(),
                    "enabled", false)
    );

    private boolean existReindexTmp0 = false;

    private void _enableIndexReindexTmp0() {
        if (!existReindexTmp0) {
            if (!indexDao.existIndex(INDEX_REINDEX_TMP_0)) {
                indexDao.createIndex(INDEX_REINDEX_TMP_0, LOWEST_COST_STORAGE);
            }
            existReindexTmp0 = true;
        }
    }

    private String _createIndexReindexTmp(String domainName) {
        String index = INDEX_REINDEX_TMP_PREFIX + IdGenerator.newId20(domainName).toLowerCase();
        indexDao.createIndex(index, LOWEST_COST_STORAGE);
        return index;
    }

    private List<String> _collectFqmnMappingByPaging(String tmpIndex, String domainName,
                                                    Map<String, String> fqmnMapping) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_search")
                .queryParam("_source", "idea.fqmn")
                .build(tmpIndex);

        List<String> oldFqmns = new ArrayList<>();
        JsonArray searchAfter = null;
        while (true) {
            SearchRequest reqBody = new SearchRequest();
            reqBody.setSize(REINDEX_BY_QUERY_BATCH_SIZE);
            reqBody.setSort(JsonArray.of(JsonObject.of("_doc", "asc")));
            if (searchAfter != null) {
                reqBody.setSearchAfter(searchAfter);
            }

            EsResponse response = esClient.post(uri, reqBody);
        if (!response.isSuccess()) {
                throw _failureWithStepAndEsResponse("reindexByQuery", "domain=" + domainName, "2", response);
            }

            JsonArray hits = response.getBody().getJsonObject("hits").getJsonArray("hits");
            if (hits == null || hits.isEmpty()) {
                break;
            }

            for (int i = 0; i < hits.size(); i++) {
                JsonObject hit = hits.getJsonObject(i);
                String oldFqmn = hit.getString("_id");
                String newFqmn = hit.getStringByPath("$._source.idea.fqmn");
                if (oldFqmn.equals(newFqmn)) {
                    throw _failureWithStepAndReason("reindexByQuery", "domain=" + domainName, "2",
                            "transformed fqmn is unchanged for " + oldFqmn);
                }
                if (!domainName.equals(Idea.of(newFqmn).getDomain())) {
                    throw _failureWithStepAndReason("reindexByQuery", "domain=" + domainName, "2",
                            "cross-domain move is not supported for " + oldFqmn);
                }
                fqmnMapping.put(oldFqmn, newFqmn);
                oldFqmns.add(oldFqmn);
            }

            JsonObject lastHit = hits.getJsonObject(hits.size() - 1);
            searchAfter = lastHit.getJsonArray("sort");
            if (searchAfter == null) {
                if (hits.size() < REINDEX_BY_QUERY_BATCH_SIZE) {
                    break;
                }
                throw _failureWithStepAndReason("reindexByQuery", "domain=" + domainName, "2",
                        "missing sort in hits for pagination");
            }
        }
        return oldFqmns;
    }

    private void _deleteByIdsInBatch(String domainName, List<String> oldFqmns, PatchOptions patchOptions) {
        for (int start = 0; start < oldFqmns.size(); start += REINDEX_BY_QUERY_BATCH_SIZE) {
            int end = Math.min(start + REINDEX_BY_QUERY_BATCH_SIZE, oldFqmns.size());
            JsonArray idValues = new JsonArray();
            for (int i = start; i < end; i++) {
                idValues.add(oldFqmns.get(i));
            }

            Query deleteQuery = new Query();
            deleteQuery.put("ids", JsonObject.of("values", idValues));
            deleteByQuery(domainName, deleteQuery, _toByQueryOptions(patchOptions));
        }
    }

    private void _deleteIndexReindexTmp(String index) {
        indexDao.deleteIndex(index);
    }

    private EsResponse _deleteRaw(String fqmn, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.storageIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}");
        _applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);
        return esClient.delete(uri);
    }

    private void _applySingleDocWriteOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        _validateSupportedOptions(patchOptions, "singleDocWrite",
                true, true, false, false);
        if (patchOptions == null) {
            return;
        }
        _applyRefresh(builder, patchOptions.getRefresh());
        if (patchOptions.getRouteKey() != null) {
            builder.queryParam("routing", patchOptions.getRouteKey());
        }
    }

    private void _applySingleDocReadOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        _validateSupportedOptions(patchOptions, "singleDocRead",
                false, true, true, false);
        if (patchOptions == null) {
            return;
        }
        if (patchOptions.getRouteKey() != null) {
            builder.queryParam("routing", patchOptions.getRouteKey());
        }
        if (patchOptions.getReadFresh() != null) {
            builder.queryParam("realtime", patchOptions.getReadFresh());
        }
    }

    private void _applyByQueryOptions(UriComponentsBuilder builder, PatchOptions patchOptions, String operationName) {
        _validateSupportedOptions(patchOptions, operationName,
                true, true, false, true);
        _validateRefreshModeForBatchOperations(patchOptions, operationName);
        if (patchOptions == null) {
            return;
        }
        _applyRefresh(builder, patchOptions.getRefresh());
        if (patchOptions.getRouteKey() != null) {
            builder.queryParam("routing", patchOptions.getRouteKey());
        }
        _applyExecutionMode(builder, patchOptions.getExecutionMode());
    }

    private void _applyReindexOptions(UriComponentsBuilder builder, PatchOptions patchOptions, String operationName) {
        _validateReindexOptions(patchOptions, operationName);
        if (patchOptions == null) {
            return;
        }
        _applyRefresh(builder, patchOptions.getRefresh());
        if (patchOptions.getRouteKey() != null) {
            builder.queryParam("routing", patchOptions.getRouteKey());
        }
        _applyExecutionMode(builder, patchOptions.getExecutionMode());
    }

    private void _applyDomainReadCountOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        _validateSupportedOptions(patchOptions, "domainReadCount",
                false, true, false, false);
        if (patchOptions == null) {
            return;
        }
        if (patchOptions.getRouteKey() != null) {
            builder.queryParam("routing", patchOptions.getRouteKey());
        }
    }

    private void _applyDomainRefreshOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        _validateSupportedOptions(patchOptions, "domainRefresh",
                false, false, false, false);
    }

    private void _validateReindexOptions(PatchOptions patchOptions, String operationName) {
        _validateSupportedOptions(patchOptions, operationName,
                true, true, false, true);
        _validateRefreshModeForBatchOperations(patchOptions, operationName);
    }

    private void _validateSyncExecutionModeForReindex(PatchOptions patchOptions, String operationName) {
        if (patchOptions != null && patchOptions.getExecutionMode() == PatchOptions.ExecutionMode.ASYNC) {
            throw _failureWithReason(operationName, "patchOptions", "executionMode=ASYNC is not supported");
        }
    }

    private void _validateRefreshModeForBatchOperations(PatchOptions patchOptions, String operationName) {
        if (patchOptions != null && patchOptions.getRefresh() == PatchOptions.RefreshMode.WAIT_UNTIL) {
            throw _failureWithReason(operationName, "patchOptions", "refresh=WAIT_UNTIL is not supported");
        }
    }

    private void _validateSupportedOptions(PatchOptions patchOptions, String operationName,
                                          boolean allowRefresh,
                                          boolean allowRouteKey,
                                          boolean allowReadFresh,
                                          boolean allowExecutionMode) {
        if (patchOptions == null) {
            return;
        }
        _ensureSupported("refresh", patchOptions.getRefresh(), allowRefresh, operationName);
        _ensureSupported("routeKey", patchOptions.getRouteKey(), allowRouteKey, operationName);
        _ensureSupported("readFresh", patchOptions.getReadFresh(), allowReadFresh, operationName);
        _ensureSupported("executionMode", patchOptions.getExecutionMode(), allowExecutionMode, operationName);
    }

    private void _ensureSupported(String optionName, Object value, boolean allowed, String operationName) {
        if (value != null && !allowed) {
            throw _failureWithReason(operationName, "patchOptions", optionName + " is not supported");
        }
    }

    private String _readTmpDocFqmn(String tmpIndex, String docId, String operationName,
                                  String target, String step) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc/{id}").build(tmpIndex, docId);
        EsResponse response = esClient.get(uri);
        if (!response.isSuccess()) {
            throw _failureWithStepAndEsResponse(operationName, target, step, response);
        }
        String transformedFqmn = response.getBody().getStringByPath("$._source.idea.fqmn");
        if (transformedFqmn == null || transformedFqmn.isBlank()) {
            throw _failureWithStepAndReason(operationName, target, step,
                    "transformed fqmn is missing in tmp doc");
        }
        return transformedFqmn;
    }

    private MetaplusException _failureWithEsResponse(String operation, String target, EsResponse response) {
        return new MetaplusException("DocDao." + operation + " failed for " + target
                + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
    }

    private MetaplusException _failureWithStepAndEsResponse(String operation, String target,
                                                            String step, EsResponse response) {
        return new MetaplusException("DocDao." + operation + " failed for " + target
                + ", step=" + step + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
    }

    private MetaplusException _failureWithReason(String operation, String target, String reason) {
        return new MetaplusException("DocDao." + operation + " failed for " + target + ": " + reason);
    }

    private MetaplusException _failureWithStepAndReason(String operation, String target,
                                                        String step, String reason) {
        return new MetaplusException("DocDao." + operation + " failed for " + target
                + ", step=" + step + ": " + reason);
    }

    private PatchOptions _toSingleDocWriteOptions(PatchOptions patchOptions) {
        if (patchOptions == null) {
            return null;
        }
        PatchOptions copied = new PatchOptions();
        copied.setRefresh(patchOptions.getRefresh());
        copied.setRouteKey(patchOptions.getRouteKey());
        return copied;
    }

    private PatchOptions _toByQueryOptions(PatchOptions patchOptions) {
        if (patchOptions == null) {
            return null;
        }
        PatchOptions copied = new PatchOptions();
        copied.setRefresh(patchOptions.getRefresh());
        copied.setRouteKey(patchOptions.getRouteKey());
        copied.setExecutionMode(patchOptions.getExecutionMode());
        return copied;
    }

    private void _applyRefresh(UriComponentsBuilder builder, PatchOptions.RefreshMode refreshMode) {
        if (refreshMode == null || refreshMode == PatchOptions.RefreshMode.DEFAULT) {
            return;
        }
        if (refreshMode == PatchOptions.RefreshMode.IMMEDIATE) {
            builder.queryParam("refresh", true);
            return;
        }
        if (refreshMode == PatchOptions.RefreshMode.WAIT_UNTIL) {
            builder.queryParam("refresh", "wait_for");
        }
    }

    private void _applyExecutionMode(UriComponentsBuilder builder, PatchOptions.ExecutionMode executionMode) {
        if (executionMode == null) {
            return;
        }
        builder.queryParam("wait_for_completion", executionMode == PatchOptions.ExecutionMode.SYNC);
    }

    private Script _copyScriptWithComposedSource(@NonNull Script script, @NonNull String domainName) {
        Script copied = new Script();
        copied.mergeWithCopy(script);
        copied.setSource(valueStore.composeScript(domainName, script.getSource()));
        return copied;
    }


}
