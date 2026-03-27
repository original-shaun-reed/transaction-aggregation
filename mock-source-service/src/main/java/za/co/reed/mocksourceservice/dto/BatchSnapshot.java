package za.co.reed.mocksourceservice.dto;
import za.co.reed.commom.dto.NormalisedTransaction;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record BatchSnapshot(List<NormalisedTransaction> records, Instant generatedAt) {
    public static BatchSnapshot empty() {
        return new BatchSnapshot(Collections.emptyList(), Instant.now());
    }
}

