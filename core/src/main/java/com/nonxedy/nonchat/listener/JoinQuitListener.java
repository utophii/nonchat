package com.nonxedy.nonchat.listener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.nonxedy.nonchat.chat.channel.ChannelManager;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.core.ChatManager;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Handles player join and quit events
 * Displays customizable messages when players join or leave the server
 */
public class JoinQuitListener implements Listener {
    
    private final PluginConfig config;
    private final ChannelManager channelManager;
    private final ChatManager chatManager;
    private final Map<UUID, Boolean> firstJoinCache = new HashMap<>();
    
    public JoinQuitListener(PluginConfig config, ChannelManager channelManager, ChatManager chatManager) {
        this.config = config;
        this.channelManager = channelManager;
        this.chatManager = chatManager;
    }

    /**
     * Cleans up sound names by converting to proper Minecraft resource location format
     * @param soundName The original sound name
     * @return The cleaned sound name in proper format (lowercase with dots)
     */
    private String cleanSoundName(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) {
            return "entity.experience_orb.pickup"; // fallback sound
        }

        // Remove "minecraft:" prefix if present
        String cleaned = soundName.trim();
        if (cleaned.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            cleaned = cleaned.substring("minecraft:".length());
        }

        // Already in resource-key format (e.g. "entity.experience_orb.pickup")
        if (cleaned.contains(".")) {
            return cleaned.toLowerCase(Locale.ROOT);
        }

        try {
            Sound sound = Sound.valueOf(cleaned.toUpperCase(Locale.ROOT).replace(' ', '_'));
            return sound.getKey().getKey();
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().log(Level.WARNING,
                "Unknown sound name in config: {0} - falling back to naive conversion", soundName);
            return cleaned.toLowerCase(Locale.ROOT).replace('_', '.');
        }
    }
    
    /**
     * Handles player join events
     * @param event Join event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isJoinMessageEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check if this is the player's first join using hasPlayedBefore()
        boolean isFirstJoin = !player.hasPlayedBefore();
        
        String joinFormat;
        if (isFirstJoin) {
            // Use first join message format
            joinFormat = config.getFirstJoinFormat();
            // Cache this information
            firstJoinCache.put(playerId, true);
        } else {
            // Use regular join message format
            joinFormat = config.getJoinFormat();
        }
        
        // Apply PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                joinFormat = PlaceholderAPI.setPlaceholders(player, joinFormat);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Error processing join message placeholders: {0}", e.getMessage());
            }
        }
        
        MessageUtil.joinMessage(event, ColorUtil.parseComponent(joinFormat));
        
        // Play join sound if enabled for join events
        if (config.isJoinSoundEnabled()) {
            try {
                String soundName = cleanSoundName(config.getJoinSound());
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.playSound(onlinePlayer.getLocation(), 
                        soundName, 
                        config.getJoinSoundVolume(), config.getJoinSoundPitch());
                }
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Error playing join sound: {0}", e.getMessage());
            }
        }
    }
    
    /**
     * Handles player quit events
     * @param event Quit event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up player data from ChannelManager to prevent memory leaks
        if (channelManager != null) {
            channelManager.cleanupPlayer(player);
        }
        if (chatManager != null) {
            chatManager.cleanupPlayer(player);
        }
        
        if (!config.isQuitMessageEnabled()) {
            return;
        }
        
        String quitFormat = config.getQuitFormat();
        
        // Apply PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                quitFormat = PlaceholderAPI.setPlaceholders(player, quitFormat);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Error processing quit message placeholders: {0}", e.getMessage());
            }
        }
        
        MessageUtil.quitMessage(event, ColorUtil.parseComponent(quitFormat));
        
        // Play quit sound if enabled for quit events
        if (config.isQuitSoundEnabled()) {
            try {
                String soundName = cleanSoundName(config.getQuitSound());
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.playSound(onlinePlayer.getLocation(), 
                        soundName, 
                        config.getQuitSoundVolume(), config.getQuitSoundPitch());
                }
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Error playing quit sound: {0}", e.getMessage());
            }
        }
    }
}
