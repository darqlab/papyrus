package io.darqlab.papyrus.ingestor;

import io.darqlab.papyrus.ingestor.config.IngestorProperties;
import io.darqlab.papyrus.ingestor.db.DatabaseBootstrapper;
import io.darqlab.papyrus.ingestor.model.TargetModelSpec;
import io.darqlab.papyrus.ingestor.reingest.ReingestOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Entry point for {@code c-ingestor} (ADR-009: On-Demand Ingestion Container with
 * Blue-Green Per-Model Databases). This is a **batch CLI**, not an HTTP server: it runs
 * its work once in {@link #run(String...)} and exits, so it can be started on-demand by
 * an operator (compose profile / manual {@code up}) and torn down when done.
 *
 * <p>Scope: P1.1 + P1.2 (module skeleton + target-model resolution + per-model database
 * bootstrap: create-if-absent, apply schema) plus P1.3 (the reingest-from-archive loop,
 * delegated to {@link ReingestOrchestrator}).
 *
 * <p>Excludes {@link DataSourceAutoConfiguration} / {@link HibernateJpaAutoConfiguration}:
 * this tool talks to <em>two different</em> Postgres databases per run (the admin/catalog
 * database, then the freshly-created target database — see {@link DatabaseBootstrapper}),
 * which doesn't fit Spring Boot's single-autoconfigured-DataSource model. Connections are
 * opened directly via JDBC instead of a Spring-managed {@code DataSource}/JPA, so no
 * {@code spring.datasource.*} properties are needed and none should be added.
 */
@SpringBootApplication(
        scanBasePackages = "io.darqlab.papyrus.ingestor",
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class}
)
@EnableConfigurationProperties(IngestorProperties.class)
public class IngestorApplication implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(IngestorApplication.class);

    private final IngestorProperties properties;
    private final TargetModelResolver resolver;
    private final DatabaseBootstrapper bootstrapper;
    private final ReingestOrchestrator orchestrator;

    private volatile int exitCode = 0;

    public IngestorApplication(IngestorProperties properties,
                                TargetModelResolver resolver,
                                DatabaseBootstrapper bootstrapper,
                                ReingestOrchestrator orchestrator) {
        this.properties = properties;
        this.resolver = resolver;
        this.bootstrapper = bootstrapper;
        this.orchestrator = orchestrator;
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(IngestorApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        int exitCode = SpringApplication.exit(context);
        System.exit(exitCode);
    }

    @Override
    public void run(String... args) {
        log.info("=== Papyrus Ingestor (c-ingestor) starting ===");
        try {
            TargetModelSpec spec = resolver.resolve(properties.targetModel());
            log.info("Target model resolved: model='{}' provider='{}' dim={} database='{}'",
                    spec.modelName(), spec.provider(), spec.dimensions(), spec.databaseName());

            bootstrapper.ensureDatabase(spec);
            bootstrapper.applySchema(spec);

            orchestrator.run(spec);

            log.info("=== Papyrus Ingestor finished successfully ===");
        } catch (Exception e) {
            log.error("=== Papyrus Ingestor failed ===", e);
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
