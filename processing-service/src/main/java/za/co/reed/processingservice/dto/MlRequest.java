package za.co.reed.processingservice.dto;

public record MlRequest(
        String merchantName,
        String merchantMcc,
        double amount,
        String currency
) {}
