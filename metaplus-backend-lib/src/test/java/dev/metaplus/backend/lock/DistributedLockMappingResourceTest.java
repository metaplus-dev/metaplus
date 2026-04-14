package dev.metaplus.backend.lock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockMappingResourceTest {

    @Test
    void lockIndexMappingResourceExistsAndDeclaresStrictFields() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(DistributedLock.INDEX_CONFIG_FILE)) {
            assertNotNull(inputStream, "Missing resource " + DistributedLock.INDEX_CONFIG_FILE);
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("\"dynamic\": \"strict\""));
            assertTrue(content.contains("\"lockedBy\""));
            assertTrue(content.contains("\"lockedAt\""));
            assertTrue(content.contains("\"expiredAt\""));
            assertTrue(content.contains("\"isReleased\""));
            assertTrue(content.contains("\"releasedAt\""));
        }
    }
}
