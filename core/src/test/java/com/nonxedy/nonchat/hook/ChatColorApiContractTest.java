package com.nonxedy.nonchat.hook;

import static com.nonxedy.nonchat.test.ComponentAssertions.assertColor;
import static com.nonxedy.nonchat.test.ComponentAssertions.glyphs;
import static com.nonxedy.nonchat.test.ComponentAssertions.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import com.nonxedy.nonchat.chat.channel.BaseChannel;
import com.nonxedy.nonchat.test.ChatColorFixture;
import com.nonxedy.nonchat.util.chat.formatting.HoverTextUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Opt-in tests against a real BusyBee release jar, without a build/runtime dependency.
 * Run with -Dchatcolor.test.jar=/absolute/path/to/ChatColor.jar. Only Bukkit and
 * ChatColor's data/config managers are mocked; the API and color/pattern utilities
 * come unmodified from the supplied jar.
 */
class ChatColorApiContractTest {
    private static URLClassLoader loader;
    private ChatColorFixture f;
    private MockedStatic<Bukkit> bukkit;
    private Object data;
    private final Map<String, Object> colors = new HashMap<>();
    private final Map<String, Object> gradients = new HashMap<>();
    private final Map<String, Object> patterns = new HashMap<>();
    private final Map<String, String> groupDefaults = new LinkedHashMap<>();
    private final Set<String> permissions = new HashSet<>();
    private String defaultColor = "NONE";

    @BeforeAll
    static void loadJar() throws Exception {
        String jar = System.getProperty("chatcolor.test.jar");
        assumeTrue(jar != null && Files.isRegularFile(Path.of(jar)), "Optional: supply -Dchatcolor.test.jar");
        loader = new URLClassLoader(new java.net.URL[] {Path.of(jar).toUri().toURL()},
                ChatColorApiContractTest.class.getClassLoader());
    }

    @AfterAll
    static void closeLoader() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        f = new ChatColorFixture();
        bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(f.manager);
        when(f.player.hasPermission(anyString())).thenAnswer(invocation -> permissions.contains(invocation.getArgument(0)));
        data = type("data.PlayerColorData").getConstructor().newInstance();
        type("data.PlayerColorData").getMethod("reset").invoke(data);

        Object config = mock(type("config.ConfigManager"), invocation -> switch (invocation.getMethod().getName()) {
            case "getDefaultColor" -> defaultColor;
            case "getGroupDefaults" -> groupDefaults;
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        Object colorManager = mock(type("config.ColorManager"), invocation -> switch (invocation.getMethod().getName()) {
            case "getColor" -> colors.get(invocation.getArgument(0));
            case "getGradient" -> gradients.get(invocation.getArgument(0));
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        Object patternManager = mock(type("config.PatternManager"), invocation ->
                invocation.getMethod().getName().equals("getPattern") ? patterns.get(invocation.getArgument(0))
                        : Answers.RETURNS_DEFAULTS.answer(invocation));
        Object dataManager = mock(type("data.PlayerDataManager"), invocation ->
                invocation.getMethod().getName().equals("getData") ? data : Answers.RETURNS_DEFAULTS.answer(invocation));
        Object upstream = mock(type("ChatColor"), invocation -> switch (invocation.getMethod().getName()) {
            case "getConfigManager" -> config;
            case "getColorManager" -> colorManager;
            case "getPatternManager" -> patternManager;
            case "getPlayerDataManager" -> dataManager;
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        Object api = type("api.ChatColorAPI").getConstructor(type("ChatColor")).newInstance(upstream);
        when(f.dependency.getChatColorAPI()).thenReturn(api);
        f.hook.refresh();
        assertTrue(f.hook.isEnabled(), "The supplied jar must implement BusyBee's public component API");
    }

    @AfterEach
    void tearDown() {
        if (bukkit != null) {
            bukkit.close();
        }
    }

    private static Class<?> type(String suffix) throws ClassNotFoundException {
        return loader.loadClass("net.busybee.chatcolor." + suffix);
    }

    private void select(String selectionType, String key, String tag) throws Exception {
        Class<?> type = type("data.PlayerColorData");
        type.getMethod("setColorType", String.class).invoke(data, selectionType);
        type.getMethod("setColorKey", String.class).invoke(data, key);
        type.getMethod("setColorTag", String.class).invoke(data, tag);
    }

    private Object entry(String entryType, String key, String tag, String permission) throws Exception {
        return type("models." + entryType).getConstructor(String.class, String.class, String.class,
                String.class, String.class).newInstance(key, key, tag, permission, "PAPER");
    }

    @Test
    void rendersSolidColorWithTheRealApiWithoutNonchatColorPermission() throws Exception {
        colors.put("red", entry("ColorEntry", "red", "<red>", "chatcolor.color.red"));
        permissions.add("chatcolor.color.red");
        select("SOLID", "red", "<red>");
        Component output = f.hook.applyColor(f.player, Component.text("hello"));
        assertColor(output, "hello", NamedTextColor.RED);
        permissions.remove("chatcolor.color.red");
        defaultColor = "<blue>";
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.BLUE);
    }

    @Test
    void rendersHexGradientWithTheRealApi() throws Exception {
        gradients.put("test", entry("GradientEntry", "test", "<gradient:#ff0000:#0000ff>", null));
        select("GRADIENT", "test", "<gradient:#ff0000:#0000ff>");
        Component output = f.hook.applyColor(f.player, Component.text("abc"));
        assertColor(output, "a", TextColor.color(0xff0000));
        assertColor(output, "c", TextColor.color(0x0000ff));
    }

    @Test
    void rendersPatternsAndHonorsPermissionRevocation() throws Exception {
        Object entry = type("models.PatternEntry").getConstructor(String.class, String.class, String.class,
                String.class, List.class).newInstance("test", "Test", "chatcolor.pattern.test", "PAPER",
                        List.of("<red>", "<blue>"));
        patterns.put("test", entry);
        permissions.add("chatcolor.pattern.test");
        select("PATTERN", "test", null);
        Component output = f.hook.applyColor(f.player, Component.text("abc"));
        assertColor(output, "a", NamedTextColor.RED);
        assertColor(output, "b", NamedTextColor.BLUE);
        assertColor(output, "c", NamedTextColor.RED);
        permissions.remove("chatcolor.pattern.test");
        defaultColor = "<green>";
        assertColor(f.hook.applyColor(f.player, Component.text("abc")), "abc", NamedTextColor.GREEN);
    }

    @Test
    void usesGroupDefaultsBeforeServerDefaultAndReset() throws Exception {
        groupDefaults.put("staff", "<gold>");
        permissions.add("chatcolor.group.staff");
        defaultColor = "<blue>";
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.GOLD);
        permissions.clear();
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.BLUE);
        defaultColor = "NONE";
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
    }

    @Test
    void seesChangedEntriesAndUsesStoredTagForDeletedEntries() throws Exception {
        colors.put("test", entry("ColorEntry", "test", "<red>", null));
        select("SOLID", "test", "<yellow>");
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.RED);
        colors.put("test", entry("ColorEntry", "test", "<green>", null));
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.GREEN);
        colors.clear();
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.YELLOW);
    }

    @Test
    void honorsCustomHexPermission() throws Exception {
        select("CUSTOM", "#12ab34", "<#12ab34>");
        permissions.add("chatcolor.set.hex");
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", TextColor.color(0x12ab34));
        permissions.clear();
        defaultColor = "<gray>";
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.GRAY);
    }

    @Test
    void preservesRichComponentsWithTheRealApi() throws Exception {
        select("SOLID", "deleted", "<red>");
        Component input = Component.text("link").decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl("https://example.com"))
                .hoverEvent(HoverEvent.showText(Component.text("hover")));
        Component output = f.hook.applyColor(f.player, input);
        assertEquals("link", plain(output));
        for (var glyph : glyphs(output)) {
            assertEquals(input.clickEvent(), glyph.style().clickEvent());
            assertEquals(input.hoverEvent(), glyph.style().hoverEvent());
            assertEquals(TextDecoration.State.TRUE, glyph.style().decoration(TextDecoration.BOLD));
        }
    }

    @Test
    void realPatternKeepsRichTextAndDoesNotTraverseHoverContents() throws Exception {
        Object entry = type("models.PatternEntry").getConstructor(String.class, String.class, String.class,
                String.class, List.class).newInstance("test", "Test", null, "PAPER", List.of("<red>", "<blue>"));
        patterns.put("test", entry);
        select("PATTERN", "test", null);
        Component input = Component.text("abc").decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl("https://example.com"))
                .hoverEvent(HoverEvent.showText(Component.text("unchanged hover", NamedTextColor.GREEN)));
        Component output = f.hook.applyColor(f.player, input);
        assertColor(output, "a", NamedTextColor.RED);
        assertColor(output, "b", NamedTextColor.BLUE);
        for (var glyph : glyphs(output)) {
            assertEquals(input.clickEvent(), glyph.style().clickEvent());
            assertEquals(input.hoverEvent(), glyph.style().hoverEvent());
            assertEquals(TextDecoration.State.TRUE, glyph.style().decoration(TextDecoration.BOLD));
        }
    }

    @Test
    void realGradientSurvivesSpanningFormatGradientAndMentionHighlight() throws Exception {
        select("GRADIENT", "deleted", "<gradient:#ff0000:#0000ff>");
        f.config.set("mention-colors.enabled", true);
        f.config.set("mention-colors.color", "&#FFAFFB");
        HoverTextUtil hover = mock(HoverTextUtil.class);
        when(hover.createHoverableText("Sender", f.player)).thenReturn(Component.text("Sender"));
        BaseChannel channel = new BaseChannel("local", "Local",
                "<gradient:#00ff00:#ff00ff>Sender: {message}!</gradient>", "", "", "", 100, true, hover, 0, 1, 256);
        Component output = channel.formatMessage(f.player, "abc @Alex xyz");
        assertEquals("Sender: abc @Alex xyz!", plain(output));
        assertColor(output, "a", TextColor.color(0xff0000));
        assertColor(output, "z", TextColor.color(0x0000ff));
        assertColor(output, "@Alex", TextColor.color(0xffaffb));
        assertColor(output, "S", TextColor.color(0x00ff00));
        assertColor(output, "!", TextColor.color(0xff00ff));
    }

    @Test
    void channelFormattingDoesNotOverrideRealApiSelectionWithWhite() throws Exception {
        select("SOLID", "deleted", "<red>");
        HoverTextUtil hover = mock(HoverTextUtil.class);
        when(hover.createHoverableText("Sender", f.player)).thenReturn(Component.text("Sender"));
        BaseChannel channel = new BaseChannel("local", "Local", "&aSender &f{message}&6!", "", "", "", 100,
                true, hover, 0, 1, 256);
        Component output = channel.formatMessage(f.player, "hello");
        assertColor(output, "Sender", NamedTextColor.GREEN);
        assertColor(output, "hello", NamedTextColor.RED);
        assertColor(output, "!", NamedTextColor.GOLD);
    }
}