package dev.metaplus.backend.lib.es;

import dev.metaplus.core.model.MetaplusDoc;
import dev.metaplus.core.model.patch.Result;
import dev.metaplus.core.model.search.SearchResponse;
import lombok.Getter;
import lombok.ToString;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

@Getter
@ToString
public class EsResponse {

    private static final String[] BULK_ITEM_OP_KEYS = {"index", "create", "update", "delete"};

    private final int statusCode;
    private final JsonObject body;

    public EsResponse(int statusCode) {
        this(statusCode, null);
    }

    public EsResponse(int statusCode, JsonObject body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public boolean isSuccess() {
        return isHttpSuccess() && !hasBulkErrors();
    }

    public boolean isHttpSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }

    public <T> T getInBody(String key, Class<T> clazz) {
        if (body == null) return null;
        return body.get(key, clazz);
    }


    public boolean hasBulkErrors() {
        Object errors = body == null ? null : body.get("errors");
        return body != null
                && Boolean.parseBoolean(String.valueOf(errors))
                && body.getJsonArray("items") != null;
    }

    public JsonArray getBulkItems() {
        if (body == null) return null;
        return body.getJsonArray("items");
    }

    public Result getResult() {
        Result bulkResult = getBulkResult();
        return bulkResult != null ? bulkResult : getOpResult();
    }

    public Result getOpResult() {
        return Result.fromEsBody(body);
    }

    public Result getBulkResult() {
        JsonArray items = getBulkItems();
        if (items == null) {
            return null;
        }

        Result result = new Result();
        for (int i = 0; i < items.size(); i++) {
            Result itemResult = _bulkItemToResult(items.getJsonObject(i));
            if (itemResult != null) {
                result.plus(itemResult);
            }
        }
        return result;
    }

    private static Result _bulkItemToResult(JsonObject bulkItem) {
        if (bulkItem == null) return null;
        for (String opKey : BULK_ITEM_OP_KEYS) {
            JsonObject opBody = bulkItem.getJsonObject(opKey);
            if (opBody != null) {
                return Result.fromEsBody(opBody);
            }
        }
        return Result.fromEsBody(bulkItem);
    }

    /// Search

    public <T extends JsonObject> SearchResponse<T> getBodyAsSearchResponse(Class<T> clazz) {
        return SearchResponse.fromEsResBody(body, clazz);
    }


    public MetaplusDoc getBodyAsMetaplusDoc() {
        return body.get("_source");
    }

}
