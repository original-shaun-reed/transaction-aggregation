package za.co.reed.mocksourceservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.reed.commom.dto.NormalisedTransaction;
import za.co.reed.mocksourceservice.service.CardBatchService;

import java.util.List;

@RestController
@RequestMapping("/batch")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sources.card-network.enabled", havingValue = "true", matchIfMissing = true)
public class CardBatchController {
    private final CardBatchService batchService;

    @GetMapping("/latest")
    public ResponseEntity<List<NormalisedTransaction>> getLatest() {
        List<NormalisedTransaction> latest = batchService.getLatest();

        if (latest.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(latest);
    }
}
