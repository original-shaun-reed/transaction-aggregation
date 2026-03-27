package za.co.reed.apiservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Springdoc OpenAPI configuration.
 *
 * Declares the JWT bearer security scheme globally so every endpoint
 * in the Swagger UI shows the padlock icon and accepts a token via
 * the "Authorize" button — no per-endpoint annotation needed.
 *
 * Swagger UI availability is controlled by springdoc.swagger-ui.enabled
 * in application.yml: true in dev, false in prod.
 * The /v3/api-docs JSON remains available in prod for internal tooling.
 */
@Configuration
@Profile(value = {"dev"})
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${spring.application.name:transaction-aggregator}")
    private String appName;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transaction Aggregator API")
                        .description("""
                                Aggregates financial transaction data from bank feeds,
                                payment processors, and card networks. Provides extensive
                                endpoints for retrieving, comparing, and exporting aggregated
                                transaction information.
                                """)
                        .version("v1")
                        .contact(new Contact()
                                .name("Shaun Reed")
                                .email("shaun.reed@hotmail.com")))

                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Development"),
                        new Server().url("https://api.trasaction.aggregator.co.za").description("Production")
                ))

                // Global JWT security scheme
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token here. Obtain one from /auth/token.")))

                // Apply JWT requirement to all endpoints by default
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
