package com.nonxedy.nonchat.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mariadb.jdbc.MariaDbDataSource;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.moderation.Strike;
import com.nonxedy.nonchat.moderation.StrikeStorage;
import com.nonxedy.nonchat.moderation.ViolationType;

public final class SqlPlayerDatabase implements AutoCloseable {
    private final DataSource source;
    private final Logger logger;
    private final ScheduledExecutorService writer;
    private boolean writeFailed;
    private final SqlPlayerTable<Set<UUID>> ignores = new SqlPlayerTable<>(
            "nonchat_ignores", "player_id, ignored_id", "?, ?", (statement, id, targets) -> {
                for (UUID target : targets) {
                    statement.setString(1, id.toString());
                    statement.setString(2, target.toString());
                    statement.addBatch();
                }
            });
    private final SqlPlayerTable<Boolean> disabled = new SqlPlayerTable<>(
            "nonchat_disabled_messages", "player_id", "?", (statement, id, value) -> {
                statement.setString(1, id.toString());
                statement.addBatch();
            });
    private final SqlPlayerTable<List<Strike>> strikes = new SqlPlayerTable<>(
            "nonchat_strikes", "player_id, slot, violation, issued, expires", "?, ?, ?, ?, ?",
            (statement, id, warnings) -> {
                Instant now = Instant.now();
                int slot = 0;
                for (Strike strike : warnings) {
                    if (strike.isExpired(now)) continue;
                    statement.setString(1, id.toString());
                    statement.setInt(2, slot++);
                    statement.setString(3, strike.type().name());
                    statement.setLong(4, strike.issuedAt().toEpochMilli());
                    statement.setLong(5, strike.expiresAt().toEpochMilli());
                    statement.addBatch();
                }
            });

    public static SqlPlayerDatabase open(Nonchat plugin, ConfigurationSection config) throws SQLException {
        MariaDbDataSource source = new MariaDbDataSource(config.getString("storage.jdbc-url"));
        source.setUser(config.getString("storage.username"));
        source.setPassword(config.getString("storage.password"));
        return new SqlPlayerDatabase(plugin, source);
    }

    SqlPlayerDatabase(Nonchat plugin, DataSource source) throws SQLException {
        this.source = source;
        this.logger = plugin.getLogger();
        try (Connection connection = source.getConnection()) {
            createTables(connection);
            importYaml(connection, plugin);
            load(connection);
        }
        writer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "nonchat-database-writer");
            thread.setDaemon(true);
            return thread;
        });
        writer.scheduleWithFixedDelay(this::flushInBackground, 1, 1, TimeUnit.SECONDS);
    }

    public SnapshotStorage<Map<UUID, Set<UUID>>> ignores() {
        return ignores;
    }

    public SnapshotStorage<Set<UUID>> disabledMessages() {
        return new SnapshotStorage<>() {
            @Override
            public Set<UUID> load() {
                return disabled.load().keySet();
            }

            @Override
            public void save(Set<UUID> players) {
                disabled.save(disabledMap(players));
            }
        };
    }

    public SnapshotStorage<Map<UUID, List<Strike>>> strikes() {
        return strikes;
    }

    private static Map<UUID, Boolean> disabledMap(Set<UUID> players) {
        Map<UUID, Boolean> result = new HashMap<>();
        players.forEach(id -> result.put(id, true));
        return result;
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement()) {
            sql.executeUpdate("CREATE TABLE IF NOT EXISTS nonchat_migrations (name VARCHAR(64) PRIMARY KEY) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE IF NOT EXISTS nonchat_ignores (player_id CHAR(36), ignored_id CHAR(36),"
                    + " PRIMARY KEY (player_id, ignored_id)) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE IF NOT EXISTS nonchat_disabled_messages (player_id CHAR(36) PRIMARY KEY) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE IF NOT EXISTS nonchat_strikes (player_id CHAR(36), slot INT,"
                    + " violation VARCHAR(32) NOT NULL, issued BIGINT NOT NULL, expires BIGINT NOT NULL,"
                    + " PRIMARY KEY (player_id, slot)) ENGINE=InnoDB");
        }
    }

    private void importYaml(Connection connection, Nonchat plugin) throws SQLException {
        try (Statement sql = connection.createStatement();
             ResultSet marker = sql.executeQuery("SELECT name FROM nonchat_migrations WHERE name = 'yaml-v1'")) {
            if (marker.next()) return;
        }

        File folder = plugin.getDataFolder();
        var ignored = IgnoreStorage.read(readYaml(folder, "ignore.yml"), logger);
        var toggles = MsgToggleStorage.read(readYaml(folder, "msgtoggle.yml"), logger);
        var warnings = StrikeStorage.read(readYaml(folder, "strikes.yml"), Instant.now());
        connection.setAutoCommit(false);
        try (Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO nonchat_migrations (name) VALUES ('yaml-v1')");
            try (ResultSet existing = sql.executeQuery("SELECT EXISTS(SELECT 1 FROM nonchat_ignores)"
                    + " OR EXISTS(SELECT 1 FROM nonchat_disabled_messages) OR EXISTS(SELECT 1 FROM nonchat_strikes)")) {
                if (existing.next() && existing.getBoolean(1)) {
                    throw new SQLException("Existing nonchat player data has no YAML migration marker");
                }
            }
            ignores.write(connection, ignored);
            disabled.write(connection, disabledMap(toggles));
            strikes.write(connection, warnings);
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        }
        connection.setAutoCommit(true);
        logger.info("Imported player data from YAML into the database");
    }

    private static YamlConfiguration readYaml(File folder, String name) {
        File file = new File(folder, name);
        YamlConfiguration yaml = new YamlConfiguration();
        if (Files.notExists(file.toPath())) return yaml;
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not import " + name, e);
        }
    }

    private void load(Connection connection) throws SQLException {
        Map<UUID, Set<UUID>> ignored = new HashMap<>();
        Map<UUID, Boolean> toggles = new HashMap<>();
        Map<UUID, List<Strike>> warnings = new HashMap<>();
        try (Statement sql = connection.createStatement()) {
            try (ResultSet rs = sql.executeQuery("SELECT player_id, ignored_id FROM nonchat_ignores")) {
                while (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString(1));
                    ignored.computeIfAbsent(owner, key -> new HashSet<>()).add(UUID.fromString(rs.getString(2)));
                }
            }
            try (ResultSet rs = sql.executeQuery("SELECT player_id FROM nonchat_disabled_messages")) {
                while (rs.next()) toggles.put(UUID.fromString(rs.getString(1)), true);
            }
            try (ResultSet rs = sql.executeQuery("SELECT player_id, violation, issued, expires FROM nonchat_strikes ORDER BY player_id, slot")) {
                while (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString(1));
                    Strike strike = new Strike(owner, ViolationType.valueOf(rs.getString(2)),
                            Instant.ofEpochMilli(rs.getLong(3)), Instant.ofEpochMilli(rs.getLong(4)));
                    warnings.computeIfAbsent(owner, key -> new ArrayList<>()).add(strike);
                }
            }
        }
        ignores.initialize(ignored);
        disabled.initialize(toggles);
        strikes.initialize(warnings);
    }

    private void flushInBackground() {
        try {
            flush();
            if (writeFailed) {
                logger.info("Database writes resumed");
                writeFailed = false;
            }
        } catch (SQLException e) {
            if (!writeFailed) {
                logger.log(Level.WARNING, "Could not save player data, pending changes will be retried", e);
                writeFailed = true;
            }
        }
    }

    synchronized void flush() throws SQLException {
        var ignored = ignores.changes();
        var toggles = disabled.changes();
        var warnings = strikes.changes();
        if (ignored.isEmpty() && toggles.isEmpty() && warnings.isEmpty()) return;
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ignores.write(connection, ignored);
                disabled.write(connection, toggles);
                strikes.write(connection, warnings);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
        ignores.committed(ignored);
        disabled.committed(toggles);
        strikes.committed(warnings);
    }

    @Override
    public void close() throws SQLException {
        writer.shutdown();
        flush();
    }
}