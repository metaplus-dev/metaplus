package dev.metaplus.backend.sample;


import dev.metaplus.backend.BackendException;
import dev.metaplus.backend.es.BulkItemMethod;
import dev.metaplus.backend.es.BulkItemReq;
import dev.metaplus.backend.es.EsClient;
import dev.metaplus.backend.es.EsResponse;
import dev.metaplus.core.model.Idea;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.patch.Script;
import dev.metaplus.core.model.query.Query;
import dev.metaplus.core.model.search.SearchRequest;
import dev.metaplus.core.model.search.SearchResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SampleStore {

    @Autowired
    private EsClient esClient;

    private static final String INDEX_SAMPLE = "i_metaplus_sample";


    public void refresh() {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_refresh").build(INDEX_SAMPLE);
        EsResponse response = esClient.post(uri);
        if (!response.isSuccess()) {
            throw new BackendException("Refresh failed. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
    }


    public Result post(@NonNull Sample sample) {
        if (null == sample.getData() || sample.getData().isEmpty()) {
            throw new IllegalArgumentException("Data is empty.");
        }
        if (null == sample.getCreatedAt()) {
            sample.setCreatedAt(Instant.now());
        }

        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc")
                .build(INDEX_SAMPLE);
        EsResponse response = esClient.post(uri, sample);
        if (!response.isSuccess()) {
            throw new BackendException("SampleStore.post sample=" + sample + " fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getOpResult();
    }


    public Result post(@NonNull String fqmn, @NonNull JsonObject data) {
        return post(Idea.of(fqmn), data);
    }

    public Result post(@NonNull Idea idea, @NonNull JsonObject data) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data is empty.");

        Sample sample = new Sample();
        sample.setIdea(idea);
        sample.setData(data);
        sample.setCreatedAt(Instant.now());
        sample.setLocked(false);
        return post(sample);
    }


    public Result post(@NonNull String fqmn, @NonNull List<JsonObject> dataList) {
        return post(Idea.of(fqmn), dataList);
    }

    public Result post(@NonNull Idea idea, @NonNull List<JsonObject> dataList) {
        dataList.removeIf(JsonObject::isEmpty);
        if (dataList.isEmpty()) return new Result();

        List<BulkItemReq> bulkItemReqs = new ArrayList<>(dataList.size());
        dataList.forEach(data -> {
            Sample sample = new Sample();
            sample.setIdea(idea);
            sample.setData(data);
            sample.setCreatedAt(Instant.now());
            sample.setLocked(false);
            bulkItemReqs.add(new BulkItemReq(BulkItemMethod.INDEX, INDEX_SAMPLE, null, sample));
        });

        EsResponse response = esClient.bulk(bulkItemReqs);
        if (!response.isSuccess()) {
            throw new BackendException("SampleStore.post(dataList.size=" + dataList.size() + ") fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getBulkResult();
    }


    public List<Sample> get(@NonNull String fqmn, int from, int size) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_search").build(INDEX_SAMPLE);

        SearchRequest search = new SearchRequest();
        Query query = new Query();
        query.addBoolFilterTerm("idea.fqmn", fqmn);
        search.setQuery(query);
        search.setSort(JsonArray.of(
                JsonObject.of("locked", "desc"),
                JsonObject.of("createdAt", "desc")
        ));
        search.setFrom(from);
        search.setSize(size);

        EsResponse response = esClient.post(uri, search);
        if (!response.isSuccess()) {
            throw new BackendException("SampleStore.get fqmn=" + fqmn + " fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }

        SearchResponse<Sample> sr = response.getSearchResponse(Sample.class);
        log.debug("Get hits={}/{}, fqmn={}", sr.getHitsSize(), sr.getTotal(), fqmn);
        return sr.getSources();
    }


    public List<Sample> get100(@NonNull String fqmn) {
        return get(fqmn, 0, 100);
    }


    public Result lock(@NonNull String fqmn, @NonNull String id, boolean locked) {
        Assert.hasText(id, "id is empty");
        URI uri = UriComponentsBuilder.fromPath("/{index}/_update/{id}").build(INDEX_SAMPLE, id);

        JsonObject body = JsonObject.of("doc", JsonObject.of("locked", locked));
        EsResponse response = esClient.post(uri, body);
        if (!response.isSuccess()) {
            throw new BackendException("SampleStore.lock fqmn=" + fqmn + " id=" + id +
                    "fail. Es.res code:" + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getOpResult();
    }


    public Result lock(@NonNull String fqmn, @NonNull String id) {
        return lock(fqmn, id, true);
    }


    public Result clear(@NonNull String fqmn, boolean forced) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_delete_by_query").build(INDEX_SAMPLE);

        SearchRequest search = new SearchRequest();
        Query query = new Query();
        query.addBoolFilterTerm("idea.fqmn", fqmn);
        if (!forced) query.addBoolFilterTerm("locked", false);
        search.setQuery(query);

        EsResponse response = esClient.post(uri, search);
        if (!response.isSuccess()) {
            throw new BackendException("SampleStore.clear fqmn=" + fqmn + " fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getOpResult();
    }


    public Result clear(@NonNull String fqmn) {
        return clear(fqmn, false);
    }


    public Result rename(@NonNull String oldFqmn, @NonNull Idea newIdea) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_update_by_query").build(INDEX_SAMPLE);
        Query query = new Query();
        query.addBoolFilterTerm("idea.fqmn", oldFqmn);
        Script script = new Script();
        script.setSource("ctx._source.idea = params.idea;");
        script.setParams(JsonObject.of("idea", newIdea));

        EsResponse response = esClient.post(uri, JsonObject.of("query", query, "script", script));
        if (!response.isSuccess()) {
            throw new BackendException("SampleStore.rename oldFqmn=" + oldFqmn + " newFqmn=" + newIdea +
                    "fail. Es.res code:" + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getOpResult();
    }



}
