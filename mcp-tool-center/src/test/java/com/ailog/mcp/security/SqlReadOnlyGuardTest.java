package com.ailog.mcp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlReadOnlyGuardTest {

    private final SqlReadOnlyGuard guard = new SqlReadOnlyGuard();

    @Test
    void allowsReadOnlyStatementsWithoutSeparators() {
        assertDoesNotThrow(() -> guard.assertReadOnly("  /* explain */ SELECT * FROM users  "));
        assertDoesNotThrow(() -> guard.assertReadOnly("SHOW TABLES"));
        assertDoesNotThrow(() -> guard.assertReadOnly("WITH recent AS (SELECT 1) SELECT * FROM recent"));
    }

    @Test
    void rejectsWritesAndMultipleStatements() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertReadOnly("SELECT * FROM users; DELETE FROM users"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertReadOnly("SELECT * FROM users;"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertReadOnly("UPDATE users SET name = 'x'"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertReadOnly("SELECT * FROM users FOR UPDATE"));
    }
}
