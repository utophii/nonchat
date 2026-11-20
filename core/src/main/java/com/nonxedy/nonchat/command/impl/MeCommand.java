package com.nonxedy.nonchat.command.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Handles roleplay action command functionality
 * Broadcasts player actions in third person
 */
public class MeCommand implements CommandExecutor, TabCompleter {

    private final Nonchat plugin;
    private final PluginConfig config;
    private final PluginMessages messages;

    // Constructor to initialize all required dependencies
    public MeCommand(Nonchat plugin, PluginConfig config, PluginMessages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    /**
     * Handles me command execution
     * @param sender Command sender
     * @param command Command being executed
     * @param label Command label used
     * @param args Command arguments
     * @return true if command handled successfully
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, String[] args) {
        plugin.logCommand(command.getName(), args);

        if (!validateCommandUsage(sender)) {
            return true;
        }

        try {
            broadcastMeMessage(sender, args);
        } catch (Exception e) {
            handleError(sender, e);
        }

        return true;
    }

    /**
     * Validates command usage permissions
     * @param sender Command sender
     * @return true if usage allowed
     */
    private boolean validateCommandUsage(CommandSender sender) {
        if (!config.isMeCommandEnabled()) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("command-disabled")));
            plugin.logError("Me command is disabled");
            return false;
        }

        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-only")));
            plugin.logError("Me command used by non-player");
            return false;
        }

        if (!sender.hasPermission("nonchat.me")) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            plugin.logError("No permission for me command: " + sender.getName());
            return false;
        }

        return true;
    }

    /**
     * Broadcasts roleplay action message
     * @param sender Command sender
     * @param args Command arguments
     */
    private void broadcastMeMessage(CommandSender sender, String[] args) {
        if (args.length == 0) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("invalid-usage-me")));
            plugin.logError("Empty me command message from: " + sender.getName());
            return;
        }

        String message = String.join(" ", args);
        String format = config.getMeFormat();

        // Parse placeholders ONLY on the format (admin-controlled), NEVER on user message
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                format = PlaceholderAPI.setPlaceholders((Player) sender, format);
            } catch (Exception e) {
                plugin.logError("Failed to process PlaceholderAPI placeholders for me command: " + e.getMessage());
            }
        }

        String formattedMessage = format.replace("{message}", "<nonchat_message>");

        plugin.getServer().broadcast(ColorUtil.parseComponent(formattedMessage,
                Placeholder.component("nonchat_message", ColorUtil.parseComponent(message, (Player) sender))));
        plugin.logResponse("Me command executed: " + sender.getName() + " - " + message);
    }

    /**
     * Handles command execution errors
     * @param sender Command sender
     * @param e Exception that occurred
     */
    private void handleError(CommandSender sender, Exception e) {
        MessageUtil.send(sender, ColorUtil.parseComponentCached("&cAn error occurred while executing the command"));
        plugin.logError("Me command error: " + e.getMessage());
    }

    /**
     * Provides tab completion suggestions
     * @param sender Command sender
     * @param command Command being completed
     * @param label Command label used
     * @param args Current arguments
     * @return List of suggestions
     */
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                        @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("nonchat.me")) {
            return Collections.emptyList();
        }
    
        if (args.length == 1) {
            List<String> suggestions = Arrays.asList(
                "is jumping"
            );
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
    
        return Collections.emptyList();
    }
}
