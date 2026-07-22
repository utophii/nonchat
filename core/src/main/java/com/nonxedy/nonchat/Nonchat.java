package com.nonxedy.nonchat;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.nonxedy.nonchat.api.Channel;
import com.nonxedy.nonchat.api.ChannelAPI;
import com.nonxedy.nonchat.api.IPlatformAdapter;
import com.nonxedy.nonchat.command.impl.IgnoreCommand;
import com.nonxedy.nonchat.command.impl.SpyCommand;
import com.nonxedy.nonchat.config.DeathConfig;
import com.nonxedy.nonchat.core.BroadcastManager;
import com.nonxedy.nonchat.core.ChatManager;
import com.nonxedy.nonchat.core.IndirectDeathTracker;
import com.nonxedy.nonchat.core.MessageManager;
import com.nonxedy.nonchat.hook.DiscordSRVHook;
import com.nonxedy.nonchat.integration.DiscordSRVIntegration;
import com.nonxedy.nonchat.listener.DamageTrackingListener;
import com.nonxedy.nonchat.listener.DeathCoordinates;
import com.nonxedy.nonchat.listener.DeathListener;
import com.nonxedy.nonchat.listener.JoinQuitListener;
import com.nonxedy.nonchat.listener.MentionTabCompleteListener;
import com.nonxedy.nonchat.listener.PlayerCleanupListener;
import com.nonxedy.nonchat.placeholders.NonchatExpansion;
import com.nonxedy.nonchat.service.ChatService;
import com.nonxedy.nonchat.service.CommandService;
import com.nonxedy.nonchat.service.ConfigService;
import com.nonxedy.nonchat.service.DeathMessageService;
import com.nonxedy.nonchat.util.InteractivePlaceholderManager;
import com.nonxedy.nonchat.util.chat.filters.LinkDetector;
import com.nonxedy.nonchat.util.core.debugging.Debugger;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;
import com.nonxedy.nonchat.util.core.updates.UpdateChecker;
import com.nonxedy.nonchat.util.integration.external.IntegrationUtil;
import com.nonxedy.nonchat.util.items.localization.ItemLocalizationUtil;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.ErrorTracker;
import dev.faststats.core.data.Metric;
import lombok.extern.slf4j.Slf4j;

import org.bstats.bukkit.Metrics;
@Slf4j
public class Nonchat extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 0; // Update with actual plugin ID from https://bstats.org/what-is-my-plugin-id
    private ChatService chatService;
    private CommandService commandService;
    private ConfigService configService;
    private DeathConfig deathConfig;
    private DeathMessageService deathMessageService;
    private ChatManager chatManager;
    private MessageManager messageManager;
    private BroadcastManager broadcastManager;
    private SpyCommand spyCommand;
    private Debugger debugger;
    private IgnoreCommand ignoreCommand;
    private DiscordSRVIntegration discordSRVIntegration;
    private InteractivePlaceholderManager placeholderManager;
    private IndirectDeathTracker indirectDeathTracker;
    private DamageTrackingListener damageTrackingListener;
    private PlayerCleanupListener playerCleanupListener;
    private MentionTabCompleteListener mentionTabCompleteListener;
    private IPlatformAdapter platformAdapter;
    private Metrics bStatsMetrics;

    public static final ErrorTracker ERROR_TRACKER = ErrorTracker.contextAware();
    private final BukkitMetrics metrics = BukkitMetrics.factory()
        .token("b1aef8463d939edcdbdd4027352dcc86")
        .addMetric(Metric.number("worlds", () -> getServer().getWorlds().size()))
        .addMetric(Metric.number("plugins", () -> getServer().getPluginManager().getPlugins().length))
        .addMetric(Metric.number("players_online", () -> getServer().getOnlinePlayers().size()))
        .errorTracker(ERROR_TRACKER)
        .debug(false)
        .onFlush(() -> resetCounters()) // Useful for cleaning up data, invalidating caches, or resetting counters
        .create(this);

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            
            // Only save language files if they don't exist (preserve user changes)
            if (!new File(getDataFolder(), "langs/messages_en.yml").exists()) {
                saveResource("langs/messages_en.yml", false);
            }
            if (!new File(getDataFolder(), "langs/messages_ru.yml").exists()) {
                saveResource("langs/messages_ru.yml", false);
            }
            if (!new File(getDataFolder(), "langs/messages_es.yml").exists()) {
                saveResource("langs/messages_es.yml", false);
            }
            
            initializeServices();
            registerPlaceholders();
            registerListeners();
            setupIntegrations();
            metrics.ready();
            this.bStatsMetrics = new Metrics(this, BSTATS_PLUGIN_ID);
            
            MessageUtil.send(Bukkit.getConsoleSender(), "§d[nonchat] §aplugin enabled");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable plugin: {0}", e.getMessage());
             throw new RuntimeException("Failed to enable plugin", e);
        }
    }

    private void initializeServices() {
        try {
            // First initialize configuration
            this.configService = new ConfigService(this);

            // Now that config is loaded, initialize the rest of the services
            this.spyCommand = new SpyCommand(this, configService.getMessages(), configService.getConfig());
            this.ignoreCommand = new IgnoreCommand(this, configService.getMessages());

            // Initialize core managers
            this.chatManager = new ChatManager(this, configService.getConfig(), configService.getMessages());
            this.messageManager = new MessageManager(this, configService.getConfig(), configService.getMessages(), spyCommand);
            this.broadcastManager = new BroadcastManager(this, configService.getConfig());

            // Set ignore command in managers
            this.chatManager.setIgnoreCommand(ignoreCommand);
            this.messageManager.setIgnoreCommand(ignoreCommand);

            // Initialize LinkDetector with translation support before creating commands
            LinkDetector.initialize(configService.getMessages());

            // Initialize service layer that depends on managers
            this.chatService = new ChatService(chatManager, messageManager, broadcastManager, configService.getConfig());

            // Initialize command service last as it depends on all other services
            this.commandService = new CommandService(this, configService);

            // Initialize interactive placeholder manager
            initializeInteractivePlaceholders();
            
            // Initialize debug system if enabled (must be before death message service)
            if (configService.getConfig().isDebug()) {
                this.debugger = new Debugger(this, configService.getConfig().getDebugLogRetentionDays());
                debugger.info("Core", "Debug system initialized");
                
                // Initialize debugger
                ItemLocalizationUtil.setDebugger(debugger);
            }
            
            // Initialize death message service with independent configuration
            // DeathConfig.load() will create deaths.yml if needed and auto-update with missing keys
            try {
                this.deathConfig = new DeathConfig(getDataFolder(), getLogger(), this);
                this.deathConfig.load(); // This calls saveDefaultConfig() and setupConfigFile() before loading
                
                // Initialize indirect death tracker with configurable tracking window
                int trackingWindow = deathConfig.getTrackingWindow();
                this.indirectDeathTracker = new IndirectDeathTracker(trackingWindow);
                
                // Initialize death message service with indirect death tracker and debugger
                this.deathMessageService = new DeathMessageService(this, deathConfig, configService.getMessages(), indirectDeathTracker, debugger);
                
                getLogger().info("Death message service initialized successfully");
                if (deathConfig.isIndirectTrackingEnabled()) {
                    getLogger().info("Indirect death tracking enabled (window: " + trackingWindow + "s)");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialize death message service: {0}", e.getMessage());
                getLogger().log(Level.WARNING, "Death messages will be disabled. Error: " + e.getMessage(), e);
                this.deathConfig = null;
                this.deathMessageService = null;
                this.indirectDeathTracker = null;
            }
            
            if (debugger != null) {
                debugger.info("Core", "Services initialized successfully");
            }

            getLogger().info("Core services initialized successfully");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize core services: {0}", e.getMessage());
            throw new RuntimeException("Failed to initialize core services", e);
        }
    }

    private void initializeInteractivePlaceholders() {
        try {
            this.placeholderManager = new InteractivePlaceholderManager();

            // Load custom placeholders from config
            configService.loadCustomPlaceholdersFromConfig(placeholderManager);

            getLogger().info("Interactive placeholders initialized successfully");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize interactive placeholders: {0}", e.getMessage());
            throw new RuntimeException("Failed to initialize interactive placeholders", e);
        }
    }

    private void reloadInteractivePlaceholders() {
        try {
            if (placeholderManager != null) {
                // Clear existing placeholders
                placeholderManager.clearPlaceholders();

                // Reload from config
                configService.loadCustomPlaceholdersFromConfig(placeholderManager);

                getLogger().info("Interactive placeholders reloaded successfully");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to reload interactive placeholders: {0}", e.getMessage());
        }
    }

    private void registerListeners() {
        try {
            // Register platform adapter
            this.platformAdapter = VersionDetector.detect();
            platformAdapter.registerChatListener(this, chatService);

            // Register death-related listeners
            if (deathMessageService != null && deathConfig != null) {
                Bukkit.getPluginManager().registerEvents(new DeathListener(configService.getConfig(), deathMessageService), this);
                Bukkit.getPluginManager().registerEvents(new DeathCoordinates(deathConfig, configService.getMessages()), this);
                
                // Register damage tracking listener for indirect death tracking (conditional based on master toggle)
                if (indirectDeathTracker != null && deathConfig.isIndirectTrackingEnabled()) {
                    double minimumDamage = deathConfig.getMinimumDamage();
                    
                    this.damageTrackingListener = new DamageTrackingListener(
                        indirectDeathTracker,
                        debugger,
                        deathConfig,
                        minimumDamage
                    );
                    Bukkit.getPluginManager().registerEvents(damageTrackingListener, this);
                    getLogger().info("Indirect death tracking enabled (window: " + deathConfig.getTrackingWindow() + "s, min damage: " + minimumDamage + " hearts)");
                } else {
                    getLogger().info("Indirect death tracking disabled");
                }
                
                // Register player cleanup listener to clear tracking data on logout
                if (indirectDeathTracker != null) {
                    this.playerCleanupListener = new PlayerCleanupListener(indirectDeathTracker);
                    Bukkit.getPluginManager().registerEvents(playerCleanupListener, this);
                }
            } else {
                Bukkit.getPluginManager().registerEvents(new DeathListener(configService.getConfig()), this);
            }

            // Register join/quit listener
            Bukkit.getPluginManager().registerEvents(new JoinQuitListener(configService.getConfig(), chatManager.getChannelManager()), this);
            this.mentionTabCompleteListener = new MentionTabCompleteListener();
            Bukkit.getPluginManager().registerEvents(mentionTabCompleteListener, this);
            mentionTabCompleteListener.refreshAllPlayers();

            // Log successful listener registration
            if (debugger != null) {
                debugger.info("Events", "Event listeners registered successfully");
            }
            getLogger().info("Event listeners registered successfully");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to register event listeners: {0}", e.getMessage());
            throw new RuntimeException("Failed to register event listeners", e);
        }
    }

    private void registerPlaceholders() {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new NonchatExpansion(this).register();
                getLogger().info("PlaceholderAPI expansion registered successfully");
            } else {
                getLogger().info("PlaceholderAPI not available - placeholders will be disabled");
            }
        } catch (NoClassDefFoundError e) {
            getLogger().info("PlaceholderAPI not installed - placeholders will be disabled");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion: {0}", e.getMessage());
        }
    }

    private void setupIntegrations() {
        try {
            // Initialize external integrations (PlaceholderAPI, LuckPerms, Vault)
            IntegrationUtil.setupIntegrations();
        } catch (NoClassDefFoundError e) {
            getLogger().info("Some external integrations not available - features will be disabled");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to setup external integrations: {0}", e.getMessage());
        }

        try {
            // Initialize DiscordSRV
            new DiscordSRVHook(this);
            ChannelAPI.initialize(new ChannelAPI.ChannelAccess() {
                @Override
                public Collection<Channel> getAllChannels() {
                    return chatManager.getChannelManager().getAllChannels();
                }

                @Override
                public Channel getChannel(String channelId) {
                    return chatManager.getChannelManager().getChannel(channelId);
                }

                @Override
                public Channel getPlayerChannel(org.bukkit.entity.Player player) {
                    return chatManager.getPlayerChannel(player);
                }
            });

            if (Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
                this.discordSRVIntegration = new DiscordSRVIntegration(this);
                getLogger().info("DiscordSRV integration enabled");
            } else {
                getLogger().info("DiscordSRV not available - Discord integration will be disabled");
            }
        } catch (NoClassDefFoundError e) {
            getLogger().info("DiscordSRV not installed - Discord integration will be disabled");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to setup DiscordSRV integration: {0}", e.getMessage());
        }

        try {
            // Initialize update checker if enabled
            if (configService.getConfig().isUpdateCheckerEnabled()) {
                getLogger().info("Initializing update checker...");
                new UpdateChecker(this);
                if (debugger != null) {
                    debugger.info("Updates", "Update checker initialized");
                }
                getLogger().info("Update checker initialized successfully");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to initialize update checker: {0}", e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        try {
            // Clean up all chat bubbles and display entities
            if (chatManager != null) {
                chatManager.cleanup();
            }

            if (platformAdapter != null) {
                platformAdapter.cleanup();
            }

            // Cancel all scheduled tasks
            if (broadcastManager != null) {
                broadcastManager.stop();
            }

            if (commandService != null) {
                commandService.unregisterAll();
            }

            if (discordSRVIntegration != null) {
                discordSRVIntegration.unregister();
            }

            // Clean up indirect death tracker cache
            if (indirectDeathTracker != null) {
                indirectDeathTracker.clearAll();
            }

            // Clean up ChannelAPI registrations
            ChannelAPI.cleanupAll();

            // Cancel all remaining Bukkit tasks for this plugin
            Bukkit.getScheduler().cancelTasks(this);

            MessageUtil.send(Bukkit.getConsoleSender(), "§d[nonchat] §cplugin disabled");
            
            metrics.shutdown();

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during plugin shutdown: {0}", e.getMessage());
        }
    }

    /**
     * Resets counters for metrics collection.
     * This method is called when metrics data is flushed.
     */
    private void resetCounters() {
        // Reset any counters or metrics data here if needed
        // Currently no specific counters to reset, but method is required by metrics API
    }

    @Override
    public void reloadConfig() {
        try {
            super.reloadConfig();

            if (configService != null) {
                configService.reload();
            }

            if (broadcastManager != null) {
                broadcastManager.reload();
            }

            // Reinitialize LinkDetector with updated messages before reloading commands
            if (configService != null) {
                LinkDetector.initialize(configService.getMessages());
            }

            if (commandService != null) {
                commandService.reloadCommands();
            }

            // Reload interactive placeholders
            reloadInteractivePlaceholders();

            getLogger().info("Configuration reloaded successfully");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to reload configuration: {0}", e.getMessage());
            throw new RuntimeException("Failed to reload configuration", e);
        }
    }

    public void reloadServices() {
        try {
            // Stop services that need clean shutdown
            if (broadcastManager != null) {
                broadcastManager.stop();
            }

            // Reload configuration
            if (configService != null) {
                configService.reload();
            }

            // Reinitialize services that need it
            if (broadcastManager != null) {
                broadcastManager.reload();
            }

            // Reinitialize LinkDetector with updated messages before reloading commands
            if (configService != null) {
                LinkDetector.initialize(configService.getMessages());
            }

            // Reload commands and channels after LinkDetector is reinitialized
            if (commandService != null) {
                commandService.reloadCommands();
            }

            if (chatManager != null) {
                chatManager.reloadChannels();
            }

            // Reload interactive placeholders
            reloadInteractivePlaceholders();
            
            // Reload death message service (includes indirect tracking configuration hot-reload)
            if (deathMessageService != null && deathConfig != null) {
                try {
                    deathMessageService.reload();
                    
                    // Handle listener re-registration based on updated master toggle
                    reloadDeathTrackingListeners();
                    
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to reload death message service: {0}", e.getMessage());
                }
            }

            // Reinitialize debugger if needed
            if (configService != null && configService.getConfig().isDebug()) {
                if (debugger == null) {
                    debugger = new Debugger(this, configService.getConfig().getDebugLogRetentionDays());
                }
                // Update debugger
                ItemLocalizationUtil.setDebugger(debugger);
            } else {
                debugger = null;
                ItemLocalizationUtil.setDebugger(null);
            }
            
            getLogger().info("Services reloaded successfully");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to reload services: {0}", e.getMessage());
            throw new RuntimeException("Failed to reload services", e);
        }
    }
    
    /**
     * Reloads death tracking listeners based on updated configuration.
     * Handles conditional listener registration based on master toggle.
     */
    private void reloadDeathTrackingListeners() {
        try {
            // Unregister existing damage tracking listener if it exists
            if (damageTrackingListener != null) {
                org.bukkit.event.HandlerList.unregisterAll(damageTrackingListener);
                damageTrackingListener = null;
            }
            
            // Re-register damage tracking listener if indirect tracking is enabled
            if (indirectDeathTracker != null && deathConfig != null && deathConfig.isIndirectTrackingEnabled()) {
                double minimumDamage = deathConfig.getMinimumDamage();
                
                this.damageTrackingListener = new DamageTrackingListener(
                    indirectDeathTracker,
                    debugger,
                    deathConfig,
                    minimumDamage
                );
                Bukkit.getPluginManager().registerEvents(damageTrackingListener, this);
                getLogger().info("Damage tracking listener re-registered (window: " + deathConfig.getTrackingWindow() + 
                               "s, min damage: " + minimumDamage + " hearts)");
            } else {
                getLogger().info("Damage tracking listener unregistered (indirect tracking disabled)");
            }
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to reload death tracking listeners: {0}", e.getMessage());
        }
    }

    public void logCommand(String command, String[] args) {
        try {
            if (debugger != null) {
                debugger.debug("Commands", command + " Args: " + Arrays.toString(args));
            }
        } catch (Exception e) {
            getLogger().log(Level.FINE, "Failed to log command: {0}", e.getMessage());
        }
    }

    public void logResponse(String response) {
        try {
            if (debugger != null) {
                debugger.debug("API", "Response: " + response);
            }
        } catch (Exception e) {
            getLogger().log(Level.FINE, "Failed to log response: {0}", e.getMessage());
        }
    }

    public void logError(String error) {
        try {
            if (debugger != null) {
                debugger.error("System", "Error occurred", new Exception(error));
            }
        } catch (Exception e) {
            getLogger().log(Level.FINE, "Failed to log error: {0}", e.getMessage());
        }
    }

    public void logPlaceholder(String placeholder, String result) {
        try {
            if (debugger != null) {
                debugger.debug("Placeholders", placeholder + " -> " + result);
            }
        } catch (Exception e) {
            getLogger().log(Level.FINE, "Failed to log placeholder: {0}", e.getMessage());
        }
    }

    /**
     * Logs chat messages to debug file
     * @param message The message to log
     */
    public void logChatMessage(String message) {
        try {
            if (debugger != null) {
                debugger.debug("Chat", message);
            }
        } catch (Exception e) {
            getLogger().log(Level.FINE, "Failed to log chat message: {0}", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------------------------
    public ChatManager getChatManager() {
        return chatManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public SpyCommand getSpyCommand() {
        return spyCommand;
    }

    public IgnoreCommand getIgnoreCommand() {
        return ignoreCommand;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public InteractivePlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    public IPlatformAdapter getPlatformAdapter() {
        return platformAdapter;
    }
    
    public DeathMessageService getDeathMessageService() {
        return deathMessageService;
    }
    
    public IndirectDeathTracker getIndirectDeathTracker() {
        return indirectDeathTracker;
    }
    
    /**
     * Reloads death message configuration
     * @throws RuntimeException if reload fails
     */
    public void reloadDeathMessages() {
        if (deathMessageService != null) {
            deathMessageService.reload();
        } else {
            getLogger().warning("Death message service is not initialized");
            throw new RuntimeException("Death message service is not initialized");
        }
    }
}
