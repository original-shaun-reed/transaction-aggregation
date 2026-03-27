package za.co.reed.processingservice.dto;

public record MlResponse (
        String categoryCode,
        double confidence
) {}
