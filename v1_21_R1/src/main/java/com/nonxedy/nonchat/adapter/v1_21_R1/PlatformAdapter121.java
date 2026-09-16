package com.nonxedy.nonchat.adapter.v1_21_R1;

import org.bukkit.event.Listener;

import com.nonxedy.nonchat.adapter.AbstractBubblePlatformAdapter;
import com.nonxedy.nonchat.api.IMessageHandler;

public final class PlatformAdapter121 extends AbstractBubblePlatformAdapter {
    public PlatformAdapter121() {
        super("1.21.6");
    }

    @Override
    public boolean supports(String bukkitVersion) {
        if (bukkitVersion == null) return false;

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
