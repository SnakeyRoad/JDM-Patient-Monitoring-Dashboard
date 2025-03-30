package com.jdm.dashboard.utils;

import com.jdm.dashboard.model.CMASScore;
import com.jdm.dashboard.model.LabResult;
import com.jdm.dashboard.model.Measurement;
import com.jdm.dashboard.model.Patient;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Utility class for generating charts.
 * Provides methods for creating various types of charts for patient data visualization.
 */
public class ChartGenerator {
    private static final Logger LOGGER = Logger.getLogger(ChartGenerator.class.getName());
    private static final int DEFAULT_CHART_WIDTH = 600;
    private static final int DEFAULT_CHART_HEIGHT = 400;
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color GRID_COLOR = Color.LIGHT_GRAY;
    private static final Color SERIES1_COLOR = Color.BLUE;
    private static final Color SERIES2_COLOR = Color.GREEN;
    
    /**
     * Create a CMAS score chart.
     * @param patient The patient whose scores to display
     * @param scores List of CMAS scores
     * @return JPanel containing the chart
     */
    public static JPanel createCMASChart(Patient patient, List<CMASScore> scores) {
        if (patient == null) {
            LOGGER.warning("Cannot create CMAS chart: patient is null");
            return createEmptyPanel("Patient information is missing");
        }
        
        if (scores == null || scores.isEmpty()) {
            LOGGER.warning("Cannot create CMAS chart: no scores available for patient " + patient.getPatientId());
            return createEmptyPanel("No CMAS scores available");
        }
        
        try {
            JPanel panel = new JPanel(new BorderLayout());
            
            // Create time series for each category
            TimeSeries series1 = new TimeSeries("CMAS Score > 10");
            TimeSeries series2 = new TimeSeries("CMAS Score 4-9");
            
            // Populate the series
            for (CMASScore score : scores) {
                if (score == null || score.getDate() == null) {
                    LOGGER.warning("Skipping invalid CMAS score");
                    continue;
                }
                
                Day day = new Day(
                    score.getDate().getDayOfMonth(),
                    score.getDate().getMonthValue(),
                    score.getDate().getYear()
                );
                
                if ("CMAS Score > 10".equals(score.getCategory())) {
                    series1.addOrUpdate(day, score.getScore());
                } else if ("CMAS Score 4-9".equals(score.getCategory())) {
                    series2.addOrUpdate(day, score.getScore());
                }
            }
            
            // Create dataset
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            if (!series1.isEmpty()) {
                dataset.addSeries(series1);
            }
            if (!series2.isEmpty()) {
                dataset.addSeries(series2);
            }
            
            // Create chart
            String chartTitle = "CMAS Scores for " + patient.getName();
            JFreeChart chart = ChartFactory.createTimeSeriesChart(
                chartTitle,
                "Date",
                "Score",
                dataset,
                true,  // legend
                true,  // tooltips
                false  // urls
            );
            
            // Customize the chart
            customizeChart(chart);
            
            // Create chart panel
            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new Dimension(DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT));
            
            panel.add(chartPanel, BorderLayout.CENTER);
            return panel;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating CMAS chart", e);
            return createEmptyPanel("Error creating chart: " + e.getMessage());
        }
    }
    
    /**
     * Create a lab result chart.
     * @param patient The patient whose results to display
     * @param labResult The lab result to display
     * @return JPanel containing the chart
     */
    public static JPanel createLabResultChart(Patient patient, LabResult labResult) {
        if (patient == null) {
            LOGGER.warning("Cannot create lab result chart: patient is null");
            return createEmptyPanel("Patient information is missing");
        }
        
        if (labResult == null) {
            LOGGER.warning("Cannot create lab result chart: lab result is null");
            return createEmptyPanel("Lab result information is missing");
        }
        
        if (labResult.getMeasurements().isEmpty()) {
            LOGGER.warning("Cannot create lab result chart: no measurements available for result " + labResult.getResultId());
            return createEmptyPanel("No measurements available");
        }
        
        try {
            JPanel panel = new JPanel(new BorderLayout());
            
            // Create time series
            String resultName = labResult.getResultNameEnglish();
            if (resultName == null || resultName.isEmpty()) {
                resultName = labResult.getResultName();
            }
            TimeSeries series = new TimeSeries(resultName);
            
            // Add measurements to series
            for (Measurement measurement : labResult.getMeasurements()) {
                if (measurement == null || measurement.getDateTime() == null) {
                    LOGGER.warning("Skipping invalid measurement");
                    continue;
                }
                
                Double value = measurement.getNumericValue();
                if (value != null) {
                    Day day = new Day(
                        measurement.getDateTime().getDayOfMonth(),
                        measurement.getDateTime().getMonthValue(),
                        measurement.getDateTime().getYear()
                    );
                    series.addOrUpdate(day, value);
                }
            }
            
            if (series.isEmpty()) {
                LOGGER.warning("No valid measurements found for chart");
                return createEmptyPanel("No valid measurements available");
            }
            
            // Create dataset
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            
            // Create chart
            String chartTitle = resultName + " Results for " + patient.getName();
            JFreeChart chart = ChartFactory.createTimeSeriesChart(
                chartTitle,
                "Date",
                labResult.getUnit(),
                dataset,
                true,  // legend
                true,  // tooltips
                false  // urls
            );
            
            // Customize the chart
            customizeChart(chart);
            
            // Create chart panel
            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new Dimension(DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT));
            
            panel.add(chartPanel, BorderLayout.CENTER);
            return panel;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating lab result chart", e);
            return createEmptyPanel("Error creating chart: " + e.getMessage());
        }
    }
    
    /**
     * Create a summary chart for a patient.
     * @param patient The patient whose data to display
     * @param cmasScores List of CMAS scores
     * @return JPanel containing the chart
     */
    public static JPanel createSummaryChart(Patient patient, List<CMASScore> cmasScores) {
        if (patient == null) {
            LOGGER.warning("Cannot create summary chart: patient is null");
            return createEmptyPanel("Patient information is missing");
        }
        
        if (cmasScores == null || cmasScores.isEmpty()) {
            LOGGER.warning("Cannot create summary chart: no scores available for patient " + patient.getPatientId());
            return createEmptyPanel("No CMAS scores available");
        }
        
        try {
            JPanel panel = new JPanel(new BorderLayout());
            
            // Sort scores by date
            List<CMASScore> sortedScores = cmasScores.stream()
                .filter(score -> score != null && score.getDate() != null)
                .sorted(Comparator.comparing(CMASScore::getDate))
                .collect(Collectors.toList());
            
            if (sortedScores.isEmpty()) {
                LOGGER.warning("No valid scores found for summary chart");
                return createEmptyPanel("No valid scores available");
            }
            
            // Create time series for CMAS scores
            TimeSeries series = new TimeSeries("CMAS Score");
            
            // Only use scores > 10 for consistency
            for (CMASScore score : sortedScores) {
                if ("CMAS Score > 10".equals(score.getCategory())) {
                    Day day = new Day(
                        score.getDate().getDayOfMonth(),
                        score.getDate().getMonthValue(),
                        score.getDate().getYear()
                    );
                    series.addOrUpdate(day, score.getScore());
                }
            }
            
            if (series.isEmpty()) {
                LOGGER.warning("No valid scores > 10 found for summary chart");
                return createEmptyPanel("No valid scores > 10 available");
            }
            
            // Create dataset
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            
            // Create chart
            String chartTitle = "CMAS Score Trend for " + patient.getName();
            JFreeChart chart = ChartFactory.createTimeSeriesChart(
                chartTitle,
                "Date",
                "Score",
                dataset,
                true,  // legend
                true,  // tooltips
                false  // urls
            );
            
            // Customize the chart
            customizeChart(chart);
            
            // Create chart panel
            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new Dimension(DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT));
            
            panel.add(chartPanel, BorderLayout.CENTER);
            return panel;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating summary chart", e);
            return createEmptyPanel("Error creating chart: " + e.getMessage());
        }
    }
    
    /**
     * Customize a chart's appearance.
     * @param chart The chart to customize
     */
    private static void customizeChart(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(BACKGROUND_COLOR);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);
        
        // Customize date axis
        DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
        dateAxis.setDateFormatOverride(new java.text.SimpleDateFormat("dd-MM-yyyy"));
        
        // Customize renderer
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setDefaultShapesVisible(true);
        renderer.setDefaultShapesFilled(true);
        
        // Set series colors
        renderer.setSeriesPaint(0, SERIES1_COLOR);
        renderer.setSeriesPaint(1, SERIES2_COLOR);
    }
    
    /**
     * Create an empty panel with a message.
     * @param message The message to display
     * @return JPanel containing the message
     */
    private static JPanel createEmptyPanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(message, JLabel.CENTER), BorderLayout.CENTER);
        return panel;
    }
}
