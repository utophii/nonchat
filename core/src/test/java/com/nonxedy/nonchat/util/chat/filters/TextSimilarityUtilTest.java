package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextSimilarityUtilTest {

    @Test
    void treatsCaseAndSurroundingWhitespaceAsEqual() {
        assertEquals(1.0, TextSimilarityUtil.calculateSimilarity("  Hello World ", "hello world"));
    }

    @Test
    void returnsExpectedScoreForSingleCharacterEdit() {
        assertEquals(0.8, TextSimilarityUtil.calculateSimilarity("hello", "hallo"), 0.0001);
    }

    @Test
    void returnsZeroWhenEitherValueIsNull() {
        assertEquals(0.0, TextSimilarityUtil.calculateSimilarity(null, "message"));
        assertEquals(0.0, TextSimilarityUtil.calculateSimilarity("message", null));
    }

    @Test
    void differentiatesUnrelatedMessages() {
        assertTrue(TextSimilarityUtil.calculateSimilarity("diamond", "potato") < 0.3);
    }
}
