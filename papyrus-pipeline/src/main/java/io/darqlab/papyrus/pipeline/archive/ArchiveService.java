package io.darqlab.papyrus.pipeline.archive;

import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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

    private final boolean enabled;
    private final Path basePath;

    public ArchiveService(PapyrusProperties properties) {
        PapyrusProperties.ArchiveProperties cfg =
                properties.archive() != null
                        ? properties.archive()
                        : new PapyrusProperties.ArchiveProperties(false, null);

        this.enabled  = cfg.enabled();
        this.basePath = Path.of(cfg.path() != null ? cfg.path() : DEFAULT_PATH);

        if (this.enabled) {
            log.info("Archive enabled — base path: {}", this.basePath);
        } else {
            log.info("Archive disabled");
        }
    }

    /** Package-private constructor for testing. */
    ArchiveService(boolean enabled, Path basePath) {
        this.enabled  = enabled;
        this.basePath = basePath;
    }

    public boolean isEnabled() {
        return enabled;
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
    public void archive(UUID sourceId, String filename, byte[] content, String extractedText) {
        if (!enabled) return;

        try {
            Path dir = basePath.resolve(sourceId.toString());
            Files.createDirectories(dir);

            String extension = getExtension(filename);
            String slug = slugify(extractedText, filename);

            // Write original file with content-derived name
            Files.write(dir.resolve(slug + extension), content);

            // Write extracted text alongside it
            Files.write(dir.resolve(slug + ".txt"), extractedText.getBytes(StandardCharsets.UTF_8));

            log.debug("Archived source {} as '{}' to {}", sourceId, slug, dir);

        } catch (IOException e) {
            log.warn("Failed to archive source {} ({}): {}", sourceId, filename, e.getMessage());
        }
    }

    /**
     * Derive a filesystem-safe slug from the first line of extracted text.
     * Falls back to the original filename stem if the text is empty or too short.
     *
     * <p>Examples:
     * <ul>
     *   <li>"BOARD MEETING MINUTES\nMarch 2024..." → "board-meeting-minutes"</li>
     *   <li>"Resolution No. 2024-05: Budget Approval" → "resolution-no-2024-05-budget-approval"</li>
     *   <li>"" (empty) → "scan_001" (original filename stem)</li>
     * </ul>
     */
    static String slugify(String extractedText, String fallbackFilename) {
        String source = firstLine(extractedText);

        if (source.length() < 3) {
            // Text too short to be meaningful — use original filename stem
            return stripExtension(fallbackFilename);
        }

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

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) return "";
        // Take first non-blank line
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) return trimmed;
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
