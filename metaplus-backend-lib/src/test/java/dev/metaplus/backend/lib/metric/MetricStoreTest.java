package dev.metaplus.backend.lib.metric;

import com.sun.net.httpserver.HttpServer;
import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.backend.lib.es.EsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricStoreTest {

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
    void refreshUsesNormalizedFailureMessage() {
        server.createContext("/i_metric_test/_refresh",
                exchange -> _respond(exchange, 500, "{\"error\":\"boom\"}", "application/json"));

        MetricStore metricStore = new MetricStore(new EsClient(baseUrl), "i_metric_test");

        BackendException ex = assertThrows(BackendException.class, metricStore::refresh);

        assertEquals("MetricStore.refresh failed: target=index=i_metric_test, status=500, body=J{error=boom}",
                ex.getMessage());
    }

    @Test
    void getUsesNormalizedFailureMessage() {
        server.createContext("/i_metric_test/_search",
                exchange -> _respond(exchange, 500, "{\"error\":\"bad_metric\"}", "application/json"));

        MetricStore metricStore = new MetricStore(new EsClient(baseUrl), "i_metric_test");
        MetricQuery query = new MetricQuery();
        query.setMetricName("rows");
        query.setAssetName("orders");
        query.setPeriod("PT1M");
        query.setStartedAtStart(Instant.parse("2026-04-27T00:00:00Z"));

        BackendException ex = assertThrows(BackendException.class, () -> metricStore.get(query));

        assertEquals("MetricStore.get failed: target=metricName=rows, assetName=orders, period=PT1M, startedAtStart=2026-04-27T00:00:00Z, startedAtEnd=null, from=0, size=1000, status=500, body=J{error=bad_metric}",
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
