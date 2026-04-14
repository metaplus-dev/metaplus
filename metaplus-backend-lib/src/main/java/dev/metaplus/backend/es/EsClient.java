package dev.metaplus.backend.es;

import dev.metaplus.core.adapter.SpringMvcSjf4jMessageConverter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.sjf4j.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.function.Supplier;

/**
 * Based on Spring RestClient.
 * Authentication is configured once here so backend stores do not need to know
 * whether Elasticsearch uses no auth, basic auth, bearer token auth, or API key auth.
 *
 * <p>Supported properties and equivalent environment variable names:</p>
 * <ul>
 *   <li>{@code metaplus.backend.es.baseUrl} -> {@code METAPLUS_BACKEND_ES_BASEURL}</li>
 *   <li>{@code metaplus.backend.es.auth.type} -> {@code METAPLUS_BACKEND_ES_AUTH_TYPE}</li>
 *   <li>{@code metaplus.backend.es.auth.username} -> {@code METAPLUS_BACKEND_ES_AUTH_USERNAME}</li>
 *   <li>{@code metaplus.backend.es.auth.password} -> {@code METAPLUS_BACKEND_ES_AUTH_PASSWORD}</li>
 *   <li>{@code metaplus.backend.es.auth.bearerToken} -> {@code METAPLUS_BACKEND_ES_AUTH_BEARERTOKEN}</li>
 *   <li>{@code metaplus.backend.es.auth.apiKey} -> {@code METAPLUS_BACKEND_ES_AUTH_APIKEY}</li>
 *   <li>{@code metaplus.backend.es.tls.trustStorePath} -> {@code METAPLUS_BACKEND_ES_TLS_TRUSTSTOREPATH}</li>
 *   <li>{@code metaplus.backend.es.tls.trustStorePassword} -> {@code METAPLUS_BACKEND_ES_TLS_TRUSTSTOREPASSWORD}</li>
 *   <li>{@code metaplus.backend.es.tls.trustStoreType} -> {@code METAPLUS_BACKEND_ES_TLS_TRUSTSTORETYPE}</li>
 *   <li>{@code metaplus.backend.es.tls.keyStorePath} -> {@code METAPLUS_BACKEND_ES_TLS_KEYSTOREPATH}</li>
 *   <li>{@code metaplus.backend.es.tls.keyStorePassword} -> {@code METAPLUS_BACKEND_ES_TLS_KEYSTOREPASSWORD}</li>
 *   <li>{@code metaplus.backend.es.tls.keyPassword} -> {@code METAPLUS_BACKEND_ES_TLS_KEYPASSWORD}</li>
 *   <li>{@code metaplus.backend.es.tls.keyStoreType} -> {@code METAPLUS_BACKEND_ES_TLS_KEYSTORETYPE}</li>
 * </ul>
 */
@Slf4j
@Component
public class EsClient {

    @Value("${metaplus.backend.es.baseUrl:}")
    private String baseUrl;

    @Value("${metaplus.backend.es.auth.type:none}")
    private String authType;

    @Value("${metaplus.backend.es.auth.username:}")
    private String authUsername;

    @Value("${metaplus.backend.es.auth.password:}")
    private String authPassword;

    @Value("${metaplus.backend.es.auth.bearerToken:}")
    private String authBearerToken;

    @Value("${metaplus.backend.es.auth.apiKey:}")
    private String authApiKey;

    @Value("${metaplus.backend.es.tls.trustStorePath:}")
    private String tlsTrustStorePath;

    @Value("${metaplus.backend.es.tls.trustStorePassword:}")
    private String tlsTrustStorePassword;

    @Value("${metaplus.backend.es.tls.trustStoreType:PKCS12}")
    private String tlsTrustStoreType;

    @Value("${metaplus.backend.es.tls.keyStorePath:}")
    private String tlsKeyStorePath;

    @Value("${metaplus.backend.es.tls.keyStorePassword:}")
    private String tlsKeyStorePassword;

    @Value("${metaplus.backend.es.tls.keyPassword:}")
    private String tlsKeyPassword;

    @Value("${metaplus.backend.es.tls.keyStoreType:PKCS12}")
    private String tlsKeyStoreType;

    private volatile RestClient restClient;

    private RestClient getRestClient() {
        if (restClient != null) {
            return restClient;
        }
        synchronized (this) {
            if (restClient != null) {
                return restClient;
            }
            if (null == baseUrl || baseUrl.isEmpty()) {
                throw new IllegalStateException("`metaplus.backend.es.baseUrl` is empty.");
            }
            EsAuthType resolvedAuthType = resolveAuthType();
            restClient = RestClient.builder()
                    .requestFactory(buildRequestFactory())
                    .messageConverters(converters -> converters.add(0, new SpringMvcSjf4jMessageConverter()))
                    .defaultHeaders(headers -> applyAuth(headers, resolvedAuthType))
                    .baseUrl(baseUrl)
                    .build();
            return restClient;
        }
    }

    public EsResponse get(@NonNull URI uri) {
        return execute("GET", uri, () -> getRestClient().get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {/**/})
                .toEntity(JsonObject.class));
    }

    public EsResponse post(@NonNull URI uri) {
        return post(uri, null);
    }

    public EsResponse post(@NonNull URI uri, JsonObject requestBody) {
        return execute("POST", uri, requestBody, () -> send(getRestClient().post().uri(uri), requestBody));
    }


    public EsResponse put(@NonNull URI uri, @NonNull JsonObject requestBody) {
        return execute("PUT", uri, requestBody, () -> send(getRestClient().put().uri(uri), requestBody));
    }

    public EsResponse delete(@NonNull URI uri) {
        return execute("DELETE", uri, () -> getRestClient().delete()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {/**/})
                .toEntity(JsonObject.class));
    }


    public EsResponse head(@NonNull URI uri) {
        log.debug("ES HEAD {}", uri);
        try {
            ResponseEntity<Void> response = getRestClient().head()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {/**/})
                    .toBodilessEntity();
            if (response.getStatusCode().value() == 401) {
                throw new EsClientException("Elasticsearch authentication failed (401 Unauthorized). "
                        + "Check `metaplus.backend.es.auth.*` configuration.");
            }
            if (response.getStatusCode().value() == 403) {
                throw new EsClientException("Elasticsearch authorization failed (403 Forbidden). "
                        + "Check credentials and cluster permissions for the configured principal.");
            }
            log.debug("ES HEAD {} -> {}", uri, response.getStatusCode().value());
            return new EsResponse(response.getStatusCode().value());
        } catch (EsClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EsClientException("ES HEAD " + uri + " failed", e);
        }
    }

    public EsResponse bulk(@NonNull List<BulkItemReq> bulkItemReqList) {
        StringBuilder sb = new StringBuilder();
        bulkItemReqList.forEach(it -> sb.append(it.toStringBuilder()));
        log.debug("ES BULK size={}", bulkItemReqList.size());
        try {
            ResponseEntity<JsonObject> response = getRestClient().post()
                    .uri("/_bulk")
                    .contentType(MediaType.APPLICATION_NDJSON)
                    .body(sb.toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {/**/})
                    .toEntity(JsonObject.class);
            EsResponse esResponse = transfer(response);
            log.debug("ES BULK -> status={}, errors={}", esResponse.getStatusCode(), esResponse.hasBulkErrors());
            return esResponse;
        } catch (EsClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EsClientException("ES BULK failed", e);
        }
    }


    /// private

    private EsAuthType resolveAuthType() {
        if (authType == null || authType.trim().isEmpty()) {
            return EsAuthType.NONE;
        }
        return EsAuthType.of(authType);
    }

    private HttpComponentsClientHttpRequestFactory buildRequestFactory() {
        SSLContext sslContext = buildSslContextIfConfigured();
        if (sslContext == null) {
            return new HttpComponentsClientHttpRequestFactory();
        }

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                                .setSslContext(sslContext)
                                .build())
                        .build())
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    private SSLContext buildSslContextIfConfigured() {
        boolean hasTrustStore = hasText(tlsTrustStorePath);
        boolean hasKeyStore = hasText(tlsKeyStorePath);
        if (!hasTrustStore && !hasKeyStore) {
            return null;
        }

        try {
            KeyManager[] keyManagers = null;
            TrustManager[] trustManagers = null;
            if (hasTrustStore) {
                KeyStore trustStore = loadKeyStore(
                        requireText(tlsTrustStorePath, "metaplus.backend.es.tls.trustStorePath"),
                        requireText(tlsTrustStorePassword, "metaplus.backend.es.tls.trustStorePassword"),
                        defaultText(tlsTrustStoreType, "PKCS12"));
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
            if (hasKeyStore) {
                String keyStorePassword = requireText(tlsKeyStorePassword, "metaplus.backend.es.tls.keyStorePassword");
                String keyPassword = hasText(tlsKeyPassword) ? tlsKeyPassword : keyStorePassword;
                KeyStore keyStore = loadKeyStore(requireText(tlsKeyStorePath, "metaplus.backend.es.tls.keyStorePath"),
                        keyStorePassword, defaultText(tlsKeyStoreType, "PKCS12"));
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, keyPassword.toCharArray());
                keyManagers = keyManagerFactory.getKeyManagers();
            }
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Elasticsearch TLS configuration.", e);
        }
    }

    private KeyStore loadKeyStore(String pathValue, String password, String type) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            Path path = Path.of(pathValue);
            try (InputStream inputStream = Files.newInputStream(path)) {
                keyStore.load(inputStream, password.toCharArray());
            }
            return keyStore;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load keystore '" + pathValue + "'.", e);
        }
    }

    private void applyAuth(HttpHeaders headers, EsAuthType resolvedAuthType) {
        switch (resolvedAuthType) {
            case NONE:
                return;
            case BASIC:
                headers.setBasicAuth(requireText(authUsername, "metaplus.backend.es.auth.username"),
                        requireText(authPassword, "metaplus.backend.es.auth.password"));
                return;
            case BEARER:
                headers.setBearerAuth(requireText(authBearerToken, "metaplus.backend.es.auth.bearerToken"));
                return;
            case API_KEY:
                headers.set(HttpHeaders.AUTHORIZATION,
                        "ApiKey " + requireText(authApiKey, "metaplus.backend.es.auth.apiKey"));
                return;
            default:
                throw new IllegalStateException("Unhandled Elasticsearch auth type " + resolvedAuthType + ".");
        }
    }

    private String requireText(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("`" + propertyName + "` is empty.");
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultText(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private EsResponse transfer(ResponseEntity<JsonObject> response) {
        if (response.getStatusCode().value() == 401) {
            throw new EsClientException("Elasticsearch authentication failed (401 Unauthorized). "
                    + "Check `metaplus.backend.es.auth.*` configuration.");
        }
        if (response.getStatusCode().value() == 403) {
            throw new EsClientException("Elasticsearch authorization failed (403 Forbidden). "
                    + "Check credentials and cluster permissions for the configured principal.");
        }
        return new EsResponse(response.getStatusCode().value(), response.getBody());
    }

    private EsResponse execute(String method, URI uri, Supplier<ResponseEntity<JsonObject>> request) {
        log.debug("ES {} {}", method, uri);
        try {
            EsResponse response = transfer(request.get());
            log.debug("ES {} {} -> {}", method, uri, response.getStatusCode());
            return response;
        } catch (EsClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EsClientException("ES " + method + " " + uri + " failed", e);
        }
    }

    private EsResponse execute(String method, URI uri, JsonObject requestBody,
                               Supplier<ResponseEntity<JsonObject>> request) {
        log.debug("ES {} {} body={}", method, uri, requestBody);
        try {
            EsResponse response = transfer(request.get());
            log.debug("ES {} {} -> {}", method, uri, response.getStatusCode());
            return response;
        } catch (EsClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EsClientException("ES " + method + " " + uri + " failed", e);
        }
    }

    private ResponseEntity<JsonObject> send(@NonNull RestClient.RequestBodySpec requestSpec, JsonObject requestBody) {
        if (requestBody != null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON).body(requestBody);
        }
        return requestSpec
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {/**/})
                .toEntity(JsonObject.class);
    }

}
