package ec.edu.ups.academicevents.reports.generator;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import ec.edu.ups.academicevents.reports.dto.CertificateData;
import ec.edu.ups.academicevents.reports.dto.EventReportHeader;
import ec.edu.ups.academicevents.reports.dto.RegistrantRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Genera los PDF de reportes con OpenPDF, siempre en memoria.
 *
 * <p>Los instantes se almacenan en UTC y se muestran convertidos a la zona de negocio,
 * que se indica explícitamente en el pie de cada documento.
 */
@Component
public class PdfGenerator {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final float[] REGISTRANT_COLUMN_WIDTHS = {4f, 18f, 24f, 12f, 16f, 26f};
    private static final String[] REGISTRANT_HEADERS = {
            "Nº", "Participante", "Correo", "Estado", "Inscripción", "Código de verificación"
    };

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font TABLE_BODY_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.DARK_GRAY);
    private static final Font CERTIFICATE_NAME_FONT = new Font(Font.HELVETICA, 20, Font.BOLD);
    private static final Font CERTIFICATE_BODY_FONT = new Font(Font.HELVETICA, 12, Font.NORMAL);
    private static final Font CODE_FONT = new Font(Font.COURIER, 11, Font.BOLD);

    private final ZoneId businessZone;
    private final DateTimeFormatter dateTimeFormatter;
    private final DateTimeFormatter dateFormatter;
    private final String universityName;

    public PdfGenerator(
            @Value("${app.timezone.business}") String businessTimezone,
            @Value("${app.reports.university-name:Universidad Politécnica Salesiana}") String universityName) {
        this.businessZone = ZoneId.of(businessTimezone);
        this.universityName = universityName;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("es", "EC"))
                .withZone(this.businessZone);
        this.dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("es", "EC"))
                .withZone(this.businessZone);
    }

    public byte[] registrantsPdf(EventReportHeader event, List<RegistrantRow> registrants) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 42, 42);
        PdfWriter.getInstance(document, output);

        document.open();
        document.add(centered(universityName, TITLE_FONT));
        document.add(centered("Listado de inscritos", SUBTITLE_FONT));
        document.add(spacer());

        document.add(eventDetails(event));
        document.add(spacer());
        document.add(registrantsTable(registrants));
        document.add(spacer());

        long confirmed = registrants.stream()
                .filter(registrant -> STATUS_CONFIRMED.equals(registrant.status()))
                .count();
        document.add(new Paragraph(
                "Total de inscripciones: " + registrants.size() + "  |  Confirmadas: " + confirmed,
                SUBTITLE_FONT));

        document.add(footer());
        document.close();

        return output.toByteArray();
    }

    public byte[] certificatePdf(CertificateData certificate) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 48, 48, 48, 48);
        PdfWriter.getInstance(document, output);

        document.open();
        document.add(centered(universityName, TITLE_FONT));
        document.add(centered("Certificado de participación", SUBTITLE_FONT));
        document.add(spacer());

        document.add(centered("Se certifica que", CERTIFICATE_BODY_FONT));
        document.add(centered(certificate.participantFullName(), CERTIFICATE_NAME_FONT));
        document.add(centered("participó en el evento académico", CERTIFICATE_BODY_FONT));
        document.add(centered(certificate.eventTitle(), SUBTITLE_FONT));
        document.add(spacer());

        document.add(centered(
                "Realizado del " + dateFormatter.format(certificate.eventStartAt())
                        + " al " + dateFormatter.format(certificate.eventEndAt())
                        + " en modalidad " + certificate.eventModality().toLowerCase(Locale.ROOT) + ".",
                CERTIFICATE_BODY_FONT));
        document.add(centered("Estado de la inscripción: " + certificate.status(), CERTIFICATE_BODY_FONT));
        document.add(spacer());

        document.add(centered("Código de verificación", BODY_FONT));
        document.add(centered(String.valueOf(certificate.registrationCode()), CODE_FONT));

        document.add(footer());
        document.close();

        return output.toByteArray();
    }

    private PdfPTable eventDetails(EventReportHeader event) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{22f, 78f});

        addDetailRow(table, "Evento", event.title());
        addDetailRow(table, "Modalidad", event.modality());
        addDetailRow(table, "Inicio", dateTimeFormatter.format(event.startAt()));
        addDetailRow(table, "Fin", dateTimeFormatter.format(event.endAt()));
        addDetailRow(table, "Organizador", event.organizerFullName());
        addDetailRow(table, "Capacidad", String.valueOf(event.capacity()));
        addDetailRow(table, "Cupos disponibles", String.valueOf(event.availableCapacity()));

        return table;
    }

    private void addDetailRow(PdfPTable table, String label, String value) {
        table.addCell(borderlessCell(new Phrase(label, TABLE_HEADER_FONT)));
        table.addCell(borderlessCell(new Phrase(value, BODY_FONT)));
    }

    private PdfPTable registrantsTable(List<RegistrantRow> registrants) {
        PdfPTable table = new PdfPTable(REGISTRANT_HEADERS.length);
        table.setWidthPercentage(100);
        table.setWidths(REGISTRANT_COLUMN_WIDTHS);
        table.setHeaderRows(1);

        for (String header : REGISTRANT_HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER_FONT));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setPadding(4f);
            table.addCell(cell);
        }

        int position = 1;
        for (RegistrantRow registrant : registrants) {
            table.addCell(bodyCell(String.valueOf(position)));
            table.addCell(bodyCell(registrant.lastName() + ", " + registrant.firstName()));
            table.addCell(bodyCell(registrant.email()));
            table.addCell(bodyCell(registrant.status()));
            table.addCell(bodyCell(dateTimeFormatter.format(registrant.registeredAt())));
            table.addCell(bodyCell(String.valueOf(registrant.registrationCode())));
            position++;
        }

        return table;
    }

    private PdfPCell bodyCell(String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, TABLE_BODY_FONT));
        cell.setPadding(3f);
        return cell;
    }

    private PdfPCell borderlessCell(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(2f);
        return cell;
    }

    private Paragraph centered(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(6f);
        return paragraph;
    }

    private Paragraph spacer() {
        return new Paragraph(" ", BODY_FONT);
    }

    /** Pie con la fecha de generación y la zona horaria aplicada a todas las fechas del documento. */
    private Paragraph footer() {
        Paragraph paragraph = new Paragraph(
                "Generado el " + dateTimeFormatter.format(Instant.now()) + " (" + businessZone.getId() + ")",
                FOOTER_FONT);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        paragraph.setSpacingBefore(12f);
        return paragraph;
    }
}
