package za.co.reed.apiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.reed.apiservice.dto.CategoryBreakdownZScore;
import za.co.reed.apiservice.dto.response.compare.Anomaly;
import za.co.reed.apiservice.dto.response.compare.CategoryBreakdown;
import za.co.reed.apiservice.enums.AnomalyReason;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

@Slf4j
@Service
public class AnomalyDetectionService {

    // Threshold for considering a z-score as anomalous
    private static final double Z_SCORE_THRESHOLD = 2.0;

    /**
     * Detects anomalies in category breakdowns using z-score analysis.
     *
     * @param categories list of category breakdowns with delta percentages
     * @return list of anomalies sorted by absolute z-score (largest first)
     */
    public List<Anomaly> detectAnomalies(List<CategoryBreakdown> categories) {
        if (categories.size() < 3) {
            // Not enough data points to compute meaningful statistics
            return List.of();
        }

        // Extract delta percentages into an array (nulls treated as 0.0)
        double[] deltas = categories.stream()
                .mapToDouble(c -> c.getDeltaPct() != null ? c.getDeltaPct().doubleValue() : 0.0)
                .toArray();

        // Compute mean and standard deviation of deltas
        double mean   = mean(deltas);
        double stddev = standardDeviation(deltas, mean);

        if (stddev == 0) {
            // No variation in data, so no anomalies can be detected
            return List.of();
        }

        // Build anomalies: compute z-scores, filter by threshold, and sort by magnitude
        return categories.stream()
                .filter(c -> c.getDeltaPct() != null) // only consider non-null deltas
                .map(c -> getCategoryBreakdownZScore(mean, stddev, c)) // compute z-score
                .filter(pair -> Math.abs(pair.getZScore()) > Z_SCORE_THRESHOLD) // keep anomalies
                .map(pair -> buildAnomaly(pair)) // convert to Anomaly object
                .sorted((a, b) -> Double.compare(
                        Math.abs(b.getZScore().doubleValue()),
                        Math.abs(a.getZScore().doubleValue()))) // sort descending by |z-score|
                .toList();
    }

    /**
     * Builds an anomaly object from a category breakdown z-score.
     *
     * @param categoryBreakdownZScore wrapper containing breakdown and z-score
     * @return Anomaly with reason, description, and rounded z-score
     */
    private Anomaly buildAnomaly(CategoryBreakdownZScore categoryBreakdownZScore) {
        CategoryBreakdown categoryBreakdown = categoryBreakdownZScore.getBreakdown();
        double zScore = categoryBreakdownZScore.getZScore();

        // Determine anomaly reason: spike if positive, drop if negative
        AnomalyReason reason = zScore > 0 ? AnomalyReason.SPIKE : AnomalyReason.DROP;

        // Generate human-readable description
        String description = getAnomalyDescription(categoryBreakdown, zScore);

        // Build anomaly object with rounded z-score
        return Anomaly.builder()
                .categoryId(categoryBreakdown.getCategoryId())
                .reason(reason)
                .zScore(BigDecimal.valueOf(zScore).round(new MathContext(3)))
                .description(description)
                .build();
    }

    /**
     * Computes the z-score for a category breakdown.
     *
     * @param mean      mean of all deltas
     * @param stddev    standard deviation of all deltas
     * @param breakdown category breakdown to evaluate
     * @return wrapper containing breakdown and its z-score
     */
    private CategoryBreakdownZScore getCategoryBreakdownZScore(double mean, double stddev, CategoryBreakdown breakdown) {
        double delta  = breakdown.getDeltaPct().doubleValue();
        double zScore = (delta - mean) / stddev;
        return new CategoryBreakdownZScore(breakdown, zScore);
    }

    /**
     * Generates a textual description of the anomaly.
     *
     * @param breakdown category breakdown
     * @param zScore    computed z-score
     * @return formatted description string
     */
    private String getAnomalyDescription(CategoryBreakdown breakdown, double zScore) {
        return String.format(
                "%s spend %.1f%% %s compared to previous period",
                breakdown.getLabel(),
                Math.abs(breakdown.getDeltaPct().doubleValue()),
                zScore > 0 ? "above" : "below"
        );
    }

    /**
     * Computes the mean of an array of values.
     *
     * @param values array of doubles
     * @return mean value
     */
    private double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /**
     * Computes the standard deviation of an array of values.
     *
     * @param values array of doubles
     * @param mean   precomputed mean of values
     * @return standard deviation
     */
    private double standardDeviation(double[] values, double mean) {
        double variance = 0;

        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }

        return Math.sqrt(variance / values.length);
    }

}
