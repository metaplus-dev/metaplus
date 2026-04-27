package dev.metaplus.core.model.search;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeProperty;


@Getter @Setter
public class SearchRequest extends JsonObject {

    private JsonArray domains;
    private Query query;
    private int from;
    private int size;
    private JsonArray fields;
    private JsonArray sort;
    @NodeProperty("search_after")
    private JsonArray searchAfter;
    private JsonObject highlight;

}
