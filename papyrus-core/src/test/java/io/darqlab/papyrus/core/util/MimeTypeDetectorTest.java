package io.darqlab.papyrus.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class MimeTypeDetectorTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "document.pdf,  application/pdf",
            "report.docx,   application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "data.xlsx,     application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "slides.pptx,   application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "page.html,     text/html",
            "page.htm,      text/html",
            "notes.txt,     text/plain",
            "readme.md,     text/markdown",
            "export.csv,    text/csv",
            "book.epub,     application/epub+zip",
            "photo.jpg,     image/jpeg",
            "photo.jpeg,    image/jpeg",
            "scan.png,      image/png",
            "scan.tiff,     image/tiff",
            "REPORT.PDF,    application/pdf",
    })
    void detect_knownExtensions(String filename, String expectedMime) {
        assertEquals(expectedMime.trim(), MimeTypeDetector.detect(filename.trim()));
    }

    @Test
    void detect_unknownExtension_returnsOctetStream() {
        assertEquals("application/octet-stream", MimeTypeDetector.detect("file.xyz"));
    }

    @Test
    void detect_noExtension_returnsOctetStream() {
        assertEquals("application/octet-stream", MimeTypeDetector.detect("Makefile"));
    }

    @Test
    void detect_null_returnsOctetStream() {
        assertEquals("application/octet-stream", MimeTypeDetector.detect(null));
    }

    @Test
    void detect_blank_returnsOctetStream() {
        assertEquals("application/octet-stream", MimeTypeDetector.detect("  "));
    }

    @Test
    void detect_fullPath_usesFilenameOnly() {
        assertEquals("application/pdf", MimeTypeDetector.detect("/home/user/docs/report.pdf"));
    }

    @Test
    void isSupported_knownType_returnsTrue() {
        assertTrue(MimeTypeDetector.isSupported("application/pdf"));
    }

    @Test
    void isSupported_unknownType_returnsFalse() {
        assertFalse(MimeTypeDetector.isSupported("application/octet-stream"));
    }
}
