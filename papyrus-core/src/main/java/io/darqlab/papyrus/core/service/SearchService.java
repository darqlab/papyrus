package io.darqlab.papyrus.core.service;

import io.darqlab.papyrus.core.domain.SearchResult;

import java.util.List;
import java.util.UUID;

public interface SearchService {

    /**
     * Perform a semantic search over all ingested document chunks.
     *
     * @param query    natural language query string
     * @param topK     maximum number of results to return
     * @param sourceId optional — limit search to a specific document source; null searches all
     * @return ranked list of matching chunks with similarity scores
     */
    List<SearchResult> search(String query, int topK, UUID sourceId);
}
