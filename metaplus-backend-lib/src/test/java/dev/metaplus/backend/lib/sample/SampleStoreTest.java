package dev.metaplus.backend.lib.sample;

import com.sun.net.httpserver.HttpServer;
import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.backend.lib.es.EsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SampleStoreTest {

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
    void refreshUsesNormalizedFailureMessage() throws Exception {
        server.createContext("/i_metaplus_sample/_refresh",
                exchange -> respond(exchange, 500, "{\"error\":\"boom\"}", "application/json"));

        SampleStore sampleStore = newStore();

        BackendException ex = assertThrows(BackendException.class, sampleStore::refresh);

        assertEquals("SampleStore.refresh failed: target=index=i_metaplus_sample, status=500, body=J{error=boom}",
                ex.getMessage());
    }

    private SampleStore newStore() throws Exception {
        SampleStore store = new SampleStore();
        Field field = SampleStore.class.getDeclaredField("esClient");
        field.setAccessible(true);
        field.set(store, new EsClient(baseUrl));
        return store;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
                                int statusCode,
                                String body,
                                String contentType) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
