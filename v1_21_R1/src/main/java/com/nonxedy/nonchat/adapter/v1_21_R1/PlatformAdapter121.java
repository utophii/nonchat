package com.nonxedy.nonchat.adapter.v1_21_R1;

import com.nonxedy.nonchat.adapter.AbstractBubblePlatformAdapter;
import com.nonxedy.nonchat.api.IMessageHandler;
import org.bukkit.event.Listener;

public final class PlatformAdapter121 extends AbstractBubblePlatformAdapter {
    public PlatformAdapter121() {
        super("1.21.6");
    }

    /**
     * The 1.21 line is supported starting from 1.21.6 only.
     * Accepts "1.21.6", "1.21.11-R0.1-SNAPSHOT", etc.;
     * rejects 1.21.0-1.21.5 and the 26.x line (handled by newer adapters).
     */
    @Override
    public boolean supports(String bukkitVersion) {
        if (bukkitVersion == null) return false;

        // "1.21.11-R0.1-SNAPSHOT" → "1.21.11"
        String serverVer = bukkitVersion.split("-")[0];
        String[] parts = serverVer.split("\\.");
        if (parts.length != 3 || !"1".equals(parts[0]) || !"21".equals(parts[1])) {
            return false;
        }

        int patch;
        try {
            patch = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        return patch >= 6;
    }

    @Override
    protected Listener createChatListener(IMessageHandler handler) {
        return new ChatListener121(handler);
    }
}
