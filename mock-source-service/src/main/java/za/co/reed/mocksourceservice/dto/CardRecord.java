package za.co.reed.mocksourceservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Represents an ISO 8583 clearing/settlement record as delivered by Visa/Mastercard.
 *
 * Key fields that differ from other sources:
 * <ul>
 *   <li><b>authCode</b> — Authorisation code provided by the issuer</li>
 *   <li><b>clearingStatus</b> — Transaction lifecycle state: AUTH | CLEARED | REVERSED</li>
 *   <li><b>mcc</b> — ISO 18245 Merchant Category Code (raw input for categorisation engine)</li>
 *   <li><b>panLastFour</b> — Last 4 digits of the card, used as an accountId proxy</li>
 * </ul>
 */
public record CardRecord(
        String recordId,

        // Card and authorisation details
        String authCode,
        String panLastFour,          // Last 4 digits of card, used as accountId proxy

        // Merchant information
        String merchantName,
        String merchantCity,
        String mcc,                  // ISO 18245 MCC — e.g. "5411" = Grocery Stores

        // Transaction amounts
        BigDecimal amount,
        Currency currency,

        // Timestamps
        Instant authorisedAt,
        Instant clearedAt,           // Only populated if status is CLEARED or REVERSED

        // Lifecycle status
        String clearingStatus        // AUTH | CLEARED | REVERSED
) {}
