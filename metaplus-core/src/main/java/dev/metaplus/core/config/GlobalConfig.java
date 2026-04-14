package dev.metaplus.core.config;


import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * metaplus.xxx.dir=${user.dir}/xxx
 *
 */
@Slf4j
public class GlobalConfig {

    private static final Map<String, Object> springPropertiesMap = new ConcurrentHashMap<>();
    private static final Map<String, Object> propertiesMap = new ConcurrentHashMap<>();

    private GlobalConfig() {}

    public static void loadPropertiesFile(@NonNull Path confPath) throws IOException {
        Properties props = new Properties();
        props.load(Files.newBufferedReader(confPath, StandardCharsets.UTF_8));
        putProperties(props);
    }


    public static void loadSpringEnv(@NonNull ConfigurableEnvironment env) {
        loadSpringEnv(env, null);
    }

    public static void loadSpringEnv(@NonNull ConfigurableEnvironment env, String prefix) {
        clearSpringProperties(prefix);
        env.getPropertySources().forEach(propertySource -> {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> eps = (EnumerablePropertySource<?>) propertySource;
                log.debug("Find PropertySource name={}", propertySource.getName());
                for (String name : eps.getPropertyNames()) {
                    if (prefix != null && !name.startsWith(prefix)) {
                        continue;
                    }
                    putSpringProperty(env, eps, name);
                }
            }
        });
    }

    public static Object get(@NonNull String key) {
        Object obj = springPropertiesMap.get(key);
        if (null == obj) {
            obj = System.getProperty(key);
        }
        if (null == obj) {
            String envName = key2EnvName(key);
            obj = System.getenv(envName);
        }
        if (null == obj) {
            obj = propertiesMap.get(key);
        }
        return obj;
    }

    /**
     * Giving key = "a.b.c"
     * Firstly, get the resolved value loaded from Spring Environment
     * Secondly, get "a.b.c" from VM options
     * Thirdly, trans "a.b.c" to "A_B_C", and get "A_B_C" from Environment variable
     * Fourthly, get the value loaded into GlobalConfig
     * Finally, get defaultValue or null
     *
     * <code>
     * export METAPLUS_SYNCER_DIR=/tmp/metaplus1
     * java -Dmetaplus.syncer.dir=/tmp/metaplus2 -jar app.jar arg1 arg2
     *
     * GlobalConfig.get("metaplus.syncer.dir")              // => /tmp/metaplus2
     * </code>
     */
    public static String getString(String key, String defaultValue) {
        Object obj = get(key);
        if (null == obj) {
            return defaultValue;
        }
        return String.valueOf(obj);
    }

    public static String getString(String key) {
        return getString(key, null);
    }

    public static Integer getInt(String key, Integer defaultValue) {
        Object obj = get(key);
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else if (null != obj) {
            try {
                return Integer.parseInt(String.valueOf(obj));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static Integer getInt(String key) {
        return getInt(key, null);
    }

    public static Boolean getBoolean(String key, Boolean defaultValue) {
        Object obj = get(key);
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (null != obj) {
            try {
                return Boolean.valueOf(String.valueOf(obj));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static Boolean getBoolean(String key) {
        return getBoolean(key, null);
    }


    public static Set<String> keySet() {
        Set<String> names = new HashSet<>();
        names.addAll(springPropertiesMap.keySet());
        names.addAll(propertiesMap.keySet());
        names.addAll(System.getProperties().stringPropertyNames());
        return names;
    }

    public static void put(@NonNull String key, Object value) {
        propertiesMap.put(key, value);
    }

    public static void putAll(@NonNull Map<String, Object> props) {
        propertiesMap.putAll(props);
    }

    public static void putProperties(@NonNull Properties props) {
        boolean changed;
        int maxAttempts = 10;
        int attempts = 0;
        do {
            changed = false;
            attempts++;
            for (Object key : props.keySet()) {
                String oldValue = String.valueOf(props.get(key));
                String newValue = resolveVars(oldValue);
                propertiesMap.put(String.valueOf(key), newValue);
                if (!oldValue.equals(newValue)) {
                    changed = true;
                }
            }
        } while (changed && attempts < maxAttempts);
    }

    public static void loadProperties(@NonNull Path propsPath) throws IOException {
        if (Files.exists(propsPath)) {
            Properties props = new Properties();
            props.load(Files.newBufferedReader(propsPath, StandardCharsets.UTF_8));
            putProperties(props);
        }
    }

    public static Properties toProperties() {
        Properties properties = new Properties();
        for (String key : keySet()) {
            properties.setProperty(key, String.valueOf(get(key)));
//            properties.put(key, get(key));
        }
        return properties;
    }

    // metaplus.debezium.name=haha => toProperties("metaplus.debezium") => name=haha
    public static Properties toProperties(@NonNull String prefix) {
        Properties properties = new Properties();
        if (!prefix.endsWith(".")) {
            prefix = prefix + ".";
        }
        for (String key : keySet()) {
            if (key.startsWith(prefix)) {
                properties.setProperty(key.substring(prefix.length()), String.valueOf(get(key)));
//                properties.put(key.substring(prefix.length()), get(key));
            }
        }
        return properties;
    }

    static void clearForTest() {
        springPropertiesMap.clear();
        propertiesMap.clear();
    }

    /// private

    private static void putSpringProperty(ConfigurableEnvironment env, EnumerablePropertySource<?> propertySource,
                                          String name) {
        if (springPropertiesMap.containsKey(name)) {
            return;
        }
        Object rawValue = propertySource.getProperty(name);
        if (rawValue == null) {
            return;
        }

        Object resolvedValue = rawValue;
        if (rawValue instanceof CharSequence) {
            resolvedValue = env.resolvePlaceholders(rawValue.toString());
        }
        springPropertiesMap.put(name, resolvedValue);
        log.debug("Put spring property({}={}) from {}", name, resolvedValue, propertySource.getName());
    }

    private static void clearSpringProperties(String prefix) {
        if (prefix == null) {
            springPropertiesMap.clear();
            return;
        }

        springPropertiesMap.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String key2EnvName(String key) {
        return key.toUpperCase().replaceAll("\\.", "_");
    }

    /**
     *   ${user.dir}/xxx.log => /user/test/xxx.log
     *   ${TMPDIR:/tmp}/file => /tmp/file (When TMPDIR does not exist)
     *   ${TMPDIR}/file => ${TMPDIR}/file (When TMPDIR does not exist)
     */
    private static String resolveVars(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        if (!input.contains("${")) {
            return input;
        }

        int startIndex;
        int endIndex = 0;
        StringBuilder output = new StringBuilder();

        while ((startIndex = input.indexOf("${", endIndex)) != -1) {
            output.append(input, endIndex, startIndex);
            endIndex = input.indexOf("}", startIndex);

            if (endIndex == -1) {
                output.append(input.substring(startIndex));
                return output.toString();
            }

            String varContent = input.substring(startIndex + 2, endIndex);

            String varName;
            String defaultValue = null;
            int colonIndex = varContent.indexOf(':');
            if (colonIndex != -1) {
                varName = varContent.substring(0, colonIndex);
                defaultValue = varContent.substring(colonIndex + 1);
            } else {
                varName = varContent;
            }
            String varValue = getString(varName, defaultValue);

            if (varValue != null) {
                output.append(varValue);
            } else {
                output.append("${").append(varContent).append("}");
            }

            endIndex++;
        }

        output.append(input.substring(endIndex));
        return output.toString();
    }

}
