package dev.metaplus.backend.server;

import dev.metaplus.core.adapter.SpringMvcSjf4jMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;


@Slf4j
@SpringBootApplication(scanBasePackages = {
        "com.outofstack.metaplus.backend"
})
public class BackendServerApp {

    public static void main(String[] args) {
        log.info("=== Start Metaplus BackendServerApp ...");

        /// fix: if '%2F' in URL, spring will return 400 HTML.
//        System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");

        new SpringApplicationBuilder(BackendServerApp.class).run(args);
    }

    @Bean
    public HttpMessageConverter<Object> createJsonObjectMessageConverter() {
        return new SpringMvcSjf4jMessageConverter();
    }

    @Value("${metaplus.backend.server.port:8120}")
    private int port;

//    @Bean
//    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
//        log.debug("Configuring Tomcat to allow encoded slashes.");
//        return factory -> {
//            factory.setPort(port);
//            factory.addConnectorCustomizers(
//                    connector -> connector.setEncodedSolidusHandling(
//                            EncodedSolidusHandling.DECODE.getValue()));
//        };
//    }

}
