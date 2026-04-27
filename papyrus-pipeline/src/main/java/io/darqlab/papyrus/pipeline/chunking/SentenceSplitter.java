package io.darqlab.papyrus.pipeline.chunking;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class SentenceSplitter {

    // Split after sentence-ending punctuation when followed by whitespace + capital letter.
    // Requires at least 3 non-punctuation chars before the period to avoid splitting on
    // abbreviations like "A.Z.", "Mr.", "PHP 2,000.00".
    private static final Pattern BOUNDARY = Pattern.compile(
            "(?<=[a-z\\d]{3,}[.!?])\\s+(?=[A-Z\"])");

    private SentenceSplitter() {}

    public static List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(BOUNDARY.split(text.trim()))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
