package dev.metaplus.core.util;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class EnvUtil {

    private static final String HOSTNAME = initHostname();
    private static final String PROCESS_NAME = initProcessName();

    private EnvUtil() {}

    public static String getHostname() {
        return HOSTNAME;
    }

    // <pid>@<hostname>
    public static String getProcessName() {
        return PROCESS_NAME;
    }

    private static String initHostname() {
        String hostname = firstNonBlank(
                System.getenv("HOSTNAME"),
                System.getenv("COMPUTERNAME")
        );
        if (hostname != null) {
            return hostname;
        }

        try {
            return firstNonBlank(
                    InetAddress.getLocalHost().getHostName(),
                    InetAddress.getLocalHost().getCanonicalHostName()
            );
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private static String initProcessName() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        String pid = runtimeName;
        int at = runtimeName == null ? -1 : runtimeName.indexOf('@');
        if (at > 0) {
            pid = runtimeName.substring(0, at);
        }
        pid = firstNonBlank(pid);
        if (pid == null) {
            pid = "unknown";
        }
        return pid + "@" + getHostname();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

}
