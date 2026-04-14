package dev.metaplus.core.model.mcp;

import dev.metaplus.core.json.Jsons;
import lombok.Getter;
import lombok.Setter;
import org.sjf4j.JsonObject;

@Getter @Setter
public class JsonRpcResponse extends JsonObject {

    private String jsonrpc = "2.0";
    private String id;
    private JsonObject result;
    private JsonObject error;

    public int getErrorCode() {
        return Jsons.cachedPath("$.error.code").getInt(this, 0);
    }
    public void setErrorCode(int errorCode) {
        Jsons.cachedPath("$.error.code").ensurePut(this, errorCode);
    }

    public String getErrorMsg() {
        return Jsons.cachedPath("$.error.msg").getString(this);
    }
    public void setErrorMsg(String errorMsg) {
        Jsons.cachedPath("$.error.msg").ensurePut(this, errorMsg);
    }

}
