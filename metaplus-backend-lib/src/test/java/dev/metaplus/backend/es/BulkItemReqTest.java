package dev.metaplus.backend.es;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BulkItemReqTest {

    @Test
    void indexItemRendersNdjsonLines() {
        BulkItemReq item = new BulkItemReq(
                BulkItemMethod.INDEX,
                "metrics",
                "m1",
                JsonObject.of("name", "latency")
        );

        assertEquals(
                "{\"index\":{\"_index\":\"metrics\",\"_id\":\"m1\"}}\n"
                        + "{\"name\":\"latency\"}\n",
                item.toStringBuilder().toString()
        );
    }

    @Test
    void updateItemWrapsPlainBodyAsDoc() {
        BulkItemReq item = new BulkItemReq(
                BulkItemMethod.UPDATE,
                "locks",
                "l1",
                JsonObject.of("released", true)
        );

        assertEquals(
                "{\"update\":{\"_index\":\"locks\",\"_id\":\"l1\"}}\n"
                        + "{\"doc\":{\"released\":true}}\n",
                item.toStringBuilder().toString()
        );
    }

    @Test
    void updateItemKeepsExplicitUpdatePayload() {
        BulkItemReq item = new BulkItemReq(
                BulkItemMethod.UPDATE,
                "locks",
                "l1",
                JsonObject.of("doc", JsonObject.of("released", true), "doc_as_upsert", true)
        );

        assertEquals(
                "{\"update\":{\"_index\":\"locks\",\"_id\":\"l1\"}}\n"
                        + "{\"doc\":{\"released\":true},\"doc_as_upsert\":true}\n",
                item.toStringBuilder().toString()
        );
    }
}
