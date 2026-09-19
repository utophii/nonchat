package com.nonxedy.nonchat.listener;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.nonxedy.nonchat.config.DeathConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

/**
 * Handles death location tracking and messaging
 * Provides players with their death coordinates
 */
public class DeathCoordinates implements Listener {
    
    private final DeathConfig deathConfig;
    private final PluginMessages messages;
    private final Logger logger;
    
    public DeathCoordinates(DeathConfig deathConfig, PluginMessages messages, Logger logger) {
        this.deathConfig = deathConfig;
        this.messages = messages;
        this.logger = logger;
    }
    
    /**
     * Handles player death events to display death coordinates
     * Runs with HIGH priority after vanilla message generation
     * @param event Death event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // This listener now ONLY handles coordinates, not death messages
        Player player = event.getEntity();
        Location deathLoc = player.getLocation();
        Environment dimension = deathLoc.getWorld().getEnvironment();
        
        // Only send coordinates if enabled in deaths.yml config
        if (!deathConfig.showCoordinates()) {
            return;
        }
        
        String template = messages.getString("death-coordinates");
        String dimensionName = formatDimension(dimension);
        int x = deathLoc.getBlockX();
        int y = deathLoc.getBlockY();
        int z = deathLoc.getBlockZ();
        
        try {
            // Args are always passed in the same order:
            //   1 = world name (String)
            //   2 = x (int)
            //   3 = y (int)
            //   4 = z (int)
            // Templates SHOULD use positional specifiers so any field can be
            // removed or reordered freely, e.g.:
            //   "%1$s x:%2$d y:%3$d z:%4$d"          – world + coords
            //   "x:%2$d y:%3$d z:%4$d"               – coords only (no world)
            //   "%4$d/%2$d/%3$d in %1$s"             – reordered
            // Legacy unindexed specifiers (%s / %d) still work as long as
            // they appear in the original world,x,y,z order.
            String coordsMessage = String.format(template, dimensionName, x, y, z);
            MessageUtil.send(player, ColorUtil.parseComponent(coordsMessage));
        } catch (Exception e) {
            // A bad user template must never break the death event itself,
            // otherwise the player gets "Could not pass event PlayerDeathEvent"
            // and may be disconnected or lose drops.
            logger.log(Level.WARNING,
                    "Failed to format death-coordinates message: \"" + template
                            + "\". Placeholders must match their values: "
                            + "%1$s = world, %2$d = x, %3$d = y, %4$d = z.", e);
        }
    }
    
    /**
     * Converts dimension enum to readable name using localized messages
     * @param dimension World environment type
     * @return Localized dimension name
     */
    private String formatDimension(Environment dimension) {
        return switch (dimension) {
            case NORMAL -> messages.getString("dimension-overworld");
            case NETHER -> messages.getString("dimension-nether");
            case THE_END -> messages.getString("dimension-end");
            default -> dimension.toString();
        };
    }
}
