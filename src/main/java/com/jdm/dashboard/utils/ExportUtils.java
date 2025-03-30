package com.jdm.dashboard.utils;

import com.jdm.dashboard.model.CMASScore;
import com.jdm.dashboard.model.LabResult;
import com.jdm.dashboard.model.Measurement;
import com.jdm.dashboard.model.Patient;
import com.jdm.dashboard.database.DatabaseManager;

import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for data export operations.
 * Provides methods for exporting patient data, lab results, and CMAS scores to various formats.
 */
public class ExportUtils {
    private static final Logger LOGGER = Logger.getLogger(ExportUtils.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final String CSV_EXTENSION = ".csv";
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * Export CMAS scores to a CSV file.
     * @param scores List of CMAS scores to export
     * @param patient Patient associated with the scores
     * @param outputPath Path to save the CSV file
     * @throws IOException if export fails
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static void exportCMASScoresToCSV(List<CMASScore> scores, Patient patient, Path outputPath) throws IOException {
        validateExportParameters(scores, patient, outputPath);
        ensureDirectoryExists(outputPath.getParent());
        
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeCSVHeader(writer, "Date,Patient ID,Patient Name,Score,Category");
            
            for (CMASScore score : scores) {
                writer.write(String.join(",",
                    score.getDate().format(DATE_FORMATTER),
                    patient.getPatientId(),
                    escapeCsvField(patient.getName()),
                    String.valueOf(score.getScore()),
                    escapeCsvField(score.getCategory())
                ));
                writer.newLine();
            }
            
            LOGGER.info("Exported " + scores.size() + " CMAS scores to " + outputPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to export CMAS scores", e);
            throw new IOException("Failed to export CMAS scores: " + e.getMessage(), e);
        }
    }
    
    /**
     * Export lab results to a CSV file.
     * @param results List of lab results to export
     * @param patient Patient associated with the results
     * @param outputPath Path to save the CSV file
     * @throws IOException if export fails
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static void exportLabResultsToCSV(List<LabResult> results, Patient patient, Path outputPath) throws IOException {
        validateExportParameters(results, patient, outputPath);
        ensureDirectoryExists(outputPath.getParent());
        
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeCSVHeader(writer, "Date,Patient ID,Patient Name,Group ID,Result Name,Result Name English,Value,Unit");
            
            int totalMeasurements = 0;
            for (LabResult result : results) {
                for (Measurement measurement : result.getMeasurements()) {
                    writer.write(String.join(",",
                        measurement.getDateTime().format(DATETIME_FORMATTER),
                        patient.getPatientId(),
                        escapeCsvField(patient.getName()),
                        escapeCsvField(result.getGroupId()),
                        escapeCsvField(result.getResultName()),
                        escapeCsvField(result.getResultNameEnglish()),
                        escapeCsvField(measurement.getValue()),
                        escapeCsvField(result.getUnit())
                    ));
                    writer.newLine();
                    totalMeasurements++;
                }
            }
            
            LOGGER.info("Exported " + totalMeasurements + " measurements from " + results.size() + " lab results to " + outputPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to export lab results", e);
            throw new IOException("Failed to export lab results: " + e.getMessage(), e);
        }
    }
    
    /**
     * Export all patient data to a directory.
     * @param patient Patient whose data to export
     * @param cmasScores List of CMAS scores
     * @param labResults List of lab results
     * @param outputDir Directory to save the exported files
     * @throws IOException if export fails
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static void exportPatientData(Patient patient, List<CMASScore> cmasScores, 
                                       List<LabResult> labResults, Path outputDir) throws IOException {
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        
        ensureDirectoryExists(outputDir);
        
        // Export patient info
        Path patientInfoPath = outputDir.resolve("patient_info" + CSV_EXTENSION);
        exportPatientInfo(patient, patientInfoPath);
        
        // Export CMAS scores if available
        if (cmasScores != null && !cmasScores.isEmpty()) {
            Path cmasPath = outputDir.resolve("cmas_scores" + CSV_EXTENSION);
            exportCMASScoresToCSV(cmasScores, patient, cmasPath);
        }
        
        // Export lab results if available
        if (labResults != null && !labResults.isEmpty()) {
            Path labResultsPath = outputDir.resolve("lab_results" + CSV_EXTENSION);
            exportLabResultsToCSV(labResults, patient, labResultsPath);
        }
        
        LOGGER.info("Exported all data for patient " + patient.getPatientId() + " to " + outputDir);
    }
    
    /**
     * Create a ZIP archive of a directory.
     * @param sourceDir Directory to compress
     * @param zipFile Path to save the ZIP file
     * @throws IOException if compression fails
     */
    public static void createZipArchive(Path sourceDir, Path zipFile) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source must be a directory: " + sourceDir);
        }
        
        ensureDirectoryExists(zipFile.getParent());
        
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            Files.walk(sourceDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        String zipEntryName = sourceDir.relativize(path).toString();
                        zos.putNextEntry(new ZipEntry(zipEntryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                        LOGGER.fine("Added to ZIP: " + zipEntryName);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            
            LOGGER.info("Created ZIP archive at " + zipFile);
        } catch (UncheckedIOException e) {
            throw new IOException("Failed to create ZIP archive: " + e.getCause().getMessage(), e.getCause());
        }
    }
    
    /**
     * Export the database to a specified file.
     * @param dbManager Database manager instance
     * @param exportPath Path to save the database file
     * @throws SQLException if export fails
     */
    public static void exportDatabase(DatabaseManager dbManager, Path exportPath) throws SQLException {
        if (dbManager == null) {
            throw new IllegalArgumentException("Database manager cannot be null");
        }
        
        try {
            ensureDirectoryExists(exportPath.getParent());
            
            // Close the database before copying
            dbManager.close();
            
            Path sourceDb = Paths.get("jdm_dashboard.db");
            if (!Files.exists(sourceDb)) {
                throw new IOException("Source database file not found: " + sourceDb);
            }
            
            // Copy the database file with a buffer
            try (InputStream in = new BufferedInputStream(Files.newInputStream(sourceDb));
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(exportPath))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
            
            LOGGER.info("Database exported to " + exportPath);
            
            // Reopen the database connection
            dbManager.initialize();
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to export database", e);
            throw new SQLException("Failed to export database: " + e.getMessage(), e);
        }
    }
    
    /**
     * Export patient information to a CSV file.
     */
    private static void exportPatientInfo(Patient patient, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeCSVHeader(writer, "Patient ID,Patient Name");
            writer.write(patient.getPatientId() + "," + escapeCsvField(patient.getName()));
            writer.newLine();
            LOGGER.info("Exported patient info to " + outputPath);
        }
    }
    
    /**
     * Validate export parameters.
     */
    private static void validateExportParameters(List<?> data, Patient patient, Path outputPath) {
        if (data == null) {
            throw new IllegalArgumentException("Data list cannot be null");
        }
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path cannot be null");
        }
    }
    
    /**
     * Ensure the directory exists.
     */
    private static void ensureDirectoryExists(Path dir) throws IOException {
        if (dir != null && !Files.exists(dir)) {
            Files.createDirectories(dir);
            LOGGER.fine("Created directory: " + dir);
        }
    }
    
    /**
     * Write CSV header.
     */
    private static void writeCSVHeader(Writer writer, String header) throws IOException {
        writer.write(header);
        writer.write('\n');
    }
    
    /**
     * Escape CSV field to handle special characters.
     */
    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
