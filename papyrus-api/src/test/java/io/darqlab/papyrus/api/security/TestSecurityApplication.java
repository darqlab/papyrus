package io.darqlab.papyrus.api.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for security slice tests.
 * Deliberately omits @EnableJpaRepositories and @EntityScan so that
 * @WebMvcTest can start without a database or JPA infrastructure.
 */
@SpringBootApplication(scanBasePackages = "io.darqlab.papyrus.api")
public class TestSecurityApplication {
}
