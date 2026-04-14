package dev.metaplus.backend.es;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EsClientTest {

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
    void postWritesJsonBodyAndReadsJsonResponse() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> contentTypeRef = new AtomicReference<>();
        server.createContext("/metrics/_search", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            bodyRef.set(readBody(exchange));
            contentTypeRef.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, "{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}", "application/json");
        });

        EsClient client = newClient();
        EsResponse response = client.post(URI.create("/metrics/_search"),
                JsonObject.of("query", JsonObject.of("match_all", new JsonObject())));

        assertTrue(response.isSuccess());
        assertEquals("POST", methodRef.get());
        assertTrue(contentTypeRef.get().startsWith("application/json"));
        assertTrue(bodyRef.get().contains("\"query\""));
    }

    @Test
    void bulkTreatsBodyErrorsAsFailure() throws Exception {
        AtomicReference<String> contentTypeRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        server.createContext("/_bulk", exchange -> {
            contentTypeRef.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            bodyRef.set(readBody(exchange));
            respond(exchange, 200,
                    "{\"errors\":true,\"items\":[{\"index\":{\"status\":409,\"error\":{\"type\":\"version_conflict_engine_exception\"}}}]}",
                    "application/json");
        });

        EsClient client = newClient();
        EsResponse response = client.bulk(List.of(
                new BulkItemReq(BulkItemMethod.INDEX, "metrics", "m1", JsonObject.of("name", "latency"))
        ));

        assertTrue(response.isHttpSuccess());
        assertTrue(response.hasBulkErrors());
        assertFalse(response.isSuccess());
        assertTrue(contentTypeRef.get().startsWith("application/x-ndjson"));
        assertTrue(bodyRef.get().contains("\"index\""));
    }

    @Test
    void headReturnsStatusWithoutDeserializingBody() throws Exception {
        server.createContext("/metrics", exchange -> respond(exchange, 404, "", null));

        EsClient client = newClient();
        EsResponse response = client.head(URI.create("/metrics"));

        assertTrue(response.isNotFound());
        assertEquals(404, response.getStatusCode());
    }

    @Test
    void unauthorizedResponseRaisesClearAuthError() {
        server.createContext("/metrics", exchange -> respond(exchange, 401, "{}", "application/json"));

        EsClient client = newClient();

        EsClientException exception = assertThrows(EsClientException.class,
                () -> client.get(URI.create("/metrics")));
        assertTrue(exception.getMessage().contains("401 Unauthorized"));
    }

    @Test
    void forbiddenResponseRaisesClearPermissionError() {
        server.createContext("/metrics", exchange -> respond(exchange, 403, "{}", "application/json"));

        EsClient client = newClient();

        EsClientException exception = assertThrows(EsClientException.class,
                () -> client.get(URI.create("/metrics")));
        assertTrue(exception.getMessage().contains("403 Forbidden"));
    }

    @Test
    void basicAuthAddsAuthorizationHeader() throws Exception {
        AtomicReference<String> authHeaderRef = new AtomicReference<>();
        server.createContext("/metrics", exchange -> {
            authHeaderRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{}", "application/json");
        });

        EsClient client = newClient("basic", "alice", "secret", null, null);
        client.get(URI.create("/metrics"));

        assertEquals("Basic YWxpY2U6c2VjcmV0", authHeaderRef.get());
    }

    @Test
    void bearerAuthAddsAuthorizationHeader() throws Exception {
        AtomicReference<String> authHeaderRef = new AtomicReference<>();
        server.createContext("/metrics", exchange -> {
            authHeaderRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{}", "application/json");
        });

        EsClient client = newClient("bearer", null, null, "token-123", null);
        client.get(URI.create("/metrics"));

        assertEquals("Bearer token-123", authHeaderRef.get());
    }

    @Test
    void apiKeyAuthAddsAuthorizationHeader() throws Exception {
        AtomicReference<String> authHeaderRef = new AtomicReference<>();
        server.createContext("/metrics", exchange -> {
            authHeaderRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{}", "application/json");
        });

        EsClient client = newClient("api_key", null, null, null, "abc123");
        client.get(URI.create("/metrics"));

        assertEquals("ApiKey abc123", authHeaderRef.get());
    }

    @Test
    void missingAuthCredentialFailsFast() {
        EsClient client = newClient("bearer", null, null, null, null);

        EsClientException exception = assertThrows(EsClientException.class,
                () -> client.get(URI.create("/metrics")));
        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertTrue(exception.getCause().getMessage().contains("metaplus.backend.es.auth.bearerToken"));
    }

    @Test
    void invalidTlsTrustStorePathFailsFast() {
        EsClient client = newClient();
        ReflectionTestUtils.setField(client, "tlsTrustStorePath", "/tmp/metaplus-missing-truststore.p12");
        ReflectionTestUtils.setField(client, "tlsTrustStorePassword", "secret");

        EsClientException exception = assertThrows(EsClientException.class,
                () -> client.get(URI.create("/metrics")));
        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertTrue(exception.getCause().getMessage().contains("Failed to load keystore"));
    }

    private EsClient newClient() {
        return newClient("none", null, null, null, null);
    }

    private EsClient newClient(String authType, String username, String password, String bearerToken, String apiKey) {
        EsClient client = new EsClient();
        ReflectionTestUtils.setField(client, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "authType", authType);
        ReflectionTestUtils.setField(client, "authUsername", username);
        ReflectionTestUtils.setField(client, "authPassword", password);
        ReflectionTestUtils.setField(client, "authBearerToken", bearerToken);
        ReflectionTestUtils.setField(client, "authApiKey", apiKey);
        return client;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
        } else {
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
