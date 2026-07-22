package com.nonxedy.nonchat.chat.channel;

import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.Channel;
import com.nonxedy.nonchat.util.chat.filters.LinkDetector;
import com.nonxedy.nonchat.util.chat.formatting.HoverTextUtil;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.items.detection.ItemDetector;
import com.nonxedy.nonchat.util.items.display.ItemDisplayUtil;
import com.nonxedy.nonchat.util.special.ping.PingDetector;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Base implementation of the Channel interface with common functionality.
 */
public class BaseChannel implements Channel {
    private final String id;
    private final String displayName;
    private final String format;
    private final String prefix;
    private final String sendPermission;
    private final String receivePermission;
    private final int radius;
    private final String world;
    private final int cooldown;
    private final int minLength;
    private final int maxLength;
    private final HoverTextUtil hoverTextUtil;
    private boolean enabled;
    private final String switchMessage;
    /**
     * Creates a new BaseChannel with all properties.
     */
    public BaseChannel(String id, String displayName, String format, String prefix,
                       String sendPermission, String receivePermission, int radius,
                       boolean enabled, HoverTextUtil hoverTextUtil, int cooldown,
                       int minLength, int maxLength) {
        this(id, displayName, format, prefix, sendPermission, receivePermission, radius,
                "", enabled, hoverTextUtil, cooldown, minLength, maxLength, "");
    }
    
    /**
     * Creates a new BaseChannel with all properties including world.
     */
    public BaseChannel(String id, String displayName, String format, String prefix,
                       String sendPermission, String receivePermission, int radius,
                       String world, boolean enabled, HoverTextUtil hoverTextUtil, int cooldown,
                       int minLength, int maxLength, String switchMessage) {
        this.id = id;
        this.displayName = displayName;
        this.format = format;
        
        // Initialize prefix with empty string if null
        String validatedPrefix = prefix != null ? prefix : "";
        
        // Validate prefix: no whitespace
        if (validatedPrefix.contains(" ")) {
            Bukkit.getLogger().warning("Channel '" + id + "' has invalid prefix with whitespace. Prefix will be set to empty.");
            validatedPrefix = "";
        }
        
        // Validate prefix: max 10 characters
        if (validatedPrefix.length() > 10) {
            Bukkit.getLogger().warning("Channel '" + id + "' prefix exceeds 10 characters. Prefix will be truncated.");
            validatedPrefix = validatedPrefix.substring(0, 10);
        }
        
        this.prefix = validatedPrefix;
        this.sendPermission = sendPermission;
        this.receivePermission = receivePermission;
        this.radius = radius;
        this.world = world;
        this.enabled = enabled;
        this.hoverTextUtil = hoverTextUtil;
        this.cooldown = cooldown;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.switchMessage = switchMessage != null ? switchMessage : "";

    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
    
    @Override
    public boolean hasPrefix() {
        return prefix != null && !prefix.isEmpty();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getSendPermission() {
        return sendPermission;
    }

    @Override
    public String getReceivePermission() {
        return receivePermission;
    }

    @Override
    public int getRadius() {
        return radius;
    }

    @Override
    public boolean isGlobal() {
        return radius == -1; // Only -1 is global, -2 is world-specific
    }

    @Override
    public String getWorld() {
        return world;
    }

    @Override
    public boolean isWorldSpecific() {
        return !world.isEmpty();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public int getCooldown() {
        return cooldown;
    }
    
    @Override
    public int getMinLength() {
        return minLength;
    }
    
    @Override
    public int getMaxLength() {
        return maxLength;
    }

    @Override
    public String getSwitchMessage() {
        return switchMessage;
    }

    @Override
    public boolean hasSwitchMessage() {
        return switchMessage != null && !switchMessage.isEmpty();
    }

    @Override
    public boolean canSend(Player player) {
        return sendPermission == null || sendPermission.isEmpty() || 
               player.hasPermission(sendPermission);
    }

    @Override
    public boolean canReceive(Player player) {
        return receivePermission == null || receivePermission.isEmpty() || 
               player.hasPermission(receivePermission);
    }

    @Override
    public boolean isInRange(Player sender, Player recipient) {
        if (isGlobal()) {
            return true;
        }
        
        // Check if this is a world-specific channel
        if (isWorldSpecific()) {
            // For world-specific channels, check if both players are in the specified world
            return sender.getWorld().getName().equals(world) && 
                   recipient.getWorld().getName().equals(world);
        }
        
        // For numeric radius, make sure they're in the same world
        if (sender.getWorld() != recipient.getWorld()) {
            return false;
        }
        
        // Check distance
        return sender.getLocation().distance(recipient.getLocation()) <= radius;
    }

    @Override
    public Component formatMessage(Player player, String message) {
        String baseFormat = getFormat();

        // Apply PlaceholderAPI to the entire format first
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                baseFormat = PlaceholderAPI.setPlaceholders(player, baseFormat);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Error processing format placeholders: {0}", e.getMessage());
            }
        }

        // Check if the format contains MiniMessage gradients that might span across {message}
        if (containsSpanningGradient(baseFormat)) {
            return formatMessageWithSpanningGradient(player, message, baseFormat);
        }

        // Split format into parts around {message} for traditional processing
        String[] formatParts = baseFormat.split("\\{message\\}");
        String beforeMessage = formatParts[0];
        String afterMessage = formatParts.length > 1 ? formatParts[1] : "";

        // Extract color from the end of beforeMessage to apply to the message
        String inheritedColor = extractTrailingColor(beforeMessage);

        // Parse format parts with colors and add hover functionality only to the player name
        Component beforeMessageComponent = parseBeforeMessageWithHover(beforeMessage, player);
        Component afterMessageComponent = ColorUtil.parseConfigComponent(afterMessage);

        // Add hover functionality to the format parts (only to player name in beforeMessage)
        afterMessageComponent = hoverTextUtil.addHoverToComponent(afterMessageComponent, player);

        Component finalMessage = beforeMessageComponent
            .append(processMessageContent(player, message, inheritedColor))
            .append(afterMessageComponent);

        return finalMessage;
    }

    /**
     * Checks if the format contains a MiniMessage gradient that spans across {message}
     * @param format The format string to check
     * @return true if contains spanning gradient, false otherwise
     */
    private boolean containsSpanningGradient(String format) {
        if (format == null || format.isEmpty()) {
            return false;
        }
        
        // Look for gradient tags that might span across {message}
        Pattern gradientPattern = Pattern.compile("<gradient:[^>]+>");
        Matcher matcher = gradientPattern.matcher(format);
        
        while (matcher.find()) {
            int gradientStart = matcher.start();
            int messageIndex = format.indexOf("{message}");
            
            if (messageIndex > gradientStart) {
                // Check if there's a closing gradient tag after {message}
                String afterMessage = format.substring(messageIndex + 9); // 9 = length of "{message}"
                if (afterMessage.contains("</gradient>")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Formats a message when the format contains a spanning gradient
     * @param player The player sending the message
     * @param message The message content
     * @param format The format string with spanning gradient
     * @return Formatted component
     */
    private Component formatMessageWithSpanningGradient(Player player, String message, String format) {
        // Process the message content first to handle color permissions
        String processedMessage = message;

        // Check color permission for the message content
        if (!player.hasPermission("nonchat.color")) {
            processedMessage = ColorUtil.stripAllColors(message);
        }

        // Replace {message} with the processed message content
        String fullFormat = format.replace("{message}", processedMessage);

        // Parse the full format with hover only on player name
        return parseFullFormatWithHover(fullFormat, player);
    }

    /**
     * Extracts the trailing color code from a format string
     * @param formatPart The format string to extract color from
     * @return The color code to inherit, or empty string if none found
     */
    private String extractTrailingColor(String formatPart) {
        if (formatPart == null || formatPart.isEmpty()) {
            return "";
        }
        
        // Look for color codes at the end of the format
        // Check for hex colors first (§#RRGGBB or &#RRGGBB)
        Pattern hexPattern = Pattern.compile(".*(§#[A-Fa-f0-9]{6}|&#[A-Fa-f0-9]{6})(?:[^§&]*?)$");
        Matcher hexMatcher = hexPattern.matcher(formatPart);
        if (hexMatcher.find()) {
            return hexMatcher.group(1);
        }
        
        // Check for BungeeCord hex format (§x§R§G§B§R§G§B or &x&R&G&B&R&G&B)
        Pattern bungeePattern = Pattern.compile(".*(?i)(§x(?:§[0-9a-fA-F]){6}|&x(?:&[0-9a-fA-F]){6})(?:[^§&]*?)$");
        Matcher bungeeMatcher = bungeePattern.matcher(formatPart);
        if (bungeeMatcher.find()) {
            return bungeeMatcher.group(1);
        }
        
        // Check for legacy colors (§[0-9a-fklmnor] or &[0-9a-fklmnor])
        Pattern legacyPattern = Pattern.compile(".*(§[0-9a-fklmnor]|&[0-9a-fklmnor])(?:[^§&]*?)$");
        Matcher legacyMatcher = legacyPattern.matcher(formatPart);
        if (legacyMatcher.find()) {
            return legacyMatcher.group(1);
        }
        
        // Check for MiniMessage color tags at the end
        Pattern miniPattern = Pattern.compile(".*(<#[A-Fa-f0-9]{6}>|<(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>)(?:[^<]*?)$");
        Matcher miniMatcher = miniPattern.matcher(formatPart);
        if (miniMatcher.find()) {
            return miniMatcher.group(1);
        }
        
        // Check for MiniMessage gradient tags that might affect the message
        Pattern gradientPattern = Pattern.compile(".*(<gradient:[^>]+>)(?:[^<]*?)$");
        Matcher gradientMatcher = gradientPattern.matcher(formatPart);
        if (gradientMatcher.find()) {
            return gradientMatcher.group(1);
        }
        
        return "";
    }

    private Component processMessageContent(Player player, String message, String inheritedColor) {
        // If message has color codes (like mention colors), use legacy processing to preserve colors
        if (ColorUtil.hasColorCodes(message)) {
            return processLegacyMessageContent(player, message, inheritedColor);
        }

        // Check if interactive placeholders are globally disabled
        Plugin plugin = Bukkit.getPluginManager().getPlugin("nonchat");
        if (plugin instanceof Nonchat nonchatPlugin) {
            boolean globalEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.enabled", true);

            if (!globalEnabled) {
                return processMessageWithColorPermission(player, message, inheritedColor);
            }

            // Use the new InteractivePlaceholderManager
            if (nonchatPlugin.getPlaceholderManager() != null) {
                // Always apply inherited color from the format
                String processedMessage = message;
                if (!inheritedColor.isEmpty()) {
                    processedMessage = inheritedColor + message;
                }

                // Handle color permissions - only strip player's own colors, keep format colors
                if (!player.hasPermission("nonchat.color")) {
                    // Strip colors from the player's message content, but preserve the inherited color
                    if (!inheritedColor.isEmpty() && processedMessage.startsWith(inheritedColor)) {
                        String playerMessagePart = processedMessage.substring(inheritedColor.length());
                        String strippedPlayerPart = ColorUtil.stripAllColors(playerMessagePart);
                        processedMessage = inheritedColor + strippedPlayerPart;
                    } else {
                        processedMessage = ColorUtil.stripAllColors(processedMessage);
                    }
                }

                // Process interactive placeholders
                return nonchatPlugin.getPlaceholderManager().processMessage(player, processedMessage);
            }
        }

        // Fallback to legacy processing
        return processLegacyMessageContent(player, message, inheritedColor);
    }

    /**
     * Legacy method for processing messages with old placeholder system
     */
    private Component processLegacyMessageContent(Player player, String message, String inheritedColor) {
        boolean hasItem = message.toLowerCase().contains("[item]");
        boolean hasPing = message.toLowerCase().contains("[ping]");

        if (hasItem && hasPing) {
            return processBothPlaceholders(player, message, inheritedColor);
        } else if (hasItem) {
            return processItemPlaceholderWithColorPermission(player, message, inheritedColor);
        } else if (hasPing) {
            return processPingPlaceholderWithColorPermission(player, message, inheritedColor);
        } else {
            return processMessageWithColorPermission(player, message, inheritedColor);
        }
    }

    /**
     * Processes message content with color permission check
     */
    private Component processMessageWithColorPermission(Player player, String message, String inheritedColor) {
        // Always apply inherited color from the format
        String processedMessage = message;
        if (!inheritedColor.isEmpty()) {
            processedMessage = inheritedColor + message;
        }

        // Handle color permissions - only strip player's own colors, keep format colors
        if (!player.hasPermission("nonchat.color")) {
            // Strip colors from the player's message content, but preserve the inherited color
            if (!inheritedColor.isEmpty() && processedMessage.startsWith(inheritedColor)) {
                String playerMessagePart = processedMessage.substring(inheritedColor.length());
                String strippedPlayerPart = ColorUtil.stripAllColors(playerMessagePart);
                processedMessage = inheritedColor + strippedPlayerPart;
            } else {
                processedMessage = ColorUtil.stripAllColors(processedMessage);
            }
        }

        return LinkDetector.makeLinksClickable(processedMessage);
    }

    /**
     * Processes item placeholder with color permission check
     */
    private Component processItemPlaceholderWithColorPermission(Player player, String message, String inheritedColor) {
        // Check if item placeholders are enabled
        Plugin plugin = Bukkit.getPluginManager().getPlugin("nonchat");
        boolean itemEnabled = true;

        if (plugin instanceof Nonchat nonchatPlugin) {
            itemEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.item-enabled", true);
        }

        if (!itemEnabled) {
            return processMessageWithColorPermission(player, message, inheritedColor);
        }

        // Always apply inherited color from the format
        String processedMessage = message;
        if (!inheritedColor.isEmpty()) {
            processedMessage = inheritedColor + message;
        }

        // Handle color permissions - only strip player's own colors, keep format colors
        if (!player.hasPermission("nonchat.color")) {
            // Strip colors from the player's message content, but preserve the inherited color
            if (!inheritedColor.isEmpty() && processedMessage.startsWith(inheritedColor)) {
                String playerMessagePart = processedMessage.substring(inheritedColor.length());
                String strippedPlayerPart = ColorUtil.stripAllColors(playerMessagePart);
                processedMessage = inheritedColor + strippedPlayerPart;
            } else {
                processedMessage = ColorUtil.stripAllColors(processedMessage);
            }
        }

        return ItemDetector.processItemPlaceholders(player, processedMessage);
    }

    /**
     * Processes ping placeholder with color permission check
     */
    private Component processPingPlaceholderWithColorPermission(Player player, String message, String inheritedColor) {
        // Check if ping placeholders are enabled
        Plugin plugin = Bukkit.getPluginManager().getPlugin("nonchat");
        boolean pingEnabled = true;

        if (plugin instanceof Nonchat nonchatPlugin) {
            pingEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.ping-enabled", true);
        }

        if (!pingEnabled) {
            return processMessageWithColorPermission(player, message, inheritedColor);
        }

        // Always apply inherited color from the format
        String processedMessage = message;
        if (!inheritedColor.isEmpty()) {
            processedMessage = inheritedColor + message;
        }

        // Handle color permissions - only strip player's own colors, keep format colors
        if (!player.hasPermission("nonchat.color")) {
            // Strip colors from the player's message content, but preserve the inherited color
            if (!inheritedColor.isEmpty() && processedMessage.startsWith(inheritedColor)) {
                String playerMessagePart = processedMessage.substring(inheritedColor.length());
                String strippedPlayerPart = ColorUtil.stripAllColors(playerMessagePart);
                processedMessage = inheritedColor + strippedPlayerPart;
            } else {
                processedMessage = ColorUtil.stripAllColors(processedMessage);
            }
        }

        return PingDetector.processPingPlaceholders(player, processedMessage);
    }

    /**
     * Processes both item and ping placeholders with color permission check
     */
    private Component processBothPlaceholders(Player player, String message, String inheritedColor) {
        // Check settings
        Plugin plugin = Bukkit.getPluginManager().getPlugin("nonchat");
        boolean itemEnabled = true;
        boolean pingEnabled = true;

        if (plugin instanceof Nonchat nonchatPlugin) {
            itemEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.item-enabled", true);
            pingEnabled = nonchatPlugin.getConfig().getBoolean("interactive-placeholders.ping-enabled", true);
        }

        // Always apply inherited color from the format
        String processedMessage = message;
        if (!inheritedColor.isEmpty()) {
            processedMessage = inheritedColor + message;
        }

        // Handle color permissions - only strip player's own colors, keep format colors
        boolean hasColorPermission = player.hasPermission("nonchat.color");
        if (!hasColorPermission) {
            // Strip colors from the player's message content, but preserve the inherited color
            if (!inheritedColor.isEmpty() && processedMessage.startsWith(inheritedColor)) {
                String playerMessagePart = processedMessage.substring(inheritedColor.length());
                String strippedPlayerPart = ColorUtil.stripAllColors(playerMessagePart);
                processedMessage = inheritedColor + strippedPlayerPart;
            } else {
                processedMessage = ColorUtil.stripAllColors(processedMessage);
            }
        }

        // Use ComponentBuilder<?, ?> to avoid TextComponent.Builder.build() return type incompatibility across Adventure versions
        ComponentBuilder<?, ?> builder = Component.text();

        // Split by [item] and [ping] and process each part
        String[] parts = processedMessage.split("(?i)\\[(item|ping)\\]");
        Pattern pattern = Pattern.compile("(?i)\\[(item|ping)\\]");
        Matcher matcher = pattern.matcher(processedMessage);

        int partIndex = 0;

        while (matcher.find()) {
            // Add text before placeholder
            if (partIndex < parts.length && !parts[partIndex].isEmpty()) {
                String textPart = parts[partIndex];
                builder.append(LinkDetector.makeLinksClickable(textPart));
            }
            partIndex++;

            String placeholder = matcher.group().toLowerCase();
            if (placeholder.equals("[item]") && itemEnabled) {
                // Process item using the new bracketed method with client-side localization
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                Component itemComponent = ItemDisplayUtil.createBracketedItemComponent(heldItem);
                builder.append(itemComponent);
            } else if (placeholder.equals("[ping]") && pingEnabled) {
                // Process ping
                int ping = player.getPing();
                NamedTextColor color;
                if (ping < 100) {
                    color = NamedTextColor.GREEN;
                } else if (ping < 300) {
                    color = NamedTextColor.GOLD;
                } else {
                    color = NamedTextColor.RED;
                }
                builder.append(Component.text(ping + "ms").color(color));
            } else {
                // If placeholder is disabled, add it as plain text
                String placeholderText = matcher.group();
                builder.append(Component.text(placeholderText));
            }
        }

        // Add remaining text
        if (partIndex < parts.length && !parts[partIndex].isEmpty()) {
            String textPart = parts[partIndex];
            builder.append(LinkDetector.makeLinksClickable(textPart));
        }

        return builder.build();
    }
    
    /**
     * Parses the beforeMessage part and adds hover only to the player name
     * @param beforeMessage The part of the format before {message}
     * @param player The player to get hover information for
     * @return Component with hover only on the player name
     */
    private Component parseBeforeMessageWithHover(String beforeMessage, Player player) {
        String playerName = player.getName();
        int nameIndex = findVisibleTextIndex(beforeMessage, playerName);

        if (nameIndex == -1) {
            // If player name not found, add hover to the entire beforeMessage
            return hoverTextUtil.addHoverToComponent(ColorUtil.parseConfigComponent(beforeMessage), player);
        }

        String beforeName = beforeMessage.substring(0, nameIndex);
        String afterName = beforeMessage.substring(nameIndex + playerName.length());

        // Extract trailing color from beforeName to apply to the player name
        String trailingColor = extractTrailingColor(beforeName);

        Component nameComponent;
        if (!trailingColor.isEmpty()) {
            // Remove the trailing color from beforeName if it's at the end
            if (beforeName.endsWith(trailingColor)) {
                beforeName = beforeName.substring(0, beforeName.length() - trailingColor.length());
            }
            // Create name component with the trailing color applied
            Component coloredNameComponent = ColorUtil.parseConfigComponent(trailingColor + playerName);
            nameComponent = hoverTextUtil.addHoverToComponent(coloredNameComponent, player);
        } else {
            nameComponent = hoverTextUtil.createHoverableText(playerName, player);
        }

        Component beforeComponent = ColorUtil.parseConfigComponent(beforeName);
        Component afterComponent = ColorUtil.parseConfigComponent(afterName);

        return beforeComponent.append(nameComponent).append(afterComponent);
    }

    /**
     * Parses the full format and adds hover only to the player name
     * @param fullFormat The full format string
     * @param player The player to get hover information for
     * @return Component with hover only on the player name
     */
    private Component parseFullFormatWithHover(String fullFormat, Player player) {
        String playerName = player.getName();
        int nameIndex = findVisibleTextIndex(fullFormat, playerName);

        if (nameIndex == -1) {
            // If player name not found, add hover to the entire format
            return hoverTextUtil.addHoverToComponent(ColorUtil.parseConfigComponent(fullFormat), player);
        }

        String beforeName = fullFormat.substring(0, nameIndex);
        String afterName = fullFormat.substring(nameIndex + playerName.length());

        // Extract trailing color from beforeName to apply to the player name
        String trailingColor = extractTrailingColor(beforeName);

        Component nameComponent;
        if (!trailingColor.isEmpty()) {
            // Remove the trailing color from beforeName if it's at the end
            if (beforeName.endsWith(trailingColor)) {
                beforeName = beforeName.substring(0, beforeName.length() - trailingColor.length());
            }
            // Create name component with the trailing color applied
            Component coloredNameComponent = ColorUtil.parseConfigComponent(trailingColor + playerName);
            nameComponent = hoverTextUtil.addHoverToComponent(coloredNameComponent, player);
        } else {
            nameComponent = hoverTextUtil.createHoverableText(playerName, player);
        }

        Component beforeComponent = ColorUtil.parseConfigComponent(beforeName);
        Component afterComponent = ColorUtil.parseConfigComponent(afterName);

        return beforeComponent.append(nameComponent).append(afterComponent);
    }

    /**
     * Finds text only in visible content, skipping MiniMessage tags like
     * <head:PlayerName> so formatting tags are not split in half.
     *
     * @param text The text to scan
     * @param target The visible text to find
     * @return Index of the first visible occurrence, or -1 if not found
     */
    private int findVisibleTextIndex(String text, String target) {
        if (text == null || text.isEmpty() || target == null || target.isEmpty()) {
            return -1;
        }

        boolean insideTag = false;

        for (int i = 0; i <= text.length() - target.length(); i++) {
            char current = text.charAt(i);

            if (current == '<') {
                insideTag = true;
            }

            if (!insideTag && text.regionMatches(i, target, 0, target.length())) {
                return i;
            }

            if (current == '>') {
                insideTag = false;
            }
        }

        return -1;
    }
}
