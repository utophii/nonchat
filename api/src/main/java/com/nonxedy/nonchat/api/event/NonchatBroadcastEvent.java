package com.nonxedy.nonchat.api.event;

import java.util.Objects;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a server-wide broadcast is about to be sent through nonchat
 * ({@code /broadcast} or an automatic scheduled broadcast).
 *
 * <p>Fired before formatting and delivery. Cancelling the event silently
 * blocks the broadcast. The recipient set is live: adding or removing
 * players changes who receives the message.
 *
 * <pre>{@code
 * @EventHandler
 * public void onBroadcast(NonchatBroadcastEvent event) {
 *     if (event.isAutomatic()) {
 *         return;
 *     }
 *     if (event.getMessage().contains("secret")) {
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 */
public final class NonchatBroadcastEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender sender;
    private final Set<Player> recipients;
    private final boolean automatic;
    private String message;
    private boolean cancelled;

    /**
     * Creates a new broadcast event.
     *
     * @param sender     who triggered the broadcast (command sender, or console for auto)
     * @param message    raw broadcast text, before formatting
     * @param recipients live set of players who will receive the broadcast
     * @param automatic  {@code true} when this is a scheduled auto-broadcast
     */
    public NonchatBroadcastEvent(
            @NotNull CommandSender sender,
            @NotNull String message,
            @NotNull Set<Player> recipients,
            boolean automatic) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.message = Objects.requireNonNull(message, "message");
        this.recipients = Objects.requireNonNull(recipients, "recipients");
        this.automatic = automatic;
    }

    /**
     * Gets who triggered the broadcast.
     * For auto-broadcasts this is the console.
     *
     * @return sender, never {@code null}
     */
    @NotNull
    public CommandSender getSender() {
        return sender;
    }

    /**
     * Gets the raw broadcast text.
     *
     * @return message text, never {@code null}
     */
    @NotNull
    public String getMessage() {
        return message;
    }

    /**
     * Replaces the broadcast text that will be delivered.
     *
     * @param message new message text, must not be {@code null}
     */
    public void setMessage(@NotNull String message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * Gets the live set of recipients. Changes are reflected when the
     * broadcast is sent.
     *
     * @return recipient set, never {@code null}
     */
    @NotNull
    public Set<Player> getRecipients() {
        return recipients;
    }

    /**
     * Checks whether this is a scheduled auto-broadcast rather than {@code /broadcast}.
     *
     * @return {@code true} if this broadcast was sent automatically
     */
    public boolean isAutomatic() {
        return automatic;
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