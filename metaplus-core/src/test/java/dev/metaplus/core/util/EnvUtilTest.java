package dev.metaplus.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvUtilTest {

    @Test
    void hostnameIsStableAndNonBlank() {
        String hostname = EnvUtil.getHostname();

        assertFalse(hostname.trim().isEmpty());
        assertTrue(hostname.equals(EnvUtil.getHostname()));
    }

    @Test
    void processNameIncludesResolvedHostname() {
        String processName = EnvUtil.getProcessName();

        assertTrue(processName.contains("@"));
        assertTrue(processName.endsWith("@" + EnvUtil.getHostname()));
    }
}
