package dev.metaplus.backend.server.dao;

import dev.metaplus.backend.lib.es.EsClient;
import dev.metaplus.backend.lib.es.EsResponse;
import dev.metaplus.backend.server.BackendServerException;
import dev.metaplus.core.model.agg.AggRequest;
import dev.metaplus.core.model.search.Query;
import dev.metaplus.core.model.search.SearchOptions;
import dev.metaplus.core.model.search.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchDaoTest {

    private EsClient esClient;
    private SearchDao searchDao;

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        searchDao = new SearchDao();
        ReflectionTestUtils.setField(searchDao, "esClient", esClient);
    }

    @Test
    void searchUsesNormalizedFailureMessage() {
        when(esClient.post(any(URI.class), any(SearchRequest.class)))
                .thenReturn(new EsResponse(500, JsonObject.of("error", "boom")));

        BackendServerException ex = assertThrows(BackendServerException.class,
                () -> searchDao.search(Set.of("b", "a"), new SearchRequest(), null));

        assertEquals("SearchDao.search failed for domains=[a, b], status=500, body=J{error=boom}",
                ex.getMessage());
    }

    @Test
    void searchAppliesRouteKeyAndDomainIndexMapping() {
        AtomicReference<URI> uriRef = new AtomicReference<>();
        when(esClient.post(any(URI.class), any(SearchRequest.class))).thenAnswer(invocation -> {
            uriRef.set(invocation.getArgument(0));
            return new EsResponse(200, JsonObject.of(
                    "hits", JsonObject.of(
                            "total", JsonObject.of("value", 0),
                            "hits", JsonArray.of())));
        });

        SearchOptions searchOptions = new SearchOptions();
        searchOptions.setRouteKey("tenant-1");
        searchDao.search(Set.of("demo", "data"), new SearchRequest(), searchOptions);

        assertNotNull(uriRef.get());
        assertEquals("/i_metaplus_domain_data,i_metaplus_domain_demo/_search?version=true&routing=tenant-1",
                uriRef.get().toString());
    }

    @Test
    void aggBuildsIndependentSizeZeroRequestWithoutMutatingInput() {
        AtomicReference<JsonObject> bodyRef = new AtomicReference<>();
        when(esClient.post(any(URI.class), any(JsonObject.class))).thenAnswer(invocation -> {
            bodyRef.set(invocation.getArgument(1));
            return new EsResponse(200, JsonObject.of(
                    "aggregations", JsonObject.of("byDomain", JsonObject.of("doc_count", 2))));
        });

        AggRequest.Agg agg = new AggRequest.Agg();
        agg.put("terms", JsonObject.of("field", "idea.domain"));

        AggRequest aggRequest = new AggRequest();
        aggRequest.setQuery(new Query());
        aggRequest.setAggs(Map.of("byDomain", agg));

        searchDao.agg("demo", aggRequest, null);

        assertNotNull(bodyRef.get());
        assertEquals(Integer.valueOf(0), bodyRef.get().getInt("size"));
        assertNull(aggRequest.getInt("size"));
    }
}
