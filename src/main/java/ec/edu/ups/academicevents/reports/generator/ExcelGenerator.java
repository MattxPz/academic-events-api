package ec.edu.ups.academicevents.reports.generator;

import ec.edu.ups.academicevents.reports.dto.EventReportHeader;
import ec.edu.ups.academicevents.reports.dto.RegistrantRow;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Genera el libro XLSX del listado de inscritos. Todo se construye en memoria:
 * el contenedor de despliegue es efímero y no debe escribirse nada a disco.
 */
@Component
public class ExcelGenerator {

    private static final String SHEET_REGISTRANTS = "Inscritos";
    private static final String SHEET_SUMMARY = "Resumen";
    private static final String DATE_FORMAT = "dd/mm/yyyy hh:mm";
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    private static final String[] HEADERS = {
            "Nº", "Apellidos", "Nombres", "Correo", "Estado", "Fecha de inscripción", "Código de inscripción"
    };

    private final ZoneId businessZone;

    public ExcelGenerator(@Value("${app.timezone.business}") String businessTimezone) {
        this.businessZone = ZoneId.of(businessTimezone);
    }

    public byte[] registrantsWorkbook(EventReportHeader event, List<RegistrantRow> registrants) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);
            CellStyle totalStyle = boldStyle(workbook);

            writeRegistrantsSheet(workbook, registrants, headerStyle, dateStyle, totalStyle);
            writeSummarySheet(workbook, event, registrants, totalStyle, dateStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el archivo XLSX del reporte.", ex);
        }
    }

    private void writeRegistrantsSheet(
            Workbook workbook,
            List<RegistrantRow> registrants,
            CellStyle headerStyle,
            CellStyle dateStyle,
            CellStyle totalStyle) {
        Sheet sheet = workbook.createSheet(SHEET_REGISTRANTS);

        Row headerRow = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = headerRow.createCell(column);
            cell.setCellValue(HEADERS[column]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (RegistrantRow registrant : registrants) {
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue(rowIndex);
            row.createCell(1).setCellValue(registrant.lastName());
            row.createCell(2).setCellValue(registrant.firstName());
            row.createCell(3).setCellValue(registrant.email());
            row.createCell(4).setCellValue(registrant.status());

            Cell registeredAtCell = row.createCell(5);
            registeredAtCell.setCellValue(toBusinessDateTime(registrant.registeredAt()));
            registeredAtCell.setCellStyle(dateStyle);

            row.createCell(6).setCellValue(String.valueOf(registrant.registrationCode()));
            rowIndex++;
        }

        long confirmed = registrants.stream()
                .filter(registrant -> STATUS_CONFIRMED.equals(registrant.status()))
                .count();

        Row totalsRow = sheet.createRow(rowIndex + 1);
        writeBoldCell(totalsRow, 0, "TOTALES", totalStyle);
        writeBoldCell(totalsRow, 1, "Inscripciones: " + registrants.size(), totalStyle);
        writeBoldCell(totalsRow, 3, "Confirmadas: " + confirmed, totalStyle);

        autoSizeColumns(sheet, HEADERS.length);
    }

    private void writeSummarySheet(
            Workbook workbook,
            EventReportHeader event,
            List<RegistrantRow> registrants,
            CellStyle labelStyle,
            CellStyle dateStyle) {
        Sheet sheet = workbook.createSheet(SHEET_SUMMARY);

        int rowIndex = 0;
        rowIndex = writeTextRow(sheet, rowIndex, "Evento", event.title(), labelStyle);
        rowIndex = writeTextRow(sheet, rowIndex, "Modalidad", event.modality(), labelStyle);
        rowIndex = writeDateRow(sheet, rowIndex, "Inicio", event.startAt(), labelStyle, dateStyle);
        rowIndex = writeDateRow(sheet, rowIndex, "Fin", event.endAt(), labelStyle, dateStyle);
        rowIndex = writeTextRow(sheet, rowIndex, "Organizador", event.organizerFullName(), labelStyle);
        rowIndex = writeNumberRow(sheet, rowIndex, "Capacidad", event.capacity(), labelStyle);
        rowIndex = writeNumberRow(sheet, rowIndex, "Cupos disponibles", event.availableCapacity(), labelStyle);
        rowIndex = writeNumberRow(sheet, rowIndex, "Cupos ocupados", event.occupiedSeats(), labelStyle);
        rowIndex = writeNumberRow(sheet, rowIndex, "Inscripciones registradas", registrants.size(), labelStyle);
        writeTextRow(sheet, rowIndex, "Zona horaria de las fechas", businessZone.getId(), labelStyle);

        autoSizeColumns(sheet, 2);
    }

    private int writeTextRow(Sheet sheet, int rowIndex, String label, String value, CellStyle labelStyle) {
        Row row = sheet.createRow(rowIndex);
        writeBoldCell(row, 0, label, labelStyle);
        row.createCell(1).setCellValue(value == null ? "" : value);
        return rowIndex + 1;
    }

    private int writeNumberRow(Sheet sheet, int rowIndex, String label, Number value, CellStyle labelStyle) {
        Row row = sheet.createRow(rowIndex);
        writeBoldCell(row, 0, label, labelStyle);
        row.createCell(1).setCellValue(value == null ? 0d : value.doubleValue());
        return rowIndex + 1;
    }

    private int writeDateRow(
            Sheet sheet, int rowIndex, String label, Instant value, CellStyle labelStyle, CellStyle dateStyle) {
        Row row = sheet.createRow(rowIndex);
        writeBoldCell(row, 0, label, labelStyle);

        Cell cell = row.createCell(1);
        cell.setCellValue(toBusinessDateTime(value));
        cell.setCellStyle(dateStyle);
        return rowIndex + 1;
    }

    private void writeBoldCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** Los instantes se guardan en UTC; en el archivo se muestran en la zona de negocio. */
    private LocalDateTime toBusinessDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, businessZone);
    }

    private void autoSizeColumns(Sheet sheet, int columns) {
        for (int column = 0; column < columns; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle boldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle dateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(DATE_FORMAT));
        return style;
    }
}
