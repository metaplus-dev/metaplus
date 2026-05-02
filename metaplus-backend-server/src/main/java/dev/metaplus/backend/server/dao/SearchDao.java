package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.backend.server.domain.StorageUtil;
import dev.metaplus.core.json.Jsons;
import dev.metaplus.core.model.agg.AggRequest;
import dev.metaplus.core.model.agg.AggResponse;
import dev.metaplus.core.model.search.SearchOptions;
import dev.metaplus.core.model.search.SearchRequest;
import dev.metaplus.core.model.search.SearchResponse;
import lombok.NonNull;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SearchDao {

    @Autowired
    private EsClient esClient;

    /**
     * Search across one or more domains.
     */
    public SearchResponse<JsonObject> search(@NonNull Set<String> domainNames,
                                             @NonNull SearchRequest searchRequest,
                                             SearchOptions searchOptions) {
        List<String> indexNames = _domainNamesToIndexNames(domainNames);
        String indexPath = String.join(",", indexNames);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_search")
                .queryParam("version", true);
        _applySearchOptions(builder, searchOptions);
        URI uri = builder.build(false).expand(indexPath).toUri();

        EsResponse response = esClient.post(uri, searchRequest);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("search", _targetDomains(domainNames), response);
        }
        return response.getBodyAsSearchResponse(JsonObject.class);
    }

    /**
     * Search within one domain.
     */
    public SearchResponse<JsonObject> search(@NonNull String domainName,
                                             @NonNull SearchRequest searchRequest,
                                             SearchOptions searchOptions) {
        return search(Set.of(domainName), searchRequest, searchOptions);
    }

    /**
     * Run aggregations across one or more domains.
     */
    public AggResponse agg(@NonNull Set<String> domainNames,
                           @NonNull AggRequest aggRequest,
                           SearchOptions searchOptions) {
        if (aggRequest.getAggs() == null) {
            throw new IllegalArgumentException("aggRequest.aggs must not be null");
        }

        List<String> indexNames = _domainNamesToIndexNames(domainNames);
        String indexPath = String.join(",", indexNames);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/{index}/_search")
                .queryParam("version", true);
        _applySearchOptions(builder, searchOptions);
        URI uri = builder.build(false).expand(indexPath).toUri();

        JsonObject reqBody = JsonObject.of(
                "query", aggRequest.getQuery(),
                "aggs", aggRequest.getAggs(),
                "size", 0);

        EsResponse response = esClient.post(uri, reqBody);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("agg", _targetDomains(domainNames), response);
        }
        return Jsons.fromNode(response.getBody(), AggResponse.class);
    }

    /**
     * Run aggregations within one domain.
     */
    public AggResponse agg(@NonNull String domainName,
                           @NonNull AggRequest aggRequest,
                           SearchOptions searchOptions) {
        return agg(Set.of(domainName), aggRequest, searchOptions);
    }

    private List<String> _domainNamesToIndexNames(Set<String> domainNames) {
        Assert.notEmpty(domainNames, "domainNames must not be empty");
        return domainNames.stream()
                .sorted()
                .map(StorageUtil::storageIndex)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private void _applySearchOptions(UriComponentsBuilder builder, SearchOptions searchOptions) {
        if (searchOptions == null) {
            return;
        }
        if (searchOptions.getRouteKey() != null) {
            builder.queryParam("routing", searchOptions.getRouteKey());
        }
    }

    private String _targetDomains(Set<String> domainNames) {
        return "domains=" + domainNames.stream().sorted().toList();
    }

    private BackendServerException _failureWithEsResponse(String operation, String target, EsResponse response) {
        return new BackendServerException("SearchDao." + operation + " failed for " + target
                + ", status=" + response.getStatusCode() + ", body=" + response.getBody());
    }
}
