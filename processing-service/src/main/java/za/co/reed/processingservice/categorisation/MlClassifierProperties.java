package za.co.reed.processingservice.categorisation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "categorisation.ml-classifier")
public class MlClassifierProperties {
    private String endpoint;
    private long timeoutMs;
}
