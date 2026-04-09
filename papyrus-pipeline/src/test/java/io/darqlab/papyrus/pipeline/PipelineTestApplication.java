package io.darqlab.papyrus.pipeline;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Minimal Spring Boot application used as the context root for pipeline module tests.
 * Not deployed — exists only to satisfy @SpringBootTest's application context requirement.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("io.darqlab.papyrus.pipeline.config")
public class PipelineTestApplication {}
