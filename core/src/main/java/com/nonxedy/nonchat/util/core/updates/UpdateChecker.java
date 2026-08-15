package com.nonxedy.nonchat.util.core.updates;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.chat.filters.LinkDetector;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import net.kyori.adventure.text.Component;

/**
 * Handles version checking and update notifications for the plugin
 * Connects to Modrinth API to check for new versions
 */
public final class UpdateChecker implements Listener {

    // Initialize class variables
    private final Nonchat plugin;
    private final String currentVersion;
    /** Modrinth API endpoint for version checking */
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/nonchat/version";
    
    // Store update information
    private String latestVersion = null;
    private String downloadUrl = null;
    private boolean updateAvailable = false;

    // Constructor to initialize class variables
    public UpdateChecker(Nonchat plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        
        if (plugin instanceof Nonchat nonchatPlugin) {
            if (!nonchatPlugin.getConfigService().getConfig().isUpdateCheckerEnabled()) {
                plugin.logResponse("Update checker is disabled in config");
                return;
            }
        }

        // Register this as an event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Run initial update check
        plugin.logResponse("Initializing update checker for nonchat version " + currentVersion);
        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate) {
                plugin.logResponse("New version available: " + latestVersion);
                plugin.logResponse("Download: " + downloadUrl);
                
                // Notify online admins
                notifyOnlinePlayers();
            } else {
                plugin.logResponse("You are using the latest version of nonchat!");
            }
        });
    }

    public CompletableFuture<Boolean> checkForUpdates() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Run the update check asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Log connection attempt
                plugin.logResponse("Checking for updates from: " + MODRINTH_API);
                
                // Build HTTP request to Modrinth API
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(MODRINTH_API))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
                
                // Send request and get response
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                
                // Parse JSON response to get latest version info
                JsonObject latestVersion;
                try (InputStreamReader reader = new InputStreamReader(response.body())) {
                    latestVersion = new JsonParser()
                        .parse(reader)
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject();
                }
    
                // Extract version information
                this.latestVersion = latestVersion.get("version_number").getAsString();
                this.downloadUrl = "https://modrinth.com/plugin/nonchat/version/" + this.latestVersion;
    
                // Log received version info
                plugin.logResponse("Current version: " + currentVersion);
                plugin.logResponse("Latest version: " + this.latestVersion);
    
                // Compare versions and update status
                this.updateAvailable = !currentVersion.equals(this.latestVersion);
                plugin.logResponse("Update available: " + this.updateAvailable);
                
                future.complete(this.updateAvailable);
            } catch (JsonIOException | JsonSyntaxException | IOException | InterruptedException | URISyntaxException e) {
                plugin.logError("Failed to check for updates: " + e.getMessage());
                future.complete(false);
            }
        });
        
        return future;
    }

    /**
     * Notifies all currently online players with appropriate permissions
     */
    private void notifyOnlinePlayers() {
        if (!updateAvailable) return;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasUpdatePermission(player)) {
                    sendUpdateNotification(player);
                }
            }
        });
    }
    
    private boolean hasUpdatePermission(Player player) {
        boolean hasPermission = player.hasPermission("nonchat.*") || 
                               player.isOp();
        
        return hasPermission;
    }
    
    /**
     * Sends update notification to a specific player
     * @param player The player to notify
     */
    private void sendUpdateNotification(Player player) {
        try {
            MessageUtil.send(player, ColorUtil.parseComponent("&#FFAFFB[nonchat] &#ffffff A new version is available: &#FFAFFB" + latestVersion));
        
            Component downloadMessage = Component.empty()
                .append(ColorUtil.parseComponent("&#FFAFFB[nonchat] &#ffffffDownload: "))
                .append(LinkDetector.makeLinksClickable(downloadUrl));
        
            MessageUtil.send(player, downloadMessage);
            
            plugin.logResponse("Sent update notification to admin: " + player.getName());
        } catch (Exception e) {
            plugin.logError("Failed to send update notification to " + player.getName() + ": " + e.getMessage());
        }
    }

    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (hasUpdatePermission(player)) {
            if (updateAvailable) {
                // Delay the message slightly to ensure it's seen after join messages
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    sendUpdateNotification(player);
                }, 40L); // 2 second delay for better visibility
            } else if (latestVersion == null) {
                checkForUpdates().thenAccept(hasUpdate -> {
                    if (hasUpdate) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            sendUpdateNotification(player);
                        }, 40L);
                    }
                });
            }
        }
    }
}
