package ec.edu.ups.academicevents.registrations.dto;

import jakarta.validation.constraints.NotNull;

public record RegistrationRequest(
        @NotNull(message = "El identificador del evento es obligatorio.")
        Long eventId) {
}
