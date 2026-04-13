package io.darqlab.papyrus.core.domain;

public record SearchResult(
        DocumentChunk chunk,
        double score,
        String sourceFilename,
        String archiveFilename
) {}
