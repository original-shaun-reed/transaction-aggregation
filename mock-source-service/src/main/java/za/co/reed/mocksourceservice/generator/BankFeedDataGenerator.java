package za.co.reed.mocksourceservice.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.reed.commom.enums.TransactionStatus;
import za.co.reed.mocksourceservice.dto.BankFeedRecord;

/**
 * Generates realistic bank feed transaction records using JavaFaker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankFeedDataGenerator {

    private static final List<String> ACCOUNT_IDS = List.of(
            "ACC-0001", "ACC-0002", "ACC-0003", "ACC-0004", "ACC-0005",
            "ACC-0006", "ACC-0007", "ACC-0008", "ACC-0009", "ACC-0010"
    );

    private static final List<String> CURRENCIES = List.of("ZAR", "USD", "GBP", "EUR");

    private final Faker faker = new Faker();

    /**
     * Generate a batch of raw bank feed records for a given number of accounts,
     * each with the specified number of transactions.
     */
    public List<BankFeedRecord> generate(int accounts, int transactionsPerAccount) {
        List<BankFeedRecord> records = new ArrayList<>();

        for (int a = 0; a < accounts; a++) {
            String accountId = ACCOUNT_IDS.get(a % ACCOUNT_IDS.size());

            for (int t = 0; t < transactionsPerAccount; t++) {
                records.add(buildRecord(accountId));
            }
        }

        log.debug("Generated {} bank feeds across {} accounts", records.size(), accounts);
        return records;
    }

    private BankFeedRecord buildRecord(String accountId) {
        String transactionId = "BF-" + UUID.randomUUID();

        // Jitter timestamp up to 24h in the past — simulates settlement lag
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, 86_400);
        Instant transactedAt = Instant.now().minus(jitterSeconds, ChronoUnit.SECONDS);

        BigDecimal amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(1.0, 5_000.0))
                .setScale(2, RoundingMode.HALF_UP);

        String currency = CURRENCIES.get(ThreadLocalRandom.current().nextInt(CURRENCIES.size()));

        return new BankFeedRecord(transactionId, accountId, faker.company().name(), amount, Currency.getInstance(currency),
                transactedAt, randomStatus());
    }

    private String randomStatus() {
        int r = ThreadLocalRandom.current().nextInt(10);

        // 70% settled
        if (r < 7) {
            return TransactionStatus.SETTLED.name();
        }

        // 20% pending
        if (r < 9) {
            return TransactionStatus.PENDING.name();
        }

        // 10% reversed
        return TransactionStatus.REVERSED.name();
    }
}
