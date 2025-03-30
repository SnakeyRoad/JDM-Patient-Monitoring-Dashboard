package com.jdm.dashboard;

import com.jdm.dashboard.database.DatabaseManager;
import com.jdm.dashboard.utils.ResourceUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class for JDM Patient Monitoring Dashboard application
 */
public class Main {
    
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    
    /**
     * Main method to launch the application
     */
    public static void main(String[] args) {
        try {
            // Set the look and feel to the system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            // Initialize the database
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initialize();
            
            // Import CSV data
            try {
                ResourceUtils.importCSVData(dbManager);
            } catch (IOException | SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to import CSV data", e);
                JOptionPane.showMessageDialog(null, 
                    "Failed to import CSV data: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            
            // Create and show the main window
            SwingUtilities.invokeLater(() -> {
                DashboardApplication app = new DashboardApplication(dbManager);
                app.setVisible(true);
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Application failed to start", e);
            JOptionPane.showMessageDialog(null, 
                "Application failed to start: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    /**
     * Show an error dialog
     */
    private static void showErrorDialog(String title, String message) {
        JOptionPane.showMessageDialog(null,
            message,
            title,
            JOptionPane.ERROR_MESSAGE);
    }
}
