package com.nonxedy.nonchat.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.event.NonchatPrivateMessageEvent;
import com.nonxedy.nonchat.command.impl.IgnoreCommand;
import com.nonxedy.nonchat.command.impl.SpyCommand;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.chat.formatting.PrivateMessageUtil;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import net.kyori.adventure.text.Component;

public class MessageManager {

    private final Nonchat plugin;
    private final PluginConfig config;
    private final PluginMessages messages;
    private final SpyCommand spyCommand;
    private final Map<UUID, UUID> lastMessageSender = new ConcurrentHashMap<>();
    private volatile IgnoreCommand ignoreCommand;

    public MessageManager(Nonchat plugin, PluginConfig config, PluginMessages messages, SpyCommand spyCommand) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.spyCommand = spyCommand;
    }

    public Map<UUID, UUID> getLastMessageSender() {
        return lastMessageSender;
    }

    /**
     * Updates the reply target for one player.
     *
     * @param player player whose /reply target should be updated
     * @param target player that /reply should message
     */
    public void setReplyTarget(Player player, Player target) {
        if (player == null || target == null) {
            return;
        }

        lastMessageSender.put(player.getUniqueId(), target.getUniqueId());
    }

    /**
     * Updates both sides of a private conversation so /reply works for the
     * sender and the receiver after either /msg or /reply is used.
     *
     * @param sender message sender
     * @param receiver message receiver
     */
    public void updateReplyTargets(Player sender, Player receiver) {
        setReplyTarget(sender, receiver);
        setReplyTarget(receiver, sender);
    }

    public void sendPrivateMessage(Player sender, Player receiver, String message) {
        sendPrivateMessage(sender, receiver, message, false);
    }

    /**
     * Sends a private message, firing {@link NonchatPrivateMessageEvent} so other
     * plugins can inspect, edit, or cancel it.
     *
     * @param sender   message sender (player or console)
     * @param receiver message recipient
     * @param message  raw message text
     * @param reply    {@code true} when this comes from {@code /reply}
     */
    public void sendPrivateMessage(CommandSender sender, Player receiver, String message, boolean reply) {
        // Check if receiver is online and available to receive the message
        if (receiver == null || !receiver.isOnline()) {
            // Only show notification if enabled in config
            if (config.isUndeliveredMessageNotificationEnabled()) {
                MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("message-not-delivered")));
            }
            return;
        }

        Player playerSender = sender instanceof Player player ? player : null;

        if (playerSender != null) {
            // Re-check the world scope at delivery time. This also protects /reply
            // when either player has moved to a different world since the last PM
            if (!config.canPrivateMessage(playerSender, receiver)) {
                MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-not-found")));
                return;
            }

            if (ignoreCommand != null && ignoreCommand.isIgnoring(receiver, playerSender)) {
                MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("ignored-by-target")));
                return;
            }

            if (ignoreCommand != null && ignoreCommand.isIgnoring(playerSender, receiver)) {
                MessageUtil.send(sender, ColorUtil.parseComponent(messages.getString("you-are-ignoring-player")
                        .replace("{player}", receiver.getName())));
                return;
            }
        }

        NonchatPrivateMessageEvent event = new NonchatPrivateMessageEvent(sender, receiver, message, reply);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        receiver = event.getReceiver();
        message = event.getMessage();

        if (receiver == null || !receiver.isOnline()) {
            if (config.isUndeliveredMessageNotificationEnabled()) {
                MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("message-not-delivered")));
            }
            return;
        }

        // Process message with color permission for sender
        String processedMessage = sender.hasPermission("nonchat.color") ? message : ColorUtil.stripAllColors(message);

        // Create and send enhanced formatted messages using new utility
        Component senderMessage = PrivateMessageUtil.createSenderMessage(config, playerSender, receiver, processedMessage);
        Component receiverMessage = PrivateMessageUtil.createReceiverMessage(config, playerSender, receiver, processedMessage);

        MessageUtil.send(sender, senderMessage);
        MessageUtil.send(receiver, receiverMessage);

        if (playerSender != null) {
            spyCommand.onPrivateMessage(playerSender, receiver, Component.text(processedMessage));
            updateReplyTargets(playerSender, receiver);
        }
    }

    public void replyToLastMessage(Player sender, String message) {
        UUID lastSenderUUID = getLastMessageSender().get(sender.getUniqueId());
        if (lastSenderUUID == null) {
            plugin.logError("No last message sender found for player " + sender.getName());
            MessageUtil.send(sender, ColorUtil.parseComponent(messages.getString("no-reply-target")));
            return;
        }

        Player receiver = Bukkit.getPlayer(lastSenderUUID);
        if (receiver == null || !receiver.isOnline()) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-offline")));
            return;
        }

        sendPrivateMessage(sender, receiver, message, true);
    }

    public Player getLastMessageSender(Player player) {
        UUID lastSenderUUID = lastMessageSender.get(player.getUniqueId());
        return lastSenderUUID != null ? Bukkit.getPlayer(lastSenderUUID) : null;
    }

    /**
     * Checks whether a player is in the sender's configured private-message scope.
     *
     * @param sender private-message sender
     * @param receiver candidate receiver
     * @return true when private messaging is permitted between their worlds
     */
    public boolean canPrivateMessage(Player sender, Player receiver) {
        return config.canPrivateMessage(sender, receiver);
    }

    public void clearLastMessageSender(Player player) {
        lastMessageSender.remove(player.getUniqueId());
    }

    /**
     * Sets the ignore command instance.
     *
     * @param ignoreCommand The ignore command instance
     */
    public void setIgnoreCommand(IgnoreCommand ignoreCommand) {
        this.ignoreCommand = ignoreCommand;
    }
}
