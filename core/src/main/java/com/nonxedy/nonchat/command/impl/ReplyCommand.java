package com.nonxedy.nonchat.command.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.core.MessageManager;
import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

public class ReplyCommand implements CommandExecutor, TabCompleter {
    private final Nonchat plugin;
    private final MessageManager messageManager;
    private final PluginMessages messages;

    public ReplyCommand(Nonchat plugin, MessageManager messageManager, PluginMessages messages) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.logCommand(command.getName(), args);

        if (!(sender instanceof Player)) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("player-only")));
            return true;
        }

        Player player = (Player) sender;

        if (!sender.hasPermission("nonchat.reply")) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            if (plugin != null) {
                plugin.logError("Player " + sender.getName() + " tried to use the reply command without permission.");
            }
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(sender, ColorUtil.parseComponentCached(messages.getString("invalid-usage-reply")));
            return true;
        }

        messageManager.replyToLastMessage(player, String.join(" ", args));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nonchat.reply")) {
            return Collections.emptyList();
        }

        if (args.length > 0) {
            List<String> mentionSuggestions = MentionCompletionUtil.getMentionSuggestions(
                    sender,
                    args[args.length - 1],
                    player -> !(sender instanceof Player senderPlayer)
                            || messageManager.canPrivateMessage(senderPlayer, player));
            if (!mentionSuggestions.isEmpty()) {
                return mentionSuggestions;
            }
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("message");
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
