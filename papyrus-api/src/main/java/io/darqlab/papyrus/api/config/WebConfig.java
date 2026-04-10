package io.darqlab.papyrus.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/chat.html");
        registry.addViewController("/chat").setViewName("forward:/chat.html");
        registry.addViewController("/ingest").setViewName("forward:/ingest.html");
        registry.addViewController("/documents").setViewName("forward:/documents.html");
    }
}
