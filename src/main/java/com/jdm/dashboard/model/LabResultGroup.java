package com.jdm.dashboard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a lab result group in the JDM monitoring system.
 * This class is immutable to ensure thread safety and data consistency.
 */
public class LabResultGroup {
    private final String groupId;
    private final String groupName;
    private final List<LabResult> labResults;

    /**
     * Creates a new LabResultGroup instance.
     * @param groupId The unique identifier for the group
     * @param groupName The name of the group
     * @throws IllegalArgumentException if groupId or groupName is null or empty
     */
    public LabResultGroup(String groupId, String groupName) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be null or empty");
        }

        this.groupId = groupId.trim();
        this.groupName = groupName.trim();
        this.labResults = new ArrayList<>();
    }

    /**
     * Gets the unique identifier of the group.
     * @return The group ID
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the name of the group.
     * @return The group name
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Gets an unmodifiable view of the lab results in this group.
     * @return An unmodifiable list of lab results
     */
    public List<LabResult> getLabResults() {
        return Collections.unmodifiableList(labResults);
    }

    /**
     * Adds a lab result to the group.
     * @param labResult The lab result to add
     * @throws IllegalArgumentException if labResult is null or doesn't belong to this group
     */
    public void addLabResult(LabResult labResult) {
        if (labResult == null) {
            throw new IllegalArgumentException("Lab result cannot be null");
        }
        if (!labResult.getGroupId().equals(this.groupId)) {
            throw new IllegalArgumentException("Lab result does not belong to this group");
        }
        this.labResults.add(labResult);
    }

    /**
     * Gets all lab results for a specific patient.
     * @param patientId The patient ID to filter by
     * @return A list of lab results for the patient
     */
    public List<LabResult> getLabResultsForPatient(String patientId) {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        return labResults.stream()
                .filter(result -> result.getPatientId().equals(patientId))
                .collect(Collectors.toList());
    }

    /**
     * Gets the number of lab results in this group.
     * @return The count of lab results
     */
    public int getLabResultCount() {
        return labResults.size();
    }

    /**
     * Gets the number of unique patients in this group.
     * @return The count of unique patients
     */
    public int getUniquePatientCount() {
        return (int) labResults.stream()
                .map(LabResult::getPatientId)
                .distinct()
                .count();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabResultGroup that = (LabResultGroup) o;
        return groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId);
    }

    @Override
    public String toString() {
        return "LabResultGroup{" +
                "groupId='" + groupId + '\'' +
                ", groupName='" + groupName + '\'' +
                ", labResultCount=" + labResults.size() +
                '}';
    }
}
