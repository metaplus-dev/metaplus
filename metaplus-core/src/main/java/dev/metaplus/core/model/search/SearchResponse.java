package dev.metaplus.core.model.search;

import dev.metaplus.core.model.MetaplusDoc;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.node.Nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {
 *     "hits": {
 *         "total": {
 *             "value": 0
 *         },
 *         "hits": [
 *             {
 *                 "_id": "...",
 *                 "_version": "...",
 *                 "_source": {...}
 *             }
 *         ]
 *     }
 * }
 */
@Getter @Setter
public class SearchResponse<T extends JsonObject> extends JsonObject {

    private long total;
    private Double maxScore;
    private List<Hit<T>> hits;

    // Hit

    @Getter @Setter
    public static class Hit<T> extends JsonObject {
        private String innerId;
        private Double score;
        private T source;
        private JsonObject fields;
        private JsonArray sort;
        private JsonObject highlight;
    }

    public int getHitsSize() {
        return hits == null ? 0 : hits.size();
    }

    public Hit<T> getHit(int index) {
        return hits.get(index);
    }

    public List<T> getSources() {
        if (hits == null) return Collections.emptyList();
        List<T> sources = new ArrayList<>();
        for (Hit<T> hit : hits) {
            sources.add(hit.getSource());
        }
        return sources;
    }

    // Static

    public static <T extends JsonObject> SearchResponse<T> fromEsResBody(JsonObject esResBody, Class<T> clazz) {
        if (null == esResBody) return null;
        SearchResponse<T> response = new SearchResponse<>();
        JsonObject hitsBody = esResBody.getJsonObject("hits");
        if (hitsBody == null) {
            response.setTotal(0);
            response.setHits(new ArrayList<>());
            return response;
        }

        JsonObject totalBody = hitsBody.getJsonObject("total");
        response.setTotal(totalBody == null ? 0 : totalBody.getLong("value", 0L));
        response.setMaxScore(hitsBody.getDouble("max_score"));
        List<Hit<T>> hits = new ArrayList<>();
        List<Object> rawHits = hitsBody.getList("hits");
        if (rawHits != null) {
            for (Object rawHit : rawHits) {
                Hit<T> hit = new Hit<>();
                hit.setInnerId(Nodes.getInObject(rawHit, "_id", String.class));
                hit.setSource(Nodes.getInObject(rawHit, "_source", clazz));
                hits.add(hit);
            }
        }
        response.setHits(hits);
        return response;
    }

}
