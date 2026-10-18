package com.ailog.mcp.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SqlReadOnlyGuard {

    private static final Set<String> READ_ONLY_START = Set.of(
            "SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "WITH");
    private static final Set<String> FORBIDDEN = Set.of(
            "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "CREATE", "ALTER", "DROP",
            "TRUNCATE", "GRANT", "REVOKE", "CALL", "DO", "SET", "USE", "KILL", "LOAD",
            "HANDLER", "LOCK", "UNLOCK", "START", "COMMIT", "ROLLBACK", "ANALYZE",
            "OPTIMIZE", "REPAIR");

    public void assertReadOnly(String sql) {
        List<String> tokens = tokenize(sql);
        if (tokens.isEmpty() || !READ_ONLY_START.contains(tokens.get(0))) {
            throw new IllegalArgumentException("Only read-only SQL statements are allowed");
        }
        if (tokens.stream().anyMatch(FORBIDDEN::contains)) {
            throw new IllegalArgumentException("SQL contains a write or administrative operation");
        }
        String normalized = String.join(" ", tokens);
        if (normalized.contains("FOR UPDATE") || normalized.contains("LOCK IN SHARE MODE")
                || normalized.contains("INTO OUTFILE") || normalized.contains("INTO DUMPFILE")) {
            throw new IllegalArgumentException("Locking or file-writing SQL is not allowed");
        }
    }

    private List<String> tokenize(String sql) {
        if (sql == null || sql.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n' || c == '\r') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && c == '-' && next == '-') {
                flush(token, tokens);
                lineComment = true;
                i++;
                continue;
            }
            if (!singleQuote && !doubleQuote && c == '#') {
                flush(token, tokens);
                lineComment = true;
                continue;
            }
            if (!singleQuote && !doubleQuote && c == '/' && next == '*') {
                flush(token, tokens);
                blockComment = true;
                i++;
                continue;
            }
            if (c == '\'' && !doubleQuote) {
                if (singleQuote && next == '\'') i++;
                singleQuote = !singleQuote;
                continue;
            }
            if (c == '"' && !singleQuote) {
                if (doubleQuote && next == '"') i++;
                doubleQuote = !doubleQuote;
                continue;
            }
            if (!singleQuote && !doubleQuote && c == ';') {
                throw new IllegalArgumentException("Semicolons and multiple SQL statements are not allowed");
            }
            if (!singleQuote && !doubleQuote && Character.isLetterOrDigit(c)) {
                token.append(c);
            } else if (!singleQuote && !doubleQuote) {
                flush(token, tokens);
            }
        }
        if (singleQuote || doubleQuote || blockComment) {
            throw new IllegalArgumentException("SQL contains an unterminated quote or comment");
        }
        flush(token, tokens);
        return tokens;
    }

    private void flush(StringBuilder token, List<String> tokens) {
        if (token.length() > 0) {
            tokens.add(token.toString().toUpperCase(Locale.ROOT));
            token.setLength(0);
        }
    }

}
