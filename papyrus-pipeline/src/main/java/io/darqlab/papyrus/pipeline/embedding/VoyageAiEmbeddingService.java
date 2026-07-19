package io.darqlab.papyrus.pipeline.embedding;

import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "papyrus.embedding.provider", havingValue = "voyage", matchIfMissing = true)
public class VoyageAiEmbeddingService implements EmbeddingService {

    private static final String VOYAGE_API_URL = "https://api.voyageai.com/v1/embeddings";
    private static final int DIMENSIONS = 512;

    private final RestClient restClient;
    private final String model;

    public VoyageAiEmbeddingService(PapyrusProperties properties) {
        String apiKey = properties.embedding().voyage().apiKey();
        this.model    = properties.embedding().voyage().model();

        // Dead/stalled TCP connections (e.g. a server-side reset that the client never
        // observes) otherwise block indefinitely here: a batch reingest run stalled ~9min
        // on a single embed() call with no read timeout, confirmed via thread dump parked
        // in NioSocketImpl.timedRead.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(VOYAGE_API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Float> embed(String text) {
        Map<String, Object> request = Map.of(
                "input", List.of(text),
                "model", model
        );

        Map<String, Object> response = restClient.post()
                .body(request)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        List<Double> raw = (List<Double>) data.get(0).get("embedding");
        return raw.stream().map(Double::floatValue).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        Map<String, Object> request = Map.of(
                "input", texts,
                "model", model
        );

        Map<String, Object> response = restClient.post()
                .body(request)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        return data.stream()
                .sorted(Comparator.comparingInt(d -> ((Number) d.get("index")).intValue()))
                .map(item -> (List<Double>) item.get("embedding"))
                .map(raw -> raw.stream().map(Double::floatValue).toList())
                .toList();
    }

    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }
}
