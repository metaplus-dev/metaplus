package dev.metaplus.core.model.mcp;


import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

@Getter @Setter
public class JsonRpcRequest extends JsonObject {

    private String jsonrpc = "2.0";
    private String id;
    private String method;
    private JsonObject params;

}


