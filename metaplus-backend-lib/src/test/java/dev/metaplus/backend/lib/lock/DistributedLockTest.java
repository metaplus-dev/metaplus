package dev.metaplus.backend.lib.lock;

import com.sun.net.httpserver.HttpServer;
import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.backend.lib.es.EsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributedLockTest {

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
    void lockUsesNormalizedFailureMessage() {
        server.createContext("/i_lock_test/_doc/job-a",
                exchange -> _respond(exchange, 500, "{\"error\":\"boom\"}", "application/json"));

        DistributedLock lock = new DistributedLock(new EsClient(baseUrl), "i_lock_test");

        BackendException ex = assertThrows(BackendException.class,
                () -> lock.lock("job-a", 30, "worker-1"));

        assertEquals("DistributedLock.lock failed: target=lockId=job-a, status=500, body=J{error=boom}",
                ex.getMessage());
    }

    @Test
    void lockUsesNormalizedReasonWhenExpiredAtMissing() {
        server.createContext("/i_lock_test/_doc/job-a",
                exchange -> _respond(exchange, 200, "{\"_source\":{\"isReleased\":false}}", "application/json"));

        DistributedLock lock = new DistributedLock(new EsClient(baseUrl), "i_lock_test");

        BackendException ex = assertThrows(BackendException.class,
                () -> lock.lock("job-a", 30, "worker-1"));

        assertEquals("DistributedLock.lock failed: target=lockId=job-a, reason=missing expiredAt",
                ex.getMessage());
    }

    private static void _respond(com.sun.net.httpserver.HttpExchange exchange,
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
