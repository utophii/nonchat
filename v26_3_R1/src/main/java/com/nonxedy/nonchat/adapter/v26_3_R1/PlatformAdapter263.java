package com.nonxedy.nonchat.adapter.v26_3_R1;

import com.nonxedy.nonchat.adapter.AbstractBubblePlatformAdapter;
import com.nonxedy.nonchat.api.IMessageHandler;
import org.bukkit.event.Listener;

public final class PlatformAdapter263 extends AbstractBubblePlatformAdapter {
    public PlatformAdapter263() {
        super("26.3");
    }

    @Override
    protected Listener createChatListener(IMessageHandler handler) {
        return new ChatListener263(handler);
    }
}
