package dev.metaplus.core.model.agg;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

import java.util.Map;


@Getter @Setter
public class AggResponse extends JsonObject {
    private Map<String, Aggregation> aggregations;

    // Aggregation

    @Getter @Setter
    public static class Aggregation extends JsonObject {
        private Double value;
        private Object buckets;

        public long getDocCount() {
            return getLong("doc_count", 0);
        }
    }

}
