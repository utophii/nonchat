package com.nonxedy.nonchat.storage;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

import com.nonxedy.nonchat.Nonchat;

/**
 * Persists the players who turned their private messages off.
 *
 * <p>The file contains UUIDs only, so renaming a player keeps the setting.</p>
 */
public final class MsgToggleStorage implements SnapshotStorage<Set<UUID>> {
    private static final String ROOT_PATH = "disabled-players";

    private final Nonchat plugin;
    private final File file;

    public MsgToggleStorage(Nonchat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "msgtoggle.yml");
    }

    @Override
    public Set<UUID> load() {
        return read(file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration(), plugin.getLogger());
    }

    static Set<UUID> read(YamlConfiguration configuration, Logger logger) {
        Set<UUID> result = new HashSet<>();
        for (String playerId : configuration.getStringList(ROOT_PATH)) {
            try {
                result.add(UUID.fromString(playerId));
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping invalid player UUID in msgtoggle.yml: " + playerId);
            }
        }
        return result;
    }

    /** Writes a detached snapshot, avoiding mutable state leaking into I/O. */
    @Override
    public void save(Set<UUID> disabledPlayers) {
        YamlConfiguration configuration = new YamlConfiguration();
        List<String> playerIds = disabledPlayers.stream()
                .map(UUID::toString)
                .sorted()
                .toList();
        configuration.set(ROOT_PATH, playerIds);

        try {
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Unable to create data directory for msgtoggle.yml");
                return;
            }
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save private message toggles", exception);
        }
    }
}
