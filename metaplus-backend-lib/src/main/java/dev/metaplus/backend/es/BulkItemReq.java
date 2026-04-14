package dev.metaplus.backend.es;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.sjf4j.JsonObject;

import java.util.Objects;

@Data
@AllArgsConstructor
public class BulkItemReq {
    private BulkItemMethod method;
    private String index;
    private String id;
    private JsonObject body;

    public StringBuilder toStringBuilder() {
        StringBuilder sb = new StringBuilder();
        sb.append(buildMetaLine().toJson()).append("\n");

        JsonObject payload = buildPayload();
        if (payload != null) {
            sb.append(payload.toJson()).append("\n");
        }
        return sb;
    }

    private JsonObject buildMetaLine() {
        JsonObject meta = new JsonObject();
        meta.put("_index", Objects.requireNonNull(index, "index is required"));
        if (id != null && !id.isEmpty()) {
            meta.put("_id", id);
        }
        return JsonObject.of(Objects.requireNonNull(method, "method is required").toString(), meta);
    }

    private JsonObject buildPayload() {
        if (method == BulkItemMethod.DELETE) {
            return null;
        }

        JsonObject payload = Objects.requireNonNull(body, "body is required");
        return method == BulkItemMethod.UPDATE ? toUpdatePayload(payload) : payload;
    }

    private JsonObject toUpdatePayload(JsonObject payload) {
        if (payload.keySet().contains("doc")
                || payload.keySet().contains("script")
                || payload.keySet().contains("upsert")
                || payload.keySet().contains("doc_as_upsert")) {
            return payload;
        }
        return JsonObject.of("doc", payload);
    }


}
