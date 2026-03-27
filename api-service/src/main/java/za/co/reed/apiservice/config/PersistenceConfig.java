package za.co.reed.apiservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "za.co.reed.persistence.repository")
@EntityScan(basePackages = "za.co.reed.persistence.entity")
public class PersistenceConfig {
    // No manual EntityManagerFactory or DataSource beans needed.
    // Spring Boot auto-configures them using application.yml.
}
