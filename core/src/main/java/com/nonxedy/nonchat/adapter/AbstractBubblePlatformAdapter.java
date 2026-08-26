package com.nonxedy.nonchat.adapter;

import com.nonxedy.nonchat.api.ServiceAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public abstract class AbstractBubblePlatformAdapter extends ServiceAdapter {
    protected AbstractBubblePlatformAdapter(String supportedVersion) {
        super(supportedVersion);
    }

    @Override
    public boolean supportsChatBubbles() {
        return true;
    }

    @Override
    public List<?> spawnMultilineBubble(
        Player player,
        String message,
        Location location,
        double scale,
        double scaleX,
        double scaleY,
        double scaleZ,
        String backgroundColor
    ) {
        List<TextDisplay> displays = new ArrayList<>();
        String[] lines = message.split("\\R");
        Color parsedBackgroundColor = parseBackgroundColor(backgroundColor);

        for (int i = 0; i < lines.length; i++) {
            Location lineLocation = location.clone().add(0.0D, (lines.length - i - 1) * 0.25D, 0.0D);
            TextDisplay display = player.getWorld().spawn(lineLocation, TextDisplay.class);
            display.text(Component.text(lines[i]));
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setBillboard(Display.Billboard.CENTER);

            if (parsedBackgroundColor != null) {
                display.setBackgroundColor(parsedBackgroundColor);
            }

            float totalScale = (float) scale;
            display.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(
                    totalScale * (float) scaleX,
                    totalScale * (float) scaleY,
                    totalScale * (float) scaleZ
                ),
                new AxisAngle4f()
            ));

            displays.add(display);
        }

        return displays;
    }

    @Override
    public void updateBubblesLocation(Collection<?> bubbles, Location location) {
        if (bubbles == null || bubbles.isEmpty()) {
            return;
        }

        int total = bubbles.size();
        int index = 0;
        for (Object bubble : bubbles) {
            if (bubble instanceof TextDisplay display) {
                Location target = location.clone().add(0.0D, (total - index - 1) * 0.25D, 0.0D);
                display.teleport(target);
                index++;
            }
        }
    }

    @Override
    public void removeBubbles(Collection<?> bubbles) {
        if (bubbles == null || bubbles.isEmpty()) {
            return;
        }

        for (Object bubble : bubbles) {
            if (bubble instanceof TextDisplay display) {
                display.remove();
            }
        }
    }

    private Color parseBackgroundColor(String colorValue) {
        if (colorValue == null || colorValue.isEmpty()) {
            return null;
        }

        try {
            String hex = colorValue.startsWith("#") ? colorValue.substring(1) : colorValue;

            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                    + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
            }

            if (hex.length() == 6) {
                return Color.fromRGB(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
                );
            }

            if (hex.length() == 8) {
                return Color.fromARGB(
                    Integer.parseInt(hex.substring(6, 8), 16),
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
                );
            }
        } catch (IllegalArgumentException ignored) {
        }

        return Color.BLACK;
    }
}