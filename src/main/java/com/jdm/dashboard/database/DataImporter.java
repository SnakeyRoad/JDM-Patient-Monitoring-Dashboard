package com.jdm.dashboard.database;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles importing data from CSV files into the database
 */
public class DataImporter {
    
    private final DatabaseManager dbManager;
    private final AtomicBoolean isImporting = new AtomicBoolean(false);
    
    /**
     * Constructor
     */
    public DataImporter(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    /**
     * Import data from CSV files
     * 
     * @param dataDirectory Directory containing CSV files
     * @param progressCallback Callback for progress updates
     * @return True if import was successful
     */
    public boolean importFromCSV(File dataDirectory, ProgressCallback progressCallback) {
        if (isImporting.get()) {
            progressCallback.onProgressUpdate("Import already in progress", 0);
            return false;
        }
        
        isImporting.set(true);
        
        try {
            // Verify directory exists
            if (!dataDirectory.exists() || !dataDirectory.isDirectory()) {
                progressCallback.onProgressUpdate("Data directory does not exist", 0);
                return false;
            }
            
            // Find required CSV files
            File patientCsv = new File(dataDirectory, "Patient.csv");
            File labResultGroupCsv = new File(dataDirectory, "LabResultGroup.csv");
            File labResultCsv = new File(dataDirectory, "LabResult.csv");
            File labResultsEnCsv = new File(dataDirectory, "LabResultsEN.csv");
            File measurementCsv = new File(dataDirectory, "Measurement.csv");
            File cmasCsv = new File(dataDirectory, "CMAS.csv");
            
            // Verify all files exist
            if (!patientCsv.exists() || !labResultGroupCsv.exists() || 
                !labResultCsv.exists() || !labResultsEnCsv.exists() || 
                !measurementCsv.exists() || !cmasCsv.exists()) {
                
                progressCallback.onProgressUpdate("One or more required CSV files are missing", 0);
                return false;
            }
            
            // Import data
            progressCallback.onProgressUpdate("Starting import...", 5);
            
            dbManager.importFromCSV(
                patientCsv.toPath(),
                labResultGroupCsv.toPath(),
                labResultCsv.toPath(),
                labResultsEnCsv.toPath(),
                measurementCsv.toPath(),
                cmasCsv.toPath()
            );
            
            progressCallback.onProgressUpdate("Import complete", 100);
            return true;
            
        } catch (Exception e) {
            progressCallback.onProgressUpdate("Error importing data: " + e.getMessage(), 0);
            return false;
        } finally {
            isImporting.set(false);
        }
    }
    
    /**
     * Check if an import is in progress
     */
    public boolean isImporting() {
        return isImporting.get();
    }
    
    /**
     * Callback interface for reporting import progress
     */
    public interface ProgressCallback {
        void onProgressUpdate(String message, int percentComplete);
    }
}
