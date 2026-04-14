package dev.metaplus.core.adapter;

import dev.metaplus.core.json.Jsons;
import org.sjf4j.Sjf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SpringWebFluxSjf4jMessageWriter implements HttpMessageWriter<Object> {

    private static final MediaType APPLICATION_ANY_JSON = new MediaType("application", "*+json");
    private static final MediaType APPLICATION_STREAM_JSON = new MediaType("application", "stream+json");
    private static final MediaType APPLICATION_NDJSON = MediaType.parseMediaType("application/x-ndjson");
    private static final byte[] NEWLINE_BYTES = "\n".getBytes(StandardCharsets.UTF_8);
    private static final List<MediaType> MEDIA_TYPES = Arrays.asList(
            MediaType.APPLICATION_JSON,
            APPLICATION_ANY_JSON,
            APPLICATION_STREAM_JSON,
            APPLICATION_NDJSON
    );

    @Override
    public List<MediaType> getWritableMediaTypes() {
        return MEDIA_TYPES;
    }

    @Override
    public boolean canWrite(ResolvableType elementType, MediaType mediaType) {
        return mediaType == null || isJsonMediaType(mediaType);
    }

    @Override
    public Mono<Void> write(Publisher<? extends Object> inputStream, ResolvableType elementType,
                            MediaType mediaType, ReactiveHttpOutputMessage message, Map<String, Object> hints) {
        MediaType contentType = mediaType != null ? mediaType : MediaType.APPLICATION_JSON;
        message.getHeaders().setContentType(contentType);

        DataBufferFactory bufferFactory = message.bufferFactory();
        if (isStreamingMediaType(contentType)) {
            Flux<DataBuffer> bufferFlux = Flux.from(inputStream)
                    .map(value -> wrapStreamingValue(bufferFactory, value));
            return message.writeAndFlushWith(bufferFlux.map(Mono::just));
        }

        if (inputStream instanceof Mono) {
            return Mono.from(inputStream)
                    .map(value -> bufferFactory.wrap(Jsons.toJsonBytes(value)))
                    .flatMap(dataBuffer -> message.writeWith(Mono.just(dataBuffer)));
        }

        return Flux.from(inputStream)
                .collectList()
                .map(values -> bufferFactory.wrap(Jsons.toJsonBytes(values)))
                .flatMap(dataBuffer -> message.writeWith(Mono.just(dataBuffer)));
    }


    private static boolean isStreamingMediaType(MediaType mediaType) {
        return APPLICATION_STREAM_JSON.isCompatibleWith(mediaType)
                || APPLICATION_NDJSON.isCompatibleWith(mediaType);
    }

    private static boolean isJsonMediaType(MediaType mediaType) {
        return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                || APPLICATION_ANY_JSON.isCompatibleWith(mediaType)
                || APPLICATION_NDJSON.isCompatibleWith(mediaType)
                || mediaType.getSubtype().endsWith("+json");
    }

    private static DataBuffer wrapStreamingValue(DataBufferFactory bufferFactory, Object value) {
        byte[] jsonBytes = Jsons.toJsonBytes(value);
        byte[] payload = Arrays.copyOf(jsonBytes, jsonBytes.length + 1);
        payload[payload.length - 1] = NEWLINE_BYTES[0];
        return bufferFactory.wrap(payload);
    }

}
