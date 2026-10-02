package com.nonxedy.nonchat.test;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.hook.ChatColorHook;
import com.nonxedy.nonchat.service.ConfigService;
import com.nonxedy.nonchat.util.InteractivePlaceholderManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** A public-API contract double; no ChatColor jar is required by ordinary tests. */
public final class ChatColorFixture {
    public interface PluginWithApi extends Plugin {
        Object getChatColorAPI();
    }

    public static final class TestApi {
        public final AtomicInteger calls = new AtomicInteger();
        public BiFunction<Player, Component, Component> colorizer = (player, message) -> message;
        public Component lastInput;

        public Component applyColorToComponent(Player player, Component message) {
            calls.incrementAndGet();
            lastInput = message;
            return colorizer.apply(player, message);
        }

        public void tag(String tag) {
            MiniMessage mini = MiniMessage.miniMessage();
            colorizer = (player, message) -> mini.deserialize(tag + mini.serialize(message));
        }
    }

    public final Nonchat nonchat = mock(Nonchat.class);
    public final Server server = mock(Server.class);
    public final PluginManager manager = mock(PluginManager.class);
    public final Logger logger = mock(Logger.class);
    public final ConfigService service = mock(ConfigService.class);
    public final YamlConfiguration config = new YamlConfiguration();
    public final YamlConfiguration dependencyConfig = new YamlConfiguration();
    public final PluginWithApi dependency = mock(PluginWithApi.class);
    public final TestApi api = new TestApi();
    public final Player player = mock(Player.class);
    public final ChatColorHook hook;

    public ChatColorFixture() {
        config.set("interactive-placeholders.enabled", false);
        config.set("mention-colors.enabled", false);
        when(nonchat.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(nonchat.getLogger()).thenReturn(logger);
        when(nonchat.getConfig()).thenReturn(config);
        when(nonchat.getConfigService()).thenReturn(service);
        when(nonchat.getPlaceholderManager()).thenReturn(new InteractivePlaceholderManager());
        when(service.getBoolean(anyString(), anyBoolean())).thenAnswer(invocation ->
                config.getBoolean(invocation.getArgument(0), invocation.getArgument(1)));
        when(service.getString(anyString(), anyString())).thenAnswer(invocation ->
                config.getString(invocation.getArgument(0), invocation.getArgument(1)));
        when(manager.getPlugin("nonchat")).thenReturn(nonchat);
        when(manager.getPlugin("ChatColor")).thenReturn(dependency);
        when(dependency.getName()).thenReturn("ChatColor");
        when(dependency.isEnabled()).thenReturn(true);
        when(dependency.getDescription()).thenReturn(new PluginDescriptionFile(
                "ChatColor", "test", "net.busybee.chatcolor.ChatColor"));
        when(dependency.getConfig()).thenReturn(dependencyConfig);
        when(dependency.getChatColorAPI()).thenReturn(api);
        when(player.getName()).thenReturn("Sender");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        hook = new ChatColorHook(nonchat);
        when(nonchat.getChatColorHook()).thenReturn(hook);
    }
}