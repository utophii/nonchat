package com.nonxedy.nonchat.adapter.v26_3_R1;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.nonxedy.nonchat.api.IMessageHandler;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ChatListener263 implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private final IMessageHandler handler;

    public ChatListener263(IMessageHandler handler) {
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        event.setCancelled(true);
        handler.handleChat(event.getPlayer(), PLAIN_TEXT.serialize(event.message()));
    }
}
