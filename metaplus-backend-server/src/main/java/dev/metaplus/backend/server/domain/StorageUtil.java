package dev.metaplus.backend.server.domain;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.node.Nodes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class StorageUtil {

    private static final Set<String> PURE_STORAGE_ROOT_KEYS = Set.of("settings", "mappings", "aliases");

    /**
     * Return the physical index name for one domain.
     */
    public static String storageIndex(String domain) {
        return "i_metaplus_domain_" + domain;
    }

    /**
     * Remove Metaplus-only storage metadata and keep ES index sections only.
     */
    public static JsonObject pureStorage(JsonObject mergedStorage) {
        JsonObject pureStorage = mergedStorage.deepCopy();
        pureStorage.removeIf(entry -> !PURE_STORAGE_ROOT_KEYS.contains(entry.getKey()));
        pureStorage.walk(Nodes.WalkTarget.CONTAINER, Nodes.WalkOrder.BOTTOM_UP, -1, (ps, node) -> {
            if (JsonType.of(node).isObject()) {
                Nodes.removeIfInObject(node, (k, v) -> k.startsWith("$"));
            }
            return true;
        });
        return pureStorage;
    }

}
