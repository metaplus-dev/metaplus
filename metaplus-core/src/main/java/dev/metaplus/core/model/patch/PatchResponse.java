package dev.metaplus.core.model.patch;


import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.schema.ValidJsonSchema;

/**
 *
 * {
 *     "code": 200,
 *     "msg": "...",
 *     "body": {...}
 * }
 *
 */
@Getter @Setter
@ValidJsonSchema(ref = "patch-response.json")
public class PatchResponse extends JsonObject {

    private final int code;
    private final String msg;
    private final Result body;

    @NodeCreator
    public PatchResponse(@NodeProperty("code") int code,
                         @NodeProperty("msg") String msg,
                         @NodeProperty("body") Result body) {
        this.code = code;
        this.msg = msg;
        this.body = body;
    }

    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    public boolean isNotFound() {
        return code == 404;
    }

    /// Final http response

    public static PatchResponse ok() {
        return new PatchResponse(200, "ok", null);
    }
    public static PatchResponse ok(Result body) {
        return new PatchResponse(200, "ok", body);
    }

    public static PatchResponse notFound() {
        return new PatchResponse(404, "not found", null);
    }
    public static PatchResponse notFound(Result body) {
        return new PatchResponse(404, "not found", body);
    }

}
