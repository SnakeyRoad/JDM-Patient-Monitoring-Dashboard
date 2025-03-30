package com.jdm.dashboard.ui;

import com.jdm.dashboard.database.DatabaseManager;
import com.jdm.dashboard.model.CMASScore;
import com.jdm.dashboard.model.Patient;
import com.jdm.dashboard.utils.DateUtils;
import com.jdm.dashboard.utils.ChartGenerator;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.io.File;


/**
 * Panel for displaying CMAS score charts and data.
 * Provides visualization and analysis of CMAS scores over time.
 */
public class CMASChartPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(CMASChartPanel.class.getName());
    private static final int STATS_PANEL_WIDTH = 200;
    
    private Patient currentPatient;
    private List<CMASScore> currentScores = new ArrayList<>();
    
    private JPanel chartPanel;
    private JPanel tablePanel;
    private JPanel statsPanel;
    
    private JComboBox<String> timeRangeSelector;
    private JTable scoresTable;
    private JLabel averageScoreLabel;
    private JLabel maxScoreLabel;
    private JLabel minScoreLabel;
    private JLabel trendLabel;
    
    /**
     * Constructor
     * @param dbManager The database manager instance
     * @throws IllegalArgumentException if dbManager is null
     */
    public CMASChartPanel(DatabaseManager dbManager) {
        if (dbManager == null) {
            throw new IllegalArgumentException("Database manager cannot be null");
        }
        setLayout(new BorderLayout());
        initUI();
    }
    
    /**
     * Initialize the UI components
     */
    private void initUI() {
        try {
            // Create top control panel
            JPanel controlPanel = createControlPanel();
            add(controlPanel, BorderLayout.NORTH);
            
            // Create chart panel
            chartPanel = new JPanel(new BorderLayout());
            chartPanel.setBorder(BorderFactory.createTitledBorder("CMAS Score Trends"));
            
            // Create stats panel
            statsPanel = createStatsPanel();
            
            // Create table panel
            tablePanel = createTablePanel();
            
            // Add panels to a split pane
            JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, tablePanel);
            mainSplitPane.setResizeWeight(0.6);
            
            // Add main panel and stats panel
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(mainSplitPane, BorderLayout.CENTER);
            mainPanel.add(statsPanel, BorderLayout.EAST);
            
            add(mainPanel, BorderLayout.CENTER);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing UI", e);
            add(new JLabel("Error initializing UI: " + e.getMessage(), JLabel.CENTER), BorderLayout.CENTER);
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
            // Add time range selector
            JLabel timeRangeLabel = new JLabel("Time Range:");
            panel.add(timeRangeLabel);
            
            timeRangeSelector = new JComboBox<>(new String[]{
                "All Time",
                "Last Year",
                "Last 6 Months",
                "Last 3 Months",
                "Last Month"
            });
            
            timeRangeSelector.addActionListener(e -> updateChart());
            panel.add(timeRangeSelector);
            
            // Add export button
            JButton exportButton = new JButton("Export Data");
            exportButton.addActionListener(e -> exportData());
            panel.add(exportButton);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating control panel", e);
            panel.add(new JLabel("Error creating controls: " + e.getMessage()));
        }
        
        return panel;
    }
    
    /**
     * Create the stats panel
     * @return The stats panel
     */
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Statistics"));
        panel.setPreferredSize(new Dimension(STATS_PANEL_WIDTH, 0));
        
        try {
            averageScoreLabel = new JLabel("Average: ");
            maxScoreLabel = new JLabel("Maximum: ");
            minScoreLabel = new JLabel("Minimum: ");
            trendLabel = new JLabel("Trend: ");
            
            // Add some padding
            panel.add(Box.createVerticalStrut(10));
            panel.add(averageScoreLabel);
            panel.add(Box.createVerticalStrut(5));
            panel.add(maxScoreLabel);
            panel.add(Box.createVerticalStrut(5));
            panel.add(minScoreLabel);
            panel.add(Box.createVerticalStrut(5));
            panel.add(trendLabel);
            panel.add(Box.createVerticalStrut(10));
            
            // Add interpretation panel
            JPanel interpretationPanel = new JPanel();
            interpretationPanel.setLayout(new BoxLayout(interpretationPanel, BoxLayout.Y_AXIS));
            interpretationPanel.setBorder(BorderFactory.createTitledBorder("Interpretation"));
            
            JTextArea interpretationText = new JTextArea(
                "CMAS Score Interpretation:\n\n" +
                "0-15: Severe weakness\n" +
                "16-30: Moderate weakness\n" +
                "31-45: Mild weakness\n" +
                "46-52: Normal strength"
            );
            interpretationText.setEditable(false);
            interpretationText.setBackground(panel.getBackground());
            interpretationText.setWrapStyleWord(true);
            interpretationText.setLineWrap(true);
            
            interpretationPanel.add(interpretationText);
            panel.add(interpretationPanel);
            
            // Add filler to push everything to the top
            panel.add(Box.createVerticalGlue());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating stats panel", e);
            panel.add(new JLabel("Error creating statistics: " + e.getMessage()));
        }
        
        return panel;
    }
    
    /**
     * Create the table panel
     * @return The table panel
     */
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("CMAS Score History"));
        
        try {
            // Create table model
            String[] columnNames = {"Date", "Score", "Category"};
            Object[][] data = new Object[0][3];
            
            scoresTable = new JTable(data, columnNames);
            scoresTable.setFillsViewportHeight(true);
            
            JScrollPane scrollPane = new JScrollPane(scoresTable);
            panel.add(scrollPane, BorderLayout.CENTER);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating table panel", e);
            panel.add(new JLabel("Error creating table: " + e.getMessage()), BorderLayout.CENTER);
        }
        
        return panel;
    }
    
    /**
     * Update the chart with the current data
     */
    private void updateChart() {
        if (currentPatient == null) {
            LOGGER.warning("Cannot update chart: patient is null");
            showError("No patient selected");
            return;
        }
        
        if (currentScores.isEmpty()) {
            LOGGER.warning("Cannot update chart: no scores available for patient " + currentPatient.getPatientId());
            showError("No CMAS scores available");
            return;
        }
        
        try {
            // Filter data based on selected time range
            List<CMASScore> filteredScores = filterScoresByTimeRange();
            
            if (filteredScores.isEmpty()) {
                LOGGER.warning("No scores found for selected time range");
                showError("No data available for selected time range");
                return;
            }
            
            // Create chart using ChartGenerator
            JPanel chartPanelComponent = ChartGenerator.createCMASChart(currentPatient, filteredScores);
            
            // Update the chart panel
            chartPanel.removeAll();
            chartPanel.add(chartPanelComponent, BorderLayout.CENTER);
            chartPanel.revalidate();
            chartPanel.repaint();
            
            // Update statistics and table
            updateStatistics(filteredScores);
            updateTable(filteredScores);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating chart", e);
            showError("Error updating chart: " + e.getMessage());
        }
    }
    
    /**
     * Filter scores based on selected time range
     * @return List of filtered scores
     */
    private List<CMASScore> filterScoresByTimeRange() {
        if (currentScores.isEmpty()) {
            return new ArrayList<>();
        }
        
        String selectedRange = (String) timeRangeSelector.getSelectedItem();
        if (selectedRange == null) {
            LOGGER.warning("No time range selected");
            return currentScores;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffDate;
        
        switch (selectedRange) {
            case "Last Year":
                cutoffDate = now.minusYears(1);
                break;
            case "Last 6 Months":
                cutoffDate = now.minusMonths(6);
                break;
            case "Last 3 Months":
                cutoffDate = now.minusMonths(3);
                break;
            case "Last Month":
                cutoffDate = now.minusMonths(1);
                break;
            default:
                return currentScores;
        }
        
        return currentScores.stream()
            .filter(score -> score != null && score.getDate() != null && !score.getDate().isBefore(cutoffDate))
            .collect(Collectors.toList());
    }
    
    /**
     * Update statistics display
     * @param scores List of scores to analyze
     */
    private void updateStatistics(List<CMASScore> scores) {
        if (scores == null || scores.isEmpty()) {
            averageScoreLabel.setText("Average: N/A");
            maxScoreLabel.setText("Maximum: N/A");
            minScoreLabel.setText("Minimum: N/A");
            trendLabel.setText("Trend: N/A");
            return;
        }
        
        try {
            // Calculate statistics
            double average = scores.stream()
                .mapToDouble(CMASScore::getScore)
                .average()
                .orElse(0.0);
                
            double max = scores.stream()
                .mapToDouble(CMASScore::getScore)
                .max()
                .orElse(0.0);
                
            double min = scores.stream()
                .mapToDouble(CMASScore::getScore)
                .min()
                .orElse(0.0);
                
            // Calculate trend
            String trend;
            if (scores.size() >= 2) {
                double firstScore = scores.get(0).getScore();
                double lastScore = scores.get(scores.size() - 1).getScore();
                double difference = lastScore - firstScore;
                
                if (Math.abs(difference) < 0.1) {
                    trend = "Stable";
                } else if (difference > 0) {
                    trend = "Improving";
                } else {
                    trend = "Declining";
                }
            } else {
                trend = "Insufficient data";
            }
            
            // Update labels
            averageScoreLabel.setText(String.format("Average: %.1f", average));
            maxScoreLabel.setText(String.format("Maximum: %.1f", max));
            minScoreLabel.setText(String.format("Minimum: %.1f", min));
            trendLabel.setText("Trend: " + trend);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating statistics", e);
            averageScoreLabel.setText("Average: Error");
            maxScoreLabel.setText("Maximum: Error");
            minScoreLabel.setText("Minimum: Error");
            trendLabel.setText("Trend: Error");
        }
    }
    
    /**
     * Update the table with CMAS scores
     * @param scores List of scores to display
     */
    private void updateTable(List<CMASScore> scores) {
        if (scores == null || scores.isEmpty()) {
            scoresTable.setModel(new javax.swing.table.DefaultTableModel(new Object[0][3], 
                new String[]{"Date", "Score", "Category"}));
            return;
        }
        
        try {
            String[] columnNames = {"Date", "Score", "Category"};
            Object[][] data = new Object[scores.size()][3];
            
            for (int i = 0; i < scores.size(); i++) {
                CMASScore score = scores.get(i);
                if (score != null) {
                    data[i][0] = DateUtils.formatDateTime(score.getDate());
                    data[i][1] = String.format("%.1f", score.getScore());
                    data[i][2] = score.getCategory();
                }
            }
            
            scoresTable.setModel(new javax.swing.table.DefaultTableModel(data, columnNames));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating table", e);
            scoresTable.setModel(new javax.swing.table.DefaultTableModel(new Object[0][3], 
                new String[]{"Date", "Score", "Category"}));
        }
    }
    
    /**
     * Get the category for a given score
     * @param score The CMAS score
     * @return The category string
     */
    private String getScoreCategory(double score) {
        return CMASScore.getCategoryForScore(score);
    }

    /**
     * Export the CMAS scores data to a CSV file
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
            
            if (currentScores.isEmpty()) {
                LOGGER.warning("Cannot export data: no scores available for patient " + currentPatient.getPatientId());
                JOptionPane.showMessageDialog(this,
                    "No data available to export.",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export CMAS Scores Data");
            
            // Set default filename
            String defaultFilename = "cmas_scores_" + currentPatient.getName().replace(" ", "_") + ".csv";
            fileChooser.setSelectedFile(new File(defaultFilename));
            
            int result = fileChooser.showSaveDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                
                // Create CSV content
                StringBuilder csv = new StringBuilder();
                csv.append("Date,Score,Category\n");
                
                // Sort scores by date (newest first)
                List<CMASScore> sortedScores = currentScores.stream()
                    .filter(score -> score != null && score.getDate() != null)
                    .sorted(Comparator.comparing(CMASScore::getDate).reversed())
                    .collect(Collectors.toList());
                
                for (CMASScore score : sortedScores) {
                    if (score != null && score.getDate() != null) {
                        String category = getScoreCategory(score.getScore());
                        csv.append(DateUtils.formatDateTime(score.getDate()))
                           .append(",")
                           .append(score.getScore())
                           .append(",")
                           .append(category)
                           .append("\n");
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
     * Show an error message in the chart panel
     * @param message The error message to display
     */
    private void showError(String message) {
        chartPanel.removeAll();
        chartPanel.add(new JLabel(message, JLabel.CENTER), BorderLayout.CENTER);
        chartPanel.revalidate();
        chartPanel.repaint();
    }
    
    /**
     * Update the panel with new CMAS scores
     * @param patient The patient whose scores to display
     * @param scores List of CMAS scores
     */
    public void updateCMASScores(Patient patient, List<CMASScore> scores) {
        if (patient == null) {
            LOGGER.warning("Cannot update CMAS scores: patient is null");
            return;
        }
        
        this.currentPatient = patient;
        this.currentScores = scores != null ? scores : new ArrayList<>();
        updateChart();
    }
}
