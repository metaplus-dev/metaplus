package dev.metaplus.core.model.agg;

import dev.metaplus.core.model.query.Query;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.util.Map;


@Getter @Setter
public class AggRequest extends JsonObject {

    private String domain;
    private Query query;
    private Map<String, Agg> aggs;

    // Agg

    @Getter @Setter
    public static class Agg extends JsonObject {
        // terms / avg / filter...
        private Map<String, Agg> aggs;
    }

}
