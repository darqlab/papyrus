package io.darqlab.papyrus.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    @Test
    void normalize_null_returnsEmpty() {
        assertEquals("", TextNormalizer.normalize(null));
    }

    @Test
    void normalize_blank_returnsEmpty() {
        assertEquals("", TextNormalizer.normalize("   "));
    }

    @Test
    void normalize_windowsLineEndings_convertsToUnix() {
        assertEquals("line1\nline2", TextNormalizer.normalize("line1\r\nline2"));
    }

    @Test
    void normalize_oldMacLineEndings_convertsToUnix() {
        assertEquals("line1\nline2", TextNormalizer.normalize("line1\rline2"));
    }

    @Test
    void normalize_excessiveBlankLines_collapsedToTwo() {
        String input = "para1\n\n\n\n\npara2";
        assertEquals("para1\n\npara2", TextNormalizer.normalize(input));
    }

    @Test
    void normalize_trims_leadingAndTrailingWhitespace() {
        assertEquals("hello", TextNormalizer.normalize("  hello  "));
    }

    @Test
    void flatten_null_returnsEmpty() {
        assertEquals("", TextNormalizer.flatten(null));
    }

    @Test
    void flatten_multipleSpacesAndNewlines_collapsedToSingleSpace() {
        assertEquals("hello world foo", TextNormalizer.flatten("hello   world\n\nfoo"));
    }

    @Test
    void flatten_trims() {
        assertEquals("hello", TextNormalizer.flatten("\n  hello  \n"));
    }
}
