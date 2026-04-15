package dev.metaplus.backend.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlValidatorTest {

    @Test
    void requireSelectAcceptsSelectCompatibleQueries() {
        assertDoesNotThrow(() -> SqlValidator.requireSelect("select 1"));
        assertDoesNotThrow(() -> SqlValidator.requireSelect("select 1 order by 1"));
        assertDoesNotThrow(() -> SqlValidator.requireSelect("select 1 union select 2"));
        assertDoesNotThrow(() -> SqlValidator.requireSelect("with cte as (select 1) select * from cte"));
    }

    @Test
    void requireSelectRejectsMultipleStatements() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SqlValidator.requireSelect("select 1; select 2"));

        assertTrue(exception.getMessage().contains("Multiple statements detected"));
    }

    @Test
    void requireSelectRejectsNonQueryStatement() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SqlValidator.requireSelect("delete from demo"));

        assertTrue(exception.getMessage().contains("Not a query statement"));
    }

    @Test
    void requireSelectRejectsInvalidSqlSyntax() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SqlValidator.requireSelect("select from"));

        assertTrue(exception.getMessage().contains("Invalid SQL syntax"));
    }

    @Test
    void requireSelectRejectsNullSql() {
        assertThrows(NullPointerException.class, () -> SqlValidator.requireSelect(null));
    }
}
