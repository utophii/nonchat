package com.nonxedy.nonchat.util.chat.formatting;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

/**
 * Simple utility for adding hover text to existing components
 * Does not process colors or placeholders - just adds hover functionality
 */
public class HoverTextUtil {
    private final List<String> hoverFormat;
    private final boolean enabled;
    private final String clickCommand;
    private final boolean usePlaceholderAPI;

    public HoverTextUtil(List<String> format, boolean enabled, String clickCommand) {
        this.hoverFormat = format;
        this.enabled = enabled;
        this.clickCommand = clickCommand;
        this.usePlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    /**
     * Adds hover text to an existing component
     * @param component The existing component to add hover to
     * @param player The player to get hover information for
     * @return Component with hover event added
     */
    public Component addHoverToComponent(Component component, Player player) {
        if (!enabled || hoverFormat == null || hoverFormat.isEmpty()) {
            return component;
        }

        String hoverText = buildHoverText(player);
        if (hoverText.isEmpty()) {
            return component;
        }

        // Parse colors in hover text using ColorUtil
        Component hoverComponent = ColorUtil.parseComponent(hoverText);
        Component result = component
            .hoverEvent(HoverEvent.showText(hoverComponent));

        String click = buildClickCommand(player);
        if (!click.isEmpty()) {
            result = result.clickEvent(ClickEvent.suggestCommand(click));
        }
        return result;
    }

    /**
     * Creates a hoverable component from plain text
     * @param text The plain text to make hoverable
     * @param player The player to get hover information for
     * @return Component with hover event and click event
     */
    public Component createHoverableText(String text, Player player) {
        if (!enabled) {
            return Component.text(text);
        }

        Component baseComponent = Component.text(text);
        String hoverText = buildHoverText(player);
        
        if (hoverText.isEmpty()) {
            return baseComponent;
        }

        // Parse colors in hover text using ColorUtil
        Component hoverComponent = ColorUtil.parseComponent(hoverText);
        Component result = baseComponent
            .hoverEvent(HoverEvent.showText(hoverComponent));

        String click = buildClickCommand(player);
        if (!click.isEmpty()) {
            result = result.clickEvent(ClickEvent.suggestCommand(click));
        }
        return result;
    }

    /**
     * Builds the click command suggested when a player clicks a name in chat.
     * Resolves PlaceholderAPI placeholders (e.g. %player_name%) when
     * PlaceholderAPI is installed, then returns the command. An empty template
     * disables the click action entirely.
     * @param player The player whose name was clicked
     * @return The click command, or an empty string to disable clicking
     */
    private String buildClickCommand(Player player) {
        if (clickCommand == null || clickCommand.isEmpty()) {
            return "";
        }

        String command = clickCommand;
        if (usePlaceholderAPI) {
            try {
                command = PlaceholderAPI.setPlaceholders(player, command);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Error processing placeholder in click command: {0}", e.getMessage());
            }
        }
        return command;
    }

    /**
     * Builds hover text for a player
     * @param player The player to build hover text for
     * @return Formatted hover text string
     */
    private String buildHoverText(Player player) {
        StringBuilder hoverBuilder = new StringBuilder();
        
        for (String line : hoverFormat) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            
            String processedLine = processHoverLine(line, player);
            if (!processedLine.trim().isEmpty()) {
                if (hoverBuilder.length() > 0) {
                    hoverBuilder.append("\n");
                }
                hoverBuilder.append(processedLine);
            }
        }
        
        return hoverBuilder.toString();
    }

    /**
     * Processes a single line of hover text
     * @param line The line to process
     * @param player The player to get information for
     * @return Processed line
     */
    private String processHoverLine(String line, Player player) {
        if (line == null) return "";
        
        // Resolve PAPI in the administrator-configured hover template before
        // adding player or integration values
        String processed = line;
        if (usePlaceholderAPI) {
            try {
                processed = PlaceholderAPI.setPlaceholders(player, processed);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Error processing placeholder in hover text: {0}", e.getMessage());
            }
        }

        // Get integration data
        String prefix = IntegrationUtil.getPlayerPrefix(player);
        String balance = IntegrationUtil.getBalance(player);
        String playtime = IntegrationUtil.getPlayTime(player);
        
        // Replace basic placeholders
        processed = processed
            .replace("{player}", player.getName())
            .replace("{level}", String.valueOf(player.getLevel()))
            .replace("{prefix}", prefix != null ? prefix : "")
            .replace("{playtime}", playtime != null ? playtime : "0h")
            .replace("{balance}", balance != null ? balance : "0");
    
        return processed;
    }

    /**
     * Checks if hover functionality is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the hover format configuration
     * @return List of hover format lines
     */
    public List<String> getHoverFormat() {
        return hoverFormat;
    }
}
