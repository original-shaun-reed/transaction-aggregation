package za.co.reed.mocksourceservice.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ingestor")
public class IngestorProperties {
    private String webhookUrl;
    private String webhookSecret;
}
