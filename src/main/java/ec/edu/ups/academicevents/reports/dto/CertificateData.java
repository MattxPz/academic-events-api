package ec.edu.ups.academicevents.reports.dto;

import java.time.Instant;
import java.util.UUID;

/** Datos necesarios para emitir el certificado individual de una inscripción. */
public record CertificateData(
        Long registrationId,
        UUID registrationCode,
        String status,
        Long participantId,
        String participantFirstName,
        String participantLastName,
        String participantEmail,
        String eventTitle,
        String eventModality,
        Instant eventStartAt,
        Instant eventEndAt) {

    public String participantFullName() {
        return participantFirstName + " " + participantLastName;
    }
}
