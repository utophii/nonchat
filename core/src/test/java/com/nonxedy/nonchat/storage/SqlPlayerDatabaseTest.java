package com.nonxedy.nonchat.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.MariaDbDataSource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.moderation.Strike;
import com.nonxedy.nonchat.moderation.ViolationType;

class SqlPlayerDatabaseTest {
    @TempDir Path folder;

    @Test
    void importsOnceAndRetainsNewerChangesAcrossRetriesAndRestarts() throws Exception {
        Nonchat plugin = mock(Nonchat.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("database-test"));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        long issued = System.currentTimeMillis();
        long expires = issued + 600_000;
        Strike strike = new Strike(alice, ViolationType.CAPS, Instant.ofEpochMilli(issued), Instant.ofEpochMilli(expires));
        Files.writeString(folder.resolve("ignore.yml"), "ignored-players:\n  " + alice + ": [" + bob + "]\n");
        Files.writeString(folder.resolve("msgtoggle.yml"), "disabled-players: [" + alice + "]\n");
        Files.writeString(folder.resolve("strikes.yml"), "[broken");

        DataSource source = testDatabase();
        try (Connection connection = source.getConnection();
             var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                assertFalse(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT).startsWith("nonchat_"),
                        "Use an empty database for the integration test");
            }
        }
        assertThrows(IllegalStateException.class, () -> new SqlPlayerDatabase(plugin, source));
        Files.writeString(folder.resolve("strikes.yml"), alice + ":\n  - type: CAPS\n    issued: " + issued
                + "\n    expires: " + expires + "\n  - type: CAPS\n    issued: 1\n    expires: 2\n");
        try (SqlPlayerDatabase database = new SqlPlayerDatabase(plugin, source)) {
            assertEquals(Map.of(alice, Set.of(bob)), database.ignores().load());
            assertEquals(Set.of(alice), database.disabledMessages().load());
            assertEquals(List.of(strike), database.strikes().load().get(alice));

            // A failure in the second table must roll back the first table as well
            try (Connection connection = source.getConnection(); var sql = connection.createStatement()) {
                sql.executeUpdate("ALTER TABLE nonchat_disabled_messages ADD CONSTRAINT test_block CHECK (player_id <> '" + bob + "')");
                database.disabledMessages().save(Set.of(bob));
                database.ignores().save(Map.of(bob, Set.of(alice)));
                assertThrows(SQLException.class, database::flush);
                try (var rs = sql.executeQuery("SELECT player_id FROM nonchat_ignores")) {
                    rs.next();
                    assertEquals(alice.toString(), rs.getString(1));
                }
                sql.executeUpdate("ALTER TABLE nonchat_disabled_messages DROP CONSTRAINT test_block");
            }
            database.strikes().save(Map.of(alice, List.of(strike, strike)));
        }
        try (SqlPlayerDatabase database = new SqlPlayerDatabase(plugin, source)) {
            assertEquals(Map.of(bob, Set.of(alice)), database.ignores().load());
            assertEquals(Set.of(bob), database.disabledMessages().load());
            assertEquals(List.of(strike, strike), database.strikes().load().get(alice));
            database.ignores().save(Map.of());
            database.disabledMessages().save(Set.of());
            database.strikes().save(Map.of());
        }
        try (SqlPlayerDatabase database = new SqlPlayerDatabase(plugin, source)) {
            assertEquals(Map.of(), database.ignores().load());
            assertEquals(Set.of(), database.disabledMessages().load());
            assertEquals(Map.of(), database.strikes().load());
        }
    }

    // Set these variables to exercise the same test against an empty, disposable server database
    private DataSource testDatabase() throws SQLException {
        String url = System.getenv("NONCHAT_TEST_JDBC_URL");
        if (url != null) {
            MariaDbDataSource source = new MariaDbDataSource(url);
            source.setUser(System.getenv("NONCHAT_TEST_DB_USER"));
            source.setPassword(System.getenv("NONCHAT_TEST_DB_PASSWORD"));
            return source;
        }
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return source;
    }
}