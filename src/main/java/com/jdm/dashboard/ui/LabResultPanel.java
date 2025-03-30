package com.jdm.dashboard.ui;

import com.jdm.dashboard.database.DatabaseManager;
import com.jdm.dashboard.model.LabResult;
import com.jdm.dashboard.model.LabResultGroup;
import com.jdm.dashboard.model.Measurement;
import com.jdm.dashboard.model.Patient;
import com.jdm.dashboard.utils.DateUtils;
import com.jdm.dashboard.utils.ChartGenerator;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Panel for displaying lab results.
 * Provides visualization and analysis of lab results over time.
 */
public class LabResultPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(LabResultPanel.class.getName());
    private static final int SELECTOR_WIDTH = 200;
    private static final int SELECTOR_HEIGHT = 25;
    
    private final DatabaseManager dbManager;
    private Patient currentPatient;
    private List<LabResult> currentResults = new ArrayList<>();
    
    private JComboBox<String> groupSelector;
    private JComboBox<String> resultSelector;
    private JPanel chartPanel;
    private JTable resultsTable;
    
    /**
     * Constructor
     * @param dbManager The database manager instance
     * @throws IllegalArgumentException if dbManager is null
     */
    public LabResultPanel(DatabaseManager dbManager) {
        if (dbManager == null) {
            throw new IllegalArgumentException("Database manager cannot be null");
        }
        this.dbManager = dbManager;
        setLayout(new BorderLayout());
        initUI();
    }
    
    /**
     * Initialize UI components
     */
    private void initUI() {
        try {
            // Create top control panel
            JPanel controlPanel = createControlPanel();
            add(controlPanel, BorderLayout.NORTH);
            
            // Create split pane for chart and table
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setResizeWeight(0.6);
            
            // Create chart panel
            chartPanel = new JPanel(new BorderLayout());
            chartPanel.setBorder(BorderFactory.createTitledBorder("Lab Result Trends"));
            splitPane.setTopComponent(chartPanel);
            
            // Create table panel
            JPanel tablePanel = createTablePanel();
            splitPane.setBottomComponent(tablePanel);
            
            add(splitPane, BorderLayout.CENTER);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing UI", e);
            showError("Error initializing UI: " + e.getMessage());
        }
    }
    
    /**
     * Create the control panel
     * @return The control panel
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        try {
            // Add group selector
            JLabel groupLabel = new JLabel("Group:");
            panel.add(groupLabel);
            
            groupSelector = new JComboBox<>();
            groupSelector.setPreferredSize(new Dimension(SELECTOR_WIDTH, SELECTOR_HEIGHT));
            groupSelector.addActionListener(e -> updateResultSelector());
            panel.add(groupSelector);
            
            // Add result selector
            JLabel resultLabel = new JLabel("Result:");
            panel.add(resultLabel);
            
            resultSelector = new JComboBox<>();
            resultSelector.setPreferredSize(new Dimension(SELECTOR_WIDTH, SELECTOR_HEIGHT));
            resultSelector.addActionListener(e -> updateChart());
            panel.add(resultSelector);
            
            // Add export button
            JButton exportButton = new JButton("Export Data");
            exportButton.addActionListener(e -> exportData());
            panel.add(exportButton);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating control panel", e);
            showError("Error creating control panel: " + e.getMessage());
        }
        
        return panel;
    }
    
    /**
     * Create the table panel
     * @return The table panel
     */
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Lab Result History"));
        
        try {
            // Create table model
            String[] columnNames = {"Date", "Result", "Value", "Unit"};
            Object[][] data = new Object[0][4];
            
            DefaultTableModel model = new DefaultTableModel(data, columnNames) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            
            resultsTable = new JTable(model);
            resultsTable.setFillsViewportHeight(true);
            
            // Add sorter
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>((DefaultTableModel) resultsTable.getModel());
            resultsTable.setRowSorter(sorter);
            
            JScrollPane scrollPane = new JScrollPane(resultsTable);
            panel.add(scrollPane, BorderLayout.CENTER);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating table panel", e);
            showError("Error creating table panel: " + e.getMessage());
        }
        
        return panel;
    }
    
    /**
     * Update the result selector based on the selected group
     */
    private void updateResultSelector() {
        try {
            if (currentPatient == null) {
                LOGGER.warning("Cannot update result selector: patient is null");
                resultSelector.removeAllItems();
                return;
            }
            
            if (currentResults.isEmpty()) {
                LOGGER.warning("Cannot update result selector: no results available for patient " + currentPatient.getPatientId());
                resultSelector.removeAllItems();
                return;
            }
            
            String selectedGroup = (String) groupSelector.getSelectedItem();
            if (selectedGroup == null) {
                LOGGER.warning("No group selected");
                resultSelector.removeAllItems();
                return;
            }
            
            // Get the group ID
            final String groupId = currentResults.stream()
                .filter(result -> result != null && result.getGroupId() != null && 
                    selectedGroup.equals(getGroupName(result.getGroupId())))
                .map(LabResult::getGroupId)
                .findFirst()
                .orElse(null);
            
            if (groupId == null) {
                LOGGER.warning("No matching group ID found for selected group: " + selectedGroup);
                resultSelector.removeAllItems();
                return;
            }
            
            // Filter results by group
            List<LabResult> groupResults = currentResults.stream()
                .filter(r -> r != null && r.getGroupId() != null && r.getGroupId().equals(groupId))
                .collect(Collectors.toList());
            
            // Update result selector
            resultSelector.removeAllItems();
            for (LabResult result : groupResults) {
                if (result != null) {
                    String displayName = result.getResultNameEnglish();
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = result.getResultName();
                    }
                    if (displayName != null && !displayName.isEmpty()) {
                        resultSelector.addItem(displayName);
                    }
                }
            }
            
            if (resultSelector.getItemCount() > 0) {
                resultSelector.setSelectedIndex(0);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating result selector", e);
            showError("Error updating result selector: " + e.getMessage());
        }
    }
    
    /**
     * Update the chart with the selected result
     */
    private void updateChart() {
        try {
            if (currentPatient == null) {
                LOGGER.warning("Cannot update chart: patient is null");
                showError("No patient selected");
                return;
            }
            
            if (currentResults.isEmpty()) {
                LOGGER.warning("Cannot update chart: no results available for patient " + currentPatient.getPatientId());
                showError("No lab results available");
                return;
            }
            
            String selectedGroup = (String) groupSelector.getSelectedItem();
            String selectedResult = (String) resultSelector.getSelectedItem();
            
            if (selectedGroup == null || selectedResult == null) {
                LOGGER.warning("No group or result selected");
                showError("Please select a group and result");
                return;
            }
            
            // Find the selected lab result
            LabResult labResult = null;
            for (LabResult result : currentResults) {
                if (result == null) continue;
                
                String displayName = result.getResultNameEnglish();
                if (displayName == null || displayName.isEmpty()) {
                    displayName = result.getResultName();
                }
                
                if (displayName != null && selectedResult.equals(displayName) && 
                    selectedGroup.equals(getGroupName(result.getGroupId()))) {
                    labResult = result;
                    break;
                }
            }
            
            if (labResult == null) {
                LOGGER.warning("No matching lab result found for selected group and result");
                showError("No data available");
                return;
            }
            
            if (labResult.getMeasurements().isEmpty()) {
                LOGGER.warning("No measurements available for lab result: " + labResult.getResultId());
                showError("No measurements available");
                return;
            }
            
            // Create chart using ChartGenerator
            JPanel chartPanelComponent = ChartGenerator.createLabResultChart(currentPatient, labResult);
            
            // Update the chart panel
            chartPanel.removeAll();
            chartPanel.add(chartPanelComponent, BorderLayout.CENTER);
            chartPanel.revalidate();
            chartPanel.repaint();
            
            // Update table
            updateTable(labResult);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating chart", e);
            showError("Error updating chart: " + e.getMessage());
        }
    }
    
    /**
     * Update the table with measurements for the selected lab result
     * @param labResult The lab result to display
     */
    private void updateTable(LabResult labResult) {
        try {
            if (labResult == null) {
                LOGGER.warning("Cannot update table: lab result is null");
                return;
            }
            
            DefaultTableModel model = (DefaultTableModel) resultsTable.getModel();
            model.setRowCount(0);
            
            if (labResult.getMeasurements() == null) {
                LOGGER.warning("No measurements available for lab result: " + labResult.getResultId());
                return;
            }
            
            // Sort measurements by date (newest first)
            List<Measurement> sortedMeasurements = labResult.getMeasurements().stream()
                .filter(m -> m != null && m.getDateTime() != null)
                .sorted(Comparator.comparing(Measurement::getDateTime).reversed())
                .collect(Collectors.toList());
            
            String displayName = labResult.getResultNameEnglish();
            if (displayName == null || displayName.isEmpty()) {
                displayName = labResult.getResultName();
            }
            
            for (Measurement measurement : sortedMeasurements) {
                if (measurement != null && measurement.getDateTime() != null) {
                    model.addRow(new Object[]{
                        DateUtils.formatDateTime(measurement.getDateTime()),
                        displayName,
                        measurement.getValue(),
                        labResult.getUnit()
                    });
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating table", e);
            showError("Error updating table: " + e.getMessage());
        }
    }
    
    /**
     * Export the lab results data to a CSV file
     */
    private void exportData() {
        try {
            if (currentPatient == null) {
                LOGGER.warning("Cannot export data: patient is null");
                JOptionPane.showMessageDialog(this,
                    "No patient selected.",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (currentResults.isEmpty()) {
                LOGGER.warning("Cannot export data: no results available for patient " + currentPatient.getPatientId());
                JOptionPane.showMessageDialog(this,
                    "No data available to export.",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Lab Results Data");
            
            // Set default filename
            String defaultFilename = "lab_results_" + currentPatient.getName().replace(" ", "_") + ".csv";
            fileChooser.setSelectedFile(new File(defaultFilename));
            
            int result = fileChooser.showSaveDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                
                // Create CSV content
                StringBuilder csv = new StringBuilder();
                csv.append("Date,Group,Result,Value,Unit\n");
                
                for (LabResult labResult : currentResults) {
                    if (labResult == null) continue;
                    
                    String groupName = getGroupName(labResult.getGroupId());
                    String resultName = labResult.getResultNameEnglish() != null && !labResult.getResultNameEnglish().isEmpty() ?
                        labResult.getResultNameEnglish() : labResult.getResultName();
                    
                    if (labResult.getMeasurements() != null) {
                        for (Measurement measurement : labResult.getMeasurements()) {
                            if (measurement != null && measurement.getDateTime() != null) {
                                csv.append(DateUtils.formatDateTime(measurement.getDateTime()))
                                   .append(",")
                                   .append(groupName)
                                   .append(",")
                                   .append(resultName)
                                   .append(",")
                                   .append(measurement.getValue())
                                   .append(",")
                                   .append(labResult.getUnit())
                                   .append("\n");
                            }
                        }
                    }
                }
                
                // Write to file
                Files.write(file.toPath(), csv.toString().getBytes());
                
                LOGGER.info("Data exported successfully to: " + file.getAbsolutePath());
                JOptionPane.showMessageDialog(this,
                    "Data exported successfully to:\n" + file.getAbsolutePath(),
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error exporting data", e);
            JOptionPane.showMessageDialog(this,
                "Error exporting data: " + e.getMessage(),
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Get the group name for a group ID
     * @param groupId The group ID
     * @return The group name, or "Unknown Group" if not found
     */
    private String getGroupName(String groupId) {
        try {
            if (groupId == null) {
                LOGGER.warning("Cannot get group name: group ID is null");
                return "Unknown Group";
            }
            
            // Get all groups from database
            List<LabResultGroup> groups = dbManager.getAllLabResultGroups();
            
            for (LabResultGroup group : groups) {
                if (group != null && group.getGroupId() != null && group.getGroupId().equals(groupId)) {
                    return group.getGroupName();
                }
            }
            
            LOGGER.warning("No group found for ID: " + groupId);
            return "Unknown Group";
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting group name", e);
            return "Unknown Group";
        }
    }
    
    /**
     * Show an error message
     * @param message The error message to display
     */
    private void showError(String message) {
        chartPanel.removeAll();
        chartPanel.add(new JLabel(message, JLabel.CENTER), BorderLayout.CENTER);
        chartPanel.revalidate();
        chartPanel.repaint();
    }
    
    /**
     * Update the panel with new lab results
     * @param patient The patient whose results to display
     * @param results List of lab results
     */
    public void updateLabResults(Patient patient, List<LabResult> results) {
        try {
            if (patient == null) {
                LOGGER.warning("Cannot update lab results: patient is null");
                return;
            }
            
            this.currentPatient = patient;
            this.currentResults = results != null ? new ArrayList<>(results) : new ArrayList<>();
            
            // Get unique group names
            Map<String, String> groupNames = this.currentResults.stream()
                .filter(r -> r != null && r.getGroupId() != null)
                .map(LabResult::getGroupId)
                .distinct()
                .collect(Collectors.toMap(
                    groupId -> groupId,
                    this::getGroupName
                ));
            
            // Update group selector
            groupSelector.removeAllItems();
            for (String groupName : groupNames.values()) {
                if (groupName != null && !groupName.isEmpty()) {
                    groupSelector.addItem(groupName);
                }
            }
            
            if (groupSelector.getItemCount() > 0) {
                groupSelector.setSelectedIndex(0);
            }
            
            // Result selector will be updated by the group selector's action listener
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating lab results", e);
            showError("Error updating lab results: " + e.getMessage());
        }
    }
}
