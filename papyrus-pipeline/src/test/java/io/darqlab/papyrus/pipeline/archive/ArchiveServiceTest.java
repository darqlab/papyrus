package io.darqlab.papyrus.pipeline.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveServiceTest {

    @Test
    void archive_enabled_writesFilesWithContentDerivedName(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir);
        UUID sourceId = UUID.randomUUID();
        byte[] content = "fake image bytes".getBytes(StandardCharsets.UTF_8);

        service.archive(sourceId, "IMG_001.png", content,
                "Board Meeting Minutes\nMarch 2024\nAttendees: ...");

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("board-meeting-minutes.png")));
        assertTrue(Files.exists(dir.resolve("board-meeting-minutes.txt")));
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("board-meeting-minutes.png")));
        assertEquals("Board Meeting Minutes\nMarch 2024\nAttendees: ...",
                Files.readString(dir.resolve("board-meeting-minutes.txt")));
    }

    @Test
    void archive_emptyText_fallsBackToOriginalFilename(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir);
        UUID sourceId = UUID.randomUUID();

        service.archive(sourceId, "scan_003.png", "bytes".getBytes(), "  ");

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("scan_003.png")));
        assertTrue(Files.exists(dir.resolve("scan_003.txt")));
    }

    @Test
    void archive_disabled_writesNothing(@TempDir Path tempDir) {
        ArchiveService service = new ArchiveService(false, tempDir);
        UUID sourceId = UUID.randomUUID();

        service.archive(sourceId, "photo.png", "bytes".getBytes(), "text");

        Path dir = tempDir.resolve(sourceId.toString());
        assertFalse(Files.exists(dir));
    }

    @Test
    void archive_ioFailure_doesNotThrow(@TempDir Path tempDir) throws IOException {
        // Create a regular file where the service expects to create a directory
        Path blocker = tempDir.resolve("blocked");
        Files.writeString(blocker, "I am a file, not a directory");
        ArchiveService service = new ArchiveService(true, blocker);
        UUID sourceId = UUID.randomUUID();

        // Should not throw — failures are logged, not propagated
        assertDoesNotThrow(() ->
                service.archive(sourceId, "photo.png", "bytes".getBytes(), "text"));
    }

    @Test
    void archive_pdfFile_writesWithContentName(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir);
        UUID sourceId = UUID.randomUUID();
        byte[] content = "fake pdf bytes".getBytes(StandardCharsets.UTF_8);

        service.archive(sourceId, "document.pdf", content,
                "Resolution No. 2024-05: Budget Approval\nWhereas...");

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("resolution-no-2024-05-budget-approval.pdf")));
        assertTrue(Files.exists(dir.resolve("resolution-no-2024-05-budget-approval.txt")));
    }

    @Test
    void isEnabled_reflectsConfiguration() {
        assertTrue(new ArchiveService(true, Path.of("/tmp")).isEnabled());
        assertFalse(new ArchiveService(false, Path.of("/tmp")).isEnabled());
    }

    // ── slugify unit tests ──────────────────────────────────────────────────

    @Test
    void slugify_normalText_producesCleanSlug() {
        assertEquals("board-meeting-minutes",
                ArchiveService.slugify("Board Meeting Minutes\nMore text...", "scan.png"));
    }

    @Test
    void slugify_specialCharacters_stripped() {
        assertEquals("resolution-no-2024-05-budget",
                ArchiveService.slugify("Resolution No. 2024-05: Budget!!!", "doc.pdf"));
    }

    @Test
    void slugify_shortText_fallsBackToFilename() {
        assertEquals("scan_001",
                ArchiveService.slugify("Hi", "scan_001.png"));
    }

    @Test
    void slugify_emptyText_fallsBackToFilename() {
        assertEquals("photo",
                ArchiveService.slugify("", "photo.jpg"));
    }

    @Test
    void slugify_longText_truncatesAtWordBoundary() {
        String longTitle = "This is a very long document title that keeps going and going and really should be truncated at some reasonable point";
        String slug = ArchiveService.slugify(longTitle, "doc.pdf");
        assertTrue(slug.length() <= 80, "Slug too long: " + slug.length());
        assertFalse(slug.endsWith("-"), "Slug should not end with dash");
    }

    @Test
    void slugify_blankLines_usesFirstNonBlankLine() {
        assertEquals("actual-content-here",
                ArchiveService.slugify("\n\n  \n  Actual Content Here\nMore...", "scan.png"));
    }
}
