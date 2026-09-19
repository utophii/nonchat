package com.nonxedy.nonchat.api.event;

import java.util.Objects;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.api.Channel;

/**
 * Called when a player is about to send a message to a nonchat channel.
 *
 * <p>Fired after built-in filters, channel access checks, and
 * {@link com.nonxedy.nonchat.api.ChannelAPI} processors, and before
 * mention handling, formatting, and broadcast. Cancelling the event
 * silently blocks the message.
 *
 * <p>The recipient set is live: adding or removing players changes who
 * receives the message.
 *
 * <pre>{@code
 * @EventHandler
 * public void onChannelMessage(NonchatChannelMessageEvent event) {
 *     if (event.getChannel().getId().equals("staff")) {
 *         event.setCancelled(true);
 *         return;
 *     }
 *     event.getRecipients().removeIf(player -> player.hasPermission("vip.nomention"));
 * }
 * }</pre>
 */
public final class NonchatChannelMessageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Channel channel;
    private final Set<Player> recipients;
    private String message;
    private boolean cancelled;

    /**
     * Creates a new channel-message event.
     *
     * @param player     who is sending the message
     * @param channel    channel the message will be posted to
     * @param message    message text after ChannelAPI processors
     * @param recipients live set of players who will receive the message
     */
    public NonchatChannelMessageEvent(
            @NotNull Player player,
            @NotNull Channel channel,
            @NotNull String message,
            @NotNull Set<Player> recipients) {
        this.player = Objects.requireNonNull(player, "player");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.message = Objects.requireNonNull(message, "message");
        this.recipients = Objects.requireNonNull(recipients, "recipients");
    }

    /**
     * Gets the player sending the channel message.
     *
     * @return sender, never {@code null}
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the channel this message will be posted to.
     *
     * @return channel, never {@code null}
     */
    @NotNull
    public Channel getChannel() {
        return channel;
    }

    /**
     * Gets the message text after ChannelAPI processors.
     *
     * @return message text, never {@code null}
     */
    @NotNull
    public String getMessage() {
        return message;
    }

    /**
     * Replaces the message text that will be broadcast.
     *
     * @param message new message text, must not be {@code null}
     */
    public void setMessage(@NotNull String message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * Gets the live set of recipients. Changes are reflected when the
     * message is broadcast.
     *
     * @return recipient set, never {@code null}
     */
    @NotNull
    public Set<Player> getRecipients() {
        return recipients;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}