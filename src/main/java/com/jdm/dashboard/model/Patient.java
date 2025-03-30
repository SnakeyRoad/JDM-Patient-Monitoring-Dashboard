package com.jdm.dashboard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a patient in the JDM monitoring system.
 * This class is immutable to ensure thread safety and data consistency.
 */
public class Patient {
    private final String patientId;
    private final String name;
    private final List<LabResult> labResults;
    private final List<CMASScore> cmasScores;

    /**
     * Creates a new Patient instance.
     * @param patientId The unique identifier for the patient
     * @param name The patient's name
     * @throws IllegalArgumentException if patientId or name is null or empty
     */
    public Patient(String patientId, String name) {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        this.patientId = patientId.trim();
        this.name = name.trim();
        this.labResults = new ArrayList<>();
        this.cmasScores = new ArrayList<>();
    }

    /**
     * Gets the patient's unique identifier.
     * @return The patient ID
     */
    public String getPatientId() {
        return patientId;
    }

    /**
     * Gets the patient's name.
     * @return The patient's name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets an unmodifiable view of the patient's lab results.
     * @return An unmodifiable list of lab results
     */
    public List<LabResult> getLabResults() {
        return Collections.unmodifiableList(labResults);
    }

    /**
     * Gets an unmodifiable view of the patient's CMAS scores.
     * @return An unmodifiable list of CMAS scores
     */
    public List<CMASScore> getCmasScores() {
        return Collections.unmodifiableList(cmasScores);
    }

    /**
     * Adds a lab result to the patient's records.
     * @param labResult The lab result to add
     * @throws IllegalArgumentException if labResult is null or doesn't belong to this patient
     */
    public void addLabResult(LabResult labResult) {
        if (labResult == null) {
            throw new IllegalArgumentException("Lab result cannot be null");
        }
        if (!labResult.getPatientId().equals(this.patientId)) {
            throw new IllegalArgumentException("Lab result does not belong to this patient");
        }
        this.labResults.add(labResult);
    }

    /**
     * Adds a CMAS score to the patient's records.
     * @param cmasScore The CMAS score to add
     * @throws IllegalArgumentException if cmasScore is null or doesn't belong to this patient
     */
    public void addCMASScore(CMASScore cmasScore) {
        if (cmasScore == null) {
            throw new IllegalArgumentException("CMAS score cannot be null");
        }
        if (!cmasScore.getPatientId().equals(this.patientId)) {
            throw new IllegalArgumentException("CMAS score does not belong to this patient");
        }
        this.cmasScores.add(cmasScore);
    }

    /**
     * Gets the most recent CMAS score for the patient.
     * @return The most recent CMAS score, or null if no scores exist
     */
    public CMASScore getMostRecentCMASScore() {
        return cmasScores.stream()
                .max((a, b) -> a.getDate().compareTo(b.getDate()))
                .orElse(null);
    }

    /**
     * Gets the average CMAS score for the patient.
     * @return The average score, or 0.0 if no scores exist
     */
    public double getAverageCMASScore() {
        return cmasScores.stream()
                .mapToDouble(CMASScore::getScore)
                .average()
                .orElse(0.0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Patient patient = (Patient) o;
        return patientId.equals(patient.patientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId);
    }

    @Override
    public String toString() {
        return "Patient{" +
                "patientId='" + patientId + '\'' +
                ", name='" + name + '\'' +
                ", labResultsCount=" + labResults.size() +
                ", cmasScoresCount=" + cmasScores.size() +
                '}';
    }
}

