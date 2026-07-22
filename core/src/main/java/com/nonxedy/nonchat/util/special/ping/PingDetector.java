package com.nonxedy.nonchat.util.special.ping;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.nonxedy.nonchat.Nonchat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Utility for detecting and replacing ping placeholders in chat messages
 * Similar to ItemDetector but for [ping] placeholders
 */
public class PingDetector {
    // Pattern to match the [ping] placeholder
    private static final Pattern PING_PATTERN = Pattern.compile("\\[ping\\]", Pattern.CASE_INSENSITIVE);
    
    /**
     * Scans the text for [ping] placeholders and replaces them with 
     * colored ping values
     *
     * @param player The player whose ping to check
     * @param text The message text to process
     * @return Component with ping placeholders converted to colored values
     */
    public static Component processPingPlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return Component.text(text);
        }
        
        // Check if interactive placeholders are enabled
        Plugin plugin = Bukkit.getPluginManager().getPlugin("nonchat");
        if (!(plugin instanceof Nonchat nonchatPlugin)) {
            return Component.text(text);
        }
        
        boolean globalEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.enabled", true);
        boolean pingEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.ping-enabled", true);
        
        if (!globalEnabled || !pingEnabled) {
            return Component.text(text);
        }
        
        ComponentBuilder<?, ?> builder = Component.text().content("");
        Matcher matcher = PING_PATTERN.matcher(text);
        int lastEnd = 0;
        boolean foundPing = false;
        
        while (matcher.find()) {
            // Add text before the placeholder
            if (matcher.start() > lastEnd) {
                String textBefore = text.substring(lastEnd, matcher.start());
                builder.append(Component.text(textBefore));
            }
            
            // Get player's ping
            int ping = player.getPing();
            
            // Determine color based on ping
            NamedTextColor color;
            if (ping < 100) {
                color = NamedTextColor.GREEN; // Good
            } else if (ping < 300) {
                color = NamedTextColor.GOLD; // Medium
            } else {
                color = NamedTextColor.RED; // Bad
            }
            
            // Get ping format from config
            String pingFormat = nonchatPlugin.getConfig().getString("interactive-placeholders.ping-format", "{ping}ms");
            
            // Replace {ping} placeholder with actual ping value
            String formattedPing = pingFormat.replace("{ping}", String.valueOf(ping));
            
            // Create ping component with appropriate color
            Component pingComponent = Component.text(formattedPing).color(color);
            builder.append(pingComponent);
            
            lastEnd = matcher.end();
            foundPing = true;
        }
        
        // Add remaining text
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            builder.append(Component.text(remainingText));
        }
        
        return foundPing ? builder.build() : Component.text(text);
    }
}
