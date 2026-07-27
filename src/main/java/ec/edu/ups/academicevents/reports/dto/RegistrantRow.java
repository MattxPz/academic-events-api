package ec.edu.ups.academicevents.reports.dto;

import java.time.Instant;
import java.util.UUID;

/** Fila del listado de inscritos de un evento. */
public record RegistrantRow(
        String firstName,
        String lastName,
        String email,
        String status,
        Instant registeredAt,
        UUID registrationCode) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
