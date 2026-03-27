package za.co.reed.mocksourceservice.generator;

import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.reed.mocksourceservice.dto.CardRecord;
import za.co.reed.mocksourceservice.dto.MccEntry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates ISO 8583-inspired card network settlement records.
 */
@Slf4j
@Component
public class CardBatchGenerator {

    // Real ISO 18245 MCC codes mapped to category labels
    private static final List<MccEntry> MCC_POOL = List.of(
            new MccEntry("5411", "Grocery Stores"),
            new MccEntry("5912", "Drug Stores & Pharmacies"),
            new MccEntry("5541", "Service Stations"),
            new MccEntry("5812", "Eating Places & Restaurants"),
            new MccEntry("5311", "Department Stores"),
            new MccEntry("4111", "Transportation"),
            new MccEntry("7011", "Hotels & Motels"),
            new MccEntry("4121", "Taxicabs & Limousines"),
            new MccEntry("5999", "Miscellaneous Retail"),
            new MccEntry("7372", "Computer Programming & Data Processing"),
            new MccEntry("5045", "Computers & Peripherals"),
            new MccEntry("5961", "Catalog & Mail-Order"),
            new MccEntry("4814", "Telecommunication Services"),
            new MccEntry("7941", "Sports Clubs & Fields"),
            new MccEntry("8011", "Doctors & Physicians"),
            new MccEntry("5261", "Lawn & Garden Supply"),
            new MccEntry("5732", "Electronics Stores"),
            new MccEntry("5651", "Family Clothing Stores"),
            new MccEntry("5661", "Shoe Stores"),
            new MccEntry("5944", "Jewelry Stores")
    );

    private static final List<String> PAN_LAST_FOURS = List.of(
            "1234", "5678", "9012", "3456", "7890",
            "2345", "6789", "0123", "4567", "8901"
    );

    private static final List<String> CURRENCIES = List.of("ZAR", "USD", "GBP", "EUR");

    /**
     * Generate a batch of card network records simulating a settlement file.
     * Each call produces a fresh batch — no state between calls.
     */
    public List<CardRecord> generate(int count) {
        List<CardRecord> records = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            records.add(buildRecord());
        }

        log.debug("Generated card network batch of {} records", records.size());
        return records;
    }

    private CardRecord buildRecord() {
        final Faker faker = new Faker();

        MccEntry mcc = MCC_POOL.get(ThreadLocalRandom.current().nextInt(MCC_POOL.size()));
        String panLastFour = PAN_LAST_FOURS.get(ThreadLocalRandom.current().nextInt(PAN_LAST_FOURS.size()));
        String currency = CURRENCIES.get(ThreadLocalRandom.current().nextInt(CURRENCIES.size()));

        BigDecimal amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(1.0, 8_000.0))
                .setScale(2, RoundingMode.HALF_UP);

        // Auth time up to 48h ago; clearing time up to 24h after auth
        long authJitter = ThreadLocalRandom.current().nextLong(0, 172_800); // 48h
        Instant authorisedAt = Instant.now().minus(authJitter, ChronoUnit.SECONDS);

        String status = randomStatus();
        Instant clearedAt = status.equals("CLEARED")
                ? authorisedAt.plus(ThreadLocalRandom.current().nextLong(1, 86_400), ChronoUnit.SECONDS)
                : null;

        return new CardRecord(
                "CN-" + UUID.randomUUID(),
                generateAuthCode(),
                panLastFour,
                faker.company().name(),
                faker.address().city(),
                mcc.code(),
                amount,
                Currency.getInstance(currency),
                authorisedAt,
                clearedAt,
                status
        );
    }

    private String generateAuthCode() {
        // Auth codes are 6 alphanumeric characters — e.g. "A3F7K2"
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }

        return sb.toString();
    }

    private String randomStatus() {
        int random = ThreadLocalRandom.current().nextInt(10);
        if (random < 6) {
            return "CLEARED";
        }

        if (random < 9) {
            return "AUTH";
        }

        return "REVERSED";
    }
}
