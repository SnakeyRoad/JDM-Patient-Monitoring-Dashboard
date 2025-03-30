package com.jdm.dashboard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a lab result in the JDM monitoring system.
 * This class is immutable to ensure thread safety and data consistency.
 */
public class LabResult {
    private final String resultId;
    private final String groupId;
    private final String patientId;
    private final String resultName;
    private final String unit;
    private final String resultNameEnglish;
    private final List<Measurement> measurements;

    /**
     * Creates a new LabResult instance.
     * @param resultId The unique identifier for the result
     * @param groupId The identifier of the result group
     * @param patientId The identifier of the patient
     * @param resultName The name of the result
     * @param unit The unit of measurement
     * @param resultNameEnglish The English name of the result
     * @throws IllegalArgumentException if any required parameter is null or empty
     */
    public LabResult(String resultId, String groupId, String patientId, String resultName, String unit, String resultNameEnglish) {
        if (resultId == null || resultId.trim().isEmpty()) {
            throw new IllegalArgumentException("Result ID cannot be null or empty");
        }
        if (resultName == null || resultName.trim().isEmpty()) {
            throw new IllegalArgumentException("Result name cannot be null or empty");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Unit cannot be null");
        }
        if (resultNameEnglish == null) {
            throw new IllegalArgumentException("English result name cannot be null");
        }

        this.resultId = resultId.trim();
        this.groupId = groupId != null ? groupId.trim() : null;
        this.patientId = patientId != null ? patientId.trim() : null;
        this.resultName = resultName.trim();
        this.unit = unit;
        this.resultNameEnglish = resultNameEnglish;
        this.measurements = new ArrayList<>();
    }

    /**
     * Gets the unique identifier of the result.
     * @return The result ID
     */
    public String getResultId() {
        return resultId;
    }

    /**
     * Gets the identifier of the result group.
     * @return The group ID
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the identifier of the patient.
     * @return The patient ID
     */
    public String getPatientId() {
        return patientId;
    }

    /**
     * Gets the name of the result.
     * @return The result name
     */
    public String getResultName() {
        return resultName;
    }

    /**
     * Gets the unit of measurement.
     * @return The unit
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Gets the English name of the result.
     * @return The English result name
     */
    public String getResultNameEnglish() {
        return resultNameEnglish;
    }

    /**
     * Gets an unmodifiable view of the measurements.
     * @return An unmodifiable list of measurements
     */
    public List<Measurement> getMeasurements() {
        return Collections.unmodifiableList(measurements);
    }

    /**
     * Adds a measurement to the lab result.
     * @param measurement The measurement to add
     * @throws IllegalArgumentException if measurement is null or doesn't belong to this result
     */
    public void addMeasurement(Measurement measurement) {
        if (measurement == null) {
            throw new IllegalArgumentException("Measurement cannot be null");
        }
        if (!measurement.getResultId().equals(this.resultId)) {
            throw new IllegalArgumentException("Measurement does not belong to this result");
        }
        this.measurements.add(measurement);
    }

    /**
     * Gets the most recent measurement.
     * @return The most recent measurement, or null if no measurements exist
     */
    public Measurement getMostRecentMeasurement() {
        return measurements.stream()
                .max((a, b) -> a.getDateTime().compareTo(b.getDateTime()))
                .orElse(null);
    }

    /**
     * Gets the average numeric value of all measurements.
     * @return The average value, or null if no valid numeric measurements exist
     */
    public Double getAverageValue() {
        return measurements.stream()
                .map(Measurement::getNumericValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
    }

    /**
     * Gets the number of measurements.
     * @return The count of measurements
     */
    public int getMeasurementCount() {
        return measurements.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabResult labResult = (LabResult) o;
        return resultId.equals(labResult.resultId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resultId);
    }

    @Override
    public String toString() {
        return "LabResult{" +
                "resultId='" + resultId + '\'' +
                ", resultName='" + resultName + '\'' +
                ", unit='" + unit + '\'' +
                ", measurementCount=" + measurements.size() +
                '}';
    }
}

