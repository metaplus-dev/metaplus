package dev.metaplus.backend.server.domain;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.node.Nodes;

public final class StorageUtil {

    public static String storageIndex(String domain) {
        return "i_metaplus_domain_" + domain;
    }

    public static JsonObject pureStorage(JsonObject mergedStorage) {
        JsonObject pureStorage = mergedStorage.deepCopy();
        pureStorage.walk(Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.BOTTOM_UP, -1, (ps, node) -> {
            if (JsonType.of(node).isObject()) {
                Nodes.removeIfInObject(node, (k, v) -> k.startsWith("$"));
            }
            return true;
        });
        return pureStorage;
    }

}
