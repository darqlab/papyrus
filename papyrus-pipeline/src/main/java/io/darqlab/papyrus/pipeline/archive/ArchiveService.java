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

/**
 * Archives original uploaded files alongside their extracted text on the filesystem.
 *
 * <p>When enabled, creates a directory per source ID under the configured base path
 * and writes both the original file and a {@code .txt} file with the extracted text.
 *
 * <p>Archiving is best-effort — failures are logged but never propagate to the caller,
 * so the main ingestion pipeline is never blocked by archive I/O issues.
 */
@Service
public class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);
    private static final String DEFAULT_PATH = "/data/papyrus/archive";

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
     * <p>Creates {@code {basePath}/{sourceId}/{filename}} for the original file
     * and {@code {basePath}/{sourceId}/{stem}.txt} for the extracted text.
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

            // Write original file
            Files.write(dir.resolve(filename), content);

            // Write extracted text alongside it
            String stem = stripExtension(filename);
            Files.write(dir.resolve(stem + ".txt"), extractedText.getBytes(StandardCharsets.UTF_8));

            log.debug("Archived source {} to {}", sourceId, dir);

        } catch (IOException e) {
            log.warn("Failed to archive source {} ({}): {}", sourceId, filename, e.getMessage());
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
