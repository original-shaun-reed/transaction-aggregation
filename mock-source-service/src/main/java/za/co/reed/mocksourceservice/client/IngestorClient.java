package za.co.reed.mocksourceservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.mocksourceservice.properties.IngestorProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Shared HTTP client used by all three source adapters to deliver
 * NormalisedTransactions to the ingestor-service webhook endpoint.
 *
 * Signs each request with an HMAC-SHA256 signature so the ingestor
 * can verify the payload came from a trusted source.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestorClient {
    private final RestTemplate restTemplate;
    private final IngestorProperties properties;
    private final ObjectMapper objectMapper;

    public void send(NormalisedTransaction transaction) {
        try {
            // Serialize transaction to JSON for HMAC computation
            String jsonBody = objectMapper.writeValueAsString(transaction);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Transaction-Aggregator-Signature", sign(jsonBody));
            headers.set("X-Source-Type", transaction.sourceType().name());

            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<Void> response = restTemplate.exchange(properties.getWebhookUrl(), HttpMethod.POST, request, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Ingestor returned non-2xx for sourceId {}: {}", transaction.sourceId(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("HTTP call to ingestor failed for sourceId {}", transaction.sourceId(), e);
            throw new RuntimeException("Failed to send transaction to ingestor", e);
        }
    }

    /**
     * HMAC-SHA256 signature of the payload body.
     * Ingestor validates this with the same shared secret.
     */
    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }
}
