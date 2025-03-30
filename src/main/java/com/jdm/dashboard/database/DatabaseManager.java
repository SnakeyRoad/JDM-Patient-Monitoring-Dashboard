package com.jdm.dashboard.database;

import com.jdm.dashboard.model.*;
import com.jdm.dashboard.utils.DateUtils;
import com.jdm.dashboard.utils.ResourceUtils;
import com.jdm.dashboard.utils.CSVParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.UUID;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Manages database connection and operations
 */
public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DB_NAME = "jdm_dashboard.db";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int MAX_CONNECTIONS = 5;
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int BATCH_SIZE = 1000;
    private static final int STATEMENT_TIMEOUT_MS = 30000;
    private static final int HEALTH_CHECK_TIMEOUT_MS = 5000;
    private static final String BACKUP_DIR = "backups";
    private static final String BACKUP_PREFIX = "jdm_dashboard_backup_";
    private static final int MAX_BACKUPS = 5;
    private static final int INTEGRITY_CHECK_TIMEOUT_MS = 60000;
    
    private final List<Connection> connectionPool;
    private final ReentrantLock poolLock;
    private boolean isInitialized = false;

    // Connection pool statistics
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger waitingThreads = new AtomicInteger(0);
    private final AtomicLong totalWaitTime = new AtomicLong(0);
    private final AtomicLong totalQueryTime = new AtomicLong(0);
    private final AtomicInteger totalQueries = new AtomicInteger(0);

    private Map<String, Integer> measurementCounters = new HashMap<>();

    private static DatabaseManager instance;

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public DatabaseManager() {
        this.connectionPool = new ArrayList<>();
        this.poolLock = new ReentrantLock();
    }

    /**
     * Initialize the database connection pool
     */
    public synchronized void initialize() throws SQLException {
        if (isInitialized) {
            LOGGER.warning("Database already initialized");
            return;
        }

        boolean dbExists = Files.exists(Paths.get(DB_NAME));
        
        // Create connection pool
        for (int i = 0; i < MAX_CONNECTIONS; i++) {
            Connection conn = createConnection();
            if (conn != null) {
                connectionPool.add(conn);
            }
        }
        
        // If the database doesn't exist, create the schema
        if (!dbExists) {
            createSchema();
        }
        
        isInitialized = true;
        LOGGER.info("Database initialized successfully with " + connectionPool.size() + " connections");
    }

    /**
     * Create a new database connection
     */
    private Connection createConnection() throws SQLException {
        String url = "jdbc:sqlite:" + DB_NAME;
        Connection conn = DriverManager.getConnection(url);
        
        // Set connection timeout
        conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), CONNECTION_TIMEOUT_MS);
        
        // Enable foreign keys
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.setQueryTimeout(STATEMENT_TIMEOUT_MS / 1000); // Convert to seconds
        }
        
        return conn;
    }

    /**
     * Validate a database connection
     */
    private boolean validateConnection(Connection conn) {
        if (conn == null) {
            return false;
        }
        
        try {
            // Try to execute a simple query with timeout
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(HEALTH_CHECK_TIMEOUT_MS / 1000);
                stmt.execute("SELECT 1");
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Connection validation failed", e);
            return false;
        }
    }

    /**
     * Get a connection from the pool with monitoring
     */
    private Connection getConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        waitingThreads.incrementAndGet();
        
        try {
            poolLock.lock();
            
            // Wait for a connection to become available
            while (connectionPool.isEmpty() && activeConnections.get() >= MAX_CONNECTIONS) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for connection", e);
                }
            }
            
            // If pool is empty but we haven't reached max connections, create a new one
            if (connectionPool.isEmpty() && activeConnections.get() < MAX_CONNECTIONS) {
                Connection conn = createConnection();
                if (conn != null) {
                    activeConnections.incrementAndGet();
                    return conn;
                }
            }
            
            // Get connection from pool
            if (!connectionPool.isEmpty()) {
                Connection conn = connectionPool.remove(0);
                if (!validateConnection(conn)) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        LOGGER.log(Level.WARNING, "Error closing invalid connection", e);
                    }
                    conn = createConnection();
                }
                
                activeConnections.incrementAndGet();
                return conn;
            }
            
            throw new SQLException("No available connections in pool");
        } finally {
            long waitTime = System.currentTimeMillis() - startTime;
            totalWaitTime.addAndGet(waitTime);
            waitingThreads.decrementAndGet();
            poolLock.unlock();
        }
    }

    /**
     * Return a connection to the pool with monitoring
     */
    private void returnConnection(Connection conn) {
        if (conn != null) {
            try {
                poolLock.lock();
                if (!connectionPool.contains(conn)) {
                    connectionPool.add(conn);
                    activeConnections.decrementAndGet();
                }
            } finally {
                poolLock.unlock();
            }
        }
    }

    /**
     * Create the database schema from the SQL script
     */
    private void createSchema() throws SQLException {
        try (Statement stmt = connectionPool.get(0).createStatement()) {
            // Get schema SQL from ResourceUtils
            String schemaSql = ResourceUtils.getSchemaSQL();
            if (schemaSql == null || schemaSql.trim().isEmpty()) {
                throw new SQLException("Schema SQL is empty or null");
            }
            
            // Disable foreign key checks temporarily
            stmt.execute("PRAGMA foreign_keys = OFF");
            
            // Split by semicolon to execute each statement separately
            String[] statements = schemaSql.split(";");
            for (String statement : statements) {
                if (!statement.trim().isEmpty()) {
                    stmt.execute(statement);
                }
            }
            
            // Re-enable foreign key checks
            stmt.execute("PRAGMA foreign_keys = ON");
            LOGGER.info("Schema created successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating schema", e);
            throw new SQLException("Failed to create schema", e);
        }
    }

    /**
     * Close all database connections
     */
    public synchronized void close() {
        poolLock.lock();
        try {
            for (Connection conn : connectionPool) {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Error closing database connection", e);
                }
            }
            connectionPool.clear();
            isInitialized = false;
            LOGGER.info("All database connections closed");
        } finally {
            poolLock.unlock();
        }
    }

    /**
     * Check if the database is initialized
     */
    private void checkInitialized() throws SQLException {
        if (!isInitialized || connectionPool.isEmpty()) {
            throw new SQLException("Database not initialized");
        }
    }

    /**
     * Execute a batch of SQL statements
     */
    private void executeBatch(Connection conn, String sql, List<Object[]> batchData) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setQueryTimeout(STATEMENT_TIMEOUT_MS / 1000);
            
            for (Object[] data : batchData) {
                for (int i = 0; i < data.length; i++) {
                    pstmt.setObject(i + 1, data[i]);
                }
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
        }
    }

    /**
     * Import data from CSV files into the database with batch processing
     */
    public void importFromCSV(Path patientCsvPath, Path labResultGroupCsvPath, 
                            Path labResultCsvPath, Path labResultsEnCsvPath,
                            Path measurementCsvPath, Path cmasCsvPath) throws SQLException {
        checkInitialized();
        validatePaths(patientCsvPath, labResultGroupCsvPath, labResultCsvPath, 
                     labResultsEnCsvPath, measurementCsvPath, cmasCsvPath);
        
        Connection conn = getConnection();
        conn.setAutoCommit(false);
        
        try {
            // Disable foreign key constraints temporarily
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = OFF");
            }
            
            clearExistingData(conn);
            
            // Import patients in batches
            List<Patient> patients = CSVParser.parsePatientCSV(patientCsvPath);
            List<Object[]> patientBatch = new ArrayList<>();
            for (Patient patient : patients) {
                patientBatch.add(new Object[]{patient.getPatientId(), patient.getName()});
                if (patientBatch.size() >= BATCH_SIZE) {
                    executeBatch(conn, "INSERT INTO patients (patient_id, name) VALUES (?, ?)", patientBatch);
                    patientBatch.clear();
                }
            }
            if (!patientBatch.isEmpty()) {
                executeBatch(conn, "INSERT INTO patients (patient_id, name) VALUES (?, ?)", patientBatch);
            }
            
            // Import lab result groups in batches
            List<LabResultGroup> groups = CSVParser.parseLabResultGroupCSV(labResultGroupCsvPath);
            List<Object[]> groupBatch = new ArrayList<>();
            for (LabResultGroup group : groups) {
                groupBatch.add(new Object[]{group.getGroupId(), group.getGroupName()});
                if (groupBatch.size() >= BATCH_SIZE) {
                    executeBatch(conn, "INSERT INTO lab_result_groups (group_id, group_name) VALUES (?, ?)", groupBatch);
                    groupBatch.clear();
                }
            }
            if (!groupBatch.isEmpty()) {
                executeBatch(conn, "INSERT INTO lab_result_groups (group_id, group_name) VALUES (?, ?)", groupBatch);
            }
            
            // Import lab results in batches
            Map<String, String> englishNames = CSVParser.parseLabResultsEnCSV(labResultsEnCsvPath);
            List<LabResult> results = CSVParser.parseLabResultCSV(labResultCsvPath, englishNames);
            List<Object[]> resultBatch = new ArrayList<>();
            for (LabResult result : results) {
                String groupId = result.getGroupId();
                String patientId = result.getPatientId();
                
                // Skip results with invalid foreign keys
                if (groupId != null) {
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "SELECT COUNT(*) FROM lab_result_groups WHERE group_id = ?")) {
                        pstmt.setString(1, groupId);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) == 0) {
                                LOGGER.warning("Skipping lab result with invalid group ID: " + groupId);
                                continue;
                            }
                        }
                    }
                }
                
                if (patientId != null) {
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "SELECT COUNT(*) FROM patients WHERE patient_id = ?")) {
                        pstmt.setString(1, patientId);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) == 0) {
                                LOGGER.warning("Skipping lab result with invalid patient ID: " + patientId);
                                continue;
                            }
                        }
                    }
                }
                
                resultBatch.add(new Object[]{
                    result.getResultId(), 
                    groupId,  // Can be null
                    patientId,  // Can be null
                    result.getResultName(), 
                    result.getUnit(), 
                    result.getResultNameEnglish()
                });
                
                if (resultBatch.size() >= BATCH_SIZE) {
                    executeBatch(conn, "INSERT INTO lab_results (result_id, group_id, patient_id, result_name, unit, result_name_english) " +
                                     "VALUES (?, ?, ?, ?, ?, ?)", resultBatch);
                    resultBatch.clear();
                }
            }
            if (!resultBatch.isEmpty()) {
                executeBatch(conn, "INSERT INTO lab_results (result_id, group_id, patient_id, result_name, unit, result_name_english) " +
                                 "VALUES (?, ?, ?, ?, ?, ?)", resultBatch);
            }
            
            // Import measurements in batches
            List<Measurement> measurements = CSVParser.parseMeasurementCSV(measurementCsvPath);
            List<Object[]> measurementBatch = new ArrayList<>();
            for (Measurement measurement : measurements) {
                String resultId = measurement.getResultId();
                
                // Skip measurements with invalid result_id
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM lab_results WHERE result_id = ?")) {
                    pstmt.setString(1, resultId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            LOGGER.warning("Skipping measurement with invalid result ID: " + resultId);
                            continue;
                        }
                    }
                }
                
                measurementBatch.add(new Object[]{
                    measurement.getMeasurementId(),
                    resultId,
                    DateUtils.formatDateTime(measurement.getDateTime()),
                    measurement.getValue()
                });
                
                if (measurementBatch.size() >= BATCH_SIZE) {
                    executeBatch(conn, "INSERT INTO measurements (measurement_id, result_id, date_time, value) " +
                                     "VALUES (?, ?, ?, ?)", measurementBatch);
                    measurementBatch.clear();
                }
            }
            if (!measurementBatch.isEmpty()) {
                executeBatch(conn, "INSERT INTO measurements (measurement_id, result_id, date_time, value) " +
                                 "VALUES (?, ?, ?, ?)", measurementBatch);
            }
            
            // Import CMAS scores in batches
            List<CMASScore> scores = CSVParser.parseCMASCSV(cmasCsvPath);
            List<Object[]> scoreBatch = new ArrayList<>();
            for (CMASScore score : scores) {
                scoreBatch.add(new Object[]{
                    score.getPatientId(), DateUtils.formatDateTime(score.getDate()),
                    score.getScore(), score.getCategory()
                });
                if (scoreBatch.size() >= BATCH_SIZE) {
                    executeBatch(conn, "INSERT INTO cmas_scores (patient_id, date, score, category) " +
                                     "VALUES (?, ?, ?, ?)", scoreBatch);
                    scoreBatch.clear();
                }
            }
            if (!scoreBatch.isEmpty()) {
                executeBatch(conn, "INSERT INTO cmas_scores (patient_id, date, score, category) " +
                                 "VALUES (?, ?, ?, ?)", scoreBatch);
            }
            
            conn.commit();
            
            // Re-enable foreign key constraints
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            
            LOGGER.info("Data imported successfully");
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
            }
            LOGGER.log(Level.SEVERE, "Error importing data", e);
            throw new SQLException("Error importing data: " + e.getMessage(), e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error resetting auto-commit", e);
            }
            returnConnection(conn);
        }
    }

    /**
     * Validate file paths
     */
    private void validatePaths(Path... paths) throws IllegalArgumentException {
        for (Path path : paths) {
            if (path == null || !Files.exists(path)) {
                throw new IllegalArgumentException("Invalid file path: " + path);
            }
        }
    }

    /**
     * Validate that all referenced IDs exist in their respective tables
     */
    private void validateForeignKeys(Connection conn, String tableName, String csvFile) throws SQLException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header row
                }

                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                for (int i = 0; i < values.length; i++) {
                    values[i] = values[i].trim().replaceAll("^\"|\"$", "");
                }

                switch (tableName) {
                    case "lab_results":
                        if (values.length >= 3) {
                            // Skip validation if values are null or empty
                            if (values[2] == null || values[2].trim().isEmpty()) {
                                LOGGER.warning("Skipping validation for null patient ID");
                                continue;
                            }
                            if (values[1] == null || values[1].trim().isEmpty()) {
                                LOGGER.warning("Skipping validation for null group ID");
                                continue;
                            }

                            // Check patient_id exists
                            try (PreparedStatement pstmt = conn.prepareStatement(
                                    "SELECT COUNT(*) FROM patients WHERE patient_id = ?")) {
                                pstmt.setString(1, values[2]);
                                try (ResultSet rs = pstmt.executeQuery()) {
                                    if (rs.next() && rs.getInt(1) == 0) {
                                        LOGGER.warning("Skipping lab result with invalid patient ID: " + values[2]);
                                        continue; // Skip this record instead of failing
                                    }
                                }
                            }

                            // Check group_id exists
                            try (PreparedStatement pstmt = conn.prepareStatement(
                                    "SELECT COUNT(*) FROM lab_result_groups WHERE group_id = ?")) {
                                pstmt.setString(1, values[1]);
                                try (ResultSet rs = pstmt.executeQuery()) {
                                    if (rs.next() && rs.getInt(1) == 0) {
                                        LOGGER.warning("Skipping lab result with invalid group ID: " + values[1]);
                                        continue; // Skip this record instead of failing
                                    }
                                }
                            }
                        }
                        break;
                    case "measurements":
                        if (values.length >= 2 && values[1] != null && !values[1].trim().isEmpty()) {
                            try (PreparedStatement pstmt = conn.prepareStatement(
                                    "SELECT COUNT(*) FROM lab_results WHERE result_id = ?")) {
                                pstmt.setString(1, values[1]);
                                try (ResultSet rs = pstmt.executeQuery()) {
                                    if (rs.next() && rs.getInt(1) == 0) {
                                        LOGGER.warning("Skipping measurement with invalid result ID: " + values[1]);
                                        continue;
                                    }
                                }
                            }
                        }
                        break;
                    case "cmas_scores":
                        if (values.length >= 2 && values[1] != null && !values[1].trim().isEmpty()) {
                            try (PreparedStatement pstmt = conn.prepareStatement(
                                    "SELECT COUNT(*) FROM patients WHERE patient_id = ?")) {
                                pstmt.setString(1, values[1]);
                                try (ResultSet rs = pstmt.executeQuery()) {
                                    if (rs.next() && rs.getInt(1) == 0) {
                                        LOGGER.warning("Skipping CMAS score with invalid patient ID: " + values[1]);
                                        continue;
                                    }
                                }
                            }
                        }
                        break;
                }
            }
        } catch (IOException e) {
            throw new SQLException("Error reading CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Clear all existing data from the database
     */
    private void clearExistingData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Disable foreign key constraints temporarily
            stmt.execute("PRAGMA foreign_keys = OFF");
            
            // Delete all data from tables in reverse order of dependencies
            stmt.execute("DELETE FROM cmas_scores");
            stmt.execute("DELETE FROM measurements");
            stmt.execute("DELETE FROM lab_results");
            stmt.execute("DELETE FROM lab_result_groups");
            stmt.execute("DELETE FROM patients");
            
            // Reset autoincrement counters
            stmt.execute("DELETE FROM sqlite_sequence");
            
            // Re-enable foreign key constraints
            stmt.execute("PRAGMA foreign_keys = ON");
            LOGGER.info("Existing data cleared successfully");
        }
    }

    /**
     * Insert a patient into the database with retry mechanism
     */
    public void insertPatient(Connection conn, Patient patient) throws SQLException {
        checkInitialized();
        validatePatient(patient);
        
        String sql = "INSERT INTO patients (patient_id, name) VALUES (?, ?)";
        executeWithRetry(conn, sql, (pstmt) -> {
            pstmt.setString(1, patient.getPatientId());
            pstmt.setString(2, patient.getName());
            pstmt.executeUpdate();
        });
    }

    /**
     * Validate patient data
     */
    private void validatePatient(Patient patient) throws IllegalArgumentException {
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        if (patient.getPatientId() == null || patient.getPatientId().trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        if (patient.getName() == null || patient.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name cannot be null or empty");
        }
    }

    /**
     * Execute a SQL statement with monitoring
     */
    private void executeWithRetry(Connection conn, String sql, SQLExecutor executor) throws SQLException {
        long startTime = System.currentTimeMillis();
        int retries = 0;
        
        while (retries < MAX_RETRIES) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                executor.execute(pstmt);
                long queryTime = System.currentTimeMillis() - startTime;
                totalQueryTime.addAndGet(queryTime);
                totalQueries.incrementAndGet();
                return;
            } catch (SQLException e) {
                retries++;
                if (retries == MAX_RETRIES) {
                    LOGGER.log(Level.SEVERE, "Max retries reached for SQL: " + sql, e);
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Operation interrupted", ie);
                }
            }
        }
    }

    /**
     * Functional interface for SQL execution
     */
    @FunctionalInterface
    private interface SQLExecutor {
        void execute(PreparedStatement pstmt) throws SQLException;
    }

    /**
     * Insert a lab result group into the database
     */
    public void insertLabResultGroup(Connection conn, LabResultGroup group) throws SQLException {
        checkInitialized();
        validateLabResultGroup(group);
        
        String sql = "INSERT INTO lab_result_groups (group_id, group_name) VALUES (?, ?)";
        executeWithRetry(conn, sql, (pstmt) -> {
            pstmt.setString(1, group.getGroupId());
            pstmt.setString(2, group.getGroupName());
            pstmt.executeUpdate();
        });
    }

    /**
     * Validate lab result group data
     */
    private void validateLabResultGroup(LabResultGroup group) throws IllegalArgumentException {
        if (group == null) {
            throw new IllegalArgumentException("Lab result group cannot be null");
        }
        if (group.getGroupId() == null || group.getGroupId().trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        if (group.getGroupName() == null || group.getGroupName().trim().isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be null or empty");
        }
    }

    /**
     * Insert a lab result into the database
     */
    public void insertLabResult(Connection conn, LabResult result) throws SQLException {
        checkInitialized();
        validateLabResult(result);
        
        String sql = "INSERT INTO lab_results (result_id, group_id, patient_id, result_name, unit, result_name_english) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
        executeWithRetry(conn, sql, (pstmt) -> {
            pstmt.setString(1, result.getResultId());
            pstmt.setString(2, result.getGroupId());
            pstmt.setString(3, result.getPatientId());
            pstmt.setString(4, result.getResultName());
            pstmt.setString(5, result.getUnit());
            pstmt.setString(6, result.getResultNameEnglish());
            pstmt.executeUpdate();
        });
    }

    /**
     * Validate lab result data
     */
    private void validateLabResult(LabResult result) throws IllegalArgumentException {
        if (result == null) {
            throw new IllegalArgumentException("Lab result cannot be null");
        }
        if (result.getResultId() == null || result.getResultId().trim().isEmpty()) {
            throw new IllegalArgumentException("Result ID cannot be null or empty");
        }
        if (result.getGroupId() == null || result.getGroupId().trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        if (result.getPatientId() == null || result.getPatientId().trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        if (result.getResultName() == null || result.getResultName().trim().isEmpty()) {
            throw new IllegalArgumentException("Result name cannot be null or empty");
        }
    }

    /**
     * Insert a measurement into the database
     */
    public void insertMeasurement(Connection conn, Measurement measurement) throws SQLException {
        checkInitialized();
        validateMeasurement(measurement);
        
        String sql = "INSERT INTO measurements (measurement_id, result_id, date_time, value) " +
                    "VALUES (?, ?, ?, ?)";
        executeWithRetry(conn, sql, (pstmt) -> {
            pstmt.setString(1, measurement.getMeasurementId());
            pstmt.setString(2, measurement.getResultId());
            pstmt.setString(3, DateUtils.formatDateTime(measurement.getDateTime()));
            pstmt.setString(4, measurement.getValue());
            pstmt.executeUpdate();
        });
    }

    /**
     * Validate measurement data
     */
    private void validateMeasurement(Measurement measurement) throws IllegalArgumentException {
        if (measurement == null) {
            throw new IllegalArgumentException("Measurement cannot be null");
        }
        if (measurement.getMeasurementId() == null || measurement.getMeasurementId().trim().isEmpty()) {
            throw new IllegalArgumentException("Measurement ID cannot be null or empty");
        }
        if (measurement.getResultId() == null || measurement.getResultId().trim().isEmpty()) {
            throw new IllegalArgumentException("Result ID cannot be null or empty");
        }
        if (measurement.getDateTime() == null) {
            throw new IllegalArgumentException("DateTime cannot be null");
        }
        if (measurement.getValue() == null || measurement.getValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Value cannot be null or empty");
        }
    }

    /**
     * Insert a CMAS score into the database
     */
    public void insertCMASScore(Connection conn, CMASScore score) throws SQLException {
        checkInitialized();
        validateCMASScore(score);
        
        String sql = "INSERT INTO cmas_scores (patient_id, date, score, category) " +
                    "VALUES (?, ?, ?, ?)";
        executeWithRetry(conn, sql, (pstmt) -> {
            pstmt.setString(1, score.getPatientId());
            pstmt.setString(2, DateUtils.formatDateTime(score.getDate()));
            pstmt.setDouble(3, score.getScore());
            pstmt.setString(4, score.getCategory());
            pstmt.executeUpdate();
        });
    }

    /**
     * Validate CMAS score data
     */
    private void validateCMASScore(CMASScore score) throws IllegalArgumentException {
        if (score == null) {
            throw new IllegalArgumentException("CMAS score cannot be null");
        }
        if (score.getPatientId() == null || score.getPatientId().trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        if (score.getDate() == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (score.getScore() < 0) {
            throw new IllegalArgumentException("Score cannot be negative");
        }
        if (score.getCategory() == null || score.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Category cannot be null or empty");
        }
    }

    /**
     * Get all patients from the database
     */
    public List<Patient> getAllPatients() throws SQLException {
        checkInitialized();
        List<Patient> patients = new ArrayList<>();
        String sql = "SELECT patient_id, name FROM patients";
        
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(STATEMENT_TIMEOUT_MS / 1000);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    patients.add(new Patient(
                        rs.getString("patient_id"),
                        rs.getString("name")
                    ));
                }
            }
        } finally {
            returnConnection(conn);
        }
        
        return patients;
    }

    /**
     * Get patient by ID
     */
    public Patient getPatientById(String patientId) throws SQLException {
        checkInitialized();
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        
        String sql = "SELECT patient_id, name FROM patients WHERE patient_id = ?";
        
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, patientId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Patient(
                        rs.getString("patient_id"),
                        rs.getString("name")
                    );
                }
            }
        }
        
        return null;
    }

    /**
     * Get CMAS scores for a patient
     */
    public List<CMASScore> getCMASScoresForPatient(String patientId) throws SQLException {
        checkInitialized();
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        
        List<CMASScore> scores = new ArrayList<>();
        String sql = "SELECT id, patient_id, date, score, category FROM cmas_scores " +
                    "WHERE patient_id = ? ORDER BY date";
        
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, patientId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime date = DateUtils.parseDateTime(rs.getString("date"));
                    
                    CMASScore score = new CMASScore(
                        rs.getInt("id"),
                        rs.getString("patient_id"),
                        date,
                        rs.getDouble("score"),
                        rs.getString("category")
                    );
                    scores.add(score);
                }
            }
        }
        
        return scores;
    }

    /**
     * Get lab results for a patient
     */
    public List<LabResult> getLabResultsForPatient(String patientId) throws SQLException {
        checkInitialized();
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty");
        }
        
        Map<String, LabResult> results = new HashMap<>();
        String sql = "SELECT r.result_id, r.group_id, r.patient_id, r.result_name, r.unit, " +
                     "r.result_name_english, g.group_name " +
                     "FROM lab_results r " +
                     "LEFT JOIN lab_result_groups g ON r.group_id = g.group_id " +
                     "WHERE r.patient_id = ?";
        
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, patientId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String resultId = rs.getString("result_id");
                    String groupId = rs.getString("group_id");
                    
                    LabResult result = new LabResult(
                        resultId,
                        groupId,  // Can be null
                        rs.getString("patient_id"),
                        rs.getString("result_name"),
                        rs.getString("unit"),
                        rs.getString("result_name_english")
                    );
                    
                    results.put(resultId, result);
                }
            }
        }
        
        // Get measurements for each lab result
        for (String resultId : results.keySet()) {
            LabResult result = results.get(resultId);
            List<Measurement> measurements = getMeasurementsForLabResult(conn, resultId);
            for (Measurement measurement : measurements) {
                result.addMeasurement(measurement);
            }
        }
        
        return new ArrayList<>(results.values());
    }

    /**
     * Get measurements for a lab result
     */
    public List<Measurement> getMeasurementsForLabResult(Connection conn, String resultId) throws SQLException {
        checkInitialized();
        if (resultId == null || resultId.trim().isEmpty()) {
            throw new IllegalArgumentException("Result ID cannot be null or empty");
        }
        
        List<Measurement> measurements = new ArrayList<>();
        String sql = "SELECT measurement_id, result_id, date_time, value " +
                    "FROM measurements WHERE result_id = ? ORDER BY date_time";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, resultId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime dateTime = DateUtils.parseDateTime(rs.getString("date_time"));
                    
                    Measurement measurement = new Measurement(
                        rs.getString("measurement_id"),
                        rs.getString("result_id"),
                        dateTime,
                        rs.getString("value")
                    );
                    measurements.add(measurement);
                }
            }
        }
        
        return measurements;
    }

    /**
     * Get all lab result groups
     */
    public List<LabResultGroup> getAllLabResultGroups() throws SQLException {
        checkInitialized();
        List<LabResultGroup> groups = new ArrayList<>();
        String sql = "SELECT group_id, group_name FROM lab_result_groups";
        
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                LabResultGroup group = new LabResultGroup(
                    rs.getString("group_id"),
                    rs.getString("group_name")
                );
                groups.add(group);
            }
        }
        
        return groups;
    }

    /**
     * Get lab results for a specific group
     */
    public List<LabResult> getLabResultsForGroup(String groupId) throws SQLException {
        checkInitialized();
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        
        List<LabResult> results = new ArrayList<>();
        String sql = "SELECT result_id, group_id, patient_id, result_name, unit, result_name_english " +
                    "FROM lab_results WHERE group_id = ?";
        
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LabResult result = new LabResult(
                        rs.getString("result_id"),
                        rs.getString("group_id"),
                        rs.getString("patient_id"),
                        rs.getString("result_name"),
                        rs.getString("unit"),
                        rs.getString("result_name_english")
                    );
                    results.add(result);
                }
            }
        }
        
        return results;
    }

    /**
     * Get all CMAS scores
     */
    public List<CMASScore> getAllCMASScores() throws SQLException {
        checkInitialized();
        List<CMASScore> scores = new ArrayList<>();
        String sql = "SELECT id, patient_id, date, score, category FROM cmas_scores ORDER BY date";
        
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                LocalDateTime date = DateUtils.parseDateTime(rs.getString("date"));
                
                CMASScore score = new CMASScore(
                    rs.getInt("id"),
                    rs.getString("patient_id"),
                    date,
                    rs.getDouble("score"),
                    rs.getString("category")
                );
                scores.add(score);
            }
        }
        
        return scores;
    }

    /**
     * Export the database to a SQLite file
     */
    public void exportDatabase(Path exportPath) throws SQLException {
        checkInitialized();
        if (exportPath == null) {
            throw new IllegalArgumentException("Export path cannot be null");
        }
        
        // For SQLite, we can simply copy the database file
        try {
            Files.copy(Paths.get(DB_NAME), exportPath);
            LOGGER.info("Database exported successfully to: " + exportPath);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error exporting database", e);
            throw new SQLException("Error exporting database: " + e.getMessage(), e);
        }
    }

    /**
     * Perform database maintenance
     */
    public void performMaintenance() throws SQLException {
        checkInitialized();
        Connection conn = getConnection();
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(STATEMENT_TIMEOUT_MS / 1000);
                
                // Analyze tables for better query planning
                stmt.execute("ANALYZE");
                
                // Vacuum the database to reclaim space
                stmt.execute("VACUUM");
                
                // Rebuild indexes
                stmt.execute("REINDEX");
                
                LOGGER.info("Database maintenance completed successfully");
            }
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * Get database statistics
     */
    public Map<String, Object> getDatabaseStats() throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        Connection conn = null;
        
        try {
            conn = getConnection();
            
            try (Statement stmt = conn.createStatement()) {
                // Get database size
                try (ResultSet rs = stmt.executeQuery("PRAGMA page_count")) {
                    if (rs.next()) {
                        stats.put("pageCount", rs.getLong(1));
                    }
                }
                
                // Get database page size
                try (ResultSet rs = stmt.executeQuery("PRAGMA page_size")) {
                    if (rs.next()) {
                        stats.put("pageSize", rs.getLong(1));
                    }
                }
                
                // Get database cache size
                try (ResultSet rs = stmt.executeQuery("PRAGMA cache_size")) {
                    if (rs.next()) {
                        stats.put("cacheSize", rs.getLong(1));
                    }
                }
            }
        } finally {
            returnConnection(conn);
        }
        
        return stats;
    }

    /**
     * Create a backup of the database
     */
    public void createBackup() throws SQLException {
        checkInitialized();
        
        // Create backup directory if it doesn't exist
        Path backupDir = Paths.get(BACKUP_DIR);
        try {
            Files.createDirectories(backupDir);
        } catch (Exception e) {
            throw new SQLException("Failed to create backup directory", e);
        }
        
        // Generate backup filename with timestamp
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupPath = backupDir.resolve(BACKUP_PREFIX + timestamp + ".db");
        
        // Close all connections before backup
        close();
        
        try {
            // Copy database file
            Files.copy(Paths.get(DB_NAME), backupPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Clean up old backups
            cleanupOldBackups(backupDir);
            
            LOGGER.info("Database backup created successfully: " + backupPath);
        } catch (Exception e) {
            throw new SQLException("Failed to create backup", e);
        } finally {
            // Reinitialize connections
            initialize();
        }
    }

    /**
     * Restore database from a backup
     */
    public void restoreFromBackup(Path backupPath) throws SQLException {
        if (backupPath == null || !Files.exists(backupPath)) {
            throw new IllegalArgumentException("Invalid backup path");
        }
        
        // Close all connections before restore
        close();
        
        try {
            // Copy backup file to database location
            Files.copy(backupPath, Paths.get(DB_NAME), StandardCopyOption.REPLACE_EXISTING);
            
            LOGGER.info("Database restored successfully from: " + backupPath);
        } catch (Exception e) {
            throw new SQLException("Failed to restore backup", e);
        } finally {
            // Reinitialize connections
            initialize();
        }
    }

    /**
     * Clean up old backup files
     */
    private void cleanupOldBackups(Path backupDir) throws SQLException {
        try {
            List<Path> backups = Files.list(backupDir)
                .filter(path -> path.getFileName().toString().startsWith(BACKUP_PREFIX))
                .sorted((p1, p2) -> p2.getFileName().compareTo(p1.getFileName()))
                .collect(Collectors.toList());
            
            // Delete old backups beyond MAX_BACKUPS
            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.delete(backups.get(i));
                LOGGER.info("Deleted old backup: " + backups.get(i));
            }
        } catch (Exception e) {
            throw new SQLException("Failed to cleanup old backups", e);
        }
    }

    /**
     * Optimize database performance
     */
    public void optimizeDatabase() throws SQLException {
        checkInitialized();
        Connection conn = getConnection();
        
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(STATEMENT_TIMEOUT_MS / 1000);
                
                // Enable WAL mode for better concurrency
                stmt.execute("PRAGMA journal_mode = WAL");
                
                // Set synchronous mode for better performance
                stmt.execute("PRAGMA synchronous = NORMAL");
                
                // Set cache size
                stmt.execute("PRAGMA cache_size = -2000"); // Use 2MB of cache
                
                // Set page size
                stmt.execute("PRAGMA page_size = 4096");
                
                // Analyze tables
                stmt.execute("ANALYZE");
                
                // Vacuum database
                stmt.execute("VACUUM");
                
                // Rebuild indexes
                stmt.execute("REINDEX");
                
                LOGGER.info("Database optimization completed successfully");
            }
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * Check database version and perform migrations if needed
     */
    public void checkAndMigrate() throws SQLException {
        checkInitialized();
        Connection conn = getConnection();
        
        try {
            // Create version table if it doesn't exist
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS db_version (" +
                           "version INTEGER NOT NULL," +
                           "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                           ")");
            }
            
            // Get current version
            int currentVersion = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM db_version")) {
                if (rs.next()) {
                    currentVersion = rs.getInt(1);
                }
            }
            
            // Perform migrations if needed
            if (currentVersion < 1) {
                performMigration(conn, 1);
                currentVersion = 1;
            }
            
            // Add more version checks and migrations as needed
            
            LOGGER.info("Database version: " + currentVersion);
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * Perform database migration
     */
    private void performMigration(Connection conn, int version) throws SQLException {
        conn.setAutoCommit(false);
        try {
            // Example migration
            if (version == 1) {
                try (Statement stmt = conn.createStatement()) {
                    // Add any new columns or tables here
                    // stmt.execute("ALTER TABLE table_name ADD COLUMN column_name type");
                }
                
                // Record migration
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO db_version (version) VALUES (?)")) {
                    pstmt.setInt(1, version);
                    pstmt.executeUpdate();
                }
                
                conn.commit();
                LOGGER.info("Database migrated to version " + version);
            }
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error rolling back migration", ex);
            }
            throw new SQLException("Migration failed: " + e.getMessage(), e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error resetting auto-commit", e);
            }
        }
    }

    /**
     * Get connection pool statistics
     */
    public Map<String, Object> getConnectionPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeConnections", activeConnections.get());
        stats.put("availableConnections", connectionPool.size());
        stats.put("waitingThreads", waitingThreads.get());
        stats.put("totalWaitTime", totalWaitTime.get());
        stats.put("averageWaitTime", totalWaitTime.get() / Math.max(1, waitingThreads.get()));
        stats.put("totalQueries", totalQueries.get());
        stats.put("averageQueryTime", totalQueryTime.get() / Math.max(1, totalQueries.get()));
        return stats;
    }

    /**
     * Check database integrity
     */
    public boolean checkDatabaseIntegrity() throws SQLException {
        checkInitialized();
        Connection conn = getConnection();
        
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(INTEGRITY_CHECK_TIMEOUT_MS / 1000);
                
                // Check database integrity
                try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                    if (rs.next()) {
                        String result = rs.getString(1);
                        if (!"ok".equalsIgnoreCase(result)) {
                            LOGGER.warning("Database integrity check failed: " + result);
                            return false;
                        }
                    }
                }
                
                // Check foreign key constraints
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
                    if (rs.next()) {
                        LOGGER.warning("Foreign key constraint violation detected");
                        return false;
                    }
                }
                
                LOGGER.info("Database integrity check passed");
                return true;
            }
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * Handle database locks
     */
    public boolean waitForLock(Connection conn, int timeoutMs) throws SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return true;
            } catch (SQLException e) {
                if (e.getMessage().contains("database is locked")) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Lock wait interrupted", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        
        throw new SQLException("Timeout waiting for database lock");
    }

    /**
     * Recover from database corruption
     */
    public void recoverDatabase() throws SQLException {
        checkInitialized();
        
        // Create backup before recovery
        createBackup();
        
        Connection conn = getConnection();
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(INTEGRITY_CHECK_TIMEOUT_MS / 1000);
                
                // Try to recover using SQLite's built-in recovery
                stmt.execute("PRAGMA integrity_check");
                
                // If database is corrupted, try to recover
                stmt.execute("PRAGMA optimize");
                stmt.execute("PRAGMA vacuum");
                stmt.execute("PRAGMA reindex");
                
                // Check if recovery was successful
                try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                    if (rs.next() && !"ok".equalsIgnoreCase(rs.getString(1))) {
                        throw new SQLException("Database recovery failed");
                    }
                }
                
                LOGGER.info("Database recovery completed successfully");
            }
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * Get database size and usage statistics
     */
    public Map<String, Long> getDatabaseSizeStats() throws SQLException, IOException {
        checkInitialized();
        Map<String, Long> stats = new HashMap<>();
        Connection conn = getConnection();
        
        try {
            try (Statement stmt = conn.createStatement()) {
                // Get database file size
                Path dbPath = Paths.get(DB_NAME);
                stats.put("fileSize", Files.size(dbPath));
                
                // Get database page count
                try (ResultSet rs = stmt.executeQuery("PRAGMA page_count")) {
                    if (rs.next()) {
                        stats.put("pageCount", rs.getLong(1));
                    }
                }
                
                // Get database page size
                try (ResultSet rs = stmt.executeQuery("PRAGMA page_size")) {
                    if (rs.next()) {
                        stats.put("pageSize", rs.getLong(1));
                    }
                }
                
                // Get database cache size
                try (ResultSet rs = stmt.executeQuery("PRAGMA cache_size")) {
                    if (rs.next()) {
                        stats.put("cacheSize", rs.getLong(1));
                    }
                }
            }
        } finally {
            returnConnection(conn);
        }
        
        return stats;
    }

    /**
     * Imports data from a CSV file into a specific table.
     */
    public void importFromCSV(String tableName, String csvFile) {
        Map<String, String> columnMapping = new HashMap<>();
        columnMapping.put("PatientID", "patient_id");
        columnMapping.put("Name", "name");
        columnMapping.put("MeasurementID", "measurement_id");
        columnMapping.put("LabResultID", "result_id");
        columnMapping.put("LabResultGroupID", "group_id");
        columnMapping.put("DateTime", "date_time");
        columnMapping.put("Value", "value");
        columnMapping.put("GroupID", "group_id");
        columnMapping.put("GroupName", "group_name");
        columnMapping.put("ResultID", "result_id");
        columnMapping.put("ResultName", "result_name");
        columnMapping.put("Unit", "unit");
        columnMapping.put("ReferenceRange", "reference_range");

        try (Connection conn = getConnection()) {
            // Disable foreign key constraints temporarily
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = OFF");
            }

            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                String line;
                String[] headers = null;
                StringBuilder lineBuilder = new StringBuilder();
                boolean inQuotes = false;
                List<String[]> batch = new ArrayList<>();
                int batchSize = 1000;

                // Read headers
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);
                        if (c == '"') {
                            inQuotes = !inQuotes;
                        }
                        lineBuilder.append(c);
                    }
                    
                    if (!inQuotes) {
                        String completeLine = lineBuilder.toString().trim();
                        if (headers == null) {
                            headers = parseCsvLine(completeLine);
                            break;
                        }
                    }
                    lineBuilder.append("\n");
                }

                if (headers == null) {
                    LOGGER.warning("No headers found in CSV file: " + csvFile);
                    return;
                }

                // Map CSV headers to database columns
                String[] dbColumns = new String[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    dbColumns[i] = columnMapping.getOrDefault(headers[i], headers[i].toLowerCase());
                }

                // Prepare SQL statement
                String questionMarks = String.join(",", Collections.nCopies(headers.length, "?"));
                String columnNames = String.join(",", dbColumns);
                String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columnNames, questionMarks);

                conn.setAutoCommit(false);
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    lineBuilder.setLength(0);
                    while ((line = br.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        
                        for (int i = 0; i < line.length(); i++) {
                            char c = line.charAt(i);
                            if (c == '"') {
                                inQuotes = !inQuotes;
                            }
                            lineBuilder.append(c);
                        }
                        
                        if (!inQuotes) {
                            String completeLine = lineBuilder.toString().trim();
                            String[] values = parseCsvLine(completeLine);
                            
                            if (values.length != headers.length) {
                                LOGGER.warning("Skipping malformed line in " + csvFile + ": " + completeLine);
                                lineBuilder.setLength(0);
                                continue;
                            }

                            // Clean and validate values
                            for (int i = 0; i < values.length; i++) {
                                values[i] = values[i].trim().replaceAll("^\"|\"$", "");
                                if (values[i].isEmpty()) {
                                    values[i] = null;
                                }
                                pstmt.setObject(i + 1, values[i]);
                            }

                            pstmt.addBatch();
                            batch.add(values);

                            if (batch.size() >= batchSize) {
                                pstmt.executeBatch();
                                batch.clear();
                            }
                            lineBuilder.setLength(0);
                        }
                    }

                    if (!batch.isEmpty()) {
                        pstmt.executeBatch();
                    }

                    // Commit the transaction
                    conn.commit();

                    // Re-enable foreign key constraints
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("PRAGMA foreign_keys = ON");
                    }

                    // Validate foreign key constraints
                    validateForeignKeys(conn, tableName, csvFile);

                } catch (SQLException e) {
                    conn.rollback();
                    LOGGER.severe("Error executing batch insert for " + tableName + ": " + e.getMessage());
                    throw new RuntimeException(e);
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException | IOException e) {
            LOGGER.severe("Error importing CSV file " + csvFile + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                value.append(c);
            } else if (c == ',' && !inQuotes) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        values.add(value.toString());
        
        return values.toArray(new String[0]);
    }

    private LocalDateTime parseDateTime(String dateStr) {
        dateStr = dateStr.trim();
        if (dateStr.startsWith("\"")) {
            dateStr = dateStr.substring(1);
        }
        if (dateStr.endsWith("\"")) {
            dateStr = dateStr.substring(0, dateStr.length() - 1);
        }
        
        // Remove any leading/trailing whitespace or newlines
        dateStr = dateStr.trim();
        
        // Try different date formats
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("dd-MM-yyyyHH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyyHH:mm.S"),
            DateTimeFormatter.ofPattern("dd-MM-yyyyHH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm.S"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        
        throw new IllegalArgumentException("Unable to parse date: " + dateStr);
    }
}

