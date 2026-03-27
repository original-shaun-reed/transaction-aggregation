package za.co.reed.apiservice.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cache.ttl")
public class CacheConfigProperties {
    private int summary = 300;
    private int compare = 300;
    private int timeSeries = 300;
    private int topMerchants = 300;
    private int categories = 3600;
}