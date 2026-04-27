package dev.metaplus.backend.lib.sql;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;

import java.util.List;

@Slf4j
public final class SqlValidator {

    public static void requireSelect(@NonNull String sql) {
        try {
            SqlParser.Config parserConfig = SqlParser.config().withCaseSensitive(false);
            SqlParser parser = SqlParser.create(sql, parserConfig);
            List<SqlNode> stmts = parser.parseStmtList();

            if (stmts.size() != 1) {
                throw new IllegalArgumentException("Multiple statements detected");
            }

            SqlKind kind = stmts.get(0).getKind();
            if (!_isQueryKind(kind)) {
                throw new IllegalArgumentException("Not a query statement");
            }

        } catch (SqlParseException e) {
            throw new IllegalArgumentException("Invalid SQL syntax: " + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unexpected error: " + e.toString());
        }
    }


    /// private

    private static boolean _isQueryKind(SqlKind kind) {
        if (kind == null) return false;
        return switch (kind) {
            case SELECT, ORDER_BY, UNION, INTERSECT, MINUS, WITH -> true;
            default -> false;
        };
    }

}
