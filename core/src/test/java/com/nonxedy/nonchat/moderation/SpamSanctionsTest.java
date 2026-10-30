package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.util.chat.filters.SpamDetector;

class SpamSanctionsTest {

    @Test
    void notifyStaffWithoutBlockLetsMessageThrough() {
        ModerationConfig config = mock(ModerationConfig.class);
        when(config.isAntiSpamEnabled()).thenReturn(true);
        when(config.isAntiSpamRepetitiveEnabled()).thenReturn(true);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(1);
        when(config.getAntiSpamRepetitiveTimeWindow()).thenReturn(60);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alice");

        // A rule with 'block: false' and a 'notify-staff' tier only reports the spam
        ViolationRule rule = new ViolationRule(ViolationType.SPAM_REPETITIVE, null, null, null, false, false,
                List.of(new SanctionTier(1, Duration.ofMinutes(5), List.of("notify-staff"))));
        SanctionsManager manager = new SanctionsManager(
                List.of(new SpamDetector(config)), type -> rule, line -> { }, null);

        manager.process(player, "buy now");
        assertFalse(manager.process(player, "buy now"));
        assertEquals(1, manager.getStrikes(id).size());
    }
}
