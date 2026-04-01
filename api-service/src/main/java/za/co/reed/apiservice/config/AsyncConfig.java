package za.co.reed.apiservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool configuration for async operations in the api-service.
 *
 * Used by:
 *   - CompareService: CompletableFuture.supplyAsync() for parallel DB queries
 *
 * Pool sizing rationale:
 *   - Core pool = 4: baseline threads for parallel compare queries
 *   - Max pool = 20: burst capacity for concurrent export requests
 *   - Queue = 50: backlog before rejecting — at this depth exports are slow anyway
 *
 * Thread name prefix "api-async-" makes these visible and distinguishable
 * in thread dumps and APM tools.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:50}")
    private int queueCapacity;

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("api-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
