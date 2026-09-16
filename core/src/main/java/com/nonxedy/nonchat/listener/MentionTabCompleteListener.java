package com.nonxedy.nonchat.listener;

import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;

public class MentionTabCompleteListener implements Listener {

    public void refreshAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayerCompletions(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joinedPlayer = event.getPlayer();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            refreshPlayerCompletions(onlinePlayer);
        }

        refreshPlayerCompletions(joinedPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String mention = "@" + event.getPlayer().getName();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.removeCustomChatCompletions(List.of(mention));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (event.isCommand() || !(event.getSender() instanceof Player player)) {
            return;
        }

        List<String> suggestions = MentionCompletionUtil.getMentionSuggestions(player, MentionCompletionUtil.extractLastToken(event.getBuffer()));
        if (suggestions.isEmpty()) {
            return;
        }

        event.setCompletions(suggestions);
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (event.isCommand() || !(event.getSender() instanceof Player player)) {
            return;
        }

        List<String> suggestions = MentionCompletionUtil.getMentionSuggestions(player, MentionCompletionUtil.extractLastToken(event.getBuffer()));
        if (suggestions.isEmpty()) {
            return;
        }

        event.setCompletions(suggestions);
    }

    private void refreshPlayerCompletions(Player player) {
        List<String> mentions = Bukkit.getOnlinePlayers().stream()
                .filter(player::canSee)
                .map(onlinePlayer -> "@" + onlinePlayer.getName())
                .toList();

        player.setCustomChatCompletions(mentions);
    }
}
