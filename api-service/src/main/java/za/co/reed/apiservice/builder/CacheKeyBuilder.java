package za.co.reed.apiservice.builder;

import org.springframework.stereotype.Component;

/**
 * Builds composite Redis cache keys for all getKey endpoints.
 */
@Component
public class CacheKeyBuilder {
    private static final String SEP = ":";
    private static final String NONE = "NONE";

    public String getKey(String methodName, Object... params) {
        return build(methodName, params);
    }

    private String build(String prefix, Object... parts) {
        StringBuilder sb = new StringBuilder("transaction_aggregator").append(SEP).append(prefix);

        for (Object part : parts) {
            sb.append(SEP).append(part != null ? part.toString() : NONE);
        }

        return sb.toString();
    }
}
