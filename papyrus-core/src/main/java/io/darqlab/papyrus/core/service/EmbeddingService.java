package io.darqlab.papyrus.core.service;

import java.util.List;

public interface EmbeddingService {

    /**
     * Embed a single text string into a float vector.
     */
    List<Float> embed(String text);

    /**
     * The number of dimensions this provider's vectors have.
     * Voyage AI voyage-3-lite = 1024; Ollama nomic-embed-text = 768.
     */
    int getDimensions();
}
