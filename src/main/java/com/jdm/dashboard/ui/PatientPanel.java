package com.jdm.dashboard.ui;

import com.jdm.dashboard.model.Patient;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel for displaying patient information
 */
public class PatientPanel extends JPanel {

    private JLabel patientNameLabel;
    private JLabel patientIdLabel;
    private JPanel infoPanel;
    private JLabel statusLabel;
    
    private Map<String, JComponent> dynamicFields = new HashMap<>();
    
    /**
     * Constructor
     */
    public PatientPanel() {
        setLayout(new BorderLayout());
        initUI();
    }
    
    /**
     * Initialize UI components
     */
    private void initUI() {
        try {
            // Create header panel
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Add patient name and ID labels
            patientNameLabel = new JLabel("Select a patient");
            patientNameLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
            headerPanel.add(patientNameLabel, BorderLayout.NORTH);
            
            patientIdLabel = new JLabel("ID: ");
            headerPanel.add(patientIdLabel, BorderLayout.CENTER);
            
            // Add status label
            statusLabel = new JLabel("Ready");
            statusLabel.setForeground(Color.GRAY);
            headerPanel.add(statusLabel, BorderLayout.SOUTH);
            
            add(headerPanel, BorderLayout.NORTH);
            
            // Create info panel
            infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Add some placeholder content
            JLabel placeholderLabel = new JLabel("No patient selected");
            infoPanel.add(placeholderLabel);
            
            JScrollPane scrollPane = new JScrollPane(infoPanel);
            add(scrollPane, BorderLayout.CENTER);
        } catch (Exception e) {
            showError("Error initializing UI: " + e.getMessage());
        }
    }
    
    /**
     * Update the panel with a new patient
     */
    public void updatePatient(Patient patient) {
        try {
            if (patient == null) {
                showNoPatientSelected();
                return;
            }
            
            setStatus("Updating patient information...");
            
            // Validate patient data
            if (!isValidPatient(patient)) {
                showError("Invalid patient data");
                return;
            }
            
            // Update header
            updateHeader(patient);
            
            // Clear info panel
            infoPanel.removeAll();
            dynamicFields.clear();
            
            // Add patient summary section
            addSummarySection(patient);
            
            // Add disease information section
            addDiseaseInfoSection();
            
            // Add monitoring information section
            addMonitoringSection();
            
            // Add treatment information section
            addTreatmentSection();
            
            // Add notes section
            addNotesSection();
            
            // Add some spacing
            infoPanel.add(Box.createVerticalStrut(20));
            
            // Update UI
            infoPanel.revalidate();
            infoPanel.repaint();
            
            setStatus("Patient information updated");
        } catch (Exception e) {
            showError("Error updating patient information: " + e.getMessage());
        }
    }
    
    /**
     * Validate patient data
     */
    private boolean isValidPatient(Patient patient) {
        return patient != null && 
               patient.getPatientId() != null && !patient.getPatientId().trim().isEmpty() &&
               patient.getName() != null && !patient.getName().trim().isEmpty();
    }
    
    /**
     * Update the header with patient information
     */
    private void updateHeader(Patient patient) {
        patientNameLabel.setText(patient.getName());
        patientIdLabel.setText("ID: " + patient.getPatientId());
    }
    
    /**
     * Add the patient summary section
     */
    private void addSummarySection(Patient patient) {
        JPanel summaryPanel = createSectionPanel("Patient Summary");
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        addFormField(formPanel, gbc, "Patient ID:", patient.getPatientId());
        addFormField(formPanel, gbc, "Name:", patient.getName());
        addFormField(formPanel, gbc, "Diagnosis:", "Juvenile Dermatomyositis (JDM)");
        addFormField(formPanel, gbc, "Current Status:", "Active Monitoring");
        
        summaryPanel.add(formPanel, BorderLayout.CENTER);
        infoPanel.add(summaryPanel);
    }
    
    /**
     * Add the disease information section
     */
    private void addDiseaseInfoSection() {
        JPanel diseaseInfoPanel = createSectionPanel("Disease Information");
        
        JTextArea diseaseInfoText = new JTextArea(
            "Juvenile Dermatomyositis (JDM) is a rare autoimmune disease that causes muscle weakness " +
            "and skin rash. It affects approximately 3 in 1,000,000 children per year.\n\n" +
            "The CMAS (Childhood Myositis Assessment Scale) is used to measure muscle strength and " +
            "endurance in children with JDM. Higher scores indicate better muscle function."
        );
        diseaseInfoText.setEditable(false);
        diseaseInfoText.setLineWrap(true);
        diseaseInfoText.setWrapStyleWord(true);
        diseaseInfoText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        diseaseInfoText.setBackground(diseaseInfoPanel.getBackground());
        
        diseaseInfoPanel.add(diseaseInfoText, BorderLayout.CENTER);
        infoPanel.add(diseaseInfoPanel);
    }
    
    /**
     * Add the monitoring information section
     */
    private void addMonitoringSection() {
        JPanel monitoringPanel = createSectionPanel("Monitoring Information");
        
        JPanel monitoringFormPanel = new JPanel(new GridBagLayout());
        GridBagConstraints mgbc = new GridBagConstraints();
        mgbc.anchor = GridBagConstraints.WEST;
        mgbc.insets = new Insets(5, 5, 5, 5);
        
        addFormField(monitoringFormPanel, mgbc, "Monitoring Frequency:", "Monthly");
        addFormField(monitoringFormPanel, mgbc, "Last Assessment:", "See CMAS Scores tab");
        addFormField(monitoringFormPanel, mgbc, "Next Assessment:", "To be scheduled");
        
        monitoringPanel.add(monitoringFormPanel, BorderLayout.CENTER);
        infoPanel.add(monitoringPanel);
    }
    
    /**
     * Add the treatment information section
     */
    private void addTreatmentSection() {
        JPanel treatmentPanel = createSectionPanel("Treatment Information");
        
        JTextArea treatmentText = new JTextArea(
            "Treatment information is not available in this view. Please consult the patient's " +
            "medical record for detailed treatment information."
        );
        treatmentText.setEditable(false);
        treatmentText.setLineWrap(true);
        treatmentText.setWrapStyleWord(true);
        treatmentText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        treatmentText.setBackground(treatmentPanel.getBackground());
        
        treatmentPanel.add(treatmentText, BorderLayout.CENTER);
        infoPanel.add(treatmentPanel);
    }
    
    /**
     * Add the notes section
     */
    private void addNotesSection() {
        JPanel notesPanel = createSectionPanel("Notes");
        
        JTextArea notesTextArea = new JTextArea();
        notesTextArea.setRows(5);
        notesTextArea.setLineWrap(true);
        notesTextArea.setWrapStyleWord(true);
        
        JScrollPane notesScrollPane = new JScrollPane(notesTextArea);
        notesPanel.add(notesScrollPane, BorderLayout.CENTER);
        
        dynamicFields.put("notes", notesTextArea);
        
        infoPanel.add(notesPanel);
    }
    
    /**
     * Show the no patient selected state
     */
    private void showNoPatientSelected() {
        patientNameLabel.setText("Select a patient");
        patientIdLabel.setText("ID: ");
        setStatus("No patient selected");
        
        infoPanel.removeAll();
        dynamicFields.clear();
        
        JLabel placeholderLabel = new JLabel("No patient selected");
        infoPanel.add(placeholderLabel);
        
        infoPanel.revalidate();
        infoPanel.repaint();
    }
    
    /**
     * Show an error message
     */
    private void showError(String message) {
        patientNameLabel.setText("Error");
        patientIdLabel.setText("");
        setStatus("Error");
        
        infoPanel.removeAll();
        dynamicFields.clear();
        
        JLabel errorLabel = new JLabel(message);
        errorLabel.setForeground(Color.RED);
        infoPanel.add(errorLabel);
        
        infoPanel.revalidate();
        infoPanel.repaint();
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
     * Create a section panel with a title
     */
    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        
        return panel;
    }
    
    /**
     * Add a form field to a panel
     */
    private void addFormField(JPanel panel, GridBagConstraints gbc, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(new JLabel(label), gbc);
        
        gbc.gridx = 1;
        JLabel valueLabel = new JLabel(value != null ? value : "N/A");
        panel.add(valueLabel, gbc);
    }
}
