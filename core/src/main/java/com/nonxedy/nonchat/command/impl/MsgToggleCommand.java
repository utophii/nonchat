package com.nonxedy.nonchat.command.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import com.nonxedy.nonchat.storage.MsgToggleStorage;
import com.nonxedy.nonchat.storage.SnapshotStorage;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

/**
 * Lets players turn their own private messages off and on.
 * Staff with {@code nonchat.msgtoggle.others} can toggle other players.
 */
public class MsgToggleCommand implements CommandExecutor, TabCompleter {

    private final Nonchat plugin;
    private final PluginMessages messages;
    // Players who have private messages turned off
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private final SnapshotStorage<Set<UUID>> storage;

    public MsgToggleCommand(Nonchat plugin, PluginMessages messages) {
        this(plugin, messages, new MsgToggleStorage(plugin));
    }

    public MsgToggleCommand(Nonchat plugin, PluginMessages messages, SnapshotStorage<Set<UUID>> storage) {
        this.plugin = plugin;
        this.messages = messages;
        this.storage = storage;
        this.disabledPlayers.addAll(storage.load());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        plugin.logCommand(command.getName(), args);

        if (!plugin.getConfigService().getConfig().isPrivateChatEnabled()) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("command-disabled")));
            return true;
        }

        if (args.length > 1) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("invalid-usage-msgtoggle")));
            return true;
        }

        // Toggle another player
        if (args.length == 1) {
            if (!sender.hasPermission("nonchat.msgtoggle.others")) {
                MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("no-permission")));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-not-found")));
                return true;
            }

            boolean enabled = toggle(target);
            if (!target.equals(sender)) {
                MessageUtil.send(sender, ColorUtil.parseComponent(messages.getString(enabled
                        ? "msgtoggle-enabled-other" : "msgtoggle-disabled-other")
                        .replace("{player}", target.getName())));
            }
            MessageUtil.send(target, ColorUtil.parseComponentCached(messages.getString(enabled
                    ? "msgtoggle-enabled" : "msgtoggle-disabled")));
            plugin.logResponse("Private messages " + (enabled ? "enabled" : "disabled")
                    + " for " + target.getName() + " by " + sender.getName());
            return true;
        }

        // Toggle yourself
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-only")));
            return true;
        }

        if (!player.hasPermission("nonchat.msgtoggle")) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            return true;
        }

        boolean enabled = toggle(player);
        MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString(enabled
                ? "msgtoggle-enabled" : "msgtoggle-disabled")));
        plugin.logResponse("Private messages " + (enabled ? "enabled" : "disabled") + " for " + player.getName());
        return true;
    }

    /**
     * Flips the private message setting of a player and saves it.
     * @param player Player to toggle
     * @return true if private messages are now enabled
     */
    public synchronized boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        boolean enabled = disabledPlayers.remove(uuid);
        if (!enabled) {
            disabledPlayers.add(uuid);
        }
        storage.save(new HashSet<>(disabledPlayers));
        return enabled;
    }

    /**
     * Checks if a player accepts private messages
     * @param player Player to check
     * @return true if the player has not turned private messages off
     */
    public boolean isEnabled(Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && plugin.getConfigService().getConfig().isPrivateChatEnabled()
                && sender.hasPermission("nonchat.msgtoggle.others")) {
            String partialName = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partialName))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
