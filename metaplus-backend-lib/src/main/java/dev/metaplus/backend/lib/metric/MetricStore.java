package dev.metaplus.backend.lib.metric;

import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.backend.lib.es.BulkItemMethod;
import dev.metaplus.backend.lib.es.BulkItemReq;
import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.core.model.metric.Metric;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.search.Query;
import dev.metaplus.core.model.search.SearchResponse;
import dev.metaplus.core.util.SchemaUtil;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.openhft.hashing.LongHashFunction;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MetricStore {

    private final EsClient esClient;

    private final String indexName;

    public MetricStore(EsClient esClient,
                       @Value("${metaplus.backend.es.indices.metric:i_metaplus_metric}")
                       String indexName) {
        this.esClient = esClient;
        this.indexName = indexName;
    }

    public void refresh() {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_refresh").build(indexName());
        EsResponse response = esClient.post(uri);
        if (!response.isSuccess()) {
            throw new BackendException("Refresh failed. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
    }


    public Result post(@NonNull Metric metric) {
        SchemaUtil.requireValid(metric);

        URI uri = UriComponentsBuilder.fromPath("/{index}/_doc/{id}")
                .build(indexName(), genMetricIdHash(metric));
        EsResponse response = esClient.put(uri, metric);
        if (!response.isSuccess()) {
            throw new BackendException("MetricStore.post metric=" + metric + " fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getResult();
    }

    public Result post(@NonNull List<Metric> metrics) {
        metrics.forEach(SchemaUtil::requireValid);

        List<BulkItemReq> bulkItemReqs = new ArrayList<>(metrics.size());
        metrics.forEach(metric -> {
            bulkItemReqs.add(new BulkItemReq(BulkItemMethod.INDEX, indexName(), genMetricIdHash(metric), metric));
        });
        EsResponse response = esClient.bulk(bulkItemReqs);
        if (!response.isSuccess()) {
            throw new BackendException("MetricStore.post(metrics.size=" + metrics.size() + ") fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getBulkResult();
    }

    public Result post(@NonNull JsonArray metrics) {
        return post(metrics.toList(Metric.class));
    }


    public List<Metric> get(@Valid @NonNull MetricQuery metricQuery) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_search").build(indexName());
        JsonObject reqBody = toSearchRequest(metricQuery);
        reqBody.put("sort", JsonObject.of("startedAt", "asc"));
        reqBody.put("from", metricQuery.getFrom());
        reqBody.put("size", metricQuery.getSize());

        EsResponse response = esClient.post(uri, reqBody);
        if (!response.isSuccess()) {
            throw new BackendException("MetricStore.get metricQuery=" + metricQuery + " fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }

        SearchResponse<Metric> sr = response.getBodyAsSearchResponse(Metric.class);
        log.debug("Get hits={}/{}, query={}", sr.getHitsSize(), sr.getTotal(), metricQuery);
        return sr.getSources();
    }


    public Result delete(@Valid @NonNull MetricQuery metricQuery) {
        URI uri = UriComponentsBuilder.fromPath("/{index}/_delete_by_query").build(indexName());
        JsonObject reqBody = toSearchRequest(metricQuery);

        EsResponse response = esClient.post(uri, reqBody);
        if (!response.isSuccess()) {
            throw new BackendException("MetricStore.delete metricQuery=" + metricQuery + " fail. Es.res code:"
                    + response.getStatusCode() + ", body: " + response.getBody());
        }
        return response.getOpResult();
    }



    /// static

    private static final LongHashFunction XX_HASH = LongHashFunction.xx3();

    // 16 characters
    public static String genMetricIdHash(Metric metric) {
        String idStr = genMetricIdStr(metric);
        if (idStr.length() > 500) {
            // When the ID length exceeds 500, return its hash.
            long hash = XX_HASH.hashBytes(idStr.getBytes(StandardCharsets.UTF_8));
            return Long.toHexString(hash);
        } else {
            return idStr;
        }
    }

    // metricName-assertName-period-dims-startedAt
    public static String genMetricIdStr(Metric metric) {
        StringBuilder sb = new StringBuilder();
        sb.append(metric.getMetricName())
                .append("|").append(metric.getAssetName())
                .append("|").append(metric.getPeriod());
        if (null != metric.getDims()) {
            Map<String, String> dims = metric.getDims();
            List<String> sortedDims = new ArrayList<>(dims.keySet());
            sortedDims.sort(null);
            for (String dimKey : sortedDims) {
                sb.append("|").append(dimKey).append(":").append(dims.get(dimKey));
            }
        }
        sb.append("|").append(metric.getStartedAt());
        return sb.toString();
    }


    /// private

    private static JsonObject toSearchRequest(MetricQuery metricQuery) {
        Query query = new Query();
        query.addBoolFilterTerm("metricName", metricQuery.getMetricName());
        query.addBoolFilterTerm("assetName", metricQuery.getAssetName());
        query.addBoolFilterTerm("period", metricQuery.getPeriod());
        query.addBoolFilterRange("startedAt", metricQuery.getStartedAtStart(), metricQuery.getStartedAtEnd());
        Map<String, String> dims = metricQuery.getDims();
        if (null != dims) {
            for (String dimKey : dims.keySet()) {
                query.addBoolFilterTerm("dims." + dimKey, dims.get(dimKey));
            }
        }
        return JsonObject.of("query", query);
    }

    private String indexName() {
        return indexName;
    }


}
