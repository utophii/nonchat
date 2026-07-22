package com.nonxedy.nonchat.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import com.nonxedy.nonchat.config.DeathConfig;
import com.nonxedy.nonchat.core.IndirectDeathTracker;
import com.nonxedy.nonchat.util.core.debugging.Debugger;
import com.nonxedy.nonchat.util.death.DamageType;

/**
 * Listens to damage events and records them for indirect death tracking.
 * Uses MONITOR priority to observe final damage after other plugins have processed it.
 */
public class DamageTrackingListener implements Listener {
    
    private final IndirectDeathTracker tracker;
    private final Debugger debugger;
    private final DeathConfig deathConfig;
    private final double minimumDamage;
    
    /**
     * Creates a new damage tracking listener.
     * 
     * @param tracker The indirect death tracker to record damage events
     * @param debugger Debug logger for output
     * @param deathConfig Death configuration for master toggle check
     * @param minimumDamage Minimum damage threshold (in hearts) to record
     */
    public DamageTrackingListener(IndirectDeathTracker tracker, Debugger debugger, DeathConfig deathConfig, double minimumDamage) {
        this.tracker = tracker;
        this.debugger = debugger;
        this.deathConfig = deathConfig;
        this.minimumDamage = minimumDamage;
    }
    
    /**
     * Handles entity damage events to track potential indirect kills.
     * Uses MONITOR priority to observe final damage after other plugins.
     * 
     * @param event The entity damage event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!deathConfig.isIndirectTrackingEnabled()) {
            return;
        }
        
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        
        double damageInHearts = event.getFinalDamage() / 2.0;
        if (damageInHearts < minimumDamage) {
            if (deathConfig.isDebugEnabled() && debugger != null) {
                debugger.debug("DamageTrackingListener", String.format(
                    "Damage below threshold: %s -> %s (%.1f hearts < %.1f hearts minimum)",
                    event.getDamager().getType().name(),
                    victim.getName(),
                    damageInHearts,
                    minimumDamage
                ));
            }
            return;
        }
        
        Player damager = extractDamager(event.getDamager());
        if (damager == null) {
            if (deathConfig.isDebugEnabled() && debugger != null) {
                debugger.debug("DamageTrackingListener", String.format(
                    "No player damager found for damage to %s (damager type: %s)",
                    victim.getName(),
                    event.getDamager().getType().name()
                ));
            }
            return;
        }
        
        if (victim.getUniqueId().equals(damager.getUniqueId())) {
            if (deathConfig.isDebugEnabled() && debugger != null) {
                debugger.debug("DamageTrackingListener", String.format(
                    "Ignoring self-damage: %s",
                    victim.getName()
                ));
            }
            return;
        }
        
        DamageType damageType = classifyDamageType(event);
        tracker.recordDamage(victim, damager, damageType);
        
        if (deathConfig.isDebugEnabled() && debugger != null) {
            debugger.info("DamageTrackingListener", String.format(
                "Recorded %s damage: %s -> %s (%.1f hearts, cause: %s)",
                damageType.name(),
                damager.getName(),
                victim.getName(),
                damageInHearts,
                event.getCause().name()
            ));
        }
    }
    
    /**
     * Extracts the actual player damager from the damage source.
     * Handles direct attacks, projectiles, and explosions.
     * 
     * @param damager The entity that caused the damage
     * @return The player responsible for the damage, or null if not player-caused
     */
    private Player extractDamager(Entity damager) {
        if (damager == null) {
            return null;
        }
        
        if (damager instanceof Player) {
            return (Player) damager;
        }
        
        if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            ProjectileSource shooter = projectile.getShooter();
            if (shooter == null) {
                if (deathConfig.isDebugEnabled() && debugger != null) {
                    debugger.debug("DamageTrackingListener", "Projectile has null shooter");
                }
                return null;
            }
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        
        if (damager instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) damager;
            Entity source = tnt.getSource();
            if (source instanceof Player) {
                return (Player) source;
            }
        }
        
        return null;
    }
    
    /**
     * Classifies the type of damage based on the damage source and cause.
     * 
     * @param event The damage event
     * @return The classified damage type
     */
    private DamageType classifyDamageType(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager == null) {
            return DamageType.UNKNOWN;
        }
        
        EntityDamageEvent.DamageCause cause = event.getCause();
        
        if (damager instanceof Projectile) {
            return DamageType.PROJECTILE;
        }
        
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || 
            cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
            damager instanceof TNTPrimed) {
            return DamageType.EXPLOSION;
        }
        
        if (damager instanceof Player && 
            (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || 
             cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            return DamageType.MELEE;
        }
        
        // Heuristic: low damage with knockback
        if (damager instanceof Player) {
            double damage = event.getFinalDamage();
            if (damage < 4.0) {
                return DamageType.KNOCKBACK;
            }
            return DamageType.MELEE;
        }
        
        return DamageType.UNKNOWN;
    }
}
