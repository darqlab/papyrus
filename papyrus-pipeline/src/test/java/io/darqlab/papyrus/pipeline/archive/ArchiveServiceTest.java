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
    void archive_enabled_writesOriginalFileAndText(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir);
        UUID sourceId = UUID.randomUUID();
        byte[] content = "fake image bytes".getBytes(StandardCharsets.UTF_8);

        service.archive(sourceId, "photo.png", content, "Extracted OCR text");

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("photo.png")));
        assertTrue(Files.exists(dir.resolve("photo.txt")));
        assertArrayEquals(content, Files.readAllBytes(dir.resolve("photo.png")));
        assertEquals("Extracted OCR text", Files.readString(dir.resolve("photo.txt")));
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
    void archive_filenameWithoutExtension_createsTextFile(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir);
        UUID sourceId = UUID.randomUUID();

        service.archive(sourceId, "noext", "bytes".getBytes(), "text");

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("noext")));
        assertTrue(Files.exists(dir.resolve("noext.txt")));
    }

    @Test
    void archive_pdfFile_writesOriginalAndText(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir);
        UUID sourceId = UUID.randomUUID();
        byte[] content = "fake pdf bytes".getBytes(StandardCharsets.UTF_8);

        service.archive(sourceId, "minutes.pdf", content, "Scanned PDF text");

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("minutes.pdf")));
        assertTrue(Files.exists(dir.resolve("minutes.txt")));
        assertEquals("Scanned PDF text", Files.readString(dir.resolve("minutes.txt")));
    }

    @Test
    void isEnabled_reflectsConfiguration() {
        assertTrue(new ArchiveService(true, Path.of("/tmp")).isEnabled());
        assertFalse(new ArchiveService(false, Path.of("/tmp")).isEnabled());
    }
}
