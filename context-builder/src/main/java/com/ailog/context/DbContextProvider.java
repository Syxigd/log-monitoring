package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.MysqlTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DbContextProvider implements ContextProvider {

    private final MysqlTool mysqlTool;

    public DbContextProvider(MysqlTool mysqlTool) {
        this.mysqlTool = mysqlTool;
    }

    @Override
    public String name() {
        return "db";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("slowSql", mysqlTool.slowSql());
            data.put("locks", mysqlTool.locks());
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
