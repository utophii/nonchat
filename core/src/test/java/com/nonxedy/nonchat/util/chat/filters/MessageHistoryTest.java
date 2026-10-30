package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nonxedy.nonchat.util.chat.filters.MessageHistory.MessageEntry;

class MessageHistoryTest {

    @Test
    void includesOnlyMessagesWithinTimeWindow() {
        long now = System.currentTimeMillis();
        MessageHistory history = new MessageHistory(UUID.randomUUID());
        history.addMessage("old", now - 15_000L);
        history.addMessage("recent", now - 3_000L);
        history.addMessage("newest", now);

        List<MessageEntry> recent = history.getMessagesInTimeWindow(10);

        assertEquals(2, recent.size());
        assertEquals(2, history.getMessageCountInTimeWindow(10));
        assertEquals("recent", recent.get(0).getMessage());
        assertEquals("newest", recent.get(1).getMessage());
    }

    @Test
    void cleanupRemovesExpiredMessages() {
        long now = System.currentTimeMillis();
        MessageHistory history = new MessageHistory(UUID.randomUUID());
        history.addMessage("expired", now - 30_000L);
        history.addMessage("kept", now - 2_000L);
        history.addMessage("fresh", now);

        history.cleanupOldMessages(10);

        assertEquals(2, history.getMessages().size());
        assertEquals("kept", history.getMessages().get(0).getMessage());
        assertEquals("fresh", history.getMessages().get(1).getMessage());
    }

    @Test
    void zeroSecondWindowExcludesPastMessages() {
        long now = System.currentTimeMillis();
        MessageHistory history = new MessageHistory(UUID.randomUUID());
        history.addMessage("past", now - 1_000L);
        history.addMessage("future", now + 60_000L);

        List<MessageEntry> inWindow = history.getMessagesInTimeWindow(0);

        assertEquals(1, inWindow.size());
        assertEquals("future", inWindow.get(0).getMessage());
    }

    @Test
    void emptyHistoryReturnsNoMessages() {
        MessageHistory history = new MessageHistory(UUID.randomUUID());

        assertTrue(history.getMessagesInTimeWindow(30).isEmpty());
        assertEquals(0, history.getMessageCountInTimeWindow(30));
    }
}
