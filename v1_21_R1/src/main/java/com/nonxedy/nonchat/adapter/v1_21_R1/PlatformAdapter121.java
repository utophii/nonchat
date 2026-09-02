package com.nonxedy.nonchat.adapter.v1_21_R1;

import com.nonxedy.nonchat.adapter.AbstractBubblePlatformAdapter;
import com.nonxedy.nonchat.api.IMessageHandler;
import org.bukkit.event.Listener;

public final class PlatformAdapter121 extends AbstractBubblePlatformAdapter {
    public PlatformAdapter121() {
        super("1.21");
    }

    @Override
    protected Listener createChatListener(IMessageHandler handler) {
        return new ChatListener121(handler);
    }
}
