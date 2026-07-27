package ec.edu.ups.academicevents.registrations.dto;

import java.time.Instant;
import java.util.UUID;

public record RegistrationResponse(
        Long id,
        UUID registrationCode,
        Long eventId,
        String eventTitle,
        Long participantId,
        String participantFullName,
        String participantEmail,
        String status,
        Instant registeredAt,
        Instant statusUpdatedAt,
        Instant confirmedAt,
        Instant cancelledAt) {
}
