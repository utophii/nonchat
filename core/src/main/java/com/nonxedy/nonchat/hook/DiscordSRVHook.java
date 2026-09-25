package com.nonxedy.nonchat.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.nonxedy.nonchat.Nonchat;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.util.DiscordUtil;

/**
 * Provides integration with the DiscordSRV plugin
 */
public class DiscordSRVHook {

    private final Nonchat plugin;
    private DiscordSRV discordSRV;
    private boolean isHooked = false;

    public DiscordSRVHook(Nonchat plugin) {
        this.plugin = plugin;
        try {
            Plugin discordSRVPlugin = Bukkit.getPluginManager().getPlugin("DiscordSRV");
            
            if (discordSRVPlugin != null && discordSRVPlugin.isEnabled()) {
                this.discordSRV = DiscordSRV.getPlugin();
                // Register this as a listener for DiscordSRV events
                DiscordSRV.api.subscribe(this);
                this.isHooked = true;
                plugin.logResponse("Successfully hooked into DiscordSRV!");
            } else {
                plugin.logError("DiscordSRV plugin not found or is disabled. DiscordSRV integration will not be available.");
            }
        } catch (Exception e) {
            plugin.logError("Failed to hook into DiscordSRV: " + e.getMessage());
        }
    }

    /**
     * Send a message to a specific Discord channel through DiscordSRV
     * 
     * @param channelName The name of the channel as configured in DiscordSRV
     * @param message The message to send
     * @return true if message was sent, false otherwise
     */
    public boolean sendMessageToChannel(String channelName, String message) {
        if (!isHooked || discordSRV == null) {
            return false;
        }

        try {
            plugin.logResponse("DiscordSRVHook: Sending message to channel '" + channelName + "': " + message);
            
            // Get the TextChannel object from DiscordSRV using the channel name
            TextChannel channel = discordSRV.getDestinationTextChannelForGameChannelName(channelName);
            
            if (channel != null) {
                plugin.logResponse("Found Discord channel: " + channel.getName() + " (" + channel.getId() + ")");
                DiscordUtil.sendMessage(channel, message);
                return true;
            } else {
                plugin.logError("DiscordSRV channel not found: " + channelName);
                return false;
            }
        } catch (Exception e) {
            plugin.logError("Error sending message to Discord: " + e.getMessage());
        }
        
        return false;
    }

    /**
     * Handle Discord ready event
     */
    @Subscribe
    public void onDiscordReady(DiscordReadyEvent event) {
        plugin.logResponse("Connected to Discord via DiscordSRV!");
    }

    /**
     * Check if the hook to DiscordSRV is active
     * 
     * @return true if connected to DiscordSRV, false otherwise
     */
    public boolean isHooked() {
        return isHooked;
    }

    /**
     * Shutdown the hook - unsubscribe from DiscordSRV events
     */
    public void shutdown() {
        if (isHooked && discordSRV != null) {
            try {
                DiscordSRV.api.unsubscribe(this);
                plugin.logResponse("Successfully unhooked from DiscordSRV");
            } catch (Exception e) {
                plugin.logError("Error while unhooking from DiscordSRV: " + e.getMessage());
            }
        }
    }
}
