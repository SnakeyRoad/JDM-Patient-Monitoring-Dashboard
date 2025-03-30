package com.jdm.dashboard.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Utility class for date and time operations
 */
public class DateUtils {
    private static final Logger LOGGER = Logger.getLogger(DateUtils.class.getName());
    
    private static final String DB_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DB_DATETIME_FORMATTER = 
        DateTimeFormatter.ofPattern(DB_DATETIME_FORMAT);
    
    private static final List<DateTimeFormatter> CSV_DATE_FORMATTERS = new ArrayList<>();
    private static final List<DateTimeFormatter> CSV_DATETIME_FORMATTERS = new ArrayList<>();
    
    static {
        // Initialize date formatters for different CSV formats
        CSV_DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        CSV_DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        CSV_DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("d-M-yyyy"));
        CSV_DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        CSV_DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        
        // Initialize datetime formatters for different CSV formats
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("d-M-yyyy HH:mm:ss"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyyHH:mm"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyyHH:mm.S"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyyHH:mm.SS"));
        CSV_DATETIME_FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyyHH:mm.SSS"));
    }
    
    /**
     * Format a LocalDateTime object to a string for database storage
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            LOGGER.warning("Attempted to format null date-time");
            return null;
        }
        try {
            return dateTime.format(DB_DATETIME_FORMATTER);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error formatting date-time: " + dateTime, e);
            return null;
        }
    }
    
    /**
     * Parse a date-time string from the database
     */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            LOGGER.warning("Attempted to parse null or empty date-time string");
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DB_DATETIME_FORMATTER);
        } catch (DateTimeParseException e) {
            LOGGER.log(Level.SEVERE, "Error parsing date-time: " + dateTimeStr, e);
            return null;
        }
    }
    
    /**
     * Parse a date string from CSV file
     */
    public static LocalDateTime parseDateFromCSV(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            LOGGER.warning("Attempted to parse null or empty date string");
            return null;
        }
        
        // Try each formatter until one works
        for (DateTimeFormatter formatter : CSV_DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return date.atStartOfDay();
            } catch (DateTimeParseException e) {
                LOGGER.fine("Failed to parse date with formatter: " + formatter + ", trying next");
                // Try the next formatter
            }
        }
        
        LOGGER.warning("Unable to parse date with any formatter: " + dateStr);
        return null;
    }
    
    /**
     * Parse a date-time string from CSV file
     */
    public static LocalDateTime parseDateTimeFromCSV(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            LOGGER.warning("Attempted to parse null or empty date-time string");
            return null;
        }
        
        // Try each datetime formatter until one works
        for (DateTimeFormatter formatter : CSV_DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e) {
                LOGGER.fine("Failed to parse date-time with formatter: " + formatter + ", trying next");
                // Try the next formatter
            }
        }
        
        // If datetime parsing fails, try to parse as date with default time
        LocalDateTime dateTime = parseDateFromCSV(dateTimeStr);
        if (dateTime != null) {
            return dateTime;
        }
        
        LOGGER.warning("Unable to parse date-time with any formatter: " + dateTimeStr);
        return null;
    }
    
    /**
     * Format a LocalDateTime object to a display string (for UI)
     */
    public static String formatDateTimeForDisplay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        try {
            return dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error formatting date-time for display: " + dateTime, e);
            return "";
        }
    }
    
    /**
     * Format a LocalDateTime object to a date string (for UI)
     */
    public static String formatDateForDisplay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        try {
            return dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error formatting date for display: " + dateTime, e);
            return "";
        }
    }
    
    /**
     * Get the current date and time
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
    
    /**
     * Convert string to LocalDateTime for a specific pattern
     */
    public static LocalDateTime parseCustomDateTime(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            LOGGER.warning("Attempted to parse null or empty date-time string");
            return null;
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            LOGGER.warning("Attempted to parse with null or empty pattern");
            return null;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Invalid date-time pattern: " + pattern, e);
            return null;
        } catch (DateTimeParseException e) {
            LOGGER.log(Level.SEVERE, "Error parsing custom date-time: " + dateTimeStr + " with pattern: " + pattern, e);
            return null;
        }
    }

    /**
     * Validate a date-time string
     */
    public static boolean isValidDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return false;
        }
        try {
            LocalDateTime.parse(dateTimeStr, DB_DATETIME_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Validate a date string
     */
    public static boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return false;
        }
        for (DateTimeFormatter formatter : CSV_DATE_FORMATTERS) {
            try {
                LocalDate.parse(dateStr, formatter);
                return true;
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        return false;
    }

    /**
     * Get the start of day for a given date-time
     */
    public static LocalDateTime getStartOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.with(LocalTime.MIN);
    }

    /**
     * Get the end of day for a given date-time
     */
    public static LocalDateTime getEndOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.with(LocalTime.MAX);
    }

    /**
     * Get the number of days between two date-times
     */
    public static long getDaysBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * Get the number of hours between two date-times
     */
    public static long getHoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * Get the number of minutes between two date-times
     */
    public static long getMinutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(start, end);
    }

    /**
     * Check if a date-time is within a given range (inclusive)
     */
    public static boolean isDateTimeInRange(LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {
        if (dateTime == null || start == null || end == null) {
            return false;
        }
        return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    /**
     * Get the earliest date-time from a list
     */
    public static LocalDateTime getEarliestDateTime(List<LocalDateTime> dateTimes) {
        if (dateTimes == null || dateTimes.isEmpty()) {
            return null;
        }
        return dateTimes.stream()
            .filter(dt -> dt != null)
            .min(LocalDateTime::compareTo)
            .orElse(null);
    }

    /**
     * Get the latest date-time from a list
     */
    public static LocalDateTime getLatestDateTime(List<LocalDateTime> dateTimes) {
        if (dateTimes == null || dateTimes.isEmpty()) {
            return null;
        }
        return dateTimes.stream()
            .filter(dt -> dt != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);
    }

    /**
     * Format a duration in hours and minutes
     */
    public static String formatDuration(long minutes) {
        if (minutes < 0) {
            return "0h 0m";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return hours + "h " + remainingMinutes + "m";
    }
}
