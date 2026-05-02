package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import lombok.RequiredArgsConstructor;
import org.sjf4j.JsonObject;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;


@Component
@RequiredArgsConstructor
public class IndexDao {

    private final EsClient esClient;

    /**
     * Check whether one index exists.
     */
    public boolean existIndex(String index) {
        URI uri = UriComponentsBuilder.fromPath("/{index}").build(index);
        EsResponse response = esClient.head(uri);
        if (response.isSuccess()) {
            return true;
        }
        if (response.isNotFound()) {
            return false;
        }
        throw _failureWithEsResponse("existIndex", _targetIndex(index), response);
    }

    /**
     * Create one index from pure storage settings and mappings.
     */
    public void createIndex(String index, JsonObject pureStorage) {
        URI uri = UriComponentsBuilder.fromPath("/{index}").build(index);
        EsResponse response = esClient.put(uri, pureStorage);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("createIndex", _targetIndex(index), response);
        }
    }

    /**
     * Read raw index metadata.
     */
    public JsonObject readIndex(String index) {
        URI uri = UriComponentsBuilder.fromPath("/{index}").build(index);
        EsResponse response = esClient.get(uri);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("readIndex", _targetIndex(index), response);
        }
        return response.getBody();
    }

    /**
     * Update index settings.
     */
    public void updateSettings(String index, JsonObject settings) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_settings")
                .queryParam("reopen", true).build(index);
        EsResponse response = esClient.put(uri, settings);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("updateSettings", _targetIndex(index), response);
        }
    }

    /**
     * Update index mappings.
     */
    public void updateMappings(String index, JsonObject mappings) {
        // why not `_mappings` ?
        URI uri = UriComponentsBuilder.fromPath("/{index}/_mapping").build(index);
        EsResponse response = esClient.put(uri, mappings);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("updateMappings", _targetIndex(index), response);
        }
    }

    /**
     * Delete one index if it exists.
     */
    public void deleteIndex(String index) {
        URI uri = UriComponentsBuilder.fromPath("/{index}").build(index);
        EsResponse response = esClient.delete(uri);
        if (!response.isSuccess() && !response.isNotFound()) {
            throw _failureWithEsResponse("deleteIndex", _targetIndex(index), response);
        }
    }

    /**
     * Refresh one index.
     */
    public void refreshIndex(String index) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_refresh").build(index);
        EsResponse response = esClient.post(uri);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("refreshIndex", _targetIndex(index), response);
        }
    }

    /**
     * Return selected index stats.
     */
    public JsonObject statsIndex(String index) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_stats/docs,indexing,search").build(index);
        EsResponse response = esClient.get(uri);
        if (!response.isSuccess()) {
            throw _failureWithEsResponse("statsIndex", _targetIndex(index), response);
        }
        return response.getBody();
    }

    private String _targetIndex(String index) {
        return "index=" + index;
    }

    private BackendServerException _failureWithEsResponse(String operation, String target, EsResponse response) {
        return new BackendServerException("IndexDao." + operation + " failed for " + target +
                ", status=" + response.getStatusCode() + ", body=" + response.getBody());
    }


}
