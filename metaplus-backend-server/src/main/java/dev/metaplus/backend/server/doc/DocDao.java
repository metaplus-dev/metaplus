package dev.metaplus.backend.server.doc;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.backend.server.domain.ValuesStore;
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
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class DocDao {

    @Autowired
    private EsClient esClient;
    @Autowired
    private ValuesStore valuesStore;
    @Autowired
    private IndexDao indexDao;
    

    public Result upsert(@NonNull MetaplusDoc doc, String source, PatchOptions patchOptions) {
        String index = StorageUtil.getDomainIndex(doc.getIdeaDomain());
        String fqmn = doc.getIdeaFqmn();
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_update/{fqmn}");
        applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);
        JsonObject body = JsonObject.of(
                "scripted_upsert", true,
                "upsert", JsonObject.of(),
                "script", JsonObject.of(
                        "source", valuesStore.composeScript(doc.getIdeaDomain(), source),
                        "params", doc));

        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw new BackendServerException(buildEsFailureMessage("upsert", targetFqmn(fqmn), response));
        }
        return response.getOpResult();
    }


    public Result script(@NonNull String fqmn, @NonNull Script script, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.getDomainIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_update/{fqmn}");
        applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);
        
        Script fixedScript = copyScriptWithComposedSource(script, idea.getDomain());

        EsResponse response = esClient.post(uri, JsonObject.of("script", fixedScript));
        if (!response.isSuccess()) {
            throw failureWithEsResponse("script", targetFqmn(fqmn), response);
        }
        return response.getOpResult();
    }


    //POST _reindex
    //{
    //  "source": {
    //    "index": "source_index",
    //    "query": {
    //      "ids": {
    //        "values": ["1", "2", "3"]
    //      }
    //    }
    //  },
    //  "dest": {
    //    "index": "dest_index"
    //  },
    //  "script": {
    //    "source": """
    //      ctx._id = 'new_' + ctx._id;
    //    """
    //  }
    //}
    public Result reindex(@NonNull String fqmn, @NonNull MetaplusDoc doc, String source,
                          PatchOptions patchOptions) {
        Idea oldIdea = Idea.of(fqmn);
        String sourceIndex = StorageUtil.getDomainIndex(oldIdea.getDomain());
        String targetDomain = doc.getIdeaDomain();
        validateReindexOptions(patchOptions, "reindex");
        validateSyncExecutionModeForReindex(patchOptions, "reindex");

        // Step 1 reindex
        enableIndexReindexTmp0();
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
                        "source", valuesStore.composeScript(targetDomain, source),
                        "params", doc));

        EsResponse response = esClient.post(uri1, body1);
        if (!response.isSuccess()) {
            throw failureWithStepAndEsResponse("reindex", targetFqmn(fqmn), "1", response);
        }
        if (response.getOpResult().getTotal() == 0) {
            throw failureWithStepAndReason("reindex", targetFqmn(fqmn), "1", "source document not found");
        }

        // Step 2 resolve transformed fqmn/domain from tmp doc
        String transformedFqmn = readTmpDocFqmn(INDEX_REINDEX_TMP_0, fqmn, "reindex", targetFqmn(fqmn), "2");
        if (fqmn.equals(transformedFqmn)) {
            throw failureWithStepAndReason("reindex", targetFqmn(fqmn), "2",
                    "transformed fqmn is unchanged");
        }
        String transformedDomain = Idea.of(transformedFqmn).getDomain();
        if (!targetDomain.equals(transformedDomain)) {
            throw failureWithStepAndReason("reindex", targetFqmn(fqmn), "2",
                    "transformed domain does not match target doc domain");
        }
        String destinationIndex = StorageUtil.getDomainIndex(transformedDomain);

        // Step 3 reindex back
        UriComponentsBuilder builder3 = UriComponentsBuilder.fromPath("/_reindex");
        applyReindexOptions(builder3, patchOptions, "reindex");
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
            throw failureWithStepAndEsResponse("reindex", targetFqmn(fqmn), "3", response2);
        }

        // Step 4 delete old doc only after successful reindex-back
        EsResponse deleteResponse = deleteRaw(fqmn, toSingleDocWriteOptions(patchOptions));
        if (!deleteResponse.isSuccess()) {
            throw failureWithStepAndEsResponse("reindex", targetFqmn(fqmn), "4", deleteResponse);
        }

        return response2.getOpResult();
    }


    public Result delete(@NonNull String fqmn, PatchOptions patchOptions) {
        EsResponse response = deleteRaw(fqmn, patchOptions);
        if (!response.isSuccess()) {
            throw failureWithEsResponse("delete", targetFqmn(fqmn), response);
        }
        return response.getOpResult();
    }


    public MetaplusDoc read(@NonNull String fqmn, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.getDomainIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}");
        applySingleDocReadOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);

        EsResponse response = esClient.get(uri);
        if (response.isSuccess()) {
            return response.getBodyAsMetaplusDoc();
        } else if (response.isNotFound()) {
            return null;
        } else {
            throw failureWithEsResponse("read", targetFqmn(fqmn), response);
        }
    }

    public boolean exist(@NonNull String fqmn, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.getDomainIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}");
        applySingleDocReadOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);

        EsResponse response = esClient.head(uri);
        if (response.isSuccess()) {
            return true;
        } else if (response.isNotFound()) {
            return false;
        } else {
            throw failureWithEsResponse("exist", targetFqmn(fqmn), response);
        }
    }


    public Result updateByQuery(@NonNull String domainName, @NonNull Query query, @NonNull Script script,
                                PatchOptions patchOptions) {
        Script fixedScript = copyScriptWithComposedSource(script, domainName);

        EsResponse response = _operateByQuery("_update_by_query", domainName, query, fixedScript, patchOptions);
        if (!response.isSuccess()) {
            throw failureWithEsResponse("updateByQuery", targetDomain(domainName), response);
        }
        return response.getOpResult();
    }


    public Result deleteByQuery(@NonNull String domainName, @NonNull Query query,
                                PatchOptions patchOptions) {
        EsResponse response = _operateByQuery("_delete_by_query", domainName, query, null, patchOptions);
        if (!response.isSuccess()) {
            throw failureWithEsResponse("deleteByQuery", targetDomain(domainName), response);
        }
        return response.getOpResult();
    }


    private EsResponse _operateByQuery(@NonNull String operate, @NonNull String domainName, @NonNull Query query,
                                       Script script, PatchOptions patchOptions) {
        String index = StorageUtil.getDomainIndex(domainName);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/{operate}");
        applyByQueryOptions(builder, patchOptions, operate);
        URI uri = builder.build(index, operate);

        JsonObject body = JsonObject.of("query", query);
        if (null != script) body.put("script", script);
        return esClient.post(uri, body);
    }

    public Result reindexByQuery(@NonNull String domainName, @NonNull Query query, @NonNull Script script,
                                 Map<String, String> fqmnMapping, PatchOptions patchOptions) {
        String index = StorageUtil.getDomainIndex(domainName);
        validateReindexOptions(patchOptions, "reindexByQuery");
        validateSyncExecutionModeForReindex(patchOptions, "reindexByQuery");

        // Step 0 create tmpIndex
        String tmpIndex = createIndexReindexTmp(domainName);
        try {
            // Step 1 reindex
            URI uri1 = UriComponentsBuilder.fromPath("/_reindex")
                    .queryParam("refresh", true)
                    .build().toUri();

            Script fixedScript = copyScriptWithComposedSource(script, domainName);
            JsonObject body1 = JsonObject.of(
                    "source", JsonObject.of("index", index,
                            "query", query,
                            "sort", JsonArray.of(JsonObject.of("_doc", "asc")),
                            "size", REINDEX_BY_QUERY_BATCH_SIZE),
                    "dest", JsonObject.of("index", tmpIndex),
                    "script", fixedScript);

            EsResponse response1 = esClient.post(uri1, body1);
            if (!response1.isSuccess()) {
                throw failureWithStepAndEsResponse("reindexByQuery", targetDomain(domainName), "1", response1);
            }
            Result result1 = response1.getOpResult();
            if (result1.getCreated() == 0) {
                log.warn("DocDao.reindexByQuery skipped: target={}, step=1, reason=empty query", targetDomain(domainName));
                return result1;
            }

            // Step 2 fill fqmnRenameMapping
            List<String> oldFqmns = collectFqmnMappingByPaging(tmpIndex, domainName, fqmnMapping);

            // Step 3 reindex back
            UriComponentsBuilder builder3 = UriComponentsBuilder.fromPath("/_reindex");
            applyReindexOptions(builder3, patchOptions, "reindexByQuery");
            URI uri3 = builder3.build().toUri();
            JsonObject body3 = JsonObject.of(
                    "source", JsonObject.of("index", tmpIndex),
                    "dest", JsonObject.of("index", index),
                    "script", JsonObject.of(
                        "source", "ctx._id = ctx._source.idea.fqmn;"));

            EsResponse response3 = esClient.post(uri3, body3);
            if (!response3.isSuccess()) {
                throw failureWithStepAndEsResponse("reindexByQuery", targetDomain(domainName), "3", response3);
            }

            // Step 4 delete old docs by captured ids
            deleteByIdsInBatch(domainName, oldFqmns, patchOptions);

            return response3.getOpResult();

        } finally {
            // delete tmpIndex
            deleteIndexReindexTmp(tmpIndex);
        }

    }


    /// by domain

    public void refresh(String domain, PatchOptions patchOptions) {
        String index = StorageUtil.getDomainIndex(domain);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_refresh");
        applyDomainRefreshOptions(builder, patchOptions);
        URI uri = builder.build(index);

        EsResponse response = esClient.post(uri);
        if (!response.isSuccess()) {
            throw failureWithEsResponse("refresh", targetDomain(domain), response);
        }
    }

    public SearchResponse<MetaplusDoc> readByDomain(@NonNull String domain, int size, JsonArray sort,
                                                    PatchOptions patchOptions) {
        String index = StorageUtil.getDomainIndex(domain);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_search");
        applyDomainReadCountOptions(builder, patchOptions);
        URI uri = builder
                .queryParam("version", true)
                .queryParam("size", size)
                .build(index);

        SearchRequest search = new SearchRequest();
        if (null != sort) {
            search.setSort(sort);
        }

        EsResponse response = esClient.post(uri, search);
        if (response.isSuccess()) {
            return response.getBodyAsSearchResponse(MetaplusDoc.class);
        } else {
            throw failureWithEsResponse("readByDomain", targetDomain(domain), response);
        }
    }

    public int countByDomain(String domain, PatchOptions patchOptions) {
        String index = StorageUtil.getDomainIndex(domain);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_count");
        applyDomainReadCountOptions(builder, patchOptions);
        URI uri = builder.build(index);

        EsResponse response = esClient.get(uri);
        if (response.isSuccess()) {
            return response.getBody().getInt("count");
        } else {
            throw failureWithEsResponse("countByDomain", targetDomain(domain), response);
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

    private void enableIndexReindexTmp0() {
        if (!existReindexTmp0) {
            if (!indexDao.existIndex(INDEX_REINDEX_TMP_0)) {
                indexDao.createIndex(INDEX_REINDEX_TMP_0, LOWEST_COST_STORAGE);
            }
            existReindexTmp0 = true;
        }
    }

    private String createIndexReindexTmp(String domainName) {
        String index = INDEX_REINDEX_TMP_PREFIX + IdGenerator.newId20(domainName).toLowerCase();
        indexDao.createIndex(index, LOWEST_COST_STORAGE);
        return index;
    }

    private List<String> collectFqmnMappingByPaging(String tmpIndex, String domainName,
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
                throw failureWithStepAndEsResponse("reindexByQuery", targetDomain(domainName), "2", response);
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
                    throw failureWithStepAndReason("reindexByQuery", targetDomain(domainName), "2",
                            "transformed fqmn is unchanged for " + oldFqmn);
                }
                if (!domainName.equals(Idea.of(newFqmn).getDomain())) {
                    throw failureWithStepAndReason("reindexByQuery", targetDomain(domainName), "2",
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
                throw failureWithStepAndReason("reindexByQuery", targetDomain(domainName), "2",
                        "missing sort in hits for pagination");
            }
        }
        return oldFqmns;
    }

    private void deleteByIdsInBatch(String domainName, List<String> oldFqmns, PatchOptions patchOptions) {
        for (int start = 0; start < oldFqmns.size(); start += REINDEX_BY_QUERY_BATCH_SIZE) {
            int end = Math.min(start + REINDEX_BY_QUERY_BATCH_SIZE, oldFqmns.size());
            JsonArray idValues = new JsonArray();
            for (int i = start; i < end; i++) {
                idValues.add(oldFqmns.get(i));
            }

            Query deleteQuery = new Query();
            deleteQuery.put("ids", JsonObject.of("values", idValues));
            deleteByQuery(domainName, deleteQuery, toByQueryOptions(patchOptions));
        }
    }

    private void deleteIndexReindexTmp(String index) {
        indexDao.deleteIndex(index);
    }

    private EsResponse deleteRaw(String fqmn, PatchOptions patchOptions) {
        Idea idea = Idea.of(fqmn);
        String index = StorageUtil.getDomainIndex(idea.getDomain());

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}");
        applySingleDocWriteOptions(builder, patchOptions);
        URI uri = builder.build(index, fqmn);
        return esClient.delete(uri);
    }

    private void applySingleDocWriteOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        validateSupportedOptions(patchOptions, "singleDocWrite",
                true, true, false, false);
        if (patchOptions == null) {
            return;
        }
        applyCommonWriteOptions(builder, patchOptions);
    }

    private void applySingleDocReadOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        validateSupportedOptions(patchOptions, "singleDocRead",
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

    private void applyByQueryOptions(UriComponentsBuilder builder, PatchOptions patchOptions, String operationName) {
        validateSupportedOptions(patchOptions, operationName,
                true, true, false, true);
        validateRefreshModeForBatchOperations(patchOptions, operationName);
        if (patchOptions == null) {
            return;
        }
        applyRefresh(builder, patchOptions.getRefresh());
        applyRouteKey(builder, patchOptions);
        applyExecutionMode(builder, patchOptions.getExecutionMode());
    }

    private void applyReindexOptions(UriComponentsBuilder builder, PatchOptions patchOptions, String operationName) {
        validateReindexOptions(patchOptions, operationName);
        if (patchOptions == null) {
            return;
        }
        applyRefresh(builder, patchOptions.getRefresh());
        applyRouteKey(builder, patchOptions);
        applyExecutionMode(builder, patchOptions.getExecutionMode());
    }

    private void applyDomainReadCountOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        validateSupportedOptions(patchOptions, "domainReadCount",
                false, true, false, false);
        if (patchOptions == null) {
            return;
        }
        applyRouteKey(builder, patchOptions);
    }

    private void applyDomainRefreshOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        validateSupportedOptions(patchOptions, "domainRefresh",
                false, false, false, false);
    }

    private void applyCommonWriteOptions(UriComponentsBuilder builder, PatchOptions patchOptions) {
        applyRefresh(builder, patchOptions.getRefresh());
        applyRouteKey(builder, patchOptions);
    }

    private void validateReindexOptions(PatchOptions patchOptions, String operationName) {
        validateSupportedOptions(patchOptions, operationName,
                true, true, false, true);
        validateRefreshModeForBatchOperations(patchOptions, operationName);
    }

    private void validateSyncExecutionModeForReindex(PatchOptions patchOptions, String operationName) {
        if (patchOptions != null && patchOptions.getExecutionMode() == PatchOptions.ExecutionMode.ASYNC) {
            throw failureWithReason(operationName, "patchOptions", "executionMode=ASYNC is not supported");
        }
    }

    private void validateRefreshModeForBatchOperations(PatchOptions patchOptions, String operationName) {
        if (patchOptions != null && patchOptions.getRefresh() == PatchOptions.RefreshMode.WAIT_UNTIL) {
            throw failureWithReason(operationName, "patchOptions", "refresh=WAIT_UNTIL is not supported");
        }
    }

    private void validateSupportedOptions(PatchOptions patchOptions, String operationName,
                                          boolean allowRefresh,
                                          boolean allowRouteKey,
                                          boolean allowReadFresh,
                                          boolean allowExecutionMode) {
        if (patchOptions == null) {
            return;
        }
        ensureSupported("refresh", patchOptions.getRefresh(), allowRefresh, operationName);
        ensureSupported("routeKey", patchOptions.getRouteKey(), allowRouteKey, operationName);
        ensureSupported("readFresh", patchOptions.getReadFresh(), allowReadFresh, operationName);
        ensureSupported("executionMode", patchOptions.getExecutionMode(), allowExecutionMode, operationName);
    }

    private void ensureSupported(String optionName, Object value, boolean allowed, String operationName) {
        if (value != null && !allowed) {
            throw failureWithReason(operationName, "patchOptions", optionName + " is not supported");
        }
    }

    private String readTmpDocFqmn(String tmpIndex, String docId, String operationName,
                                  String target, String step) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc/{id}").build(tmpIndex, docId);
        EsResponse response = esClient.get(uri);
        if (!response.isSuccess()) {
            throw failureWithStepAndEsResponse(operationName, target, step, response);
        }
        String transformedFqmn = response.getBody().getStringByPath("$._source.idea.fqmn");
        if (transformedFqmn == null || transformedFqmn.isBlank()) {
            throw failureWithStepAndReason(operationName, target, step,
                    "transformed fqmn is missing in tmp doc");
        }
        return transformedFqmn;
    }

    private String targetFqmn(String fqmn) {
        return "fqmn=" + fqmn;
    }

    private String targetDomain(String domain) {
        return "domain=" + domain;
    }

    private MetaplusException failureWithEsResponse(String operation, String target, EsResponse response) {
        return new MetaplusException(buildEsFailureMessage(operation, target, response));
    }

    private MetaplusException failureWithStepAndEsResponse(String operation, String target,
                                                           String step, EsResponse response) {
        return new MetaplusException(buildStepEsFailureMessage(operation, target, step, response));
    }

    private MetaplusException failureWithReason(String operation, String target, String reason) {
        return new MetaplusException(buildReasonFailureMessage(operation, target, reason));
    }

    private MetaplusException failureWithStepAndReason(String operation, String target,
                                                       String step, String reason) {
        return new MetaplusException(buildStepReasonFailureMessage(operation, target, step, reason));
    }

    private String buildEsFailureMessage(String operation, String target, EsResponse response) {
        return "DocDao." + operation + " failed: target=" + target +
                ", status=" + response.getStatusCode() + ", body=" + response.getBody();
    }

    private String buildStepEsFailureMessage(String operation, String target, String step, EsResponse response) {
        return "DocDao." + operation + " failed: target=" + target +
                ", step=" + step + ", status=" + response.getStatusCode() + ", body=" + response.getBody();
    }

    private String buildReasonFailureMessage(String operation, String target, String reason) {
        return "DocDao." + operation + " failed: target=" + target + ", reason=" + reason;
    }

    private String buildStepReasonFailureMessage(String operation, String target, String step, String reason) {
        return "DocDao." + operation + " failed: target=" + target + ", step=" + step + ", reason=" + reason;
    }

    private PatchOptions toSingleDocWriteOptions(PatchOptions patchOptions) {
        if (patchOptions == null) {
            return null;
        }
        PatchOptions copied = new PatchOptions();
        copied.setRefresh(patchOptions.getRefresh());
        copied.setRouteKey(patchOptions.getRouteKey());
        return copied;
    }

    private PatchOptions toByQueryOptions(PatchOptions patchOptions) {
        if (patchOptions == null) {
            return null;
        }
        PatchOptions copied = new PatchOptions();
        copied.setRefresh(patchOptions.getRefresh());
        copied.setRouteKey(patchOptions.getRouteKey());
        copied.setExecutionMode(patchOptions.getExecutionMode());
        return copied;
    }

    private void applyRouteKey(UriComponentsBuilder builder, PatchOptions patchOptions) {
        if (patchOptions.getRouteKey() != null) {
            builder.queryParam("routing", patchOptions.getRouteKey());
        }
    }

    private void applyRefresh(UriComponentsBuilder builder, PatchOptions.RefreshMode refreshMode) {
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

    private void applyExecutionMode(UriComponentsBuilder builder, PatchOptions.ExecutionMode executionMode) {
        if (executionMode == null) {
            return;
        }
        builder.queryParam("wait_for_completion", executionMode == PatchOptions.ExecutionMode.SYNC);
    }

    private Script copyScriptWithComposedSource(@NonNull Script script, @NonNull String domainName) {
        Script copied = new Script();
        copied.mergeWithCopy(script);
        copied.setSource(valuesStore.composeScript(domainName, script.getSource()));
        return copied;
    }


}
