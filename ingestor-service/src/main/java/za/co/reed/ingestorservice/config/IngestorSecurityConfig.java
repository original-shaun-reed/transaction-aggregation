package za.co.reed.ingestorservice.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StreamUtils;
import za.co.reed.ingestorservice.config.helper.CachingRequestWrapper;
import za.co.reed.ingestorservice.config.helper.HmacSignatureFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Validates HMAC-SHA256 signatures on all inbound webhook requests.
 *
 * Every request to /api/internal/webhook/** must include a header:
 *   X-Transaction-Aggregator-Signature: sha256={hex-encoded-hmac}
 *
 * The HMAC is computed over the raw request body using the shared secret
 * configured in application.yaml (fetched from Secrets Manager in prod).
 *
 * This mirrors how Stripe validates webhooks — the secret is shared
 * out-of-band between mock-sources (IngestorClient) and this service.
 *
 * If the signature is missing or invalid, the filter returns 401 immediately
 * without forwarding the request to the controller.
 *
 * Implementation notes:
 *   - We use a CachingRequestWrapper to allow reading the body twice
 *     (once for HMAC, once for Jackson deserialisation).
 *   - MessageDigest.isEqual() is used for timing-safe comparison to
 *     prevent timing attacks that could leak secret information.
 */
@Slf4j
@Configuration
public class IngestorSecurityConfig {

    @Value("${webhook.secret}")
    private String webhookSecret;

    @Bean
    public FilterRegistrationBean<HmacSignatureFilter> hmacFilter() {
        FilterRegistrationBean<HmacSignatureFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new HmacSignatureFilter(webhookSecret));
        registration.addUrlPatterns("/api/internal/webhook/*");
        registration.setOrder(1);

        return registration;
    }
}
