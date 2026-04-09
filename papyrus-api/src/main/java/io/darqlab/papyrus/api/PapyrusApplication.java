package io.darqlab.papyrus.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "io.darqlab.papyrus")
@EntityScan(basePackages = "io.darqlab.papyrus")
@EnableJpaRepositories(basePackages = "io.darqlab.papyrus")
@ConfigurationPropertiesScan(basePackages = "io.darqlab.papyrus")
public class PapyrusApplication {

    public static void main(String[] args) {
        SpringApplication.run(PapyrusApplication.class, args);
    }
}
