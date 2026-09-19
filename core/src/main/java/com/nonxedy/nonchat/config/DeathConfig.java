package com.nonxedy.nonchat.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;

/**
 * Independent configuration class for deaths.yml
 * Manages death message system settings separate from main config
 */
public class DeathConfig {
    private final File configFile;
    private final Logger logger;
    private final Nonchat plugin;
    private FileConfiguration config;

    /**
     * Creates a new DeathConfig instance
     * @param dataFolder Plugin data folder
     * @param logger Plugin logger
     * @param plugin Plugin instance for resource access
     */
    public DeathConfig(File dataFolder, Logger logger, Nonchat plugin) {
        this.configFile = new File(dataFolder, "deaths.yml");
        this.logger = logger;
        this.plugin = plugin;
    }

    /**
     * Loads the deaths.yml configuration file
     * Creates default file if it doesn't exist and updates with missing keys
     */
    public void load() {
        try {
            boolean fileExisted = configFile.exists();
            saveDefaultConfig();
            
            if (!fileExisted) {
                logger.info("Created default deaths.yml configuration file");
            }
            
            setupConfigFile(true);
            
            this.config = YamlConfiguration.loadConfiguration(configFile);
            
            if (this.config == null) {
                throw new IllegalStateException("Configuration loaded as null");
            }
            
            logger.info("Death messages configuration loaded successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load deaths.yml: " + e.getMessage(), e);
            this.config = new YamlConfiguration();
        }
    }

    /**
     * Creates deaths.yml from resource if it doesn't exist
     */
    private void saveDefaultConfig() {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                plugin.saveResource("deaths.yml", false);
                logger.info("Created default deaths.yml");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to create default deaths.yml: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Checks if the death message system is enabled
     * @return true if enabled
     */
    public boolean isEnabled() {
        return config.getBoolean("settings.enabled", true);
    }

    /**
     * Checks if fallback to generic message is enabled when no custom message is found
     * @return true if fallback is enabled
     */
    public boolean useFallback() {
        return config.getBoolean("settings.use-fallback", true);
    }

    /**
     * Checks if debug logging is enabled for death message system
     * @return true if debug is enabled
     */
    public boolean isDebugEnabled() {
        return config.getBoolean("settings.debug", false);
    }

    /**
     * Checks if death coordinates should be shown in chat
     * @return true if coordinates should be shown
     */
    public boolean showCoordinates() {
        return config.getBoolean("settings.show-coordinates", true);
    }

    /**
     * Gets the underlying FileConfiguration object
     * @return FileConfiguration instance
     */
    @NotNull
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Gets the deaths.yml file
     * @return File instance
     */
    @NotNull
    public File getConfigFile() {
        return configFile;
    }

    /**
     * Saves the configuration to file
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save deaths.yml: " + e.getMessage(), e);
        }
    }

    /**
     * Reloads the configuration from file
     */
    public void reload() {
        load();
    }

    /**
     * Gets the placeholder text for "unknown" values
     * @return Configured unknown placeholder text
     */
    @NotNull
    public String getUnknownPlaceholder() {
        return config.getString("placeholders.unknown", "Unknown");
    }

    /**
     * Gets the placeholder text for "none" values
     * @return Configured none placeholder text
     */
    @NotNull
    public String getNonePlaceholder() {
        return config.getString("placeholders.none", "None");
    }

    /**
     * Gets the placeholder text for "unknown entity" values
     * @return Configured unknown entity placeholder text
     */
    @NotNull
    public String getUnknownEntityPlaceholder() {
        return config.getString("placeholders.unknown-entity", "Something");
    }

    /**
     * Gets the placeholder text for "unknown player" values
     * @return Configured unknown player placeholder text
     */
    @NotNull
    public String getUnknownPlayerPlaceholder() {
        return config.getString("placeholders.unknown-player", "Someone");
    }

    // ========================================
    // Indirect Death Tracking Configuration
    // ========================================

    /**
     * Checks if indirect death tracking is enabled
     * @return true if indirect death tracking is enabled
     */
    public boolean isIndirectTrackingEnabled() {
        return config.getBoolean("indirect-deaths.enabled", true);
    }

    /**
     * Gets the tracking window in seconds for indirect deaths
     * Valid range: 5-60 seconds
     * @return Tracking window in seconds (default: 10)
     */
    public int getTrackingWindow() {
        int window = config.getInt("indirect-deaths.tracking-window", 10);
        
        // Validate range: 5-60 seconds
        if (window < 5) {
            logger.log(Level.WARNING, "Indirect death tracking window is too low ({0}s). Using minimum value of 5 seconds.", window);
            return 5;
        }
        if (window > 60) {
            logger.log(Level.WARNING, "Indirect death tracking window is too high ({0}s). Using maximum value of 60 seconds.", window);
            return 60;
        }
        
        return window;
    }

    /**
     * Gets the minimum damage required to trigger indirect death tracking (in hearts)
     * Valid range: 0.5-20.0 hearts
     * @return Minimum damage in hearts (default: 1.0)
     */
    public double getMinimumDamage() {
        double damage = config.getDouble("indirect-deaths.minimum-damage", 1.0);
        
        // Validate range: 0.5-20.0 hearts
        if (damage < 0.5) {
            logger.log(Level.WARNING, "Indirect death minimum damage is too low ({0} hearts). Using minimum value of 0.5 hearts.", damage);
            return 0.5;
        }
        if (damage > 20.0) {
            logger.log(Level.WARNING, "Indirect death minimum damage is too high ({0} hearts). Using maximum value of 20.0 hearts.", damage);
            return 20.0;
        }
        
        return damage;
    }

    /**
     * Checks if melee damage should be tracked for indirect deaths
     * @return true if melee damage tracking is enabled
     */
    public boolean isTrackMelee() {
        return config.getBoolean("indirect-deaths.track-melee", true);
    }

    /**
     * Checks if projectile damage should be tracked for indirect deaths
     * @return true if projectile damage tracking is enabled
     */
    public boolean isTrackProjectile() {
        return config.getBoolean("indirect-deaths.track-projectile", true);
    }

    /**
     * Checks if explosion damage should be tracked for indirect deaths
     * @return true if explosion damage tracking is enabled
     */
    public boolean isTrackExplosion() {
        return config.getBoolean("indirect-deaths.track-explosion", true);
    }

    /**
     * Gets the list of death causes that are eligible for indirect attribution
     * @return List of death cause names (e.g., "FALL", "VOID", "LAVA")
     */
    @NotNull
    public List<String> getTrackedCauses() {
        List<String> causes = config.getStringList("indirect-deaths.track-causes");
        
        // Return default list if not configured
        if (causes.isEmpty()) {
            return List.of("FALL", "VOID", "LAVA", "DROWNING", "FIRE", "FIRE_TICK", "SUFFOCATION", "CONTACT");
        }
        
        return causes;
    }

    /**
     * Checks if custom entity names (name tags) should be used in death messages
     * @return true if custom names should be used (default: true)
     */
    public boolean useCustomEntityNames() {
        return config.getBoolean("settings.entity-names.use-entity-nametags", true);
    }

    /**
     * Gets the priority order for entity names
     * @return "nametag" if name tags have priority, "custom-type" if custom-type-names have priority
     */
    @NotNull
    public String getEntityNamePriority() {
        String priority = config.getString("settings.entity-names.priority", "nametag");
        
        // Validate the priority value
        if (!"nametag".equalsIgnoreCase(priority) && !"custom-type".equalsIgnoreCase(priority)) {
            logger.log(Level.WARNING, "Invalid entity name priority: {0}. Using default: nametag", priority);
            return "nametag";
        }
        
        return priority.toLowerCase();
    }

    /**
     * Gets a custom name for an entity type if configured
     * @param entityType The entity type (e.g., "ZOMBIE", "SKELETON")
     * @return Custom name if configured, null otherwise
     */
    public String getCustomEntityTypeName(String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            return null;
        }
        
        String path = "settings.entity-names.custom-type-names." + entityType.toUpperCase();
        if (config.contains(path)) {
            return config.getString(path);
        }
        
        return null;
    }


    /**
     * Updates deaths.yml with missing keys from default configuration
     * Uses EXACT same pattern as PluginConfig.setupConfigFile()
     */
    private void setupConfigFile(boolean backup) {
        try {
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            
            FileConfiguration defaultConfig;
            try (InputStream resourceStream = plugin.getResource("deaths.yml")) {
                if (resourceStream == null) {
                    logger.log(Level.WARNING, "Could not load default deaths.yml from plugin resources");
                    return;
                }
                
                defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8)
                );
            }

            boolean hasChanges = false;
            int keysAdded = 0;
            for (String key : defaultConfig.getKeys(true)) {
                if (currentConfig.contains(key)) continue;
                if (defaultConfig.isConfigurationSection(key)) continue;

                if (shouldAddMissingKey(key, currentConfig)) {
                    currentConfig.set(key, defaultConfig.get(key));
                    hasChanges = true;
                    keysAdded++;
                    logger.log(Level.INFO, "Added missing configuration key: {0}", key);
                }
            }
            
            if (hasChanges && backup) {
                createBackup();
            }

            if (hasChanges) {
                List<String> templateAsList = readResourceToList("deaths.yml");
                HashMap<Integer, FileLine> templateLines = processFileLines(templateAsList);

                StringBuilder builder = new StringBuilder();
                HashMap<Integer, String> headers = new HashMap<>();

                int templatePositions = templateLines.size() + 1;
                for (int pos = 1; pos < templatePositions; pos++) {
                    FileLine fileLine = templateLines.get(pos);
                    if (fileLine == null) continue;

                    String line = fileLine.getLine();
                    if (!fileLine.isValue() && !fileLine.isHeader() && !fileLine.isList()) {
                        builder.append(line).append("\n");
                        continue;
                    }

                    int spaces = 0;
                    for (char c : line.toCharArray()) {
                        if (c == ' ') spaces++;
                        else break;
                    }

                    int point = spaces / 2;
                    String identifier = line.substring(spaces).split(":")[0];
                    if (fileLine.isHeader()) headers.put(point, identifier);

                    if (fileLine.isValue()) {
                        StringBuilder path = new StringBuilder();
                        for (int i = 0; i <= point - 1; i++) {
                            String header = headers.get(i);
                            if (header != null) path.append(header).append(".");
                        }
                        path.append(identifier);

                        for (int i = 0; i < spaces; i++) builder.append(" ");
                        builder.append(identifier).append(": ");

                        Object value = currentConfig.get(path.toString());
                        
                        // Skip if this is actually a configuration section (should be a header, not a value)
                        if (value instanceof ConfigurationSection) {
                            // This shouldn't happen, but if it does, treat it as empty collection
                            builder.append("{}\n");
                            continue;
                        }
                        
                        // Special handling for empty maps (should be {} not null)
                        if (value == null && currentConfig.isConfigurationSection(path.toString())) {
                            var section = currentConfig.getConfigurationSection(path.toString());
                            if (section != null && section.getKeys(false).isEmpty()) {
                                builder.append("{}\n");
                                continue;
                            }
                        }
                        
                        if (value instanceof String string) {
                            String stringValue = string.replace("\n", "\\n");
                            builder.append("\"").append(stringValue).append("\"\n");
                        } else {
                            builder.append(value).append("\n");
                        }
                        continue;
                    }

                    if (fileLine.isList()) {
                        StringBuilder path = new StringBuilder();
                        for (int i = 0; i <= point - 1; i++) {
                            String header = headers.get(i);
                            if (header != null) path.append(header).append(".");
                        }
                        path.append(identifier);

                        for (int i = 0; i < spaces; i++) builder.append(" ");
                        builder.append(identifier).append(":");

                        List<String> value = currentConfig.getStringList(path.toString());
                        if (value.isEmpty()) builder.append(" []\n");
                        else {
                            builder.append("\n");
                            for (String listLine : value) {
                                for (int i = 0; i < spaces + 2; i++) builder.append(" ");
                                String escapedListLine = listLine.replace("\n", "\\n");
                                boolean useSingleQuotes = escapedListLine.contains("\\");
                                String quote = useSingleQuotes ? "'" : "\"";
                                builder.append("- ").append(quote).append(escapedListLine).append(quote).append("\n");
                            }
                        }
                        continue;
                    }

                    // Handle headers (including empty collections like {})
                    if (fileLine.isHeader()) {
                        // Check if this is an empty collection in the template
                        String trimmedLine = line.trim();
                        if (trimmedLine.endsWith(": {}") || trimmedLine.endsWith(": []")) {
                            // Build the path for this header
                            StringBuilder path = new StringBuilder();
                            for (int i = 0; i <= point - 1; i++) {
                                String header = headers.get(i);
                                if (header != null) path.append(header).append(".");
                            }
                            path.append(identifier);
                            
                            // Check if it's a configuration section in the current config
                            if (currentConfig.isConfigurationSection(path.toString())) {
                                var section = currentConfig.getConfigurationSection(path.toString());
                                if (section != null && section.getKeys(false).isEmpty()) {
                                    // Keep it as empty collection
                                    for (int i = 0; i < spaces; i++) builder.append(" ");
                                    builder.append(identifier);
                                    if (trimmedLine.endsWith(": {}")) {
                                        builder.append(": {}\n");
                                    } else {
                                        builder.append(": []\n");
                                    }
                                    continue;
                                }
                            }
                        }
                    }

                    builder.append(line).append("\n");
                }

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                    writer.write(builder.toString());
                    writer.flush();
                }
                
                logger.info("Deaths.yml updated successfully with " + keysAdded + " new configuration option(s)");
                if (backup) {
                    logger.info("A backup of your previous deaths.yml was created in the 'backups' folder");
                }
            } else {
                logger.info("Deaths.yml is up to date, no changes needed");
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to update deaths.yml configuration: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during deaths.yml auto-update: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a backup of the current deaths.yml file
     */
    private void createBackup() {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String timestamp = dateFormat.format(new Date());
            File backupFile = new File(backupDir, "deaths_" + timestamp + ".yml.backup");

            try (BufferedReader reader = new BufferedReader(new FileReader(configFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(backupFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            
            logger.info("Created deaths.yml backup: " + backupFile.getName());

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create deaths.yml backup: " + e.getMessage(), e);
        }
    }

    /**
     * Determines if a missing key should be added based on intelligent section detection
     * 
     * @param key The configuration key to check
     * @param currentConfig The current configuration
     * @return true if the key should be added
     */
    private boolean shouldAddMissingKey(String key, FileConfiguration currentConfig) {
        if (!key.contains(".")) {
            return true;
        }

        String[] parts = key.split("\\.");
        String topLevelSection = parts[0];
        
        // For all sections, add missing keys if it's a new feature section
        if (!hasAnyKeysInSection(currentConfig, topLevelSection)) {
            return true;
        }
        
        // If user has some keys in this section, add missing keys to complete it
        return true;
    }

    /**
     * Checks if the user has ANY configuration keys under a given section
     * 
     * @param config The configuration to check
     * @param sectionName The section name to check
     * @return true if any keys exist in the section
     */
    private boolean hasAnyKeysInSection(FileConfiguration config, String sectionName) {
        if (!config.contains(sectionName)) {
            return false;
        }
        
        Set<String> allKeys = config.getKeys(true);
        for (String existingKey : allKeys) {
            if (existingKey.startsWith(sectionName + ".") || existingKey.equals(sectionName)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Reads a resource into a list of strings
     */
    private List<String> readResourceToList(String resourcePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream resource = plugin.getResource(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Processes file lines into FileLine objects
     */
    private HashMap<Integer, FileLine> processFileLines(List<String> fileLines) {
        HashMap<Integer, FileLine> processedLines = new HashMap<>();
        int positions = 1;

        for (int i = 0; i < fileLines.size(); i++) {
            String line = fileLines.get(i);
            if (isListContent(line)) continue;

            if (!line.isEmpty() && !isComment(line) && line.contains(":")) {
                String[] split = line.split(":", 2);
                String afterColon = split.length > 1 ? split[1].trim() : "";
                
                // Check if it's an empty map {} or empty array []
                boolean isEmptyCollection = afterColon.equals("{}") || afterColon.equals("[]");

                boolean isValue = split.length > 1 && !isComment(split[1]) && !isEmptyCollection;
                boolean isHeader = split.length == 1 || isComment(split[1]) || isEmptyCollection;
                boolean isList = isHeader && i + 1 < fileLines.size() && isListContent(fileLines.get(i + 1));
                
                processedLines.put(positions, new FileLine(line, isValue, isHeader, isList));
                positions++;
                continue;
            }
            processedLines.put(positions, new FileLine(line, false, false, false));
            positions++;
        }
        return processedLines;
    }

    /**
     * Checks if a line is a comment
     */
    private boolean isComment(String s) {
        return s.replace(" ", "").startsWith("#");
    }

    /**
     * Checks if a line is list content
     */
    private boolean isListContent(String s) {
        return s.replace(" ", "").startsWith("-");
    }

    /**
     * Inner class to represent a line in the configuration file
     */
    private static class FileLine {
        private final String line;
        private final boolean isValue, isHeader, isList;

        public FileLine(String line, boolean isValue, boolean isHeader, boolean isList) {
            this.line = line;
            this.isValue = isValue;
            this.isHeader = isHeader;
            this.isList = isList;
        }

        public String getLine() {
            return line;
        }

        public boolean isValue() {
            return isValue;
        }

        public boolean isHeader() {
            return isHeader;
        }

        public boolean isList() {
            return isList;
        }
    }
}
