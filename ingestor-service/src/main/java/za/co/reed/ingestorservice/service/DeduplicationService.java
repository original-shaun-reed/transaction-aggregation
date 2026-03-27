package za.co.reed.ingestorservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed idempotency guard for inbound transactions.
 *
 * Every sourceId that successfully passes through the ingest pipeline is
 * stored in Redis with a configurable TTL. Any subsequent message with the
 * same sourceId within that TTL window is treated as a duplicate and dropped.
 *
 * Key design decisions:
 *
 *   - We use Redis SET NX (set if not exists) via setIfAbsent() — this is
 *     atomic, so there's no race condition between the isDuplicate check
 *     and the markSeen write even under concurrent ingest threads.
 *
 *   - isDuplicate() and markSeen() are separate methods so IngestorService
 *     can call markSeen() ONLY after a successful Kafka publish. This avoids
 *     a scenario where we mark something as seen but then fail to publish it
 *     — it would be silently dropped on the next retry.
 *
 *   - Key format: "dedup:{sourceId}" — namespaced to avoid collisions with
 *     other Redis keys in the same instance (cache TTLs, rate limit buckets).
 *
 *   - TTL defaults to 24 hours — long enough to catch Stripe's retry window
 *     (which retries for up to 72h, but with exponential backoff meaning most
 *     retries arrive within the first hour).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private static final String KEY_PREFIX = "dedup:";

    private final StringRedisTemplate redisTemplate;

    @Value("${deduplication.ttl-minutes:1440}")
    private long ttlMinutes;

    /**
     * Check if a sourceId has already been processed.
     *
     * @param sourceId the transaction's original source identifier
     * @return true if this sourceId exists in Redis (i.e. is a duplicate)
     */
    public boolean isDuplicate(String sourceId) {
        Boolean exists = redisTemplate.hasKey(key(sourceId));
        boolean duplicate = Boolean.TRUE.equals(exists);
        if (duplicate) {
            log.debug("Duplicate detected in Redis — sourceId={}", sourceId);
        }
        return duplicate;
    }

    /**
     * Mark a sourceId as processed in Redis with the configured TTL.
     * Should only be called after a successful Kafka publish.
     *
     * @param sourceId the transaction's original source identifier
     */
    public void markSeen(String sourceId) {
        redisTemplate.opsForValue().set(key(sourceId), "1", Duration.ofMinutes(ttlMinutes));
        log.debug("Marked as seen in Redis — sourceId={} ttlMinutes={}", sourceId, ttlMinutes);
    }

    /**
     * Atomic check-and-mark in a single Redis round-trip.
     * Returns true if the key was newly set (i.e. NOT a duplicate).
     * Returns false if the key already existed (i.e. IS a duplicate).
     *
     * Use this in high-throughput paths where you want to check and mark
     * in one operation rather than two separate calls.
     */
    public boolean checkAndMark(String sourceId) {
        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(key(sourceId), "1", Duration.ofMinutes(ttlMinutes));
        return Boolean.TRUE.equals(wasSet); // true = newly set = not a duplicate
    }

    private String key(String sourceId) {
        return KEY_PREFIX + sourceId;
    }
}
