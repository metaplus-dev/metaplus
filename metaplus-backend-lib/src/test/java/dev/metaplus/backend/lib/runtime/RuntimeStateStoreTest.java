package dev.metaplus.backend.lib.runtime;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.core.json.Jsons;
import dev.metaplus.core.model.search.SearchRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStateStoreTest {

    private static final String INDEX_NAME = "i_metaplus_runtime";

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void markJobCompletedUsesJobSpecificFieldWithUpsert() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<JsonObject> bodyRef = new AtomicReference<>();
        server.createContext("/" + INDEX_NAME + "/_update/data:system:instance:entity", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            bodyRef.set(_readJsonBody(exchange));
            _respond(exchange, 200, "{\"result\":\"updated\"}", "application/json");
        });

        RuntimeStateStore store = _newStore();
        store.markJobCompleted("data:system:instance:entity", RuntimeJobType.LLM_GEN);

        JsonObject body = bodyRef.get();
        JsonObject doc = body.getJsonObject("doc");
        JsonObject idea = doc.getJsonObject("idea");

        assertEquals("POST", methodRef.get());
        assertEquals("data:system:instance:entity", idea.getString("fqmn"));
        assertEquals("data", idea.getString("domain"));
        assertTrue(doc.containsKey("lastLlmGenAt"));
        assertEquals(Boolean.TRUE, body.getBoolean("doc_as_upsert"));
    }

    @Test
    void searchPendingByJobBuildsPendingSearchWithStablePaging() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<SearchRequest> requestRef = new AtomicReference<>();
        server.createContext("/" + INDEX_NAME + "/_search", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            requestRef.set(_readBody(exchange, SearchRequest.class));
            _respond(exchange, 200, "{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}", "application/json");
        });

        RuntimeStateStore store = _newStore();
        store.searchPendingByJob("data", RuntimeJobType.SAMPLING, "2026-03-25T10:00:00Z", 10,
                JsonArray.of("2026-03-24T10:00:00Z", "2026-03-25T09:00:00Z", "data:system:instance:entity"));

        SearchRequest request = requestRef.get();
        String queryJson = Jsons.toJsonString(request.getQuery());
        String sortJson = Jsons.toJsonString(request.getSort());

        assertEquals("POST", methodRef.get());
        assertNotNull(request);
        assertEquals(10, request.getSize());
        assertTrue(queryJson.contains("\"idea.domain\":\"data\""));
        assertTrue(queryJson.contains("\"lastSamplingAt\""));
        assertTrue(queryJson.contains("\"deletedAt\""));
        assertTrue(sortJson.contains("\"idea.fqmn\":\"asc\""));
        assertEquals(3, request.getSearchAfter().size());
        assertEquals("data:system:instance:entity", request.getSearchAfter().getString(2));
    }

    @Test
    void getReturnsNullWhenDocumentIsMissing() {
        server.createContext("/" + INDEX_NAME + "/_doc/data:system:instance:entity",
                exchange -> _respond(exchange, 404, "{}", "application/json"));

        RuntimeStateStore store = _newStore();

        assertNull(store.get("data:system:instance:entity"));
    }

    @Test
    void clearByDomainUsesDeleteByQueryWithDomainFilter() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<SearchRequest> requestRef = new AtomicReference<>();
        server.createContext("/" + INDEX_NAME + "/_delete_by_query", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            requestRef.set(_readBody(exchange, SearchRequest.class));
            _respond(exchange, 200, "{\"deleted\":2,\"total\":2}", "application/json");
        });

        RuntimeStateStore store = _newStore();
        store.clearByDomain("data");

        assertEquals("POST", methodRef.get());
        assertTrue(Jsons.toJsonString(requestRef.get().getQuery()).contains("\"idea.domain\":\"data\""));
    }

    @Test
    void getUsesNormalizedFailureMessage() {
        server.createContext("/" + INDEX_NAME + "/_doc/data:system:instance:entity",
                exchange -> _respond(exchange, 500, "{\"error\":\"boom\"}", "application/json"));

        RuntimeStateStore store = _newStore();

        BackendException ex = assertThrows(BackendException.class,
                () -> store.get("data:system:instance:entity"));

        assertEquals("RuntimeStateStore.get failed for fqmn=data:system:instance:entity, status=500, body=J{error=boom}",
                ex.getMessage());
    }

    private RuntimeStateStore _newStore() {
        EsClient client = new EsClient(baseUrl);
        return new RuntimeStateStore(client, INDEX_NAME);
    }

    private static String _readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static JsonObject _readJsonBody(HttpExchange exchange) throws IOException {
        return _readBody(exchange, JsonObject.class);
    }

    private static <T> T _readBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        return Jsons.fromJson(_readBody(exchange), clazz);
    }

    private static void _respond(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
