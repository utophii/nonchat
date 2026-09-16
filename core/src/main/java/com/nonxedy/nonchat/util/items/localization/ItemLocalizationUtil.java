package com.nonxedy.nonchat.util.items.localization;

import java.text.MessageFormat;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import com.nonxedy.nonchat.util.core.debugging.Debugger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;

/**
 * Utility class for localizing item and enchantment names using Minecraft's translation system
 * Uses Paper's GlobalTranslator API for server-side translation
 */
public class ItemLocalizationUtil {
    
    private static Debugger debugger;
    
    public static void setDebugger(Debugger debugger) {
        ItemLocalizationUtil.debugger = debugger;
    }
    
    /**
     * Gets the localized name of a material using Minecraft's translation keys
     * @param material The material to localize
     * @param language The language code ("en" or "ru")
     * @return Localized material name component
     */
    public static Component getLocalizedMaterialComponent(Material material) {
        String translationKey = getTranslationKey(material);
        return Component.translatable(translationKey);
    }
    
    /**
     * Gets the localized name of a material as string (fallback)
     * @param material The material to localize
     * @param language The language code ("en" or "ru")
     * @return Localized material name
     */
    public static String getLocalizedMaterialName(Material material, String language) {
        // For now, return formatted name as fallback
        // The actual translation will happen on the client side
        return formatMaterialName(material.name());
    }
    
    /**
     * Gets the localized name of an enchantment using Minecraft's translation keys
     * @param enchantment The enchantment to localize
     * @return Localized enchantment component
     */
    public static Component getLocalizedEnchantmentComponent(Enchantment enchantment) {
        String translationKey = "enchantment.minecraft." + enchantment.getKey().getKey();
        return Component.translatable(translationKey);
    }
    
    /**
     * Gets the localized name of an enchantment as string (fallback)
     * @param enchantment The enchantment to localize
     * @param language The language code ("en" or "ru")
     * @return Localized enchantment name
     */
    public static String getLocalizedEnchantmentName(Enchantment enchantment, String language) {
        // For now, return formatted name as fallback
        return formatEnchantmentName(enchantment.getKey().getKey());
    }
    
    /**
     * Creates a translatable component for an item
     * This will be automatically translated by the client
     * @param item The item to create component for
     * @return Translatable component
     */
    public static Component createTranslatableItemComponent(ItemStack item) {
        return createTranslatableItemComponent(item, null);
    }
    
    /**
     * Creates a translatable component for an item with specific locale
     * Uses Paper's GlobalTranslator for server-side translation
     * @param item The item to create component for
     * @param locale The locale to use for translation (e.g., "ru_ru"), or null for default
     * @return Translatable component or translated text
     */
    public static Component createTranslatableItemComponent(ItemStack item, String locale) {
        if (item == null || item.getType().isAir()) {
            return Component.translatable("item.minecraft.air");
        }
        
        // Check if item has custom display name - use that instead of translating
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName();
        }
        
        // Get the translation key for this item
        String translationKey = getTranslationKey(item.getType());
        
        // Create a translatable component
        TranslatableComponent translatable = Component.translatable(translationKey);
        
        // If locale is provided, try to translate server-side
        if (locale != null && !locale.isEmpty()) {
            try {
                // Convert locale string to Locale object
                Locale targetLocale = parseLocale(locale);
                
                // Try to get translation from GlobalTranslator
                Component translated = translateWithGlobalTranslator(translationKey, targetLocale);
                
                // If translation was successful, return it
                if (translated != null) {
                    return translated;
                }
            } catch (Exception e) {
                if (debugger != null) {
                    debugger.error("ItemLocalization", "Failed to translate item with key '" + translationKey + "' for locale '" + locale + "': " + e.getMessage(), e);
                }
            }
        }
        
        return translatable;
    }
    
    /**
     * Gets the translated item name as a plain string
     * Uses Paper's GlobalTranslator for server-side translation
     * @param item The item to translate
     * @param locale The locale to translate to (e.g., "ru_ru")
     * @return Translated item name as string
     */
    public static String getTranslatedItemName(ItemStack item, String locale) {
        if (item == null || item.getType().isAir()) {
            return "Air";
        }
        
        // Check if item has custom display name
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        
        // Get the translation key for this item
        String translationKey = getTranslationKey(item.getType());
        
        // If locale is provided, try to translate
        if (locale != null && !locale.isEmpty()) {
            try {
                Locale targetLocale = parseLocale(locale);
                
                // Try to translate using GlobalTranslator
                Component translated = translateWithGlobalTranslator(translationKey, targetLocale);
                
                if (translated != null) {
                    return PlainTextComponentSerializer.plainText().serialize(translated);
                }
            } catch (Exception e) {
                if (debugger != null) {
                    debugger.error("ItemLocalization", "Failed to get translated item name for key '" + translationKey + "' and locale '" + locale + "': " + e.getMessage(), e);
                }
            }
        }
        
        // Fallback: format the material name ourselves
        return formatMaterialName(item.getType().name());
    }
    
    /**
     * Translates a string using GlobalTranslator
     * @param key The translation key
     * @param locale The target locale
     * @return Translated component
     */
    private static Component translateWithGlobalTranslator(String key, Locale locale) {
        try {
            // Get the GlobalTranslator instance
            Translator translator = GlobalTranslator.translator();
            
            if (translator == null) {
                if (debugger != null) {
                    debugger.warn("ItemLocalization", "GlobalTranslator is null, cannot translate key: " + key);
                }
                return null;
            }
            
            // Translate returns MessageFormat, convert to string then to Component
            MessageFormat translated = translator.translate(key, locale);
            
            if (translated != null) {
                // Format the MessageFormat to a string
                String translatedText = translated.toPattern();
                // Also try to format with empty arguments
                try {
                    translatedText = translated.format(new Object[]{});
                } catch (Exception e) {
                    // Ignore - use the pattern
                }
                return Component.text(translatedText);
            }
            
            return null;
        } catch (Exception e) {
            if (debugger != null) {
                debugger.error("ItemLocalization", "Failed to translate key '" + key + "' with locale '" + locale + "': " + e.getMessage(), e);
            }
            return null;
        }
    }
    
    /**
     * Parses a locale string (e.g., "ru_ru", "en-US") into a Locale object
     * @param localeString The locale string
     * @return Locale object
     */
    private static Locale parseLocale(String localeString) {
        if (localeString == null || localeString.isEmpty()) {
            return Locale.ENGLISH;
        }
        
        // Handle Minecraft locale format (ru_ru) -> Java format (ru_RU)
        String locale = localeString.replace("_", "-");
        
        // Split by dash
        String[] parts = locale.split("-");
        
        if (parts.length >= 2) {
            // Format: ru_ru / ru-RU
            return Locale.of(parts[0], parts[1].toUpperCase(Locale.ROOT));
        } else if (parts.length == 1) {
            // Format: ru
            return Locale.of(parts[0]);
        }
        
        return Locale.ENGLISH;
    }
    
    /**
     * Gets the appropriate translation key for a material
     * @param material The material
     * @return Translation key string
     */
    private static String getTranslationKey(Material material) {
        String materialName = material.name().toLowerCase();
        
        // Check if it's a block or item
        if (material.isBlock()) {
            return "block.minecraft." + materialName;
        } else {
            return "item.minecraft." + materialName;
        }
    }
    
    /**
     * Formats a material name to be more readable (fallback)
     * @param materialName The material name to format
     * @return Formatted material name
     */
    private static String formatMaterialName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            // Capitalize first letter of each word
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Formats an enchantment name to be more readable (fallback)
     * @param enchantmentName The enchantment name to format
     * @return Formatted enchantment name
     */
    private static String formatEnchantmentName(String enchantmentName) {
        String name = enchantmentName.replace('_', ' ');
        
        StringBuilder formattedName = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                capitalizeNext = true;
                formattedName.append(c);
            } else {
                if (capitalizeNext) {
                    formattedName.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    formattedName.append(c);
                }
            }
        }
        
        return formattedName.toString();
    }
}
