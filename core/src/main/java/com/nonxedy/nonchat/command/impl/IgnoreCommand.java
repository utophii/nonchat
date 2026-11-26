package com.nonxedy.nonchat.command.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.storage.IgnoreStorage;
import com.nonxedy.nonchat.storage.SnapshotStorage;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

public class IgnoreCommand implements CommandExecutor, TabCompleter {

    // Instance of the main plugin
    private final Nonchat plugin;
    // Plugin messages configuration
    private final PluginMessages messages;
    // Map to store ignored players (key: player UUID, value: set of ignored player UUIDs)
    private final Map<UUID, Set<UUID>> ignoredPlayers = new HashMap<>();
    private final SnapshotStorage<Map<UUID, Set<UUID>>> ignoreStorage;

    // Constructor to initialize the command
    public IgnoreCommand(Nonchat plugin, PluginMessages messages) {
        this(plugin, messages, new IgnoreStorage(plugin));
    }

    public IgnoreCommand(Nonchat plugin, PluginMessages messages, SnapshotStorage<Map<UUID, Set<UUID>>> storage) {
        this.plugin = plugin;
        this.messages = messages;
        this.ignoreStorage = storage;
        ignoreStorage.load().forEach((owner, targets) -> ignoredPlayers.put(owner, new HashSet<>(targets)));
    }

    /**
     * Handles ignore command execution
     * @param sender Command sender
     * @param command Command being executed
     * @param label Command label used
     * @param args Command arguments
     * @return true if command handled successfully
     */
    @Override
    public synchronized boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Log the executed command
        plugin.logCommand(command.getName(), args);

        // Check if sender is a player
        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-only")));
            plugin.logError("Ignore command can only be used by players");
            return true;
        }

        // Convert sender to player
        Player player = (Player) sender;

        // Check permissions for using the command
        if (!player.hasPermission("nonchat.ignore")) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            plugin.logError("No permission for ignore command");
            return true;
        }

        // Validate command usage
        if (args.length != 1) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("invalid-usage-ignore")));
            plugin.logError("Invalid arguments for ignore command");
            return true;
        }

        // Find target player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-not-found")));
            plugin.logError("Target player not found");
            return true;
        }

        // Prevent ignoring yourself
        if (target.equals(player)) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("cannot-ignore-self")));
            plugin.logError("Player tried to ignore themselves");
            return true;
        }

        // Get the set of ignored players for this player
        Set<UUID> ignored = ignoredPlayers.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());

        // Toggle ignore status
        UUID targetUUID = target.getUniqueId();
        if (ignored.contains(targetUUID)) {
            // Remove from ignore list
            ignored.remove(targetUUID);
            MessageUtil.send(sender, ColorUtil.parseComponent(messages.getString("unignored-player")
                    .replace("{player}", target.getName())));
            plugin.logResponse("Player unignored: " + target.getName());
        } else {
            // Add to ignore list
            ignored.add(targetUUID);
            MessageUtil.send(sender, ColorUtil.parseComponent(messages.getString("ignored-player")
                    .replace("{player}", target.getName())));
            plugin.logResponse("Player ignored: " + target.getName());
        }
        if (ignored.isEmpty()) {
            ignoredPlayers.remove(player.getUniqueId());
        }
        saveIgnoreLists();

        return true;
    }

    /**
     * Provides tab completion for the ignore command
     */
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partialName))
                    .filter(name -> !name.equals(sender.getName())) // Don't suggest the sender's name
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Checks if a player is ignoring another player
     * @param sender The player who might be ignoring
     * @param target The player who might be ignored
     * @return true if sender is ignoring target
     */
    public synchronized boolean isIgnoring(Player sender, Player target) {
        Set<UUID> ignored = ignoredPlayers.get(sender.getUniqueId());
        return ignored != null && ignored.contains(target.getUniqueId());
    }

    /**
     * Gets all players that a player is ignoring
     * @param player The player whose ignore list to retrieve
     * @return Set of UUIDs representing ignored players
     */
    public synchronized Set<UUID> getIgnoredPlayers(Player player) {
        Set<UUID> ignored = ignoredPlayers.get(player.getUniqueId());
        return ignored == null ? Collections.emptySet() : Set.copyOf(ignored);
    }
    
    /**
     * Checks if a player is ignoring anyone
     * @param player The player to check
     * @return true if the player is ignoring at least one other player
     */
    public synchronized boolean isIgnoringAnyone(Player player) {
        Set<UUID> ignored = ignoredPlayers.get(player.getUniqueId());
        return ignored != null && !ignored.isEmpty();
    }
    
    /**
     * Clears a player's ignore list
     * @param player The player whose ignore list to clear
     */
    public synchronized void clearIgnoreList(Player player) {
        if (ignoredPlayers.remove(player.getUniqueId()) != null) {
            saveIgnoreLists();
        }
    }

    private void saveIgnoreLists() {
        Map<UUID, Set<UUID>> snapshot = new HashMap<>();
        ignoredPlayers.forEach((owner, ignored) -> snapshot.put(owner, new HashSet<>(ignored)));
        ignoreStorage.save(snapshot);
    }
}
