package io.darqlab.papyrus.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingModelInfoServiceTest {

    @Test
    void resolvesVoyageDatabaseName() {
        EmbeddingModelInfoService service = new EmbeddingModelInfoService("voyage-3-lite");

        EmbeddingModelInfo info = service.describe("papyrus_voyage3lite");

        assertThat(info.model()).isEqualTo("voyage-3-lite");
        assertThat(info.dimensions()).isEqualTo(512);
        assertThat(info.database()).isEqualTo("papyrus_voyage3lite");
        assertThat(info.matched()).isTrue();
    }

    @Test
    void resolvesOpenAiDatabaseName() {
        EmbeddingModelInfoService service = new EmbeddingModelInfoService("voyage-3-lite");

        EmbeddingModelInfo info = service.describe("papyrus_openai3small");

        assertThat(info.model()).isEqualTo("text-embedding-3-small");
        assertThat(info.dimensions()).isEqualTo(1536);
        assertThat(info.matched()).isTrue();
    }

    @Test
    void resolvesNomicDatabaseName() {
        EmbeddingModelInfoService service = new EmbeddingModelInfoService("voyage-3-lite");

        EmbeddingModelInfo info = service.describe("papyrus_nomic");

        assertThat(info.model()).isEqualTo("nomic-embed-text");
        assertThat(info.dimensions()).isEqualTo(768);
        assertThat(info.matched()).isTrue();
    }

    @Test
    void fallsBackToConfiguredModelForPreReingestBaselineDatabase() {
        EmbeddingModelInfoService service = new EmbeddingModelInfoService("voyage-3-lite");

        EmbeddingModelInfo info = service.describe("papyrus");

        assertThat(info.database()).isEqualTo("papyrus");
        assertThat(info.model()).isEqualTo("voyage-3-lite");
        assertThat(info.dimensions()).isEqualTo(512);
        assertThat(info.matched()).isFalse();
    }

    @Test
    void fallsBackGracefullyForUnrecognizedDatabaseNameAndUnknownConfiguredModel() {
        EmbeddingModelInfoService service = new EmbeddingModelInfoService("some-future-model");

        EmbeddingModelInfo info = service.describe("papyrus_something_else");

        assertThat(info.database()).isEqualTo("papyrus_something_else");
        assertThat(info.model()).isEqualTo("some-future-model");
        assertThat(info.dimensions()).isNull();
        assertThat(info.matched()).isFalse();
    }
}
