package za.co.reed.mocksourceservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.reed.mocksourceservice.service.CardBatchService;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardBatchScheduler {
    private final CardBatchService batchService;

    @Scheduled(fixedDelayString = "${sources.card-network.batch-interval-ms:300000}")
    public void regenerateBatch() {
        log.info("Triggering card network batch regeneration...");
        batchService.regenerateBatch();
    }
}
