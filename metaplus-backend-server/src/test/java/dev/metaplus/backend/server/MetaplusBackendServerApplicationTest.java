package dev.metaplus.backend.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "metaplus.bootstrap.mode=off")
class MetaplusBackendServerApplicationTest {

    @Test
    void contextLoads() {
    }
}
