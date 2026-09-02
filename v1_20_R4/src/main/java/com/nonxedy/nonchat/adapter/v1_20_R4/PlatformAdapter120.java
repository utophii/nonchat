package com.nonxedy.nonchat.adapter.v1_20_R4;

import com.nonxedy.nonchat.adapter.AbstractBubblePlatformAdapter;
import com.nonxedy.nonchat.api.IMessageHandler;
import org.bukkit.event.Listener;

public final class PlatformAdapter120 extends AbstractBubblePlatformAdapter {
    public PlatformAdapter120() {
        super("1.20");
    }

    @Override
    protected Listener createChatListener(IMessageHandler handler) {
        return new ChatListener120(handler);
    }
}
