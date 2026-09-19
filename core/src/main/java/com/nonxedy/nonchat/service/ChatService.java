package com.nonxedy.nonchat.service;

import com.nonxedy.nonchat.util.core.broadcast.BroadcastMessage;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.api.IMessageHandler;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.core.BroadcastManager;
import com.nonxedy.nonchat.core.ChatManager;
import com.nonxedy.nonchat.core.MessageManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

public class ChatService implements IMessageHandler {
    private final ChatManager chatManager;
    private final MessageManager messageManager;
    private final BroadcastManager broadcastManager;

    public ChatService(ChatManager chatManager, MessageManager messageManager, 
                      BroadcastManager broadcastManager, PluginConfig config) {
        this.chatManager = chatManager;
        this.messageManager = messageManager;
        this.broadcastManager = broadcastManager;
    }

    @Override
    public void handleChat(Player player, String message) {
        chatManager.processChat(player, message);
    }

    public void handlePrivateMessage(Player sender, Player receiver, String message) {
        // Delegate to MessageManager which handles ignore checking internally
        messageManager.sendPrivateMessage(sender, receiver, message);
    }

    public void handleBroadcast(BroadcastMessage message) {
        // Broadcasts should always support colors (admin command)
        broadcastManager.broadcast(message);
    }

    public void handleStaffChat(Player sender, String message) {
        // Check if player is trying to use colors without permission in staff chat
        if (!sender.hasPermission("nonchat.color") && ColorUtil.hasColorCodes(message)) {
            // Strip colors from staff chat message if no permission
            String strippedMessage = ColorUtil.stripFormatting(message);
            chatManager.processChat(sender, "@" + strippedMessage);
        } else {
            // Instead of using the old staff chat format, we'll use the chat manager with the staff chat prefix
            chatManager.processChat(sender, "@" + message);
        }
    }
}
