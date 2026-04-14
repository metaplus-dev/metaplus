package dev.metaplus.core.model.agg;

import dev.metaplus.core.model.query.Query;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeProperty;

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
