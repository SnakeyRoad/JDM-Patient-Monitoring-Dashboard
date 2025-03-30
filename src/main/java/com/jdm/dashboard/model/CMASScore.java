package com.jdm.dashboard.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a CMAS score measurement in the JDM monitoring system.
 * This class is immutable to ensure thread safety and data consistency.
 */
public class CMASScore {
    private final Integer id;
    private final String patientId;
    private final LocalDateTime date;
    private final double score;
    private final String category;

    public static final String CATEGORY_HIGH = "CMAS Score > 10";
    public static final String CATEGORY_LOW = "CMAS Score 4-9";
    public static final double MIN_SCORE = 0.0;
    public static final double MAX_SCORE = 52.0;

    /**
     * Creates a new CMASScore instance with an ID.
     * @param id The unique identifier for the score
     * @param patientId The identifier of the patient
     * @param date The date of the score measurement
     * @param score The CMAS score value
     * @param category The score category
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public CMASScore(Integer id, String patientId, LocalDateTime date, double score, String category) {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("Score must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
        if (category == null || (!category.equals(CATEGORY_HIGH) && !category.equals(CATEGORY_LOW))) {
            throw new IllegalArgumentException("Invalid category");
        }

        this.id = id;
        this.patientId = patientId.trim();
        this.date = date;
        this.score = score;
        this.category = category;
    }

    /**
     * Creates a new CMASScore instance without an ID (for new records).
     * @param patientId The identifier of the patient
     * @param date The date of the score measurement
     * @param score The CMAS score value
     * @param category The score category
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public CMASScore(String patientId, LocalDateTime date, double score, String category) {
        this(null, patientId, date, score, category);
    }

    /**
     * Gets the unique identifier of the score.
     * @return The score ID
     */
    public Integer getId() {
        return id;
    }

    /**
     * Gets the identifier of the patient.
     * @return The patient ID
     */
    public String getPatientId() {
        return patientId;
    }

    /**
     * Gets the date of the score measurement.
     * @return The date
     */
    public LocalDateTime getDate() {
        return date;
    }

    /**
     * Gets the CMAS score value.
     * @return The score
     */
    public double getScore() {
        return score;
    }

    /**
     * Gets the score category.
     * @return The category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Determines if this is a high score (> 10).
     * @return true if the score is in the high category
     */
    public boolean isHighScore() {
        return category.equals(CATEGORY_HIGH);
    }

    /**
     * Gets the appropriate category for a given score.
     * @param score The score value
     * @return The category string
     */
    public static String getCategoryForScore(double score) {
        return score > 10 ? CATEGORY_HIGH : CATEGORY_LOW;
    }

    /**
     * Compares this score with another based on date.
     * @param other The score to compare with
     * @return A negative integer if this score is earlier, a positive integer if later,
     *         or zero if they are at the same time
     */
    public int compareDate(CMASScore other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot compare with null score");
        }
        return this.date.compareTo(other.date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CMASScore cmasScore = (CMASScore) o;
        return Objects.equals(id, cmasScore.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CMASScore{" +
                "id=" + id +
                ", date=" + date +
                ", score=" + score +
                ", category='" + category + '\'' +
                '}';
    }
}


