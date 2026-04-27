package dev.metaplus.core.util;


import lombok.NonNull;
import org.sjf4j.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


/**
 * Filters idea documents with scope-aware match/exclude/include rules.
 *
 * Rule syntax uses `;` for OR, `,` for AND, and `field:pattern` for each clause.
 *
 * Example:
 * <pre>{@code
 * IdeaFilter filter = new IdeaFilter(
 *         "data/table",
 *         "mysql",
 *         null,
 *         "*:*",
 *         "table:test_*; table:*_test;",
 *         "schema:db_datong;"
 * );
 * }</pre>
 */
public class IdeaFilter {

    final String domain;
    final String system;
    final String instance;
    final List<List<FilterRule>> matchRules = new ArrayList<>();
    final List<List<FilterRule>> excludeRules = new ArrayList<>();
    final List<List<FilterRule>> includeRules = new ArrayList<>();


    public IdeaFilter(@NonNull String match, @NonNull String exclude, @NonNull String include) {
        this(null, null, null, match, exclude, include);
    }

    public IdeaFilter(String domain, String system, String instance, @NonNull String match,
                      @NonNull String exclude, @NonNull String include) {
        this.domain = domain;
        this.system = system;
        this.instance = instance;
        matchRules.addAll(_parseRules(match));
        excludeRules.addAll(_parseRules(exclude));
        includeRules.addAll(_parseRules(include));
    }

    public IdeaFilter(@NonNull JsonObject filterConfig) {
        this.domain = filterConfig.getString("domain");
        this.system = filterConfig.getString("system");
        this.instance = filterConfig.getString("instance");
        matchRules.addAll(_parseRules(filterConfig.getString("match")));
        excludeRules.addAll(_parseRules(filterConfig.getString("exclude")));
        includeRules.addAll(_parseRules(filterConfig.getString("include")));
    }


    /**
     * Returns whether the idea matches the target scope and match rules.
     */
    public boolean match(@NonNull JsonObject ideaJo) {
        if (null != domain && !domain.equals(ideaJo.getString("domain"))) {
            return false;
        }
        if (null != system && !system.equals(ideaJo.getString("system"))) {
            return false;
        }
        if (null != instance && !instance.equals(ideaJo.getString("instance"))) {
            return false;
        }

        if (matchRules.stream().anyMatch(rs ->
                rs.stream().allMatch(r -> r.matches(ideaJo)))) {
            return true;
        }
        return false;
    }

    /**
     * Returns whether the idea is finally allowed after exclude/include evaluation.
     */
    public boolean allow(@NonNull JsonObject ideaJo) {
        if (!match(ideaJo)) {
            return false;
        }

        if (excludeRules.stream().anyMatch(rs -> rs.stream().allMatch(
                r -> r.matches(ideaJo)))) {
            return false;
        }

        if (includeRules.stream().anyMatch(rs -> rs.stream().allMatch(
                r -> r.matches(ideaJo)))) {
            return true;
        }

        return false;
    }



    private List<List<FilterRule>> _parseRules(String rules) {
        List<List<FilterRule>> orList = new ArrayList<>();
        for (String orRule : rules.split(";")) {
            orRule = orRule.trim();
            if (!orRule.isEmpty()) {
                List<FilterRule> andList = new ArrayList<>();
                for (String andRule : orRule.split(",")) {
                    andRule = andRule.trim();
                    if (!andRule.isEmpty()) {
                        String[] parts = andRule.split(":");
                        if (parts.length == 2) {
                            andList.add(new FilterRule(
                                    parts[0].trim(),
                                    _compilePattern(parts[1].trim())
                            ));
                        }
                    }
                }
                orList.add(andList);
            }
        }
        return orList;
    }

    private Pattern _compilePattern(String pattern) {
        String regex = pattern
                .replace("*", ".*")
                .replace("?", ".");
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static class FilterRule {
        final String ideaPartKey;
        final Pattern valuePattern;

        FilterRule(String ideaPartKey, Pattern valuePattern) {
            this.ideaPartKey = ideaPartKey;
            this.valuePattern = valuePattern;
        }

        /**
         * Returns whether the rule matches the target field or any field when `*` is used.
         */
        boolean matches(JsonObject ideaJo) {
            for (String key : ideaJo.keySet()) {
                if (ideaPartKey.equals("*") || ideaPartKey.equals(key)) {
                    if (null != ideaJo.get(key) && valuePattern.matcher(ideaJo.getString(key, "")).matches()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

}
