package com.nonxedy.nonchat.moderation;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class StrikeStorageRecoveryTest {
    @Test
    void unreadableFilesSurviveSubsequentSaves(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("strikes.yml");
        String broken = "strikes: [\n";
        for (int i = 0; i < 2; i++) {
            Files.writeString(file, broken);
            StrikeStorage storage = new StrikeStorage(file.toFile());
            assertTrue(storage.load().isEmpty());
            storage.save(Map.of());
        }
        try (var paths = Files.list(dir)) {
            var backups = paths.filter(p -> p.getFileName().toString().startsWith("strikes.yml.unreadable-")).toList();
            assertEquals(2, backups.size());
            for (Path backup : backups) assertEquals(broken, Files.readString(backup));
        }
    }

    @Test
    void failedPreservationDisablesWrites(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("strikes.yml");
        String broken = "strikes: [\n";
        Files.writeString(file, broken);
        StrikeStorage storage = new StrikeStorage(file.toFile());
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(eq(file), any(Path.class), any(CopyOption[].class)))
                    .thenThrow(new IOException("move denied"));
            assertTrue(storage.load().isEmpty());
        }
        storage.save(Map.of());
        assertEquals(broken, Files.readString(file));
        assertFalse(Files.exists(dir.resolve("strikes.yml.tmp")));
    }
}
