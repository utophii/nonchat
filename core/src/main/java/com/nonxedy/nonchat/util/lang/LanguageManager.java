package com.nonxedy.nonchat.util.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

// Manages language files and provides methods to load and get translations
public class LanguageManager {

    // Directory containing all language files
    private final File langsFolder;
    // Currently active language configuration
    private FileConfiguration currentLang;
    // Map of language codes to their corresponding configurations
    private final Map<String, FileConfiguration> loadedLanguages;
    
    /**
     * Initializes the language manager and sets up default languages
     * @param dataFolder The plugin's data folder where language files will be stored
     */
    public LanguageManager(File dataFolder) {
        this.langsFolder = new File(dataFolder, "langs");
        this.loadedLanguages = new HashMap<>();
        setupLanguages();
    }
    
    /**
     * Creates the language directory and default language files
     * Loads all available language files into memory
     */
    private void setupLanguages() {
        if (!langsFolder.exists()) {
            langsFolder.mkdirs();
        }
        
        // Create default language files if they don't exist
        createLanguageFile("messages_en.yml");
        createLanguageFile("messages_ru.yml");
        createLanguageFile("messages_es.yml");
        
        // Load all language files from the directory
        for (File file : langsFolder.listFiles()) {
            if (file.getName().startsWith("messages_") && file.getName().endsWith(".yml")) {
                String langCode = file.getName().replace("messages_", "").replace(".yml", "");
                loadedLanguages.put(langCode, loadLanguageFile(file));
            }
        }
    }
    
    /**
     * Loads a language file and sets the bundled resource as its default values.
     * Keys added in new plugin versions keep resolving through the bundled file
     * even when the server still has an older extracted copy of the language file.
     * @param file The extracted language file
     * @return Loaded configuration with bundled defaults applied
     */
    private YamlConfiguration loadLanguageFile(File file) {
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = getClass().getResourceAsStream("/langs/" + file.getName())) {
            if (in != null) {
                lang.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to load default language resource: {0}", e.getMessage());
        }
        return lang;
    }
    
    /**
     * Creates a new language file by copying it from plugin resources
     * @param filename Name of the language file to create
     */
    private void createLanguageFile(String filename) {
        File langFile = new File(langsFolder, filename);
        if (!langFile.exists()) {
            // Copy default language file from resources to the language folder
            try (InputStream in = getClass().getResourceAsStream("/langs/" + filename)) {
                Files.copy(in, langFile.toPath());
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to create language file: {0}", e.getMessage());
            }
        }
    }
    
    /**
     * Sets the active language for message retrieval
     * Falls back to English if specified language is not found
     * @param lang Language code to set as active
     */
    public void setLanguage(String lang) {
        currentLang = loadedLanguages.getOrDefault(lang, loadedLanguages.get("en"));
    }

    /**
     * Reloads all language files from disk
     * This should be called when the plugin is reloaded to pick up changes to message files
     */
    public void reload() {
        // Clear the loaded languages map
        loadedLanguages.clear();
        
        // Reload all language files from the directory
        for (File file : langsFolder.listFiles()) {
            if (file.getName().startsWith("messages_") && file.getName().endsWith(".yml")) {
                String langCode = file.getName().replace("messages_", "").replace(".yml", "");
                loadedLanguages.put(langCode, loadLanguageFile(file));
            }
        }
        
        // Restore the current language setting
        if (currentLang != null) {
            // Find which language was currently active
            String currentLangCode = null;
            for (Map.Entry<String, FileConfiguration> entry : loadedLanguages.entrySet()) {
                if (entry.getValue() == currentLang) {
                    currentLangCode = entry.getKey();
                    break;
                }
            }
            
            // If we can't determine the current language, try to set it again
            if (currentLangCode != null) {
                setLanguage(currentLangCode);
            } else if (!loadedLanguages.isEmpty()) {
                // Fallback to the first available language or English
                setLanguage(loadedLanguages.containsKey("en") ? "en" : loadedLanguages.keySet().iterator().next());
            }
        }
    }

    /**
     * Retrieves a raw message from the current language configuration
     * @param key The message key to retrieve
     * @return The raw message string, or an error message if key not found
     */
    public String getMessage(String key) {
        return currentLang.getString(key, "Missing message: " + key);
    }
}
