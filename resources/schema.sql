-- Database schema for JDM Patient Monitoring Application

-- Patients table
CREATE TABLE IF NOT EXISTS patients (
    patient_id TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

-- Lab Result Groups table
CREATE TABLE IF NOT EXISTS lab_result_groups (
    group_id TEXT PRIMARY KEY,
    group_name TEXT NOT NULL
);

-- Lab Results table
CREATE TABLE IF NOT EXISTS lab_results (
    result_id TEXT PRIMARY KEY,
    group_id TEXT,
    patient_id TEXT,
    result_name TEXT NOT NULL,
    unit TEXT,
    result_name_english TEXT,
    FOREIGN KEY (group_id) REFERENCES lab_result_groups(group_id),
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
);

-- Measurements table
CREATE TABLE IF NOT EXISTS measurements (
    measurement_id TEXT PRIMARY KEY,
    result_id TEXT,
    date_time TEXT,
    value TEXT,
    FOREIGN KEY (result_id) REFERENCES lab_results(result_id)
);

-- CMAS Scores table
CREATE TABLE IF NOT EXISTS cmas_scores (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    patient_id TEXT,
    date TEXT,
    score REAL,
    category TEXT,  -- "CMAS Score > 10" or "CMAS Score 4-9"
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
);

-- Create indices for better query performance
CREATE INDEX IF NOT EXISTS idx_lab_results_patient_id ON lab_results(patient_id);
CREATE INDEX IF NOT EXISTS idx_lab_results_group_id ON lab_results(group_id);
CREATE INDEX IF NOT EXISTS idx_measurements_result_id ON measurements(result_id);
CREATE INDEX IF NOT EXISTS idx_cmas_scores_patient_id ON cmas_scores(patient_id);
CREATE INDEX IF NOT EXISTS idx_cmas_scores_date ON cmas_scores(date);
