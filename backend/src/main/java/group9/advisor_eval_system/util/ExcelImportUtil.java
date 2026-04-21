package group9.advisor_eval_system.util;

import group9.advisor_eval_system.dto.ImportStudentDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ExcelImportUtil {

    public static List<ImportStudentDTO> parseStudentsFromExcel(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            return parseStudentsFromCsv(file);
        }
        return parseStudentsFromExcelFile(file);
    }

    public static List<ImportStudentDTO> parseStudentsFromCsv(MultipartFile file) throws IOException {
        List<ImportStudentDTO> students = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int rowIndex = 0;

            while ((line = reader.readLine()) != null) {
                // Skip blank lines
                if (line.trim().isEmpty()) {
                    rowIndex++;
                    continue;
                }

                String[] cols = line.split(",", -1);
                // Trim each cell
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim().replaceAll("^\"|\"$", ""); // remove surrounding quotes
                }

                String col0Upper = cols[0].toUpperCase();

                // Skip header rows
                if (col0Upper.contains("TEAM") || col0Upper.contains("CODE") ||
                        col0Upper.contains("MEMBER") || col0Upper.contains("STUDENT") ||
                        col0Upper.contains("ID") || col0Upper.contains("NAME")) {
                    rowIndex++;
                    continue;
                }

                // Detect format 2: Team Code in col 0 (contains -sem or year pattern)
                boolean isFormat2 = cols[0].contains("-sem") || cols[0].matches(".*\\d{4}-sem.*");

                ImportStudentDTO student = new ImportStudentDTO();

                if (isFormat2) {
                    // Format 2: Team Code, Member #, Student ID, Last Name, First Name, Email
                    if (cols.length > 2) student.setStudentId(cols[2]);
                    if (cols.length > 3) student.setLastName(cols[3]);
                    if (cols.length > 4) student.setFirstName(cols[4]);
                    if (cols.length > 5 && !cols[5].isEmpty()) student.setEmail(cols[5]);
                } else {
                    // Format 1: Student ID, First Name, Last Name, Email, Phone
                    if (cols.length > 0) student.setStudentId(cols[0]);
                    if (cols.length > 1) student.setFirstName(cols[1]);
                    if (cols.length > 2) student.setLastName(cols[2]);
                    if (cols.length > 3 && !cols[3].isEmpty()) student.setEmail(cols[3]);
                }

                // Only add if required fields are present
                if (student.getStudentId() != null && !student.getStudentId().isEmpty() &&
                        student.getFirstName() != null && !student.getFirstName().isEmpty() &&
                        student.getLastName() != null && !student.getLastName().isEmpty()) {
                    students.add(student);
                }

                rowIndex++;
            }
        }

        return students;
    }

    private static List<ImportStudentDTO> parseStudentsFromExcelFile(MultipartFile file) throws IOException {
        List<ImportStudentDTO> students = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Skip header row (row 0)
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                
                try {
                    ImportStudentDTO student = new ImportStudentDTO();
                    
                    // Try to detect column positions based on header or use flexible mapping
                    // Support both formats:
                    // Format 1: Student ID, First Name, Last Name, Email, Phone Number
                    // Format 2: Team Code, Member #, Student ID, Last Name, First Name, Email, ...
                    
                    // Check if this looks like format 2 (has Team Code in column 0)
                    String col0 = getCellValueAsString(row.getCell(0)).trim();
                    boolean isFormat2 = col0.contains("-sem") || col0.matches(".*\\d{4}-sem.*");

                    // Skip header-like rows (e.g. "TEAM CODE", "STUDENT ID" text rows)
                    String col0Upper = col0.toUpperCase();
                    if (col0Upper.contains("TEAM") || col0Upper.contains("CODE") || col0Upper.contains("MEMBER")) {
                        continue;
                    }
                    
                    if (isFormat2) {
                        // Format 2: Team Code, Member #, Student ID, Last Name, First Name, Email, ...
                        // Column 2: Student ID
                        Cell studentIdCell = row.getCell(2);
                        if (studentIdCell != null) {
                            student.setStudentId(getCellValueAsString(studentIdCell).trim());
                        }
                        
                        // Column 4: First Name
                        Cell firstNameCell = row.getCell(4);
                        if (firstNameCell != null) {
                            student.setFirstName(getCellValueAsString(firstNameCell).trim());
                        }
                        
                        // Column 3: Last Name
                        Cell lastNameCell = row.getCell(3);
                        if (lastNameCell != null) {
                            student.setLastName(getCellValueAsString(lastNameCell).trim());
                        }
                        
                        // Column 5: Email (optional)
                        Cell emailCell = row.getCell(5);
                        if (emailCell != null) {
                            String email = getCellValueAsString(emailCell).trim();
                            if (!email.isEmpty()) {
                                student.setEmail(email);
                            }
                        }
                        
                        // Validate required fields
                        if (student.getStudentId() != null && !student.getStudentId().isEmpty() &&
                            student.getFirstName() != null && !student.getFirstName().isEmpty() &&
                            student.getLastName() != null && !student.getLastName().isEmpty()) {
                            students.add(student);
                        }
                    } else {
                        // Format 1: Student ID, First Name, Last Name, Email, Phone Number
                        // Column 0: Student ID
                        Cell studentIdCell = row.getCell(0);
                        if (studentIdCell != null) {
                            student.setStudentId(getCellValueAsString(studentIdCell).trim());
                        }
                        
                        // Column 1: First Name
                        Cell firstNameCell = row.getCell(1);
                        if (firstNameCell != null) {
                            student.setFirstName(getCellValueAsString(firstNameCell).trim());
                        }
                        
                        // Column 2: Last Name
                        Cell lastNameCell = row.getCell(2);
                        if (lastNameCell != null) {
                            student.setLastName(getCellValueAsString(lastNameCell).trim());
                        }
                        
                        // Column 3: Email (optional)
                        Cell emailCell = row.getCell(3);
                        if (emailCell != null) {
                            String email = getCellValueAsString(emailCell).trim();
                            if (!email.isEmpty()) {
                                student.setEmail(email);
                            }
                        }
                        
                        // Validate required fields
                        if (student.getStudentId() != null && !student.getStudentId().isEmpty() &&
                            student.getFirstName() != null && !student.getFirstName().isEmpty() &&
                            student.getLastName() != null && !student.getLastName().isEmpty()) {
                            students.add(student);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing row " + (rowIndex + 1) + ": " + e.getMessage());
                }
            }
        }
        
        return students;
    }
    
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    private static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int cellIndex = row.getFirstCellNum(); cellIndex < row.getLastCellNum(); cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
