package dev.metaplus.core.adapter;

import dev.metaplus.core.config.GlobalConfig;
import dev.metaplus.core.model.mcp.JsonRpcRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAdaptersTest {

    private static final String CORE_KEY = "metaplus.core.path";

    @AfterEach
    void tearDown() throws Exception {
        Method method = GlobalConfig.class.getDeclaredMethod("clearForTest");
        method.setAccessible(true);
        method.invoke(null);
        System.clearProperty(CORE_KEY);
    }

    @Test
    void initializerLoadsMetaplusScopedSpringProperties() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Collections.singletonMap(CORE_KEY, "/srv/metaplus")));

        new SpringMetaplusConfigInitializer().initialize(context);

        assertEquals("/srv/metaplus", GlobalConfig.getString(CORE_KEY));
    }

    @Test
    void mvcConverterReadsAndWritesJsonPayloads() throws Exception {
        SpringMvcSjf4jMessageConverter converter = new SpringMvcSjf4jMessageConverter();

        MockHttpInputMessage inputMessage = new MockHttpInputMessage(
                "{\"id\":\"1\",\"method\":\"tools/list\"}".getBytes(StandardCharsets.UTF_8));
        JsonRpcRequest request = (JsonRpcRequest) converter.read(JsonRpcRequest.class, inputMessage);

        MockHttpOutputMessage outputMessage = new MockHttpOutputMessage();
        converter.write(JsonObject.of("ok", true), MediaType.APPLICATION_JSON, outputMessage);

        assertTrue(converter.canRead(JsonRpcRequest.class, MediaType.APPLICATION_JSON));
        assertTrue(converter.canWrite(JsonObject.class, MediaType.valueOf("application/problem+json")));
        assertEquals("1", request.getId());
        assertEquals("tools/list", request.getMethod());
        assertTrue(outputMessage.getBodyAsString().contains("\"ok\":true"));
    }

    @Test
    void webFluxReaderAcceptsJsonMediaTypesAndParsesBody() {
        SpringWebFluxSjf4jMessageReader reader = new SpringWebFluxSjf4jMessageReader();
        MockServerHttpRequest request = MockServerHttpRequest.post("/")
                .contentType(MediaType.valueOf("application/vnd.api+json"))
                .body("{\"id\":\"rpc-1\",\"method\":\"tools/call\"}");

        JsonRpcRequest parsed = (JsonRpcRequest) reader.readMono(
                ResolvableType.forClass(JsonRpcRequest.class),
                request,
                Collections.emptyMap()
        ).block();

        assertTrue(reader.canRead(ResolvableType.forClass(JsonRpcRequest.class), null));
        assertTrue(reader.canRead(ResolvableType.forClass(JsonRpcRequest.class), MediaType.APPLICATION_JSON));
        assertFalse(reader.canRead(ResolvableType.forClass(JsonRpcRequest.class), MediaType.TEXT_PLAIN));
        assertEquals("rpc-1", parsed.getId());
        assertEquals("tools/call", parsed.getMethod());
    }

    @Test
    void webFluxWriterSupportsJsonAndStreamingPayloads() {
        SpringWebFluxSjf4jMessageWriter writer = new SpringWebFluxSjf4jMessageWriter();

        MockServerHttpResponse jsonResponse = new MockServerHttpResponse();
        writer.write(
                Mono.just(JsonObject.of("ok", true)),
                ResolvableType.forClass(Object.class),
                null,
                jsonResponse,
                Collections.emptyMap()
        ).block();

        MockServerHttpResponse ndjsonResponse = new MockServerHttpResponse();
        writer.write(
                Flux.just(JsonObject.of("seq", 1), JsonObject.of("seq", 2)),
                ResolvableType.forClass(Object.class),
                MediaType.parseMediaType("application/x-ndjson"),
                ndjsonResponse,
                Collections.emptyMap()
        ).block();

        String jsonBody = jsonResponse.getBodyAsString().block();
        String ndjsonBody = ndjsonResponse.getBodyAsString().block();

        assertTrue(writer.canWrite(ResolvableType.forClass(Object.class), MediaType.APPLICATION_JSON));
        assertTrue(writer.canWrite(ResolvableType.forClass(Object.class), MediaType.valueOf("application/problem+json")));
        assertFalse(writer.canWrite(ResolvableType.forClass(Object.class), MediaType.TEXT_PLAIN));
        assertTrue(jsonBody.contains("\"ok\":true"));
        assertEquals(MediaType.APPLICATION_JSON, jsonResponse.getHeaders().getContentType());
        assertTrue(ndjsonBody.contains("\"seq\":1"));
        assertTrue(ndjsonBody.contains("\"seq\":2"));
        assertTrue(ndjsonBody.contains("\n"));
        assertEquals(MediaType.parseMediaType("application/x-ndjson"), ndjsonResponse.getHeaders().getContentType());
    }
}
