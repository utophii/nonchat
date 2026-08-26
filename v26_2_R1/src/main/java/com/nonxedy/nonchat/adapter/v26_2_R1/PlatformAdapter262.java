package com.nonxedy.nonchat.adapter.v26_2_R1;

import com.nonxedy.nonchat.adapter.AbstractBubblePlatformAdapter;
import com.nonxedy.nonchat.api.IMessageHandler;
import org.bukkit.event.Listener;

public final class PlatformAdapter262 extends AbstractBubblePlatformAdapter {
    public PlatformAdapter262() {
        super("26.2");
    }

    @Override
    protected Listener createChatListener(IMessageHandler handler) {
        return new ChatListener262(handler);
    }
}
