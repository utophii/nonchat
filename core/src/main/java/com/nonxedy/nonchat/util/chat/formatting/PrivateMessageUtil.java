package com.nonxedy.nonchat.util.chat.formatting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class PrivateMessageUtil {

    public static Component createSenderMessage(
            PluginConfig config, Player sender, Player target, String message) {

        String format = config.getPrivateChatSenderFormat();
        String senderName = sender != null ? sender.getName() : "Console";

        // Parse placeholders ONLY on the format (before inserting user message)
        if (sender != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                format = PlaceholderAPI.setPlaceholders(sender, format);
            } catch (Exception e) {
                // Ignore placeholder errors
            }
        }

        String formattedMessage = format
                .replace("{sender}", senderName)
                .replace("{receiver}", target.getName())
                .replace("{message}", "<nonchat_message>");

        Component baseComponent = ColorUtil.parseComponent(formattedMessage,
                Placeholder.component("nonchat_message", ColorUtil.parseComponent(message, sender)));

        if (config.isPrivateChatSenderHoverEnabled()) {
            baseComponent = addSenderInteractivity(config, baseComponent, sender, target);
        }

        return baseComponent;
    }

    public static Component createReceiverMessage(
            PluginConfig config, Player sender, Player target, String message) {

        String format = config.getPrivateChatReceiverFormat();
        String senderName = sender != null ? sender.getName() : "Console";

        // Parse placeholders ONLY on the format (before inserting user message)
        // Use target for receiver-specific placeholders
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                format = PlaceholderAPI.setPlaceholders(target, format);
            } catch (Exception e) {
                // Ignore placeholder errors
            }
        }

        String formattedMessage = format
                .replace("{sender}", senderName)
                .replace("{receiver}", target.getName())
                .replace("{message}", "<nonchat_message>");

        Component baseComponent = ColorUtil.parseComponent(formattedMessage,
                Placeholder.component("nonchat_message", ColorUtil.parseComponent(message, sender)));

        if (config.isPrivateChatReceiverHoverEnabled()) {
            baseComponent = addReceiverInteractivity(config, baseComponent, sender, target);
        }

        return baseComponent;
    }

    private static Component addSenderInteractivity(
            PluginConfig config, Component component, Player sender, Player target) {

        List<String> hoverLines = config.getPrivateChatSenderHover();
        if (hoverLines.isEmpty()) return component;

        Component hoverComponent = buildHoverText(hoverLines, sender, target);
        Component resultComponent = component.hoverEvent(HoverEvent.showText(hoverComponent));

        if (config.isPrivateChatClickActionsEnabled()) {
            String clickCommand = config.getPrivateChatSenderReplyCommand()
                    .replace("{sender}", sender != null ? sender.getName() : "Console")
                    .replace("{receiver}", target.getName());
            resultComponent = resultComponent.clickEvent(ClickEvent.suggestCommand(clickCommand));
        }

        return resultComponent;
    }

    private static Component addReceiverInteractivity(
            PluginConfig config, Component component, Player sender, Player target) {

        List<String> hoverLines = config.getPrivateChatReceiverHover();
        if (hoverLines.isEmpty()) return component;

        Component hoverComponent = buildHoverText(hoverLines, sender, target);
        Component resultComponent = component.hoverEvent(HoverEvent.showText(hoverComponent));

        if (config.isPrivateChatClickActionsEnabled()) {
            String clickCommand = config.getPrivateChatReceiverReplyCommand()
                    .replace("{sender}", sender != null ? sender.getName() : "Console")
                    .replace("{receiver}", target.getName());
            resultComponent = resultComponent.clickEvent(ClickEvent.suggestCommand(clickCommand));
        }

        return resultComponent;
    }

    private static Component buildHoverText(
            List<String> hoverLines, Player sender, Player target) {

        String currentTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        List<Component> components = new java.util.ArrayList<>();

        for (int i = 0; i < hoverLines.size(); i++) {
            String line = hoverLines.get(i);
            String senderName = sender != null ? sender.getName() : "Console";

            // Parse placeholders on hover lines (these are config-controlled)
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    if (sender != null) {
                        line = PlaceholderAPI.setPlaceholders(sender, line);
                    } else if (target != null) {
                        line = PlaceholderAPI.setPlaceholders(target, line);
                    }
                } catch (Exception ignored) {}
            }

            line = line
                    .replace("{sender}", senderName)
                    .replace("{receiver}", target.getName())
                    .replace("{time}", currentTime);

            components.add(ColorUtil.parseComponent(line));

            if (i < hoverLines.size() - 1) {
                components.add(Component.newline());
            }
        }

        return Component.join(
            JoinConfiguration.noSeparators(),
            components
        );
    }
}