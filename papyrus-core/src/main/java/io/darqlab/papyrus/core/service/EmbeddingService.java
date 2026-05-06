package io.darqlab.papyrus.core.service;

import java.util.List;

public interface EmbeddingService {

    List<Float> embed(String text);

    /**
     * Embed multiple texts in one call. Default implementation calls embed() in a loop;
     * providers that support native batching (Voyage AI) should override this.
     */
    default List<List<Float>> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    int getDimensions();
}
