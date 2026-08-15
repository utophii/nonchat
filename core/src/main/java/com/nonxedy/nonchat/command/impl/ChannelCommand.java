package com.nonxedy.nonchat.command.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.Channel;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.core.ChatManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

/**
 * Command for managing chat channels.
 * Allows players to switch between channels, list available channels,
 * and get information about channels.
 */
public class ChannelCommand implements CommandExecutor, TabCompleter {
    
    private final ChatManager chatManager;
    private final PluginMessages messages;
    private final Nonchat plugin;
    
    public ChannelCommand(Nonchat plugin, ChatManager chatManager, PluginMessages messages) {
        this.plugin = plugin;
        this.chatManager = chatManager;
        this.messages = messages;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                         @NotNull String label, @NotNull String[] args) {
        plugin.logCommand(command.getName(), args);
        
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-only")));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Show help
            sendHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "set" -> {
                return handleSetChannel(player, args);
            }
            case "list" -> {
                return handleListChannels(player);
            }
            case "info" -> {
                return handleChannelInfo(player, args);
            }
            case "create" -> {
                return handleCreateChannel(player, args);
            }
            case "delete" -> {
                return handleDeleteChannel(player, args);
            }
            case "edit" -> {
                return handleEditChannel(player, args);
            }
            case "default" -> {
                return handleSetDefaultChannel(player, args);
            }
            default -> {
                sendHelp(player);
                return true;
            }
        }
    }
    
    private boolean handleSetChannel(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-set-usage")));
            return true;
        }
        
        String channelId = args[1].toLowerCase();
        Channel channel = chatManager.getChannel(channelId);
        
        if (channel == null) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-not-found")
                    .replace("{channel}", channelId)));
            return true;
        }
        
        if (!channel.isEnabled()) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-disabled")
                    .replace("{channel}", channel.getDisplayName())));
            return true;
        }
        
        if (!channel.canSend(player)) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            return true;
        }
        
        if (chatManager.setPlayerChannel(player, channelId)) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-set")
                    .replace("{channel}", channel.getDisplayName())));
        } else {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-set-failed")
                    .replace("{channel}", channelId)));
        }
        
        return true;
    }
    
    private boolean handleListChannels(Player player) {
        Collection<Channel> channels = chatManager.getEnabledChannels();
        Channel currentChannel = chatManager.getPlayerChannel(player);
        
        if (channels.isEmpty()) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("no-channels")));
            return true;
        }
        
        MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-list-header")));
        
        for (Channel channel : channels) {
            String entryMessage = messages.getString("channel-list-entry")
                    .replace("{id}", channel.getId())
                    .replace("{display}", channel.getDisplayName())
                    .replace("{current}", channel.getId().equals(currentChannel.getId()) ? 
                            messages.getString("channel-current") : "");
            
            if (channel.canSend(player)) {
                MessageUtil.send(player, ColorUtil.parseComponent(entryMessage));
            } else {
                MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-list-no-permission")
                        .replace("{id}", channel.getId())
                        .replace("{display}", channel.getDisplayName())));
            }
        }
        
        return true;
    }
    
    private boolean handleChannelInfo(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-info-usage")));
            return true;
        }
        
        String channelId = args[1].toLowerCase();
        Channel channel = chatManager.getChannel(channelId);
        
        if (channel == null) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-not-found")
                    .replace("{channel}", channelId)));
            return true;
        }
        
        MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-info-header")
                .replace("{channel}", channel.getDisplayName())));
        
        MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-info-id")
                .replace("{id}", channel.getId())));
        
        MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-info-enabled")
                .replace("{enabled}", channel.isEnabled() ? 
                        messages.getString("channel-info-yes") : 
                        messages.getString("channel-info-no"))));
        
        MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-info-character")
                .replace("{character}", channel.hasPrefix() ? 
                        channel.getPrefix() : messages.getString("channel-info-none"))));
        
        String radiusDisplay;
        if (channel.isGlobal()) {
            radiusDisplay = messages.getString("channel-info-global");
        } else if (channel.isWorldSpecific()) {
            if (channel.getWorldRadii() != null && !channel.getWorldRadii().isEmpty()) {
                radiusDisplay = channel.getWorldRadii().toString();
            } else {
                radiusDisplay = String.join(", ", channel.getWorlds());
            }
        } else {
            radiusDisplay = String.valueOf(channel.getRadius());
        }

        MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-info-radius").replace("{radius}", radiusDisplay)));
        
        return true;
    }
    
    private boolean handleCreateChannel(Player player, String[] args) {
        // Check permission
        if (!player.hasPermission("nonchat.admin.channel.create")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            return true;
        }
        
        if (args.length < 3) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-create-usage")));
            return true;
        }
        
        String channelId = args[1].toLowerCase();
        String displayName = args[2];
        
        // Validate channel ID
        if (!channelId.matches("^[a-z0-9-]+$")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-invalid-id")));
            return true;
        }
        
        // Check if channel already exists
        if (chatManager.getChannel(channelId) != null) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-already-exists")
                    .replace("{channel}", channelId)));
            return true;
        }
        
        // Set default values
        String format = "§7({display})§r {prefix} §f{sender}§r {suffix}§7: §f{message}";
        format = format.replace("{display}", displayName);
        
        String prefix = "";
        String sendPermission = "";
        String receivePermission = "";
        int radius = -1; // Global by default
        List<String> worlds = new ArrayList<>();
        Map<String, Integer> worldRadii = new ConcurrentHashMap<>();
        int cooldown = 0;
        int minLength = 0;
        int maxLength = 256;
        String switchMessage = "";
        
        // Process optional parameters
        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("format:")) {
                format = arg.substring(7);
            } else if (arg.startsWith("prefix:")) {
                prefix = arg.substring(7);
            } else if (arg.startsWith("char:")) {
                // Support legacy char: parameter for backward compatibility
                prefix = arg.substring(5);
            } else if (arg.startsWith("send:")) {
                sendPermission = arg.substring(5);
            } else if (arg.startsWith("receive:")) {
                receivePermission = arg.substring(8);
            } else if (arg.startsWith("worlds:") || arg.startsWith("world:")) {
                String worldsStr = arg.substring(arg.indexOf(':') + 1);
                for (String w : worldsStr.split(",")) {
                    String trimmed = w.toLowerCase().trim();
                    if (!trimmed.isEmpty() && !worlds.contains(trimmed)) {
                        worlds.add(trimmed);
                    }
                }
            } else if (arg.startsWith("radius:")) {
                String rStr = arg.substring(7).toLowerCase().trim();
                if (rStr.equals("world") || rStr.equals("world-only")) {
                    radius = -2;
                    if (worlds.isEmpty()) {
                        worlds.add("world");
                    }
                } else if (rStr.contains(",")) {
                    radius = -2;
                    for (String w : rStr.split(",")) {
                        String trimmed = w.toLowerCase().trim();
                        if (!trimmed.isEmpty() && !worlds.contains(trimmed)) {
                            worlds.add(trimmed);
                        }
                    }
                } else {
                    try {
                        radius = Integer.parseInt(rStr);
                    } catch (NumberFormatException e) {
                        if (!rStr.isEmpty()) {
                            radius = -2;
                            if (!worlds.contains(rStr)) {
                                worlds.add(rStr);
                            }
                        } else {
                            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                            return true;
                        }
                    }
                }
            } else if (arg.startsWith("cooldown:")) {
                try {
                    cooldown = Integer.parseInt(arg.substring(9));
                } catch (NumberFormatException e) {
                    MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                    return true;
                }
            } else if (arg.startsWith("min:")) {
                try {
                    minLength = Integer.parseInt(arg.substring(4));
                } catch (NumberFormatException e) {
                    MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                    return true;
                }
            } else if (arg.startsWith("max:")) {
                try {
                    maxLength = Integer.parseInt(arg.substring(4));
                } catch (NumberFormatException e) {
                    MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                    return true;
                }
            }
        }
        
        // Validate prefix
        if (prefix != null && !prefix.isEmpty()) {
            // Check for whitespace
            if (prefix.contains(" ")) {
                MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-prefix-no-spaces")));
                return true;
            }
            
            // Check length
            if (prefix.length() > 10) {
                MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-prefix-too-long")));
                return true;
            }
            
            // Check uniqueness
            if (!chatManager.getChannelManager().isPrefixUnique(prefix, null)) {
                MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-prefix-exists")
                        .replace("{prefix}", prefix)));
                return true;
            }
        }
        
        // Create the channel using ChannelManager directly to support multi-character prefixes
        Channel channel = chatManager.getChannelManager().createChannel(
            channelId, displayName, format, prefix, 
            sendPermission, receivePermission, radius, worlds, worldRadii,
            cooldown, minLength, maxLength, switchMessage
        );
        
        
        if (channel == null) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-create-failed")
                    .replace("{channel}", channelId)));
            return true;
        }
        
        MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-created")
                .replace("{channel}", channel.getDisplayName())));
        return true;
    }
    
    private boolean handleEditChannel(Player player, String[] args) {
        // Check permission
        if (!player.hasPermission("nonchat.admin.channel.edit")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            return true;
        }
        
        if (args.length < 3) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-edit-usage")));
            return true;
        }
        
        String channelId = args[1].toLowerCase();
        Channel channel = chatManager.getChannel(channelId);
        
        if (channel == null) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-not-found")
                    .replace("{channel}", channelId)));
            return true;
        }
        
        // Process parameters to edit
        String displayName = null;
        String format = null;
        String prefix = null;
        String sendPermission = null;
        String receivePermission = null;
        Integer radius = null;
        List<String> worlds = null;
        Map<String, Integer> worldRadii = null;
        Boolean enabled = null;
        Integer cooldown = null;
        Integer minLength = null;
        Integer maxLength = null;
        String switchMessage = null;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("display:")) {
                displayName = arg.substring(8);
            } else if (arg.startsWith("format:")) {
                format = arg.substring(7);
            } else if (arg.startsWith("prefix:")) {
                prefix = arg.substring(7);
            } else if (arg.startsWith("char:")) {
                // Support legacy char: parameter for backward compatibility
                prefix = arg.substring(5);
            } else if (arg.startsWith("send:")) {
                sendPermission = arg.substring(5);
            } else if (arg.startsWith("receive:")) {
                receivePermission = arg.substring(8);
            } else if (arg.startsWith("worlds:") || arg.startsWith("world:")) {
                if (worlds == null) worlds = new ArrayList<>();
                String worldsStr = arg.substring(arg.indexOf(':') + 1);
                for (String w : worldsStr.split(",")) {
                    String trimmed = w.toLowerCase().trim();
                    if (!trimmed.isEmpty() && !worlds.contains(trimmed)) {
                        worlds.add(trimmed);
                    }
                }
            } else if (arg.startsWith("radius:")) {
                String rStr = arg.substring(7).toLowerCase().trim();
                if (rStr.equals("world") || rStr.equals("world-only")) {
                    radius = -2;
                    if (worlds == null) worlds = new ArrayList<>();
                    if (worlds.isEmpty()) {
                        worlds.add("world");
                    }
                } else if (rStr.contains(",")) {
                    radius = -2;
                    if (worlds == null) worlds = new ArrayList<>();
                    for (String w : rStr.split(",")) {
                        String trimmed = w.toLowerCase().trim();
                        if (!trimmed.isEmpty() && !worlds.contains(trimmed)) {
                            worlds.add(trimmed);
                        }
                    }
                } else {
                    try {
                        radius = Integer.valueOf(rStr);
                    } catch (NumberFormatException e) {
                        if (!rStr.isEmpty()) {
                            radius = -2;
                            if (worlds == null) worlds = new ArrayList<>();
                            if (!worlds.contains(rStr)) {
                                worlds.add(rStr);
                            }
                        } else {
                            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                            return true;
                        }
                    }
                }
            } else if (arg.startsWith("enabled:")) {
                enabled = arg.substring(8).equalsIgnoreCase("true");
            } else if (arg.startsWith("cooldown:")) {
                try {
                    cooldown = Integer.valueOf(arg.substring(9));
                } catch (NumberFormatException e) {
                    MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                    return true;
                }
            } else if (arg.startsWith("min:")) {
                try {
                    minLength = Integer.valueOf(arg.substring(4));
                } catch (NumberFormatException e) {
                    MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                    return true;
                }
            } else if (arg.startsWith("max:")) {
                try {
                    maxLength = Integer.valueOf(arg.substring(4));
                } catch (NumberFormatException e) {
                    MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("invalid-number")));
                    return true;
                }
            }
        }
        
        // Validate prefix if it's being updated
        if (prefix != null && !prefix.isEmpty()) {
            // Check for whitespace
            if (prefix.contains(" ")) {
                MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-prefix-no-spaces")));
                return true;
            }
            
            // Check length
            if (prefix.length() > 10) {
                MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-prefix-too-long")));
                return true;
            }
            
            // Check uniqueness (excluding current channel)
            if (!chatManager.getChannelManager().isPrefixUnique(prefix, channelId)) {
                MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-prefix-exists")
                        .replace("{prefix}", prefix)));
                return true;
            }
        }
        
        // Update the channel using ChannelManager directly to support multi-character prefixes
        boolean success = chatManager.getChannelManager().updateChannel(
            channelId, displayName, format, prefix,
            sendPermission, receivePermission, radius, worlds, worldRadii, enabled,
            cooldown, minLength, maxLength, switchMessage
        );
        
        if (success) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-updated")
                    .replace("{channel}", channel.getDisplayName())));
        } else {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-update-failed")
                    .replace("{channel}", channelId)));
        }
        
        return true;
    }
    
    private boolean handleDeleteChannel(Player player, String[] args) {
        // Check permission
        if (!player.hasPermission("nonchat.admin.channel.delete")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            return true;
        }
        
        if (args.length < 2) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-delete-usage")));
            return true;
        }
        
        String channelId = args[1].toLowerCase();
        Channel channel = chatManager.getChannel(channelId);
        
        if (channel == null) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-not-found")
                    .replace("{channel}", channelId)));
            return true;
        }
        
        boolean success = chatManager.deleteChannel(channelId);
        
        if (success) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-deleted")
                    .replace("{channel}", channel.getDisplayName())));
        } else {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-delete-failed")
                    .replace("{channel}", channel.getDisplayName())));
        }
        
        return true;
    }
    
    private boolean handleSetDefaultChannel(Player player, String[] args) {
        // Check permission
        if (!player.hasPermission("nonchat.admin.channel.default")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            return true;
        }
        
        if (args.length < 2) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-default-usage")));
            return true;
        }
        
        String channelId = args[1].toLowerCase();
        Channel channel = chatManager.getChannel(channelId);
        
        if (channel == null) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-not-found")
                    .replace("{channel}", channelId)));
            return true;
        }
        
        boolean success = chatManager.setDefaultChannel(channelId);
        
        if (success) {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-default-set")
                    .replace("{channel}", channel.getDisplayName())));
        } else {
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-default-failed")
                    .replace("{channel}", channel.getDisplayName())));
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-header")));
        MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-set")));
        MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-list")));
        MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-info")));
        
        // Only show admin commands if player has permission
        if (player.hasPermission("nonchat.admin.channel.create")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-create")));
        }
        if (player.hasPermission("nonchat.admin.channel.edit")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-edit")));
        }
        if (player.hasPermission("nonchat.admin.channel.delete")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-delete")));
        }
        if (player.hasPermission("nonchat.admin.channel.default")) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("channel-help-default")));
        }
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                   @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("set");
            subcommands.add("list");
            subcommands.add("info");
            
            // Only add admin commands if player has permission
            if (player.hasPermission("nonchat.admin.channel.create")) {
                subcommands.add("create");
            }
            if (player.hasPermission("nonchat.admin.channel.edit")) {
                subcommands.add("edit");
            }
            if (player.hasPermission("nonchat.admin.channel.delete")) {
                subcommands.add("delete");
            }
            if (player.hasPermission("nonchat.admin.channel.default")) {
                subcommands.add("default");
            }
            return filterStartingWith(args[0], subcommands);
        }
        
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("info"))) {
            return filterStartingWith(args[1], 
                    chatManager.getAllChannels().stream()
                        .filter(Channel::isEnabled)
                        .filter(channel -> channel.canSend(player))
                        .map(Channel::getId)
                        .collect(Collectors.toList()));
        }
        
        return Collections.emptyList();
    }
    
    private List<String> filterStartingWith(String prefix, List<String> options) {
        List<String> result = new ArrayList<>();
        
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(option);
            }
        }
        
        return result;
    }
}
