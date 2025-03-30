# JDM Patient Monitoring Dashboard

A Java application for healthcare professionals to monitor patients with Juvenile Dermatomyositis (JDM) and track their CMAS (Childhood Myositis Assessment Scale) scores over time.

## Features

- Monitor CMAS scores for JDM patients
- Visualize lab results with charts and graphs
- View patient information and progress
- Export data for research and analysis
- Store patient data in a local SQLite database

## Project Structure

```
JDM-Patient-Monitoring-Dashboard/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── jdm/
│       │           └── dashboard/
│       │               ├── Main.java
│       │               ├── DashboardApplication.java
│       │               ├── database/
│       │               │   ├── DatabaseManager.java
│       │               │   ├── CSVParser.java
│       │               │   └── DataImporter.java
│       │               ├── model/
│       │               │   ├── Patient.java
│       │               │   ├── LabResultGroup.java
│       │               │   ├── LabResult.java
│       │               │   ├── Measurement.java
│       │               │   └── CMASScore.java
│       │               ├── ui/
│       │               │   ├── PatientPanel.java
│       │               │   ├── CMASChartPanel.java
│       │               │   ├── LabResultPanel.java
│       │               │   └── DashboardPanel.java
│       │               └── utils/
│       │                   ├── ChartGenerator.java
│       │                   ├── DateUtils.java
│       │                   └── ExportUtils.java
│       └── resources/
│           ├── schema.sql
│           └── data/
│               ├── Patient.csv
│               ├── LabResultGroup.csv
│               ├── LabResult.csv
│               ├── LabResultsEN.csv
│               ├── Measurement.csv
│               └── CMAS.csv
├── lib/
├── build.gradle
└── README.md
```

## Requirements

- Java 8 or higher
- Gradle for building (optional, but recommended)

## Dependencies

- SQLite JDBC (3.40.0.0) - for database operations
- JFreeChart (1.5.3) - for data visualization
- Apache Commons CSV (1.9.0) - for CSV parsing
- Apache Commons IO (2.11.0) - for file operations

## Building the Project

### Using Gradle

```bash
# Clone the repository
git clone https://github.com/your-username/JDM-Patient-Monitoring-Dashboard.git
cd JDM-Patient-Monitoring-Dashboard

# Build the project
./gradlew build

# Run the application
./gradlew run
```

### Using Java directly

```bash
# Compile the Java files
javac -d bin -cp "lib/*" src/main/java/com/jdm/dashboard/**/*.java

# Run the application
java -cp "bin:lib/*" com.jdm.dashboard.Main
```

## Usage

1. Launch the application
2. The application will automatically load sample data if no database exists
3. Use the patient selector to choose a patient
4. Navigate through the tabs to view different aspects of the patient's data:
   - Patient Overview: Basic patient information
   - CMAS Scores: Charts and tables of CMAS scores over time
   - Lab Results: Laboratory test results and trends

## Data Import and Export

### Import

The application automatically imports data from CSV files when first run. To import new data:

1. Place your CSV files in the `resources/data` directory
2. Ensure the files have the correct format and names:
   - Patient.csv - Patient information
   - LabResultGroup.csv - Lab result group definitions
   - LabResult.csv - Lab result definitions
   - LabResultsEN.csv - Lab result English translations
   - Measurement.csv - Measurement values
   - CMAS.csv - CMAS scores

### Export

You can export data in several ways:

1. Click "Export Data" in the CMAS Scores or Lab Results tabs to export specific data
2. Use "Export Database" from the File menu to export the entire database
3. Exported data will be saved in CSV format for easy analysis in other tools

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Developed as part of a Software Engineering project for monitoring JDM patients
- Special thanks to the healthcare professionals who provided domain expertise
