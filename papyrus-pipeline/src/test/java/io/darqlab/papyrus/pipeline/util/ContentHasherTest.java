package io.darqlab.papyrus.pipeline.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ContentHasherTest {

    @Test
    void sha256_knownInput_returnsExpectedHash() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        String expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        assertEquals(expected, ContentHasher.sha256(input));
    }

    @Test
    void sha256_emptyBytes_returnsHash() {
        String result = ContentHasher.sha256(new byte[0]);
        assertNotNull(result);
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]{64}"), "Expected 64-char lowercase hex string");
    }

    @Test
    void sha256_sameInput_returnsSameHash() {
        byte[] input = "deterministic input".getBytes(StandardCharsets.UTF_8);
        assertEquals(ContentHasher.sha256(input), ContentHasher.sha256(input));
    }

    @Test
    void sha256_differentInput_returnsDifferentHash() {
        byte[] a = "foo".getBytes(StandardCharsets.UTF_8);
        byte[] b = "bar".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(ContentHasher.sha256(a), ContentHasher.sha256(b));
    }

    @Test
    void sha256_returnsLowercase64CharHex() {
        byte[] input = "Papyrus document intelligence".getBytes(StandardCharsets.UTF_8);
        String result = ContentHasher.sha256(input);
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]{64}"),
                "Hash must be 64 lowercase hex characters, got: " + result);
    }
}
