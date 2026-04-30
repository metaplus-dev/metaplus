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
        "dev.metaplus.backend"
})
public class BackendServerApp {

    private static final String HELP_TEXT = """
            Metaplus Backend Server

            Usage:
              java -jar metaplus-backend-server.jar [options]

            Options:
              --help, -h
                  Show this help message and exit

              --metaplus.bootstrap.mode=verify
                  Verify bootstrap state and load domain registry (default)

              --metaplus.bootstrap.mode=bootstrap
                  Initialize built-in domain index/docs if needed, then load registry

              --metaplus.bootstrap.mode=off
                  Skip bootstrap/verify

            Examples:
              java -jar metaplus-backend-server.jar
              java -jar metaplus-backend-server.jar --metaplus.bootstrap.mode=bootstrap
            """;

    public static void main(String[] args) {
        if (_containsHelpFlag(args)) {
            System.out.println(HELP_TEXT);
            return;
        }
        log.info("=== Start Metaplus BackendServerApp ...");

        /// fix: if '%2F' in URL, spring will return 400 HTML.
//        System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");

        new SpringApplicationBuilder(BackendServerApp.class).run(args);
    }

    private static boolean _containsHelpFlag(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
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
