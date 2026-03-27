package za.co.reed.apiservice.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client rate limiting using Bucket4j token bucket algorithm.
 *
 * Each client IP gets its own Bucket with a configurable capacity and
 * refill rate. Requests that exceed the rate return 429 Too Many Requests
 * with a Retry-After header.
 *
 * In production, buckets should be backed by Redis (bucket4j-redis) so
 * rate limits are shared across api-service instances. This in-memory
 * implementation is correct for single-instance dev and testing.
 *
 * Configuration:
 *   rate-limit.capacity          — max tokens (burst capacity)
 *   rate-limit.refill-tokens     — tokens added per period
 *   rate-limit.refill-period-seconds — period length
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.capacity:100}")
    private int capacity;

    @Value("${rate-limit.refill-tokens:100}")
    private int refillTokens;

    @Value("${rate-limit.refill-period-seconds:60}")
    private int refillPeriodSeconds;

    // Per-client bucket store — keyed by IP address
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        Bucket bucket   = buckets.computeIfAbsent(clientIp, ip -> newBucket());

        if (bucket.tryConsume(1)) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded — clientIp={} path={}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.addHeader("Retry-After", String.valueOf(refillPeriodSeconds));
            response.addHeader("Content-Type", "application/json");
            response.getWriter().write("{\"error\":\"Too many requests\",\"retryAfterSeconds\":" + refillPeriodSeconds + "}");
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillTokens, Duration.ofSeconds(refillPeriodSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Respect X-Forwarded-For if behind a load balancer
        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
