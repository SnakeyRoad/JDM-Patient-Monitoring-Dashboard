package com.jdm.dashboard;

import com.jdm.dashboard.database.DatabaseManager;
import com.jdm.dashboard.ui.DashboardPanel;
import com.jdm.dashboard.utils.ExportUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Main application class for the JDM Dashboard
 */
public class DashboardApplication extends JFrame {
    
    private final DatabaseManager dbManager;
    private final DashboardPanel dashboardPanel;
    
    /**
     * Constructor
     */
    public DashboardApplication(DatabaseManager dbManager) {
        super("JDM Patient Monitoring Dashboard");
        this.dbManager = dbManager;
        this.dashboardPanel = new DashboardPanel(dbManager);
        
        // Set up the UI
        initUI();
    }
    
    /**
     * Initialize the UI components
     */
    private void initUI() {
        // Set frame properties
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null); // Center on screen
        
        // Create menu bar
        setJMenuBar(createMenuBar());
        
        // Create main dashboard panel
        getContentPane().add(dashboardPanel, BorderLayout.CENTER);
        
        // Add status panel at the bottom
        JPanel statusPanel = createStatusPanel();
        getContentPane().add(statusPanel, BorderLayout.SOUTH);
        
        // Add window close listener to close database connection
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                dbManager.close();
            }
        });
    }
    
    /**
     * Create the menu bar
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        
        JMenuItem exportItem = new JMenuItem("Export Database");
        exportItem.addActionListener(e -> exportDatabase());
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> exitApplication());
        
        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // View menu
        JMenu viewMenu = new JMenu("View");
        
        JMenuItem refreshItem = new JMenuItem("Refresh Data");
        refreshItem.addActionListener(e -> refreshData());
        
        viewMenu.add(refreshItem);
        
        // Help menu
        JMenu helpMenu = new JMenu("Help");
        
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        
        helpMenu.add(aboutItem);
        
        // Add menus to menu bar
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    /**
     * Create the status panel
     */
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JLabel statusLabel = new JLabel("Ready");
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        // Add current date/time
        JLabel timeLabel = new JLabel();
        updateTimeLabel(timeLabel);
        
        // Update time every minute
        Timer timer = new Timer(60000, e -> updateTimeLabel(timeLabel));
        timer.start();
        
        statusPanel.add(timeLabel, BorderLayout.EAST);
        
        return statusPanel;
    }
    
    /**
     * Update the time label with current date/time
     */
    private void updateTimeLabel(JLabel timeLabel) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        timeLabel.setText(now.format(formatter));
    }
    
    /**
     * Export the database to a file
     */
    private void exportDatabase() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Database");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        // Set default filename with timestamp
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String defaultFilename = "jdm_dashboard_export_" + now.format(formatter) + ".db";
        fileChooser.setSelectedFile(new File(defaultFilename));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                Path exportPath = Paths.get(selectedFile.getAbsolutePath());
                ExportUtils.exportDatabase(dbManager, exportPath);
                
                JOptionPane.showMessageDialog(this,
                    "Database exported successfully to:\n" + selectedFile.getAbsolutePath(),
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this,
                    "Error exporting database: " + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Refresh the data display
     */
    private void refreshData() {
        try {
            dashboardPanel.refreshData();
            JOptionPane.showMessageDialog(this,
                "Data refreshed successfully!",
                "Refresh Complete",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error refreshing data: " + e.getMessage(),
                "Refresh Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Show the about dialog
     */
    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
            "JDM Patient Monitoring Dashboard\n\n" +
            "A monitoring tool for healthcare professionals to track\n" +
            "juvenile dermatomyositis (JDM) patients and their CMAS scores.\n\n" +
            "Version: 1.0.0\n" +
            "© 2025 JDM Dashboard Team",
            "About JDM Dashboard",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Exit the application
     */
    private void exitApplication() {
        // Close database connection
        dbManager.close();
        
        // Exit
        dispose();
        System.exit(0);
    }
}
