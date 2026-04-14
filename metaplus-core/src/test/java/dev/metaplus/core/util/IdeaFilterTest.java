package dev.metaplus.core.util;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeaFilterTest {

    @Test
    void matchHonorsScopeAndAndOrRules() {
        IdeaFilter filter = new IdeaFilter(
                "data",
                "mysql",
                "main",
                "entity:sales.*,owner:data-team;entity:finance.*",
                "",
                "system:mysql"
        );

        JsonObject matchedIdea = JsonObject.of(
                "domain", "data",
                "system", "mysql",
                "instance", "main",
                "entity", "sales.orders",
                "owner", "data-team"
        );
        JsonObject wrongScopeIdea = JsonObject.of(
                "domain", "data",
                "system", "mysql",
                "instance", "test",
                "entity", "sales.orders",
                "owner", "data-team"
        );

        assertTrue(filter.match(matchedIdea));
        assertFalse(filter.match(wrongScopeIdea));
    }

    @Test
    void allowRequiresIncludeAndRespectsExcludePrecedence() {
        IdeaFilter filter = new IdeaFilter(
                "domain:data",
                "status:deprecated",
                "entity:sales.*"
        );

        JsonObject allowedIdea = JsonObject.of(
                "domain", "data",
                "entity", "sales.orders",
                "status", "active"
        );
        JsonObject excludedIdea = JsonObject.of(
                "domain", "data",
                "entity", "sales.orders",
                "status", "deprecated"
        );
        JsonObject notIncludedIdea = JsonObject.of(
                "domain", "data",
                "entity", "finance.budget",
                "status", "active"
        );

        assertTrue(filter.allow(allowedIdea));
        assertFalse(filter.allow(excludedIdea));
        assertFalse(filter.allow(notIncludedIdea));
    }
}
