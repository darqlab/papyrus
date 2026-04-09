package io.darqlab.papyrus.api.config;

import io.darqlab.papyrus.extractor.FormatRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiConfig {

    @Bean
    public FormatRouter formatRouter() {
        return FormatRouter.withDefaultExtractors();
    }
}
