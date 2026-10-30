package com.nonxedy.nonchat.moderation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Keeps the strikes of {@link SanctionsManager} in a YAML file, so they survive restarts:
 * <pre>
 * 3f2c...-uuid:
 *   - type: CAPS
 *     issued: 1760000000000   # epoch millis
 *     expires: 1760001800000
 * </pre>
 */
class StrikeStorage {

    private static final Logger LOGGER = Logger.getLogger("nonchat");

    private final File file;

    StrikeStorage(File file) {
        this.file = file;
    }

    /**
     * Reads the strikes that are still valid. Entries that cannot be read are skipped.
     * An unreadable file is left untouched and treated as empty.
     */
    Map<UUID, List<Strike>> load() {
        Map<UUID, List<Strike>> result = new HashMap<>();
        if (!file.exists()) {
            return result;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.log(Level.WARNING, "[nonchat] Could not read {0}, starting without strikes: {1}",
                    new Object[] {file.getName(), e.getMessage()});
            return result;
        }

        Instant now = Instant.now();
        for (String key : yaml.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }

            List<Strike> strikes = new ArrayList<>();
            for (Map<?, ?> entry : yaml.getMapList(key)) {
                Strike strike = parse(playerId, entry);
                if (strike != null && !strike.isExpired(now)) {
                    strikes.add(strike);
                }
            }
            if (!strikes.isEmpty()) {
                result.put(playerId, strikes);
            }
        }
        return result;
    }

    /**
     * Writes the strikes that are still valid, replacing the file atomically
     */
    synchronized void save(Map<UUID, List<Strike>> strikes) {
        Instant now = Instant.now();
        YamlConfiguration yaml = new YamlConfiguration();
        strikes.forEach((playerId, list) -> {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Strike strike : list) {
                if (strike.isExpired(now)) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", strike.type().name());
                entry.put("issued", strike.issuedAt().toEpochMilli());
                entry.put("expires", strike.expiresAt().toEpochMilli());
                entries.add(entry);
            }
            if (!entries.isEmpty()) {
                yaml.set(playerId.toString(), entries);
            }
        });

        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            Files.writeString(temp.toPath(), yaml.saveToString());
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[nonchat] Could not save {0}: {1}", new Object[] {file.getName(), e.getMessage()});
        }
    }

    private Strike parse(UUID playerId, Map<?, ?> entry) {
        try {
            ViolationType type = ViolationType.valueOf(String.valueOf(entry.get("type")));
            long issued = ((Number) entry.get("issued")).longValue();
            long expires = ((Number) entry.get("expires")).longValue();
            return new Strike(playerId, type, Instant.ofEpochMilli(issued), Instant.ofEpochMilli(expires));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
            return null;
        }
    }
}
