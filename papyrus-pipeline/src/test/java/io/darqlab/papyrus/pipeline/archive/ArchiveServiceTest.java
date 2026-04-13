package io.darqlab.papyrus.pipeline.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveServiceTest {

    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "executive committee", "execomm",
            "western mindanao mission", "wmm"
    );

    @Test
    void archive_enabled_writesFilesWithContentDerivedName(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir, Map.of());
        UUID sourceId = UUID.randomUUID();
        byte[] content = "fake image bytes".getBytes(StandardCharsets.UTF_8);

        service.archive(sourceId, "IMG_001.png", content,
                "Board Meeting Minutes\nMarch 2024\nAttendees: ...");

        Path dir = tempDir.resolve(sourceId.toString());
        // Date "March 2024" won't match (no day), so falls back to today's date
        String today = LocalDate.now().toString();
        assertTrue(Files.exists(dir.resolve(today + "_board-meeting-minutes.png")));
        assertTrue(Files.exists(dir.resolve(today + "_board-meeting-minutes.txt")));
    }

    @Test
    void archive_withDateAndPage_usesFullFormat(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir, ABBREVIATIONS);
        UUID sourceId = UUID.randomUUID();
        byte[] content = "fake image bytes".getBytes(StandardCharsets.UTF_8);
        String ocrText = "1.\nMINUTES OF WESTERN MINDANAO MISSION EXECUTIVE COMMITTEE MEETING\n"
                + "Held at the Mission Headquarters\nJanuary 19, 1985";

        service.archive(sourceId, "IMG_001.png", content, ocrText);

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("1985-01-19_p1_minutes-of-wmm-execomm-meeting.png")));
        assertTrue(Files.exists(dir.resolve("1985-01-19_p1_minutes-of-wmm-execomm-meeting.txt")));
    }

    @Test
    void archive_emptyText_fallsBackToOriginalFilename(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir, Map.of());
        UUID sourceId = UUID.randomUUID();

        service.archive(sourceId, "scan_003.png", "bytes".getBytes(), "  ");

        Path dir = tempDir.resolve(sourceId.toString());
        String today = LocalDate.now().toString();
        assertTrue(Files.exists(dir.resolve(today + "_scan_003.png")));
        assertTrue(Files.exists(dir.resolve(today + "_scan_003.txt")));
    }

    @Test
    void archive_disabled_writesNothing(@TempDir Path tempDir) {
        ArchiveService service = new ArchiveService(false, tempDir, Map.of());
        UUID sourceId = UUID.randomUUID();

        service.archive(sourceId, "photo.png", "bytes".getBytes(), "text");

        Path dir = tempDir.resolve(sourceId.toString());
        assertFalse(Files.exists(dir));
    }

    @Test
    void archive_ioFailure_doesNotThrow(@TempDir Path tempDir) throws IOException {
        Path blocker = tempDir.resolve("blocked");
        Files.writeString(blocker, "I am a file, not a directory");
        ArchiveService service = new ArchiveService(true, blocker, Map.of());
        UUID sourceId = UUID.randomUUID();

        assertDoesNotThrow(() ->
                service.archive(sourceId, "photo.png", "bytes".getBytes(), "text"));
    }

    @Test
    void archive_pdfFile_writesWithContentName(@TempDir Path tempDir) throws IOException {
        ArchiveService service = new ArchiveService(true, tempDir, Map.of());
        UUID sourceId = UUID.randomUUID();
        byte[] content = "fake pdf bytes".getBytes(StandardCharsets.UTF_8);

        service.archive(sourceId, "document.pdf", content,
                "Resolution No. 2024-05: Budget Approval\nJune 15, 2024\nWhereas...");

        Path dir = tempDir.resolve(sourceId.toString());
        assertTrue(Files.exists(dir.resolve("2024-06-15_resolution-no-2024-05-budget-approval.pdf")));
        assertTrue(Files.exists(dir.resolve("2024-06-15_resolution-no-2024-05-budget-approval.txt")));
    }

    @Test
    void isEnabled_reflectsConfiguration() {
        assertTrue(new ArchiveService(true, Path.of("/tmp"), Map.of()).isEnabled());
        assertFalse(new ArchiveService(false, Path.of("/tmp"), Map.of()).isEnabled());
    }

    // ── extractDate unit tests ─────────────────────────────────────────────

    @Test
    void extractDate_monthDdYyyy() {
        assertEquals("1985-01-19",
                ArchiveService.extractDate("Meeting held\nJanuary 19, 1985\nAttendees..."));
    }

    @Test
    void extractDate_monthDdYyyy_noComma() {
        assertEquals("1985-01-19",
                ArchiveService.extractDate("January 19 1985"));
    }

    @Test
    void extractDate_ddMonthYyyy() {
        assertEquals("1985-01-19",
                ArchiveService.extractDate("Minutes from 19 January 1985"));
    }

    @Test
    void extractDate_isoFormat() {
        assertEquals("1985-01-19",
                ArchiveService.extractDate("Date: 1985-01-19\nContent..."));
    }

    @Test
    void extractDate_mmDdYyyy() {
        assertEquals("1985-01-19",
                ArchiveService.extractDate("Filed on 01/19/1985"));
    }

    @Test
    void extractDate_noDate_returnsNull() {
        assertNull(ArchiveService.extractDate("No date information here"));
    }

    @Test
    void extractDate_nullOrBlank_returnsNull() {
        assertNull(ArchiveService.extractDate(null));
        assertNull(ArchiveService.extractDate("  "));
    }

    // ── extractPage unit tests ─────────────────────────────────────────────

    @Test
    void extractPage_standaloneNumber() {
        assertEquals("p1", ArchiveService.extractPage("1.\nMINUTES OF MEETING"));
    }

    @Test
    void extractPage_pageN() {
        assertEquals("p42", ArchiveService.extractPage("Some text\nPage 42\nMore text"));
    }

    @Test
    void extractPage_pDotN() {
        assertEquals("p3", ArchiveService.extractPage("Header\np. 3\nContent"));
    }

    @Test
    void extractPage_dashSurrounded() {
        assertEquals("p5", ArchiveService.extractPage("Content here\n- 5 -\nMore content"));
    }

    @Test
    void extractPage_noPage_returnsNull() {
        assertNull(ArchiveService.extractPage("No page number anywhere"));
    }

    @Test
    void extractPage_nullOrBlank_returnsNull() {
        assertNull(ArchiveService.extractPage(null));
        assertNull(ArchiveService.extractPage("  "));
    }

    // ── applyAbbreviations unit tests ──────────────────────────────────────

    @Test
    void applyAbbreviations_caseInsensitive() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), ABBREVIATIONS);
        assertEquals("wmm execomm Meeting",
                service.applyAbbreviations("Western Mindanao Mission Executive Committee Meeting"));
    }

    @Test
    void applyAbbreviations_noMatch_unchanged() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), ABBREVIATIONS);
        assertEquals("Board Meeting", service.applyAbbreviations("Board Meeting"));
    }

    @Test
    void applyAbbreviations_emptyMap_unchanged() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        assertEquals("Executive Committee", service.applyAbbreviations("Executive Committee"));
    }

    // ── buildArchiveFilename unit tests ────────────────────────────────────

    @Test
    void buildArchiveFilename_fullExample() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), ABBREVIATIONS);
        String text = "1.\nMINUTES OF WESTERN MINDANAO MISSION EXECUTIVE COMMITTEE MEETING\n"
                + "Held at the Mission Headquarters\nJanuary 19, 1985";
        String result = service.buildArchiveFilename(text, "IMG_001.png");
        assertEquals("1985-01-19_p1_minutes-of-wmm-execomm-meeting", result);
    }

    @Test
    void buildArchiveFilename_noPage() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), ABBREVIATIONS);
        String text = "MINUTES OF WESTERN MINDANAO MISSION EXECUTIVE COMMITTEE MEETING\n"
                + "January 19, 1985";
        String result = service.buildArchiveFilename(text, "IMG_001.png");
        assertEquals("1985-01-19_minutes-of-wmm-execomm-meeting", result);
    }

    @Test
    void buildArchiveFilename_noDate_usesToday() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        String text = "Board Meeting Minutes\nAttendees: ...";
        String result = service.buildArchiveFilename(text, "scan.png");
        assertTrue(result.startsWith(LocalDate.now().toString()));
        assertTrue(result.contains("board-meeting-minutes"));
    }

    @Test
    void buildArchiveFilename_noText_fallsBackToFilename() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        String result = service.buildArchiveFilename("  ", "IMG_001.png");
        assertEquals(LocalDate.now() + "_IMG_001", result);
    }

    // ── slugify unit tests ─────────────────────────────────────────────────

    @Test
    void slugify_normalText_producesCleanSlug() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        assertEquals("board-meeting-minutes",
                service.slugify("Board Meeting Minutes\nMore text...", "scan.png"));
    }

    @Test
    void slugify_specialCharacters_stripped() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        assertEquals("resolution-no-2024-05-budget",
                service.slugify("Resolution No. 2024-05: Budget!!!", "doc.pdf"));
    }

    @Test
    void slugify_shortText_fallsBackToFilename() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        assertEquals("scan_001",
                service.slugify("Hi", "scan_001.png"));
    }

    @Test
    void slugify_emptyText_fallsBackToFilename() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        assertEquals("photo",
                service.slugify("", "photo.jpg"));
    }

    @Test
    void slugify_longText_truncatesAtWordBoundary() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        String longTitle = "This is a very long document title that keeps going and going and really should be truncated at some reasonable point";
        String slug = service.slugify(longTitle, "doc.pdf");
        assertTrue(slug.length() <= 80, "Slug too long: " + slug.length());
        assertFalse(slug.endsWith("-"), "Slug should not end with dash");
    }

    @Test
    void slugify_blankLines_usesFirstNonBlankLine() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), Map.of());
        assertEquals("actual-content-here",
                service.slugify("\n\n  \n  Actual Content Here\nMore...", "scan.png"));
    }

    @Test
    void slugify_withAbbreviations_appliesThem() {
        ArchiveService service = new ArchiveService(false, Path.of("/tmp"), ABBREVIATIONS);
        assertEquals("wmm-execomm-meeting",
                service.slugify("Western Mindanao Mission Executive Committee Meeting", "scan.png"));
    }
}
