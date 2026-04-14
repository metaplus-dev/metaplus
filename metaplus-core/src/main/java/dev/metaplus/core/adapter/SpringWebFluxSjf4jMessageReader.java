package dev.metaplus.core.adapter;

import dev.metaplus.core.json.Jsons;
import org.sjf4j.Sjf4j;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.HttpMessageReader;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SpringWebFluxSjf4jMessageReader implements HttpMessageReader<Object> {
    private static final MediaType APPLICATION_ANY_JSON = new MediaType("application", "*+json");
    private static final List<MediaType> MEDIA_TYPES = Arrays.asList(MediaType.APPLICATION_JSON, APPLICATION_ANY_JSON);

    @Override
    public List<MediaType> getReadableMediaTypes() {
        return MEDIA_TYPES;
    }

    @Override
    public boolean canRead(ResolvableType elementType, MediaType mediaType) {
        return mediaType == null || isJsonMediaType(mediaType);
    }

    @Override
    public Flux<Object> read(ResolvableType elementType, ReactiveHttpInputMessage message,
                                 Map<String, Object> hints) {

        return readMono(elementType, message, hints).flux();
    }

    @Override
    public Mono<Object> readMono(ResolvableType elementType, ReactiveHttpInputMessage message,
                                     Map<String, Object> hints) {
        return DataBufferUtils.join(message.getBody())
                .map(dataBuffer -> dataBuffer2Object(dataBuffer, elementType));
    }


    private Object dataBuffer2Object(DataBuffer dataBuffer, ResolvableType elementType) {
        Class<?> clazz = elementType.resolve(Object.class);
        try (InputStream is = dataBuffer.asInputStream(true)) {
            return Jsons.fromJson(is, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isJsonMediaType(MediaType mediaType) {
        return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                || APPLICATION_ANY_JSON.isCompatibleWith(mediaType)
                || mediaType.getSubtype().endsWith("+json");
    }
}
