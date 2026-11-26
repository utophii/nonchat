package com.nonxedy.nonchat.storage;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.nonxedy.nonchat.Nonchat;

/**
 * Persists player ignore lists independently from the main plugin configuration.
 *
 * <p>The file contains UUIDs only, so renaming a player does not invalidate an
 * existing ignore relationship.</p>
 */
public final class IgnoreStorage implements SnapshotStorage<Map<UUID, Set<UUID>>> {
    private static final String ROOT_PATH = "ignored-players";

    private final Nonchat plugin;
    private final File file;

    public IgnoreStorage(Nonchat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ignore.yml");
    }

    @Override
    public Map<UUID, Set<UUID>> load() {
        return read(file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration(), plugin.getLogger());
    }

    static Map<UUID, Set<UUID>> read(YamlConfiguration configuration, Logger logger) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        ConfigurationSection owners = configuration.getConfigurationSection(ROOT_PATH);
        if (owners == null) {
            return result;
        }

        for (String ownerId : owners.getKeys(false)) {
            UUID owner = parseUuid(ownerId, "owner", logger);
            if (owner == null) {
                continue;
            }

            Set<UUID> ignored = new HashSet<>();
            for (String ignoredId : configuration.getStringList(ROOT_PATH + "." + ownerId)) {
                UUID ignoredPlayer = parseUuid(ignoredId, "ignored player", logger);
                if (ignoredPlayer != null && !owner.equals(ignoredPlayer)) {
                    ignored.add(ignoredPlayer);
                }
            }
            if (!ignored.isEmpty()) {
                result.put(owner, ignored);
            }
        }
        return result;
    }

    /** Writes a detached snapshot, avoiding mutable state leaking into I/O. */
    @Override
    public void save(Map<UUID, Set<UUID>> ignoreLists) {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : ignoreLists.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> ignoredIds = entry.getValue().stream()
                    .map(UUID::toString)
                    .sorted()
                    .toList();
            configuration.set(ROOT_PATH + "." + entry.getKey(), ignoredIds);
        }

        try {
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Unable to create data directory for ignore.yml");
                return;
            }
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save ignore lists", exception);
        }
    }

    private static UUID parseUuid(String value, String type, Logger logger) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            logger.warning("Skipping invalid " + type + " UUID in ignore.yml: " + value);
            return null;
        }
    }
}
