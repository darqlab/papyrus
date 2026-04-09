package io.darqlab.papyrus.core.util;

/**
 * Rough token count estimator based on word count.
 * Used for chunk size validation — not for billing or exact limits.
 *
 * Rule of thumb: 1 token ≈ 0.75 words (English text).
 */
public final class TokenEstimator {

    private static final double WORDS_PER_TOKEN = 0.75;

    private TokenEstimator() {}

    /**
     * Estimate token count from text.
     *
     * @param text the input text
     * @return estimated token count (0 for null or blank input)
     */
    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return (int) Math.ceil(words.length / WORDS_PER_TOKEN);
    }

    /**
     * Returns true if the estimated token count exceeds the given limit.
     */
    public static boolean exceedsLimit(String text, int tokenLimit) {
        return estimate(text) > tokenLimit;
    }
}
