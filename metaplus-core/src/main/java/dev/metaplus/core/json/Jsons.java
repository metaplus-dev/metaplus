package dev.metaplus.core.json;

import org.sjf4j.Sjf4j;
import org.sjf4j.path.JsonPath;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Jsons {

    private static final Sjf4j SJF4J;
    static {
        SJF4J = Sjf4j.builder().build();
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return SJF4J.fromJson(json, clazz);
    }

    public static <T> T fromJson(InputStream in, Class<T> clazz) {
        return SJF4J.fromJson(in, clazz);
    }

    public static void toJson(OutputStream out, Object node) {
        SJF4J.toJson(out, node);
    }

    public static String toJsonString(Object node) {
        return SJF4J.toJsonString(node);
    }

    public static byte[] toJsonBytes(Object node) {
        return SJF4J.toJsonBytes(node);
    }

    public static <T> T fromNode(Object node, Class<T> clazz) {
        return SJF4J.fromNode(node, clazz);
    }

    /// Path

    private static final Map<String, JsonPath> PATH_CACHE = new IdentityHashMap<>();

    public static JsonPath cachedPath(String expr) {
        return PATH_CACHE.computeIfAbsent(expr, JsonPath::compile);
    }

}
