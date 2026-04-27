package dev.metaplus.backend.server.domain;

import dev.metaplus.backend.server.BackendServerException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ValueExprUtil {

    private static final Pattern VALUE_REF_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern SAFE_REF_PATTERN = Pattern.compile("(?:idea|meta|plus|edit)(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");

    private ValueExprUtil() {
    }

    public static String toLogicalFieldPath(String mappingsPath) {
        if (mappingsPath == null || mappingsPath.isEmpty() || "$".equals(mappingsPath)) {
            return "";
        }

        String normalizedPath = mappingsPath.startsWith("$.")
                ? mappingsPath.substring(2)
                : mappingsPath;
        String[] segments = normalizedPath.split("\\.");
        List<String> fieldSegments = new ArrayList<>();
        boolean expectSchemaPropertiesKeyword = true;

        for (String segment : segments) {
            if (expectSchemaPropertiesKeyword && "properties".equals(segment)) {
                expectSchemaPropertiesKeyword = false;
                continue;
            }
            fieldSegments.add(segment);
            expectSchemaPropertiesKeyword = true;
        }

        return String.join(".", fieldSegments);
    }

    public static String toPainlessValueExpr(String template) {
        if (template == null || template.isEmpty()) {
            return "''";
        }

        Matcher matcher = VALUE_REF_PATTERN.matcher(template);
        List<String> parts = new ArrayList<>();
        int offset = 0;
        while (matcher.find()) {
            if (matcher.start() > offset) {
                parts.add(toPainlessStringLiteral(template.substring(offset, matcher.start())));
            }
            parts.add(normalizeRefExpr(matcher.group(1)));
            offset = matcher.end();
        }
        if (offset < template.length()) {
            parts.add(toPainlessStringLiteral(template.substring(offset)));
        }
        return parts.isEmpty() ? "''" : String.join(" + ", parts);
    }

    private static String normalizeRefExpr(String expr) {
        String trimmed = expr == null ? "" : expr.trim();
        if (!SAFE_REF_PATTERN.matcher(trimmed).matches()) {
            throw new BackendServerException("Unsafe $value placeholder reference: " + expr);
        }
        return trimmed;
    }

    private static String toPainlessStringLiteral(String value) {
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "'";
    }
}
