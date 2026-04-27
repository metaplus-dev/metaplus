package dev.metaplus.backend.lib.runtime;


import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.core.json.Jsons;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.search.Query;
import dev.metaplus.core.model.search.SearchResponse;
import dev.metaplus.core.util.DateUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Component
public class RuntimeStateStore {
    private final EsClient esClient;
    private final String indexName;

    public RuntimeStateStore(EsClient esClient,
                             @Value("${metaplus.backend.es.indices.runtime-state:i_metaplus_runtime}")
                             String indexName) {
        this.esClient = esClient;
        this.indexName = indexName;
    }

    /**
     * Reads the runtime sidecar state for one FQMN.
     */
    public RuntimeState get(@NonNull String fqmn) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc/{fqmn}")
                .build(indexName(), fqmn);

        EsResponse response = esClient.get(uri);
        if (response.isNotFound()) {
            return null;
        } else if (response.isSuccess()) {
            return response.getInBody("_source", RuntimeState.class);
        } else {
            throw failureWithEsResponse("get", targetFqmn(fqmn), response);
        }
    }

    /** Marks that the source-aligned object was upserted. */
    public void markUpserted(@NonNull String fqmn) {
        setField(fqmn, "upsertedAt", DateUtil.now());
    }

    /** Marks that the source-aligned object was deleted. */
    public void markDeleted(@NonNull String fqmn) {
        setField(fqmn, "deletedAt", DateUtil.now());
    }

    /** Marks one runtime job as completed for the target FQMN. */
    public void markJobCompleted(@NonNull String fqmn, @NonNull RuntimeJobType jobType) {
        setField(fqmn, jobType.completedAtFieldName(), DateUtil.now());
    }

    /**
     * Returns pending runtime work for one job type.
     *
     * <p>A document is pending when it belongs to the domain, is not marked deleted,
     * and the job completion timestamp is either missing or older than the supplied bound.</p>
     *
     * <p>The sort order includes fqmn as a stable tie-breaker so search_after paging remains deterministic.</p>
     */
    public SearchResponse<RuntimeState> searchPendingByJob(@NonNull String domainName,
                                                           @NonNull RuntimeJobType jobType,
                                                           @NonNull String lastCompletedAtEnd,
                                                           int size,
                                                           JsonArray searchAfter) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_search")
                .build(indexName());

        String completedAtFieldName = jobType.completedAtFieldName();

        Query query = new Query();
        query.addBoolFilterTerm("idea.domain", domainName);
        Jsons.cachedPath("/bool/should/-/bool/must_not/exists/field")
                .ensurePut(query, completedAtFieldName);
        Jsons.cachedPath("/bool/should/-/range")
                .ensurePut(query, JsonObject.of(
                        completedAtFieldName, JsonObject.of("lt", lastCompletedAtEnd))
                );
        Jsons.cachedPath("/bool/minimum_should_match").ensurePut(query, 1);
        Jsons.cachedPath("/bool/must_not/-/exists/field").ensurePut(query, "deletedAt");

        JsonObject reqBody = JsonObject.of(
                "query", query,
                "sort", JsonArray.of(
                        JsonObject.of(completedAtFieldName, JsonObject.of(
                                "order", "asc",
                                "missing", "_first")),
                        JsonObject.of("upsertedAt", "asc"),
                        JsonObject.of("idea.fqmn", "asc")),
                "size", size);
        if (null != searchAfter) {
            reqBody.put("search_after", searchAfter);
        }

        EsResponse response = esClient.post(uri, reqBody);
        if (!response.isSuccess()) {
            throw failureWithEsResponse("searchPendingByJob",
                    "domain=" + domainName + ", jobType=" + jobType + ", lastCompletedAtEnd=" + lastCompletedAtEnd
                            + ", size=" + size + ", searchAfter=" + searchAfter,
                    response);
        }

        return response.getBodyAsSearchResponse(RuntimeState.class);
    }


    /**
     * Administrative cleanup operation for removing all runtime state documents in one domain.
     * Normal worker flows should prefer per-FQMN updates instead of bulk clears.
     */
    public Result clearByDomain(@NonNull String domainName) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_delete_by_query")
                .build(indexName());

        Query query = new Query();
        query.addBoolFilterTerm("idea.domain", domainName);
        JsonObject reqBody = JsonObject.of("query", query);

        EsResponse response = esClient.post(uri, reqBody);
        if (!response.isSuccess()) {
            throw failureWithEsResponse("clearByDomain", targetDomain(domainName), response);
        }
        return response.getResult();
    }


    /// private

    private void setField(@NonNull String fqmn, @NonNull String fieldName, @NonNull Object fieldValue) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_update/{fqmn}")
                .build(indexName(), fqmn);

        JsonObject body = JsonObject.of(
                "doc", JsonObject.of(
                        "idea", Idea.of(fqmn),
                        fieldName, fieldValue),
                "doc_as_upsert", true);
        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw failureWithEsResponse("setField",
                    targetFqmn(fqmn) + ", fieldName=" + fieldName + ", fieldValue=" + fieldValue,
                    response);
        }
    }

    private String indexName() {
        return indexName;
    }

    private String targetFqmn(String fqmn) {
        return "fqmn=" + fqmn;
    }

    private String targetDomain(String domainName) {
        return "domain=" + domainName;
    }

    private BackendException failureWithEsResponse(String operation, String target, EsResponse response) {
        return new BackendException("RuntimeStateStore." + operation + " failed: target=" + target +
                ", status=" + response.getStatusCode() + ", body=" + response.getBody());
    }


}
