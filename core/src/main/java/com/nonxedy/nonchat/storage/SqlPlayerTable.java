package com.nonxedy.nonchat.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// tracks the latest change per player while a background transaction is in flight
final class SqlPlayerTable<V> implements SnapshotStorage<Map<UUID, V>> {
    @FunctionalInterface
    interface Rows<V> {
        void add(PreparedStatement insert, UUID player, V value) throws SQLException;
    }

    private final String table;
    private final String insertSql;
    private final Rows<V> rows;
    private Map<UUID, V> current;
    private final Map<UUID, V> dirty = new HashMap<>();

    SqlPlayerTable(String table, String columns, String parameters, Rows<V> rows) {
        this.table = table;
        this.insertSql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + parameters + ")";
        this.rows = rows;
    }

    void initialize(Map<UUID, V> loaded) {
        current = loaded;
    }

    @Override
    public synchronized Map<UUID, V> load() {
        return Map.copyOf(current);
    }

    @Override
    public synchronized void save(Map<UUID, V> snapshot) {
        snapshot.forEach((id, value) -> {
            if (!Objects.equals(current.get(id), value)) {
                dirty.put(id, value);
            }
        });
        for (UUID id : current.keySet()) {
            if (!snapshot.containsKey(id)) {
                dirty.put(id, null);
            }
        }
        current = snapshot;
    }

    synchronized Map<UUID, V> changes() {
        return new HashMap<>(dirty);
    }

    synchronized void committed(Map<UUID, V> written) {
        written.forEach(dirty::remove);
    }

    void write(Connection connection, Map<UUID, V> changes) throws SQLException {
        if (changes.isEmpty()) return;
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table + " WHERE player_id = ?");
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            for (var entry : changes.entrySet()) {
                UUID id = entry.getKey();
                delete.setString(1, id.toString());
                delete.addBatch();
                if (entry.getValue() != null) {
                    rows.add(insert, id, entry.getValue());
                }
            }
            delete.executeBatch();
            insert.executeBatch();
        }
    }
}