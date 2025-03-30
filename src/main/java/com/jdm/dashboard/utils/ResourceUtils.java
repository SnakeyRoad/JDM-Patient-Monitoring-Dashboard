package com.jdm.dashboard.utils;

import com.jdm.dashboard.database.DatabaseManager;
import javax.swing.JOptionPane;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.sql.SQLException;
import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for handling resource files and data import operations.
 * This class provides methods for managing application resources, including
 * CSV data files, SQL schema, and configuration properties.
 */
public class ResourceUtils {
    private static final Logger LOGGER = Logger.getLogger(ResourceUtils.class.getName());
    private static final int BUFFER_SIZE = 8192;
    private static final String DATA_DIR = "data";
    private static final String PATIENT_X_DIR = "data/PatientX";
    private static final String DEFAULT_PATIENT_ID = "55e2d179-d738-47d1-b88c-606833ce4d31"; // Patient X
    private static final List<String> REQUIRED_CSV_FILES = Arrays.asList(
        "Patient.csv",
        "CMAS.csv",
        "LabResultGroup.csv",
        "Measurement.csv",
        "LabResults(EN).csv",
        "LabResult.csv"
    );
    
    private static final String TEMP_CMAS_FILE = "temp_cmas_transformed.csv";

    /**
     * Import data from CSV files into the database.
     * @throws SQLException if there's an error during import
     */
    public static void importCSVData(DatabaseManager dbManager) throws SQLException, IOException {
        if (dbManager == null) {
            throw new IllegalArgumentException("Database manager cannot be null");
        }

        try {
            // Create and validate data directory
            File dataDir = createAndValidateDataDirectory();
            
            // Validate all required CSV files exist in PatientX directory
            validateRequiredCSVFiles(new File(PATIENT_X_DIR));
            
            // Import CSV files into database
            importCSVFilesToDatabase(new File(PATIENT_X_DIR));
            
            LOGGER.info("CSV data import completed successfully");
            showSuccessMessage("CSV data imported successfully!");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error importing CSV data", e);
            throw new SQLException("Error importing CSV data: " + e.getMessage(), e);
        }
    }

    /**
     * Creates and validates the data directory.
     * @return Path to the data directory
     * @throws IOException if directory creation fails
     */
    private static File createAndValidateDataDirectory() throws IOException {
        File dataDir = new File(DATA_DIR);
        File patientXDir = new File(PATIENT_X_DIR);
        
        if (!dataDir.exists()) {
            if (!dataDir.mkdirs()) {
                throw new IOException("Failed to create data directory: " + dataDir);
            }
            LOGGER.info("Created data directory: " + dataDir);
        }
        
        if (!patientXDir.exists() || !patientXDir.isDirectory()) {
            throw new IOException("PatientX directory not found: " + patientXDir);
        }
        
        if (!patientXDir.canWrite()) {
            throw new IOException("PatientX directory is not writable: " + patientXDir);
        }
        
        return dataDir;
    }

    /**
     * Validates that all required CSV files exist in the data directory.
     * @param dataDir Path to the data directory
     * @throws IOException if any required file is missing
     */
    private static void validateRequiredCSVFiles(File dataDir) throws IOException {
        for (String fileName : REQUIRED_CSV_FILES) {
            File file = new File(dataDir, fileName);
            if (!file.exists() || !file.isFile()) {
                throw new IOException("Required CSV file not found: " + fileName);
            }
            LOGGER.info("Found required file: " + fileName);
        }
    }

    /**
     * Imports CSV files into the database.
     * @param dataDir Path to the data directory
     * @throws SQLException if import fails
     * @throws IOException if file operations fail
     */
    private static void importCSVFilesToDatabase(File dataDir) throws SQLException, IOException {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        
        // Import all CSV files together using the main import method
        dbManager.importFromCSV(
            new File(dataDir, "Patient.csv").toPath(),
            new File(dataDir, "LabResultGroup.csv").toPath(),
            new File(dataDir, "LabResult.csv").toPath(),
            new File(dataDir, "LabResults(EN).csv").toPath(),
            new File(dataDir, "Measurement.csv").toPath(),
            new File(dataDir, "CMAS.csv").toPath()
        );
        
        LOGGER.info("All CSV files imported successfully");
    }

    /**
     * Updates patient IDs in a CSV file to use the default patient ID.
     * @param inputFile The input CSV file
     * @param outputFile The output CSV file
     * @param defaultPatientId The default patient ID to use
     * @throws IOException if file operations fail
     */
    private static void updatePatientIdsInCSV(File inputFile, File outputFile, String defaultPatientId) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            
            // Read and analyze header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("CSV file is empty");
            }
            
            // Write header line unchanged
            writer.write(headerLine);
            writer.newLine();
            
            // Find the index of the PatientID column
            String[] headers = headerLine.split(",", -1);
            int patientIdIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase("PatientID")) {
                    patientIdIndex = i;
                    break;
                }
            }
            
            if (patientIdIndex == -1) {
                throw new IOException("PatientID column not found in CSV header");
            }
            
            // Process data lines
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1); // -1 to keep empty fields
                if (parts.length > patientIdIndex) {
                    // Replace patient ID with default patient ID
                    parts[patientIdIndex] = defaultPatientId;
                }
                writer.write(String.join(",", parts));
                writer.newLine();
            }
        }
    }

    /**
     * Get the schema SQL file as a string.
     * @return The SQL schema as a string
     * @throws IOException if schema file cannot be read
     */
    public static String getSchemaSQL() throws IOException {
        try (InputStream in = ResourceUtils.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IOException("Schema file not found in resources");
            }
            return new String(in.readAllBytes());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to read schema SQL", e);
            throw new IOException("Failed to read schema SQL: " + e.getMessage(), e);
        }
    }

    /**
     * Show a success message dialog.
     * @param message The message to show
     */
    private static void showSuccessMessage(String message) {
        JOptionPane.showMessageDialog(null, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void importCSVFile(DatabaseManager dbManager, String tableName, String filePath) throws SQLException, IOException {
        dbManager.importFromCSV(tableName, filePath);
    }
} 