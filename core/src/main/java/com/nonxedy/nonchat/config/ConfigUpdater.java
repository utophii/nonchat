package com.nonxedy.nonchat.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Adds keys that are missing from a user's YAML file using the bundled file as a template.
 * The bundled file's layout and comments are kept, user values replace the template values,
 * and user keys that the template does not know about are kept under their section.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {}

    /**
     * Outcome of an update.
     *
     * @param changed       true when keys were added or migrated and {@code content} should be written
     * @param content       rebuilt file content, or {@code null} when nothing changed
     * @param addedKeys     template keys that were missing from the user's file
     * @param preservedKeys user keys that are not in the template and were carried over
     */
    public record Result(boolean changed, String content, List<String> addedKeys, List<String> preservedKeys) {}

    /**
     * Updates the given file from a bundled resource, creating a backup before writing.
     * The file is left untouched when it cannot be parsed, so a typo never resets it to defaults.
     *
     * @param plugin       plugin that owns the resource and the backups folder
     * @param file         user's file to update
     * @param resourceName bundled template name, e.g. {@code config.yml}
     * @param migration    optional in-place migration run before missing keys are added;
     *                     returns true when it changed something
     * @return true when the file was rewritten
     */
    public static boolean updateFile(Plugin plugin, File file, String resourceName,
                                     Predicate<ConfigurationSection> migration) {
        Logger logger = plugin.getLogger();
        try {
            String template;
            try (InputStream stream = plugin.getResource(resourceName)) {
                if (stream == null) {
                    logger.log(Level.WARNING, "Could not load default {0} from plugin resources", resourceName);
                    return false;
                }
                template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            String current = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Result result;
            try {
                result = update(template, current, migration);
            } catch (InvalidConfigurationException e) {
                logger.log(Level.WARNING, "{0} has a syntax error, skipping automatic update: {1}",
                        new Object[]{file.getName(), e.getMessage()});
                return false;
            }

            if (!result.changed()) {
                return false;
            }

            createBackup(plugin, file);
            Files.writeString(file.toPath(), result.content(), StandardCharsets.UTF_8);

            for (String key : result.addedKeys()) {
                logger.log(Level.INFO, "Added missing configuration key: {0}", key);
            }
            for (String key : result.preservedKeys()) {
                logger.log(Level.INFO, "Preserved custom configuration key: {0}", key);
            }
            logger.log(Level.INFO, "{0} updated, a backup was created in the 'backups' folder", file.getName());
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to update " + file.getName() + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Rebuilds {@code current} on top of {@code template}.
     *
     * @throws InvalidConfigurationException when either text is not valid YAML
     */
    public static Result update(String template, String current, Predicate<ConfigurationSection> migration)
            throws InvalidConfigurationException {
        YamlConfiguration defaultConfig = new YamlConfiguration();
        defaultConfig.loadFromString(template);
        YamlConfiguration currentConfig = new YamlConfiguration();
        currentConfig.loadFromString(current);

        boolean changed = migration != null && migration.test(currentConfig);

        List<String> addedKeys = new ArrayList<>();
        for (String key : defaultConfig.getKeys(true)) {
            if (currentConfig.contains(key) || defaultConfig.isConfigurationSection(key)) continue;
            currentConfig.set(key, defaultConfig.get(key));
            addedKeys.add(key);
        }
        changed |= !addedKeys.isEmpty();

        if (!changed) {
            return new Result(false, null, addedKeys, List.of());
        }

        List<String> preservedKeys = new ArrayList<>();
        String content = new Builder(currentConfig, defaultConfig, preservedKeys).build(template.split("\\R", -1));
        return new Result(true, content, addedKeys, preservedKeys);
    }

    private static void createBackup(Plugin plugin, File file) throws IOException {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        backupDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String baseName = file.getName().replaceFirst("\\.yml$", "");
        File backupFile = new File(backupDir, baseName + "_" + timestamp + ".yml.backup");
        Files.copy(file.toPath(), backupFile.toPath());
        plugin.getLogger().log(Level.INFO, "Created backup: {0}", backupFile.getName());
    }

    /**
     * Walks the template line by line and writes user values in place of template values.
     */
    private static final class Builder {
        private final YamlConfiguration currentConfig;
        private final YamlConfiguration defaultConfig;
        private final List<String> preservedKeys;
        private final StringBuilder out = new StringBuilder();
        private final Map<Integer, String> headers = new HashMap<>();
        private final List<SectionFrame> openSections = new ArrayList<>();
        private final Set<String> preservedPaths = new HashSet<>();

        Builder(YamlConfiguration currentConfig, YamlConfiguration defaultConfig, List<String> preservedKeys) {
            this.currentConfig = currentConfig;
            this.defaultConfig = defaultConfig;
            this.preservedKeys = preservedKeys;
        }

        String build(String[] lines) {
            int listIndent = -1;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                // List items are written from the user's value together with their key,
                // including the lines of an item that is a mapping
                if (isListContent(line)) continue;
                if (listIndent >= 0) {
                    if (!line.isBlank() && !isComment(line) && countLeadingSpaces(line) > listIndent) continue;
                    listIndent = -1;
                }

                int spaces = countLeadingSpaces(line);
                boolean isEntry = !line.isBlank() && !isComment(line) && line.contains(":");
                if (!isEntry) {
                    if (isComment(line)) {
                        closeSections(spaces);
                    }
                    if (i < lines.length - 1 || !line.isEmpty()) {
                        out.append(line).append("\n");
                    }
                    continue;
                }

                closeSections(spaces);

                String[] split = line.substring(spaces).split(":", 2);
                String identifier = split[0];
                String afterColon = split[1].trim();
                boolean isHeader = afterColon.isEmpty() || isComment(afterColon);
                boolean isList = isHeader && i + 1 < lines.length && isListContent(lines[i + 1]);
                if (isList) {
                    listIndent = spaces;
                }

                int point = spaces / 2;
                String path = buildPath(point, identifier);

                if (isHeader && !isList) {
                    headers.put(point, identifier);
                    out.append(line).append("\n");
                    openSections.add(new SectionFrame(path, spaces));
                    continue;
                }

                // A scalar, an inline collection such as [] or {}, or a block list:
                // write whatever the user has there, which may be a list or a section
                if (currentConfig.contains(path)) {
                    appendEntry(path, identifier, spaces);
                } else {
                    out.append(line).append("\n");
                }
            }

            closeSections(-1);
            appendCustomChildren("", 0);
            return out.toString();
        }

        /**
         * Closes template sections that the next line is no longer inside of, writing the
         * user's custom children of each section before leaving it.
         */
        private void closeSections(int nextIndent) {
            while (!openSections.isEmpty() && nextIndent <= openSections.get(openSections.size() - 1).indent()) {
                SectionFrame section = openSections.remove(openSections.size() - 1);
                appendCustomChildren(section.path(), section.indent() + 2);
            }
        }

        private void appendCustomChildren(String sectionPath, int indent) {
            ConfigurationSection section = sectionPath.isEmpty()
                    ? currentConfig
                    : currentConfig.getConfigurationSection(sectionPath);
            if (section == null) return;

            for (String childKey : section.getKeys(false)) {
                String childPath = sectionPath.isEmpty() ? childKey : sectionPath + "." + childKey;
                if (defaultConfig.contains(childPath) || !preservedPaths.add(childPath)) continue;

                appendEntry(childPath, childKey, indent);
                preservedKeys.add(childPath);
            }
        }

        private void appendEntry(String path, String key, int indent) {
            appendIndent(indent);
            out.append(key).append(":");

            if (currentConfig.isConfigurationSection(path)) {
                ConfigurationSection section = currentConfig.getConfigurationSection(path);
                if (section == null || section.getKeys(false).isEmpty()) {
                    out.append(" {}\n");
                    return;
                }

                out.append("\n");
                for (String childKey : section.getKeys(false)) {
                    appendEntry(path + "." + childKey, childKey, indent + 2);
                }
                return;
            }

            Object value = currentConfig.get(path);
            if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    out.append(" []\n");
                    return;
                }

                out.append("\n");
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        appendMapItem(map, indent);
                        continue;
                    }
                    appendIndent(indent + 2);
                    out.append("- ").append(formatScalar(item)).append("\n");
                }
                return;
            }

            out.append(" ").append(formatScalar(value)).append("\n");
        }

        /** Writes a list item that is a mapping, e.g. a moderation tier */
        private void appendMapItem(Map<?, ?> map, int indent) {
            appendIndent(indent + 2);
            out.append("- ");
            if (map.isEmpty()) {
                out.append("{}\n");
                return;
            }

            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    appendIndent(indent + 4);
                }
                out.append(entry.getKey()).append(": ").append(formatInline(entry.getValue())).append("\n");
                first = false;
            }
        }

        private String buildPath(int point, String identifier) {
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < point; i++) {
                String header = headers.get(i);
                if (header != null) path.append(header).append(".");
            }
            return path.append(identifier).toString();
        }

        private void appendIndent(int spaces) {
            out.append(" ".repeat(Math.max(0, spaces)));
        }
    }

    private static String formatInline(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(ConfigUpdater::formatScalar).collect(Collectors.joining(", ", "[", "]"));
        }
        return formatScalar(value);
    }

    static String formatScalar(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        String escaped = String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static int countLeadingSpaces(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') spaces++;
        return spaces;
    }

    private static boolean isComment(String s) {
        return s.stripLeading().startsWith("#");
    }

    private static boolean isListContent(String s) {
        return s.stripLeading().startsWith("-");
    }

    private record SectionFrame(String path, int indent) {}
}
