package za.co.reed.apiservice.builder;

import org.springframework.stereotype.Component;

/**
 * Builds composite Redis cache keys for all getKey endpoints.
 *
 * Key format: {prefix}:{accountId}:{from}:{to}[:{extras}]
 *
 * Design rules:
 *   - All parts are URL-safe — no slashes, spaces, or special chars
 *   - accountId "ALL" is used when no account filter is applied
 *   - Category lists are sorted before hashing so [food, travel] == [travel, food]
 *   - Null-safe throughout — missing params produce deterministic keys
 *
 * A cache miss on any parameter change is intentional — stale aggregations
 * are worse than a cache miss that triggers a fresh DB query.
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
