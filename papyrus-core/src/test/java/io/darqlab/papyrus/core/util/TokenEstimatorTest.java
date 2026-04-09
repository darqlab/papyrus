package io.darqlab.papyrus.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEstimatorTest {

    @Test
    void estimate_null_returnsZero() {
        assertEquals(0, TokenEstimator.estimate(null));
    }

    @Test
    void estimate_blank_returnsZero() {
        assertEquals(0, TokenEstimator.estimate("   "));
    }

    @Test
    void estimate_singleWord_returnsNonZero() {
        assertTrue(TokenEstimator.estimate("hello") > 0);
    }

    @Test
    void estimate_longerText_scalesWithWordCount() {
        int short_ = TokenEstimator.estimate("one two three");
        int longer  = TokenEstimator.estimate("one two three four five six seven eight nine ten");
        assertTrue(longer > short_);
    }

    @Test
    void exceedsLimit_belowLimit_returnsFalse() {
        assertFalse(TokenEstimator.exceedsLimit("hello world", 100));
    }

    @Test
    void exceedsLimit_aboveLimit_returnsTrue() {
        // 1000 words ≈ 1333 tokens
        String manyWords = "word ".repeat(1000).trim();
        assertTrue(TokenEstimator.exceedsLimit(manyWords, 512));
    }
}
