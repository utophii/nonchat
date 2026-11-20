package com.nonxedy.nonchat.util.items.detection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.chat.filters.LinkDetector;
import com.nonxedy.nonchat.util.items.display.ItemDisplayUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;

/**
 * Utility for detecting and replacing item placeholders in chat messages
 * Uses client-side localization for proper translation
 */
public class ItemDetector {
    // Pattern to match the [item] placeholder
    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[item\\]", Pattern.CASE_INSENSITIVE);
    
    /**
     * Scans the text for [item] placeholders and replaces them with 
     * clickable, hoverable item information with client-side localization
     *
     * @param player The player who sent the message
     * @param text The message text to process
     * @return Component with item placeholders converted to hoverable text
     */
    public static Component processItemPlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return Component.text(text);
        }
        
        // Check if interactive placeholders are enabled
        Plugin plugin = Bukkit.getPluginManager().getPlugin("nonchat");
        if (plugin instanceof Nonchat nonchatPlugin) {
            boolean globalEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.enabled", true);
            boolean itemEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.item-enabled", true);
            
            if (!globalEnabled || !itemEnabled) {
                return LinkDetector.makeLinksClickable(text, player);
            }
        }
        
        // Initialize the component with an empty text
        ComponentBuilder<?, ?> builder = Component.text().content("");
        
        // Get matcher for the item pattern
        Matcher matcher = ITEM_PATTERN.matcher(text);
        
        // Keep track of the last match end position
        int lastEnd = 0;
        
        // Flag to indicate if any replacements were made
        boolean foundItem = false;
        
        // Loop through all matches
        while (matcher.find()) {
            // Add the text before the [item] placeholder (with link detection)
            if (matcher.start() > lastEnd) {
                String textBefore = text.substring(lastEnd, matcher.start());
                builder.append(LinkDetector.makeLinksClickable(textBefore, player));
            }
            
            // Get held item
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            
            // Use the new bracketed item component method with client-side localization
            Component itemComponent = ItemDisplayUtil.createBracketedItemComponent(heldItem);
            builder.append(itemComponent);
            
            // Update lastEnd and set flag
            lastEnd = matcher.end();
            foundItem = true;
        }
        
        // Add any remaining text after the last match (with link detection)
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            builder.append(LinkDetector.makeLinksClickable(remainingText, player));
        }
        
        // If no items were found, return the original text with link detection
        if (!foundItem) {
            return LinkDetector.makeLinksClickable(text, player);
        }
        
        // Return the built component
        return builder.build();
    }
}
