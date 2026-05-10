package io.darqlab.papyrus.pipeline.archive;

import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Archives original uploaded files alongside their extracted text on the filesystem.
 *
 * <p>When enabled, creates a directory per source ID under the configured base path
 * and writes both the original file and a {@code .txt} file with the extracted text.
 * Filenames are derived from the extracted text content so they are human-readable.
 *
 * <p>Archiving is best-effort — failures are logged but never propagate to the caller,
 * so the main ingestion pipeline is never blocked by archive I/O issues.
 */
@Service
public class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);
    private static final String DEFAULT_PATH = "/data/papyrus/archive";
    private static final int MAX_SLUG_LENGTH = 80;
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");

    // Date patterns (first match wins)
    private static final Pattern DATE_MONTH_DD_YYYY = Pattern.compile(
            "(January|February|March|April|May|June|July|August|September|October|November|December)"
                    + "\\s+(\\d{1,2}),?\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_DD_MONTH_YYYY = Pattern.compile(
            "(\\d{1,2})\\s+(January|February|March|April|May|June|July|August|September|October|November|December)"
                    + "\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern DATE_SLASH = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

    // Page number patterns
    private static final Pattern PAGE_STANDALONE = Pattern.compile("^\\s*(\\d{1,3})\\.\\s*$", Pattern.MULTILINE);
    private static final Pattern PAGE_WORD = Pattern.compile("[Pp]age\\s+(\\d{1,3})");
    private static final Pattern PAGE_ABBREV = Pattern.compile("[Pp]\\.\\s*(\\d{1,3})");
    private static final Pattern PAGE_DASH = Pattern.compile("-\\s*(\\d{1,3})\\s*-");

    private static final Map<String, Month> MONTH_MAP;
    static {
        Map<String, Month> m = new LinkedHashMap<>();
        for (Month month : Month.values()) {
            String name = month.name().charAt(0) + month.name().substring(1).toLowerCase();
            m.put(name.toLowerCase(), month);
        }
        MONTH_MAP = Collections.unmodifiableMap(m);
    }

    private final boolean enabled;
    private final Path basePath;
    private final Map<String, String> abbreviations;

    @Autowired
    public ArchiveService(PapyrusProperties properties) {
        PapyrusProperties.ArchiveProperties cfg =
                properties.archive() != null
                        ? properties.archive()
                        : new PapyrusProperties.ArchiveProperties(false, null, null);

        this.enabled  = cfg.enabled();
        this.basePath = Path.of(cfg.path() != null ? cfg.path() : DEFAULT_PATH);
        this.abbreviations = cfg.abbreviations() != null ? cfg.abbreviations() : Collections.emptyMap();

        if (this.enabled) {
            log.info("Archive enabled - base path: {}", this.basePath);
        } else {
            log.info("Archive disabled");
        }
    }

    /** Package-private constructor for testing. */
    ArchiveService(boolean enabled, Path basePath, Map<String, String> abbreviations) {
        this.enabled  = enabled;
        this.basePath = basePath;
        this.abbreviations = abbreviations != null ? abbreviations : Collections.emptyMap();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Find the original archived file (any type) for a given source and archive filename.
     * Returns the Path if it exists, or null if not found.
     */
    public Path findOriginalFile(UUID sourceId, String archiveFilename) {
        if (archiveFilename == null || archiveFilename.isBlank()) return null;
        Path dir = basePath.resolve(sourceId.toString());
        if (!Files.exists(dir)) return null;
        try {
            return Files.list(dir)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(archiveFilename + ".") && !name.endsWith(".txt");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to list archive dir for {}: {}", sourceId, e.getMessage());
            return null;
        }
    }

    /**
     * Read the extracted text (.txt) for an archived source.
     * Returns null if the file does not exist or cannot be read.
     */
    public String readExtractedText(UUID sourceId, String archiveFilename) {
        if (archiveFilename == null || archiveFilename.isBlank()) return null;
        Path file = basePath.resolve(sourceId.toString()).resolve(archiveFilename + ".txt");
        if (!Files.exists(file)) return null;
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read extracted text for {}/{}: {}", sourceId, archiveFilename, e.getMessage());
            return null;
        }
    }

    /**
     * Archive the original file and its extracted text.
     *
     * <p>Creates a directory {@code {basePath}/{sourceId}/} and writes the original
     * file and a {@code .txt} file. Both are named with a slug derived from the
     * extracted text content so they are meaningful at a glance.
     *
     * @param sourceId      the document source ID (used as directory name)
     * @param filename      the original filename (e.g. "scan_001.png")
     * @param content       the raw file bytes
     * @param extractedText the text produced by OCR / extraction
     */
    public String archive(UUID sourceId, String filename, byte[] content, String extractedText) {
        return archive(sourceId, filename, content, extractedText, null);
    }

    public String archive(UUID sourceId, String filename, byte[] content, String extractedText, String customFilename) {
        if (!enabled) return null;

        try {
            Path dir = basePath.resolve(sourceId.toString());
            Files.createDirectories(dir);

            String extension = getExtension(filename);
            String baseName = (customFilename != null && !customFilename.isBlank())
                    ? customFilename.strip()
                    : buildArchiveFilename(extractedText, filename);

            // Write original file with content-derived name
            Files.write(dir.resolve(baseName + extension), content);

            // Write extracted text alongside it
            Files.write(dir.resolve(baseName + ".txt"), extractedText.getBytes(StandardCharsets.UTF_8));

            log.debug("Archived source {} as '{}' to {}", sourceId, baseName, dir);
            return baseName;

        } catch (IOException e) {
            log.warn("Failed to archive source {} ({}): {}", sourceId, filename, e.getMessage());
            return null;
        }
    }

    /**
     * Overwrite the extracted text file for an already-archived source.
     * Used when re-extracting from the original file to refresh annotations.
     */
    public void writeExtractedText(UUID sourceId, String archiveFilename, String extractedText) {
        if (!enabled) return;
        try {
            Path file = basePath.resolve(sourceId.toString()).resolve(archiveFilename + ".txt");
            Files.writeString(file, extractedText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to write extracted text for {}/{}: {}", sourceId, archiveFilename, e.getMessage());
        }
    }

    /**
     * Delete the archive directory for a source, including all files within it.
     * No-op if the directory does not exist or archiving is disabled.
     */
    public void deleteArchiveDir(UUID sourceId) {
        if (!enabled) return;
        Path dir = basePath.resolve(sourceId.toString());
        if (!Files.exists(dir)) return;
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException e) {
                            log.warn("Failed to delete archive entry {}: {}", p, e.getMessage());
                        }
                    });
            log.debug("Deleted archive directory for source {}", sourceId);
        } catch (IOException e) {
            log.warn("Failed to delete archive dir for source {}: {}", sourceId, e.getMessage());
        }
    }

    /**
     * Build the archive filename (without extension) from extracted text.
     *
     * <p>Format: {@code {date}_{page}_{content-slug}} with fallbacks:
     * <ul>
     *   <li>Date: extracted from text, or today's date</li>
     *   <li>Page: extracted from text, or omitted</li>
     *   <li>Slug: first line slugified with abbreviations, or original filename stem</li>
     * </ul>
     */
    public String buildArchiveFilename(String extractedText, String fallbackFilename) {
        String date = extractDate(extractedText);
        if (date == null) {
            date = LocalDate.now().toString();
        }

        String page = extractPage(extractedText);
        String slug = slugify(extractedText, fallbackFilename);

        StringBuilder sb = new StringBuilder();
        sb.append(date);
        if (page != null) {
            sb.append('_').append(page);
        }
        sb.append('_').append(slug);
        return sb.toString();
    }

    /**
     * Extract a date from OCR text. Scans for common date patterns (first match wins).
     * Returns an ISO date string (yyyy-MM-dd) or {@code null} if no date is found.
     */
    static String extractDate(String text) {
        if (text == null || text.isBlank()) return null;

        // Pattern 1: Month DD, YYYY (e.g. "January 19, 1985")
        Matcher m = DATE_MONTH_DD_YYYY.matcher(text);
        if (m.find()) {
            return formatDate(m.group(3), m.group(1), m.group(2));
        }

        // Pattern 2: DD Month YYYY (e.g. "19 January 1985")
        m = DATE_DD_MONTH_YYYY.matcher(text);
        if (m.find()) {
            return formatDate(m.group(3), m.group(2), m.group(1));
        }

        // Pattern 3: YYYY-MM-DD (ISO, e.g. "1985-01-19")
        m = DATE_ISO.matcher(text);
        if (m.find()) {
            return m.group(); // already in ISO format
        }

        // Pattern 4: MM/DD/YYYY (e.g. "01/19/1985")
        m = DATE_SLASH.matcher(text);
        if (m.find()) {
            return String.format("%s-%s-%s", m.group(3), m.group(1), m.group(2));
        }

        return null;
    }

    /**
     * Extract a page number from OCR text.
     * Returns "p1", "p42", etc. or {@code null} if no page indicator is found.
     */
    static String extractPage(String text) {
        if (text == null || text.isBlank()) return null;

        // Pattern 1: standalone "1." at start/end of line
        Matcher m = PAGE_STANDALONE.matcher(text);
        if (m.find()) {
            return "p" + m.group(1);
        }

        // Pattern 2: "Page 42"
        m = PAGE_WORD.matcher(text);
        if (m.find()) {
            return "p" + m.group(1);
        }

        // Pattern 3: "p. 3"
        m = PAGE_ABBREV.matcher(text);
        if (m.find()) {
            return "p" + m.group(1);
        }

        // Pattern 4: "- 5 -"
        m = PAGE_DASH.matcher(text);
        if (m.find()) {
            return "p" + m.group(1);
        }

        return null;
    }

    /**
     * Apply configured abbreviations to text (case-insensitive).
     * E.g. "executive committee" -> "execomm", "western mindanao mission" -> "wmm".
     */
    String applyAbbreviations(String text) {
        if (abbreviations.isEmpty() || text == null) return text;
        String result = text;
        for (Map.Entry<String, String> entry : abbreviations.entrySet()) {
            result = Pattern.compile(Pattern.quote(entry.getKey()), Pattern.CASE_INSENSITIVE)
                    .matcher(result)
                    .replaceAll(entry.getValue());
        }
        return result;
    }

    /**
     * Derive a filesystem-safe slug from the first line of extracted text.
     * Applies abbreviations before slugifying.
     * Falls back to the original filename stem if the text is empty or too short.
     */
    String slugify(String extractedText, String fallbackFilename) {
        String source = firstContentLine(extractedText);

        if (source.length() < 3) {
            return stripExtension(fallbackFilename);
        }

        // Apply abbreviations before slugifying
        source = applyAbbreviations(source);

        String slug = NON_ALPHANUM.matcher(source.toLowerCase()).replaceAll("-");

        // Trim leading/trailing dashes
        slug = slug.replaceAll("^-+|-+$", "");

        // Truncate to max length at a word boundary (dash)
        if (slug.length() > MAX_SLUG_LENGTH) {
            int cut = slug.lastIndexOf('-', MAX_SLUG_LENGTH);
            slug = cut > 10 ? slug.substring(0, cut) : slug.substring(0, MAX_SLUG_LENGTH);
        }

        return slug.isEmpty() ? stripExtension(fallbackFilename) : slug;
    }

    private static String formatDate(String year, String monthName, String day) {
        Month month = MONTH_MAP.get(monthName.toLowerCase());
        if (month == null) return null;
        return String.format("%s-%02d-%02d", year, month.getValue(), Integer.parseInt(day));
    }

    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile(
            "^\\s*\\d{1,3}\\.?\\s*$");

    private static String firstContentLine(String text) {
        if (text == null || text.isBlank()) return "";
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !PAGE_NUMBER_LINE.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return "";
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot) : "";
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
