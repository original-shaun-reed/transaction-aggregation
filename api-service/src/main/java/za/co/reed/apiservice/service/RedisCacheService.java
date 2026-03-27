package za.co.reed.apiservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Generic Redis cache service used by getKey endpoint services.
 *
 * Serialises values to JSON strings for Redis storage. Deserialisation
 * uses the target class type so Spring's ObjectMapper handles polymorphism.
 *
 * Cache-aside pattern:
 *   1. Check Redis — return if hit (includes cached=true in meta)
 *   2. On miss: run DB query, cache result, return result
 *
 * All cache failures are silent — a Redis outage degrades to uncached
 * responses rather than a service outage. Cache errors are logged as WARN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get a cached value by key, deserialised to the given type.
     *
     * @return Optional.of(value) on hit, Optional.empty() on miss or error
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        return get(key, objectMapper.getTypeFactory().constructType(type));
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        return get(key, objectMapper.getTypeFactory().constructType(type));
    }

    private <T> Optional<T> get(String key, JavaType javaType) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, javaType));
        } catch (Exception e) {
            log.warn("Cache read failed — key={} reason={}", key, e.getMessage());
            return Optional.empty();
        }
    }


    /**
     * Cache a value with a TTL.
     *
     * @param key   the cache key (from CacheKeyBuilder)
     * @param value the object to serialise and cache
     * @param ttl   time-to-live
     */
    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
            log.debug("Cached — key={} ttl={}", key, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Cache write failed (serialisation) — key={} reason={}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("Cache write failed — key={} reason={}", key, e.getMessage());
        }
    }

    /**
     * Evict a specific cache entry.
     */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Cache evict failed — key={}", key);
        }
    }
}
