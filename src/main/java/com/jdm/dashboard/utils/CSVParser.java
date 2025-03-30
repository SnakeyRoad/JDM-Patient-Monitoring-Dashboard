package com.jdm.dashboard.utils;

import com.jdm.dashboard.model.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Parser for CSV files to convert them to model objects
 */
public class CSVParser {
    private static final Logger LOGGER = Logger.getLogger(CSVParser.class.getName());

    /**
     * Parse a Patient CSV file
     */
    public static List<Patient> parsePatientCSV(Path csvPath) throws IOException {
        if (csvPath == null) {
            throw new IllegalArgumentException("CSV path cannot be null");
        }
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }

        List<Patient> patients = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            // Skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOGGER.warning("Empty CSV file: " + csvPath);
                return patients;
            }
            
            String line;
            int lineNumber = 2; // Start from 2 since we skipped header
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String patientId = parts[0].trim();
                        String name = parts[1].trim();
                        
                        if (patientId.isEmpty() || name.isEmpty()) {
                            LOGGER.warning("Skipping invalid patient data at line " + lineNumber + ": " + line);
                            continue;
                        }
                        
                        Patient patient = new Patient(patientId, name);
                        patients.add(patient);
                    } else {
                        LOGGER.warning("Skipping invalid line " + lineNumber + ": " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error parsing patient at line " + lineNumber + ": " + line, e);
                }
                lineNumber++;
            }
        }
        
        LOGGER.info("Successfully parsed " + patients.size() + " patients from " + csvPath);
        return patients;
    }
    
    /**
     * Parse a LabResultGroup CSV file
     */
    public static List<LabResultGroup> parseLabResultGroupCSV(Path csvPath) throws IOException {
        if (csvPath == null) {
            throw new IllegalArgumentException("CSV path cannot be null");
        }
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }

        List<LabResultGroup> groups = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            // Skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOGGER.warning("Empty CSV file: " + csvPath);
                return groups;
            }
            
            String line;
            int lineNumber = 2; // Start from 2 since we skipped header
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String groupId = parts[0].trim();
                        String groupName = parts[1].trim();
                        
                        if (groupId.isEmpty() || groupName.isEmpty()) {
                            LOGGER.warning("Skipping invalid group data at line " + lineNumber + ": " + line);
                            continue;
                        }
                        
                        LabResultGroup group = new LabResultGroup(groupId, groupName);
                        groups.add(group);
                    } else {
                        LOGGER.warning("Skipping invalid line " + lineNumber + ": " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error parsing group at line " + lineNumber + ": " + line, e);
                }
                lineNumber++;
            }
        }
        
        LOGGER.info("Successfully parsed " + groups.size() + " groups from " + csvPath);
        return groups;
    }
    
    /**
     * Parse a LabResult CSV file
     */
    public static List<LabResult> parseLabResultCSV(Path csvPath, Map<String, String> englishNames) throws IOException {
        if (csvPath == null) {
            throw new IllegalArgumentException("CSV path cannot be null");
        }
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }
        if (englishNames == null) {
            throw new IllegalArgumentException("English names map cannot be null");
        }

        List<LabResult> results = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            // Skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOGGER.warning("Empty CSV file: " + csvPath);
                return results;
            }
            
            String line;
            int lineNumber = 2; // Start from 2 since we skipped header
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String resultId = parts[0].trim();
                        String groupId = parts[1].trim();
                        String patientId = parts[2].trim();
                        String resultName = parts[3].trim();
                        String unit = parts.length > 4 ? parts[4].trim() : "";
                        
                        if (resultId.isEmpty()) {
                            LOGGER.warning("Skipping line " + lineNumber + ": empty result ID");
                            continue;
                        }
                        
                        // Get English name from map if available
                        String resultNameEnglish = englishNames.getOrDefault(resultId, resultName);
                        
                        LabResult result = new LabResult(
                            resultId,
                            groupId.isEmpty() ? null : groupId,
                            patientId.isEmpty() ? null : patientId,
                            resultName.isEmpty() ? resultNameEnglish : resultName,
                            unit,
                            resultNameEnglish
                        );
                        results.add(result);
                    } else {
                        LOGGER.warning("Skipping invalid line " + lineNumber + ": " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error parsing result at line " + lineNumber + ": " + line, e);
                }
                lineNumber++;
            }
        }
        
        LOGGER.info("Successfully parsed " + results.size() + " results from " + csvPath);
        return results;
    }
    
    /**
     * Parse a LabResultsEN CSV file to get English names mapping
     */
    public static Map<String, String> parseLabResultsEnCSV(Path csvPath) throws IOException {
        if (csvPath == null) {
            throw new IllegalArgumentException("CSV path cannot be null");
        }
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }

        Map<String, String> englishNames = new HashMap<>();
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            // Skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOGGER.warning("Empty CSV file: " + csvPath);
                return englishNames;
            }
            
            String line;
            int lineNumber = 2; // Start from 2 since we skipped header
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        String resultId = parts[0].trim();
                        String resultNameEnglish = parts[5].trim();
                        
                        if (resultId.isEmpty() || resultNameEnglish.isEmpty()) {
                            LOGGER.warning("Skipping invalid English name data at line " + lineNumber + ": " + line);
                            continue;
                        }
                        
                        englishNames.put(resultId, resultNameEnglish);
                    } else {
                        LOGGER.warning("Skipping invalid line " + lineNumber + ": " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error parsing English name at line " + lineNumber + ": " + line, e);
                }
                lineNumber++;
            }
        }
        
        LOGGER.info("Successfully parsed " + englishNames.size() + " English names from " + csvPath);
        return englishNames;
    }
    
    /**
     * Parse a Measurement CSV file
     */
    public static List<Measurement> parseMeasurementCSV(Path csvPath) throws IOException {
        if (csvPath == null) {
            throw new IllegalArgumentException("CSV path cannot be null");
        }
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }

        List<Measurement> measurements = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            // Skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOGGER.warning("Empty CSV file: " + csvPath);
                return measurements;
            }
            
            String line;
            int lineNumber = 2; // Start from 2 since we skipped header
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String measurementId = parts[0].trim();
                        String resultId = parts[1].trim();
                        String dateTimeStr = parts[2].trim();
                        String value = parts[3].trim();
                        
                        if (measurementId.isEmpty() || resultId.isEmpty() || dateTimeStr.isEmpty() || value.isEmpty()) {
                            LOGGER.warning("Skipping invalid measurement data at line " + lineNumber + ": " + line);
                            continue;
                        }
                        
                        // Parse date time
                        LocalDateTime dateTime = DateUtils.parseDateTimeFromCSV(dateTimeStr);
                        if (dateTime == null) {
                            LOGGER.warning("Skipping measurement with invalid date at line " + lineNumber + ": " + line);
                            continue;
                        }
                        
                        Measurement measurement = new Measurement(
                            measurementId,
                            resultId,
                            dateTime,
                            value
                        );
                        measurements.add(measurement);
                    } else {
                        LOGGER.warning("Skipping invalid line " + lineNumber + ": " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error parsing measurement at line " + lineNumber + ": " + line, e);
                }
                lineNumber++;
            }
        }
        
        LOGGER.info("Successfully parsed " + measurements.size() + " measurements from " + csvPath);
        return measurements;
    }
    
    /**
     * Parse a CMAS CSV file
     * Note: This is more complex because of the structure of the CMAS data
     */
    public static List<CMASScore> parseCMASCSV(Path csvPath) throws IOException {
        if (csvPath == null) {
            throw new IllegalArgumentException("CSV path cannot be null");
        }
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }

        List<CMASScore> scores = new ArrayList<>();
        String defaultPatientId = "55e2d179-d738-47d1-b88c-606833ce4d31"; // Patient X
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            // Read header line to get dates
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOGGER.warning("Empty CSV file: " + csvPath);
                return scores;
            }
            
            String[] headerParts = headerLine.split(",");
            if (headerParts.length < 2) {
                LOGGER.warning("Invalid header line: " + headerLine);
                return scores;
            }
            
            List<String> dates = Arrays.asList(headerParts).subList(1, headerParts.length - 1);
            
            // Read other lines to get scores
            Map<String, List<String>> categoryScores = new HashMap<>();
            String line;
            int lineNumber = 2; // Start from 2 since we skipped header
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        String category = parts[0].trim();
                        if (category.isEmpty()) {
                            LOGGER.warning("Skipping line with empty category at line " + lineNumber + ": " + line);
                            continue;
                        }
                        
                        List<String> scoreValues = Arrays.asList(parts).subList(1, parts.length - 1);
                        categoryScores.put(category, scoreValues);
                    } else {
                        LOGGER.warning("Skipping invalid line " + lineNumber + ": " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error parsing category scores at line " + lineNumber + ": " + line, e);
                }
                lineNumber++;
            }
            
            // Process scores by date
            for (int i = 0; i < dates.size(); i++) {
                String dateStr = dates.get(i);
                if (dateStr.isEmpty()) {
                    continue;
                }
                
                // Process each category for this date
                for (Map.Entry<String, List<String>> entry : categoryScores.entrySet()) {
                    String category = entry.getKey();
                    List<String> scoreValues = entry.getValue();
                    
                    if (i < scoreValues.size()) {
                        String scoreStr = scoreValues.get(i);
                        
                        if (!scoreStr.isEmpty()) {
                            try {
                                double score = Double.parseDouble(scoreStr);
                                LocalDateTime date = DateUtils.parseDateFromCSV(dateStr);
                                
                                if (date == null) {
                                    LOGGER.warning("Skipping score with invalid date: " + dateStr);
                                    continue;
                                }
                                
                                CMASScore cmasScore = new CMASScore(
                                    defaultPatientId,
                                    date,
                                    score,
                                    category
                                );
                                scores.add(cmasScore);
                            } catch (NumberFormatException e) {
                                LOGGER.warning("Invalid score value: " + scoreStr);
                            }
                        }
                    }
                }
            }
        }
        
        LOGGER.info("Successfully parsed " + scores.size() + " CMAS scores from " + csvPath);
        return scores;
    }
} 