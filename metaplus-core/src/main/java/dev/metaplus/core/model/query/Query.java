package dev.metaplus.core.model.query;


import dev.metaplus.core.json.Jsons;
import lombok.NonNull;
import org.sjf4j.JsonObject;

import java.time.Instant;

public class Query extends JsonObject {


    /**
     * {
     *     "bool": {
     *         "filter": [
     *           {
     *               "term": {
     *                   "key": "value"
     *               }
     *           }
     *         ]
     *     }
     * }
     */
    public void addBoolFilterTerm(@NonNull String key, Object value) {
        if (value == null) return;
        Jsons.cachedPath("/bool/filter/-/term").ensurePut(this, JsonObject.of(key, value));
    }


    /**
     * {
     *     "bool": {
     *         "filter": [
     *           {
     *               "range": {
     *                  "key": {
     *                      "gte": "dateStart",
     *                      "lt": "dateEnd"
     *                  }
     *               }
     *           }
     *         ]
     *     }
     * }
     */
    public void addBoolFilterRange(@NonNull String key, Instant dateStart, Instant dateEnd) {
        JsonObject rangeValue = new JsonObject();
        if (dateStart != null) {
            rangeValue.put("gte", dateStart);
        }
        if (dateEnd != null) {
            rangeValue.put("lt", dateEnd);
        }
        if (!rangeValue.isEmpty()) {
            Jsons.cachedPath("/bool/filter/-/range").ensurePut(this, JsonObject.of(key, rangeValue));
        }
    }

}
