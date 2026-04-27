package dev.metaplus.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalConfigTest {

    private static final String PREFIX = "metaplus.test.";
    private static final String DIR_KEY = PREFIX + "dir";
    private static final String BASE_KEY = PREFIX + "base";
    private static final String OLD_KEY = PREFIX + "old";

    @AfterEach
    void tearDown() {
        GlobalConfig.clearForTest();
        System.clearProperty(DIR_KEY);
        System.clearProperty(BASE_KEY);
        System.clearProperty(OLD_KEY);
    }

    @Test
    void loadSpringEnvPrefersHighestPrecedenceSpringProperty() {
        System.setProperty(DIR_KEY, "/system");
        GlobalConfig.put(DIR_KEY, "/global");

        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("high", _singletonMap(DIR_KEY, "/spring-high")));
        env.getPropertySources().addLast(new MapPropertySource("low", _singletonMap(DIR_KEY, "/spring-low")));

        GlobalConfig.loadSpringEnv(env, PREFIX);

        assertEquals("/spring-high", GlobalConfig.getString(DIR_KEY));
    }

    @Test
    void loadSpringEnvResolvesPlaceholdersWithinSpringEnvironment() {
        MockEnvironment env = new MockEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put(BASE_KEY, "/srv/metaplus");
        props.put(DIR_KEY, "${" + BASE_KEY + "}/data");
        env.getPropertySources().addFirst(new MapPropertySource("metaplus", props));

        GlobalConfig.loadSpringEnv(env, PREFIX);

        assertEquals("/srv/metaplus/data", GlobalConfig.getString(DIR_KEY));
    }

    @Test
    void loadSpringEnvClearsPreviouslyLoadedSpringKeysForPrefix() {
        MockEnvironment firstEnv = new MockEnvironment();
        Map<String, Object> firstProps = new HashMap<>();
        firstProps.put(DIR_KEY, "/first");
        firstProps.put(OLD_KEY, "legacy");
        firstEnv.getPropertySources().addFirst(new MapPropertySource("first", firstProps));
        GlobalConfig.loadSpringEnv(firstEnv, PREFIX);

        MockEnvironment secondEnv = new MockEnvironment();
        secondEnv.getPropertySources().addFirst(new MapPropertySource("second", _singletonMap(DIR_KEY, "/second")));
        GlobalConfig.loadSpringEnv(secondEnv, PREFIX);

        assertEquals("/second", GlobalConfig.getString(DIR_KEY));
        assertNull(GlobalConfig.get(OLD_KEY));
    }

    @Test
    void putPropertiesResolvesPlaceholdersAgainstLoadedSpringValues() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("metaplus", _singletonMap(BASE_KEY, "/spring-base")));
        GlobalConfig.loadSpringEnv(env, PREFIX);

        Properties props = new Properties();
        props.setProperty(DIR_KEY, "${" + BASE_KEY + "}/jobs");
        GlobalConfig.putProperties(props);

        assertEquals("/spring-base/jobs", GlobalConfig.getString(DIR_KEY));
    }

    @Test
    void getUsesExpectedPrecedenceAndTypedAccessorsFallbackGracefully() {
        String key = PREFIX + "typed";
        String numberKey = PREFIX + "number";
        String invalidNumberKey = PREFIX + "invalid-number";
        String booleanKey = PREFIX + "boolean";
        String envBackedKey = "path";

        GlobalConfig.put(key, "from-properties");
        assertEquals("from-properties", GlobalConfig.getString(key));

        System.setProperty(key, "from-system");
        assertEquals("from-system", GlobalConfig.getString(key));

        GlobalConfig.put(numberKey, 12);
        GlobalConfig.put(invalidNumberKey, "abc");
        GlobalConfig.put(booleanKey, true);
        GlobalConfig.put(PREFIX + "boolean-string", "true");

        assertEquals(Integer.valueOf(12), GlobalConfig.getInt(numberKey));
        assertEquals(Integer.valueOf(7), GlobalConfig.getInt(invalidNumberKey, 7));
        assertEquals(Boolean.TRUE, GlobalConfig.getBoolean(booleanKey));
        assertEquals(Boolean.TRUE, GlobalConfig.getBoolean(PREFIX + "boolean-string", true));
        assertEquals(Boolean.FALSE, GlobalConfig.getBoolean(PREFIX + "missing-boolean", false));

        assertNotNull(System.getenv("PATH"));
        System.clearProperty(envBackedKey);
        assertEquals(System.getenv("PATH"), GlobalConfig.getString(envBackedKey));
    }

    @Test
    void putPropertiesSupportsDefaultsUnresolvedVarsAndDanglingPlaceholders() {
        Properties props = new Properties();
        props.setProperty(PREFIX + "with-default", "${" + PREFIX + "missing:/tmp}/file");
        props.setProperty(PREFIX + "unresolved", "${" + PREFIX + "missing}/file");
        props.setProperty(PREFIX + "dangling", "${" + PREFIX + "missing");

        GlobalConfig.putProperties(props);

        assertEquals("/tmp/file", GlobalConfig.getString(PREFIX + "with-default"));
        assertEquals("${" + PREFIX + "missing}/file", GlobalConfig.getString(PREFIX + "unresolved"));
        assertEquals("${" + PREFIX + "missing", GlobalConfig.getString(PREFIX + "dangling"));
    }

    @Test
    void loadPropertiesAndToPropertiesExposeCurrentView() throws Exception {
        Path propsFile = Files.createTempFile("metaplus-core", ".properties");
        Files.write(propsFile, (
                PREFIX + "name=metaplus\n"
                        + PREFIX + "enabled=true\n"
        ).getBytes());

        try {
            GlobalConfig.loadProperties(propsFile);
            GlobalConfig.loadProperties(propsFile.resolveSibling("missing.properties"));

            Properties all = GlobalConfig.toProperties();
            Properties prefixed = GlobalConfig.toProperties(PREFIX.substring(0, PREFIX.length() - 1));

            assertEquals("metaplus", all.getProperty(PREFIX + "name"));
            assertEquals("metaplus", prefixed.getProperty("name"));
            assertEquals("true", prefixed.getProperty("enabled"));
            assertTrue(GlobalConfig.keySet().contains(PREFIX + "name"));
            assertFalse(prefixed.containsKey(PREFIX + "name"));
        } finally {
            Files.deleteIfExists(propsFile);
        }
    }

    @Test
    void loadPropertiesFileAndLoadSpringEnvWithoutPrefixReplacePreviousSpringSnapshot() throws Exception {
        Path propsFile = Files.createTempFile("metaplus-core-file", ".properties");
        Files.write(propsFile, (PREFIX + "file=/srv/file\n").getBytes());

        try {
            GlobalConfig.loadPropertiesFile(propsFile);
            assertEquals("/srv/file", GlobalConfig.getString(PREFIX + "file"));

            MockEnvironment firstEnv = new MockEnvironment();
            firstEnv.getPropertySources().addFirst(new MapPropertySource("first",
                    _singletonMap(PREFIX + "alpha", "a")));
            GlobalConfig.loadSpringEnv(firstEnv);

            MockEnvironment secondEnv = new MockEnvironment();
            secondEnv.getPropertySources().addFirst(new MapPropertySource("second",
                    _singletonMap(PREFIX + "beta", "b")));
            GlobalConfig.loadSpringEnv(secondEnv);

            assertNull(GlobalConfig.get(PREFIX + "alpha"));
            assertEquals("b", GlobalConfig.getString(PREFIX + "beta"));
        } finally {
            Files.deleteIfExists(propsFile);
        }
    }

    @Test
    void nonNullContractsRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> GlobalConfig.get(null));
        assertThrows(NullPointerException.class, () -> GlobalConfig.put(null, "value"));
        assertThrows(NullPointerException.class, () -> GlobalConfig.putAll(null));
        assertThrows(NullPointerException.class, () -> GlobalConfig.putProperties(null));
        assertThrows(NullPointerException.class, () -> GlobalConfig.loadProperties(null));
        assertThrows(NullPointerException.class, () -> GlobalConfig.loadPropertiesFile(null));
        assertThrows(NullPointerException.class, () -> GlobalConfig.toProperties(null));
        assertThrows(NullPointerException.class, () -> GlobalConfig.loadSpringEnv(null));
    }

    private static Map<String, Object> _singletonMap(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}
