package dev.metaplus.core.model.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonRpcResponseTest {

    @Test
    void errorAccessorsCreateAndReadNestedErrorPayload() {
        JsonRpcResponse response = new JsonRpcResponse();

        response.setErrorCode(4001);
        response.setErrorMsg("invalid request");

        assertEquals("2.0", response.getJsonrpc());
        assertEquals(4001, response.getErrorCode());
        assertEquals("invalid request", response.getErrorMsg());
    }
}
