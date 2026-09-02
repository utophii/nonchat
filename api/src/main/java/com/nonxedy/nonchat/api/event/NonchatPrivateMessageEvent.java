package com.nonxedy.nonchat.api.event;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a private message is about to be sent through nonchat
 * ({@code /msg}, {@code /tell}, {@code /whisper}, {@code /reply}, ...).
 *
 * <p>Fired after ignore and world-scope checks, and before formatting,
 * delivery, spy, and reply-target updates. Cancelling the event silently
 * blocks the message: no spy notification and no reply-target change.
 *
 * <pre>{@code
 * @EventHandler
 * public void onPrivateMessage(NonchatPrivateMessageEvent event) {
 *     if (event.getMessage().contains("secret")) {
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 */
public final class NonchatPrivateMessageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender sender;
    private Player receiver;
    private String message;
    private final boolean reply;
    private boolean cancelled;

    /**
     * Creates a new private-message event.
     *
     * @param sender   who is sending the message (player or console)
     * @param receiver who should receive the message
     * @param message  raw message text, before color stripping and formatting
     * @param reply    {@code true} when the message comes from {@code /reply}
     */
    public NonchatPrivateMessageEvent(
            @NotNull CommandSender sender,
            @NotNull Player receiver,
            @NotNull String message,
            boolean reply) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.message = Objects.requireNonNull(message, "message");
        this.reply = reply;
    }

    /**
     * Gets the sender of the private message.
     * May be the console; use {@code instanceof Player} when a player is required.
     *
     * @return message sender, never {@code null}
     */
    @NotNull
    public CommandSender getSender() {
        return sender;
    }

    /**
     * Gets the intended recipient of the private message.
     *
     * @return message recipient, never {@code null}
     */
    @NotNull
    public Player getReceiver() {
        return receiver;
    }

    /**
     * Changes the recipient of the private message.
     *
     * @param receiver new recipient, must not be {@code null}
     */
    public void setReceiver(@NotNull Player receiver) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
    }

    /**
     * Gets the raw private-message text.
     *
     * @return message text, never {@code null}
     */
    @NotNull
    public String getMessage() {
        return message;
    }

    /**
     * Replaces the private-message text that will be delivered.
     *
     * @param message new message text, must not be {@code null}
     */
    public void setMessage(@NotNull String message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * Checks whether this message was sent with {@code /reply} rather than {@code /msg}.
     *
     * @return {@code true} if this is a reply
     */
    public boolean isReply() {
        return reply;
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
