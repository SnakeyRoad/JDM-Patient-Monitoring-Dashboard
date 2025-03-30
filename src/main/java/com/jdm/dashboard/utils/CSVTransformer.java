package com.jdm.dashboard.utils;

import java.io.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;

public class CSVTransformer {
    private static final Logger LOGGER = Logger.getLogger(CSVTransformer.class.getName());
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd-M-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-M-d"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    public static void transformCMASData(String inputPath, String outputPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputPath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            
            // Write header
            writer.write("PatientID,Date,Score,Category\n");
            
            // Read all lines first
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            
            if (lines.size() < 3) {
                LOGGER.warning("CMAS data file has insufficient rows");
                return;
            }
            
            // First row contains dates
            String[] dates = lines.get(0).split(",");
            
            // Process each score category (rows 2 and 3)
            for (int rowIndex = 1; rowIndex < lines.size(); rowIndex++) {
                String[] scores = lines.get(rowIndex).split(",");
                String category = scores[0].trim();
                
                if (!category.startsWith("CMAS Score")) {
                    continue;
                }
                
                // Process each date column (starting from index 1)
                for (int colIndex = 1; colIndex < dates.length && colIndex < scores.length; colIndex++) {
                    String dateStr = dates[colIndex].trim();
                    String scoreStr = scores[colIndex].trim();
                    
                    if (dateStr.isEmpty() || scoreStr.isEmpty()) {
                        continue;
                    }
                    
                    try {
                        // Parse and format date
                        LocalDateTime date = parseDate(dateStr);
                        if (date == null) {
                            LOGGER.warning("Could not parse date: " + dateStr);
                            continue;
                        }
                        
                        // Parse score
                        double score;
                        try {
                            if (scoreStr.endsWith("points")) {
                                scoreStr = scoreStr.substring(0, scoreStr.length() - 6).trim();
                            }
                            score = Double.parseDouble(scoreStr);
                            if (score < 0 || score > 100) {
                                LOGGER.warning("Invalid score value (out of range): " + scoreStr);
                                continue;
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid score value (not a number): " + scoreStr);
                            continue;
                        }
                        
                        // Write the transformed data
                        writer.write(String.format("%s,%s,%.1f,%s\n",
                            "55e2d179-d738-47d1-b88c-606833ce4d31",
                            date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            score,
                            category));
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error processing date: " + dateStr + ", score: " + scoreStr, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error transforming CMAS data", e);
            throw e;
        }
    }

    private static LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        dateStr = dateStr.trim();
        
        // Try each formatter
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return date.atStartOfDay(); // Convert to LocalDateTime at start of day
            } catch (DateTimeParseException e) {
                // Continue to next formatter
            }
        }
        
        // If all formatters fail, try to handle special cases
        try {
            // Handle dates with quotes
            if (dateStr.startsWith("\"") && dateStr.endsWith("\"")) {
                dateStr = dateStr.substring(1, dateStr.length() - 1);
                return parseDate(dateStr); // Recursive call without quotes
            }
            
            // Handle dates with time
            if (dateStr.contains(":")) {
                String[] parts = dateStr.split(" ", 2);
                if (parts.length == 2) {
                    LocalDateTime dt = parseDate(parts[0]);
                    if (dt != null) {
                        return dt; // Ignore time part for consistency
                    }
                }
            }
            
            // Handle dates with slashes
            if (dateStr.contains("/")) {
                String[] parts = dateStr.split("/");
                if (parts.length == 3) {
                    // Try different orderings (DD/MM/YYYY or MM/DD/YYYY)
                    try {
                        LocalDate date = LocalDate.of(
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[0])
                        );
                        return date.atStartOfDay();
                    } catch (NumberFormatException e) {
                        // Try MM/DD/YYYY
                        try {
                            LocalDate date = LocalDate.of(
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1])
                            );
                            return date.atStartOfDay();
                        } catch (NumberFormatException ex) {
                            // Continue to next formatter
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing special date format: " + dateStr, e);
        }
        
        LOGGER.warning("Could not parse date with any formatter: " + dateStr);
        return null;
    }
} 