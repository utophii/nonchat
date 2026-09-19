package com.nonxedy.nonchat.api.event;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a private message has been successfully delivered through nonchat
 * ({@code /msg}, {@code /tell}, {@code /whisper}, {@code /reply}, ...).
 *
 * <p>Fired after formatting, delivery, spy, and reply-target updates.
 * This event is not cancellable — use {@link NonchatPrivateMessageEvent}
 * to block or edit a message before it is sent.
 *
 * <pre>{@code
 * @EventHandler
 * public void onPrivateMessageSent(NonchatPrivateMessageSentEvent event) {
 *     plugin.getLogger().info(event.getSender().getName()
 *             + " -> " + event.getReceiver().getName()
 *             + ": " + event.getMessage());
 * }
 * }</pre>
 */
public final class NonchatPrivateMessageSentEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender sender;
    private final Player receiver;
    private final String message;
    private final boolean reply;

    /**
     * Creates a new post-delivery private-message event.
     *
     * @param sender   who sent the message (player or console)
     * @param receiver who received the message
     * @param message  final delivered text (after color stripping)
     * @param reply    {@code true} when the message came from {@code /reply}
     */
    public NonchatPrivateMessageSentEvent(
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
     * Gets the recipient who received the private message.
     *
     * @return message recipient, never {@code null}
     */
    @NotNull
    public Player getReceiver() {
        return receiver;
    }

    /**
     * Gets the final delivered message text.
     *
     * @return message text after color stripping, never {@code null}
     */
    @NotNull
    public String getMessage() {
        return message;
    }

    /**
     * Checks whether this message was sent with {@code /reply} rather than {@code /msg}.
     *
     * @return {@code true} if this was a reply
     */
    public boolean isReply() {
        return reply;
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