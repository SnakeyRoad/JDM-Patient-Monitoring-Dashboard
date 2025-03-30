package com.jdm.dashboard.ui;

import com.jdm.dashboard.database.DatabaseManager;
import com.jdm.dashboard.model.CMASScore;
import com.jdm.dashboard.model.LabResult;
import com.jdm.dashboard.model.Patient;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Main dashboard panel for the application
 */
public class DashboardPanel extends JPanel {
    
    private final DatabaseManager dbManager;
    private JComboBox<Patient> patientSelector;
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;
    
    private CMASChartPanel cmasChartPanel;
    private LabResultPanel labResultPanel;
    private PatientPanel patientPanel;
    
    /**
     * Constructor
     */
    public DashboardPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        setLayout(new BorderLayout());
        initUI();
    }
    
    /**
     * Initialize the UI components
     */
    private void initUI() {
        try {
            // Create the top panel with patient selector
            JPanel topPanel = createTopPanel();
            add(topPanel, BorderLayout.NORTH);
            
            // Create status label
            statusLabel = new JLabel("Ready");
            statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            add(statusLabel, BorderLayout.SOUTH);
            
            // Create tabbed pane for different views
            tabbedPane = new JTabbedPane();
            
            // Create panels for each tab
            patientPanel = new PatientPanel();
            cmasChartPanel = new CMASChartPanel(dbManager);
            labResultPanel = new LabResultPanel(dbManager);
            
            // Add tabs
            tabbedPane.addTab("Patient Overview", new JScrollPane(patientPanel));
            tabbedPane.addTab("CMAS Scores", new JScrollPane(cmasChartPanel));
            tabbedPane.addTab("Lab Results", new JScrollPane(labResultPanel));
            
            add(tabbedPane, BorderLayout.CENTER);
            
            // Load patients and select the first one
            loadPatients();
        } catch (Exception e) {
            showError("Error initializing UI: " + e.getMessage());
        }
    }
    
    /**
     * Create the top panel with patient selector
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        try {
            // Add patient selection dropdown
            JLabel patientLabel = new JLabel("Patient:");
            panel.add(patientLabel);
            
            patientSelector = new JComboBox<>();
            patientSelector.setPreferredSize(new Dimension(250, 25));
            patientSelector.addActionListener(e -> {
                Patient selectedPatient = (Patient) patientSelector.getSelectedItem();
                if (selectedPatient != null) {
                    loadPatientData(selectedPatient);
                }
            });
            
            panel.add(patientSelector);
            
            // Add refresh button
            JButton refreshButton = new JButton("Refresh");
            refreshButton.addActionListener(e -> refreshData());
            panel.add(refreshButton);
        } catch (Exception e) {
            showError("Error creating top panel: " + e.getMessage());
        }
        
        return panel;
    }
    
    /**
     * Load the list of patients into the selector
     */
    private void loadPatients() {
        try {
            setStatus("Loading patients...");
            
            List<Patient> patients = dbManager.getAllPatients();
            
            patientSelector.removeAllItems();
            for (Patient patient : patients) {
                if (patient != null) {
                    patientSelector.addItem(patient);
                }
            }
            
            // Select the first patient
            if (!patients.isEmpty()) {
                patientSelector.setSelectedIndex(0);
            } else {
                setStatus("No patients found");
                clearAllPanels();
            }
        } catch (Exception e) {
            showError("Error loading patients: " + e.getMessage());
            clearAllPanels();
        }
    }
    
    /**
     * Load data for the selected patient
     */
    private void loadPatientData(Patient patient) {
        if (patient == null) {
            clearAllPanels();
            setStatus("No patient selected");
            return;
        }
        
        try {
            setStatus("Loading patient data...");
            
            // Get CMAS scores for the patient
            List<CMASScore> cmasScores = dbManager.getCMASScoresForPatient(patient.getPatientId());
            
            // Get lab results for the patient
            List<LabResult> labResults = dbManager.getLabResultsForPatient(patient.getPatientId());
            
            // Update UI components
            patientPanel.updatePatient(patient);
            cmasChartPanel.updateCMASScores(patient, cmasScores);
            labResultPanel.updateLabResults(patient, labResults);
            
            setStatus("Data loaded successfully");
        } catch (Exception e) {
            showError("Error loading patient data: " + e.getMessage());
            clearAllPanels();
        }
    }
    
    /**
     * Clear all panels
     */
    private void clearAllPanels() {
        patientPanel.updatePatient(null);
        cmasChartPanel.updateCMASScores(null, null);
        labResultPanel.updateLabResults(null, null);
    }
    
    /**
     * Refresh the displayed data
     */
    public void refreshData() {
        try {
            setStatus("Refreshing data...");
            
            Patient selectedPatient = (Patient) patientSelector.getSelectedItem();
            
            // Reload patients (in case there are new ones)
            loadPatients();
            
            // Reselect the previously selected patient
            if (selectedPatient != null) {
                for (int i = 0; i < patientSelector.getItemCount(); i++) {
                    Patient patient = patientSelector.getItemAt(i);
                    if (patient != null && patient.getPatientId().equals(selectedPatient.getPatientId())) {
                        patientSelector.setSelectedIndex(i);
                        break;
                    }
                }
            }
            
            setStatus("Data refreshed successfully");
        } catch (Exception e) {
            showError("Error refreshing data: " + e.getMessage());
        }
    }
    
    /**
     * Set the status message
     */
    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }
    
    /**
     * Show an error message
     */
    private void showError(String message) {
        setStatus("Error: " + message);
        JOptionPane.showMessageDialog(this,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE);
    }
}
