package com.jdm.dashboard.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a measurement in the JDM monitoring system.
 * This class is immutable to ensure thread safety and data consistency.
 */
public class Measurement {
    private final String measurementId;
    private final String resultId;
    private final LocalDateTime dateTime;
    private final String value;

    /**
     * Creates a new Measurement instance.
     * @param measurementId The unique identifier for the measurement
     * @param resultId The identifier of the associated lab result
     * @param dateTime The date and time of the measurement
     * @param value The measured value
     * @throws IllegalArgumentException if any required parameter is null or empty
     */
    public Measurement(String measurementId, String resultId, LocalDateTime dateTime, String value) {
        if (measurementId == null || measurementId.trim().isEmpty()) {
            throw new IllegalArgumentException("Measurement ID cannot be null or empty");
        }
        if (resultId == null || resultId.trim().isEmpty()) {
            throw new IllegalArgumentException("Result ID cannot be null or empty");
        }
        if (dateTime == null) {
            throw new IllegalArgumentException("DateTime cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        this.measurementId = measurementId.trim();
        this.resultId = resultId.trim();
        this.dateTime = dateTime;
        this.value = value;
    }

    /**
     * Gets the unique identifier of the measurement.
     * @return The measurement ID
     */
    public String getMeasurementId() {
        return measurementId;
    }

    /**
     * Gets the identifier of the associated lab result.
     * @return The result ID
     */
    public String getResultId() {
        return resultId;
    }

    /**
     * Gets the date and time of the measurement.
     * @return The date and time
     */
    public LocalDateTime getDateTime() {
        return dateTime;
    }

    /**
     * Gets the measured value as a string.
     * @return The value
     */
    public String getValue() {
        return value;
    }

    /**
     * Gets the numeric value if possible.
     * @return Double value or null if not parseable
     */
    public Double getNumericValue() {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Checks if the measurement value is numeric.
     * @return true if the value can be parsed as a number, false otherwise
     */
    public boolean isNumeric() {
        return getNumericValue() != null;
    }

    /**
     * Gets the measurement value with its unit.
     * @param unit The unit to append to the value
     * @return The formatted value with unit
     */
    public String getValueWithUnit(String unit) {
        if (unit == null || unit.trim().isEmpty()) {
            return value;
        }
        return value + " " + unit.trim();
    }

    /**
     * Compares this measurement with another based on date and time.
     * @param other The measurement to compare with
     * @return A negative integer if this measurement is earlier, a positive integer if later,
     *         or zero if they are at the same time
     */
    public int compareDateTime(Measurement other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot compare with null measurement");
        }
        return this.dateTime.compareTo(other.dateTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Measurement that = (Measurement) o;
        return measurementId.equals(that.measurementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(measurementId);
    }

    @Override
    public String toString() {
        return "Measurement{" +
                "measurementId='" + measurementId + '\'' +
                ", dateTime=" + dateTime +
                ", value='" + value + '\'' +
                ", isNumeric=" + isNumeric() +
                '}';
    }
}
