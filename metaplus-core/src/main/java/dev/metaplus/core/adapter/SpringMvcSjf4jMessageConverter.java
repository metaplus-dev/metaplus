package dev.metaplus.core.adapter;

import dev.metaplus.core.json.Jsons;
import lombok.NonNull;
import org.sjf4j.Sjf4j;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;


public class SpringMvcSjf4jMessageConverter extends AbstractHttpMessageConverter<Object> {

    private static final MediaType APPLICATION_ANY_JSON = new MediaType("application", "*+json");

    public SpringMvcSjf4jMessageConverter() {
        super(MediaType.APPLICATION_JSON, APPLICATION_ANY_JSON);
    }

    @Override
    protected boolean supports(@NonNull Class<?> clazz) {
        return true;
    }

    @NonNull
    @Override
    protected Object readInternal(@NonNull Class<?> clazz, HttpInputMessage inputMessage) throws
            IOException, HttpMessageNotReadableException {
        return Jsons.fromJson(inputMessage.getBody(), clazz);
    }

    @Override
    protected void writeInternal(@NonNull Object object, @NonNull HttpOutputMessage outputMessage) throws
            IOException, HttpMessageNotWritableException {
        Jsons.toJson(outputMessage.getBody(), object);
    }


}
