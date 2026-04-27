package dev.metaplus.backend.lib.es;

import dev.metaplus.backend.lib.BackendException;
import dev.metaplus.core.json.Jsons;
import org.junit.jupiter.api.BeforeEach;
import org.sjf4j.JsonObject;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

public abstract class EsIntegrationTestSupport {

    private static final DockerImageName ELASTICSEARCH_IMAGE =
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.0");

    private static volatile ElasticsearchContainer elasticsearch;

    protected EsClient esClient;

    @BeforeEach
    void setUpEsClient() {
        esClient = new EsClient(_resolveBaseUrl());
    }

    protected String uniqueIndexName(String baseName) {
        return baseName + "_it_" + UUID.randomUUID().toString().replace("-", "");
    }

    protected void recreateIndex(String indexName, String resourcePath) {
        deleteIndexIfExists(indexName);
        EsResponse response = esClient.put(URI.create("/" + indexName), _readJsonResource(resourcePath));
        if (!response.isSuccess()) {
            throw new BackendException("Create index '" + indexName + "' failed. status="
                    + response.getStatusCode() + ", body=" + response.getBody());
        }
    }

    protected void refreshIndex(String indexName) {
        EsResponse response = esClient.post(URI.create("/" + indexName + "/_refresh"));
        if (!response.isSuccess()) {
            throw new BackendException("Refresh index '" + indexName + "' failed. status="
                    + response.getStatusCode() + ", body=" + response.getBody());
        }
    }

    protected void indexDocument(String indexName, String id, JsonObject document) {
        EsResponse response = esClient.put(URI.create("/" + indexName + "/_doc/" + id), document);
        if (!response.isSuccess()) {
            throw new BackendException("Index document '" + id + "' into '" + indexName + "' failed. status="
                    + response.getStatusCode() + ", body=" + response.getBody());
        }
    }

    protected void deleteIndexIfExists(String indexName) {
        EsResponse response = esClient.delete(URI.create("/" + indexName));
        if (!response.isSuccess() && !response.isNotFound()) {
            throw new BackendException("Delete index '" + indexName + "' failed. status="
                    + response.getStatusCode() + ", body=" + response.getBody());
        }
    }

    private static String _resolveBaseUrl() {
        String externalBaseUrl = System.getProperty("metaplus.backend.es.baseUrl");
        if (externalBaseUrl == null || externalBaseUrl.isBlank()) {
            externalBaseUrl = System.getenv("METAPLUS_BACKEND_ES_BASEURL");
        }
        if (externalBaseUrl == null || externalBaseUrl.isBlank()) {
            externalBaseUrl = System.getProperty("metaplus.test.es.baseUrl");
        }
        if (externalBaseUrl == null || externalBaseUrl.isBlank()) {
            externalBaseUrl = System.getenv("METAPLUS_TEST_ES_BASEURL");
        }
        if (externalBaseUrl != null && !externalBaseUrl.isBlank()) {
            return externalBaseUrl;
        }

        ElasticsearchContainer container = elasticsearch;
        if (container == null) {
            synchronized (EsIntegrationTestSupport.class) {
                container = elasticsearch;
                if (container == null) {
                    container = new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
                            .withEnv("discovery.type", "single-node")
                            .withEnv("xpack.security.enabled", "false")
                            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
                    container.start();
                    elasticsearch = container;
                }
            }
        }
        return "http://" + container.getHost() + ":" + container.getMappedPort(9200);
    }

    private JsonObject _readJsonResource(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new BackendException("Missing resource '" + resourcePath + "'.");
            }
            return Jsons.fromJson(inputStream, JsonObject.class);
        } catch (Exception e) {
            throw new BackendException("Read resource '" + resourcePath + "' failed.", e);
        }
    }
}
