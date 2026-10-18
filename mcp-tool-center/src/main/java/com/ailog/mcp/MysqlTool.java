package com.ailog.mcp;

import com.ailog.mcp.security.SqlReadOnlyGuard;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MysqlTool {

    private final JdbcTemplate jdbcTemplate;
    private final SqlReadOnlyGuard sqlReadOnlyGuard;

    public MysqlTool(JdbcTemplate jdbcTemplate, SqlReadOnlyGuard sqlReadOnlyGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlReadOnlyGuard = sqlReadOnlyGuard;
    }

    @Tool(description = "Execute a SELECT query and return results")
    public String query(String sql) {
        try {
            sqlReadOnlyGuard.assertReadOnly(sql);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (rows.isEmpty()) return "0 rows returned";
            return rows.stream()
                    .limit(50)
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "List all tables in the database")
    public String tables() {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList("SHOW TABLES");
        return tables.stream()
                .map(m -> String.valueOf(m.values().iterator().next()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Show indexes for a table")
    public String indexes(String table) {
        if (table == null || !table.matches("[A-Za-z0-9_$]+")) {
            return "Error: invalid table identifier";
        }
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM `" + table + "`");
        return indexes.stream()
                .map(m -> String.format("%s (%s) %s",
                        m.get("Key_name"), m.get("Column_name"), m.get("Non_unique")))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Show current database locks / processlist")
    public String locks() {
        List<Map<String, Object>> processes = jdbcTemplate.queryForList("SHOW PROCESSLIST");
        return processes.stream()
                .map(m -> String.format("%s  %s  %s  %s",
                        m.get("Id"), m.get("User"), m.get("Command"), m.get("Info")))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Find slow queries (longer than 1 second)")
    public String slowSql() {
        List<Map<String, Object>> slow = jdbcTemplate.queryForList(
                "SELECT * FROM information_schema.PROCESSLIST WHERE TIME > 1");
        return slow.stream()
                .map(m -> String.format("%ss  %s", m.get("Time"), m.get("Info")))
                .collect(Collectors.joining("\n"));
    }
}
