package com.nonxedy.nonchat.hook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.chat.formatting.ChatColorRenderer;

import net.kyori.adventure.text.Component;

/**
 * Optional, native integration with BusyBee's ChatColor.
 *
 * <p>Only the documented public API is used: getChatColorAPI(), resolveActiveTag()
 * and resolveActivePattern(). For older releases without resolvers, the documented
 * applyColorToComponent(Player, Component) method is used. Reflection is confined to this
 * boundary because ChatColor does not publish a standalone Maven API artifact.
 * No ChatColor classes or dependencies are bundled in nonchat, and no player
 * data, internal listeners or PlaceholderAPI expansions are accessed.</p>
 */
public final class ChatColorHook implements Listener {
    private static final String PLUGIN_NAME = "ChatColor";
    private static final String PLUGIN_MAIN = "net.busybee.chatcolor.ChatColor";
    private final Nonchat plugin;
    private final AtomicReference<Binding> binding = new AtomicReference<>();

    public ChatColorHook(Nonchat plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refresh();
    }

    /** Rebind on nonchat reload, or when ChatColor is enabled again. */
    public void refresh() {
        binding.set(null);
        if (!isConfigured()) {
            return;
        }

        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (dependency == null || !dependency.isEnabled()) {
            return;
        }

        if (!PLUGIN_MAIN.equals(dependency.getDescription().getMain())) {
            plugin.getLogger().warning("ChatColor integration requires BusyBee's ChatColor; found "
                    + dependency.getDescription().getMain() + ". Using normal chat colors.");
            return;
        }

        try {
            Object api = dependency.getClass().getMethod("getChatColorAPI").invoke(dependency);
            if (api == null) {
                throw new IllegalStateException("getChatColorAPI() returned null");
            }
            Binding resolved = bind(dependency, api);
            binding.set(resolved);
            plugin.getLogger().info("ChatColor integration enabled ("
                    + (resolved.resolveTag() != null ? "native tag/pattern renderer" : "legacy component API") + ").");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING,
                    "Cannot bind ChatColor's public API. Using normal chat colors.", unwrap(e));
        }
    }

    private static Binding bind(Plugin dependency, Object api) throws ReflectiveOperationException {
        try {
            Method tag = api.getClass().getMethod("resolveActiveTag", Player.class);
            Method pattern = api.getClass().getMethod("resolveActivePattern", Player.class);
            Method colors = pattern.getReturnType().getMethod("getColors");
            if (tag.getReturnType() != String.class || !List.class.isAssignableFrom(colors.getReturnType())) {
                throw new IllegalStateException("ChatColor selection resolvers have incompatible return types");
            }
            return new Binding(dependency, api, null, tag, pattern, colors);
        } catch (NoSuchMethodException ignored) {
            // Older BusyBee releases did not expose the effective-selection resolvers.
            Method apply = api.getClass().getMethod("applyColorToComponent", Player.class, Component.class);
            if (!Component.class.isAssignableFrom(apply.getReturnType())) {
                throw new IllegalStateException("applyColorToComponent() does not return an Adventure Component");
            }
            return new Binding(dependency, api, apply, null, null, null);
        }
    }

    public boolean isEnabled() {
        return canApply(binding.get());
    }

    private boolean isConfigured() {
        return plugin.getConfigService().getBoolean("integrations.chatcolor.enabled", true);
    }

    private boolean canApply(Binding current) {
        return current != null && current.dependency().isEnabled() && isConfigured()
                && current.dependency().getConfig().getBoolean("settings.apply-to-message", true);
    }

    /**
     * Applies the current selection, including defaults and permission changes,
     * to the message body only. Never caches a player's selection or a result.
     * nonchat.color is deliberately not checked here: that permission controls
     * player-entered formatting, not a selection authorized by ChatColor.
     */
    public Component applyColor(Player player, Component message) {
        Binding current = binding.get();
        if (!canApply(current)) {
            return message;
        }

        try {
            if (current.resolveTag() != null) {
                Object pattern = current.resolvePattern().invoke(current.api(), player);
                if (pattern != null) {
                    Object value = current.patternColors().invoke(pattern);
                    if (!(value instanceof List<?> values)) {
                        throw new IllegalStateException("PatternEntry.getColors() did not return a list");
                    }
                    List<String> colors = new ArrayList<>(values.size());
                    for (Object color : values) {
                        if (!(color instanceof String tag)) {
                            throw new IllegalStateException("PatternEntry.getColors() contains a non-string value");
                        }
                        colors.add(tag);
                    }
                    return ChatColorRenderer.applyPattern(colors, message);
                }
                String tag = (String) current.resolveTag().invoke(current.api(), player);
                return ChatColorRenderer.applyTag(tag, message);
            }
            Object result = current.apply().invoke(current.api(), player, message);
            if (result instanceof Component component) {
                return component;
            }
            throw new IllegalStateException("applyColorToComponent() returned null or an invalid value");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Throwable cause = unwrap(e);
            // Disable only the failed binding. A stale call must not disable a
            // newer binding, and concurrent failures must not spam the console.
            if (binding.compareAndSet(current, null)) {
                plugin.getLogger().log(Level.WARNING,
                        "ChatColor API failed. Using normal chat colors until ChatColor is enabled again"
                                + " or /nonchat reload is run.", cause);
            }
            return message;
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (PLUGIN_NAME.equals(event.getPlugin().getName())) {
            refresh();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Binding current = binding.get();
        if (current != null && current.dependency() == event.getPlugin()) {
            binding.compareAndSet(current, null);
        }
    }

    public void close() {
        binding.set(null);
        HandlerList.unregisterAll(this);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation
                ? invocation.getCause() : failure;
        // Do not swallow JVM failures or other fatal errors thrown through reflection.
        if (cause instanceof Error error && !(cause instanceof LinkageError)) {
            throw error;
        }
        return cause;
    }

    private record Binding(Plugin dependency, Object api, Method apply,
                           Method resolveTag, Method resolvePattern, Method patternColors) {}
}