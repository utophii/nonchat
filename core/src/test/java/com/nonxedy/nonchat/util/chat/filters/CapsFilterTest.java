package com.nonxedy.nonchat.util.chat.filters;


import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CapsFilterTest {

    @Test
    void filtersMessageWhenCapsPercentageExceedsLimit() {
        CapsFilter filter = new CapsFilter(true, 50, 4);

        assertTrue(filter.shouldFilter("THIS IS LOUD"));
    }

    @Test
    void permitsMessageAtCapsPercentageLimit() {
        CapsFilter filter = new CapsFilter(true, 50, 4);

        assertFalse(filter.shouldFilter("ABcd"));
    }

    @Test
    void ignoresShortMessagesAndDisabledFilter() {
        assertFalse(new CapsFilter(true, 0, 5).shouldFilter("HEY"));
        assertFalse(new CapsFilter(false, 0, 1).shouldFilter("HEY"));
    }
}
